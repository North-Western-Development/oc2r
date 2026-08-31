/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.vm.tablet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fml.util.thread.EffectiveSide;

public class TabletData implements ITabletData, INBTSerializable<CompoundTag> {
    public static final Map<UUID, TabletState> map = new HashMap<>();

    @Override
    public Iterable<TabletState> getAll() {
        return map.values();
    }

    @Override
    public TabletState get(UUID id) {
        return map.get(id);
    }

    @Override
    public void put(UUID id, TabletState state) {
        map.put(id, state);
    }

    @Override
    public void remove(UUID id) {
        TabletState state = map.get(id);
        if (state != null) {
            state.getVirtualMachine().stop();
            map.remove(id);
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        Iterator<Map.Entry<UUID, TabletState>> it = map.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, TabletState> entry = it.next();
            tag.put(entry.getKey().toString(), entry.getValue().serializeNBT());
        }

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        map.clear();

        for (String key : tag.getAllKeys()) {
            UUID id = UUID.fromString(key);

            TabletState tablet = new TabletState(tag.getCompound(key), !EffectiveSide.get().isServer());

            map.put(id, tablet);
        }
    }
}
