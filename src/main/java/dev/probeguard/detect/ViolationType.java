package dev.probeguard.detect;

import java.util.Locale;

/**
 * Every finding type ProbeGuard can produce. The key is used in logs, commands
 * and Discord embeds; it is stable so external tooling can match on it.
 */
public enum ViolationType {

    BRAND_EMPTY("brand-empty"),
    BRAND_BLACKLISTED("brand-blacklisted"),
    BRAND_NOT_WHITELISTED("brand-not-whitelisted"),
    BRAND_MALFORMED("brand-malformed"),

    CHANNEL_BLOCKED("channel-blocked"),
    CHANNEL_UNKNOWN_NAMESPACE("channel-unknown-namespace"),
    VANILLA_WITH_MOD_CHANNELS("vanilla-with-mod-channels"),

    HANDSHAKE_MISMATCH("handshake-mismatch"),
    SETTINGS_ANOMALY("settings-anomaly"),
    BEDROCK_SPOOF("bedrock-spoof"),

    PING_MISSED("ping-missed"),
    PING_SLOW("ping-slow"),

    ECHO_NO_REPLY("echo-no-reply"),
    ECHO_MOD_REPLY("echo-mod-reply");

    private final String key;

    ViolationType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static ViolationType fromKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        for (ViolationType type : values()) {
            if (type.key.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
