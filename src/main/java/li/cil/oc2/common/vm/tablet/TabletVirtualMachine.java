/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.vm.tablet;

import java.nio.ByteBuffer;

import javax.annotation.Nullable;

import li.cil.oc2.common.bus.CommonDeviceBusController;
import li.cil.oc2.common.config.Config;
import li.cil.oc2.common.network.Network;
import li.cil.oc2.common.network.message.TabletBootErrorMessage;
import li.cil.oc2.common.network.message.TabletBusStateMessage;
import li.cil.oc2.common.network.message.TabletRunStateMessage;
import li.cil.oc2.common.network.message.TabletTerminalOutputMessage;
import li.cil.oc2.common.util.TerminalUtils;
import li.cil.oc2.common.vm.AbstractTerminalVMRunner;
import li.cil.oc2.common.vm.AbstractVirtualMachine;
import li.cil.oc2.common.vm.VMRunState;
import net.minecraft.network.chat.Component;

public class TabletVirtualMachine extends AbstractVirtualMachine {
    private TabletState tabletState;

    public TabletVirtualMachine(final TabletState tabletState) {
        super(tabletState.getBusController());

        this.tabletState = tabletState;

        state.vmAdapter.setBaseAddressProvider(tabletState.getDeviceItems()::getDeviceAddressBase);
    }

    @Override
    protected boolean consumeEnergy(final int amount, final boolean simulate) {
        if (!Config.tabletsUseEnergy()) {
            return true;
        }

        if (amount > tabletState.getEnergy().getEnergyStored()) {
            return false;
        }

        tabletState.getEnergy().extractEnergy(amount, simulate);
        return true;
    }

    @Override
    protected void stopRunnerAndReset() {
        super.stopRunnerAndReset();

        TerminalUtils.resetTerminal(tabletState.getTerminal(), output -> {
        if (tabletState.getTerminalUser() != null)
            Network.sendToClient(new TabletTerminalOutputMessage(tabletState.getId(), output),
                    tabletState.getTerminalUser());
        });
    }

    @Override
    protected AbstractTerminalVMRunner createRunner() {
        return new TabletVMRunner(tabletState);
    }

    @Override
    public void handleBusStateChanged(final CommonDeviceBusController.BusState value) {
        if (tabletState.getTerminalUser() != null)
            Network.sendToClient(new TabletBusStateMessage(tabletState.getId(), value), tabletState.getTerminalUser());
    }

    @Override
    public void handleRunStateChanged(final VMRunState value) {
        if (tabletState.getTerminalUser() != null)
            Network.sendToClient(new TabletRunStateMessage(tabletState.getId(), value), tabletState.getTerminalUser());
    }

    @Override
    protected void handleBootErrorChanged(@Nullable Component value) {
        if (value == null) {
            value = Component.literal("");
        }
        if (tabletState.getTerminalUser() != null)
            Network.sendToClient(new TabletBootErrorMessage(tabletState.getId(), value), tabletState.getTerminalUser());
    }

    private final class TabletVMRunner extends AbstractTerminalVMRunner {
        private final TabletState state;

        public TabletVMRunner(final TabletState state) {
            super(state.getVirtualMachine(), state.getTerminal());

            this.state = state;
        }

        protected void sendTerminalUpdateToClient(final ByteBuffer output) {
            if (tabletState.getTerminalUser() != null)
                Network.sendToClient(new TabletTerminalOutputMessage(state.getId(), output), state.getTerminalUser());
        }
    }
}
