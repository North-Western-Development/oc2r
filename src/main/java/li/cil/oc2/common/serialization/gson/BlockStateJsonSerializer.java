package li.cil.oc2.common.serialization.gson;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Type;

public class BlockStateJsonSerializer implements JsonSerializer<BlockState> {

    @Override
    public JsonElement serialize(final BlockState src, final Type typeOfSrc, final JsonSerializationContext context) {
        if (src.isAir()) {
            return JsonNull.INSTANCE;
        }
        JsonObject json = new JsonObject();
        ResourceLocation blockName = ForgeRegistries.BLOCKS.getKey(src.getBlock());
        json.addProperty("block", blockName.toString());

        JsonObject props = new JsonObject();
        for (Property<?> property : src.getProperties()) {
            props.addProperty(property.getName(), src.getValue(property).toString());
        }
        json.add("properties", props);
        return json;
    }
}
