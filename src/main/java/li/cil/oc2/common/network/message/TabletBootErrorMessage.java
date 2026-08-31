/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.network.message;

import java.util.UUID;

import javax.annotation.Nullable;

import li.cil.oc2.client.TabletStateCache;
import li.cil.oc2.common.vm.tablet.TabletState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

public final class TabletBootErrorMessage extends AbstractMessage {
    private UUID id;
    private Component value;

    ///////////////////////////////////////////////////////////////////

    public TabletBootErrorMessage(final UUID id, @Nullable final Component value) {
        this.id = id;
        this.value = value;
    }

    public TabletBootErrorMessage(final FriendlyByteBuf buffer) {
        super(buffer);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    public void fromBytes(final FriendlyByteBuf buffer) {
        id = buffer.readUUID();
        value = buffer.readComponent();
    }

    @Override
    public void toBytes(final FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeComponent(value);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    protected void handleMessage(final NetworkEvent.Context context) {
        TabletState tablet = TabletStateCache.getOrCreateTabletState(id);
        tablet.getVirtualMachine().setBootErrorClient(value);
    }
}

