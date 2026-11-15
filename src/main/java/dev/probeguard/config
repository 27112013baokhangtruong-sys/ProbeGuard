package dev.probeguard.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Immutable snapshot of config.yml. A reload simply builds a new instance, so
 * every manager must read {@code plugin.settings()} instead of caching it.
 */
public final class Settings {

    public final boolean debug;
    public final Brand brand;
    public final Channels channels;
    public final Handshake handshake;
    public final SettingsCheck settingsCheck;
    public final Bedrock bedrock;
    public final Ping ping;
    public final Echo echo;
    public final Response response;
    public final Probe probe;

    private Settings(boolean debug, Brand brand, Channels channels, Handshake handshake,
                     SettingsCheck settingsCheck, Bedrock bedrock, Ping ping, Echo echo,
                     Response response, Probe probe) {
        this.debug = debug;
        this.brand = brand;
        this.channels = channels;
        this.handshake = handshake;
        this.settingsCheck = settingsCheck;
        this.bedrock = bedrock;
        this.ping = ping;
        this.echo = echo;
        this.response = response;
        this.probe = probe;
    }

    public static Settings load(FileConfiguration c, Logger log) {
        Brand brand = new Brand(
                c.getBoolean("checks.brand.enabled", true),
                weight(c, "checks.brand.weight", 4),
                c.getBoolean("checks.brand.allow-empty", false),
                patterns(c, "checks.brand.blacklist", log),
                patterns(c, "checks.brand.whitelist", log),
                lowerSet(c.getStringList("checks.brand.vanilla-aliases"))
        );

        Channels channels = new Channels(
                c.getBoolean("checks.channels.enabled", true),
                weight(c, "checks.channels.weight", 5),
                c.getStringList("checks.channels.vanilla-brands").stream()
                        .map(s -> s.toLowerCase(Locale.ROOT)).toList(),
                lowerSet(c.getStringList("checks.channels.allowed-namespaces")),
                patterns(c, "checks.channels.blacklist", log),
                patterns(c, "checks.channels.whitelist", log),
                patterns(c, "checks.channels.ignore", log)
        );

        Handshake handshake = new Handshake(
                c.getBoolean("checks.handshake.enabled", true),
                weight(c, "checks.handshake.weight", 6),
                Math.max(0, c.getInt("checks.handshake.grace-seconds", 15)) * 1000L,
                c.getStringList("checks.handshake.vanilla-brands").stream()
                        .map(s -> s.toLowerCase(Locale.ROOT)).toList(),
                c.getStringList("checks.handshake.mod-prefixes").stream()
                        .map(s -> s.toLowerCase(Locale.ROOT)).toList()
        );

        String localePattern = c.getString("checks.settings.locale-pattern", "^[a-z]{2,3}_[A-Z]{2}$");
        SettingsCheck settingsCheck = new SettingsCheck(
                c.getBoolean("checks.settings.enabled", true),
                weight(c, "checks.settings.weight", 2),
                c.getInt("checks.settings.min-view-distance", 2),
                c.getInt("checks.settings.max-view-distance", 32),
                compile(localePattern, "checks.settings.locale-pattern", log)
        );

        Bedrock bedrock = new Bedrock(
                c.getBoolean("checks.bedrock.enabled", true),
                weight(c, "checks.bedrock.weight", 9),
                c.getString("checks.bedrock.prefix", ".")
        );

        Ping ping = new Ping(
                c.getBoolean("checks.ping.enabled", true),
                weight(c, "checks.ping.weight", 4),
                Math.max(1, c.getInt("checks.ping.probes", 3)),
                Math.max(1, c.getInt("checks.ping.interval-ticks", 20)),
                Math.max(500L, c.getLong("checks.ping.timeout-ms", 4000L)),
                Math.max(0L, c.getLong("checks.ping.max-avg-rtt-ms", 0L))
        );

        Echo echo = new Echo(
                c.getBoolean("checks.echo.enabled", false),
                weight(c, "checks.echo.weight", 7),
                c.getStringList("checks.echo.flag-replies-on").stream()
                        .map(s -> s.toLowerCase(Locale.ROOT)).toList(),
                readEchoProbes(c.getMapList("checks.echo.probes"), log)
        );

        Discord discord = new Discord(
                c.getBoolean("response.discord.enabled", false),
                c.getString("response.discord.webhook-url", ""),
                c.getString("response.discord.username", "ProbeGuard"),
                c.getInt("response.discord.min-score", 6)
        );
        Response response = new Response(
                c.getString("response.alert-permission", "probeguard.alerts"),
                c.getString("response.alert-format",
                        "<red>[ProbeGuard]</red> <white>%player%</white> <gray>-></gray> <red>%reason%</red> <dark_gray>(score %score%)</dark_gray>"),
                Math.max(1, c.getInt("response.alert-score", 6)),
                Math.max(0, c.getInt("response.kick-score", 0)),
                c.getString("response.kick-message", "<red>Client integrity check failed.</red>"),
                List.copyOf(c.getStringList("response.console-commands")),
                discord
        );

        Probe probe = new Probe(
                Math.max(0, c.getInt("probe.start-delay-ticks", 60)),
                Math.max(0, c.getInt("probe.rescan-interval-seconds", 300)),
                c.getBoolean("probe.bedrock-bypass", true)
        );

        if (ping.enabled && ping.probes < 1) {
            log.warning("checks.ping.probes must be >= 1; ping probing disabled.");
        }

        return new Settings(c.getBoolean("debug", false), brand, channels, handshake,
                settingsCheck, bedrock, ping, echo, response, probe);
    }

