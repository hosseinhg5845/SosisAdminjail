package dev.sosis.adminjail.listeners;

import dev.sosis.adminjail.*;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class Join implements Listener {
    private final SosisAdminjailPlugin plugin;

    public Join(SosisAdminjailPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String nameLower = player.getName().toLowerCase();

        if (plugin.getOfflineJailedPlayers().containsKey(nameLower)) {
            OfflineData offline = plugin.getOfflineJailedPlayers().get(nameLower);

            if (offline.getStatus() != null && offline.getStatus().equals("unjailed")) {
                plugin.getOfflineJailedPlayers().remove(nameLower);
                plugin.getDbManager().removeJailedPlayer(player.getUniqueId());
                plugin.getLogger().info("Player " + player.getName() + " was unjailed. Skipping jail.");
                player.sendMessage(MessageManager.translate("&a&l✅ You have been unjailed!"));
                return;
            }

            if (plugin.getJailedPlayers().containsKey(player.getUniqueId())) {
                plugin.getLogger().warning("Player " + player.getName() + " is already in jailedPlayers map! Skipping duplicate.");
                return;
            }

            int brokenBlocks = offline.getBrokenBlocks();
            int neededBlocks = offline.getNeededBlocks();

            if (brokenBlocks >= neededBlocks) {
                plugin.getOfflineJailedPlayers().remove(nameLower);
                plugin.getDbManager().removeJailedPlayer(player.getUniqueId());
                plugin.getLogger().info("Player " + player.getName() + " already completed sentence on join! Unjailing...");
                player.sendMessage(MessageManager.translate("&a&l✅ You have completed your sentence! You are free!"));
                return;
            }

            Data activeData = new Data(
                    player.getUniqueId(),
                    offline.getSavedInventory(),
                    neededBlocks,
                    brokenBlocks,
                    offline.getJailName(),
                    offline.getReason()
            );

            plugin.getJailedPlayers().put(player.getUniqueId(), activeData);
            plugin.getLogger().info("Added player to jailedPlayers on join: " + player.getName() + " | Blocks: " + brokenBlocks + "/" + neededBlocks);

            player.getInventory().clear();
            player.setGameMode(GameMode.SURVIVAL);
            player.getInventory().addItem(plugin.getJailPickaxe());

            Location jailLoc = plugin.getJailLocation(activeData.getJailName());
            if (jailLoc != null) {
                player.teleport(jailLoc);
            } else {
                plugin.getLogger().warning("Jail location '" + activeData.getJailName() + "' not found for " + player.getName());
                if (!plugin.getJailLocations().isEmpty()) {
                    Location fallback = plugin.getJailLocations().values().iterator().next();
                    player.teleport(fallback);
                }
            }

            plugin.createBossBar(player, activeData);

            int remaining = neededBlocks - brokenBlocks;
            player.sendMessage(MessageManager.translate("&cYou are still jailed! &e" + remaining + " blocks remaining."));
            player.sendMessage(MessageManager.translate("&7Reason: &e" + activeData.getReason()));
        } else {
            plugin.getLogger().info("Player " + player.getName() + " is not in offlineJailedPlayers.");

            if (plugin.getJailedPlayers().containsKey(player.getUniqueId())) {
                plugin.getLogger().info("Player " + player.getName() + " is already in jailedPlayers map.");
                return;
            }
        }
    }
}