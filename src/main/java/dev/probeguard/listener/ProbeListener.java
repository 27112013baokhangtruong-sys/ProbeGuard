package dev.probeguard.listener;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientSettings;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;
import dev.probeguard.ProbeGuardPlugin;
import dev.probeguard.profile.ClientProfile;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * PacketEvents bridge. Everything here runs on a netty thread: do not touch
 * Bukkit objects. Raw values are copied into the profile and a debounced main
 * thread task runs the checks.
 */
public final class ProbeListener implements PacketListener {

    private final ProbeGuardPlugin plugin;

    public ProbeListener(ProbeGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        try {
            handle(event);
        } catch (Throwable failure) {
            plugin.debug("packet handler failed: " + failure);
        }
    }

    private void handle(PacketReceiveEvent event) {
        if (event.getUser() == null || event.getUser().getUUID() == null) {
            return;
        }
        UUID uuid = event.getUser().getUUID();
        ClientProfile profile = plugin.profiles().get(uuid);
        profile.setUser(event.getUser());

        if (event.getPacketType().equals(PacketType.Play.Client.PLUGIN_MESSAGE)) {
            WrapperPlayClientPluginMessage message = new WrapperPlayClientPluginMessage(event);
            onPluginMessage(uuid, profile, message.getChannelName(), message.getData());
        } else if (event.getPacketType().equals(PacketType.Configuration.Client.PLUGIN_MESSAGE)) {
            WrapperConfigClientPluginMessage message = new WrapperConfigClientPluginMessage(event);
            onPluginMessage(uuid, profile, message.getChannelName(), message.getData());
        } else if (event.getPacketType().equals(PacketType.Play.Client.CLIENT_SETTINGS)) {
            WrapperPlayClientSettings settings = new WrapperPlayClientSettings(event);
            applySettings(profile, settings);
            plugin.engine().scheduleRescan(uuid);
        } else if (event.getPacketType().equals(PacketType.Configuration.Client.CLIENT_SETTINGS)) {
            WrapperConfigClientSettings settings = new WrapperConfigClientSettings(event);
            applySettings(profile, settings);
            plugin.engine().scheduleRescan(uuid);
        } else if (event.getPacketType().equals(PacketType.Play.Client.PONG)) {
            WrapperPlayClientPong pong = new WrapperPlayClientPong(event);
            plugin.pingProbe().onPong(uuid, pong.getId());
        }
    }

    private void onPluginMessage(UUID uuid, ClientProfile profile, String rawChannel, byte[] data) {
        if (rawChannel == null) {
            return;
        }
        String channel = rawChannel.toLowerCase(Locale.ROOT);
        byte[] payload = data == null ? new byte[0] : data;

        if (channel.equals("minecraft:brand")) {
            profile.setRawBrand(decodeString(payload));
        } else if (channel.equals("minecraft:register") || channel.equals("minecraft:unregister")) {
            String joined = new String(payload, StandardCharsets.UTF_8);
            for (String part : joined.split("\u0000")) {
                if (!part.isBlank()) {
                    profile.addRegisteredChannel(part);
                }
            }
        }

        profile.addChannel(channel);
        plugin.echoProbe().onClientMessage(uuid, channel);
        plugin.engine().scheduleRescan(uuid);
    }

    private static void applySettings(ClientProfile profile, com.github.retrooper.packetevents.wrapper.common.client.WrapperCommonClientSettings<?> settings) {
        profile.setSettings(new ClientProfile.ClientSettings(
                settings.getLocale(),
                settings.getViewDistance(),
                settings.getChatVisibility() == null ? null : settings.getChatVisibility().name(),
                settings.isChatColors(),
                settings.getSkinMask(),
                settings.getMainHand() == null ? null : settings.getMainHand().name(),
                settings.getParticleStatus() == null ? null : settings.getParticleStatus().name(),
                settings.isTextFilteringEnabled()
        ));
    }

    /**
     * Client brand is a Minecraft string (VarInt length + UTF-8 bytes) inside
     * the plugin message payload. Be lenient if a client omits the prefix.
     */
    static String decodeString(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return "";
        }
        int index = 0;
        int length = 0;
        int shift = 0;
        while (index < payload.length && shift < 35) {
            int b = payload[index++] & 0xFF;
            length |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        }
        if (length >= 0 && length <= payload.length - index) {
            String decoded = new String(payload, index, length, StandardCharsets.UTF_8);
            if (decoded.chars().allMatch(c -> c >= 0x20 || c == '\t')) {
                return decoded;
            }
        }
        return new String(payload, StandardCharsets.UTF_8).replace("\u0000", "");
    }
}