    public String checksSummary() {
        List<String> active = new ArrayList<>();
        if (brand.enabled) active.add("brand");
        if (channels.enabled) active.add("channels");
        if (handshake.enabled) active.add("handshake");
        if (settingsCheck.enabled) active.add("settings");
        if (bedrock.enabled) active.add("bedrock");
        if (ping.enabled) active.add("ping");
        if (echo.enabled) active.add("echo");
        return String.join(", ", active);
    }

    // ------------------------------------------------------------------ helpers

    private static int weight(FileConfiguration c, String path, int def) {
        return Math.max(1, c.getInt(path, def));
    }

    private static Set<String> lowerSet(List<String> values) {
        Set<String> out = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                out.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(out);
    }

    private static List<Pattern> patterns(FileConfiguration c, String path, Logger log) {
        List<Pattern> out = new ArrayList<>();
        for (String raw : c.getStringList(path)) {
            Pattern pattern = compile(raw, path, log);
            if (pattern != null) {
                out.add(pattern);
            }
        }
        return List.copyOf(out);
    }

    private static Pattern compile(String raw, String path, Logger log) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(raw);
        } catch (PatternSyntaxException e) {
            log.warning("Invalid regex in " + path + ": '" + raw + "' (" + e.getDescription() + ") - ignored.");
            return null;
        }
    }

    private static List<EchoProbe> readEchoProbes(List<Map<?, ?>> raw, Logger log) {
        List<EchoProbe> out = new ArrayList<>();
        for (Map<?, ?> map : raw) {
            Object channel = map.get("channel");
            if (!(channel instanceof String name) || name.isBlank()) {
                log.warning("checks.echo.probes entry without a valid 'channel' - ignored.");
                continue;
            }
            Object payload = map.get("payload");
            Object timeout = map.get("timeout-ms");
            Object expect = map.get("expect-reply");
            out.add(new EchoProbe(
                    name.toLowerCase(Locale.ROOT),
                    decodePayload(payload instanceof String s ? s : ""),
                    timeout instanceof Number n ? Math.max(250L, n.longValue()) : 3000L,
                    expect instanceof Boolean b && b
            ));
        }
        return List.copyOf(out);
    }

    private static byte[] decodePayload(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new byte[0];
        }
        try {
            if (raw.startsWith("hex:")) {
                String hex = raw.substring(4).replaceAll("\\s", "");
                if (hex.length() % 2 != 0) {
                    throw new IllegalArgumentException("odd length");
                }
                byte[] data = new byte[hex.length() / 2];
                for (int i = 0; i < data.length; i++) {
                    data[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                }
                return data;
            }
            if (raw.startsWith("base64:")) {
                return Base64.getDecoder().decode(raw.substring(7));
            }
        } catch (RuntimeException ignored) {
            return new byte[0];
        }
        return raw.getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ sections

    public static final class Brand {
        public final boolean enabled;
        public final int weight;
        public final boolean allowEmpty;
        public final List<Pattern> blacklist;
        public final List<Pattern> whitelist;
        public final Set<String> vanillaAliases;

        Brand(boolean enabled, int weight, boolean allowEmpty, List<Pattern> blacklist,
              List<Pattern> whitelist, Set<String> vanillaAliases) {
            this.enabled = enabled;
            this.weight = weight;
            this.allowEmpty = allowEmpty;
            this.blacklist = blacklist;
            this.whitelist = whitelist;
            this.vanillaAliases = vanillaAliases;
        }
    }

    public static final class Channels {
        public final boolean enabled;
        public final int weight;
        public final List<String> vanillaBrands;
        public final Set<String> allowedNamespaces;
        public final List<Pattern> blacklist;
        public final List<Pattern> whitelist;
        public final List<Pattern> ignore;

        Channels(boolean enabled, int weight, List<String> vanillaBrands, Set<String> allowedNamespaces,
                 List<Pattern> blacklist, List<Pattern> whitelist, List<Pattern> ignore) {
            this.enabled = enabled;
            this.weight = weight;
            this.vanillaBrands = vanillaBrands;
            this.allowedNamespaces = allowedNamespaces;
            this.blacklist = blacklist;
            this.whitelist = whitelist;
            this.ignore = ignore;
        }
    }

    public static final class Handshake {
        public final boolean enabled;
        public final int weight;
        public final long graceMs;
        public final List<String> vanillaBrands;
        public final List<String> modPrefixes;

        Handshake(boolean enabled, int weight, long graceMs, List<String> vanillaBrands, List<String> modPrefixes) {
            this.enabled = enabled;
            this.weight = weight;
            this.graceMs = graceMs;
            this.vanillaBrands = vanillaBrands;
            this.modPrefixes = modPrefixes;
        }
    }

    public static final class SettingsCheck {
        public final boolean enabled;
        public final int weight;
        public final int minViewDistance;
        public final int maxViewDistance;
        public final Pattern localePattern;

        SettingsCheck(boolean enabled, int weight, int minViewDistance, int maxViewDistance, Pattern localePattern) {
            this.enabled = enabled;
            this.weight = weight;
            this.minViewDistance = minViewDistance;
            this.maxViewDistance = maxViewDistance;
            this.localePattern = localePattern;
        }
    }

    public static final class Bedrock {
        public final boolean enabled;
        public final int weight;
        public final String prefix;

        Bedrock(boolean enabled, int weight, String prefix) {
            this.enabled = enabled;
            this.weight = weight;
            this.prefix = prefix == null ? "." : prefix;
        }
    }

    public static final class Ping {
        public final boolean enabled;
        public final int weight;
        public final int probes;
        public final int intervalTicks;
        public final long timeoutMs;
        public final long maxAvgRttMs;

        Ping(boolean enabled, int weight, int probes, int intervalTicks, long timeoutMs, long maxAvgRttMs) {
            this.enabled = enabled;
            this.weight = weight;
            this.probes = probes;
            this.intervalTicks = intervalTicks;
            this.timeoutMs = timeoutMs;
            this.maxAvgRttMs = maxAvgRttMs;
        }
    }

    public static final class Echo {
        public final boolean enabled;
        public final int weight;
        public final List<String> flagReplyPrefixes;
        public final List<EchoProbe> probes;

        Echo(boolean enabled, int weight, List<String> flagReplyPrefixes, List<EchoProbe> probes) {
            this.enabled = enabled;
            this.weight = weight;
            this.flagReplyPrefixes = flagReplyPrefixes;
            this.probes = probes;
        }
    }

    public record EchoProbe(String channel, byte[] payload, long timeoutMs, boolean expectReply) {
    }

    public static final class Response {
        public final String alertPermission;
        public final String alertFormat;
        public final int alertScore;
        public final int kickScore;
        public final String kickMessage;
        public final List<String> consoleCommands;
        public final Discord discord;

        Response(String alertPermission, String alertFormat, int alertScore, int kickScore,
                 String kickMessage, List<String> consoleCommands, Discord discord) {
            this.alertPermission = alertPermission;
            this.alertFormat = alertFormat;
            this.alertScore = alertScore;
            this.kickScore = kickScore;
            this.kickMessage = kickMessage;
            this.consoleCommands = consoleCommands;
            this.discord = discord;
        }
    }

    public static final class Discord {
        public final boolean enabled;
        public final String webhookUrl;
        public final String username;
        public final int minScore;

        Discord(boolean enabled, String webhookUrl, String username, int minScore) {
            this.enabled = enabled;
            this.webhookUrl = webhookUrl == null ? "" : webhookUrl;
            this.username = username == null ? "ProbeGuard" : username;
            this.minScore = minScore;
        }
    }

    public static final class Probe {
        public final int startDelayTicks;
        public final int rescanIntervalSeconds;
        public final boolean bedrockBypass;

        Probe(int startDelayTicks, int rescanIntervalSeconds, boolean bedrockBypass) {
            this.startDelayTicks = startDelayTicks;
            this.rescanIntervalSeconds = rescanIntervalSeconds;
            this.bedrockBypass = bedrockBypass;
        }
    }
}
