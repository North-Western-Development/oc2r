package li.cil.oc2.common.mixin;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import li.cil.oc2.client.TabletStateCache;
import li.cil.oc2.common.item.Items;
import li.cil.oc2.common.item.TabletItem;
import li.cil.oc2.common.network.Network;
import li.cil.oc2.common.network.message.TabletRemovalMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.Type;
import net.minecraft.world.item.ItemStack;

@Mixin(CreativeModeInventoryScreen.class)
public class CreativeClientMixin {
    @Shadow
    private Slot destroyItemSlot;

    @Shadow
    private boolean hasClickedOutside;

    @Shadow
    private static CreativeModeTab selectedTab;

    private static void handleStack(ItemStack stack) {
        if (!stack.isEmpty() && stack.is(Items.TABLET.get())) {
            if (stack.getOrCreateTag().hasUUID(TabletItem.UUID_TAG_NAME)) {
                UUID id = stack.getOrCreateTag().getUUID(TabletItem.UUID_TAG_NAME);
                TabletStateCache.remove(id);
                Network.sendToServer(new TabletRemovalMessage(id));
            }
        }
    }

    @Inject(method = "slotClicked", at = @At("HEAD"))
    private void onSlotClicked(@Nullable Slot slot, int a, int b, ClickType ct, CallbackInfo ci) {
        if ((Object) this instanceof CreativeModeInventoryScreen self) {
            ItemStack stack = self.getMenu().getCarried();

            boolean targetDiff = true;

            if (slot != null) {
                targetDiff = !stack.getDescriptionId().equals(slot.getItem().getDescriptionId());
            }

            if (!hasClickedOutside) {
                if (slot == this.destroyItemSlot && selectedTab.getType() == Type.INVENTORY) {
                    if (ct == ClickType.QUICK_MOVE) {
                        // Destroy all items in inventory using the trash slot.
                        for (int j = 0; j < Minecraft.getInstance().player.inventoryMenu.getItems().size(); ++j) {
                            Slot target = self.getMenu().getSlot(j);
                            if (target != null && target.getItem() != null) {
                                handleStack(target.getItem());
                            }
                        }
                    } else {
                        // Destroy using the trash slot.
                        handleStack(stack);
                    }
                } else if (selectedTab.getType() != Type.INVENTORY) {
                    if (ct == ClickType.QUICK_MOVE && (a >= 45 || !stack.isEmpty()) && !(b == 0 && !targetDiff)) {
                        if (!stack.isEmpty()) {
                            handleStack(stack);
                        } else {
                            Slot target = self.getMenu().getSlot(a);
                            if (target != null && target.getItem() != null) {
                                // Destroy by shift clicking into category.
                                handleStack(target.getItem());
                            }
                        }
                    } else if (ct == ClickType.PICKUP && a >= 0 && a < 45 && !stack.isEmpty() && b < 2) {
                        if (targetDiff || b == 1) {
                            // Destroy by dropping into category.
                            handleStack(stack);
                        }
                    } else if (ct == ClickType.SWAP && a < 45) {
                        Slot target = self.getMenu().getSlot(b + 45);
                        if (target != null && target.getItem() != null) {
                            // Destroy by swapping stack onto item.
                            handleStack(target.getItem());
                        }
                    }
                }

            }
        }
    }
}
