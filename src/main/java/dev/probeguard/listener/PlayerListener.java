package dev.probeguard.listener;

import dev.probeguard.ProbeGuardPlugin;
import dev.probeguard.profile.ClientProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class PlayerListener implements Listener {

    private final ProbeGuardPlugin plugin;

    public PlayerListener(ProbeGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        ClientProfile profile = plugin.profiles().get(uuid);
        profile.setName(player.getName());
        profile.setJoinedAt(System.currentTimeMillis());
        if (plugin.floodgate().isBedrock(uuid)) {
            profile.setBedrock(true);
        }

        int delay = plugin.settings().probe.startDelayTicks;
        plugin.pingProbe().schedule(player, delay);
        plugin.echoProbe().schedule(player, delay);

        // Brand/register channels usually arrive during the configuration phase,
        // so run a first pass shortly after join and another one after the
        // behaviour probes had time to finish.
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.engine().rescan(uuid), Math.max(20, delay / 2));
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.engine().rescan(uuid), delay + 60L);

        plugin.debug("join " + player.getName() + " (bedrock=" + profile.bedrock() + ")");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.pingProbe().remove(uuid);
        plugin.echoProbe().remove(uuid);
        plugin.engine().forget(uuid);
        plugin.profiles().remove(uuid);
    }
}
