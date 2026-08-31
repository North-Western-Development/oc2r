/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.network.message;

import java.nio.ByteBuffer;
import java.util.UUID;

import li.cil.oc2.client.TabletStateCache;
import li.cil.oc2.common.vm.tablet.TabletState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public final class TabletTerminalOutputMessage extends AbstractTerminalItemMessage {
    public TabletTerminalOutputMessage(final UUID id, final ByteBuffer data) {
        super(id, data);
    }

    public TabletTerminalOutputMessage(final FriendlyByteBuf buffer) {
        super(buffer);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    protected void handleMessage(final NetworkEvent.Context context) {
        TabletState tablet = TabletStateCache.getOrCreateTabletState(id);
        tablet.getTerminal().putOutput(ByteBuffer.wrap(data));
    }
}
