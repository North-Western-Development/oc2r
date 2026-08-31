/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.item;

import static li.cil.oc2.common.util.NBTUtils.makeInventoryTag;
import static li.cil.oc2.common.util.RegistryUtils.key;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import static li.cil.oc2.common.Constants.*;

import li.cil.oc2.api.bus.device.DeviceTypes;
import li.cil.oc2.client.TabletStateCache;
import li.cil.oc2.common.bus.CommonDeviceBusController.BusState;
import li.cil.oc2.common.capabilities.Capabilities;
import li.cil.oc2.common.config.Config;
import li.cil.oc2.common.container.TabletInventoryContainer;
import li.cil.oc2.common.container.TabletTerminalContainer;
import li.cil.oc2.common.network.Network;
import li.cil.oc2.common.network.message.TabletClientUpdateMessage;
import li.cil.oc2.common.network.message.TabletEnergyMessage;
import li.cil.oc2.common.network.message.TabletItemsMessage;
import li.cil.oc2.common.serialization.NBTSerialization;
import li.cil.oc2.common.util.NBTUtils;
import li.cil.oc2.common.util.TooltipUtils;
import li.cil.oc2.common.vm.VMRunState;
import li.cil.oc2.common.vm.tablet.TabletState;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.server.ServerLifecycleHooks;

public class TabletItem extends ModItem {
    public static final String UUID_TAG_NAME = "uuid";
    public static final String TABLET_ON_TAG_NAME = "tablet_on";

    private static final String TERMINAL_TAG_NAME = "terminal";

    public static UUID getOrCreateUUID(final ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();

        if (!tag.hasUUID(UUID_TAG_NAME)) {
            UUID id = UUID.randomUUID();
            tag.putUUID(UUID_TAG_NAME, id);
            return id;
        }

        return tag.getUUID(UUID_TAG_NAME);
    }

    public TabletItem() {
        super(createProperties().stacksTo(1));
    }

    public static void openTerminalScreen(final ServerPlayer player, final UUID id) {
        if (player.level() != null && !player.level().isClientSide()) {
            player.getServer().getLevel(Level.OVERWORLD).getCapability(Capabilities.tabletData())
                    .ifPresent(tabletData -> {
                        TabletTerminalContainer.createServer(id, player);
                    });
        }
    }

    public static void openInventoryScreen(final ServerPlayer player, final UUID id) {
        if (player.level() != null && !player.level().isClientSide()) {
            player.getServer().getLevel(Level.OVERWORLD).getCapability(Capabilities.tabletData())
                    .ifPresent(tabletData -> {
                        TabletInventoryContainer.createServer(id, player);
                    });
        }
    }

    public static void start(final Level level, final UUID id) {
        if (level != null && !level.isClientSide() && level.dimension() == Level.OVERWORLD) {
            level.getCapability(Capabilities.tabletData())
                    .ifPresent(tabletData -> tabletData.get(id).getVirtualMachine().start());
        }
    }

    public static void stop(final Level level, final UUID id) {
        if (level != null && !level.isClientSide() && level.dimension() == Level.OVERWORLD) {
            level.getCapability(Capabilities.tabletData())
                    .ifPresent(tabletData -> tabletData.get(id).getVirtualMachine().stop());
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() && stack.getOrCreateTag().hasUUID(TabletItem.UUID_TAG_NAME)) {
            TabletState tablet = TabletStateCache.getOrCreateTabletState(getOrCreateUUID(stack));
            tablet.getTerminal().clientTick();
        }
    }

    @Override
    public void appendHoverText(final ItemStack stack, @Nullable final Level level, final List<Component> tooltip,
            final TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        TooltipUtils.addEnergyConsumption(Config.tabletEnergyPerTick, tooltip);
        TooltipUtils.addEntityEnergyInformation(stack, tooltip);
        TooltipUtils.addEntityInventoryInformation(stack, tooltip);
    }

    @Nullable
    @Override
    public ICapabilityProvider initCapabilities(final ItemStack stack, @Nullable final CompoundTag nbt) {
        return new TabletItemStackCapabilityProvider(stack);
    }

    private class TabletItemStackCapabilityProvider implements ICapabilitySerializable<CompoundTag> {
        private ItemStack stack;

        public TabletItemStackCapabilityProvider(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap,
                @Nullable Direction side) {
            if (!stack.getOrCreateTag().hasUUID(TabletItem.UUID_TAG_NAME)) {
                return LazyOptional.empty();
            }

            UUID id = stack.getTag().getUUID(TabletItem.UUID_TAG_NAME);

            TabletState state;

            if (EffectiveSide.get().isServer()) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                state = overworld.getCapability(Capabilities.tabletData()).map(tabletData -> tabletData.get(id))
                        .orElse(null);

                if (state == null)
                    return LazyOptional.empty();
            } else {
                state = TabletStateCache.getOrCreateTabletState(id);
            }

