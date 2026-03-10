/* SPDX-License-Identifier: MIT */

package li.cil.oc2.api.bus.device.rpc;
import java.util.UUID;

/**
 * This interface handles events coming from RPCEventSources.
 * RPCDeviceBusAdapter implements this to relay events via the built in serial
 */

public interface IEventSink {
    /**
     * Hand a message to the event sink to process
     *
     * @param sourceid The UUID of the originator, usually given by {@link RPCEventSource#subscribe(IEventSink, UUID)}
     * @param msg The message. Should be serializable with gson
     */
    void postEvent(UUID sourceid, Object msg);
}
