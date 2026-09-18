package dev.sosis.adminjail.commands;

import dev.sosis.adminjail.*;
import org.bukkit.entity.Player;

public class JailPay implements org.bukkit.command.CommandExecutor {
    private final SosisAdminjailPlugin plugin;
    public JailPay(SosisAdminjailPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageManager.translate("&cOnly players can use this command!"));
            return true;
        }
        Player p = (Player) sender;
        if (!plugin.isDiscountBlocksEnabled()) {
            p.sendMessage(MessageManager.translate("&cBlock discount is disabled!"));
            return true;
        }
        if (!plugin.getJailedPlayers().containsKey(p.getUniqueId())) {
            p.sendMessage(MessageManager.translate("&cYou are not jailed!"));
            return true;
        }
        Data data = plugin.getJailedPlayers().get(p.getUniqueId());
        int remaining = data.getNeededBlocks() - data.getBrokenBlocks();
        if (remaining <= 0) {
            p.sendMessage(MessageManager.translate("&cYou have already completed your blocks!"));
            return true;
        }
        int cost = remaining * plugin.getDiscountCostPerBlock();
        p.sendMessage(MessageManager.translate("&aYou would need to pay " + cost + " to be freed!"));
        p.sendMessage(MessageManager.translate("&cThis feature requires Vault economy plugin!"));
        return true;
    }
}