/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.bus.device.rpc.item;

import li.cil.oc2.api.bus.device.object.Callback;
import li.cil.oc2.api.bus.device.object.Parameter;
import li.cil.oc2.api.capabilities.Robot;
import li.cil.oc2.api.util.RobotOperationSide;
import li.cil.oc2.common.config.Config;
import li.cil.oc2.common.util.FakePlayerUtils;
import li.cil.oc2.common.util.TickUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;

public final class BlockOperationsModuleDevice extends AbstractItemRPCDevice {
    private static final String LAST_OPERATION_TAG_NAME = "cooldown";

    private static final int COOLDOWN = TickUtils.toTicks(Duration.ofMillis((long) (Config.robotCooldown * 1000)));

    ///////////////////////////////////////////////////////////////////

    private final Entity entity;
    private final Robot robot;
    private long lastOperation;

    ///////////////////////////////////////////////////////////////////

    public BlockOperationsModuleDevice(final ItemStack identity, final Entity entity, final Robot robot) {
        super(identity, "block_operations");
        this.entity = entity;
        this.robot = robot;
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    public CompoundTag serializeNBT() {
        final CompoundTag tag = new CompoundTag();
        tag.putLong(LAST_OPERATION_TAG_NAME, lastOperation);
        return tag;
    }

    @Override
    public void deserializeNBT(final CompoundTag tag) {
        lastOperation = (long) Mth.clamp(tag.getLong(LAST_OPERATION_TAG_NAME), 0, entity.level().getGameTime());
    }

    @Callback
    public boolean excavate() {
        return excavate(null);
    }

    @Callback
    public boolean excavate(@Parameter("side") @Nullable final RobotOperationSide side) {
        if (isOnCooldown()) {
            return false;
        }

        final Level level = entity.level();
        if (!(level instanceof final ServerLevel serverLevel)) {
            beginCooldown();
            return false;
        }

        final int selectedSlot = robot.getSelectedSlot(); // Get once to avoid change due to threading.
        final ItemStackHandler inventory = robot.getInventory();

        final List<ItemEntity> oldItems = getItemsInRange();

        final Direction direction = RobotOperationSide.toGlobal(entity, side);
        final BlockPos pos = entity.blockPosition().relative(direction);
        final int cooldown = tryHarvestBlock(serverLevel, pos);
        if (cooldown < 0) {
            beginCooldown();
            return false;
        }
        beginCooldown(cooldown);

        final List<ItemEntity> droppedItems = getItemsInRange();
        droppedItems.removeAll(oldItems);

        for (final ItemEntity itemEntity : droppedItems) {
            ItemStack stack = itemEntity.getItem();
            stack = insertStartingAt(inventory, stack, selectedSlot, false);
            itemEntity.setItem(stack);
        }

        return true;
    }

    @Callback
    public boolean place() {
        return place(null);
    }

    @Callback
    public boolean place(@Parameter("side") @Nullable final RobotOperationSide side) {
        if (isOnCooldown()) {
            return false;
        }

        beginCooldown();

        final Level level = entity.level();
        if (!(level instanceof final ServerLevel serverLevel)) {
            return false;
        }

        final int selectedSlot = robot.getSelectedSlot(); // Get once to avoid change due to threading.
        final ItemStackHandler inventory = robot.getInventory();

        final ItemStack extracted = inventory.extractItem(selectedSlot, 1, true);
        if (extracted.isEmpty() || !(extracted.getItem() instanceof final BlockItem blockItem)) {
            return false;
        }

        final Direction direction = RobotOperationSide.toGlobal(entity, side);
        final BlockPos blockPos = entity.blockPosition().relative(direction);
        final Direction oppositeDirection = direction.getOpposite();
        final BlockHitResult hit = new BlockHitResult(
            Vec3.atCenterOf(blockPos).add(Vec3.atCenterOf(oppositeDirection.getNormal()).scale(0.5)),
            oppositeDirection,
            blockPos,
            false);

        final ItemStack itemStack = extracted.copy();
        final ServerPlayer player = FakePlayerUtils.getFakePlayer(serverLevel, entity);
        final BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, itemStack, hit);

        final InteractionResult result = blockItem.place(context);
        if (!result.consumesAction()) {
            return false;
        }

        if (itemStack.isEmpty()) {
            inventory.extractItem(selectedSlot, 1, false);
        }

        return true;
    }

