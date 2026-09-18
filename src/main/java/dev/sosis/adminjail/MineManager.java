package dev.sosis.adminjail;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MineManager implements Listener {
    private final SosisAdminjailPlugin plugin;
    private final File mineFile;
    private YamlConfiguration mineConfig;
    private boolean enabled;
    private int respawnTime;
    private int mineSize;
    private boolean dropsEnabled;
    private double dropChance;
    private final Map<String, MineData> mines = new ConcurrentHashMap<>();
    private final Map<Location, String> locationToMine = new ConcurrentHashMap<>();
    private final Map<Location, Material> originalBlocks = new ConcurrentHashMap<>();
    private final Map<Location, Integer> respawnTasks = new ConcurrentHashMap<>();
    private final Set<UUID> selectingPlayers = new HashSet<>();
    private String selectedMineName = null;

    public MineManager(SosisAdminjailPlugin plugin) {
        this.plugin = plugin;
        this.mineFile = new File(plugin.getDataFolder(), "mine.yml");
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfig() {
        if (!mineFile.exists()) {
            plugin.saveResource("mine.yml", false);
        }
        mineConfig = YamlConfiguration.loadConfiguration(mineFile);
        enabled = mineConfig.getBoolean("enabled", true);
        respawnTime = mineConfig.getInt("respawn-time", 30);
        mineSize = mineConfig.getInt("mine-size", 5);
        dropsEnabled = mineConfig.getBoolean("drops.enabled", false);
        dropChance = mineConfig.getDouble("drops.drop-chance", 50);
        loadMines();
    }

    private void loadMines() {
        mines.clear();
        locationToMine.clear();
        originalBlocks.clear();
        respawnTasks.clear();

        if (mineConfig.contains("mines")) {
            for (String name : mineConfig.getConfigurationSection("mines").getKeys(false)) {
                World world = Bukkit.getWorld(mineConfig.getString("mines." + name + ".world"));
                if (world != null) {
                    double x = mineConfig.getDouble("mines." + name + ".x");
                    double y = mineConfig.getDouble("mines." + name + ".y");
                    double z = mineConfig.getDouble("mines." + name + ".z");
                    Location center = new Location(world, x, y, z);
                    MineData mine = new MineData(name, center);
                    mines.put(name, mine);
                    saveOriginalBlocksForMine(mine);
                }
            }
        }

        plugin.getDebugLogger().info("Loaded " + mines.size() + " mines.");
    }

    private void saveOriginalBlocksForMine(MineData mine) {
        int half = mineSize / 2;
        World world = mine.center.getWorld();
        int centerX = mine.center.getBlockX();
        int centerY = mine.center.getBlockY();
        int centerZ = mine.center.getBlockZ();

        for (int x = centerX - half; x <= centerX + half; x++) {
            for (int y = centerY - half; y <= centerY + half; y++) {
                for (int z = centerZ - half; z <= centerZ + half; z++) {
                    Location loc = new Location(world, x, y, z);
                    originalBlocks.put(loc, world.getBlockAt(loc).getType());
                    locationToMine.put(loc, mine.name);
                }
            }
        }

        plugin.getDebugLogger().debug("Saved " + originalBlocks.size() + " blocks for mine: " + mine.name);
    }

    public void resetAllMines() {
        plugin.getDebugLogger().info("Resetting all mines...");
        for (MineData mine : mines.values()) {
            resetMine(mine);
        }
    }

    private void resetMine(MineData mine) {
        int half = mineSize / 2;
        World world = mine.center.getWorld();
        int centerX = mine.center.getBlockX();
        int centerY = mine.center.getBlockY();
        int centerZ = mine.center.getBlockZ();
        int resetCount = 0;

        for (int x = centerX - half; x <= centerX + half; x++) {
            for (int y = centerY - half; y <= centerY + half; y++) {
                for (int z = centerZ - half; z <= centerZ + half; z++) {
                    Location loc = new Location(world, x, y, z);
                    Material original = originalBlocks.get(loc);
                    if (original != null && loc.getBlock().getType() != original) {
                        loc.getBlock().setType(original);
                        resetCount++;
                    }
                }
            }
        }

        if (resetCount > 0) {
            plugin.getDebugLogger().debug("Reset " + resetCount + " blocks in mine: " + mine.name);
        }
    }

    public void startMineSelection(Player player, String mineName) {
        if (mines.containsKey(mineName)) {
            player.sendMessage(MessageManager.translate("&cA mine with name '" + mineName + "' already exists!"));
            return;
        }
        selectingPlayers.add(player.getUniqueId());
        selectedMineName = mineName;
        player.sendMessage(MessageManager.translate("&eClick on a block to set the mine center for '" + mineName + "'!"));
        player.sendMessage(MessageManager.translate("&eType /adminjailadmin cancelmine to cancel"));
    }

    public void setMine(Location loc, String mineName) {
        MineData mine = new MineData(mineName, loc);
        mines.put(mineName, mine);
        saveMineLocation(mine);
        saveOriginalBlocksForMine(mine);
        plugin.getDebugLogger().info("Mine '" + mineName + "' set at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
    }

    private void saveMineLocation(MineData mine) {
        mineConfig.set("mines." + mine.name + ".world", mine.center.getWorld().getName());
        mineConfig.set("mines." + mine.name + ".x", mine.center.getX());
        mineConfig.set("mines." + mine.name + ".y", mine.center.getY());
        mineConfig.set("mines." + mine.name + ".z", mine.center.getZ());
        try {
            mineConfig.save(mineFile);
        } catch (Exception e) {
            plugin.getDebugLogger().warning("Failed to save mine location: " + e.getMessage());
        }
    }

    public void deleteMine(String name) {
        MineData mine = mines.remove(name);
        if (mine != null) {
            mineConfig.set("mines." + name, null);
            try {
                mineConfig.save(mineFile);
            } catch (Exception e) {
                plugin.getDebugLogger().warning("Failed to delete mine: " + e.getMessage());
            }

            List<Location> toRemove = new ArrayList<>();
            for (Map.Entry<Location, String> entry : locationToMine.entrySet()) {
                if (entry.getValue().equals(name)) {
                    toRemove.add(entry.getKey());
                }
            }
            for (Location loc : toRemove) {
                locationToMine.remove(loc);
                originalBlocks.remove(loc);
                if (respawnTasks.containsKey(loc)) {
                    Bukkit.getScheduler().cancelTask(respawnTasks.get(loc));
                    respawnTasks.remove(loc);
                }
            }
            plugin.getDebugLogger().info("Mine '" + name + "' deleted!");
        }
    }

    public boolean isInMine(Location loc) {
        return locationToMine.containsKey(loc);
    }

    public String getMineName(Location loc) {
        return locationToMine.get(loc);
    }

    public void respawnSingleBlock(Location loc) {
        Material original = originalBlocks.get(loc);
        if (original != null && loc.getBlock().getType() != original) {
            loc.getBlock().setType(original);
        }
    }

    private void scheduleRespawn(Location loc) {
        if (respawnTasks.containsKey(loc)) {
            Bukkit.getScheduler().cancelTask(respawnTasks.get(loc));
            respawnTasks.remove(loc);
        }

        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            respawnSingleBlock(loc);
            respawnTasks.remove(loc);
        }, respawnTime * 20L);

        respawnTasks.put(loc, taskId);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!selectingPlayers.contains(player.getUniqueId())) return;
        event.setCancelled(true);

        Block block = event.getClickedBlock();
        if (block == null) return;

        String mineName = selectedMineName;
        if (mineName == null) return;

        setMine(block.getLocation(), mineName);
        selectingPlayers.remove(player.getUniqueId());
        selectedMineName = null;
        player.sendMessage(MessageManager.translate("&aMine '" + mineName + "' set successfully at " + block.getType().name() + "!"));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location loc = block.getLocation();

        if (!isInMine(loc)) return;

        if (!plugin.getJailedPlayers().containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(MessageManager.translate("&cYou cannot break blocks in the mine!"));
            return;
        }

        event.setDropItems(false);
        event.setCancelled(true);
        block.setType(Material.AIR);

        if (dropsEnabled && Math.random() * 100 < dropChance) {
            ItemStack drop = new ItemStack(block.getType(), 1);
            player.getInventory().addItem(drop);
        }

        scheduleRespawn(loc);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        if (isInMine(loc)) {
            event.setCancelled(true);
            player.sendMessage(MessageManager.translate("&cYou cannot place blocks in the mine!"));
        }
    }

    public void cancelSelection(Player player) {
        selectingPlayers.remove(player.getUniqueId());
        selectedMineName = null;
        player.sendMessage(MessageManager.translate("&cMine selection cancelled!"));
    }

    public void reload() {
        loadConfig();
        loadMines();
        resetAllMines();
        plugin.getDebugLogger().info("MineManager reloaded and all mines reset!");
    }

    public void onDisable() {
        for (Integer taskId : respawnTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        respawnTasks.clear();
        resetAllMines();
        plugin.getDebugLogger().info("MineManager disabled and all mines reset!");
    }

    public boolean hasMine(String name) {
        return mines.containsKey(name);
    }

    public Set<String> getMineNames() {
        return mines.keySet();
    }

    public Location getMineCenter(String name) {
        MineData mine = mines.get(name);
        return mine != null ? mine.center : null;
    }

    public int getRespawnTime() {
        return respawnTime;
    }

    public boolean isSelecting(Player player) {
        return selectingPlayers.contains(player.getUniqueId());
    }

    public int getMineSize() {
        return mineSize;
    }

    private static class MineData {
        String name;
        Location center;
        MineData(String name, Location center) {
            this.name = name;
            this.center = center;
        }
    }
}