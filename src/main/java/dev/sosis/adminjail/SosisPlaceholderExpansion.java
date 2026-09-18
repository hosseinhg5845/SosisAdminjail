package dev.sosis.adminjail;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SosisPlaceholderExpansion extends PlaceholderExpansion {
    private final SosisAdminjailPlugin plugin;

    public SosisPlaceholderExpansion(SosisAdminjailPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "sosisadminjail";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SosisDevelopment";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "";
        if (!plugin.getJailedPlayers().containsKey(player.getUniqueId())) {
            if (identifier.equals("jailed")) return "false";
            if (identifier.equals("status")) return "&aFree";
            if (identifier.equals("status_raw")) return "free";
            return "";
        }
        Data data = plugin.getJailedPlayers().get(player.getUniqueId());
        if (data == null) return "";
        switch (identifier.toLowerCase()) {
            case "jailed": return "true";
            case "status": return "&cJailed";
            case "status_raw": return "jailed";
            case "jail_name": return data.getJailName();
            case "reason": return data.getReason();
            case "needed_blocks": return String.valueOf(data.getNeededBlocks());
            case "broken_blocks": return String.valueOf(data.getBrokenBlocks());
            case "remaining_blocks": return String.valueOf(data.getNeededBlocks() - data.getBrokenBlocks());
            case "progress_percent":
                int percent = (int) (((double) data.getBrokenBlocks() / data.getNeededBlocks()) * 100);
                return String.valueOf(percent);
            case "progress_bar_5": return getProgressBar(data.getBrokenBlocks(), data.getNeededBlocks(), 5);
            case "progress_bar_10": return getProgressBar(data.getBrokenBlocks(), data.getNeededBlocks(), 10);
            case "progress_bar_20": return getProgressBar(data.getBrokenBlocks(), data.getNeededBlocks(), 20);
            default: return "";
        }
    }

    private String getProgressBar(int current, int needed, int length) {
        int progress = (int) (((double) current / needed) * length);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < length; i++) {
            bar.append(i < progress ? "█" : "░");
        }
        return bar.toString();
    }
}