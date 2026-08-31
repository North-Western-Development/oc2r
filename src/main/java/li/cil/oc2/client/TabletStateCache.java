/* SPDX-License-Identifier: MIT */

package li.cil.oc2.client;

import li.cil.oc2.common.vm.tablet.TabletState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TabletStateCache {
    private static final Map<UUID, TabletState> CACHE = new HashMap<>();

    public static TabletState getOrCreateTabletState(UUID id) {
        if (CACHE.containsKey(id)) {
            return CACHE.get(id);
        }

        TabletState state = new TabletState(id, true);
        CACHE.put(id, state);
        return state;
    }

    public static void remove(UUID id) {
        CACHE.remove(id);
    }
}
