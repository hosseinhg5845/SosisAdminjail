package dev.sosis.adminjail.event;

import dev.sosis.adminjail.SosisAdminjailPlugin;
import dev.sosis.adminjail.Data;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class Death implements Listener {
    private final SosisAdminjailPlugin plugin;
    public Death(SosisAdminjailPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getJailedPlayers().containsKey(event.getEntity().getUniqueId())) return;
        if (plugin.isClearDropsOnDeath()) event.getDrops().clear();
        if (plugin.isClearExpOnDeath()) event.setDroppedExp(0);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getJailedPlayers().containsKey(player.getUniqueId())) return;
        if (plugin.isRespawnInJail()) {
            Data data = plugin.getJailedPlayers().get(player.getUniqueId());
            Location jailLoc = plugin.getJailLocation(data.getJailName());
            if (jailLoc != null) event.setRespawnLocation(jailLoc);
        }
        if (!player.getInventory().contains(plugin.getJailPickaxe().getType())) {
            player.getInventory().addItem(plugin.getJailPickaxe());
        }
    }
}