/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.item;

import li.cil.oc2.api.API;
import li.cil.oc2.common.block.ComputerBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ItemGroup {
    public static final DeferredRegister<CreativeModeTab> TAB_REGISTER = DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, API.MOD_ID);

    @SuppressWarnings("unused")
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> COMMON_TAB = TAB_REGISTER.register("common", () -> CreativeModeTab.builder()
        // Set name of tab to display
        .title(Component.translatable("item_group." + API.MOD_ID + ".common"))
        // Set icon of creative tab
        .icon(() -> new ItemStack(Items.COMPUTER.get()))
        // Add default items to tab
        .displayItems((params, output) -> {
            // Items
            output.accept(Items.BUS_CABLE.get());
            output.accept(Items.BUS_INTERFACE.get());
            output.accept(Items.CHARGER.get());
            output.accept(ComputerBlock.getComputerWithFlash());
            output.accept(ComputerBlock.getPreconfiguredComputer());
            output.accept(Items.CREATIVE_ENERGY.get());
            output.accept(Items.DISK_DRIVE.get());
            output.accept(Items.FLASH_MEMORY_FLASHER.get());
            output.accept(Items.KEYBOARD.get());
            output.accept(Items.NETWORK_CONNECTOR.get());
            output.accept(Items.NETWORK_HUB.get());
            output.accept(Items.PROJECTOR.get());
            output.accept(Items.MONITOR.get());
            output.accept(Items.REDSTONE_INTERFACE.get());
            output.accept(Items.WRENCH.get());
            output.accept(Items.MANUAL.get());
            output.accept(RobotItem.getRobotWithFlash());
            output.accept(Items.NETWORK_CABLE.get());
            output.accept(Items.MEMORY_SMALL.get());
            output.accept(Items.MEMORY_MEDIUM.get());
            output.accept(Items.MEMORY_LARGE.get());
            output.accept(Items.MEMORY_EXTRA_LARGE.get());
            output.accept(Items.HARD_DRIVE_SMALL.get());
            output.accept(Items.HARD_DRIVE_MEDIUM.get());
            output.accept(Items.HARD_DRIVE_LARGE.get());
            output.accept(Items.HARD_DRIVE_EXTRA_LARGE.get());
            output.accept(Items.CPU_TIER_1.get());
            output.accept(Items.CPU_TIER_2.get());
            output.accept(Items.CPU_TIER_3.get());
            output.accept(Items.CPU_TIER_4.get());
            output.accept(Items.CPU_TIER_INF.get());
            output.accept(Items.FLASH_MEMORY.get());
            output.accept(Items.FLASH_MEMORY_CUSTOM.get());
            output.accept(Items.FLOPPY.get());
            output.accept(Items.FLOPPY_MODERN.get());
            output.accept(Items.REDSTONE_INTERFACE_CARD.get());
            output.accept(Items.INTERNET_CARD.get());
            output.accept(Items.NETWORK_INTERFACE_CARD.get());
            output.accept(Items.NETWORK_TUNNEL_CARD.get());
            output.accept(Items.FILE_IMPORT_EXPORT_CARD.get());
            output.accept(Items.SOUND_CARD.get());
            output.accept(Items.INVENTORY_OPERATIONS_MODULE.get());
            output.accept(Items.BLOCK_OPERATIONS_MODULE.get());
            output.accept(Items.NETWORK_TUNNEL_MODULE.get());
            output.accept(Items.SILICON.get());
            output.accept(Items.SILICON_BLEND.get());
            output.accept(Items.SILICON_WAFER.get());
            output.accept(Items.RAW_SILICON_WAFER.get());
            output.accept(Items.TRANSISTOR.get());
            output.accept(Items.CIRCUIT_BOARD.get());
            //output.accept(Items.NETWORK_SWITCH.get());
            output.accept(Items.VXLAN_HUB.get());
            output.accept(Items.PCI_CARD_CAGE.get());
            output.accept(Items.INTERNET_GATEWAY.get());
        })
        .build()
    );
}
