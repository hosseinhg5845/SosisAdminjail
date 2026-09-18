package dev.sosis.adminjail.event;

import dev.sosis.adminjail.SosisAdminjailPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class CommandBlocker implements Listener {
    private final SosisAdminjailPlugin plugin;
    public CommandBlocker(SosisAdminjailPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.isBlockCommands()) return;
        Player player = event.getPlayer();
        if (plugin.getJailedPlayers().containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.getMessageManager().sendMessage(player, "no-command");
        }
    }
}