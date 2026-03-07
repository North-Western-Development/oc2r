/* SPDX-License-Identifier: MIT */

package li.cil.oc2.api.util;

import net.minecraft.core.Direction;
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

    private final Direction direction;
    private final boolean local;

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
    public Direction getDirection() {
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