            if (cap == Capabilities.itemHandler()) {
                return LazyOptional.of(() -> state.getDeviceItems()).cast();
            }
            if (cap == Capabilities.energyStorage() && Config.tabletsUseEnergy()) {
                return LazyOptional.of(() -> state.getEnergy()).cast();
            }

            return LazyOptional.empty();
        }

		@Override
		public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();

            if (!stack.getOrCreateTag().hasUUID(TabletItem.UUID_TAG_NAME)) {
                return tag;
            }

            UUID id = stack.getTag().getUUID(TabletItem.UUID_TAG_NAME);

            // Sync NBT
            if (EffectiveSide.get().isServer()) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                TabletState state = null;

                try {
                    state = overworld.getCapability(Capabilities.tabletData()).map(tabletData -> tabletData.get(id))
                        .orElse(null);
                } catch (Exception e) {
                    // Only happens when destroyed in the creative menu with destroy all.
                }

                if (state == null)
                    return tag;

                if (state.getBusController().getState() == BusState.SCAN_PENDING) {
                    state.getBusController().scheduleBusScan();
                }

                tag.put(ENERGY_TAG_NAME, state.getEnergy().serializeNBT());
                tag.put(ITEMS_TAG_NAME, state.getDeviceItems().saveItems());

                boolean running = state.getVirtualMachine().getRunState() == VMRunState.RUNNING;

                tag.putBoolean(TABLET_ON_TAG_NAME, running);
                stack.getTag().putBoolean(TABLET_ON_TAG_NAME, running);

                if (id != null) {
                    tag.putUUID(TabletItem.UUID_TAG_NAME, id);
                }

                if (state.getTerminalUser() != null) {
                    Network.sendToClient(new TabletItemsMessage(state.getId(), tag), state.getTerminalUser());
                    Network.sendToClient(new TabletEnergyMessage(id, tag), state.getTerminalUser());
                }
            }

            return tag;
		}

		@Override
		public void deserializeNBT(CompoundTag tag) {
            // Handled internally.
		}
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            final ItemStack item = serverPlayer.getItemInHand(hand);

            serverPlayer.getServer().getLevel(Level.OVERWORLD).getCapability(Capabilities.tabletData())
                    .ifPresent(tabletData -> {
                        UUID id = getOrCreateUUID(item);

                        // Create a new TabletState if it doesn't exist for this ItemStack yet.
                        if (tabletData.get(id) == null) {
                            tabletData.put(id, new TabletState(id, true));
                            tabletData.get(id).getDeviceItems().loadItems(
                                    NBTUtils.getOrCreateChildTag(item.getOrCreateTag(), MOD_TAG_NAME, ITEMS_TAG_NAME));
                        }

                        TabletState tablet = tabletData.get(id);

                        // Run an initial client setup when a new user starts using the tablet.
                        ServerPlayer currentUser = tablet.getTerminalUser();
                        if (currentUser == null || currentUser.getUUID() != serverPlayer.getUUID()) {
                            tablet.setTerminalUser(serverPlayer);

                            if (tablet.getVirtualMachine().getRunState() != VMRunState.STOPPED) {
                                CompoundTag tag = new CompoundTag();
                                tag.put(TERMINAL_TAG_NAME, NBTSerialization.serialize(tablet.getTerminal()));

                                try {
                                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                                    NbtIo.writeCompressed(tag, out);

                                    byte[] data = out.toByteArray();

                                    Network.sendToClient(new TabletClientUpdateMessage(id, data), serverPlayer);
                                } catch (Exception e) {
                                    System.out.println("Failed to serialize terminal state.");
                                }
                            }

                            tablet.getVirtualMachine().handleBusStateChanged(tablet.getBusController().getState());
                            tablet.getVirtualMachine().handleRunStateChanged(tablet.getVirtualMachine().getRunState());
                        }

                        if (player.isShiftKeyDown()) {
                            tablet.getVirtualMachine().start();
                        } else {
                            openTerminalScreen(serverPlayer, getOrCreateUUID(item));
                        }
                    });
        }

        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    public static ItemStack getTabletWithFlash() {
        final ItemStack tablet = new ItemStack(Items.TABLET.get());

        final CompoundTag itemsTag = NBTUtils.getOrCreateChildTag(tablet.getOrCreateTag(), MOD_TAG_NAME,
                ITEMS_TAG_NAME);
        itemsTag.put(key(DeviceTypes.FLASH_MEMORY), makeInventoryTag(
                new ItemStack(Items.FLASH_MEMORY_CUSTOM.get())));

        return tablet;
    }
}
