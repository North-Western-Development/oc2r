/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.network.message;

import static li.cil.oc2.common.container.AbstractTabletContainer.getTabletState;

import java.nio.ByteBuffer;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class TabletTerminalInputMessage extends AbstractTerminalItemMessage {
    public TabletTerminalInputMessage(final UUID id, final ByteBuffer data) {
        super(id, data);
    }

    public TabletTerminalInputMessage(final FriendlyByteBuf buffer) {
        super(buffer);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    protected void handleMessage(final NetworkEvent.Context context) {
        final ServerPlayer player = context.getSender();
        if (player != null) {
            getTabletState(id, player).getTerminal().putInput(ByteBuffer.wrap(data));
        }
    }
}
