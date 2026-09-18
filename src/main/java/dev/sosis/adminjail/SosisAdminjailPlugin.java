package dev.sosis.adminjail;

import dev.sosis.adminjail.commands.*;
import dev.sosis.adminjail.listeners.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SosisAdminjailPlugin extends JavaPlugin {
    private static SosisAdminjailPlugin instance;
    private MessageManager messageManager;
    private Database dbManager;
    private WebHook webHook;
    private DiscordConfig discordConfig;
    private DiscordWebhook discordWebhook;
    private SoundManager soundManager;
    private TitleManager titleManager;
    private AntiCheatManager antiCheatManager;
    private MineManager mineManager;
    private Debug debugLogger;
    private final Map<UUID, Data> jailedPlayers = new ConcurrentHashMap<>();
    private final Map<String, OfflineData> offlineJailedPlayers = new ConcurrentHashMap<>();
    private final Map<String, Location> jailLocations = new ConcurrentHashMap<>();
    private final Map<String, Location> spawnLocations = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> playerBossBars = new ConcurrentHashMap<>();
    private ItemStack jailPickaxe;
    private boolean blockCommands;
    private boolean blockTeleport;
    private boolean clearDropsOnDeath;
    private boolean clearExpOnDeath;
    private boolean respawnInJail;
    private boolean blockWrongPickaxe;
    private boolean showActionBar;
    private boolean autoSaveProgress;
    private boolean showReasonActionBar;
    private boolean bossBarEnabled;
    private boolean visitJailEnabled;
    private boolean transferJailEnabled;
    private boolean discountBlocksEnabled;
    private int discountCostPerBlock;
    private String bossBarTitle;
    private BarColor bossBarColor;
    private BarStyle bossBarStyle;
    private int bossBarUpdateInterval;
    private int actionBarTaskId;
    private int bossBarUpdateTaskId;
    private boolean hasBossBarAPI;
    private boolean hasActionBarAPI;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        createStorageFile();
        detectVersionCompatibility();
        loadConfigValues();
        loadFeaturesFromConfig();
        loadBossBarConfig();

        this.debugLogger = new Debug(this);
        Debug.setDebugMode(getConfig().getBoolean("debug-mode", false));

        this.messageManager = new MessageManager(this);
        this.dbManager = new Database(this);
        this.webHook = new WebHook(this);
        this.discordConfig = new DiscordConfig(this);
        this.discordWebhook = new DiscordWebhook(this, this.discordConfig);
        this.soundManager = new SoundManager(this);
        this.titleManager = new TitleManager(this);
        this.antiCheatManager = new AntiCheatManager(this);
        this.mineManager = new MineManager(this);

        try {
            dbManager.connect();
            offlineJailedPlayers.clear();
            Map<String, OfflineData> loaded = dbManager.loadAllJailedPlayers();
            int loadedCount = 0;
            for (Map.Entry<String, OfflineData> entry : loaded.entrySet()) {
                String status = entry.getValue().getStatus();
                if (status == null || !status.equals("unjailed")) {
                    offlineJailedPlayers.put(entry.getKey().toLowerCase(), entry.getValue());
                    loadedCount++;
                }
            }
            debugLogger.info("Loaded " + loadedCount + " jailed players from database.");
            dbManager.loadAllJailLocations().forEach((name, loc) -> jailLocations.put(name, loc));
            dbManager.loadAllSpawnLocations().forEach((name, loc) -> spawnLocations.put(name, loc));
        } catch (SQLException e) {
            debugLogger.severe("Failed to load database: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        registerCommands();
        registerListeners();
        registerTabCompleter();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new SosisPlaceholderExpansion(this).register();
                debugLogger.info("PlaceholderAPI expansion registered!");
            } catch (Exception e) {
                debugLogger.warning("Failed to register PlaceholderAPI: " + e.getMessage());
            }
        }

        int rejailedCount = 0;
        for (Map.Entry<String, OfflineData> entry : offlineJailedPlayers.entrySet()) {
            OfflineData offline = entry.getValue();
            Player player = Bukkit.getPlayer(offline.getPlayerName());
            if (player != null && player.isOnline()) {
                if (offline.getStatus() == null || !offline.getStatus().equals("unjailed")) {
                    int broken = offline.getBrokenBlocks();
                    int needed = offline.getNeededBlocks();

                    if (broken >= needed) {
                        debugLogger.info("Player " + player.getName() + " already completed sentence on startup! Removing...");
                        dbManager.removeJailedPlayer(player.getUniqueId());
                        offlineJailedPlayers.remove(player.getName().toLowerCase());
                        player.sendMessage(MessageManager.translate("&a&l✅ You have completed your sentence! You are free!"));
                        continue;
                    }

                    Data data = new Data(
                            player.getUniqueId(),
                            offline.getSavedInventory(),
                            needed,
                            broken,
                            offline.getJailName(),
                            offline.getReason()
                    );

                    jailedPlayers.put(player.getUniqueId(), data);
                    rejailedCount++;

                    player.getInventory().clear();
                    player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    player.getInventory().addItem(jailPickaxe.clone());

                    Location jailLoc = jailLocations.get(offline.getJailName());
                    if (jailLoc != null) {
                        player.teleport(jailLoc);
                    } else if (!jailLocations.isEmpty()) {
                        Location fallback = jailLocations.values().iterator().next();
                        player.teleport(fallback);
                    }

                    createBossBar(player, data);
                    int remaining = needed - broken;
                    player.sendMessage(MessageManager.translate("&cYou are still jailed! &e" + remaining + " blocks remaining."));
                    player.sendMessage(MessageManager.translate("&7Reason: &e" + offline.getReason()));
                }
            }
        }

        if (rejailedCount > 0) {
            debugLogger.info("Re-jailed " + rejailedCount + " players on startup.");
        }

        if (mineManager != null) {
            mineManager.resetAllMines();
            debugLogger.info("All mines reset on startup.");
        }

        if (hasActionBarAPI && showReasonActionBar) {
            startReasonActionBarTask();
        }
        if (hasBossBarAPI && bossBarEnabled) {
            startBossBarUpdateTask();
        }

        debugLogger.info("SosisAdminJail v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (actionBarTaskId != 0) {
            Bukkit.getScheduler().cancelTask(actionBarTaskId);
        }
        if (bossBarUpdateTaskId != 0) {
            Bukkit.getScheduler().cancelTask(bossBarUpdateTaskId);
        }

        for (BossBar bar : playerBossBars.values()) {
            bar.removeAll();
        }
        playerBossBars.clear();

        if (mineManager != null) {
            mineManager.onDisable();
            debugLogger.info("All mines reset on disable.");
        }

        int savedCount = 0;
        for (Map.Entry<UUID, Data> entry : jailedPlayers.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                OfflineData offline = new OfflineData(
                        p.getUniqueId(),
                        p.getName(),
                        entry.getValue().getSavedInventory(),
                        entry.getValue().getNeededBlocks(),
                        entry.getValue().getBrokenBlocks(),
                        entry.getValue().getJailName(),
                        entry.getValue().getReason(),
                        "jailed"
                );
                dbManager.saveJailedPlayerSync(offline);
                savedCount++;
            }
        }

        if (savedCount > 0) {
            debugLogger.info("Saved " + savedCount + " jailed players on disable.");
        }

        if (dbManager != null) {
            dbManager.disconnect();
        }

        debugLogger.info("SosisAdminJail disabled.");
    }

    private void createStorageFile() {
        File storageFile = new File(getDataFolder(), "storage.yml");
        if (!storageFile.exists()) {
            saveResource("storage.yml", false);
        }
    }

    private void detectVersionCompatibility() {
        try {
            Class.forName("org.bukkit.boss.BossBar");
            hasBossBarAPI = true;
        } catch (ClassNotFoundException e) {
            hasBossBarAPI = false;
        }

        try {
            Class.forName("net.md_5.bungee.api.ChatMessageType");
            hasActionBarAPI = true;
        } catch (ClassNotFoundException e) {
            hasActionBarAPI = false;
        }
    }

    private void loadConfigValues() {
        String matName = getConfig().getString("pickaxe.material", "IRON_PICKAXE");
        String pickName = getConfig().getString("pickaxe.name", "&cAdmin Jail Pickaxe");
        boolean unbreakable = getConfig().getBoolean("pickaxe.unbreakable", true);

        Material mat = Material.getMaterial(matName.toUpperCase());
        if (mat == null) {
            mat = Material.IRON_PICKAXE;
        }

        jailPickaxe = new ItemStack(mat);
        ItemMeta meta = jailPickaxe.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.translate(pickName));
            meta.setUnbreakable(unbreakable);
            jailPickaxe.setItemMeta(meta);
        }

        discountCostPerBlock = getConfig().getInt("discount.cost-per-block", 100);
    }

    private void loadFeaturesFromConfig() {
        ConfigurationSection features = getConfig().getConfigurationSection("features");
        if (features == null) {
            blockCommands = true;
            blockTeleport = true;
            clearDropsOnDeath = true;
            clearExpOnDeath = true;
            respawnInJail = true;
            blockWrongPickaxe = true;
            showActionBar = true;
            autoSaveProgress = true;
            showReasonActionBar = true;
            bossBarEnabled = true;
            visitJailEnabled = true;
            transferJailEnabled = true;
            discountBlocksEnabled = true;
            return;
        }

        blockCommands = features.getBoolean("block-commands", true);
        blockTeleport = features.getBoolean("block-teleport", true);
        clearDropsOnDeath = features.getBoolean("clear-drops-on-death", true);
        clearExpOnDeath = features.getBoolean("clear-exp-on-death", true);
        respawnInJail = features.getBoolean("respawn-in-jail", true);
        blockWrongPickaxe = features.getBoolean("block-wrong-pickaxe", true);
        showActionBar = features.getBoolean("show-action-bar", true);
        autoSaveProgress = features.getBoolean("auto-save-progress", true);
        showReasonActionBar = features.getBoolean("show-reason-action-bar", true);
        bossBarEnabled = features.getBoolean("boss-bar-enabled", true);
        visitJailEnabled = features.getBoolean("visit-jail", true);
        transferJailEnabled = features.getBoolean("transfer-jail", true);
        discountBlocksEnabled = features.getBoolean("discount-blocks", true);

        if (!hasBossBarAPI && bossBarEnabled) {
            bossBarEnabled = false;
        }
        if (!hasActionBarAPI && (showActionBar || showReasonActionBar)) {
            showActionBar = false;
            showReasonActionBar = false;
        }
    }

    private void loadBossBarConfig() {
        if (!hasBossBarAPI) {
            return;
        }

        ConfigurationSection bossSec = getConfig().getConfigurationSection("boss-bar");
        if (bossSec == null) {
            bossBarTitle = "&cJailed &7| &e%reason% &7| &f%current%&7/&a%needed% &7(&6%progress%%&7)";
            bossBarColor = BarColor.RED;
            bossBarStyle = BarStyle.SOLID;
            bossBarUpdateInterval = 20;
            return;
        }

        bossBarTitle = bossSec.getString("title", "&cJailed &7| &e%reason% &7| &f%current%&7/&a%needed% &7(&6%progress%%&7)");

        try {
            bossBarColor = BarColor.valueOf(bossSec.getString("color", "RED").toUpperCase());
        } catch (IllegalArgumentException e) {
            bossBarColor = BarColor.RED;
        }

        try {
            bossBarStyle = BarStyle.valueOf(bossSec.getString("style", "SOLID").toUpperCase());
        } catch (IllegalArgumentException e) {
            bossBarStyle = BarStyle.SOLID;
        }

        bossBarUpdateInterval = bossSec.getInt("update-interval", 20);
    }

    private void registerCommands() {
        if (getCommand("adminjail") != null) {
            getCommand("adminjail").setExecutor(new AdminJail(this));
        }
        if (getCommand("unjail") != null) {
            getCommand("unjail").setExecutor(new Unjail(this));
        }
        if (getCommand("adminjailadmin") != null) {
            getCommand("adminjailadmin").setExecutor(new AdminJailAdmin(this));
        }
        if (getCommand("jailpay") != null) {
            getCommand("jailpay").setExecutor(new JailPay(this));
        }
        if (getCommand("adminjailstats") != null) {
            getCommand("adminjailstats").setExecutor(new Stats(this));
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new Jail(this), this);
        getServer().getPluginManager().registerEvents(new Death(this), this);
        getServer().getPluginManager().registerEvents(new Join(this), this);
        getServer().getPluginManager().registerEvents(new Teleport(this), this);
        getServer().getPluginManager().registerEvents(new CommandBlocker(this), this);
        getServer().getPluginManager().registerEvents(new Stats(this), this);
        getServer().getPluginManager().registerEvents(mineManager, this);
        getServer().getPluginManager().registerEvents(antiCheatManager, this);
    }

    private void registerTabCompleter() {
        JailTabCompleter completer = new JailTabCompleter(this);
        if (getCommand("adminjail") != null) {
            getCommand("adminjail").setTabCompleter(completer);
        }
        if (getCommand("adminjailadmin") != null) {
            getCommand("adminjailadmin").setTabCompleter(completer);
        }
    }

    private void startReasonActionBarTask() {
        if (!hasActionBarAPI) {
            return;
        }

        actionBarTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            String template = getConfig().getString("messages.action-bar-reason", "&cReason: &e%reason% &7| &fBlocks: &a%current%&7/&a%needed%");
            for (Player p : Bukkit.getOnlinePlayers()) {
                Data data = jailedPlayers.get(p.getUniqueId());
                if (data != null) {
                    String msg = template.replace("%reason%", data.getReason())
                            .replace("%current%", String.valueOf(data.getBrokenBlocks()))
                            .replace("%needed%", String.valueOf(data.getNeededBlocks()));
                    try {
                        p.spigot().sendMessage(
                                net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(MessageManager.translate(msg))
                        );
                    } catch (Exception ignored) {}
                }
            }
        }, 0L, 100L);
    }

    private void startBossBarUpdateTask() {
        if (!hasBossBarAPI) {
            return;
        }

        bossBarUpdateTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Map.Entry<UUID, Data> entry : jailedPlayers.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    updateBossBar(p, entry.getValue());
                }
            }
        }, 0L, Math.max(1, bossBarUpdateInterval));
    }

    public void createBossBar(Player player, Data data) {
        if (!hasBossBarAPI || !bossBarEnabled) {
            return;
        }

        String title = formatBossBarTitle(data);
        BossBar bar = Bukkit.createBossBar(MessageManager.translate(title), bossBarColor, bossBarStyle);
        double progress = (double) data.getBrokenBlocks() / data.getNeededBlocks();
        bar.setProgress(Math.min(1.0, Math.max(0.0, progress)));
        bar.addPlayer(player);
        playerBossBars.put(player.getUniqueId(), bar);
    }

    public void updateBossBar(Player player, Data data) {
        if (!hasBossBarAPI || !bossBarEnabled) {
            return;
        }

        BossBar bar = playerBossBars.get(player.getUniqueId());
        if (bar == null) {
            createBossBar(player, data);
            return;
        }

        bar.setTitle(MessageManager.translate(formatBossBarTitle(data)));
        double progress = (double) data.getBrokenBlocks() / data.getNeededBlocks();
        bar.setProgress(Math.min(1.0, Math.max(0.0, progress)));
    }

    public void removeBossBar(Player player) {
        if (!hasBossBarAPI) {
            return;
        }

        BossBar bar = playerBossBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    private String formatBossBarTitle(Data data) {
        int current = data.getBrokenBlocks();
        int needed = data.getNeededBlocks();
        int progress = (int) (((double) current / needed) * 100);
        return bossBarTitle.replace("%reason%", data.getReason())
                .replace("%current%", String.valueOf(current))
                .replace("%needed%", String.valueOf(needed))
                .replace("%progress%", String.valueOf(progress));
    }

    public void restartReasonActionBarTask() {
        if (actionBarTaskId != 0) {
            Bukkit.getScheduler().cancelTask(actionBarTaskId);
        }
        if (hasActionBarAPI && showReasonActionBar) {
            startReasonActionBarTask();
        }
    }

    public void restartBossBarTask() {
        if (bossBarUpdateTaskId != 0) {
            Bukkit.getScheduler().cancelTask(bossBarUpdateTaskId);
        }
        if (hasBossBarAPI && bossBarEnabled) {
            startBossBarUpdateTask();
        }
    }

    public void reloadAll() {
        reloadConfig();
        loadConfigValues();
        loadFeaturesFromConfig();
        loadBossBarConfig();

        Debug.setDebugMode(getConfig().getBoolean("debug-mode", false));

        this.webHook = new WebHook(this);
        this.discordConfig.reload();
        this.discordWebhook = new DiscordWebhook(this, this.discordConfig);
        this.soundManager = new SoundManager(this);
        this.titleManager = new TitleManager(this);
        this.antiCheatManager = new AntiCheatManager(this);
        this.mineManager.reload();

        if (getCommand("adminjailstats") != null && getCommand("adminjailstats").getExecutor() instanceof Stats) {
            ((Stats) getCommand("adminjailstats").getExecutor()).reload();
        }

        jailLocations.clear();
        spawnLocations.clear();
        dbManager.loadAllJailLocations().forEach((name, loc) -> jailLocations.put(name, loc));
        dbManager.loadAllSpawnLocations().forEach((name, loc) -> spawnLocations.put(name, loc));

        restartReasonActionBarTask();
        restartBossBarTask();

        getMessageManager().sendMessage(Bukkit.getConsoleSender(), "reload-complete");
    }

    public void setJailLocation(String name, Location loc) {
        jailLocations.put(name, loc);
        dbManager.saveJailLocation(name, loc);
    }

    public void removeJailLocation(String name) {
        jailLocations.remove(name);
        dbManager.deleteJailLocation(name);
    }

    public void setSpawnLocation(String name, Location loc) {
        spawnLocations.put(name, loc);
        dbManager.saveSpawnLocation(name, loc);
    }

    public void removeSpawnLocation(String name) {
        spawnLocations.remove(name);
        dbManager.deleteSpawnLocation(name);
    }

    public Location getJailLocation(String name) {
        return jailLocations.get(name);
    }

    public Location getSpawnLocation(String name) {
        return spawnLocations.get(name);
    }

    public Map<String, Location> getJailLocations() {
        return Collections.unmodifiableMap(jailLocations);
    }

    public Map<String, Location> getSpawnLocations() {
        return Collections.unmodifiableMap(spawnLocations);
    }

    public ItemStack getJailPickaxe() {
        return jailPickaxe.clone();
    }

    public boolean isBlockCommands() {
        return blockCommands;
    }

    public boolean isBlockTeleport() {
        return blockTeleport;
    }

    public boolean isClearDropsOnDeath() {
        return clearDropsOnDeath;
    }

    public boolean isClearExpOnDeath() {
        return clearExpOnDeath;
    }

    public boolean isRespawnInJail() {
        return respawnInJail;
    }

    public boolean isBlockWrongPickaxe() {
        return blockWrongPickaxe;
    }

    public boolean isShowActionBar() {
        return showActionBar && hasActionBarAPI;
    }

    public boolean isAutoSaveProgress() {
        return autoSaveProgress;
    }

    public boolean isShowReasonActionBar() {
        return showReasonActionBar && hasActionBarAPI;
    }

    public boolean isBossBarEnabled() {
        return bossBarEnabled && hasBossBarAPI;
    }

    public boolean isVisitJailEnabled() {
        return visitJailEnabled;
    }

    public boolean isTransferJailEnabled() {
        return transferJailEnabled;
    }

    public boolean isDiscountBlocksEnabled() {
        return discountBlocksEnabled;
    }

    public int getDiscountCostPerBlock() {
        return discountCostPerBlock;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public TitleManager getTitleManager() {
        return titleManager;
    }

    public MineManager getMineManager() {
        return mineManager;
    }

    public Debug getDebugLogger() {
        return debugLogger;
    }

    public void unjailPlayer(Player player) {
        Data data = jailedPlayers.remove(player.getUniqueId());

        if (data == null) {
            debugLogger.warning("unjailPlayer called but player " + player.getName() + " was not in jailedPlayers map!");
            player.sendMessage(MessageManager.translate("&cYou are not jailed!"));
            return;
        }

        debugLogger.info("Unjailing " + player.getName() + " (Blocks: " + data.getBrokenBlocks() + "/" + data.getNeededBlocks() + ")");

        offlineJailedPlayers.remove(player.getName().toLowerCase());

        try {
            dbManager.removeJailedPlayer(player.getUniqueId());
        } catch (Exception e) {
            debugLogger.severe("Failed to remove player from database: " + e.getMessage());
            e.printStackTrace();
        }

        removeBossBar(player);

        player.getInventory().clear();
        List<ItemStack> saved = data.getSavedInventory();
        if (saved != null) {
            for (int i = 0; i < saved.size() && i < player.getInventory().getSize(); i++) {
                ItemStack item = saved.get(i);
                if (item != null && !item.getType().isAir()) {
                    player.getInventory().setItem(i, item);
                }
            }
        }

        Location spawnLoc = spawnLocations.get("default");
        if (spawnLoc == null && !spawnLocations.isEmpty()) {
            List<Location> spawns = new ArrayList<>(spawnLocations.values());
            spawnLoc = spawns.get(new Random().nextInt(spawns.size()));
        }

        if (spawnLoc != null) {
            player.teleport(spawnLoc);
        } else {
            player.sendMessage(messageManager.format("no-spawn-defined", MessageManager.Placeholder.of("player", player.getName())));
        }

        titleManager.sendFreedTitle(player);
        soundManager.playUnjailSound(player);
        messageManager.sendMessage(player, "player-freed");

        player.sendMessage(MessageManager.translate("&a&l✅ You have been unjailed!"));
    }

    public static SosisAdminjailPlugin getInstance() {
        return instance;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public Database getDbManager() {
        return dbManager;
    }

    public WebHook getWebHook() {
        return webHook;
    }

    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    public DiscordConfig getDiscordConfig() {
        return discordConfig;
    }

    public Map<UUID, Data> getJailedPlayers() {
        return jailedPlayers;
    }

    public Map<String, OfflineData> getOfflineJailedPlayers() {
        return offlineJailedPlayers;
    }
}