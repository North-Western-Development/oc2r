package li.cil.oc2.common.integration.projectred;

import li.cil.oc2.common.blockentity.ComputerBlockEntity;
import li.cil.oc2.common.blockentity.RedstoneInterfaceBlockEntity;
import li.cil.oc2.common.integration.util.BundledRedstone;
import mrtjp.projectred.api.IBundledTileInteraction;
import mrtjp.projectred.api.ITransmissionAPI;
import mrtjp.projectred.api.ProjectRedAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class BundledCableHandler implements IBundledTileInteraction {
    private final ITransmissionAPI transmissionAPI;

    public static void initialize() {
        if (ProjectRedAPI.transmissionAPI != null) {
            BundledCableHandler handler = new BundledCableHandler(ProjectRedAPI.transmissionAPI);
            ProjectRedAPI.transmissionAPI.registerBundledTileInteraction(handler);
            BundledRedstone.getInstance().register(handler);
        }
    }

    private BundledCableHandler(ITransmissionAPI transmissionAPI) {
        this.transmissionAPI = transmissionAPI;
    }

    @Override
    public boolean isValidInteractionFor(final Level level, final BlockPos blockPos, final Direction direction) {
        BlockEntity entity = level.getBlockEntity(blockPos);
        return (entity instanceof RedstoneInterfaceBlockEntity) || (entity instanceof ComputerBlockEntity);
    }

    @Override
    public boolean canConnectBundled(final Level level, final BlockPos blockPos, final Direction direction) {
        BlockEntity entity = level.getBlockEntity(blockPos);
        return (entity instanceof RedstoneInterfaceBlockEntity) || (entity instanceof ComputerBlockEntity);
    }

    @Nullable
    @Override
    public byte[] getBundledSignal(final Level level, final BlockPos blockPos, final Direction direction) {
        BlockEntity entity = level.getBlockEntity(blockPos);
        if (entity instanceof RedstoneInterfaceBlockEntity rs) {
            return rs.getBundledSignal(direction);
        } else if (entity instanceof ComputerBlockEntity cbe) {
            return cbe.getBundledSignal(direction);
        } else {
            return null;
        }
    }

    @Nullable
    public byte[] getBundledInput(final Level level, final BlockPos blockPos, final Direction direction) {
        return transmissionAPI.getBundledInput(level, blockPos, direction);
    }
}
