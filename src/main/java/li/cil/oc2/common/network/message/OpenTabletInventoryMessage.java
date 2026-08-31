/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.network.message;

import java.util.UUID;

import li.cil.oc2.common.item.TabletItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public final class OpenTabletInventoryMessage extends AbstractMessage {
    private UUID id;

    ///////////////////////////////////////////////////////////////////

    public OpenTabletInventoryMessage(final UUID id) {
        this.id = id;
    }

    public OpenTabletInventoryMessage(final FriendlyByteBuf buffer) {
        super(buffer);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    public void fromBytes(final FriendlyByteBuf buffer) {
        id = buffer.readUUID();
    }

    @Override
    public void toBytes(final FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    protected void handleMessage(final NetworkEvent.Context context) {
        final ServerPlayer player = context.getSender();
        if (player != null) {
            TabletItem.openInventoryScreen(player, id);
        }
    }
}
