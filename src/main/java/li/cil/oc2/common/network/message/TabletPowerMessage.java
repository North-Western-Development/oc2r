/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.network.message;

import java.util.UUID;

import li.cil.oc2.common.item.TabletItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class TabletPowerMessage extends AbstractMessage {
    private UUID id;
    private boolean power;

    ///////////////////////////////////////////////////////////////////

    public TabletPowerMessage(final UUID id, final boolean power) {
        this.id = id;
        this.power = power;
    }

    public TabletPowerMessage(final FriendlyByteBuf buffer) {
        super(buffer);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    public void fromBytes(final FriendlyByteBuf buffer) {
        id = buffer.readUUID();
        power = buffer.readBoolean();
    }

    @Override
    public void toBytes(final FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeBoolean(power);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    protected void handleMessage(final NetworkEvent.Context context) {
        final ServerPlayer player = context.getSender();
        if (player != null) {
            if (power) {
                TabletItem.start(player.level(), id);
            } else {
                TabletItem.stop(player.level(), id);
            }
        }
    }
}

