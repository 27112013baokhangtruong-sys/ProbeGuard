package dev.probeguard.detect;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import dev.probeguard.ProbeGuardPlugin;
import dev.probeguard.config.Settings;
import dev.probeguard.profile.ClientProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Behaviour probe #1.
 *
 * <p>Sends one or more play-phase "Ping" packets and expects the client to echo
 * every id back in a "Pong" packet. A real Minecraft client always answers; a
 * naive protocol reimplementation, a packet filter, or a client that only
 * replays the handshake will not.</p>
 */
public final class PingProbe {

    private final ProbeGuardPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public PingProbe(ProbeGuardPlugin plugin) {
        this.plugin = plugin;
    }

    public void schedule(Player player, int delayTicks) {
        if (!plugin.settings().ping.enabled || !plugin.isEnabled()) {
            return;
        }
        if (!supportsPlayPing()) {
            plugin.debug("ping: play ping is not available on this server version; probe skipped.");
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> start(player), Math.max(0, delayTicks));
    }

    private void start(Player player) {
        if (!player.isOnline()) {
            return;
        }
        ClientProfile profile = plugin.profiles().peek(player.getUniqueId());
        if (profile == null) {
            return;
        }
        if (profile.bedrock() && plugin.settings().probe.bedrockBypass) {
            plugin.debug("ping: skipping Bedrock player " + player.getName());
            return;
        }
        User user = profile.user();
        if (user == null) {
            plugin.debug("ping: no PacketEvents user yet for " + player.getName() + "; probe skipped.");
            return;
        }

        Settings.Ping cfg = plugin.settings().ping;
        Session session = new Session(player.getUniqueId());
        Session previous = sessions.put(player.getUniqueId(), session);
        if (previous != null) {
            previous.cancelled = true;
        }

        for (int i = 0; i < cfg.probes; i++) {
            long delay = (long) i * cfg.intervalTicks + 1L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> send(player, session, user), delay);
        }

        long timeoutTicks = (cfg.timeoutMs + 49L) / 50L;
        long finishAt = (long) cfg.probes * cfg.intervalTicks + timeoutTicks + 2L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> finish(session), finishAt);
    }

    private void send(Player player, Session session, User user) {
        if (session.cancelled || !player.isOnline()) {
            return;
        }
        int id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        session.pending.put(id, System.currentTimeMillis());
        session.sent.incrementAndGet();
        try {
            user.sendPacket(new WrapperPlayServerPing(id));
        } catch (Throwable failure) {
            plugin.debug("ping: failed to send to " + player.getName() + ": " + failure);
        }
    }

    /** Called from the PacketEvents netty thread. */
    public void onPong(UUID uuid, int id) {
        Session session = sessions.get(uuid);
        if (session == null || session.cancelled) {
            return;
        }
        Long sentAt = session.pending.remove(id);
        if (sentAt == null) {
            return;
        }
        session.replied.incrementAndGet();
        session.rtts.add(System.currentTimeMillis() - sentAt);
    }

    /** Periodic cleanup so abandoned pings don't leak. */
    public void tick() {
        long now = System.currentTimeMillis();
        long grace = plugin.settings().ping.timeoutMs + 5000L;
        for (Session session : sessions.values()) {
            session.pending.entrySet().removeIf(entry -> now - entry.getValue() > grace);
        }
    }

    private void finish(Session session) {
        sessions.remove(session.uuid(), session);
        if (session.cancelled) {
            return;
        }
        Settings.Ping cfg = plugin.settings().ping;
        int replied = session.replied.get();
        int sent = session.sent.get();

        if (sent == 0) {
            return;
        }
        if (replied < sent) {
            plugin.engine().report(session.uuid(), ViolationType.PING_MISSED,
                    "pong " + replied + "/" + sent, cfg.weight);
            return;
        }
        if (cfg.maxAvgRttMs > 0 && !session.rtts.isEmpty()) {
            long sum = 0;
            for (long rtt : session.rtts) {
                sum += rtt;
            }
            long average = sum / session.rtts.size();
            if (average > cfg.maxAvgRttMs) {
                plugin.engine().report(session.uuid(), ViolationType.PING_SLOW,
                        "avg pong rtt " + average + "ms > " + cfg.maxAvgRttMs + "ms", cfg.weight);
            }
        }
    }

    public void remove(UUID uuid) {
        Session session = sessions.remove(uuid);
        if (session != null) {
            session.cancelled = true;
        }
    }

    public void clear() {
        sessions.values().forEach(session -> session.cancelled = true);
        sessions.clear();
    }

    private static boolean supportsPlayPing() {
        try {
            return PacketEvents.getAPI().getServerManager().getVersion()
                    .isNewerThanOrEquals(ServerVersion.V_1_17);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class Session {
        private final UUID uuid;
        private final AtomicInteger sent = new AtomicInteger();
        private final AtomicInteger replied = new AtomicInteger();
        private final Map<Integer, Long> pending = new ConcurrentHashMap<>();
        private final List<Long> rtts = Collections.synchronizedList(new ArrayList<>());
        private volatile boolean cancelled;

        private Session(UUID uuid) {
            this.uuid = uuid;
        }

        private UUID uuid() {
            return uuid;
        }
    }
}
