package dev.probeguard.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Small helper that accepts either MiniMessage ("&lt;red&gt;hi") or legacy
 * ampersand codes ("&chi") in the same config string.
 */
public final class Text {

    private Text() {
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        if (input.indexOf('<') >= 0) {
            try {
                return MiniMessage.miniMessage().deserialize(input);
            } catch (RuntimeException ignored) {
                // fall through to legacy parsing
            }
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(input);
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Escapes control characters so a spoofed brand cannot mess up chat/logs. */
    public static String escape(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
