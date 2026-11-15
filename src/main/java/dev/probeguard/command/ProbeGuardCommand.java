package dev.probeguard.command;

import dev.probeguard.ProbeGuardPlugin;
import dev.probeguard.detect.Violation;
import dev.probeguard.profile.ClientProfile;
import dev.probeguard.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProbeGuardCommand implements TabExecutor {

    private final ProbeGuardPlugin plugin;

    public ProbeGuardCommand(ProbeGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("probeguard.command")) {
            sender.sendMessage(Text.parse("<red>You do not have permission to use ProbeGuard."));
            return true;
        }
        if (args.length == 0) {
            help(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> help(sender, label);
            case "check" -> check(sender, args);
            case "brand" -> brand(sender, args);
            case "channels" -> channels(sender, args);
            case "probe" -> probe(sender, args);
            case "reload" -> reload(sender);
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(Text.parse("<gray>── <white>ProbeGuard <gray>──"));
        sender.sendMessage(Text.parse("<gray>/" + label + " check <player> <dark_gray>- score and findings"));
        sender.sendMessage(Text.parse("<gray>/" + label + " brand <player> <dark_gray>- raw client brand"));
        sender.sendMessage(Text.parse("<gray>/" + label + " channels <player> <dark_gray>- registered channels"));
        sender.sendMessage(Text.parse("<gray>/" + label + " probe <player> <dark_gray>- run behaviour probes now"));
        sender.sendMessage(Text.parse("<gray>/" + label + " reload <dark_gray>- reload config.yml"));
    }

    private void check(CommandSender sender, String[] args) {
        ClientProfile profile = profile(sender, args);
        if (profile == null) {
            return;
        }
        int score = plugin.engine().score(profile);

        sender.sendMessage(Text.parse("<gray>── <white>ProbeGuard <gray>──"));
        sender.sendMessage(Text.parse("<gray>Player: <white>" + display(profile)));
        sender.sendMessage(Text.parse("<gray>Brand: <white>"
                + (profile.rawBrand().isEmpty() ? "<i>none</i>" : Text.escape(profile.rawBrand()))));
        sender.sendMessage(Text.parse("<gray>Channels: <white>" + profile.channels().size()
                + " <dark_gray>(" + profile.registeredChannels().size() + " via register)"));
        sender.sendMessage(Text.parse("<gray>Score: <white>" + score
                + " <dark_gray>(alert at " + plugin.settings().response.alertScore + ")"));
        sender.sendMessage(Text.parse("<gray>Bedrock: <white>" + profile.bedrock()));

        if (profile.violations().isEmpty()) {
            sender.sendMessage(Text.parse("<green>No findings."));
            return;
        }
        for (Violation violation : profile.violations()) {
            sender.sendMessage(Text.parse("<red>* <white>" + violation.type().key()
                    + " <gray>[" + violation.weight() + "] <dark_gray>" + Text.escape(violation.detail())));
        }
    }

    private void brand(CommandSender sender, String[] args) {
        ClientProfile profile = profile(sender, args);
        if (profile == null) {
            return;
        }
        sender.sendMessage(Text.parse("<gray>Brand: <white>"
                + (profile.rawBrand().isEmpty() ? "<i>none</i>" : Text.escape(profile.rawBrand()))));
    }

    private void channels(CommandSender sender, String[] args) {
        ClientProfile profile = profile(sender, args);
        if (profile == null) {
            return;
        }
        sender.sendMessage(Text.parse("<gray>Channels of <white>" + display(profile) + "<gray>:"));
        if (profile.channels().isEmpty()) {
            sender.sendMessage(Text.parse("<dark_gray>(none)"));
        } else {
            profile.channels().stream().sorted()
                    .forEach(channel -> sender.sendMessage(Text.parse("<gray>- <white>" + channel)));
        }
        if (!profile.registeredChannels().isEmpty()) {
            sender.sendMessage(Text.parse("<gray>Registered via minecraft:register:"));
            profile.registeredChannels().stream().sorted()
                    .forEach(channel -> sender.sendMessage(Text.parse("<dark_gray>- " + channel)));
        }
    }

    private void probe(CommandSender sender, String[] args) {
        if (!sender.hasPermission("probeguard.admin")) {
            sender.sendMessage(Text.parse("<red>You need probeguard.admin for this."));
            return;
        }
        Player target = target(sender, args);
        if (target == null) {
            return;
        }
        plugin.pingProbe().schedule(target, 0);
        plugin.echoProbe().schedule(target, 0);
        plugin.engine().rescan(target.getUniqueId());
        sender.sendMessage(Text.parse("<green>Probes queued for <white>" + target.getName() + "<green>."));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("probeguard.admin")) {
            sender.sendMessage(Text.parse("<red>You need probeguard.admin for this."));
            return;
        }
        plugin.reloadSettings();
        sender.sendMessage(Text.parse("<green>ProbeGuard config reloaded. Active checks: <white>"
                + plugin.settings().checksSummary()));
    }

    private ClientProfile profile(CommandSender sender, String[] args) {
        Player target = target(sender, args);
        return target == null ? null : plugin.profiles().get(target.getUniqueId());
    }

    private Player target(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Text.parse("<red>Usage: /probeguard " + args[0] + " <player>"));
            return null;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Text.parse("<red>Player is not online: <white>" + args[1]));
        }
        return target;
    }

    private static String display(ClientProfile profile) {
        return profile.name() == null || profile.name().isBlank()
                ? profile.uuid().toString()
                : profile.name();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("help", "check", "brand", "channels", "probe", "reload"), args[0]);
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return filter(names, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .toList();
    }
}
