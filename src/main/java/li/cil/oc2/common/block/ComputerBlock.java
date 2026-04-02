/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.block;

import com.mojang.serialization.MapCodec;
import li.cil.oc2.api.capabilities.RedstoneEmitter;
import li.cil.oc2.common.components.RestrictedContainer;
import li.cil.oc2.common.config.Config;
import li.cil.oc2.common.blockentity.BlockEntities;
import li.cil.oc2.common.blockentity.ComputerBlockEntity;
import li.cil.oc2.common.blockentity.TickableBlockEntity;
import li.cil.oc2.common.capabilities.Capabilities;
import li.cil.oc2.common.integration.Wrenches;
import li.cil.oc2.common.item.Items;
import li.cil.oc2.common.tags.ItemTags;
import li.cil.oc2.common.util.HorizontalBlockUtils;
import li.cil.oc2.common.util.TooltipUtils;
import li.cil.oc2.common.util.VoxelShapeUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

import static li.cil.oc2.common.util.TranslationUtils.text;

public final class ComputerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    // We bake the "screen" indent on the front into the collision shape, to prevent stuff being
    // placeable on that side, such as network connectors, torches, etc.
    private static final VoxelShape NEG_Z_SHAPE = Shapes.or(
        Block.box(0, 0, 1, 16, 16, 16), // main body
        Block.box(0, 15, 0, 16, 16, 1), // across top
        Block.box(0, 0, 0, 16, 6, 1), // across bottom
        Block.box(0, 0, 0, 1, 16, 1), // up left
        Block.box(15, 0, 0, 16, 16, 1) // up right
    );
    private static final VoxelShape NEG_X_SHAPE = VoxelShapeUtils.rotateHorizontalClockwise(NEG_Z_SHAPE);
    private static final VoxelShape POS_Z_SHAPE = VoxelShapeUtils.rotateHorizontalClockwise(NEG_X_SHAPE);
    private static final VoxelShape POS_X_SHAPE = VoxelShapeUtils.rotateHorizontalClockwise(POS_Z_SHAPE);

    ///////////////////////////////////////////////////////////////////

    public ComputerBlock() {
        super(Properties
            .of()
            .mapColor(MapColor.METAL)
            .sound(SoundType.METAL)
            .strength(1.5f, 6.0f));
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<ComputerBlock> codec() {
        return BlockCodecs.COMPUTER.get();
    }

    ///////////////////////////////////////////////////////////////////

    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final List<Component> tooltip, final TooltipFlag advanced) {
        super.appendHoverText(stack, context, tooltip, advanced);
        TooltipUtils.addEnergyConsumption(Config.computerEnergyPerTick, tooltip);
        TooltipUtils.addInventoryInformation(stack, tooltip);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isSignalSource(final BlockState state) {
        return true;
    }

    @Override
    public int getSignal(final BlockState state, final BlockGetter blockGetter, final BlockPos pos, final Direction side) {
        final BlockEntity blockEntity = blockGetter.getBlockEntity(pos);
        if (blockEntity != null) {
            var level = blockEntity.getLevel();
            if (level != null) {
                // Redstone requests info for faces with external perspective. Capabilities treat
                // the Direction from an internal and local perspective, so flip it, and transform it from global to
                // local.
                var cap = level.getCapability(Capabilities.RedstoneEmitter.BLOCK, blockEntity.getBlockPos(), null, blockEntity, HorizontalBlockUtils.toLocal(state, side.getOpposite()));
                return Optional.ofNullable(cap)
                    .map(RedstoneEmitter::getRedstoneOutput)
                    .orElse(0);
            }
        }

        return super.getSignal(state, blockGetter, pos, side);
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getDirectSignal(final BlockState state, final BlockGetter level, final BlockPos pos, final Direction side) {
        return getSignal(state, level, pos, side);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(final BlockState state, final Level level, final BlockPos pos, final Block changedBlock, final BlockPos changedBlockPos, final boolean isMoving) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof final ComputerBlockEntity computer) {
            computer.handleNeighborChanged();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NEG_Z_SHAPE;
            case SOUTH -> POS_Z_SHAPE;
            case WEST -> NEG_X_SHAPE;
            default -> POS_X_SHAPE;
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level, final BlockPos pos, final Player player, final InteractionHand hand, final BlockHitResult hitResult) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof final ComputerBlockEntity computer)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }

        if (Wrenches.isWrench(stack)) {
            if (!player.isShiftKeyDown()) {
                if (!level.isClientSide() && player instanceof final ServerPlayer serverPlayer) {
                    computer.openInventoryScreen(serverPlayer);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof final ComputerBlockEntity computer)) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }

        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                computer.start();
            } else if (player instanceof final ServerPlayer serverPlayer) {
                computer.openTerminalScreen(serverPlayer);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state, final Player player) {
        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!level.isClientSide() && blockEntity instanceof final ComputerBlockEntity computer) {
            if (!computer.getItemStackHandlers().isEmpty()) {
                if (player.isCreative()) {
                    final ItemStack stack = new ItemStack(Items.COMPUTER.get());
                    computer.exportToItemStack(stack);
                    popResource(level, pos, stack);
                }
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return super.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    ///////////////////////////////////////////////////////////////////
    // EntityBlock

    @Nullable
    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return BlockEntities.COMPUTER.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(final Level level, final BlockState state, final BlockEntityType<T> type) {
        return TickableBlockEntity.createTicker(level, type, BlockEntities.COMPUTER.get());
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    ///////////////////////////////////////////////////////////////////

    private static RestrictedContainer emptyRestrictedContainer() {
        var container = new RestrictedContainer();

        container.items().put(ItemTags.DEVICES_FLASH_MEMORY, NonNullList.withSize(1, ItemStack.EMPTY));
        container.items().put(ItemTags.DEVICES_CPU, NonNullList.withSize(1, ItemStack.EMPTY));
        container.items().put(ItemTags.DEVICES_MEMORY, NonNullList.withSize(4, ItemStack.EMPTY));
        container.items().put(ItemTags.DEVICES_CARD, NonNullList.withSize(4, ItemStack.EMPTY));
        container.items().put(ItemTags.DEVICES_HARD_DRIVE, NonNullList.withSize(4, ItemStack.EMPTY));

        return container;
    }

    public static ItemStack getComputerWithFlash() {
        final ItemStack computer = new ItemStack(Items.COMPUTER.get());

        var container = emptyRestrictedContainer();
        container.items().get(ItemTags.DEVICES_FLASH_MEMORY).set(0, new ItemStack(Items.FLASH_MEMORY_CUSTOM.get()));
        computer.set(
            li.cil.oc2.common.components.DataComponents.RESTRICTED_CONTAINER,
            container
        );

        return computer;
    }

    public static ItemStack getPreconfiguredComputer() {
        final ItemStack computer = new ItemStack(Items.COMPUTER.get());

        var container = emptyRestrictedContainer();
        container.items().get(ItemTags.DEVICES_FLASH_MEMORY).set(0, new ItemStack(Items.FLASH_MEMORY_CUSTOM.get()));
        container.items().get(ItemTags.DEVICES_CPU).set(0, new ItemStack(Items.CPU_TIER_3.get()));
        container.items().get(ItemTags.DEVICES_MEMORY).replaceAll(ignored -> new ItemStack(Items.MEMORY_LARGE.get()));
        container.items().get(ItemTags.DEVICES_CARD).set(0, new ItemStack(Items.NETWORK_INTERFACE_CARD.get()));
        container.items().get(ItemTags.DEVICES_HARD_DRIVE).set(0, new ItemStack(Items.HARD_DRIVE_LARGE.get()));
        computer.set(
            li.cil.oc2.common.components.DataComponents.RESTRICTED_CONTAINER,
            container
        );

        computer.set(DataComponents.CUSTOM_NAME, text("block.{mod}.computer.preconfigured"));

        return computer;
    }
}
