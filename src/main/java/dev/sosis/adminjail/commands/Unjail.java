package dev.sosis.adminjail.commands;

import dev.sosis.adminjail.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class Unjail implements org.bukkit.command.CommandExecutor {
    private final SosisAdminjailPlugin plugin;

    public Unjail(SosisAdminjailPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        if (!sender.hasPermission("adminjail.use")) {
            sender.sendMessage(MessageManager.translate("&cYou don't have permission!"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(MessageManager.translate("&cUsage: /unjail <player>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);

        if (target != null && plugin.getJailedPlayers().containsKey(target.getUniqueId())) {
            Data data = plugin.getJailedPlayers().get(target.getUniqueId());
            String reason = data.getReason();

            plugin.unjailPlayer(target);

            sender.sendMessage(MessageManager.translate("&a" + target.getName() + " has been unjailed."));

            if (plugin.getWebHook() != null) {
                plugin.getWebHook().sendUnjailData(target.getName(), reason);
            }

            if (plugin.getDiscordWebhook() != null) {
                try {
                    plugin.getDiscordWebhook().sendUnjail(target.getName(), reason);
                } catch (Exception e) {
                    plugin.getLogger().warning("Discord sendUnjail error: " + e.getMessage());
                }
            }

            if (plugin.getConfig().getBoolean("broadcast.unjail-enabled", false)) {
                String msg = plugin.getConfig().getString("messages.unjail-broadcast", "&a%player% &ewas freed from jail. Reason: &c%reason%");
                msg = msg.replace("%player%", target.getName()).replace("%reason%", reason);
                Bukkit.broadcastMessage(MessageManager.translate(msg));
            }
        } else if (plugin.getOfflineJailedPlayers().containsKey(args[0].toLowerCase())) {
            String reason = plugin.getOfflineJailedPlayers().get(args[0].toLowerCase()).getReason();

            plugin.getOfflineJailedPlayers().remove(args[0].toLowerCase());

            try {
                plugin.getDbManager().removeJailedPlayerByName(args[0]);
                plugin.getLogger().info("Player " + args[0] + " removed from offline database.");
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to remove offline player from database: " + e.getMessage());
            }

            sender.sendMessage(MessageManager.translate("&a" + args[0] + " has been unjailed."));

            if (plugin.getWebHook() != null) {
                plugin.getWebHook().sendUnjailData(args[0], reason);
            }

            if (plugin.getDiscordWebhook() != null) {
                try {
                    plugin.getDiscordWebhook().sendUnjail(args[0], reason);
                } catch (Exception e) {
                    plugin.getLogger().warning("Discord sendUnjail error: " + e.getMessage());
                }
            }

            if (plugin.getConfig().getBoolean("broadcast.unjail-enabled", false)) {
                String msg = plugin.getConfig().getString("messages.unjail-broadcast", "&a%player% &ewas freed from jail. Reason: &c%reason%");
                msg = msg.replace("%player%", args[0]).replace("%reason%", reason);
                Bukkit.broadcastMessage(MessageManager.translate(msg));
            }
        } else {
            sender.sendMessage(MessageManager.translate("&cPlayer is not in jail!"));
        }
        return true;
    }
}