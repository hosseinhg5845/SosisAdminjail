package dev.sosis.adminjail;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AntiCheatManager implements Listener {
    private final SosisAdminjailPlugin plugin;
    private final boolean enabled;
    private final boolean autoMineDetection;
    private final Map<UUID, Location> lastLocations;
    private final Map<UUID, Integer> violationLevels;
    private final Map<UUID, Long> lastViolationTime;
    private final Map<UUID, Integer> brokenBlocksCount;
    private final Map<UUID, Long> lastBreakTime;
    private final double MAX_SPEED;
    private final double MAX_VERTICAL_SPEED;
    private final int MAX_VIOLATIONS;
    private final long RESET_TIME;
    private final boolean KICK_ON_VIOLATION;
    private final int MAX_BLOCKS_PER_SECOND;

    public AntiCheatManager(SosisAdminjailPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("features.anti-cheat", true);
        this.autoMineDetection = plugin.getConfig().getBoolean("anti-cheat.auto-mine-detection", true);
        this.MAX_SPEED = plugin.getConfig().getDouble("anti-cheat.max-speed", 0.8);
        this.MAX_VERTICAL_SPEED = plugin.getConfig().getDouble("anti-cheat.max-vertical-speed", 1.2);
        this.MAX_VIOLATIONS = plugin.getConfig().getInt("anti-cheat.max-violations", 10);
        this.RESET_TIME = plugin.getConfig().getLong("anti-cheat.reset-time", 30000);
        this.KICK_ON_VIOLATION = plugin.getConfig().getBoolean("anti-cheat.kick-on-violation", true);
        this.MAX_BLOCKS_PER_SECOND = plugin.getConfig().getInt("anti-cheat.max-blocks-per-second", 5);
        this.lastLocations = new HashMap<>();
        this.violationLevels = new HashMap<>();
        this.lastViolationTime = new HashMap<>();
        this.brokenBlocksCount = new HashMap<>();
        this.lastBreakTime = new HashMap<>();
        if (enabled) { plugin.getServer().getPluginManager().registerEvents(this, plugin); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (!plugin.getJailedPlayers().containsKey(player.getUniqueId())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        UUID uuid = player.getUniqueId();
        resetViolationsIfNeeded(uuid);
        double distance = from.distance(to);
        double horizontalDistance = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        double verticalDistance = Math.abs(to.getY() - from.getY());
        boolean flag = false;
        String reason = "";
        if (isInAir(player) && !isNearGround(player) && !isInWater(player) && !isOnClimbable(player)) {
            if (distance > MAX_SPEED && verticalDistance < 0.5) { flag = true; reason = "Fly Hack"; addViolation(uuid); }
        }
        if (horizontalDistance > MAX_SPEED && !isInWater(player) && !isOnIce(player)) {
            flag = true; reason = "Speed Hack (" + String.format("%.2f", horizontalDistance) + ")"; addViolation(uuid);
        }
        if (verticalDistance > MAX_VERTICAL_SPEED && !player.isOnGround()) {
            flag = true; reason = "Vertical Speed Hack"; addViolation(uuid);
        }
        if (isClippingThroughWall(from, to)) {
            flag = true; reason = "NoClip/Wall Hack"; addViolation(uuid);
        }
        if (isWalkingOnWater(player, to)) {
            flag = true; reason = "Jesus Hack"; addViolation(uuid);
        }
        if (flag) {
            event.setCancelled(true);
            player.teleport(from);
            int violations = violationLevels.getOrDefault(uuid, 0);
            if (KICK_ON_VIOLATION && violations >= MAX_VIOLATIONS) {
                plugin.getLogger().warning("AntiCheat: " + player.getName() + " was kicked for " + reason);
                player.kickPlayer("§cAntiCheat: You were kicked for " + reason);
            } else {
                player.sendMessage(MessageManager.translate("&c⚠ AntiCheat: " + reason + " detected! Warning " + violations + "/" + MAX_VIOLATIONS));
            }
        }
        lastLocations.put(uuid, to.clone());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!enabled || !autoMineDetection) return;
        Player player = event.getPlayer();
        if (!plugin.getJailedPlayers().containsKey(player.getUniqueId())) return;
        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long lastTime = lastBreakTime.getOrDefault(uuid, 0L);
        if (lastTime == 0 || currentTime - lastTime > 1000) {
            brokenBlocksCount.put(uuid, 1);
            lastBreakTime.put(uuid, currentTime);
        } else {
            int count = brokenBlocksCount.getOrDefault(uuid, 0) + 1;
            brokenBlocksCount.put(uuid, count);
            lastBreakTime.put(uuid, currentTime);
            if (count > MAX_BLOCKS_PER_SECOND) {
                event.setCancelled(true);
                player.sendMessage(MessageManager.translate("&c⚠ AntiCheat: AutoMine detected! Slow down!"));
                addViolation(uuid);
                int violations = violationLevels.getOrDefault(uuid, 0);
                if (KICK_ON_VIOLATION && violations >= MAX_VIOLATIONS) {
                    player.kickPlayer("§cAntiCheat: AutoMine detected!");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (plugin.getJailedPlayers().containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(MessageManager.translate("&cYou cannot place blocks while jailed!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (!plugin.getJailedPlayers().containsKey(player.getUniqueId())) return;
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN &&
                event.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) {
            event.setCancelled(true);
            player.sendMessage(MessageManager.translate("&cYou cannot teleport while jailed!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (!plugin.getJailedPlayers().containsKey(player.getUniqueId())) return;
        Vector velocity = event.getVelocity();
        double speed = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
        if (speed > 1.5) {
            event.setCancelled(true);
            player.sendMessage(MessageManager.translate("&cKnockback disabled while jailed!"));
        }
    }

    private boolean isInAir(Player player) { return !player.isOnGround() && !player.isInWater() && !player.isClimbing(); }
    private boolean isNearGround(Player player) {
        Location loc = player.getLocation();
        for (int y = 0; y < 3; y++) {
            if (loc.clone().subtract(0, y, 0).getBlock().getType().isSolid()) return true;
        }
        return false;
    }
    private boolean isInWater(Player player) { return player.isInWater() || player.getLocation().getBlock().getType() == Material.WATER; }
    private boolean isOnClimbable(Player player) {
        Material mat = player.getLocation().getBlock().getType();
        return mat == Material.LADDER || mat == Material.VINE || mat == Material.SCAFFOLDING;
    }
    private boolean isOnIce(Player player) {
        Material mat = player.getLocation().getBlock().getType();
        return mat == Material.ICE || mat == Material.PACKED_ICE || mat == Material.BLUE_ICE || mat == Material.FROSTED_ICE;
    }
    private boolean isWalkingOnWater(Player player, Location to) {
        if (player.isOnGround()) return false;
        Location below = to.clone().subtract(0, 0.5, 0);
        return below.getBlock().getType() == Material.WATER && !player.isInWater();
    }
    private boolean isClippingThroughWall(Location from, Location to) {
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ() && Math.abs(from.getY() - to.getY()) < 1.5) return false;
        Location middle = from.clone().add(to).multiply(0.5);
        return middle.getBlock().getType().isSolid();
    }
    private void addViolation(UUID uuid) {
        int violations = violationLevels.getOrDefault(uuid, 0) + 1;
        violationLevels.put(uuid, violations);
        lastViolationTime.put(uuid, System.currentTimeMillis());
    }
    private void resetViolationsIfNeeded(UUID uuid) {
        long lastTime = lastViolationTime.getOrDefault(uuid, 0L);
        if (System.currentTimeMillis() - lastTime > RESET_TIME) { violationLevels.put(uuid, 0); }
    }
}