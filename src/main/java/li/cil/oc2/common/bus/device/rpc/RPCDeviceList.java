/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.bus.device.rpc;

import li.cil.oc2.api.bus.device.rpc.IEventSink;
import li.cil.oc2.api.bus.device.rpc.RPCDevice;
import li.cil.oc2.api.bus.device.rpc.RPCEventSource;
import li.cil.oc2.api.bus.device.rpc.RPCMethodGroup;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public record RPCDeviceList(ArrayList<RPCDevice> devices) implements RPCDevice {

    public ArrayList<RPCDevice> getDevices() {
        return devices;
    }

    @Override
    public List<String> getTypeNames() {
        return devices.stream()
            .map(RPCDevice::getTypeNames)
            .flatMap(Collection::stream)
            .distinct()
            .collect(Collectors.toList());
    }

    @Override
    public List<RPCMethodGroup> getMethodGroups() {
        return devices.stream()
            .map(RPCDevice::getMethodGroups)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }

    @Override
    public void mount() {
        for (final RPCDevice device : devices) {
            device.mount();
        }
    }

    @Override
    public void unmount() {
        for (final RPCDevice device : devices) {
            device.unmount();
        }
    }

    @Override
    public void dispose() {
        for (final RPCDevice device : devices) {
            device.dispose();
        }
    }

    // NB: We only use the list device in the adapter, for referencing grouped devices by their ID.
    //     As such, serialize/deserialize will never be called on this class.

    @Override
    public CompoundTag serializeNBT() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deserializeNBT(final CompoundTag tag) {
        throw new UnsupportedOperationException();
    }

    private record RPCEventSourceList(List<RPCEventSource> sources) implements RPCEventSource {
        @Override
        public void subscribe(IEventSink dba, UUID sourceid) {
            for (RPCEventSource source : sources) {
                source.subscribe(dba, sourceid);
            }
        }

        @Override
        public void unsubscribe(IEventSink dba) {
            for (RPCEventSource source : sources) {
                source.unsubscribe(dba);
            }
        }
    }

    @Override
    public RPCEventSource asEventSource() {
        List<RPCEventSource> sources = devices.stream()
                .map(RPCDevice::asEventSource)
                .filter(Objects::nonNull)
                .toList();
        return sources.isEmpty() ? null : new RPCEventSourceList(sources);
    }
}
