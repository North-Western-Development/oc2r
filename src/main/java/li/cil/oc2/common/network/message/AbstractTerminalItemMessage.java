/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.network.message;

import java.nio.ByteBuffer;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;

public abstract class AbstractTerminalItemMessage extends AbstractMessage {
    protected UUID id;
    protected byte[] data;

    ///////////////////////////////////////////////////////////////////

    public AbstractTerminalItemMessage(final UUID id, final ByteBuffer data) {
        this.id = id;
        this.data = data.array();
    }

    public AbstractTerminalItemMessage(final FriendlyByteBuf buffer) {
        super(buffer);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    public void fromBytes(final FriendlyByteBuf buffer) {
        id = buffer.readUUID();
        data = buffer.readByteArray();
    }

    @Override
    public void toBytes(final FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeByteArray(data);
    }
}
