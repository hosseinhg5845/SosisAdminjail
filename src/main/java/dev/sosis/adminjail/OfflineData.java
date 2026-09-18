package dev.sosis.adminjail;

import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.UUID;

public class OfflineData extends Data {
    private final String playerName;
    private String status;

    public OfflineData(UUID uuid, String name, List<ItemStack> inv, int needed, int broken, String jail, String reason) {
        super(uuid, inv, needed, broken, jail, reason);
        this.playerName = name;
        this.status = "jailed";
    }

    public OfflineData(UUID uuid, String name, List<ItemStack> inv, int needed, int broken, String jail, String reason, String status) {
        super(uuid, inv, needed, broken, jail, reason);
        this.playerName = name;
        this.status = (status != null && !status.isEmpty()) ? status : "jailed";
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}