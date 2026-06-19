/* SPDX-License-Identifier: MIT */

package li.cil.oc2.api.capabilities;

/**
 * This interface may be provided as a capability by item components to signal
 * to the containing {@link net.minecraft.world.level.block.entity.BlockEntity} that they wish
 * to emit redstone signals via a Project Red bundled cable. This is used by the built-in
 * redstone interface card, for example.
 */
public interface BundledEmitter {
    /**
     * Returns the bundled output levels for the side this interface was returned for.
     *
     * @return the bundled output levels.
     */
    byte[] getBundledOutput();
}
