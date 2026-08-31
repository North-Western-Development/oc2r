/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.vm.tablet;

import li.cil.oc2.api.bus.device.DeviceTypes;
import li.cil.oc2.api.bus.device.provider.ItemDeviceQuery;
import li.cil.oc2.common.bus.device.util.Devices;
import li.cil.oc2.common.network.Network;
import li.cil.oc2.common.network.message.TabletItemsMessage;
import li.cil.oc2.common.vm.AbstractVMItemStackHandlers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import static li.cil.oc2.common.Constants.ITEMS_TAG_NAME;

public class TabletItemStackHandlers extends AbstractVMItemStackHandlers {
    private static final int MEMORY_SLOTS = 4;
    private static final int HARD_DRIVE_SLOTS = 2;
    private static final int FLASH_MEMORY_SLOTS = 1;
    private static final int CPU_SLOTS = 1;
    private static final int CARD_SLOTS = 2;

    private final TabletState state;
    private final boolean isClientSide;


    public TabletItemStackHandlers(TabletState state, boolean isClientSide) {
        super(
                new GroupDefinition(DeviceTypes.MEMORY, MEMORY_SLOTS),
                new GroupDefinition(DeviceTypes.HARD_DRIVE, HARD_DRIVE_SLOTS),
                new GroupDefinition(DeviceTypes.FLASH_MEMORY, FLASH_MEMORY_SLOTS),
                new GroupDefinition(DeviceTypes.CARD, CARD_SLOTS),
                new GroupDefinition(DeviceTypes.CPU, CPU_SLOTS));

        this.state = state;
        this.isClientSide = isClientSide;
    }

    @Override
    protected ItemDeviceQuery makeQuery(final ItemStack stack) {
        return Devices.makeQuery(state, stack);
    }

    @Override
    protected void onChanged() {
        super.onChanged();
        if (!isClientSide) {
            state.getBusElement().scheduleScan();

            if (state.getTerminalUser() != null) {
                CompoundTag tag = new CompoundTag();
                tag.put(ITEMS_TAG_NAME, state.getDeviceItems().saveItems());

                Network.sendToClient(new TabletItemsMessage(state.getId(), tag), state.getTerminalUser());
            }
        }
    }
}

