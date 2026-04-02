/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.blockentity;

import li.cil.oc2.api.API;
import li.cil.oc2.api.bus.DeviceBusElement;
import li.cil.oc2.api.bus.device.Device;
import li.cil.oc2.api.bus.device.DeviceTypes;
import li.cil.oc2.api.bus.device.provider.ItemDeviceQuery;
import li.cil.oc2.api.capabilities.BundledEmitter;
import li.cil.oc2.api.capabilities.RedstoneEmitter;
import li.cil.oc2.api.capabilities.TerminalUserProvider;
import li.cil.oc2.client.audio.LoopingSoundManager;
import li.cil.oc2.common.Constants;
import li.cil.oc2.common.block.Blocks;
import li.cil.oc2.common.components.DataComponents;
import li.cil.oc2.common.components.RestrictedContainer;
import li.cil.oc2.common.config.Config;
import li.cil.oc2.common.block.ComputerBlock;
import li.cil.oc2.common.bus.AbstractBlockDeviceBusElement;
import li.cil.oc2.common.bus.BlockDeviceBusController;
import li.cil.oc2.common.bus.CommonDeviceBusController;
import li.cil.oc2.common.bus.device.util.Devices;
import li.cil.oc2.common.capabilities.Capabilities;
import li.cil.oc2.common.container.ComputerInventoryContainer;
import li.cil.oc2.common.container.ComputerTerminalContainer;
import li.cil.oc2.common.energy.FixedEnergyStorage;
import li.cil.oc2.common.ext.ICaptureInputStateStorage;
import li.cil.oc2.common.network.Network;
import li.cil.oc2.common.network.message.ComputerBootErrorMessage;
import li.cil.oc2.common.network.message.ComputerBusStateMessage;
import li.cil.oc2.common.network.message.ComputerRunStateMessage;
import li.cil.oc2.common.network.message.ComputerTerminalOutputMessage;
import li.cil.oc2.common.serialization.NBTSerialization;
import li.cil.oc2.common.util.*;
import li.cil.oc2.common.vm.*;
import li.cil.oc2.common.vm.terminal.Terminal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

import static li.cil.oc2.common.Constants.ITEMS_TAG_NAME;

@EventBusSubscriber(modid = API.MOD_ID)
public final class ComputerBlockEntity extends ModBlockEntity implements TerminalUserProvider, TickableBlockEntity, ICaptureInputStateStorage {
    private static final String BUS_ELEMENT_TAG_NAME = "busElement";
    private static final String DEVICES_TAG_NAME = "devices";
    private static final String TERMINAL_TAG_NAME = "terminal";
    private static final String STATE_TAG_NAME = "state";
    private static final String ENERGY_TAG_NAME = "energy";

    private static final int MEMORY_SLOTS = 4;
    private static final int HARD_DRIVE_SLOTS = 4;
    private static final int FLASH_MEMORY_SLOTS = 1;
    private static final int CARD_SLOTS = 4;
    private static final int CPU_SLOTS = 1;

    private static final int MAX_RUNNING_SOUND_DELAY = TickUtils.toTicks(Duration.ofSeconds(2));

    ///////////////////////////////////////////////////////////////////

    private boolean hasAddedOwnDevices;
    private boolean isNeighborUpdateScheduled;
    private LevelChunk chunk;

    ///////////////////////////////////////////////////////////////////

    private final Terminal terminal = new Terminal();
    private final ComputerBusElement busElement = new ComputerBusElement();
    private final ComputerItemStackHandlers deviceItems = new ComputerItemStackHandlers(() -> this.getLevel().registryAccess());
    private final FixedEnergyStorage energy = new FixedEnergyStorage(Config.computerEnergyStorage);
    private final ComputerVirtualMachine virtualMachine = new ComputerVirtualMachine(new BlockDeviceBusController(busElement, Config.computerEnergyPerTick, this), deviceItems::getDeviceAddressBase);
    private final Set<Player> terminalUsers = Collections.newSetFromMap(new WeakHashMap<>());
    private boolean captureInputState;

