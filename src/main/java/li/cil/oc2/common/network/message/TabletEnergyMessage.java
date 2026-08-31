/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.network.message;

import java.util.UUID;

import li.cil.oc2.client.TabletStateCache;
import li.cil.oc2.common.vm.tablet.TabletState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import static li.cil.oc2.common.Constants.*;

public final class TabletEnergyMessage extends AbstractMessage {
    private UUID id;
    private CompoundTag tag;

    ///////////////////////////////////////////////////////////////////

    public TabletEnergyMessage(final UUID id, final CompoundTag tag) {
        this.id = id;
        this.tag = tag;
    }

    public TabletEnergyMessage(final FriendlyByteBuf buffer) {
        super(buffer);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    public void fromBytes(final FriendlyByteBuf buffer) {
        id = buffer.readUUID();
        tag = buffer.readNbt();
    }

    @Override
    public void toBytes(final FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeNbt(tag);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    protected void handleMessage(final NetworkEvent.Context context) {
        TabletState tablet = TabletStateCache.getOrCreateTabletState(id);

        tablet.getEnergy().deserializeNBT(tag.getCompound(ENERGY_TAG_NAME));
    }
}