    @Callback(synchronize = false)
    public int durability() {
        return identity.getMaxDamage() - identity.getDamageValue();
    }

    @Callback
    public boolean repair() {
        if (isOnCooldown()) {
            return false;
        }

        beginCooldown();

        if (identity.getDamageValue() == 0) {
            return false;
        }

        final int selectedSlot = robot.getSelectedSlot(); // Get once to avoid change due to threading.
        final ItemStackHandler inventory = robot.getInventory();

        final ItemStack extracted = inventory.extractItem(selectedSlot, 1, true);

        final Tier tier = getRepairItemTier(extracted);
        if (tier == null) {
            return false;
        }

        final int repairValue = tier.getUses();
        if (repairValue == 0) {
            return false;
        }

        // Extra check just to ease my paranoia.
        if (inventory.extractItem(selectedSlot, 1, false).isEmpty()) {
            return false;
        }

        identity.setDamageValue(identity.getDamageValue() - repairValue);

        return true;
    }

    ///////////////////////////////////////////////////////////////////

    private void beginCooldown() {
        beginCooldown(COOLDOWN);
    }

    private void beginCooldown(int cooldown) {
        lastOperation = entity.level().getGameTime() + cooldown;
    }

    @Callback
    public boolean isOnCooldown() {
        return getCooldown() > 0;
    }

    @Callback
    public int getCooldown() {
        return (int) (-Math.min(0, entity.level().getGameTime() - lastOperation) / 20.0); // Convert to seconds for convenience
    }

    private List<ItemEntity> getItemsInRange() {
        return entity.level().getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(2));
    }

    private int tryHarvestBlock(final ServerLevel level, final BlockPos blockPos) {
        // This method is based on PlayerInteractionManager::tryHarvestBlock. Simplified for our needs.
        final BlockState blockState = level.getBlockState(blockPos);
        if (blockState.isAir()) {
            return -1;
        }

        final ServerPlayer player = FakePlayerUtils.getFakePlayer(level, entity);
        final int experience = net.minecraftforge.common.ForgeHooks.onBlockBreakEvent(level, GameType.DEFAULT_MODE, player, blockPos);
        if (experience == -1) {
            return -1;
        }

        final BlockEntity blockEntity = level.getBlockEntity(blockPos);
        final Block block = blockState.getBlock();
        if (block.defaultDestroyTime() < 0) {
            return -1;
        }

        final boolean isCommandBlock = block instanceof CommandBlock || block instanceof StructureBlock || block instanceof JigsawBlock;
        if (isCommandBlock && !player.canUseGameMasterBlocks()) {
            return -1;
        }

        if (player.blockActionRestricted(level, blockPos, GameType.DEFAULT_MODE)) {
            return -1;
        }

        final Tier toolTier = TierSortingRegistry.byName(Config.blockOperationsModuleToolTier);
        if (toolTier == null || !TierSortingRegistry.isCorrectTierForDrops(toolTier, blockState)) {
            return -1;
        }

        if (!ForgeEventFactory.doPlayerHarvestCheck(player, blockState, true)) {
            return -1;
        }

        if (identity.hurt(1, level.random, null)) {
            return -1;
        }

        if (!blockState.onDestroyedByPlayer(level, blockPos, player, true, level.getFluidState(blockPos))) {
            return -1;
        }

        final int cooldown = Math.round(block.defaultDestroyTime() * 30 / toolTier.getSpeed());
        block.destroy(level, blockPos, blockState);
        block.playerDestroy(level, player, blockPos, blockState, blockEntity, ItemStack.EMPTY);

        return cooldown;
    }

    @Nullable
    private Tier getRepairItemTier(final ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        if (stack.getItem() instanceof final TieredItem tieredItem) {
            return tieredItem.getTier();
        }

        return null;
    }

    private ItemStack insertStartingAt(final IItemHandler handler, ItemStack stack, final int startSlot, final boolean simulate) {
        for (int i = 0; i < handler.getSlots(); i++) {
            final int slot = (startSlot + i) % handler.getSlots();
            stack = handler.insertItem(slot, stack, simulate);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        return stack;
    }
}
