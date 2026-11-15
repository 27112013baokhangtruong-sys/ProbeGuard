package dev.probeguard.integration;

import dev.probeguard.ProbeGuardPlugin;
import dev.probeguard.detect.Violation;
import dev.probeguard.profile.ClientProfile;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class PlaceholderHook {

    private PlaceholderHook() {
    }

    public static void register(ProbeGuardPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new Expansion(plugin).register();
            plugin.getLogger().info("Registered PlaceholderAPI expansion 'probeguard'.");
        } catch (Throwable failure) {
            plugin.getLogger().warning("Could not register PlaceholderAPI expansion: " + failure.getMessage());
        }
    }

    private static final class Expansion extends PlaceholderExpansion {

        private final ProbeGuardPlugin plugin;

        private Expansion(ProbeGuardPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public @NotNull String getIdentifier() {
            return "probeguard";
        }

        @Override
        public @NotNull String getAuthor() {
            return "ProbeGuard";
        }

        @Override
        public @NotNull String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer player, @NotNull String params) {
            if (player == null) {
                return "";
            }
            ClientProfile profile = plugin.profiles().peek(player.getUniqueId());
            if (profile == null) {
                return "";
            }
            return switch (params.toLowerCase(Locale.ROOT)) {
                case "brand" -> profile.brand();
                case "brand_raw" -> profile.rawBrand();
                case "channels" -> String.valueOf(profile.channels().size());
                case "score" -> String.valueOf(plugin.engine().score(profile));
                case "flagged" -> String.valueOf(!profile.violations().isEmpty());
                case "findings" -> String.valueOf(profile.violations().size());
                case "bedrock" -> String.valueOf(profile.bedrock());
                case "locale" -> profile.settings() == null ? "" : nullSafe(profile.settings().locale());
                case "view_distance" -> profile.settings() == null ? "" : String.valueOf(profile.settings().viewDistance());
                case "reason" -> {
                    StringBuilder out = new StringBuilder();
                    for (Violation violation : profile.violations()) {
                        if (!out.isEmpty()) {
                            out.append(", ");
                        }
                        out.append(violation.type().key());
                    }
                    yield out.toString();
                }
                default -> "";
            };
        }

        private static String nullSafe(String value) {
            return value == null ? "" : value;
        }
    }
}