    ///////////////////////////////////////////////////////////////////

    public ComputerBlockEntity(final BlockPos pos, final BlockState state) {
        super(BlockEntities.COMPUTER.get(), pos, state);

        // We want to unload devices even on level unload to free global resources.
        setNeedsLevelUnloadEvent();

        virtualMachine.busController.onAfterDeviceScan.add(this::onAfterDeviceScan);
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public VirtualMachine getVirtualMachine() {
        return virtualMachine;
    }

    public VMItemStackHandlers getItemStackHandlers() {
        return deviceItems;
    }

    @Override
    public boolean getCaptureInputState() {
        return captureInputState;
    }

    @Override
    public void setCaptureInputState(boolean value) {
        this.captureInputState = value;
    }

    public void start() {
        if (level != null && !level.isClientSide()) {
            virtualMachine.start();
        }
    }

    public void stop() {
        if (level != null && !level.isClientSide()) {
            virtualMachine.stop();
        }
    }

    public void openTerminalScreen(final ServerPlayer player) {
        ComputerTerminalContainer.createServer(this, energy, virtualMachine.busController, player);
    }

    public void openInventoryScreen(final ServerPlayer player) {
        ComputerInventoryContainer.createServer(this, energy, virtualMachine.busController, player);
    }

    public void addTerminalUser(final Player player) {
        terminalUsers.add(player);
    }

    public void removeTerminalUser(final Player player) {
        terminalUsers.remove(player);
    }

    @Override
    public Iterable<Player> getTerminalUsers() {
        return terminalUsers;
    }

    public void handleNeighborChanged() {
        if (level != null && !level.isClientSide()) {
            virtualMachine.busController.scheduleBusScan();
        }
    }

    public void onAfterDeviceScan(final CommonDeviceBusController.AfterDeviceScanEvent event) {
        if (event.didDevicesChange()) {
            level.invalidateCapabilities(getBlockPos());
        }
    }

    public <T extends Device> @Nullable T getFirstDevice(Class<T> cls) {
        for (final Device device : virtualMachine.busController.getDevices())
        {
            if (cls.isAssignableFrom(device.getClass())) {
                //noinspection unchecked
                return (T) device;
            }
        }

        return null;
    }

    @Override
    public void clientTick() {
        terminal.clientTick();
    }

    @Override
    public void serverTick() {
        if (level == null) {
            return;
        }

        // Always add devices provided for the computer itself, even if there's no
        // adjacent cable. Because that would just be weird.
        if (!hasAddedOwnDevices) {
            hasAddedOwnDevices = true;
            busElement.addOwnDevices();
        }

        if (isNeighborUpdateScheduled) {
            isNeighborUpdateScheduled = false;
            level.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
        }

        // Just grab it again every tick, to avoid this becoming invalid if something tries to
        // mess with this BlockEntity in unexpected ways.
        chunk = level.getChunkAt(getBlockPos());

        virtualMachine.tick();
    }

    @Override
    protected void applyImplicitComponents(final DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);

        var container = componentInput.get(DataComponents.RESTRICTED_CONTAINER);
        if (container != null) {
            deviceItems.loadItems(getLevel().registryAccess(), container);
        } else {
            var block_entity_data = componentInput.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
            if (block_entity_data == null) return;
            var tag = block_entity_data.copyTag();
            tag.size();
        }
    }

    @Override
    protected void collectImplicitComponents(final DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);

