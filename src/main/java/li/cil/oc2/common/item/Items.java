/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.item;

import li.cil.oc2.api.API;
import li.cil.oc2.common.Constants;
import li.cil.oc2.common.config.Config;
import li.cil.oc2.common.block.Blocks;
import li.cil.oc2.common.bus.device.data.FirmwareRegistry;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public final class Items {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, API.MOD_ID);

    ///////////////////////////////////////////////////////////////////

    public static final RegistryObject<Item> BUS_CABLE = register(Blocks.BUS_CABLE, BusCableItem::new);
    public static final RegistryObject<BusInterfaceItem> BUS_INTERFACE = register("bus_interface", BusInterfaceItem::new);
    public static final RegistryObject<Item> CHARGER = register(Blocks.CHARGER, ChargerItem::new);
    public static final RegistryObject<Item> COMPUTER = register(Blocks.COMPUTER);
    public static final RegistryObject<Item> MONITOR = register(Blocks.MONITOR);
    public static final RegistryObject<Item> CREATIVE_ENERGY = register(Blocks.CREATIVE_ENERGY);
    public static final RegistryObject<Item> DISK_DRIVE = register(Blocks.DISK_DRIVE);
    public static final RegistryObject<Item> FLASH_MEMORY_FLASHER = register(Blocks.FLASH_MEMORY_FLASHER);
    public static final RegistryObject<Item> KEYBOARD = register(Blocks.KEYBOARD);
    public static final RegistryObject<Item> NETWORK_CONNECTOR = register(Blocks.NETWORK_CONNECTOR);
    public static final RegistryObject<Item> NETWORK_HUB = register(Blocks.NETWORK_HUB);
    //public static final RegistryObject<Item> NETWORK_SWITCH = register(Blocks.NETWORK_SWITCH);
    public static final RegistryObject<Item> PROJECTOR = register(Blocks.PROJECTOR);
    public static final RegistryObject<Item> REDSTONE_INTERFACE = register(Blocks.REDSTONE_INTERFACE);
    public static final RegistryObject<Item> VXLAN_HUB = register(Blocks.VXLAN_HUB);
    public static final RegistryObject<Item> PCI_CARD_CAGE = register(Blocks.PCI_CARD_CAGE);
    public static final RegistryObject<Item> INTERNET_GATEWAY = register(Blocks.INTERNET_GATEWAY);

    ///////////////////////////////////////////////////////////////////

    public static final RegistryObject<Item> WRENCH = register("wrench", WrenchItem::new);
    public static final RegistryObject<Item> MANUAL = register("manual", ManualItem::new);

    public static final RegistryObject<Item> ROBOT = register("robot", RobotItem::new);
    public static final RegistryObject<NetworkCableItem> NETWORK_CABLE = register("network_cable", NetworkCableItem::new);

    public static final RegistryObject<MemoryItem> MEMORY_SMALL = register("memory_small", () ->
        new MemoryItem(2 * Constants.MEGABYTE));
    public static final RegistryObject<MemoryItem> MEMORY_MEDIUM = register("memory_medium", () ->
        new MemoryItem(4 * Constants.MEGABYTE));
    public static final RegistryObject<MemoryItem> MEMORY_LARGE = register("memory_large", () ->
        new MemoryItem(8 * Constants.MEGABYTE));
    public static final RegistryObject<MemoryItem> MEMORY_EXTRA_LARGE = register("memory_extra_large", () ->
        new MemoryItem(16 * Constants.MEGABYTE));

    public static final RegistryObject<HardDriveItem> HARD_DRIVE_SMALL = register("hard_drive_small", () ->
        new HardDriveItem(Config.diskSizeFactor, DyeColor.LIGHT_GRAY));
    public static final RegistryObject<HardDriveItem> HARD_DRIVE_MEDIUM = register("hard_drive_medium", () ->
        new HardDriveItem(2 * Config.diskSizeFactor, DyeColor.GREEN));
    public static final RegistryObject<HardDriveItem> HARD_DRIVE_LARGE = register("hard_drive_large", () ->
        new HardDriveItem(4 * Config.diskSizeFactor, DyeColor.CYAN));
    public static final RegistryObject<HardDriveItem> HARD_DRIVE_EXTRA_LARGE = register("hard_drive_extra_large", () ->
        new HardDriveItem(16 * Config.diskSizeFactor, DyeColor.YELLOW));

    public static final RegistryObject<CPUItem> CPU_TIER_1 = register("cpu_tier_1", () ->
        new CPUItem(25_000_000));
    public static final RegistryObject<CPUItem> CPU_TIER_2 = register("cpu_tier_2", () ->
        new CPUItem(50_000_000));
    public static final RegistryObject<CPUItem> CPU_TIER_3 = register("cpu_tier_3", () ->
        new CPUItem(100_000_000));
    public static final RegistryObject<CPUItem> CPU_TIER_4 = register("cpu_tier_4", () ->
        new CPUItem(200_000_000));
    public static final RegistryObject<CPUItem> CPU_TIER_INF = register("cpu_tier_inf", () ->
        new CPUItem(1_000_000_000));
    public static final RegistryObject<FlashMemoryItem> FLASH_MEMORY = register("flash_memory", () ->
        new FlashMemoryItem(12 * Constants.MEGABYTE));
    public static final RegistryObject<FlashMemoryWithExternalDataItem> FLASH_MEMORY_CUSTOM = register("flash_memory_custom", () ->
        new FlashMemoryWithExternalDataItem(FirmwareRegistry.MINUX.getId()));

    public static final RegistryObject<FloppyItem> FLOPPY = register("floppy", () ->
        new FloppyItem(512 * Constants.KILOBYTE));
    public static final RegistryObject<FloppyItem> FLOPPY_MODERN = register("floppy_modern", () ->
        new FloppyItem(1440 * Constants.KILOBYTE));

    public static final RegistryObject<Item> REDSTONE_INTERFACE_CARD = register("redstone_interface_card");
    public static final RegistryObject<Item> NETWORK_INTERFACE_CARD = register("network_interface_card", NetworkInterfaceCardItem::new);
    public static final RegistryObject<Item> NETWORK_TUNNEL_CARD = register("network_tunnel_card", NetworkTunnelItem::new);
    public static final RegistryObject<Item> INTERNET_CARD = register("internet_card");
    public static final RegistryObject<Item> FILE_IMPORT_EXPORT_CARD = register("file_import_export_card");
    public static final RegistryObject<Item> SOUND_CARD = register("sound_card");

    public static final RegistryObject<Item> INVENTORY_OPERATIONS_MODULE = register("inventory_operations_module");
    public static final RegistryObject<Item> BLOCK_OPERATIONS_MODULE = register("block_operations_module", BlockOperationsModule::new);
    public static final RegistryObject<Item> NETWORK_TUNNEL_MODULE = register("network_tunnel_module", NetworkTunnelItem::new);

    public static final RegistryObject<Item> TRANSISTOR = register("transistor", ModItem::new);
    public static final RegistryObject<Item> SILICON_BLEND = register("silicon_blend", ModItem::new);
    public static final RegistryObject<Item> SILICON = register("silicon", ModItem::new);
    public static final RegistryObject<Item> SILICON_WAFER = register("silicon_wafer", ModItem::new);
    public static final RegistryObject<Item> RAW_SILICON_WAFER = register("raw_silicon_wafer", ModItem::new);
    public static final RegistryObject<Item> CIRCUIT_BOARD = register("circuit_board", ModItem::new);

    public static final RegistryObject<Item> TABLET = register("tablet", TabletItem::new);

    ///////////////////////////////////////////////////////////////////

    public static void initialize(FMLJavaModLoadingContext context) {
        ITEMS.register(context.getModEventBus());
    }

    ///////////////////////////////////////////////////////////////////

    private static RegistryObject<Item> register(final String name) {
        return register(name, ModItem::new);
    }

    private static <T extends Item> RegistryObject<T> register(final String name, final Supplier<T> factory) {
        return ITEMS.register(name, factory);
    }

    private static <T extends Block> RegistryObject<Item> register(final RegistryObject<T> block) {
        return register(block, ModBlockItem::new);
    }

    private static <TBlock extends Block, TItem extends Item> RegistryObject<TItem> register(
            final RegistryObject<TBlock> block, final Function<TBlock, TItem> factory) {
        return register(block.getId().getPath(), () -> factory.apply(block.get()));
    }
}
