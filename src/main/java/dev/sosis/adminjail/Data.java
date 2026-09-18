package dev.sosis.adminjail;

import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.UUID;

public class Data {
    private final UUID uuid;
    private final List<ItemStack> savedInventory;
    private final int neededBlocks;
    private int brokenBlocks;
    private final String jailName;
    private final String reason;

    public Data(UUID uuid, List<ItemStack> inv, int needed, int broken, String jail, String reason) {
        this.uuid = uuid;
        this.savedInventory = inv;
        this.neededBlocks = needed;
        this.brokenBlocks = broken;
        this.jailName = jail;
        this.reason = reason;
    }

    public UUID getUuid() {
        return uuid;
    }

    public List<ItemStack> getSavedInventory() {
        return savedInventory;
    }

    public int getNeededBlocks() {
        return neededBlocks;
    }

    public int getBrokenBlocks() {
        return brokenBlocks;
    }

    public void addBrokenBlock() {
        this.brokenBlocks++;
    }

    public void setBrokenBlocks(int broken) {
        this.brokenBlocks = broken;
    }

    public String getJailName() {
        return jailName;
    }

    public String getReason() {
        return reason;
    }
}