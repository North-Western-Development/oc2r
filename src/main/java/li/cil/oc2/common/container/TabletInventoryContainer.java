/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.container;

import java.util.UUID;

import li.cil.oc2.api.bus.device.DeviceTypes;
import li.cil.oc2.common.item.TabletItem;
import li.cil.oc2.common.vm.VMItemStackHandlers;
import li.cil.oc2.common.vm.tablet.TabletState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

public final class TabletInventoryContainer extends AbstractTabletContainer {
    private final UUID tabletId;

    public static void createServer(UUID tabletId, final ServerPlayer player) {
        NetworkHooks.openScreen(player, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("item.oc2r.tablet");
            }

            @Override
            public AbstractContainerMenu createMenu(final int id, final Inventory inventory, final Player player) {
                TabletState state = getTabletState(tabletId, player);

                return new TabletInventoryContainer(id, tabletId, player,
                        createEnergyInfo(state.getEnergy(), state.getBusController()));
            }
        }, b -> b.writeUUID(tabletId));
    }

    public static TabletInventoryContainer createClient(final int id, final Inventory inventory,
            final FriendlyByteBuf data) {
        final UUID tabletId = data.readUUID();

        return new TabletInventoryContainer(id, tabletId, inventory.player, createClientEnergyInfo());
    }

    ///////////////////////////////////////////////////////////////////

    private TabletInventoryContainer(final int id, final UUID tabletId, final Player player,
            final IntPrecisionContainerData energyInfo) {
        super(Containers.TABLET.get(), id, tabletId, player, energyInfo);

        this.tabletId = tabletId;

        final VMItemStackHandlers handlers = getTabletState(tabletId, player).getDeviceItems();

        handlers.getItemHandler(DeviceTypes.FLASH_MEMORY).ifPresent(itemHandler -> {
            if (itemHandler.getSlots() > 0) {
                addSlot(new DeviceTypeSlotItemHandler(itemHandler, DeviceTypes.FLASH_MEMORY, 0, 64, 78));
            }
        });

        handlers.getItemHandler(DeviceTypes.MEMORY).ifPresent(itemHandler -> {
            for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                addSlot(new DeviceTypeSlotItemHandler(itemHandler, DeviceTypes.MEMORY, slot, 64 + slot * SLOT_SIZE,
                        24));
            }
        });

        handlers.getItemHandler(DeviceTypes.HARD_DRIVE).ifPresent(itemHandler -> {
            for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                addSlot(new DeviceTypeSlotItemHandler(itemHandler, DeviceTypes.HARD_DRIVE, slot,
                        100 + (slot % 2) * SLOT_SIZE, 60 + (slot / 2) * SLOT_SIZE));
            }
        });

        handlers.getItemHandler(DeviceTypes.CARD).ifPresent(itemHandler -> {
            for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                addSlot(new DeviceTypeSlotItemHandler(itemHandler, DeviceTypes.CARD, slot, 38, 24 + slot * SLOT_SIZE));
            }
        });

        handlers.getItemHandler(DeviceTypes.CPU).ifPresent(itemHandler -> {
            if (itemHandler.getSlots() > 0) {
                addSlot(new DeviceTypeSlotItemHandler(itemHandler, DeviceTypes.CPU, 0, 64, 52));
            }
        });

        createPlayerInventoryAndHotbarSlots(player.getInventory(), 8, 115);
    }

    @Override
    protected boolean isSlotLocked(final Inventory inventory, final int slot) {
        ItemStack stack = inventory.getItem(slot);
        if (stack != null) {
            CompoundTag tag = stack.getOrCreateTag();
            return tag.hasUUID(TabletItem.UUID_TAG_NAME) && tag.getUUID(TabletItem.UUID_TAG_NAME).equals(tabletId);
        }
        return false;
    }
}
