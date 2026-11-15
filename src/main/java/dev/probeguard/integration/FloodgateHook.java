package dev.probeguard.integration;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Optional Floodgate bridge through reflection so ProbeGuard does not need
 * Floodgate on the compile classpath and keeps working when it is absent.
 */
public final class FloodgateHook {

    private final boolean available;
    private final Object api;
    private final Method isFloodgatePlayer;

    public FloodgateHook(Logger log) {
        Object resolvedApi = null;
        Method resolvedMethod = null;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            resolvedApi = apiClass.getMethod("getInstance").invoke(null);
            resolvedMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
        } catch (Throwable ignored) {
            resolvedApi = null;
            resolvedMethod = null;
        }
        this.api = resolvedApi;
        this.isFloodgatePlayer = resolvedMethod;
        this.available = resolvedApi != null && resolvedMethod != null;
        if (available) {
            log.info("Floodgate detected: Bedrock players will be tagged and can bypass behaviour probes.");
        }
    }

    public boolean available() {
        return available;
    }

    public boolean isBedrock(UUID uuid) {
        if (!available || uuid == null) {
            return false;
        }
        try {
            Object result = isFloodgatePlayer.invoke(api, uuid);
            return result instanceof Boolean value && value;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
