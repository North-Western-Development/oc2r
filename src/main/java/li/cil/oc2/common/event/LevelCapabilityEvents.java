package li.cil.oc2.common.event;

import java.util.Iterator;

import li.cil.oc2.api.API;
import li.cil.oc2.common.capabilities.Capabilities;
import li.cil.oc2.common.vm.tablet.TabletDataProvider;
import li.cil.oc2.common.vm.tablet.TabletState;
import li.cil.oc2.common.vm.tablet.TabletVirtualMachine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = API.MOD_ID)
public class LevelCapabilityEvents {
    public static final String TABLET_STATE_DATA = "tablet_state_data";

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Level> event) {
        if (event.getObject().dimension() == Level.OVERWORLD) {
            event.addCapability(ResourceLocation.fromNamespaceAndPath(API.MOD_ID, TABLET_STATE_DATA), new TabletDataProvider());
        }
    }


    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            overworld.getCapability(Capabilities.tabletData()).ifPresent(tabletData -> {
                Iterator<TabletState> it = tabletData.getAll().iterator();

                while (it.hasNext()) {
                    TabletState state = it.next();
                    TabletVirtualMachine vm = state.getVirtualMachine();
                    vm.suspend();
                    vm.dispose();
                }
            });
        }
    }
}
