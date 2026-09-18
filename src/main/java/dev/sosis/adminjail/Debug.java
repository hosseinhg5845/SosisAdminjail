package dev.sosis.adminjail;

import org.bukkit.plugin.Plugin;

public class Debug {
    private final Plugin plugin;
    private static boolean debugMode = false;

    public Debug(Plugin plugin) {
        this.plugin = plugin;
    }

    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    public void info(String message) {
        plugin.getLogger().info(message);
    }

    public void warning(String message) {
        plugin.getLogger().warning(message);
    }

    public void severe(String message) {
        plugin.getLogger().severe(message);
    }

    public void debug(String message) {
        if (debugMode) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    public void debug(String message, Object... args) {
        if (debugMode) {
            plugin.getLogger().info("[DEBUG] " + String.format(message, args));
        }
    }

    public void fine(String message) {
        if (debugMode) {
            plugin.getLogger().info("[FINE] " + message);
        }
    }
}