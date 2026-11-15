package dev.probeguard.profile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfileRegistry {

    private final Map<UUID, ClientProfile> profiles = new ConcurrentHashMap<>();

    public ClientProfile get(UUID uuid) {
        return profiles.computeIfAbsent(uuid, ClientProfile::new);
    }

    public ClientProfile peek(UUID uuid) {
        return profiles.get(uuid);
    }

    public void remove(UUID uuid) {
        profiles.remove(uuid);
    }

    public Collection<ClientProfile> all() {
        return List.copyOf(profiles.values());
    }

    public int size() {
        return profiles.size();
    }

    public void clear() {
        profiles.clear();
    }
}
