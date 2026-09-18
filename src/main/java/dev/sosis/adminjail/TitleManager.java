package dev.sosis.adminjail;

import org.bukkit.entity.Player;

public class TitleManager {
    private final SosisAdminjailPlugin plugin;
    private final boolean enabled;

    public TitleManager(SosisAdminjailPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("features.jail-title", true);
    }

    public void sendJailedTitle(Player player, String reason) {
        if (!enabled) return;
        if (player == null || !player.isOnline()) return;
        try {
            String title = plugin.getConfig().getString("titles.jailed-title", "&c&lYOU HAVE BEEN JAILED!");
            String subtitle = plugin.getConfig().getString("titles.jailed-subtitle", "&7Reason: &e%reason%").replace("%reason%", reason);
            int fadeIn = plugin.getConfig().getInt("titles.fade-in", 10);
            int stay = plugin.getConfig().getInt("titles.stay", 70);
            int fadeOut = plugin.getConfig().getInt("titles.fade-out", 20);
            player.sendTitle(MessageManager.translate(title), MessageManager.translate(subtitle), fadeIn, stay, fadeOut);
        } catch (Exception e) {
            player.sendMessage("");
            player.sendMessage(MessageManager.translate("&c&lYOU HAVE BEEN JAILED!"));
            player.sendMessage(MessageManager.translate("&7Reason: &e" + reason));
            player.sendMessage("");
        }
    }

    public void sendFreedTitle(Player player) {
        if (!enabled) return;
        if (player == null || !player.isOnline()) return;
        try {
            String title = plugin.getConfig().getString("titles.freed-title", "&a&lYOU HAVE BEEN FREED!");
            String subtitle = plugin.getConfig().getString("titles.freed-subtitle", "&7Thank you for your service");
            int fadeIn = plugin.getConfig().getInt("titles.fade-in", 10);
            int stay = plugin.getConfig().getInt("titles.stay", 70);
            int fadeOut = plugin.getConfig().getInt("titles.fade-out", 20);
            player.sendTitle(MessageManager.translate(title), MessageManager.translate(subtitle), fadeIn, stay, fadeOut);
        } catch (Exception e) {
            player.sendMessage("");
            player.sendMessage(MessageManager.translate("&a&lYOU HAVE BEEN FREED!"));
            player.sendMessage("");
        }
    }
}