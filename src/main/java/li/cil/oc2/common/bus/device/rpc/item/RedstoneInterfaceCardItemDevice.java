/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.bus.device.rpc.item;

import li.cil.oc2.api.API;
import li.cil.oc2.api.bus.device.object.Callback;
import li.cil.oc2.api.bus.device.object.DocumentedDevice;
import li.cil.oc2.api.bus.device.object.Parameter;
import li.cil.oc2.api.capabilities.BundledEmitter;
import li.cil.oc2.api.capabilities.RedstoneEmitter;
import li.cil.oc2.api.util.Side;
import li.cil.oc2.common.Constants;
import li.cil.oc2.common.block.Blocks;
import li.cil.oc2.common.blockentity.ComputerBlockEntity;
import li.cil.oc2.common.capabilities.Capabilities;
import li.cil.oc2.common.integration.util.BundledRedstone;
import li.cil.oc2.common.util.HorizontalBlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@EventBusSubscriber(modid = API.MOD_ID)
public final class RedstoneInterfaceCardItemDevice extends AbstractItemRPCDevice implements DocumentedDevice {
    private static final String OUTPUT_TAG_NAME = "output";
    private static final String BUNDLED_TAG_NAME = "bundled";

    private static final String GET_REDSTONE_INPUT = "getRedstoneInput";
    private static final String GET_REDSTONE_OUTPUT = "getRedstoneOutput";
    private static final String SET_REDSTONE_OUTPUT = "setRedstoneOutput";
    private static final String GET_BUNDLED_INPUT = "getBundledInput";
    private static final String GET_BUNDLED_OUTPUT = "getBundledOutput";
    private static final String SET_BUNDLED_OUTPUT = "setBundledOutput";
    private static final String SET_BUNDLED_OUTPUTS = "setBundledOutputs";
    private static final String SIDE = "side";
    private static final String VALUE = "value";
    private static final String VALUES = "values";
    private static final String COLOUR = "colour";

    ///////////////////////////////////////////////////////////////////

    private final BlockEntity blockEntity;
    private final RedstoneEmitter[] re_capabilities;
    private final BundledEmitter[] be_capabilities;
    private final byte[] output = new byte[Constants.BLOCK_FACE_COUNT];
    private final byte[][] bundled_output = new byte[Constants.BLOCK_FACE_COUNT][Constants.BUNDLE_COLOR_COUNT];

    ///////////////////////////////////////////////////////////////////

    public RedstoneInterfaceCardItemDevice(final ItemStack identity, final BlockEntity blockEntity) {
        super(identity, "redstone");
        this.blockEntity = blockEntity;

        re_capabilities = new RedstoneEmitter[Constants.BLOCK_FACE_COUNT];
        be_capabilities = new BundledEmitter[Constants.BLOCK_FACE_COUNT];
        for (int i = 0; i < Constants.BLOCK_FACE_COUNT; i++) {
            final int indexForClosure = i;
            re_capabilities[i] = () -> output[indexForClosure];
            be_capabilities[i] = () -> bundled_output[indexForClosure];
        }
    }

    ///////////////////////////////////////////////////////////////////

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(
            Capabilities.RedstoneEmitter.BLOCK,
            (level, pos, state, be, side) -> {
                if (side == null) return null;

                if (be instanceof final ComputerBlockEntity computer) {
                    RedstoneInterfaceCardItemDevice self = computer.getFirstDevice(RedstoneInterfaceCardItemDevice.class);
                    if (self == null) return null;

                    final int index = side.get3DDataValue();
                    return self.re_capabilities[index];
                }
                return null;
            },
            Blocks.COMPUTER.get()
        );

