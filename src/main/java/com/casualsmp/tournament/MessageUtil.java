package com.casualsmp.tournament;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageUtil {

    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {
    }

    public static Component colorize(String legacyText) {
        if (legacyText == null || legacyText.isEmpty()) {
            return Component.empty();
        }
        return LEGACY_AMPERSAND.deserialize(legacyText);
    }

    public static String plain(Component component) {
        return LEGACY_AMPERSAND.serialize(component);
    }

    public static void send(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(colorize(message));
    }

    public static void sendPrefixed(CommandSender sender, CasualTournament plugin, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&6&lTOURNAMENT &8» &f");
        send(sender, prefix + message);
    }

    public static void broadcast(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        Component comp = colorize(message);
        Bukkit.getServer().sendMessage(comp);
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        if (player == null || !player.isOnline()) {
            return;
        }
        net.kyori.adventure.title.Title adventureTitle = net.kyori.adventure.title.Title.title(
                colorize(title),
                colorize(subtitle),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(fadeInTicks * 50L),
                        java.time.Duration.ofMillis(stayTicks * 50L),
                        java.time.Duration.ofMillis(fadeOutTicks * 50L)
                )
        );
        player.showTitle(adventureTitle);
    }
}