        var container = new RestrictedContainer();
        deviceItems.saveItems(container);
        components.set(DataComponents.RESTRICTED_CONTAINER, container);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);

        tag.put(TERMINAL_TAG_NAME, NBTSerialization.serialize(terminal));
        tag.putInt(AbstractVirtualMachine.BUS_STATE_TAG_NAME, virtualMachine.getBusState().ordinal());
        tag.putInt(AbstractVirtualMachine.RUN_STATE_TAG_NAME, virtualMachine.getRunState().ordinal());
        tag.putString(AbstractVirtualMachine.BOOT_ERROR_TAG_NAME, Component.Serializer.toJson(virtualMachine.getBootError(), registries));

        return tag;
    }

    @Override
    public void handleUpdateTag(final CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);

        NBTSerialization.deserialize(tag.getCompound(TERMINAL_TAG_NAME), terminal);

        // Only update client-side state on the client
        if (level != null && level.isClientSide()) {
            virtualMachine.setBusStateClient(CommonDeviceBusController.BusState.values()[tag.getInt(AbstractVirtualMachine.BUS_STATE_TAG_NAME)]);
            virtualMachine.setRunStateClient(VMRunState.values()[tag.getInt(AbstractVirtualMachine.RUN_STATE_TAG_NAME)]);
            virtualMachine.setBootErrorClient(Component.Serializer.fromJson(tag.getString(AbstractVirtualMachine.BOOT_ERROR_TAG_NAME), registries));
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        if (virtualMachine.getRunState() != VMRunState.STOPPED) {
            tag.put(STATE_TAG_NAME, virtualMachine.serialize());
            tag.put(TERMINAL_TAG_NAME, NBTSerialization.serialize(terminal));
        }

        tag.put(ENERGY_TAG_NAME, energy.serializeNBT(registries));
        tag.put(BUS_ELEMENT_TAG_NAME, busElement.save(registries));
        tag.put(ITEMS_TAG_NAME, deviceItems.saveItems(registries));
        tag.put(DEVICES_TAG_NAME, deviceItems.saveDevices(registries));
    }

    @Override
    public void loadAdditional(final CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        energy.deserializeNBT(registries, tag.getCompound(ENERGY_TAG_NAME));
        busElement.loadAdditional(tag.getCompound(BUS_ELEMENT_TAG_NAME), registries);
        deviceItems.loadItems(registries, tag.getCompound(ITEMS_TAG_NAME));
        deviceItems.loadDevices(registries, tag.getCompound(DEVICES_TAG_NAME));
        virtualMachine.deserialize(tag.getCompound(STATE_TAG_NAME));
        NBTSerialization.deserialize(tag.getCompound(TERMINAL_TAG_NAME), terminal);
    }

    public void exportToItemStack(final ItemStack stack) {
        var container = new RestrictedContainer();
        deviceItems.exportDeviceDataToItemStacks();
        deviceItems.saveItems(container);
        stack.set(li.cil.oc2.common.components.DataComponents.RESTRICTED_CONTAINER, container);
    }

    ///////////////////////////////////////////////////////////////////

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(
            Capabilities.ItemHandler.BLOCK,
            (level, pos, state, be, side) -> {
                if (be instanceof final ComputerBlockEntity self) {
                    return self.deviceItems.combinedItemHandlers;
                }
                return null;
            },
            Blocks.COMPUTER.get()
        );
        event.registerBlock(
            Capabilities.DeviceBusElement.BLOCK,
            (level, pos, state, be, side) -> {
                if (be instanceof final ComputerBlockEntity self) {
                    return self.busElement;
                }
                return null;
            },
            Blocks.COMPUTER.get()
        );

        if (Config.computersUseEnergy()) {
            event.registerBlock(
                Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, side) -> {
                    if (be instanceof final ComputerBlockEntity self) {
                        return self.energy;
                    }
                    return null;
                },
                Blocks.COMPUTER.get()
            );
        }
    }

    @Override
    protected void loadClient() {
        super.loadClient();

        terminal.setDisplayOnly(true);
    }

    @Override
    protected void loadServer() {
        super.loadServer();

        assert level != null;

        virtualMachine.state.builtinDevices.rtcMinecraft.setLevel(level);
    }

    @Override
    protected void unloadServer(final boolean isRemove) {
        super.unloadServer(isRemove);

        if (isRemove) {
            virtualMachine.stop();
        } else {
            virtualMachine.suspend();
        }

        virtualMachine.dispose();

        // This is necessary in case some other controller found us before our controller
        // did its scan, which can happen because the scan can happen with a delay. In
        // that case we don't know that controller and disposing our controller won't
        // notify it, so we also send out a notification through our bus element, which
        // would be registered with other controllers in that case.
        busElement.scheduleScan();
    }

    @Nullable
    public byte[] getBundledSignal(Direction direction) {
        if (level != null) {
            // Redstone requests info for faces with external perspective. Capabilities treat
            // the Direction from an internal and local perspective, so flip it, and transform it from global to
            // local.
            var cap = level.getCapability(Capabilities.BundledEmitter.BLOCK, getBlockPos(), null, this, HorizontalBlockUtils.toLocal(getBlockState(), direction.getOpposite()));
            return Optional.ofNullable(cap)
                .map(BundledEmitter::getBundledOutput)
                .orElse(new byte[Constants.BUNDLE_COLOR_COUNT]);
        }

        return new byte[Constants.BUNDLE_COLOR_COUNT];
    }

    ///////////////////////////////////////////////////////////////////

    private void sendToClientsTrackingComputer(final CustomPacketPayload message) {
        if (chunk != null) {
            Network.sendToClientsTrackingChunk(message, chunk);
        }
    }

    ///////////////////////////////////////////////////////////////////

    private final class ComputerItemStackHandlers extends AbstractVMItemStackHandlers {
        public ComputerItemStackHandlers(Supplier<HolderLookup.Provider> providerSupplier) {
            super(providerSupplier, new GroupDefinition(DeviceTypes.MEMORY, MEMORY_SLOTS), new GroupDefinition(DeviceTypes.HARD_DRIVE, HARD_DRIVE_SLOTS), new GroupDefinition(DeviceTypes.FLASH_MEMORY, FLASH_MEMORY_SLOTS), new GroupDefinition(DeviceTypes.CARD, CARD_SLOTS), new GroupDefinition(DeviceTypes.CPU, CPU_SLOTS));
        }

        @Override
        protected ItemDeviceQuery makeQuery(final ItemStack stack) {
            return Devices.makeQuery(ComputerBlockEntity.this, stack);
        }

        @Override
        protected void onChanged() {
            super.onChanged();
            if (level != null && !level.isClientSide()) {
                virtualMachine.busController.scheduleBusScan();
                ChunkUtils.setLazyUnsaved(level, getBlockPos());
            }
            isNeighborUpdateScheduled = true;
        }
    }

    private final class ComputerBusElement extends AbstractBlockDeviceBusElement {
        private static final String DEVICE_ID_TAG_NAME = "device_id";

        private final HashSet<Device> devices = new HashSet<>();
        private UUID deviceId = UUID.randomUUID();

        @Nullable
        @Override
        public Level getLevel() {
            return ComputerBlockEntity.this.getLevel();
        }

        @Override
        public BlockPos getPosition() {
            return getBlockPos();
        }

        public void addOwnDevices() {
            assert level != null;

            collectDevices(level, getPosition(), null).ifPresent(result -> {
                for (final BlockEntry info : result.getEntries()) {
                    devices.add(info.getDevice());
                    super.addDevice(info.getDevice());
                }
            });
        }

        @Override
        public Optional<Collection<DeviceBusElement>> getNeighbors() {
            return super.getNeighbors().map(neighbors -> {
                // If we have valid neighbors (complete bus) also add a connection to the bus
                // element hosting our item devices.
                final ArrayList<DeviceBusElement> list = new ArrayList<>(neighbors);
                list.add(deviceItems.busElement);
                return list;
            });
        }

        @Override
        public boolean canScanContinueTowards(@Nullable final Direction direction) {
            return getBlockState().getValue(ComputerBlock.FACING) != direction;
        }

        @Override
        protected boolean canDetectDevicesTowards(@Nullable final Direction direction) {
            return direction == null;
        }

        @Override
        public Optional<UUID> getDeviceIdentifier(final Device device) {
            if (devices.contains(device)) {
                return Optional.of(deviceId);
            }
            return super.getDeviceIdentifier(device);
        }

        @Override
        public CompoundTag save(HolderLookup.Provider registries) {
            final CompoundTag tag = super.save(registries);
            tag.putUUID(DEVICE_ID_TAG_NAME, deviceId);
            return tag;
        }

        public void loadAdditional(final CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            if (tag.hasUUID(DEVICE_ID_TAG_NAME)) {
                deviceId = tag.getUUID(DEVICE_ID_TAG_NAME);
            }
        }
    }

    private final class ComputerVMRunner extends AbstractTerminalVMRunner {
        public ComputerVMRunner(final AbstractVirtualMachine virtualMachine, final Terminal terminal) {
            super(virtualMachine, terminal);
        }

        @Override
        protected void sendTerminalUpdateToClient(final ByteBuffer output) {
            sendToClientsTrackingComputer(new ComputerTerminalOutputMessage(ComputerBlockEntity.this, output));
        }
    }

    private final class ComputerVirtualMachine extends AbstractVirtualMachine {
        private ComputerVirtualMachine(final CommonDeviceBusController busController, final BaseAddressProvider baseAddressProvider) {
            super(busController);
            state.vmAdapter.setBaseAddressProvider(baseAddressProvider);
        }

        @Override
        public void setRunStateClient(final VMRunState value) {
            super.setRunStateClient(value);

            if (value == VMRunState.RUNNING) {
                if (!LoopingSoundManager.isPlaying(ComputerBlockEntity.this) && level != null) {
                    LoopingSoundManager.play(ComputerBlockEntity.this, SoundEvents.COMPUTER_RUNNING.get(), level.getRandom().nextInt(MAX_RUNNING_SOUND_DELAY));
                }
            } else {
                LoopingSoundManager.stop(ComputerBlockEntity.this);
            }
        }

        @Override
        public void tick() {
            assert level != null;

            if (isRunning()) {
                ChunkUtils.setLazyUnsaved(level, getBlockPos());
                busController.setDeviceContainersChanged();
            }

            super.tick();
        }

        @Override
        protected boolean consumeEnergy(final int amount, final boolean simulate) {
            if (!Config.computersUseEnergy()) {
                return true;
            }

            if (amount > energy.getEnergyStored()) {
                return false;
            }

            energy.extractEnergy(amount, simulate);
            return true;
        }

        @Override
        protected void stopRunnerAndReset() {
            super.stopRunnerAndReset();

            TerminalUtils.resetTerminal(terminal, output -> sendToClientsTrackingComputer(new ComputerTerminalOutputMessage(ComputerBlockEntity.this, output)));
        }

        @Override
        protected AbstractTerminalVMRunner createRunner() {
            return new ComputerVMRunner(this, terminal);
        }

        @Override
        protected void handleBusStateChanged(final CommonDeviceBusController.BusState value) {
            sendToClientsTrackingComputer(new ComputerBusStateMessage(ComputerBlockEntity.this, value));

            if (value == CommonDeviceBusController.BusState.READY && level != null) {
                // Bus just became ready, meaning new devices may be available, meaning new
                // capabilities may be available, so we need to tell our neighbors.
                level.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
            }
        }

        @Override
        protected void handleRunStateChanged(final VMRunState value) {
            sendToClientsTrackingComputer(new ComputerRunStateMessage(ComputerBlockEntity.this, value));
        }

        @Override
        protected void handleBootErrorChanged(@Nullable final Component value) {
            sendToClientsTrackingComputer(new ComputerBootErrorMessage(ComputerBlockEntity.this, value));
        }
    }
}
