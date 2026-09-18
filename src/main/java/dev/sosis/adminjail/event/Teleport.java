package dev.sosis.adminjail.event;

import dev.sosis.adminjail.SosisAdminjailPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

public class Teleport implements Listener {
    private final SosisAdminjailPlugin plugin;
    public Teleport(SosisAdminjailPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!plugin.isBlockTeleport()) return;
        Player player = event.getPlayer();
        if (plugin.getJailedPlayers().containsKey(player.getUniqueId())) {
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN ||
                    event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND) return;
            event.setCancelled(true);
            plugin.getMessageManager().sendMessage(player, "no-teleport");
        }
    }
}