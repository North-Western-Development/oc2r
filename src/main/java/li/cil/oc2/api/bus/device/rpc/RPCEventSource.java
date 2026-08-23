/* SPDX-License-Identifier: MIT */

package li.cil.oc2.api.bus.device.rpc;

import java.util.*;

/**
 * Provides an interface for an RPC event source. Blocks which wish to provide
 * push notifications via the RPC bus serial device should implement this.
 * It is generally recommended to *also* provide documentation and a list of
 * events by implementing {@link li.cil.oc2.api.bus.device.object.DocumentedDevice
 * DocumentedDevice} and providing a {@code listEvents()} callback
 * <p>
 */
public interface RPCEventSource {

    /**
     * Called to add a {@link IEventSink} to the list of consumers.
     */
    void subscribe(IEventSink dba, UUID sourceid);
    /**
     * Called to remove a specific {@link IEventSink} from the list of consumers.
     */
    void unsubscribe(IEventSink dba);
}
