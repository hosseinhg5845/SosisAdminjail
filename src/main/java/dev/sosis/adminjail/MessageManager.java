package dev.sosis.adminjail;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class MessageManager {
    private final SosisAdminjailPlugin plugin;
    public MessageManager(SosisAdminjailPlugin plugin) { this.plugin = plugin; }

    public void sendMessage(CommandSender sender, String path, Placeholder... placeholders) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bAdmin&cJail&8]");
        String message = plugin.getConfig().getString("messages." + path);
        if (message == null || message.isEmpty()) return;
        for (Placeholder p : placeholders) message = message.replace("%" + p.key + "%", p.value);
        sender.sendMessage(translate(prefix + " " + message));
    }

    public String format(String path, Placeholder... placeholders) {
        String msg = plugin.getConfig().getString("messages." + path, "");
        for (Placeholder p : placeholders) msg = msg.replace("%" + p.key + "%", p.value);
        return translate(msg);
    }

    public static String translate(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    public static class Placeholder {
        private final String key; private final String value;
        public Placeholder(String key, String value) { this.key = key; this.value = value; }
        public static Placeholder of(String key, Object value) { return new Placeholder(key, String.valueOf(value)); }
    }
}