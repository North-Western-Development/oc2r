package li.cil.oc2.common.vm.tablet;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import li.cil.oc2.common.capabilities.Capabilities;

public class TabletDataProvider implements ICapabilitySerializable<CompoundTag> {

    private final TabletData data = new TabletData();

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == Capabilities.tabletData()) {
            return LazyOptional.of(() -> data).cast();
        }

        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        data.deserializeNBT(tag);
    }
}
