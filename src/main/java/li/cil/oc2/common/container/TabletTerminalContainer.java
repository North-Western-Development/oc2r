/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.container;

import java.util.UUID;

import li.cil.oc2.common.vm.tablet.TabletState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;

public final class TabletTerminalContainer extends AbstractTabletContainer {
    public static void createServer(UUID tabletId, final ServerPlayer player) {
        NetworkHooks.openScreen(player, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("item.oc2r.tablet");
            }

            @Override
            public AbstractContainerMenu createMenu(final int id, final Inventory inventory, final Player player) {
                TabletState state = getTabletState(tabletId, player);

                return new TabletTerminalContainer(id, tabletId, player,
                        createEnergyInfo(state.getEnergy(), state.getBusController()));
            }
        }, b -> b.writeUUID(tabletId));
    }

    public static TabletTerminalContainer createClient(final int id, final Inventory inventory, final FriendlyByteBuf data) {
        final UUID tabletId = data.readUUID();

        return new TabletTerminalContainer(id, tabletId, inventory.player, createClientEnergyInfo());
    }

    ///////////////////////////////////////////////////////////////////

    private TabletTerminalContainer(final int id, final UUID tabletId, final Player player, final IntPrecisionContainerData energyInfo) {
        super(Containers.TABLET_TERMINAL.get(), id, tabletId, player, energyInfo);
    }
}

