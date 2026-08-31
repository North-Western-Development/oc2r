/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.vm.tablet;

import static li.cil.oc2.common.Constants.ENERGY_TAG_NAME;
import static li.cil.oc2.common.Constants.ITEMS_TAG_NAME;

import java.util.Collections;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import li.cil.oc2.common.bus.CommonDeviceBusController;
import li.cil.oc2.common.config.Config;
import li.cil.oc2.common.energy.FixedEnergyStorage;
import li.cil.oc2.common.serialization.NBTSerialization;
import li.cil.oc2.common.vm.VMRunState;
import li.cil.oc2.common.vm.terminal.Terminal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.INBTSerializable;
import li.cil.oc2.common.ext.ICaptureInputStateStorage;
import li.cil.oc2.api.capabilities.TerminalUserProvider;

public class TabletState implements TerminalUserProvider, ICaptureInputStateStorage, INBTSerializable<CompoundTag> {
    private static final String ID_TAG_NAME = "id";
    private static final String TERMINAL_TAG_NAME = "terminal";
    private static final String STATE_TAG_NAME = "state";
    private static final String BUS_ELEMENT_TAG_NAME = "bus_element";
    private static final String DEVICES_TAG_NAME = "devices";

    private UUID id;
    private final FixedEnergyStorage energy;
    private final TabletItemStackHandlers deviceItems;
    private final TabletBusElement busElement;
    private final CommonDeviceBusController busController;
    private final Terminal terminal;
    private final TabletVirtualMachine virtualMachine;
    @Nullable private ServerPlayer terminalUser;

	private boolean captureInputState = false;

    public TabletState(UUID id,
            FixedEnergyStorage energy,
            TabletItemStackHandlers deviceItems,
            TabletBusElement busElement,
            CommonDeviceBusController busController,
            Terminal terminal,
            TabletVirtualMachine virtualMachine) {
        this.id = id;
        this.energy = energy;
        this.deviceItems = deviceItems;
        this.busElement = busElement;
        this.busController = busController;
        this.terminal = terminal;
        this.virtualMachine = virtualMachine;
    }

    public TabletState(CompoundTag tag, boolean isClientSide) {
        this.energy = new FixedEnergyStorage(Config.tabletEnergyStorage);
        this.deviceItems = new TabletItemStackHandlers(this, isClientSide);

        this.busElement = new TabletBusElement(this);
        this.busController = new CommonDeviceBusController(busElement,
                Config.tabletEnergyPerTick);

        this.terminal = new Terminal();
        this.virtualMachine = new TabletVirtualMachine(this);

        deserializeNBT(tag);
    }

    public TabletState(UUID id, boolean isClientSide) {
        this.id = id;
        this.energy = new FixedEnergyStorage(Config.tabletEnergyStorage);
        this.deviceItems = new TabletItemStackHandlers(this, isClientSide);

        this.busElement = new TabletBusElement(this);
        this.busController = new CommonDeviceBusController(busElement,
                Config.tabletEnergyPerTick);

        this.terminal = new Terminal();
        this.terminal.setDisplayOnly(isClientSide);

        this.virtualMachine = new TabletVirtualMachine(this);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        if (virtualMachine.getRunState() != VMRunState.STOPPED) {
            tag.put(STATE_TAG_NAME, virtualMachine.serialize());
            tag.put(TERMINAL_TAG_NAME, NBTSerialization.serialize(terminal));
        }

        tag.putUUID(ID_TAG_NAME, id);
        tag.put(ENERGY_TAG_NAME, energy.serializeNBT());
        tag.put(BUS_ELEMENT_TAG_NAME, busElement.save());
        tag.put(ITEMS_TAG_NAME, deviceItems.saveItems());
        tag.put(DEVICES_TAG_NAME, deviceItems.saveDevices());

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        id = tag.getUUID(ID_TAG_NAME);
        energy.deserializeNBT(tag.getCompound(ENERGY_TAG_NAME));
        busElement.load(tag.getCompound(BUS_ELEMENT_TAG_NAME));
        deviceItems.loadItems(tag.getCompound(ITEMS_TAG_NAME));
        deviceItems.loadDevices(tag.getCompound(DEVICES_TAG_NAME));
        virtualMachine.deserialize(tag.getCompound(STATE_TAG_NAME));
        NBTSerialization.deserialize(tag.getCompound(TERMINAL_TAG_NAME), terminal);
    }

    public void deserializeClientUpdate(CompoundTag tag) {
        energy.deserializeNBT(tag.getCompound(ENERGY_TAG_NAME));
        deviceItems.loadItems(tag.getCompound(ITEMS_TAG_NAME));
    }

    public UUID getId() {
        return id;
    }

	public FixedEnergyStorage getEnergy() {
		return energy;
	}

	public TabletItemStackHandlers getDeviceItems() {
		return deviceItems;
	}

	public TabletBusElement getBusElement() {
		return busElement;
	}

	public CommonDeviceBusController getBusController() {
		return busController;
	}

	public Terminal getTerminal() {
		return terminal;
	}

	public TabletVirtualMachine getVirtualMachine() {
		return virtualMachine;
	}

	public ServerPlayer getTerminalUser() {
		return terminalUser;
	}

    @Override
    public Iterable<Player> getTerminalUsers() {
        return (terminalUser == null) ? Collections.emptyList() : Collections.singleton(terminalUser);
    }

    public void setTerminalUser(ServerPlayer terminalUser) {
        if (terminalUser == null) {
            return;
        }

		this.terminalUser = terminalUser;
	}

    @Override
	public boolean getCaptureInputState() {
		return captureInputState;
	}

    @Override
	public void setCaptureInputState(boolean state) {
		captureInputState = state;
	}
}
