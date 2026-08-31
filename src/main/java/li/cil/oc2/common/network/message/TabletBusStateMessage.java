/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.network.message;

import java.util.UUID;

import li.cil.oc2.client.TabletStateCache;
import li.cil.oc2.common.bus.CommonDeviceBusController;
import li.cil.oc2.common.vm.tablet.TabletState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public final class TabletBusStateMessage extends AbstractMessage {
    private UUID id;
    private CommonDeviceBusController.BusState value;

    ///////////////////////////////////////////////////////////////////

    public TabletBusStateMessage(final UUID id, final CommonDeviceBusController.BusState value) {
        this.id = id;
        this.value = value;
    }

    public TabletBusStateMessage(final FriendlyByteBuf buffer) {
        super(buffer);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    public void fromBytes(final FriendlyByteBuf buffer) {
        id = buffer.readUUID();
        value = buffer.readEnum(CommonDeviceBusController.BusState.class);
    }

    @Override
    public void toBytes(final FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeEnum(value);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    protected void handleMessage(final NetworkEvent.Context context) {
        TabletState tablet = TabletStateCache.getOrCreateTabletState(id);
        tablet.getVirtualMachine().setBusStateClient(value);
    }
}


