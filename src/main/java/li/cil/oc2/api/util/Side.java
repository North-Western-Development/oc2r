/* SPDX-License-Identifier: MIT */

package li.cil.oc2.api.util;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

import com.google.gson.annotations.SerializedName;

/**
 * This enum indicates a side of a block device. It can be either a local side (eg. {@code FRONT}) or a global side
 * (eg. {@code SOUTH}.
 * <p>
 * It is intended to be used by {@link li.cil.oc2.api.bus.device.rpc.RPCDevice} APIs,
 * providing both convenience for the caller by providing a range of aliases, and also
 * stability, in case Mojang decide to rename the enum fields of the {@link Direction}
 * enum at some time in the future.
 */
public enum Side {
    // Vertical: Primarily global but also used for local
    @SerializedName(value="down", alternate={"BOTTOM", "bottom", "DOWN", "d"}) DOWN(Direction.DOWN),
    @SerializedName(value="up", alternate={"TOP", "top", "UP", "u"}) UP(Direction.UP),

    // Horizontal, global only
    @SerializedName(value="north", alternate={"NORTH", "n"}) NORTH(Direction.NORTH),
    @SerializedName(value="south", alternate={"SOUTH", "s"}) SOUTH(Direction.SOUTH),
    @SerializedName(value="west", alternate={"WEST", "w"}) WEST(Direction.WEST),
    @SerializedName(value="east", alternate={"EAST", "e"}) EAST(Direction.EAST),

    // Horizontal, local only
    @SerializedName(value="back", alternate={"BACK", "b"}) BACK(Direction.NORTH, true),
    @SerializedName(value="front", alternate={"FRONT", "f"}) FRONT(Direction.SOUTH, true),
    @SerializedName(value="left", alternate={"LEFT", "l"}) LEFT(Direction.WEST, true),
    @SerializedName(value="right", alternate={"RIGHT", "r"}) RIGHT(Direction.EAST, true),
    ;

    // Static fields
    public static final int VERTICAL_DIRECTION_COUNT = 2;
    public static final int HORIZONTAL_DIRECTION_COUNT = 4;
    public static final int GLOBAL_DIRECTION_COUNT = VERTICAL_DIRECTION_COUNT + HORIZONTAL_DIRECTION_COUNT;

    // Note: Ideally these should be calculated not hardcoded
    private static final Side[] BY_LOCAL_INDEX = {DOWN, UP, BACK, FRONT, LEFT, RIGHT};
    private static final Side[] BY_GLOBAL_INDEX = {DOWN, UP, NORTH, SOUTH, WEST, EAST};
    private static final Side[] BY_LOCAL_2D_INDEX = {FRONT, LEFT, BACK, RIGHT};

    // Instance fields
    private final Direction direction;
    private final boolean local;

    // Constructors
    Side(final Direction direction) {
        this(direction, false);
    }

    Side(final Direction direction, final boolean local) {
        this.direction = direction;
        this.local = local;
    }

    // Getters
    /**
     * Get the base minecraft {@link Direction} this block {@code Side} is built from.
     * <p>
     * Note that this method does not understand rotation.  If this is a relative {@code Side} (eg. {@code FRONT}), then
     * the return value will only make sense for a block facing South.
     *
     * @return The base absolute direction this Side is built from.
     */
    public Direction getBaseDirection() {
        return direction;
    }

