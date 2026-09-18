package dev.sosis.adminjail.commands;

import dev.sosis.adminjail.Data;
import dev.sosis.adminjail.MessageManager;
import dev.sosis.adminjail.SosisAdminjailPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class AdminJailAdmin implements org.bukkit.command.CommandExecutor {
    private final SosisAdminjailPlugin plugin;

    public AdminJailAdmin(SosisAdminjailPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!sender.hasPermission("adminjail.admin")) {
            sender.sendMessage(MessageManager.translate("&cNo permission"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("setmine") || sub.equals("delmine") || sub.equals("listmine") || sub.equals("cancelmine")) {
            if (!(sender instanceof Player) && !sub.equals("listmine")) {
                sender.sendMessage(MessageManager.translate("&cOnly players can use this command!"));
                return true;
            }
            Player p = (Player) sender;

            if (sub.equals("setmine")) {
                if (args.length < 2) {
                    p.sendMessage(MessageManager.translate("&cUsage: /adminjailadmin setmine <name>"));
                    return true;
                }
                plugin.getMineManager().startMineSelection(p, args[1]);
                return true;
            }

            if (sub.equals("delmine")) {
                if (args.length < 2) {
                    p.sendMessage(MessageManager.translate("&cUsage: /adminjailadmin delmine <name>"));
                    return true;
                }
                if (!plugin.getMineManager().hasMine(args[1])) {
                    p.sendMessage(MessageManager.translate("&cMine '" + args[1] + "' not found!"));
                    return true;
                }
                plugin.getMineManager().deleteMine(args[1]);
                p.sendMessage(MessageManager.translate("&cMine '" + args[1] + "' deleted successfully!"));
                return true;
            }

            if (sub.equals("listmine")) {
                if (plugin.getMineManager().getMineNames().isEmpty()) {
                    sender.sendMessage(MessageManager.translate("&eNo mines exist!"));
                    return true;
                }
                sender.sendMessage(MessageManager.translate("&6=== Mines ==="));
                for (String name : plugin.getMineManager().getMineNames()) {
                    Location center = plugin.getMineManager().getMineCenter(name);
                    sender.sendMessage(MessageManager.translate("&e" + name + " &7- &f" + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ()));
                }
                return true;
            }

            if (sub.equals("cancelmine")) {
                plugin.getMineManager().cancelSelection(p);
                return true;
            }
        }

        if (sub.equals("debug")) {
            sender.sendMessage(MessageManager.translate("&6=== Debug Info ==="));
            sender.sendMessage(MessageManager.translate("&7Jailed Players (RAM): &f" + plugin.getJailedPlayers().size()));
            sender.sendMessage(MessageManager.translate("&7Offline Jailed Players: &f" + plugin.getOfflineJailedPlayers().size()));
            sender.sendMessage(MessageManager.translate("&7Jail Locations: &f" + plugin.getJailLocations().size()));
            sender.sendMessage(MessageManager.translate("&7Spawn Locations: &f" + plugin.getSpawnLocations().size()));
            sender.sendMessage(MessageManager.translate("&7Database Connected: &f" + plugin.getDbManager().isConnected()));
            sender.sendMessage(MessageManager.translate("&7Storage Type: &f" + plugin.getDbManager().getStorageType()));
            return true;
        }

        if (!(sender instanceof Player) && (sub.equals("setjail") || sub.equals("setspawn") || sub.equals("tp") || sub.equals("visit"))) {
            sender.sendMessage(MessageManager.translate("&cOnly players can use this command."));
            return true;
        }
        Player p = (Player) sender;

        switch (sub) {
            case "setjail":
                if (args.length < 2) {
                    p.sendMessage(MessageManager.translate("&cUsage: /adminjailadmin setjail <name>"));
                    return true;
                }
                plugin.setJailLocation(args[1], p.getLocation());
                p.sendMessage(MessageManager.translate("&aJail '" + args[1] + "' set."));
                break;

            case "deljail":
                if (args.length < 2) {
                    p.sendMessage(MessageManager.translate("&cUsage: /adminjailadmin deljail <name>"));
                    return true;
                }
                plugin.removeJailLocation(args[1]);
                p.sendMessage(MessageManager.translate("&cJail '" + args[1] + "' removed."));
                break;

            case "setspawn":
                if (args.length < 2) {
                    p.sendMessage(MessageManager.translate("&cUsage: /adminjailadmin setspawn <name>"));
                    return true;
                }
                plugin.setSpawnLocation(args[1], p.getLocation());
                p.sendMessage(MessageManager.translate("&aSpawn '" + args[1] + "' set."));
                break;

            case "delspawn":
                if (args.length < 2) {
                    p.sendMessage(MessageManager.translate("&cUsage: /adminjailadmin delspawn <name>"));
                    return true;
                }
                plugin.removeSpawnLocation(args[1]);
                p.sendMessage(MessageManager.translate("&cSpawn '" + args[1] + "' removed."));
                break;

            case "tp":
                if (args.length < 3) {
                    p.sendMessage(MessageManager.translate("&cUsage: /adminjailadmin tp <jail/spawn> <name>"));
                    return true;
                }
                String type = args[1].toLowerCase();
                String name = args[2];
                Location loc = type.equals("jail") ? plugin.getJailLocation(name) : plugin.getSpawnLocation(name);
                if (loc == null) {
                    p.sendMessage(MessageManager.translate("&cLocation '" + name + "' not found."));
                } else {
                    p.teleport(loc);
                    p.sendMessage(MessageManager.translate("&aTeleported to " + type + " '" + name + "'."));
                }
                break;

            case "visit":
                if (args.length < 2) {
                    p.sendMessage(MessageManager.translate("&cUsage: /adminjailadmin visit <player>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    p.sendMessage(MessageManager.translate("&cPlayer not found!"));
                    return true;
                }
                if (!plugin.getJailedPlayers().containsKey(target.getUniqueId())) {
                    p.sendMessage(MessageManager.translate("&cPlayer is not jailed!"));
                    return true;
                }
                p.teleport(target.getLocation());
                p.sendMessage(MessageManager.translate("&aYou are now spectating " + target.getName()));
                break;

            case "transfer":
                if (args.length < 3) {
                    p.sendMessage(MessageManager.translate("&cUsage: /adminjailadmin transfer <player> <jailname>"));
                    return true;
                }
                Player transferTarget = Bukkit.getPlayer(args[1]);
                String newJail = args[2];
                if (transferTarget == null) {
                    p.sendMessage(MessageManager.translate("&cPlayer not found!"));
                    return true;
                }
                if (!plugin.getJailedPlayers().containsKey(transferTarget.getUniqueId())) {
                    p.sendMessage(MessageManager.translate("&cPlayer is not jailed!"));
                    return true;
                }
                Location newLoc = plugin.getJailLocation(newJail);
                if (newLoc == null) {
                    p.sendMessage(MessageManager.translate("&cJail not found!"));
                    return true;
                }
                Data transferData = plugin.getJailedPlayers().get(transferTarget.getUniqueId());
                Data newData = new Data(transferTarget.getUniqueId(), transferData.getSavedInventory(),
                        transferData.getNeededBlocks(), transferData.getBrokenBlocks(), newJail, transferData.getReason());
                plugin.getJailedPlayers().put(transferTarget.getUniqueId(), newData);
                transferTarget.teleport(newLoc);
                p.sendMessage(MessageManager.translate("&aPlayer transferred to " + newJail));
                break;

            case "reload":
                plugin.reloadAll();
                plugin.getMineManager().reload();
                plugin.getMessageManager().sendMessage(sender, "reload-complete");
                break;

            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void sendHelp(org.bukkit.command.CommandSender sender) {
        sender.sendMessage(MessageManager.translate("&6=== AdminJail Admin Commands ==="));
        sender.sendMessage(MessageManager.translate("&e/adminjailadmin setjail <name>"));
        sender.sendMessage(MessageManager.translate("&e/adminjailadmin deljail <name>"));
        sender.sendMessage(MessageManager.translate("&e/adminjailadmin setspawn <name>"));
        sender.sendMessage(MessageManager.translate("&e/adminjailadmin delspawn <name>"));
        sender.sendMessage(MessageManager.translate("&e/adminjailadmin tp <jail/spawn> <name>"));
        sender.sendMessage(MessageManager.translate("&e/adminjailadmin visit <player>"));
        sender.sendMessage(MessageManager.translate("&e/adminjailadmin transfer <player> <jailname>"));
        sender.sendMessage(MessageManager.translate("&e/adminjailadmin setmine <name>"));
        sender.sendMessage(MessageManager.translate("&e/adminjailadmin delmine <name>"));
        sender.sendMessage(MessageManager.translate("&e/adminjailadmin listmine"));
        sender.sendMessage(MessageManager.translate("&e/adminjailadmin cancelmine"));
        sender.sendMessage(MessageManager.translate("&e/adminjailadmin reload"));
        sender.sendMessage(MessageManager.translate("&e/adminjailadmin debug"));
    }
}