package dev.sosis.adminjail.event;

import dev.sosis.adminjail.*;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class Jail implements Listener {
    private final SosisAdminjailPlugin plugin;

    public Jail(SosisAdminjailPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isJailPickaxe(ItemStack item) {
        if (item == null) return false;
        ItemStack pick = plugin.getJailPickaxe();
        if (!item.hasItemMeta() || !pick.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        ItemMeta pickMeta = pick.getItemMeta();
        return meta.hasDisplayName() && pickMeta.hasDisplayName() && meta.getDisplayName().equals(pickMeta.getDisplayName());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getJailedPlayers().containsKey(player.getUniqueId())) {
            if (Debug.isDebugMode()) {
                plugin.getDebugLogger().debug("Player " + player.getName() + " broke a block but is not in jailedPlayers map!");
            }
            return;
        }

        event.setDropItems(false);
        Data data = plugin.getJailedPlayers().get(player.getUniqueId());

        if (isJailPickaxe(player.getInventory().getItemInMainHand())) {
            data.addBrokenBlock();
            int current = data.getBrokenBlocks();
            int needed = data.getNeededBlocks();

            if (Debug.isDebugMode()) {
                plugin.getDebugLogger().debug("Player " + player.getName() + " broke a block! Current: " + current + "/" + needed);
            }

            if (plugin.isShowActionBar()) {
                try {
                    String template = plugin.getConfig().getString("messages.action-bar", "&c%current%&7/&a%needed% &7(&6%progress%%&7)");
                    int progress = (int) (((double) current / needed) * 100);
                    String msg = template.replace("%current%", String.valueOf(current))
                            .replace("%needed%", String.valueOf(needed))
                            .replace("%progress%", String.valueOf(progress));
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(MessageManager.translate(msg)));
                } catch (Exception ignored) {}
            }

            plugin.updateBossBar(player, data);

            OfflineData offline = new OfflineData(
                    player.getUniqueId(),
                    player.getName(),
                    data.getSavedInventory(),
                    needed,
                    current,
                    data.getJailName(),
                    data.getReason(),
                    "jailed"
            );
            plugin.getDbManager().saveJailedPlayer(offline);
            plugin.getOfflineJailedPlayers().put(player.getName().toLowerCase(), offline);

            if (plugin.getWebHook() != null) {
                plugin.getWebHook().sendProgressUpdate(player.getName(), current, needed, data.getJailName());
            }

            if (plugin.getDiscordWebhook() != null) {
                try {
                    plugin.getDiscordWebhook().sendProgress(player.getName(), current, needed, data.getJailName());
                } catch (Exception e) {
                    plugin.getDebugLogger().warning("Discord sendProgress error: " + e.getMessage());
                }
            }

            if (current >= needed) {
                plugin.getDebugLogger().info("Player " + player.getName() + " completed sentence! Unjailing...");
                plugin.unjailPlayer(player);
                plugin.removeBossBar(player);
                player.sendTitle(MessageManager.translate("&a&lYOU ARE FREE!"), MessageManager.translate("&7You completed your sentence!"), 10, 70, 20);
                player.sendMessage(MessageManager.translate("&a&l✅ You have completed your sentence! You are free!"));
            }
        } else if (plugin.isBlockWrongPickaxe()) {
            event.setCancelled(true);
            plugin.getMessageManager().sendMessage(player, "protected-block");
        }
    }
}