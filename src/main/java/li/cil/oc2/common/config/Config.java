/* SPDX-License-Identifier: MIT */

package li.cil.oc2.common.config;

import li.cil.oc2.common.Constants;
import li.cil.oc2.common.config.client.GUISpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.TierSortingRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("FieldMayBeFinal")
public final class Config {
    // TODO: Implement configuration of CPU MHzs
    public static long maxAllocatedMemory = 512 * Constants.MEGABYTE;
    public static int diskSizeFactor = 2 * Constants.MEGABYTE;

    public static double busCableEnergyPerTick = 0.1;
    public static double busInterfaceEnergyPerTick = 0.5;
    public static int computerEnergyPerTick = 10;
    public static int computerEnergyStorage = 2000;
    public static int chargerEnergyPerTick = 2500;
    public static int chargerEnergyStorage = 10000;
    public static int projectorEnergyPerTick = 20;
    public static int projectorEnergyStorage = 2000;
    public static int monitorEnergyPerTick = 15;
    public static int monitorEnergyStorage = 2000;
    public static int cardCageEnergyPerTick = 20;
    public static int cardCageEnergyStorage = 2000;
    public static int gatewayEnergyPerPacket = 20;
    public static int gatewayEnergyStorage = 2000;

    public static int robotEnergyPerTick = 5;
    public static int robotEnergyStorage = 750000;

    public static int tabletEnergyPerTick = 1;
    public static int tabletEnergyStorage = 750000;

    public static double memoryEnergyPerMegabytePerTick = 0.5;
    public static double hardDriveEnergyPerMegabytePerTick = 1;
    public static double cpuEnergyPerMegahertzPerTick = 0.1;
    public static int redstoneInterfaceCardEnergyPerTick = 1;
    public static int networkInterfaceEnergyPerTick = 1;
    public static int fileImportExportCardEnergyPerTick = 1;
    public static int soundCardEnergyPerTick = 1;
    public static int blockOperationsModuleEnergyPerTick = 2;
    public static int inventoryOperationsModuleEnergyPerTick = 1;
    public static int networkTunnelEnergyPerTick = 2;

    public static ResourceLocation blockOperationsModuleToolTier = TierSortingRegistry.getName(Tiers.DIAMOND);
    public static long soundCardCoolDownSeconds = 2;

    public static UUID fakePlayerUUID = UUID.fromString("e39dd9a7-514f-4a2d-aa5e-b6030621416d");
    public static int projectorAverageMaxBytesPerSecond = 160 * 1024;
    public static int ethernetFrameTimeToLive = 12;
    public static int hubEthernetFramesPerTick = 32;

    public static boolean enable = false;
    public static String remoteHost = "::1";
    public static int remotePort = 4789;
    public static String bindHost = "::1";
    public static int bindPort = 4789;
    public static boolean internetCardEnabled = false;
    public static int defaultSessionLifetimeMs = 60 * 1000;
    public static int defaultSessionsNumberPerCardLimit = 10;
    public static int defaultSessionsNumberLimit = 100;
    public static int defaultEchoRequestTimeoutMs = 1000;
    public static List<String> deniedHosts = Arrays.asList("127.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10",
            "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4");
    public static List<String> allowedHosts = List.of();
    public static String defaultNameServer = "1.1.1.1";
    public static boolean useSynchronisedNAT = false;
    public static int streamBufferSize = 2000;
    public static int tcpRetransmissionTimeoutMs = 2 * 1000;

    public static GUISpec.CaptureInputMode captureInputMode = GUISpec.CaptureInputMode.PER_BLOCK;
    public static boolean captureInputDefaultState = false;

    public static boolean computersUseEnergy() {
        return computerEnergyPerTick > 0 && computerEnergyStorage > 0;
    }

    public static boolean projectorsUseEnergy() {
        return projectorEnergyStorage > 0 && projectorEnergyPerTick > 0;
    }

    public static boolean cardCagesUseEnergy() {
        return cardCageEnergyStorage > 0 && cardCageEnergyPerTick > 0;
    }

    public static boolean robotsUseEnergy() {
        return robotEnergyPerTick > 0 && robotEnergyStorage > 0;
    }

    public static boolean tabletsUseEnergy() {
        return tabletEnergyPerTick > 0 && tabletEnergyStorage > 0;
    }

    public static boolean monitorsUseEnergy() {
        return computerEnergyPerTick > 0 && computerEnergyStorage > 0;
    }

    public static boolean gatewayUseEnergy() {
        return gatewayEnergyPerPacket > 0 && gatewayEnergyStorage > 0;
    }
}
