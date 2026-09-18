package dev.sosis.adminjail.commands;

import dev.sosis.adminjail.SosisAdminjailPlugin;
import dev.sosis.adminjail.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Stats implements org.bukkit.command.CommandExecutor, Listener {
    private final SosisAdminjailPlugin plugin;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private Material backgroundColor;
    private Material separatorColor;
    private String guiTitle;
    private int guiSize;
    private int historyLimit;
    private final Map<UUID, Integer> playerPage = new HashMap<>();
    private final Map<UUID, String> viewingPlayer = new HashMap<>();

    public Stats(SosisAdminjailPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfig() {
        String bg = plugin.getConfig().getString("gui-stats.background-color", "BLACK_STAINED_GLASS_PANE");
        String sep = plugin.getConfig().getString("gui-stats.separator-color", "YELLOW_STAINED_GLASS_PANE");

        try {
            backgroundColor = Material.valueOf(bg.toUpperCase());
        } catch (IllegalArgumentException e) {
            backgroundColor = Material.BLACK_STAINED_GLASS_PANE;
        }

        try {
            separatorColor = Material.valueOf(sep.toUpperCase());
        } catch (IllegalArgumentException e) {
            separatorColor = Material.YELLOW_STAINED_GLASS_PANE;
        }

        guiTitle = plugin.getConfig().getString("gui-stats.title", "&6%player% - Jail Stats");
        guiSize = plugin.getConfig().getInt("gui-stats.size", 54);
        historyLimit = plugin.getConfig().getInt("gui-stats.history-limit", 15);

        if (guiSize < 9 || guiSize > 54 || guiSize % 9 != 0) {
            guiSize = 54;
        }
    }

    public void reload() {
        loadConfig();
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        if (!sender.hasPermission("adminjail.use")) {
            sender.sendMessage(MessageManager.translate("&cYou don't have permission!"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(MessageManager.translate("&cUsage: /adminjailstats <player>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(MessageManager.translate("&cPlayer not found or offline!"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageManager.translate("&cOnly players can use this command!"));
            return true;
        }

        Player player = (Player) sender;
        playerPage.put(player.getUniqueId(), 0);
        viewingPlayer.put(player.getUniqueId(), target.getName());
        openStatsGUI(player, target, 0);
        return true;
    }

    private void openStatsGUI(Player player, Player target, int page) {
        String title = guiTitle.replace("%player%", target.getName()) + " §7(Page " + (page + 1) + ")";
        Inventory gui = Bukkit.createInventory(null, guiSize, MessageManager.translate(title));

        int rows = guiSize / 9;

        for (int row = 0; row < rows; row++) {
            gui.setItem(row * 9, createGlass(backgroundColor));
            gui.setItem(row * 9 + 8, createGlass(backgroundColor));
        }

        for (int i = 0; i < 9; i++) {
            gui.setItem(i, createGlass(backgroundColor));
        }

        for (int i = guiSize - 9; i < guiSize; i++) {
            gui.setItem(i, createGlass(backgroundColor));
        }

        int centerRow = rows / 2;
        int separatorSlot = centerRow * 9 + 2;
        for (int i = 0; i < 5; i++) {
            int slot = separatorSlot + i;
            if (slot >= (centerRow + 1) * 9) break;
            if (slot % 9 != 8 && slot % 9 != 0) {
                gui.setItem(slot, createGlass(separatorColor));
            }
        }

        for (int i = 0; i < 5; i++) {
            int slot = separatorSlot + 9 + i;
            if (slot >= (centerRow + 2) * 9) break;
            if (slot % 9 != 8 && slot % 9 != 0) {
                gui.setItem(slot, createGlass(separatorColor));
            }
        }

        for (int i = 0; i < 5; i++) {
            int slot = separatorSlot + 18 + i;
            if (slot >= (centerRow + 3) * 9) break;
            if (slot % 9 != 8 && slot % 9 != 0) {
                gui.setItem(slot, createGlass(separatorColor));
            }
        }

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        headMeta.setOwningPlayer(target);
        headMeta.setDisplayName("§6§l" + target.getName());
        List<String> headLore = new ArrayList<>();
        headLore.add("§7UUID: §f" + target.getUniqueId().toString());
        headLore.add("§7Online: " + (target.isOnline() ? "§aYes" : "§cNo"));
        headMeta.setLore(headLore);
        head.setItemMeta(headMeta);
        gui.setItem(4, head);

        Map<String, Object> stats = getPlayerStats(target);
        int totalJails = (int) stats.getOrDefault("total_jails", 0);
        int totalBlocks = (int) stats.getOrDefault("total_blocks", 0);

        gui.setItem(10, createStatItem(Material.BOOK, "§eTotal Jails", "§7" + totalJails + " times"));
        gui.setItem(11, createStatItem(Material.DIAMOND_PICKAXE, "§eTotal Blocks", "§7" + totalBlocks + " blocks"));
        gui.setItem(12, createStatItem(Material.REDSTONE, "§eCurrent Status",
                plugin.getJailedPlayers().containsKey(target.getUniqueId()) ? "§cJailed" : "§aFree"));

        Map<String, Object> lastJail = getLastJail(target);
        if (lastJail != null) {
            gui.setItem(14, createStatItem(Material.OAK_SIGN, "§eLast Jail",
                    "§7Jail: §f" + lastJail.get("jail_name"),
                    "§7Reason: §f" + lastJail.get("reason"),
                    "§7Blocks: §f" + lastJail.get("blocks"),
                    "§7Status: §f" + lastJail.get("status"),
                    "§7Date: §f" + lastJail.get("date")));
        }

        List<Map<String, Object>> allHistory = getJailHistory(target, 999);
        int totalPages = (int) Math.ceil((double) allHistory.size() / historyLimit);
        int start = page * historyLimit;
        int end = Math.min(start + historyLimit, allHistory.size());

        int startSlot = (rows - 2) * 9 + 1;
        for (int i = start; i < end; i++) {
            Map<String, Object> h = allHistory.get(i);
            if (startSlot % 9 == 8) {
                startSlot++;
            }
            if (startSlot >= guiSize - 9) break;

            String statusColor = h.get("status").equals("jailed") ? "§c" : "§a";
            String statusText = h.get("status").equals("jailed") ? "Jailed" : "Released";

            gui.setItem(startSlot++, createStatItem(Material.PAPER, "§7#" + h.get("id"),
                    "§7Jail: §f" + h.get("jail_name"),
                    "§7Blocks: §f" + h.get("blocks") + " blocks",
                    "§7Reason: §f" + h.get("reason"),
                    "§7Status: " + statusColor + statusText,
                    "§7Date: §f" + h.get("date")));
        }

        if (totalPages > 1) {
            int navRow = rows - 1;
            int navStart = navRow * 9 + 1;

            if (page > 0) {
                gui.setItem(navStart, createNavItem(Material.ARROW, "§aPrevious Page", "§7Click to go to page " + page));
            }

            gui.setItem(navStart + 4, createNavItem(Material.BOOK, "§ePage " + (page + 1) + "/" + totalPages, "§7Total " + allHistory.size() + " records"));

            if (page < totalPages - 1) {
                gui.setItem(navStart + 7, createNavItem(Material.ARROW, "§aNext Page", "§7Click to go to page " + (page + 2)));
            }
        }

        gui.setItem(guiSize - 1, createStatItem(Material.BARRIER, "§cClose", "§7Click to close"));

        player.openInventory(gui);
    }

    private ItemStack createNavItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(MessageManager.translate(name));
        List<String> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(MessageManager.translate(line));
        }
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGlass(Material material) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        return glass;
    }

    private ItemStack createStatItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(MessageManager.translate(name));
        List<String> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(MessageManager.translate(line));
        }
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    private Map<String, Object> getPlayerStats(Player player) {
        Map<String, Object> result = new HashMap<>();
        try {
            Connection conn = plugin.getDbManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) as total_jails, SUM(blocks) as total_blocks FROM jail_logs WHERE player_name = ? AND status = 'jailed'"
            );
            ps.setString(1, player.getName());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                result.put("total_jails", rs.getInt("total_jails"));
                result.put("total_blocks", rs.getInt("total_blocks"));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    private Map<String, Object> getLastJail(Player player) {
        try {
            Connection conn = plugin.getDbManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT jail_name, reason, blocks, status, timestamp FROM jail_logs WHERE player_name = ? ORDER BY id DESC LIMIT 1"
            );
            ps.setString(1, player.getName());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> result = new HashMap<>();
                result.put("jail_name", rs.getString("jail_name"));
                result.put("reason", rs.getString("reason"));
                result.put("blocks", rs.getInt("blocks"));
                result.put("status", rs.getString("status"));
                long timestamp = rs.getLong("timestamp");
                result.put("date", sdf.format(new Date(timestamp)));
                rs.close();
                ps.close();
                return result;
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private List<Map<String, Object>> getJailHistory(Player player, int limit) {
        List<Map<String, Object>> history = new ArrayList<>();
        try {
            Connection conn = plugin.getDbManager().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, jail_name, reason, blocks, status, timestamp FROM jail_logs WHERE player_name = ? ORDER BY id DESC LIMIT ?"
            );
            ps.setString(1, player.getName());
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("id", rs.getInt("id"));
                entry.put("jail_name", rs.getString("jail_name"));
                entry.put("reason", rs.getString("reason"));
                entry.put("blocks", rs.getInt("blocks"));
                entry.put("status", rs.getString("status"));
                long timestamp = rs.getLong("timestamp");
                entry.put("date", sdf.format(new Date(timestamp)));
                history.add(entry);
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTitle().contains(" - Jail Stats"))) return;
        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        ItemStack item = event.getCurrentItem();

        if (item.getType() == Material.BARRIER) {
            event.getWhoClicked().closeInventory();
            return;
        }

        if (item.getType() == Material.ARROW) {
            Player player = (Player) event.getWhoClicked();
            String playerName = viewingPlayer.get(player.getUniqueId());
            Player target = Bukkit.getPlayer(playerName);
            if (target == null) return;

            int currentPage = playerPage.getOrDefault(player.getUniqueId(), 0);
            String displayName = item.getItemMeta().getDisplayName();

            if (displayName.contains("Previous")) {
                playerPage.put(player.getUniqueId(), currentPage - 1);
                openStatsGUI(player, target, currentPage - 1);
            } else if (displayName.contains("Next")) {
                playerPage.put(player.getUniqueId(), currentPage + 1);
                openStatsGUI(player, target, currentPage + 1);
            }
        }
    }
}