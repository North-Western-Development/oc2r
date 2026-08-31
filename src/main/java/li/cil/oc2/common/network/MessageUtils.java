/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.network;

import li.cil.oc2.common.util.LevelUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import li.cil.oc2.common.item.TabletItem;

public final class MessageUtils {
    public static <T extends BlockEntity> void withNearbyServerBlockEntityForInteraction(final NetworkEvent.Context context, final BlockPos pos, final Class<T> type, final BiConsumer<ServerPlayer, T> callback) {
        final ServerPlayer player = context.getSender();
        if (player == null) { // || !pos.closerToCenterThan(player.position(), 8)) {
            return;
        }

        withNearbyServerBlockEntity(context, pos, type, callback);
    }

    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> void withNearbyServerBlockEntity(final NetworkEvent.Context context, final BlockPos pos, final Class<T> type, final BiConsumer<ServerPlayer, T> callback) {
        final ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }

        final ServerLevel level = player.getServer().getLevel(player.level().dimension());
        final BlockEntity blockEntity = LevelUtils.getBlockEntityIfChunkExists(level, pos);
        if (type.isInstance(blockEntity)) {
            callback.accept(player, (T) blockEntity);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> void withServerEntity(final NetworkEvent.Context context, final int id, final Class<T> type, final Consumer<T> callback) {
        final ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }

        final ServerLevel level = player.getServer().getLevel(player.level().dimension());
        final Entity entity = level.getEntity(id);
        if (type.isInstance(entity)) {
            callback.accept((T) entity);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> void withNearbyServerEntity(final NetworkEvent.Context context, final int id, final Class<T> type, final Consumer<T> callback) {
        final ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }

        final ServerLevel level = player.getServer().getLevel(player.level().dimension());
        final Entity entity = level.getEntity(id);
        if (type.isInstance(entity) && entity.closerThan(player, 8)) {
            callback.accept((T) entity);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> void withClientBlockEntityAt(final BlockPos pos, final Class<T> type, final Consumer<T> callback) {
        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (type.isInstance(blockEntity)) {
            callback.accept((T) blockEntity);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> void withClientEntity(final int id, final Class<T> type, final Consumer<T> callback) {
        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        final Entity entity = level.getEntity(id);
        if (type.isInstance(entity)) {
            callback.accept((T) entity);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Item> void withInventoryItem(final NetworkEvent.Context context, final UUID id,
            Class<T> type, final BiConsumer<T, ItemStack> callback) {
        final ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }

        final ItemStack item = findItemByUUID(player, id);

        final Item tablet = item.getItem();

        if (item != ItemStack.EMPTY && type.isInstance(item.getItem())) {
            callback.accept((T) tablet, item);
        }
    }

    public static void withClientInventoryItem(final NetworkEvent.Context context, final UUID id,
            final Consumer<ItemStack> callback) {
        final Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        final ItemStack item = findItemByUUID(player, id);

        if (item != ItemStack.EMPTY) {
            callback.accept(item);
        }
    }

    public static ItemStack findItemByUUID(Player player, UUID uuid) {

        for (ItemStack stack : player.getInventory().items) {
            if (matches(stack, uuid))
                return stack;
        }

        for (ItemStack stack : player.getInventory().offhand) {
            if (matches(stack, uuid))
                return stack;
        }

        return ItemStack.EMPTY;
    }

    private static boolean matches(ItemStack stack, UUID uuid) {
        if (stack.isEmpty())
            return false;

        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TabletItem.UUID_TAG_NAME) && tag.getUUID(TabletItem.UUID_TAG_NAME).equals(uuid);
    }
}
