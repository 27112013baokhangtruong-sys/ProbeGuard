package dev.probeguard.profile;

import com.github.retrooper.packetevents.protocol.player.User;
import dev.probeguard.detect.Violation;
import dev.probeguard.detect.ViolationType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Everything ProbeGuard knows about one connection. PacketEvents events arrive
 * on netty threads while commands/actions run on the main thread, so all
 * mutable state here is concurrent or volatile.
 */
public final class ClientProfile {

    private final UUID uuid;
    private volatile String name = "";
    private volatile String rawBrand = "";
    private final java.util.Set<String> channels = ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> registeredChannels = ConcurrentHashMap.newKeySet();
    private volatile ClientSettings settings;
    private volatile User user;
    private volatile boolean bedrock;
    private volatile long firstSeen = System.currentTimeMillis();
    private volatile long joinedAt;
    private volatile int alertedScore;
    private volatile boolean punished;

    private final Map<String, Violation> violations = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingEchoes = new ConcurrentHashMap<>();
    private final AtomicInteger probeRounds = new AtomicInteger();

    public ClientProfile(UUID uuid) {
        this.uuid = uuid;
    }

    // ------------------------------------------------------------------ identity

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    /** Brand as received, unmodified. */
    public String rawBrand() {
        return rawBrand;
    }

    public void setRawBrand(String brand) {
        this.rawBrand = brand == null ? "" : brand;
    }

    /** Normalised brand used for matching. */
    public String brand() {
        return rawBrand.replaceAll("[\\p{Cntrl}]", "").trim().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ channels

    public java.util.Set<String> channels() {
        return channels;
    }

    public java.util.Set<String> registeredChannels() {
        return registeredChannels;
    }

    public void addChannel(String channel) {
        if (channel != null && !channel.isBlank()) {
            channels.add(channel.toLowerCase(Locale.ROOT));
        }
    }

    public void addRegisteredChannel(String channel) {
        if (channel != null && !channel.isBlank()) {
            registeredChannels.add(channel.toLowerCase(Locale.ROOT));
        }
    }

    // ------------------------------------------------------------------ settings

    public ClientSettings settings() {
        return settings;
    }

    public void setSettings(ClientSettings settings) {
        this.settings = settings;
    }

    public User user() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean bedrock() {
        return bedrock;
    }

    public void setBedrock(boolean bedrock) {
        this.bedrock = bedrock;
    }

    // ------------------------------------------------------------------ timing

    public long joinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(long joinedAt) {
        this.joinedAt = joinedAt;
    }

    public long ageMs() {
        long base = joinedAt > 0 ? joinedAt : firstSeen;
        return System.currentTimeMillis() - base;
    }

    public long firstSeen() {
        return firstSeen;
    }

    // ------------------------------------------------------------------ findings

    public boolean addViolation(Violation violation) {
        return violations.putIfAbsent(key(violation), violation) == null;
    }

    public boolean hasViolation(ViolationType type) {
        String prefix = type.key() + "|";
        return violations.keySet().stream().anyMatch(k -> k.startsWith(prefix));
    }

    public Collection<Violation> violations() {
        List<Violation> list = new ArrayList<>(violations.values());
        list.sort(Comparator.comparingLong(Violation::at));
        return List.copyOf(list);
    }

    public int alertedScore() {
        return alertedScore;
    }

    public void setAlertedScore(int alertedScore) {
        this.alertedScore = alertedScore;
    }

    public boolean punished() {
        return punished;
    }

    public void setPunished(boolean punished) {
        this.punished = punished;
    }

    // ------------------------------------------------------------------ echo state

    public Map<String, Long> pendingEchoes() {
        return pendingEchoes;
    }

    public int nextProbeRound() {
        return probeRounds.incrementAndGet();
    }

    private static String key(Violation violation) {
        return violation.type().key() + "|" + violation.detail();
    }

    /**
     * Values from the vanilla Client Information / Client Settings packet.
     */
    public record ClientSettings(String locale, int viewDistance, String chatVisibility,
                                 boolean chatColors, int skinMask, String mainHand,
                                 String particleStatus, boolean textFiltering) {
    }
}
