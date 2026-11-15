package dev.probeguard.response;

import dev.probeguard.ProbeGuardPlugin;
import dev.probeguard.config.Settings;
import dev.probeguard.detect.Violation;
import dev.probeguard.profile.ClientProfile;
import dev.probeguard.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class ResponseManager {

    private final ProbeGuardPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public ResponseManager(ProbeGuardPlugin plugin) {
        this.plugin = plugin;
    }

    public void alert(ClientProfile profile, int score) {
        Settings.Response cfg = plugin.settings().response;
        Component message = Text.parse(render(cfg.alertFormat, profile, score));

        Bukkit.getConsoleSender().sendMessage(message);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(cfg.alertPermission)) {
                online.sendMessage(message);
            }
        }
    }

    public void punish(ClientProfile profile, int score) {
        Settings.Response cfg = plugin.settings().response;
        Player player = Bukkit.getPlayer(profile.uuid());

        for (String raw : cfg.consoleCommands) {
            String command = render(raw, profile, score);
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        }

        if (player != null && player.isOnline()) {
            player.kick(Text.parse(render(cfg.kickMessage, profile, score)));
        }

        discord(profile, score, true);
        plugin.debug("[response] punished " + display(profile) + " score=" + score);
    }

    public void discord(ClientProfile profile, int score, boolean punished) {
        Settings.Discord cfg = plugin.settings().response.discord;
        if (!cfg.enabled || cfg.webhookUrl.isBlank()) {
            return;
        }
        if (score < cfg.minScore) {
            return;
        }

        String body = buildPayload(profile, score, punished, cfg.username);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(cfg.webhookUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "ProbeGuard/1.0")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    plugin.getLogger().warning("Discord webhook returned HTTP " + response.statusCode()
                            + ": " + response.body());
                }
            } catch (Exception failure) {
                plugin.getLogger().warning("Discord webhook failed: " + failure.getMessage());
            }
        });
    }

    private String buildPayload(ClientProfile profile, int score, boolean punished, String username) {
        String color = punished ? "15158332" : "16776960";
        String title = punished ? "Client integrity punishment" : "Client integrity alert";

        List<String> fields = new ArrayList<>();
        fields.add(field("Player", display(profile) + " (`" + profile.uuid() + "`)", true));
        fields.add(field("Score", String.valueOf(score), true));
        fields.add(field("Brand", profile.brand().isEmpty() ? "none" : profile.brand(), true));
        fields.add(field("Channels", String.valueOf(profile.channels().size()), true));
        fields.add(field("Findings", reason(profile), false));

        return "{\"username\":\"" + json(username) + "\",\"embeds\":[{"
                + "\"title\":\"" + json(title) + "\","
                + "\"color\":" + color + ","
                + "\"fields\":[" + String.join(",", fields) + "]"
                + "}]}";
    }

    private static String field(String name, String value, boolean inline) {
        return "{\"name\":\"" + json(name) + "\",\"value\":\"" + json(truncate(value, 1024))
                + "\",\"inline\":" + inline + "}";
    }

    private static String json(String input) {
        StringBuilder out = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private static String render(String template, ClientProfile profile, int score) {
        return template
                .replace("%player%", display(profile))
                .replace("%uuid%", profile.uuid().toString())
                .replace("%score%", String.valueOf(score))
                .replace("%brand%", profile.brand())
                .replace("%channels%", String.valueOf(profile.channels().size()))
                .replace("%reason%", reason(profile));
    }

    private static String reason(ClientProfile profile) {
        List<String> parts = new ArrayList<>();
        for (Violation violation : profile.violations()) {
            parts.add(violation.type().key() + " (" + violation.detail() + ")");
            if (parts.size() >= 4) {
                break;
            }
        }
        return parts.isEmpty() ? "no findings" : String.join("; ", parts);
    }

    private static String display(ClientProfile profile) {
        return profile.name() == null || profile.name().isBlank()
                ? profile.uuid().toString()
                : profile.name();
    }
}
