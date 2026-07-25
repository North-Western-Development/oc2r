package li.cil.oc2.common.config.common;

import li.cil.oc2.common.config.Config;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class InternetCardSpec {
    public final ModConfigSpec.BooleanValue internetCardEnabled;
    public final ModConfigSpec.IntValue defaultSessionLifetimeMs;
    public final ModConfigSpec.IntValue defaultSessionsNumberPerCardLimit;
    public final ModConfigSpec.IntValue defaultSessionsNumberLimit;
    public final ModConfigSpec.IntValue defaultEchoRequestTimeoutMs;
    public final ModConfigSpec.ConfigValue<List<? extends String>> deniedHosts;
    public final ModConfigSpec.ConfigValue<List<? extends String>> allowedHosts;
    public final ModConfigSpec.ConfigValue<String> defaultNameServer;
    public final ModConfigSpec.BooleanValue useSynchronisedNAT;
    public final ModConfigSpec.IntValue streamBufferSize;
    public final ModConfigSpec.IntValue tcpRetransmissionTimeoutMs;

    InternetCardSpec(ModConfigSpec.Builder builder) {
        internetCardEnabled = builder.comment("Whether to enable to internet card, VXLAN must also be enabled")
            .define("internetCardEnabled", false);

        defaultSessionLifetimeMs = builder.comment("Default lifetime of sessions in milliseconds")
            .defineInRange("defaultSessionLifetimeMs", 60*1000, 0, Integer.MAX_VALUE);

        defaultSessionsNumberPerCardLimit = builder.comment("Number of sessions (connections) allowed per internet card")
            .defineInRange("defaultSessionsNumberPerCardLimit", 10, 0, Integer.MAX_VALUE);

        defaultSessionsNumberLimit = builder.comment("Number of sessions (connections) allowed in total across all cards")
            .defineInRange("defaultSessionsNumberLimit", 100, 0, Integer.MAX_VALUE);

        defaultEchoRequestTimeoutMs = builder.comment("Number of milliseconds before a timeout should be assumed on ICMP/Echo (ping) packets")
            .defineInRange("defaultEchoRequestTimeoutMs", 1000, 1, Integer.MAX_VALUE);

        deniedHosts = builder.comment("A list of hosts (IPs) that VMs are not allowed to access",
            "By default all local network address are disallowed, we recommend leaving it this way",
            "Only denied hosts or allowed hosts may have a value, or an error will occur"
        ).defineListAllowEmpty("deniedHosts", Arrays.asList("127.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4"), obj -> obj instanceof String && !((String) obj).trim().isEmpty());

        allowedHosts = builder.comment("A list of hosts (IPs) that VMs are allowed to access",
            "Only denied hosts or allowed hosts may have a value, or an error will occur"
        ).defineListAllowEmpty("allowedHosts", List.of(), obj -> obj instanceof String && !((String) obj).trim().isEmpty());

        defaultNameServer = builder.comment("The default nameserver to be used")
            .define("defaultNameServer", "1.1.1.1");

        useSynchronisedNAT = builder.define("useSynchronisedNAT", false);

        streamBufferSize = builder.defineInRange("streamBufferSize", 2000, 1, Integer.MAX_VALUE);

        tcpRetransmissionTimeoutMs = builder.defineInRange("tcpRetransmissionTimeoutMs", 2000, 1, Integer.MAX_VALUE);
    }

    public void loadValues() {
        Config.internetCardEnabled = internetCardEnabled.get();
        Config.defaultSessionLifetimeMs = defaultSessionLifetimeMs.get();
        Config.defaultSessionsNumberPerCardLimit = defaultSessionsNumberPerCardLimit.get();
        Config.defaultSessionsNumberLimit = defaultSessionsNumberLimit.get();
        Config.defaultEchoRequestTimeoutMs = defaultEchoRequestTimeoutMs.get();
        Config.deniedHosts = deniedHosts.get().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        Config.allowedHosts = allowedHosts.get().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        Config.defaultNameServer = defaultNameServer.get();
        Config.useSynchronisedNAT = useSynchronisedNAT.get();
        Config.streamBufferSize = streamBufferSize.get();
        Config.tcpRetransmissionTimeoutMs = tcpRetransmissionTimeoutMs.get();
    }
}
