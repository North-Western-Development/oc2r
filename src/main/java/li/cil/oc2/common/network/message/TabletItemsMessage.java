/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.network.message;

import java.util.UUID;

import li.cil.oc2.client.TabletStateCache;
import li.cil.oc2.common.item.TabletItem;
import li.cil.oc2.common.network.MessageUtils;
import li.cil.oc2.common.util.NBTUtils;
import li.cil.oc2.common.vm.tablet.TabletState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import static li.cil.oc2.common.Constants.*;

public final class TabletItemsMessage extends AbstractMessage {
    private UUID id;
    private CompoundTag tag;

    ///////////////////////////////////////////////////////////////////

    public TabletItemsMessage(final UUID id, final CompoundTag tag) {
        this.id = id;
        this.tag = tag;
    }

    public TabletItemsMessage(final FriendlyByteBuf buffer) {
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

        tablet.getDeviceItems().loadItems(tag.getCompound(ITEMS_TAG_NAME));

        MessageUtils.withClientInventoryItem(context, id, stack -> {
            stack.getTag().getCompound(MOD_TAG_NAME).remove(ITEMS_TAG_NAME);
            final CompoundTag itemsTag = NBTUtils.getOrCreateChildTag(stack.getOrCreateTag(), MOD_TAG_NAME, ITEMS_TAG_NAME);
            tablet.getDeviceItems().saveItems(itemsTag); // Puts one tag per device type, as expected by TooltipUtils.
            stack.getTag().putBoolean(TabletItem.TABLET_ON_TAG_NAME, tag.getBoolean(TabletItem.TABLET_ON_TAG_NAME));
        });
    }
}





