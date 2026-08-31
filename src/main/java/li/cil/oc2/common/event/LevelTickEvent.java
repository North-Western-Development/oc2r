package li.cil.oc2.common.event;

import java.util.Iterator;

import li.cil.oc2.common.capabilities.Capabilities;
import li.cil.oc2.common.vm.tablet.TabletState;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class LevelTickEvent {
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!event.level.isClientSide() && event.phase == TickEvent.Phase.END) {
            if (event.level.dimension() == Level.OVERWORLD) {
                event.level.getCapability(Capabilities.tabletData()).ifPresent(tabletData -> {
                    Iterator<TabletState> it = tabletData.getAll().iterator();

                    while (it.hasNext()) {
                        TabletState state = it.next();

                        state.getVirtualMachine().tick();
                    }
                });
            }
        }
    }
}
