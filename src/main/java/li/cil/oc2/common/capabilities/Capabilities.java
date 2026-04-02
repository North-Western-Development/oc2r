/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.capabilities;

import li.cil.oc2.api.API;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;


public final class Capabilities {
    public static final class DeviceBusElement {
        public static final BlockCapability<li.cil.oc2.api.bus.DeviceBusElement, @Nullable Direction> BLOCK = BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath(API.MOD_ID, "device_bus_element"), li.cil.oc2.api.bus.DeviceBusElement.class);
    }
    public static final class Device {
        public static final BlockCapability<li.cil.oc2.api.bus.device.Device, @Nullable Direction> BLOCK = BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath(API.MOD_ID, "device"), li.cil.oc2.api.bus.device.Device.class);
        public static final ItemCapability<li.cil.oc2.api.bus.device.Device, @Nullable Void> ITEM = ItemCapability.createVoid(ResourceLocation.fromNamespaceAndPath(API.MOD_ID, "device"), li.cil.oc2.api.bus.device.Device.class);
    }
    public static final class RedstoneEmitter {
        public static final BlockCapability<li.cil.oc2.api.capabilities.RedstoneEmitter, @Nullable Direction> BLOCK = BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath(API.MOD_ID, "redstone_emitter"), li.cil.oc2.api.capabilities.RedstoneEmitter.class);
    }
    public static final class BundledEmitter {
        public static final BlockCapability<li.cil.oc2.api.capabilities.BundledEmitter, @Nullable Direction> BLOCK = BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath(API.MOD_ID, "bundled_emitter"), li.cil.oc2.api.capabilities.BundledEmitter.class);
    }
    public static final class NetworkInterface {
        public static final BlockCapability<li.cil.oc2.api.capabilities.NetworkInterface, @Nullable Direction> BLOCK = BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath(API.MOD_ID, "network_interface"), li.cil.oc2.api.capabilities.NetworkInterface.class);
    }
    public static final class TerminalUserProvider {
        public static final BlockCapability<li.cil.oc2.api.capabilities.TerminalUserProvider, @Nullable Void> BLOCK = BlockCapability.createVoid(ResourceLocation.fromNamespaceAndPath(API.MOD_ID, "terminal_user_provider"), li.cil.oc2.api.capabilities.TerminalUserProvider.class);
        public static final EntityCapability<li.cil.oc2.api.capabilities.TerminalUserProvider, @Nullable Void> ENTITY = EntityCapability.createVoid(ResourceLocation.fromNamespaceAndPath(API.MOD_ID, "terminal_user_provider"), li.cil.oc2.api.capabilities.TerminalUserProvider.class);
    }
    public static final class Robot {
        public static final EntityCapability<li.cil.oc2.api.capabilities.Robot, @Nullable Void> ENTITY = EntityCapability.createVoid(ResourceLocation.fromNamespaceAndPath(API.MOD_ID, "robot"), li.cil.oc2.api.capabilities.Robot.class);
    }

    // Re-export Neoforge's capabilities to save everyone else from the conflicting class names
    public static final class EnergyStorage {
        public static final BlockCapability<IEnergyStorage, @Nullable Direction> BLOCK = net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK;
        public static final EntityCapability<IEnergyStorage, @Nullable Direction> ENTITY = net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ENTITY;
        public static final ItemCapability<IEnergyStorage, @Nullable Void> ITEM = net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM;
    }

    public static final class ItemHandler {
        public static final BlockCapability<IItemHandler, @Nullable Direction> BLOCK = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK;
        public static final EntityCapability<IItemHandler, @Nullable Void> ENTITY = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ENTITY;
        public static final ItemCapability<IItemHandler, @Nullable Void> ITEM = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ITEM;
    }

    public static final class FluidHandler {
        public static final BlockCapability<IFluidHandler, @Nullable Direction> BLOCK = net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK;
        public static final EntityCapability<IFluidHandler, @Nullable Direction> ENTITY = net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ENTITY;
        public static final ItemCapability<IFluidHandlerItem, @Nullable Void> ITEM = net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM;
    }
}
