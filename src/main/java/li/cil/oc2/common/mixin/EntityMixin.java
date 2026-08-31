package li.cil.oc2.common.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import li.cil.oc2.client.TabletStateCache;
import li.cil.oc2.common.item.Items;
import li.cil.oc2.common.item.TabletItem;
import li.cil.oc2.common.network.Network;
import li.cil.oc2.common.network.message.TabletRemovalMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "remove", at = @At("HEAD"))
    private void onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof ItemEntity self && !self.level().isClientSide) {
            ItemStack stack = self.getItem();
            Component name = stack.getHoverName();

            if (name.getContents() instanceof TranslatableContents translatable
                    && translatable.getKey().equals(Items.TABLET.get().getDescriptionId())) {
                if (stack.is(Items.TABLET.get())
                        && reason == Entity.RemovalReason.KILLED
                        || reason == Entity.RemovalReason.DISCARDED) {
                    if (stack.getOrCreateTag().hasUUID(TabletItem.UUID_TAG_NAME)) {
                        UUID id = stack.getOrCreateTag().getUUID(TabletItem.UUID_TAG_NAME);
                        TabletStateCache.remove(id);
                        Network.sendToServer(new TabletRemovalMessage(id));
                    }
                }
            }
        }
    }
}
