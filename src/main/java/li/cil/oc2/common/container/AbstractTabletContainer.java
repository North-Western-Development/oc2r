/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.container;

import static li.cil.oc2.common.network.MessageUtils.findItemByUUID;

import java.nio.ByteBuffer;
import java.util.UUID;

import li.cil.oc2.client.ClientSetup;
import li.cil.oc2.client.TabletStateCache;
import li.cil.oc2.common.bus.CommonDeviceBusController;
import li.cil.oc2.common.capabilities.Capabilities;
import li.cil.oc2.common.config.Config;
import li.cil.oc2.common.network.Network;
import li.cil.oc2.common.network.message.OpenTabletInventoryMessage;
import li.cil.oc2.common.network.message.OpenTabletTerminalMessage;
import li.cil.oc2.common.network.message.TabletPowerMessage;
import li.cil.oc2.common.network.message.TabletTerminalInputMessage;
import li.cil.oc2.common.vm.VirtualMachine;
import li.cil.oc2.common.vm.tablet.TabletState;
import li.cil.oc2.common.vm.terminal.Terminal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.energy.IEnergyStorage;

public abstract class AbstractTabletContainer extends AbstractMachineTerminalContainer {
    private final UUID tabletId;
    private final Player player;
    private static boolean captureInputState = Config.captureInputDefaultState;

    ///////////////////////////////////////////////////////////////////

    public AbstractTabletContainer(final MenuType<?> type, final int id, final UUID tabletId, final Player player,
            final IntPrecisionContainerData energyInfo) {
        super(type, id, energyInfo);
        this.tabletId = tabletId;
        this.player = player;
    }

    ///////////////////////////////////////////////////////////////////

    public static TabletState getTabletState(UUID id, Player player) {
        if (player.level() != null && !player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            return serverPlayer.getServer().getLevel(Level.OVERWORLD).getCapability(Capabilities.tabletData())
                    .map(tabletData -> tabletData.get(id)).orElse(null);
        }

        return TabletStateCache.getOrCreateTabletState(id);
    }

    @Override
    public void switchToInventory() {
        Network.sendToServer(new OpenTabletInventoryMessage(tabletId));
    }

    @Override
    public void switchToTerminal() {
        Network.sendToServer(new OpenTabletTerminalMessage(tabletId));
    }

    @Override
    public VirtualMachine getVirtualMachine() {
        return getTabletState(tabletId, player).getVirtualMachine();
    }

    @Override
    public void sendPowerStateToServer(final boolean value) {
        Network.sendToServer(new TabletPowerMessage(tabletId, value));
    }

    @Override
    public Terminal getTerminal() {
        return getTabletState(tabletId, player).getTerminal();
    }

    @Override
    public boolean getCaptureInputState() {
        return switch (Config.captureInputMode) {
            case PER_BLOCK -> getTabletState(tabletId, player).getCaptureInputState();
            case SHARED_BETWEEN_TYPE -> captureInputState;
            case GLOBAL_CAPTURE -> ClientSetup.getCaptureInputState();
        };
    }

    @Override
    public void setCaptureInputState(final boolean state) {
        switch (Config.captureInputMode) {
            case PER_BLOCK -> getTabletState(tabletId, player).setCaptureInputState(state);
            case SHARED_BETWEEN_TYPE -> captureInputState = state;
            case GLOBAL_CAPTURE -> ClientSetup.setCaptureInputState(state);
        }
    }

    @Override
    public void sendTerminalInputToServer(final ByteBuffer input) {
        Network.sendToServer(new TabletTerminalInputMessage(tabletId, input));
    }

    @Override
    public boolean stillValid(final Player player) {
        return findItemByUUID(player, tabletId) != ItemStack.EMPTY;
    }

    @Override
    public void removed(final Player player) {
        super.removed(player);

        getTabletState(tabletId, player).setTerminalUser(null);
    }

    ///////////////////////////////////////////////////////////////////

    protected static IntPrecisionContainerData createEnergyInfo(final IEnergyStorage energy,
            final CommonDeviceBusController busController) {
        return new IntPrecisionContainerData.Server() {
            @Override
            public int getInt(final int index) {
                return switch (index) {
                    case AbstractMachineContainer.ENERGY_STORED_INDEX -> energy.getEnergyStored();
                    case AbstractMachineContainer.ENERGY_CAPACITY_INDEX -> energy.getMaxEnergyStored();
                    case AbstractMachineContainer.ENERGY_CONSUMPTION_INDEX -> busController.getEnergyConsumption();
                    default -> 0;
                };
            }

            @Override
            public int getIntCount() {
                return ENERGY_INFO_SIZE;
            }
        };
    }
}
