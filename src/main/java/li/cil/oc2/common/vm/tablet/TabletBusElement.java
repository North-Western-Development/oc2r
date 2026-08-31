/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.vm.tablet;

import static java.util.Collections.singleton;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import li.cil.oc2.api.bus.DeviceBusElement;
import li.cil.oc2.api.bus.device.Device;
import li.cil.oc2.api.bus.device.provider.ItemDeviceQuery;
import li.cil.oc2.common.bus.AbstractItemDeviceBusElement;
import li.cil.oc2.common.bus.device.util.Devices;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;

public class TabletBusElement extends AbstractItemDeviceBusElement {
    private static final String DEVICE_ID_TAG_NAME = "device_id";

    private final HashSet<Device> devices = new HashSet<>();
    private final TabletState state;
    private UUID deviceId = UUID.randomUUID();

    public TabletBusElement(TabletState state) {
        super(0);

        this.state = state;
    }

    @Override
    public Optional<Collection<LazyOptional<DeviceBusElement>>> getNeighbors() {
        return Optional.of(singleton(LazyOptional.of(() -> state.getDeviceItems().busElement)));
    }

    @Override
    public Optional<UUID> getDeviceIdentifier(final Device device) {
        if (devices.contains(device)) {
            return Optional.of(deviceId);
        }
        return super.getDeviceIdentifier(device);
    }

    @Override
    public CompoundTag save() {
        final CompoundTag tag = super.save();
        tag.putUUID(DEVICE_ID_TAG_NAME, deviceId);
        return tag;
    }

    public void load(final CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID(DEVICE_ID_TAG_NAME)) {
            deviceId = tag.getUUID(DEVICE_ID_TAG_NAME);
        }
    }

    @Override
    protected ItemDeviceQuery makeQuery(ItemStack stack) {
        return Devices.makeQuery(state, stack);
    }
}

