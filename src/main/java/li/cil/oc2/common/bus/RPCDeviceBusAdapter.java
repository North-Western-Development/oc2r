/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.bus;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import li.cil.ceres.api.Serialized;
import li.cil.oc2.api.bus.DeviceBusController;
import li.cil.oc2.api.bus.device.Device;
import li.cil.oc2.api.bus.device.rpc.*;
import li.cil.oc2.api.util.Side;
import li.cil.oc2.common.Constants;
import li.cil.oc2.common.bus.device.rpc.RPCDeviceList;
import li.cil.oc2.common.bus.device.rpc.RPCMethodParameterTypeAdapters;
import li.cil.oc2.common.serialization.gson.*;
import li.cil.sedna.api.device.Steppable;
import li.cil.sedna.api.device.serial.SerialDevice;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class RPCDeviceBusAdapter implements Steppable, IEventSink {
    private static final int DEFAULT_MAX_MESSAGE_SIZE = 4 * Constants.KILOBYTE;
    private static final byte[] MESSAGE_DELIMITER = "\0".getBytes();
    private static final byte[] MESSAGE_DELIMITER2 = "\r".getBytes();

    public static final String ERROR_MESSAGE_TOO_LARGE = "message too large";
    public static final String ERROR_UNKNOWN_MESSAGE_TYPE = "unknown message type: ";
    public static final String ERROR_UNKNOWN_DEVICE = "unknown device";
    public static final String ERROR_UNKNOWN_METHOD = "unknown method";
    public static final String ERROR_INVALID_PARAMETER_SIGNATURE = "invalid parameter signature";
    public static final String ERROR_SUBSCRIPTIONS_NOT_SUPPORTED = "device does not support subscriptions";

    ///////////////////////////////////////////////////////////////////

    private final SerialDevice serialDevice;
    private final Gson gson;

    private final ArrayList<RPCDeviceWithIdentifier> devicesWithId = new ArrayList<>();
    private final HashMap<UUID, RPCDeviceList> devicesById = new HashMap<>();
    private final Set<RPCDeviceList> unmountedDevices = new HashSet<>();
    private final Set<RPCDeviceList> mountedDevices = new HashSet<>();
    private final Lock pauseLock = new ReentrantLock();
    private final Object receiveLock = new Object(); // Lock object for receive buffer
    private boolean isPaused;
    private boolean crmode = false;
    private final ArrayList<RPCEventSource> subscriptions = new ArrayList<>();

    ///////////////////////////////////////////////////////////////////

    @Serialized private final ByteBuffer transmitBuffer; // for data written to device by VM
    @Serialized private ByteBuffer receiveBuffer; // for data written by device to VM
    @Serialized private MethodInvocation synchronizedInvocation; // pending main thread invocation

    ///////////////////////////////////////////////////////////////////

    public RPCDeviceBusAdapter(final SerialDevice serialDevice) {
        this(serialDevice, DEFAULT_MAX_MESSAGE_SIZE);
    }

    public RPCDeviceBusAdapter(final SerialDevice serialDevice, final int maxMessageSize) {
        this.serialDevice = serialDevice;
        this.transmitBuffer = ByteBuffer.allocate(maxMessageSize);
        this.receiveBuffer = ByteBuffer.allocate(maxMessageSize);
        this.gson = RPCMethodParameterTypeAdapters.beginBuildGson()
            .registerTypeAdapter(byte[].class, new UnsignedByteArrayJsonSerializer())
            .registerTypeAdapter(MethodInvocation.class, new MethodInvocationJsonDeserializer())
            .registerTypeAdapter(Message.class, new MessageJsonDeserializer())
            .registerTypeAdapter(RPCDeviceWithIdentifier.class, new RPCDeviceWithIdentifierJsonSerializer())
            .registerTypeHierarchyAdapter(RPCMethod.class, new RPCMethodJsonSerializer())
            .registerTypeAdapter(EmptyMethodGroup.class, new EmptyRPCMethodGroupSerializer())
            .registerTypeAdapter(Side.class, new SideJsonDeserializer())
            .create();
    }

    ///////////////////////////////////////////////////////////////////

    public void mountDevices() {
        for (final RPCDevice device : unmountedDevices) {
            device.mount();
        }

        mountedDevices.addAll(unmountedDevices);
        unmountedDevices.clear();
    }

    public void unmountDevices() {
        for (final RPCDevice device : mountedDevices) {
            device.unmount();
        }

        unmountedDevices.addAll(mountedDevices);
        mountedDevices.clear();
    }

    public void disposeDevices() {
        for (RPCEventSource res: subscriptions) {
            res.unsubscribe(this);
        }
        unmountDevices();

        unmountedDevices.forEach(RPCDeviceList::dispose);
    }

    public void reset() {
        transmitBuffer.clear();
        receiveBuffer.clear();
        synchronizedInvocation = null;
    }

    public void pause() {
        if (isPaused) {
            return;
        }

        pauseLock.lock();
        isPaused = true;
        pauseLock.unlock();
    }

    public void resume(final DeviceBusController controller, final boolean didDevicesChange) {
        // Fix for upgrade from pre-event-support.  Ideally this would be done on deserialization, but Ceres doesn't
        // have a hook for that, and this should at least run before the buffer is needed.
        if (receiveBuffer == null) {
            // Default receive buffer size is the same as transmitBuffer capacity
            receiveBuffer = ByteBuffer.allocate(transmitBuffer.capacity());
        }

        isPaused = false;

        if (!didDevicesChange) {
            return;
        }

        // How device grouping works:
        // Each device can have multiple UUIDs due to being attached to multiple bus elements.
        // There is no guarantee that for each device D1 present on bus elements E1 and E2,
        // where device D2 is present on E1 it will also be present on E2. This is completely
        // up to the device providers.
        // Therefore, we must group all devices by their identifiers to then remove duplicate
        // groups. This is fragile because it will depend on the order the devices appear in
        // the list. However, since we add devices to bus elements in the order of their
        // providers, then add devices to the controller in the order of their elements, this
        // will work. And even if it does not, it only leads to duplicate devices popping up
        // in the VM, which, while annoying, is not breaking anything.
        // In a final step, when we know which devices are duplicates and what identifiers
        // they have, we pick a single identifier in a deterministic way, given the list of
        // identifiers is the same.

        final HashMap<UUID, ArrayList<RPCDevice>> devicesByIdentifier = new HashMap<>();
        for (final Device device : controller.getDevices()) {
            if (device instanceof final RPCDevice rpcDevice) {
                final Set<UUID> identifiers = controller.getDeviceIdentifiers(device);
                for (final UUID identifier : identifiers) {
                    devicesByIdentifier
                        .computeIfAbsent(identifier, unused -> new ArrayList<>())
                        .add(rpcDevice);
                }
            }
        }

        final HashMap<RPCDeviceList, ArrayList<UUID>> identifiersByDevice = new HashMap<>();
        devicesByIdentifier.forEach((identifier, devices) -> {
            final RPCDeviceList device = new RPCDeviceList(devices);

            // If there are no methods or events we have either no devices at all, or all
            // synthetic devices, i.e. devices that only contribute type names, but have
            // no functionality. We do not expose these to avoid cluttering the device list.
            if (device.getMethodGroups().isEmpty() && device.asEventSource() == null) {
                return;
            }

            identifiersByDevice
                .computeIfAbsent(device, unused -> new ArrayList<>())
                .add(identifier);
        });

        // Rebuild devices lists.
        devicesWithId.clear();
        devicesById.clear();

        final Set<RPCDeviceList> devices = new HashSet<>();
        identifiersByDevice.forEach((device, identifiers) -> {
            final UUID identifier = selectIdentifierDeterministically(identifiers);
            devicesWithId.add(new RPCDeviceWithIdentifier(identifier, device));
            devicesById.put(identifier, device);
            devices.add(device);

            // Add to set of unmounted devices if we don't already track it. It's a set, so
            // there won't be duplicates in the unmounted set due to this.
            if (!mountedDevices.contains(device)) {
                unmountedDevices.add(device);
            }
        });

        // Remove devices from mounted set, call appropriate callbacks.
        final HashSet<RPCDeviceList> removedMountedDevices = new HashSet<>(mountedDevices);
        removedMountedDevices.removeAll(devices);
        mountedDevices.removeAll(removedMountedDevices);
        removedMountedDevices.forEach(RPCDeviceList::unmount);

        // Remove devices from unmounted set.
        unmountedDevices.retainAll(devices);
    }

    public void tick() {
        if (isPaused) {
            return;
        }

        if (synchronizedInvocation != null) {
            final MethodInvocation methodInvocation = synchronizedInvocation;
            processMethodInvocation(methodInvocation, true);

            // This is also used to prevent thread from processing messages, so only
            // reset this when we're done. Otherwise, we may get a race-condition when
            // writing back data, which would not cause interleaved messages but might
            // confuse which results go with which method call.
            synchronizedInvocation = null;
        }
    }

    public void step(final int cycles) {
        if (isPaused || !pauseLock.tryLock()) {
            return;
        }

        try {
            readFromDevice();
            writeToDevice();
        } finally {
            pauseLock.unlock();
        }
    }

    ///////////////////////////////////////////////////////////////////

    private UUID selectIdentifierDeterministically(final ArrayList<UUID> identifiers) {
        UUID lowestIdentifier = identifiers.get(0);
        for (int i = 1; i < identifiers.size(); i++) {
            final UUID identifier = identifiers.get(i);
            if (identifier.compareTo(lowestIdentifier) < 0) {
                lowestIdentifier = identifier;
            }
        }
        return lowestIdentifier;
    }

    private void readFromDevice() {
        // Early return if we don't want to handle a new message.
        // 1. Make sure receiveBuffer is empty so we can almost certainly write results back (not a guarantee if events
        // are posted at the wrong time, especially if a device posts events while handling a method, but should be fine
        // if there is no misbehaving device).
        // 2. Make sure there is no pending synchronized method invocation so we only need to deal with one at once and
        // we can be sure we respond to methods in the order called.
        // Note that a synchronized method invocation is much more likely to have unrelated events post between the call
        // and the results.
        synchronized (receiveLock) {
            if (receiveBuffer.position() != 0 || synchronizedInvocation != null) {
                return;
            }
        }

        int value;
        // Only ever read one message at a time.  The first early return check *will* be invalidated by processing a
        // message and the second also could be
        while ((value = serialDevice.read()) >= 0) {
            if (value == 0 || value == 13) {
                this.crmode = value == 13;
                if (transmitBuffer.limit() > 0) {
                    transmitBuffer.flip();
                    if (transmitBuffer.hasRemaining()) {
                        final byte[] message = new byte[transmitBuffer.remaining()];
                        transmitBuffer.get(message);
                        processMessage(message);
                    }
                } else {
                    writeError(ERROR_MESSAGE_TOO_LARGE);
                }
                transmitBuffer.clear();
            } else if (transmitBuffer.hasRemaining()) {
                transmitBuffer.put((byte) value);
            } else {
                transmitBuffer.clear();
                transmitBuffer.limit(0); // marks message too large
            }
        }
    }

    private void writeToDevice() {
        synchronized (receiveLock) {
            receiveBuffer.flip();

            while (receiveBuffer.hasRemaining() && serialDevice.canPutByte()) {
                serialDevice.putByte(receiveBuffer.get());
            }

            receiveBuffer.compact();
        }
        serialDevice.flush();
    }

    private void processMessage(final byte[] messageData) {
        // HACK: Linux thinks the RPC bus is a TTY, and when all file descriptors to it are closed and then one is
        // opened again, the kernel resets the termios attributes and *turns on echo*. This sends a bunch of mangled
        // garbage back down the bus to end up here. Part of the mangling is replacing control characters, including
        // replacing the default message delimiter with "^@", so we can try to detect it. This doesn't work in crmode
        // and it's a bit of a hack to try to catch here
        //
        // Medium term solution: keep a file descriptor open on Linux all the time, so the `stty` stuff only needs to be
        // done once and echo doesn't turn back on.  Also make a human-readable symlink from /dev/oc2r/rpc to /dev/hvc0,
        // both because of the benefit of being human-readable (see also: /dev/disk/by-label with udev), and to ease the
        // transition for the longer-term solution.
        // Longer-term solution: Add an option to VirtIOConsoleDevice to present as a non-tty port; I have a proof of
        // concept for that but it needs more work.  This would move the bus to /dev/vport0p0, but the symlink can help
        // ease the transition.
        String messageString = new String(messageData).trim();
        if (messageString.isEmpty() || messageString.startsWith("^@")) {
            return;
        }

        final InputStreamReader stream = new InputStreamReader(new ByteArrayInputStream(messageData));
        try {
            final Message message = gson.fromJson(stream, Message.class);
            switch (message.type) {
                case Message.MESSAGE_TYPE_LIST -> writeDeviceList();
                case Message.MESSAGE_TYPE_METHODS -> {
                    if (message.data != null) {
                        writeDeviceMethods((UUID) message.data);
                    } else {
                        writeError("missing device id");
                    }
                }
                case Message.MESSAGE_TYPE_INVOKE_METHOD -> {
                    if (message.data != null) {
                        processMethodInvocation((MethodInvocation) message.data, false);
                    } else {
                        writeError("missing invocation data");
                    }
                }
                case Message.MESSAGE_TYPE_SUBSCRIBE -> {
                    if (message.data != null) {
                        subscribe((UUID)message.data);
                    } else {
                        writeError("missing invocation data");
                    }
                }
                case Message.MESSAGE_TYPE_UNSUBSCRIBE -> {
                    if (message.data != null) {
                        unsubscribe((UUID)message.data);
                    } else {
                        writeError("missing invocation data");
                    }
                }

                default -> writeError(ERROR_UNKNOWN_MESSAGE_TYPE + message.type);
            }
        } catch (final Throwable e) {
            writeError(e.getMessage());
        }
    }

    @Override
    public void postEvent(UUID deviceid, Object msg) {
        writeMessage(Message.MESSAGE_TYPE_EVENT, new Object[]{deviceid, msg});
    }

    private void subscribe(final UUID deviceId) {
        RPCDeviceList devices = devicesById.get(deviceId);
        if (devices == null) {
            writeError(ERROR_UNKNOWN_DEVICE);
            return;
        }
        RPCEventSource res = devices.asEventSource();
        if (res == null) {
            writeError(ERROR_SUBSCRIPTIONS_NOT_SUPPORTED);
        }

        res.subscribe(this, deviceId);
        subscriptions.add(res);
        writeMessage(Message.MESSAGE_TYPE_SUBSCRIBE, null);
    }

    private void unsubscribe(final UUID deviceId) {
        RPCDeviceList devices = devicesById.get(deviceId);
        if (devices == null) {
            writeError(ERROR_UNKNOWN_DEVICE);
            return;
        }
        RPCEventSource res = devices.asEventSource();
        if (res == null) {
            writeError(ERROR_SUBSCRIPTIONS_NOT_SUPPORTED);
        }

        res.unsubscribe(this);
        subscriptions.remove(res);
        writeMessage(Message.MESSAGE_TYPE_UNSUBSCRIBE, null);
    }

    private void processMethodInvocation(final MethodInvocation methodInvocation, final boolean isMainThread) {
        final RPCDevice device = devicesById.get(methodInvocation.deviceId);
        if (device == null) {
            writeError(ERROR_UNKNOWN_DEVICE);
            return;
        }

        final RPCInvocation invocation = new RPCInvocationImpl(methodInvocation.parameters, gson);

        // Yes, we could hashmap this lookup, but the expectation is that we'll generally
        // have relatively few methods per object, so the overhead of hashing would not
        // be worth it. Instead, we just do a quick linear search, which also gives us
        // a lot of flexibility for free (devices may dynamically change their methods).
        String error = ERROR_UNKNOWN_METHOD;
        for (final RPCMethodGroup methodGroup : device.getMethodGroups()) {
            if (!Objects.equals(methodGroup.getName(), methodInvocation.methodName)) {
                continue;
            }

            final Optional<RPCMethod> overload = methodGroup.findOverload(invocation);
            if (overload.isPresent()) {
                invokeMethod(methodInvocation, isMainThread, overload.get(), invocation);
                return;
            }

            error = ERROR_INVALID_PARAMETER_SIGNATURE;

            // Keep going, there may be an overload with matching parameter types in another
            // method group with the same name.
        }

        writeError(error);
    }

    private void invokeMethod(final MethodInvocation methodInvocation, final boolean isMainThread, final RPCMethod method, final RPCInvocation invocation) {
        if (method.isSynchronized() && !isMainThread) {
            synchronizedInvocation = methodInvocation;
            return;
        }

        try {
            final Object result = method.invoke(invocation);
            writeMessage(Message.MESSAGE_TYPE_RESULT, result);
        } catch (final Throwable e) {
            writeError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private void writeDeviceList() {
        writeMessage(Message.MESSAGE_TYPE_LIST, devicesWithId);
    }

    private void writeDeviceMethods(final UUID deviceId) {
        final RPCDeviceList device = devicesById.get(deviceId);
        if (device != null) {
            writeMessage(Message.MESSAGE_TYPE_METHODS, flattenMethodGroups(device.getMethodGroups()));
        } else {
            writeError("unknown device");
        }
    }

    private List<Object> flattenMethodGroups(final List<? extends RPCMethodGroup> methodGroups) {
        final List<Object> result = new ArrayList<>();
        for (final RPCMethodGroup methodGroup : methodGroups) {
            final Set<RPCMethod> overloads = methodGroup.getOverloads();
            if (overloads.isEmpty()) {
                result.add(new EmptyMethodGroup(methodGroup.getName()));
            } else {
                result.addAll(overloads);
            }
        }
        return result;
    }

    private void writeError(final String message) {
        writeMessage(Message.MESSAGE_TYPE_ERROR, message);
    }

    private void writeMessage(final String type, @Nullable final Object data) {
        final String json = gson.toJson(new Message(type, data));
        final byte[] bytes = json.getBytes();
        final int messageLength = bytes.length + MESSAGE_DELIMITER.length * 2;
        synchronized (receiveLock) {
            if (receiveBuffer.remaining() < messageLength) {
                // Decide whether to resize or not
                // The current heuristic is to resize for a large message (because
                // that is probably intended by a mod author) and not for many small
                // messages (because a computer user could use unlimited memory that
                // way).
                boolean reallocate = (receiveBuffer.capacity() <= messageLength);
                LogManager.getLogger().warn(
                    "Attempted to send {} message without enough space (size {}, remaining {}), {}",
                    type, messageLength, receiveBuffer.remaining(),
                    reallocate ? "reallocating" : "ignoring");

                if (!reallocate) {
                    // Note: There is nothing that indicates to either the VM or the peripheral that a message was eaten
                    return;
                }

                ByteBuffer newReceiveBuffer = ByteBuffer.allocate(messageLength * 2);
                newReceiveBuffer.put(receiveBuffer.flip());
                receiveBuffer = newReceiveBuffer;
                assert(messageLength < receiveBuffer.remaining());
            }

            // In case we went through a reset and the VM was in the middle of reading
            // a message we inject a delimiter up front to cause the truncated message
            // to be discarded.
            if (this.crmode) {
                receiveBuffer.put(MESSAGE_DELIMITER2);
            }
            else {
                receiveBuffer.put(MESSAGE_DELIMITER);
            }

            receiveBuffer.put(bytes);

            // We follow up each message with a delimiter, too, so the VM knows when the
            // message has been completed. This will lead to two delimiters between most
            // messages. The VM is expected to ignore such "empty" messages.
            if (this.crmode) {
                receiveBuffer.put(MESSAGE_DELIMITER2);
            }
            else {
                receiveBuffer.put(MESSAGE_DELIMITER);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////

    public record RPCDeviceWithIdentifier(UUID identifier, RPCDevice device) { }

    public record EmptyMethodGroup(String name) { }

    public record Message(String type, @Nullable Object data) {
        // Device -> VM
        public static final String MESSAGE_TYPE_LIST = "list";
        public static final String MESSAGE_TYPE_METHODS = "methods";
        public static final String MESSAGE_TYPE_RESULT = "result";
        public static final String MESSAGE_TYPE_ERROR = "error";
        public static final String MESSAGE_TYPE_EVENT = "event";

        // VM -> Device
        public static final String MESSAGE_TYPE_INVOKE_METHOD = "invoke";
        public static final String MESSAGE_TYPE_SUBSCRIBE = "subscribe";
        public static final String MESSAGE_TYPE_UNSUBSCRIBE = "unsubscribe";
    }

    @Serialized
    public static final class MethodInvocation {
        public UUID deviceId;
        public String methodName;
        public JsonArray parameters;

        @SuppressWarnings("unused") // For deserialization.
        public MethodInvocation() {
        }

        public MethodInvocation(final UUID deviceId, final String methodName, final JsonArray parameters) {
            this.deviceId = deviceId;
            this.methodName = methodName;
            this.parameters = parameters;
        }
    }

    ///////////////////////////////////////////////////////////////////

    private record RPCInvocationImpl(JsonArray parameters, Gson gson) implements RPCInvocation {
        @Override
        public JsonArray getParameters() {
            return parameters;
        }

        @Override
        public Gson getGson() {
            return gson;
        }

        @Override
        public Optional<Object[]> tryDeserializeParameters(final RPCParameter... parameterTypes) {
            if (parameterTypes.length != parameters.size()) {
                return Optional.empty();
            }

            final Object[] result = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                final RPCParameter parameterInfo = parameterTypes[i];
                try {
                    result[i] = gson.fromJson(parameters.get(i), parameterInfo.getType());
                } catch (final Throwable e) {
                    return Optional.empty();
                }
            }
            return Optional.of(result);
        }
    }
}
