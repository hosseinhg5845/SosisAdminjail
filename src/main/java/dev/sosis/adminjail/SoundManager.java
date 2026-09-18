package dev.sosis.adminjail;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundManager {
    private final SosisAdminjailPlugin plugin;
    private final boolean enabled;
    private final String jailSound;
    private final String unjailSound;
    private final float volume;
    private final float pitch;

    public SoundManager(SosisAdminjailPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("features.jail-sound", true);
        this.jailSound = plugin.getConfig().getString("sounds.jail-sound", "ENTITY_ENDERMAN_TELEPORT");
        this.unjailSound = plugin.getConfig().getString("sounds.unjail-sound", "ENTITY_PLAYER_LEVELUP");
        this.volume = (float) plugin.getConfig().getDouble("sounds.volume", 1.0);
        this.pitch = (float) plugin.getConfig().getDouble("sounds.pitch", 1.0);
    }

    public void playJailSound(Player player) {
        if (!enabled) return;
        if (player == null || !player.isOnline()) return;
        try {
            Sound sound = Sound.valueOf(jailSound);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound: " + jailSound);
        }
    }

    public void playUnjailSound(Player player) {
        if (!enabled) return;
        if (player == null || !player.isOnline()) return;
        try {
            Sound sound = Sound.valueOf(unjailSound);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound: " + unjailSound);
        }
    }
}