/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.network.message;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import li.cil.oc2.client.TabletStateCache;
import li.cil.oc2.common.serialization.NBTSerialization;
import li.cil.oc2.common.vm.tablet.TabletState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public final class TabletClientUpdateMessage extends AbstractMessage {
    private static final String TERMINAL_TAG_NAME = "terminal";

    private UUID id;
    private byte[] termBuffer;

    ///////////////////////////////////////////////////////////////////

    public TabletClientUpdateMessage(final UUID id, final byte[] termBuffer) {
        this.id = id;
        this.termBuffer = termBuffer;
    }

    public TabletClientUpdateMessage(final FriendlyByteBuf buffer) {
        super(buffer);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    public void fromBytes(final FriendlyByteBuf buffer) {
        id = buffer.readUUID();
        termBuffer = buffer.readByteArray();
    }

    @Override
    public void toBytes(final FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeByteArray(termBuffer);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    protected void handleMessage(final NetworkEvent.Context context) {
        TabletState tablet = TabletStateCache.getOrCreateTabletState(id);

        try (ByteArrayInputStream in = new ByteArrayInputStream(termBuffer)) {
            CompoundTag tag = NbtIo.readCompressed(in);

            NBTSerialization.deserialize(tag.getCompound(TERMINAL_TAG_NAME), tablet.getTerminal());
        } catch (Exception e) {
            System.out.println("Failed to load terminal state from message.");
        }
    }
}