    /**
     * Whether this specifies a local direction that changes with 2d orientation (eg {@code FRONT}, or a fixed global
     * direction (eg {code #SOUTH}).
     * <p>
     * Note that {@code UP} and {@code DOWN} can usually be used as local directions but are technically global.
     */
    public boolean isLocal() {
        return local;
    }

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }

    // Global/local conversions
    /**
     * Given a global {@link Direction}, get the corresponding global block {@code Side}
     *
     * @see #toGlobal(BlockState)
     * @param direction A global {@link Direction}
     * @return A global Side corresponding to the same direction
     */
    public static Side fromGlobal(Direction direction) {
        return BY_GLOBAL_INDEX[direction.get3DDataValue()];
    }

    /**
     * Given a local index value, get the corresponding local block {@code Side}. This is roughly equivalent to
     * {@link Direction#from3DDataValue(int)} but for local instead of global sides.
     * <p>
     * The local index value can be used as an index into arrays for data that should be stored per local side of a
     * block. The order of indices is down, up, back, front, left, right.
     *
     * @see #toLocalIndex(BlockState)
     * @param index A number from 0-5 representing the local index of the side of a block
     * @return the corresponding Side
     */
    public static Side fromLocalIndex(int index) {
        return BY_LOCAL_INDEX[index];
    }

    /**
     * Given a block {@code Side} which might be global or local, return the equivalent local {@code Side}. As an
     * example, if the {@code blockState} indicates a block facing west, then both {@code WEST} and {@code FRONT}
     * would return {@code FRONT}.
     *
     * @param blockState The state of the local block, for converting global sides to local ones
     * @return the indicated local side of the block
     */
    public Side toLocal(BlockState blockState) {
        if (direction.getAxis().isVertical() || local) {
            // Already local
            return this;
        }
        if (!blockState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            // No orientation data
            return BY_LOCAL_INDEX[direction.get3DDataValue()];
        }

        final Direction facing = blockState.getValue(HorizontalDirectionalBlock.FACING);
        final int index2d = direction.get2DDataValue();
        final int toLocal2d = -facing.get2DDataValue();
        final int rotatedIndex2d = (index2d + toLocal2d + HORIZONTAL_DIRECTION_COUNT) % HORIZONTAL_DIRECTION_COUNT;
        return BY_LOCAL_2D_INDEX[rotatedIndex2d];
    }

    /**
     * Given a block {@code Side} which might be global or local, return a local index value between 0 and 5.  This is
     * roughly equivalent to {@link Direction#get3DDataValue()} but for local instead of global sides.  As an example,
     * if the blockState indicates a block facing west, then both {@code WEST} and {@code FRONT} would return {@code 3},
     * the index for front.
     * <p>
     * The local index value can be used as an index into arrays for data that should be stored per local side of a
     * block. The order of indices is down, up, back, front, left, right.
     *
     * @see #fromLocalIndex(int)
     * @param blockState The state of the local block, for converting global sides to local indices
     * @return the indicated local index of the block
     */
    public int toLocalIndex(BlockState blockState) {
        return toLocal(blockState).direction.get3DDataValue();
    }

    /**
     * Given a Side which might be global or local, return a global {@link Direction} representing it. As an example, if
     * the {@code blockState} indicates a block facing west, then both {@code WEST} and {@code FRONT} would return
     * {@link Direction#WEST}.
     *
     * @see #fromGlobal(Direction)
     * @param blockState The state of the local block, for converting local sides to global directions
     * @return the global direction corresponding to this block side
     */
    public Direction toGlobal(BlockState blockState) {
        if (direction.getAxis().isVertical() || !local) {
            return direction;
        }
        if (!blockState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return direction;
        }

        final Direction facing = blockState.getValue(HorizontalDirectionalBlock.FACING);
        final int index = direction.get2DDataValue();
        final int toGlobal = facing.get2DDataValue();
        final int rotatedIndex = (index + toGlobal) % HORIZONTAL_DIRECTION_COUNT;
        return Direction.from2DDataValue(rotatedIndex);
    }

    // Direction calculation
    public static Direction relativeDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > Math.abs(dz)) {
            if (dx > 0) {
                return Direction.EAST;
            }
            else {
                return Direction.WEST;
            }
        }
        else if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > Math.abs(dz)) {
            if (dy > 0) {
                return Direction.UP;
            }
            else {
                return Direction.DOWN;
            }
        }
        else {
            if (dz > 0) {
                return Direction.SOUTH;
            }
            else {
                return Direction.NORTH;
            }
        }
    }
}