        event.registerBlock(
            Capabilities.BundledEmitter.BLOCK,
            (level, pos, state, be, side) -> {
                if (side == null) return null;

                if (be instanceof final ComputerBlockEntity computer) {
                    RedstoneInterfaceCardItemDevice self = computer.getFirstDevice(RedstoneInterfaceCardItemDevice.class);
                    if (self == null) return null;

                    final int index = side.get3DDataValue();
                    return self.be_capabilities[index];
                }
                return null;
            },
            Blocks.COMPUTER.get()
        );
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        final CompoundTag tag = new CompoundTag();
        tag.putByteArray(OUTPUT_TAG_NAME, output);
        CompoundTag tag_bundled_output = new CompoundTag();
        for (Direction dir : Direction.values()) {
            tag_bundled_output.putByteArray(dir.getName(), bundled_output[dir.get3DDataValue()]);
        }
        tag.put(BUNDLED_TAG_NAME, tag_bundled_output);

        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, final CompoundTag tag) {
        final byte[] serializedOutput = tag.getByteArray(OUTPUT_TAG_NAME);
        System.arraycopy(serializedOutput, 0, output, 0, Math.min(serializedOutput.length, output.length));

        final CompoundTag tag_bundled_output = tag.getCompound(BUNDLED_TAG_NAME);
        for (Direction dir : Direction.values()) {
            final byte[] serializedBundledOutput = tag_bundled_output.getByteArray(dir.getName());
            byte[] dest_output = bundled_output[dir.get3DDataValue()];
            System.arraycopy(serializedBundledOutput, 0, dest_output, 0, Math.min(serializedBundledOutput.length, dest_output.length));
        }
    }

    @Callback(name = GET_REDSTONE_INPUT)
    public int getRedstoneInput(@Parameter(SIDE) @Nullable final Side side) {
        if (side == null) throw new IllegalArgumentException();

        final Level level = blockEntity.getLevel();
        if (level == null) {
            return 0;
        }

        final BlockPos pos = blockEntity.getBlockPos();
        final Direction direction = HorizontalBlockUtils.toGlobal(blockEntity.getBlockState(), side);
        assert direction != null;

        final BlockPos neighborPos = pos.relative(direction);
        final ChunkPos chunkPos = new ChunkPos(neighborPos);
        if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
            return 0;
        }

        return level.getSignal(neighborPos, direction);
    }

    @Callback(name = GET_REDSTONE_OUTPUT, synchronize = false)
    public int getRedstoneOutput(@Parameter(SIDE) @Nullable final Side side) {
        if (side == null) throw new IllegalArgumentException();
        final int index = side.getDirection().get3DDataValue();

        return output[index];
    }

    @Callback(name = SET_REDSTONE_OUTPUT)
    public void setRedstoneOutput(@Parameter(SIDE) @Nullable final Side side, @Parameter(VALUE) final int value) {
        if (side == null) throw new IllegalArgumentException();
        final int index = side.getDirection().get3DDataValue();

        final byte clampedValue = (byte) Mth.clamp(value, 0, 15);
        if (clampedValue == output[index]) {
            return;
        }

        output[index] = clampedValue;

        final Direction direction = HorizontalBlockUtils.toGlobal(blockEntity.getBlockState(), side);
        if (direction != null) {
            notifyNeighbor(direction);
        }
    }

    @Nullable
    @Callback(name = GET_BUNDLED_INPUT)
    public byte[] getBundledInput(@Parameter(SIDE) @Nullable final Side side) {
        if(!ModList.get().isLoaded("projectred_transmission")) throw new IllegalStateException();
        if (side == null) throw new IllegalArgumentException();

        final Level level = blockEntity.getLevel();
        if (level != null) {
            BundledRedstone bundledRedstone = BundledRedstone.getInstance();
            if (bundledRedstone.isAvailable()) {
                final byte[] input = bundledRedstone.getBundledInput(level, blockEntity.getBlockPos(), side.getDirection().getOpposite());
                if (input != null) return input;
            }
        }

        return new byte[Constants.BUNDLE_COLOR_COUNT];
    }

    @Callback(name = GET_BUNDLED_OUTPUT)
    public byte[] getBundledOutput(@Parameter(SIDE) @Nullable final Side side) {
        if(!ModList.get().isLoaded("projectred_transmission")) throw new IllegalStateException();
        if (side == null) throw new IllegalArgumentException();

        final int index = side.getDirection().getOpposite().get3DDataValue();
        return bundled_output[index];
    }

    @Callback(name = SET_BUNDLED_OUTPUT)
    public void setBundledOutput(@Parameter(SIDE) @Nullable final Side side, @Parameter(VALUE) final int value, @Parameter(COLOUR) final int color) {
        if(!ModList.get().isLoaded("projectred_transmission")) throw new IllegalStateException();
        if (side == null) throw new IllegalArgumentException();

        boolean changed = false;
        final int index = side.getDirection().getOpposite().get3DDataValue();
        final byte clampedValue = (byte) Mth.clamp(value, 0, 255);
        final byte clampedColor = (byte) Mth.clamp(color, 0, 15);
        /*for (int i=0; i < values.length; i++) {
            final byte clampedValue = (byte) Mth.clamp(values[i], 0, 255);
            if (clampedValue != bundled_output[index][i]) {
                bundled_output[index][i] = clampedValue;
                changed = true;
            }
        }*/

        if (bundled_output[index][clampedColor] != clampedValue) {
            changed = true;
            bundled_output[index][clampedColor] = clampedValue;
        }

        if (changed) {
            final Direction direction = HorizontalBlockUtils.toGlobal(blockEntity.getBlockState(), side);
            if (direction != null) {
                notifyNeighbor(direction);
            }
        }
    }

    @Callback(name = SET_BUNDLED_OUTPUTS)
    public void setBundledOutputs(@Parameter(SIDE) @Nullable final Side side, @Parameter(VALUES) final int[] values) {
        if(!ModList.get().isLoaded("projectred_transmission")) throw new IllegalStateException();
        if (side == null) throw new IllegalArgumentException();

        boolean changed = false;
        final int index = side.getDirection().getOpposite().get3DDataValue();
        for (int i=0; i < values.length; i++) {
            final byte clampedValue = (byte) Mth.clamp(values[i], 0, 255);
            if (clampedValue != bundled_output[index][i]) {
                bundled_output[index][i] = clampedValue;
                changed = true;
            }
        }

        if (changed) {
            final Direction direction = HorizontalBlockUtils.toGlobal(blockEntity.getBlockState(), side);
            if (direction != null) {
                notifyNeighbor(direction);
            }
        }
    }

    @Override
    public void getDeviceDocumentation(final DocumentedDevice.DeviceVisitor visitor) {
        visitor.visitCallback(GET_REDSTONE_INPUT)
            .description("Get the current redstone level received on the specified side. " +
                "Note that if the current output level on the specified side is not " +
                "zero, this will affect the measured level.\n" +
                "Sides may be specified by name or zero-based index. Please note that " +
                "the side depends on the orientation of the device's container.")
            .returnValueDescription("the current received level on the specified side.")
            .parameterDescription(SIDE, "the side to read the input level from.");

        visitor.visitCallback(GET_REDSTONE_OUTPUT)
            .description("Get the current redstone level transmitted on the specified side. " +
                "This will return the value last set via setRedstoneOutput().\n" +
                "Sides may be specified by name or zero-based index. Please note that " +
                "the side depends on the orientation of the device's container.")
            .returnValueDescription("the current transmitted level on the specified side.")
            .parameterDescription(SIDE, "the side to read the output level from.");
        visitor.visitCallback(SET_REDSTONE_OUTPUT)
            .description("Set the new redstone level transmitted on the specified side.\n" +
                "Sides may be specified by name or zero-based index. Please note that " +
                "the side depends on the orientation of the device's container.")
            .parameterDescription(SIDE, "the side to write the output level to.")
            .parameterDescription(VALUE, "the output level to set, will be clamped to [0, 15].");

        if(ModList.get().isLoaded("projectred_transmission"))
        {
            visitor.visitCallback(GET_BUNDLED_INPUT)
                .description("Get the current bundled level received on the specified side.")
                .parameterDescription(SIDE, "the side to read the bundled input level from");
            visitor.visitCallback(GET_BUNDLED_OUTPUT)
                .description("Get the current bundled level sent out on the specified side.")
                .parameterDescription(SIDE, "the side to read the bundled output level from");
            visitor.visitCallback(SET_BUNDLED_OUTPUT)
                .description("Set the new bundled level transmitted for a specific color on the specified side.\n" +
                    "Sides may be specified by name or zero-based index. Please note that " +
                    "the side depends on the orientation of the device.")
                .parameterDescription(SIDE, "the side to write the output level to.")
                .parameterDescription(VALUE, "the output level to set, will be clamped to [0, 255].")
                .parameterDescription(COLOUR, "the colour wire this sets, as int [0, 15]");
            visitor.visitCallback(SET_BUNDLED_OUTPUTS)
                .description("Set the new bundled levels transmitted on the specified side.\n" +
                    "Sides may be specified by name or zero-based index. Please note that " +
                    "the side depends on the orientation of the device.")
                .parameterDescription(SIDE, "the side to write the output level to.")
                .parameterDescription(VALUES, "the output levels to set in array form, each value will be clamped to [0, 255], 16 entries.");
        }
    }

    ///////////////////////////////////////////////////////////////////

    private void notifyNeighbor(final Direction direction) {
        final Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        level.updateNeighborsAt(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock());
        level.updateNeighborsAt(blockEntity.getBlockPos().relative(direction), blockEntity.getBlockState().getBlock());
    }
}
