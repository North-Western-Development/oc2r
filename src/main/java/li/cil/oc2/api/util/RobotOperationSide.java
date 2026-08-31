/* SPDX-License-Identifier: MIT */

package li.cil.oc2.api.util;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * A more restrictive version of {@link Side}, intended for robot operation APIs.
 */
public enum RobotOperationSide {
    @SerializedName(value="front", alternate={"FRONT", "f"}) FRONT(Direction.SOUTH),
    @SerializedName(value="up", alternate={"TOP", "top", "UP", "u"}) UP(Direction.UP),
    @SerializedName(value="down", alternate={"BOTTOM", "bottom", "DOWN", "d"}) DOWN(Direction.DOWN),
    ;

    private final Direction direction;

    RobotOperationSide(final Direction direction) {
        this.direction = direction;
    }

    RobotOperationSide(final RobotOperationSide parent) {
        this(parent.direction);
    }

    /**
     * Gets the world-space direction for the specified side relative to the specified entity.
     *
     * @param entity the entity to which the side is relative.
     * @param side   the side to convert to a world-space direction.
     * @return a world-space direction.
     */
    public static Direction toGlobal(final Entity entity, @Nullable final RobotOperationSide side) {
        Direction direction = side == null
            ? RobotOperationSide.FRONT.direction
            : side.direction;
        if (direction.getAxis().isHorizontal()) {
            final int horizontalIndex = entity.getDirection().get2DDataValue();
            for (int i = 0; i < horizontalIndex; i++) {
                direction = direction.getClockWise();
            }
        }
        return direction;
    }
}
