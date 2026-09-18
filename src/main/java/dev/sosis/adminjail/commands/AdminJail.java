package dev.sosis.adminjail.commands;

import dev.sosis.adminjail.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class AdminJail implements org.bukkit.command.CommandExecutor {
    private final SosisAdminjailPlugin plugin;

    public AdminJail(SosisAdminjailPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        if (!sender.hasPermission("adminjail.use")) {
            sender.sendMessage(MessageManager.translate("&cYou don't have permission!"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageManager.translate("&cUsage: /adminjail <player> <blocks> [jailname] [reason]"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        int blocks;
        try {
            blocks = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageManager.translate("&cBlocks must be a number!"));
            return true;
        }

        if (blocks <= 0) {
            sender.sendMessage(MessageManager.translate("&cBlocks must be greater than 0!"));
            return true;
        }

        if (target != null) {
            if (plugin.getJailedPlayers().containsKey(target.getUniqueId())) {
                sender.sendMessage(MessageManager.translate("&c" + target.getName() + " is already jailed!"));
                target.sendMessage(MessageManager.translate("&cYou are already jailed!"));
                return true;
            }

            if (plugin.getOfflineJailedPlayers().containsKey(target.getName().toLowerCase())) {
                OfflineData existing = plugin.getOfflineJailedPlayers().get(target.getName().toLowerCase());
                if (existing.getStatus() == null || !existing.getStatus().equals("unjailed")) {
                    sender.sendMessage(MessageManager.translate("&c" + target.getName() + " is already in jail database!"));
                    return true;
                }
            }
        }

        String jailName;
        String reason;
        List<String> jailNames = new ArrayList<>(plugin.getJailLocations().keySet());

        if (args.length >= 3 && jailNames.contains(args[2])) {
            jailName = args[2];
            reason = (args.length > 3) ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "No reason provided.";
        } else {
            jailName = "main";
            reason = (args.length > 2) ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "No reason provided.";
        }

        if (plugin.getJailLocation(jailName) == null) {
            if (jailNames.isEmpty()) {
                sender.sendMessage(MessageManager.translate("&cNo jail locations exist! Set one with /adminjailadmin setjail <name>"));
                return true;
            }
            String randomJail = jailNames.get(new Random().nextInt(jailNames.size()));
            sender.sendMessage(MessageManager.translate("&eJail '" + jailName + "' not found. Using random jail: " + randomJail));
            jailName = randomJail;
        }

        if (target == null) {
            if (plugin.getOfflineJailedPlayers().containsKey(args[0].toLowerCase())) {
                OfflineData existing = plugin.getOfflineJailedPlayers().get(args[0].toLowerCase());
                if (existing.getStatus() == null || !existing.getStatus().equals("unjailed")) {
                    sender.sendMessage(MessageManager.translate("&c" + args[0] + " is already in jail database!"));
                    return true;
                }
            }

            OfflineData offline = new OfflineData(null, args[0], new ArrayList<>(), blocks, 0, jailName, reason, "jailed");
            plugin.getOfflineJailedPlayers().put(args[0].toLowerCase(), offline);
            plugin.getDbManager().saveJailedPlayer(offline);
            sender.sendMessage(MessageManager.translate("&a" + args[0] + " will be jailed when they join."));

            if (plugin.getWebHook() != null) {
                plugin.getWebHook().sendJailData(args[0], jailName, blocks, reason, "offline-jailed");
            }

            if (plugin.getDiscordWebhook() != null) {
                try {
                    plugin.getDiscordWebhook().sendJail(args[0], jailName, blocks, reason);
                } catch (Exception e) {
                    plugin.getLogger().warning("Discord sendJail error: " + e.getMessage());
                }
            }

            if (plugin.getConfig().getBoolean("broadcast.jail-enabled", true)) {
                String msg = plugin.getConfig().getString("messages.jail-broadcast", "&c%player% &ewas jailed in &c%jail% &efor &c%blocks% &eblocks. Reason: &c%reason%");
                msg = msg.replace("%player%", args[0]).replace("%jail%", jailName).replace("%blocks%", String.valueOf(blocks)).replace("%reason%", reason);
                Bukkit.broadcastMessage(MessageManager.translate(msg));
            }
            return true;
        }

        List<ItemStack> inv = new ArrayList<>(Arrays.asList(target.getInventory().getContents()));
        Data data = new Data(target.getUniqueId(), inv, blocks, 0, jailName, reason);
        OfflineData offlineData = new OfflineData(target.getUniqueId(), target.getName(), inv, blocks, 0, jailName, reason, "jailed");

        plugin.getJailedPlayers().put(target.getUniqueId(), data);
        plugin.getOfflineJailedPlayers().put(target.getName().toLowerCase(), offlineData);
        plugin.getDbManager().saveJailedPlayer(offlineData);

        target.getInventory().clear();
        target.setGameMode(GameMode.SURVIVAL);
        target.getInventory().addItem(plugin.getJailPickaxe());
        target.teleport(plugin.getJailLocation(jailName));

        plugin.createBossBar(target, data);
        plugin.getTitleManager().sendJailedTitle(target, reason);
        plugin.getSoundManager().playJailSound(target);

        target.sendMessage(MessageManager.translate("&cYou have been jailed!"));
        target.sendMessage(MessageManager.translate("&7Blocks to break: &e" + blocks));
        target.sendMessage(MessageManager.translate("&7Reason: &e" + reason));
        sender.sendMessage(MessageManager.translate("&a" + target.getName() + " has been jailed."));

        if (plugin.getWebHook() != null) {
            plugin.getWebHook().sendJailData(target.getName(), jailName, blocks, reason, "jailed");
        }

        if (plugin.getDiscordWebhook() != null) {
            try {
                plugin.getDiscordWebhook().sendJail(target.getName(), jailName, blocks, reason);
            } catch (Exception e) {
                plugin.getLogger().warning("Discord sendJail error: " + e.getMessage());
            }
        }

        if (plugin.getConfig().getBoolean("broadcast.jail-enabled", true)) {
            String msg = plugin.getConfig().getString("messages.jail-broadcast", "&c%player% &ewas jailed in &c%jail% &efor &c%blocks% &eblocks. Reason: &c%reason%");
            msg = msg.replace("%player%", target.getName()).replace("%jail%", jailName).replace("%blocks%", String.valueOf(blocks)).replace("%reason%", reason);
            Bukkit.broadcastMessage(MessageManager.translate(msg));
        }

        return true;
    }
}