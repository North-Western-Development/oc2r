/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.container;

import li.cil.oc2.api.API;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class Containers {
    private static final DeferredRegister<MenuType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, API.MOD_ID);

    ///////////////////////////////////////////////////////////////////

    public static final RegistryObject<MenuType<ComputerInventoryContainer>> COMPUTER = CONTAINERS.register("computer", () -> IForgeMenuType.create(ComputerInventoryContainer::createClient));
    public static final RegistryObject<MenuType<ComputerTerminalContainer>> COMPUTER_TERMINAL = CONTAINERS.register("computer_terminal", () -> IForgeMenuType.create(ComputerTerminalContainer::createClient));
    public static final RegistryObject<MenuType<MonitorDisplayContainer>> MONITOR = CONTAINERS.register("monitor", () -> IForgeMenuType.create(MonitorDisplayContainer::createClient));
    public static final RegistryObject<MenuType<RobotInventoryContainer>> ROBOT = CONTAINERS.register("robot", () -> IForgeMenuType.create(RobotInventoryContainer::createClient));
    public static final RegistryObject<MenuType<RobotTerminalContainer>> ROBOT_TERMINAL = CONTAINERS.register("robot_terminal", () -> IForgeMenuType.create(RobotTerminalContainer::createClient));
    public static final RegistryObject<MenuType<TabletInventoryContainer>> TABLET = CONTAINERS.register("tablet", () -> IForgeMenuType.create(TabletInventoryContainer::createClient));
    public static final RegistryObject<MenuType<TabletTerminalContainer>> TABLET_TERMINAL = CONTAINERS.register("tablet_terminal", () -> IForgeMenuType.create(TabletTerminalContainer::createClient));
    public static final RegistryObject<MenuType<NetworkTunnelContainer>> NETWORK_TUNNEL = CONTAINERS.register("network_tunnel", () -> IForgeMenuType.create(NetworkTunnelContainer::createClient));

    ///////////////////////////////////////////////////////////////////

    public static void initialize(FMLJavaModLoadingContext context) {
        CONTAINERS.register(context.getModEventBus());
    }
}
