/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.network.message;

import java.util.UUID;

import li.cil.oc2.common.capabilities.Capabilities;
import li.cil.oc2.common.vm.tablet.TabletState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class TabletRemovalMessage extends AbstractMessage {
    private UUID id;

    ///////////////////////////////////////////////////////////////////

    public TabletRemovalMessage(final UUID id) {
        this.id = id;
    }

    public TabletRemovalMessage(final FriendlyByteBuf buffer) {
        super(buffer);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    public void fromBytes(final FriendlyByteBuf buffer) {
        id = buffer.readUUID();
    }

    @Override
    public void toBytes(final FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    protected void handleMessage(final NetworkEvent.Context context) {
        if (EffectiveSide.get().isServer()) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            overworld.getCapability(Capabilities.tabletData()).ifPresent(tabletData -> {
                TabletState state = tabletData.get(id);
                if (state != null) {
                    state.getVirtualMachine().stop();
                    state.getVirtualMachine().dispose();
                }

                tabletData.remove(id);
            });
        }
    }
}
