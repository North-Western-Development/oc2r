/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.blockentity;
import li.cil.oc2.api.bus.device.object.Callback;
import li.cil.oc2.api.bus.device.object.DocumentedDevice;
import li.cil.oc2.api.bus.device.object.NamedDevice;
import li.cil.oc2.api.bus.device.object.Parameter;
import li.cil.oc2.api.util.Side;
import li.cil.oc2.common.Constants;
import li.cil.oc2.common.integration.util.BundledRedstone;
import li.cil.oc2.common.util.HorizontalBlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import java.util.*;
import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import li.cil.oc2.api.bus.device.rpc.*;
import static java.util.Collections.singletonList;

@SuppressWarnings("unused")
public final class RedstoneInterfaceBlockEntity extends ModBlockEntity implements NamedDevice, DocumentedDevice, RPCEventSource {
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

    private final HashMap<IEventSink, UUID> subscribers = new HashMap<>();

    ///////////////////////////////////////////////////////////////////

    private final byte[] output = new byte[Constants.BLOCK_FACE_COUNT];
    private final byte[][] bundled_output = new byte[Constants.BLOCK_FACE_COUNT][16];

    ///////////////////////////////////////////////////////////////////

    public RedstoneInterfaceBlockEntity(final BlockPos pos, final BlockState state) {
        super(BlockEntities.REDSTONE_INTERFACE.get(), pos, state);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    protected void saveAdditional(final CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        tag.putByteArray(OUTPUT_TAG_NAME, output);
        CompoundTag tag_bundled_output = new CompoundTag();
        for (Direction dir : Direction.values()) {
            tag_bundled_output.putByteArray(dir.getName(), bundled_output[dir.get3DDataValue()]);
        }
        tag.put(BUNDLED_TAG_NAME, tag_bundled_output);
    }

    @Override
    public void loadAdditional(final CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        final byte[] serializedOutput = tag.getByteArray(OUTPUT_TAG_NAME);
        System.arraycopy(serializedOutput, 0, output, 0, Math.min(serializedOutput.length, output.length));

        final CompoundTag tag_bundled_output = tag.getCompound(BUNDLED_TAG_NAME);
        for (Direction dir : Direction.values()) {
            final byte[] serializedBundledOutput = tag_bundled_output.getByteArray(dir.getName());
            byte[] dest_output = bundled_output[dir.get3DDataValue()];
            System.arraycopy(serializedBundledOutput, 0, dest_output, 0, Math.min(serializedBundledOutput.length, dest_output.length));
        }
    }

    public int getOutputForDirection(final Direction direction) {
        final Direction localDirection = HorizontalBlockUtils.toLocal(getBlockState(), direction);
        assert localDirection != null;

        return output[localDirection.get3DDataValue()];
    }

    @Callback(name = GET_REDSTONE_INPUT)
    public int getRedstoneInput(@Parameter(SIDE) @Nullable final Side side) {
        if (side == null) throw new IllegalArgumentException();

        if (level == null) {
            return 0;
        }

        final BlockPos pos = getBlockPos();
        final Direction direction = HorizontalBlockUtils.toGlobal(getBlockState(), side);
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
    public void setRedstoneOutput(@Parameter(SIDE) @Nullable final Side side, @Parameter(VALUES) final int value) {
        if (side == null) throw new IllegalArgumentException();
        final int index = side.getDirection().get3DDataValue();

        final byte clampedValue = (byte) Mth.clamp(value, 0, 15);
        if (clampedValue == output[index]) {
            return;
        }

        output[index] = clampedValue;

        final Direction direction = HorizontalBlockUtils.toGlobal(getBlockState(), side);
        if (direction != null) {
            notifyNeighbor(direction);
        }

        setChanged();
    }

    @Nullable
    @Callback(name = GET_BUNDLED_INPUT)
    public byte[] getBundledInput(@Parameter(SIDE) @Nullable final Side side) {
        if(!ModList.get().isLoaded("projectred_transmission")) throw new IllegalStateException();
        if (side == null) throw new IllegalArgumentException();

        BundledRedstone bundledRedstone = BundledRedstone.getInstance();
        if (bundledRedstone.isAvailable()) {
            final byte[] input = bundledRedstone.getBundledInput(this.level, this.getBlockPos(), side.getDirection().getOpposite());
            if (input != null) return input;
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
            final Direction direction = HorizontalBlockUtils.toGlobal(getBlockState(), side);
            if (direction != null) {
                notifyNeighbor(direction);
            }

            setChanged();
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
            final Direction direction = HorizontalBlockUtils.toGlobal(getBlockState(), side);
            if (direction != null) {
                notifyNeighbor(direction);
            }

            setChanged();
        }
    }

    @Override
    public Collection<String> getDeviceTypeNames() {
        return singletonList("redstone");
    }

    @Override
    public void getDeviceDocumentation(final DeviceVisitor visitor) {
        visitor.visitCallback(GET_REDSTONE_INPUT)
            .description("Get the current redstone level received on the specified side. " +
                "Note that if the current output level on the specified side is not " +
                "zero, this will affect the measured level.\n" +
                "Sides may be specified by name or zero-based index. Please note that " +
                "the side depends on the orientation of the device.")
            .returnValueDescription("the current received level on the specified side.")
            .parameterDescription(SIDE, "the side to read the input level from.");

        visitor.visitCallback(GET_REDSTONE_OUTPUT)
            .description("Get the current redstone level transmitted on the specified side. " +
                "This will return the value last set via setRedstoneOutput().\n" +
                "Sides may be specified by name or zero-based index. Please note that " +
                "the side depends on the orientation of the device.")
            .returnValueDescription("the current transmitted level on the specified side.")
            .parameterDescription(SIDE, "the side to read the output level from.");
        visitor.visitCallback(SET_REDSTONE_OUTPUT)
            .description("Set the new redstone level transmitted on the specified side.\n" +
                "Sides may be specified by name or zero-based index. Please note that " +
                "the side depends on the orientation of the device.")
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
        if (level == null) {
            return;
        }

        level.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
        level.updateNeighborsAt(getBlockPos().relative(direction), getBlockState().getBlock());
    }

    @Nullable
    public byte[] getBundledSignal(Direction direction) {
        final int index = direction.get3DDataValue();
        return this.bundled_output[index];
    }

    @Override
    public void subscribe(IEventSink sink, UUID myid) {
        subscribers.put(sink, myid);
    }
    @Override
    public void unsubscribe(IEventSink sink) {
        subscribers.remove(sink);
    }

    public void neighborChanged(BlockPos fromPos) {
        int sl;
        if (level == null) {
            return;
        }

        final BlockPos pos = getBlockPos();
        final Direction direction = Side.relativeDirection(pos, fromPos);
        assert direction != null;

        final ChunkPos chunkPos = new ChunkPos(fromPos);
        if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
            sl = 0;
        } else {
            sl = level.getSignal(fromPos, direction);
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("event", "redstone");
        msg.addProperty("side", ""+direction);
        msg.addProperty("level", sl);

        for (var subscriber : subscribers.entrySet()) {
            subscriber.getKey().postEvent(subscriber.getValue(), msg);
        }
    }
}
