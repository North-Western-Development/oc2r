package li.cil.oc2.common.config.common;

import li.cil.oc2.common.config.Config;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.TierSortingRegistry;

public class GameplaySpec {
    public final ForgeConfigSpec.EnumValue<Tiers> blockOperationsModuleToolTier;
    public final ForgeConfigSpec.LongValue soundCardCoolDownSeconds;
    public final ForgeConfigSpec.DoubleValue robotCooldown;

    GameplaySpec(ForgeConfigSpec.Builder builder) {
        blockOperationsModuleToolTier = builder.comment(
            "The mining tool equivalent of the block operations module"
        ).defineEnum("blockOperationsModuleToolTier", Tiers.DIAMOND);

        soundCardCoolDownSeconds = builder.comment(
            "The number of seconds between sound card uses, to prevent spam/abuse"
        ).defineInRange("soundCardCoolDownSeconds", 2, 1, Long.MAX_VALUE);

        builder.push("entities");

        robotCooldown = builder.comment(
            "The number of seconds between actions of the robot"
        ).defineInRange("robotCooldown", 1.0, 0.05, Double.MAX_VALUE);

        builder.pop();
    }

    public void loadValues() {
        Config.blockOperationsModuleToolTier = TierSortingRegistry.getName(blockOperationsModuleToolTier.get());
        Config.soundCardCoolDownSeconds = soundCardCoolDownSeconds.get();
        Config.robotCooldown = robotCooldown.get();
    }
}
