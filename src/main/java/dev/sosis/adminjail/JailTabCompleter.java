package dev.sosis.adminjail;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class JailTabCompleter implements TabCompleter {
    private final SosisAdminjailPlugin plugin;
    public JailTabCompleter(SosisAdminjailPlugin plugin) { this.plugin = plugin; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("adminjailadmin")) {
            if (args.length == 1) {
                return filter(Arrays.asList(
                        "setjail", "deljail", "setspawn", "delspawn",
                        "tp", "reload", "visit", "transfer",
                        "setmine", "delmine", "listmine", "cancelmine"
                ), args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
                return filter(Arrays.asList("jail", "spawn"), args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("tp")) {
                if (args[1].equalsIgnoreCase("jail")) {
                    return filter(new ArrayList<>(plugin.getJailLocations().keySet()), args[2]);
                }
                if (args[1].equalsIgnoreCase("spawn")) {
                    return filter(new ArrayList<>(plugin.getSpawnLocations().keySet()), args[2]);
                }
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("transfer")) {
                return filter(new ArrayList<>(plugin.getJailLocations().keySet()), args[1]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("deljail")) {
                return filter(new ArrayList<>(plugin.getJailLocations().keySet()), args[1]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("delspawn")) {
                return filter(new ArrayList<>(plugin.getSpawnLocations().keySet()), args[1]);
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("setmine") || args[0].equalsIgnoreCase("delmine"))) {
                return null;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("visit")) {
                return null;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("setjail")) {
                return null;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("setspawn")) {
                return null;
            }
        }
        if (cmd.getName().equalsIgnoreCase("adminjail") && args.length == 3) {
            return filter(new ArrayList<>(plugin.getJailLocations().keySet()), args[2]);
        }
        if (cmd.getName().equalsIgnoreCase("adminjail") && args.length == 1) {
            return null;
        }
        if (cmd.getName().equalsIgnoreCase("unjail") && args.length == 1) {
            return null;
        }
        if (cmd.getName().equalsIgnoreCase("adminjailstats") && args.length == 1) {
            return null;
        }
        return null;
    }

    private List<String> filter(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}