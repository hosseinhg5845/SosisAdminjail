package dev.sosis.adminjail;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

public class DiscordConfig {
    private final SosisAdminjailPlugin plugin;
    private YamlConfiguration config;
    private File configFile;

    public DiscordConfig(SosisAdminjailPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        configFile = new File(plugin.getDataFolder(), "discord.yml");
        if (!configFile.exists()) { plugin.saveResource("discord.yml", false); }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void save() { try { config.save(configFile); } catch (Exception e) {} }

    public boolean isEnabled() { return config.getBoolean("enabled", false); }
    public String getWebhookUrl() { return config.getString("webhook-url", ""); }
    public String getUsername() { return config.getString("username", "AdminJail"); }
    public String getAvatarUrl() { return config.getString("avatar-url", ""); }
    public int getJailColor() { return config.getInt("colors.jail", 16711680); }
    public int getUnjailColor() { return config.getInt("colors.unjail", 65280); }
    public int getProgressColor() { return config.getInt("colors.progress", 16776960); }
    public String getJailTitle() { return config.getString("messages.jail-title", "🔒 Player Jailed"); }
    public String getJailDesc() { return config.getString("messages.jail-description", "A player has been jailed"); }
    public String getJailFieldPlayer() { return config.getString("messages.jail-field-player", "Player"); }
    public String getJailFieldJail() { return config.getString("messages.jail-field-jail", "Jail"); }
    public String getJailFieldBlocks() { return config.getString("messages.jail-field-blocks", "Blocks"); }
    public String getJailFieldReason() { return config.getString("messages.jail-field-reason", "Reason"); }
    public String getUnjailTitle() { return config.getString("messages.unjail-title", "🔓 Player Released"); }
    public String getUnjailDesc() { return config.getString("messages.unjail-description", "A player has been released"); }
    public String getUnjailFieldPlayer() { return config.getString("messages.unjail-field-player", "Player"); }
    public String getUnjailFieldReason() { return config.getString("messages.unjail-field-reason", "Reason"); }
    public String getProgressTitle() { return config.getString("messages.progress-title", "📊 Progress Update"); }
    public String getProgressDesc() { return config.getString("messages.progress-description", "Player is making progress"); }
    public String getProgressFieldPlayer() { return config.getString("messages.progress-field-player", "Player"); }
    public String getProgressFieldJail() { return config.getString("messages.progress-field-jail", "Jail"); }
    public String getProgressFieldProgress() { return config.getString("messages.progress-field-progress", "Progress"); }
}