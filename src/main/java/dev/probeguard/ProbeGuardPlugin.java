package dev.probeguard;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import dev.probeguard.command.ProbeGuardCommand;
import dev.probeguard.config.Settings;
import dev.probeguard.detect.DetectionEngine;
import dev.probeguard.detect.EchoProbe;
import dev.probeguard.detect.PingProbe;
import dev.probeguard.integration.FloodgateHook;
import dev.probeguard.integration.PlaceholderHook;
import dev.probeguard.listener.PlayerListener;
import dev.probeguard.listener.ProbeListener;
import dev.probeguard.profile.ClientProfile;
import dev.probeguard.profile.ProfileRegistry;
import dev.probeguard.response.ResponseManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProbeGuardPlugin extends JavaPlugin {

    private volatile Settings settings;
    private ProfileRegistry profiles;
    private DetectionEngine engine;
    private ResponseManager responses;
    private PingProbe pingProbe;
    private EchoProbe echoProbe;
    private FloodgateHook floodgate;
    private PacketListenerCommon listenerHandle;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.settings = Settings.load(getConfig(), getLogger());

        this.profiles = new ProfileRegistry();
        this.responses = new ResponseManager(this);
        this.pingProbe = new PingProbe(this);
        this.echoProbe = new EchoProbe(this);
        this.engine = new DetectionEngine(this);
        this.floodgate = new FloodgateHook(getLogger());

        if (!packetEventsReady()) {
            getLogger().severe("PacketEvents is missing or not initialised.");
            getLogger().severe("Install PacketEvents 2.14+ from https://github.com/retrooper/packetevents and restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.listenerHandle = PacketEvents.getAPI().getEventManager()
                .registerListener(new ProbeListener(this), PacketListenerPriority.NORMAL);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        PluginCommand command = getCommand("probeguard");
        if (command != null) {
            ProbeGuardCommand handler = new ProbeGuardCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        PlaceholderHook.register(this);
        startTasks();
        getLogger().info("ProbeGuard enabled (checks: " + settings.checksSummary() + ")");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (pingProbe != null) {
            pingProbe.clear();
        }
        if (echoProbe != null) {
            echoProbe.clear();
        }
        if (listenerHandle != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(listenerHandle);
            } catch (Throwable ignored) {
                // PacketEvents may already be shut down
            }
        }
        if (profiles != null) {
            profiles.clear();
        }
    }

    public void reloadSettings() {
        reloadConfig();
        this.settings = Settings.load(getConfig(), getLogger());
    }

    private void startTasks() {
        Bukkit.getScheduler().runTaskTimer(this, pingProbe::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, echoProbe::tick, 20L, 20L);

        int interval = settings.probe.rescanIntervalSeconds;
        if (interval > 0) {
            Bukkit.getScheduler().runTaskTimer(this, this::rescanAll,
                    interval * 20L, interval * 20L);
        }
    }

    private void rescanAll() {
        for (ClientProfile profile : profiles.all()) {
            Player player = Bukkit.getPlayer(profile.uuid());
            if (player == null || !player.isOnline()) {
                continue;
            }
            pingProbe.schedule(player, 0);
            engine.rescan(profile.uuid());
        }
    }

    private boolean packetEventsReady() {
        try {
            if (getServer().getPluginManager().getPlugin("packetevents") == null) {
                return false;
            }
            return PacketEvents.getAPI() != null && PacketEvents.getAPI().isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void debug(String message) {
        if (settings != null && settings.debug) {
            getLogger().info("[debug] " + message);
        }
    }

    public Settings settings() {
        return settings;
    }

    public ProfileRegistry profiles() {
        return profiles;
    }

    public DetectionEngine engine() {
        return engine;
    }

    public ResponseManager responses() {
        return responses;
    }

    public PingProbe pingProbe() {
        return pingProbe;
    }

    public EchoProbe echoProbe() {
        return echoProbe;
    }

    public FloodgateHook floodgate() {
        return floodgate;
    }
}
