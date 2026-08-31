/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.vm.tablet;

import java.util.UUID;

public interface ITabletData {
    Iterable<TabletState> getAll();
    TabletState get(UUID id);
    void put(UUID id, TabletState state);
    void remove(UUID id);
}
