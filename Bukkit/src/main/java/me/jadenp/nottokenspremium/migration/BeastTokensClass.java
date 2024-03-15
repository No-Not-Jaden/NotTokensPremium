package me.jadenp.nottokenspremium.migration;

import me.mraxetv.beasttokens.api.BeastTokensAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public class BeastTokensClass implements MigratablePlugin{
    private boolean enabled;
    public BeastTokensClass() {
        refreshStatus();
    }
    @Override
    public double getTokens(Player player) {
        return BeastTokensAPI.getTokensManager().getTokens(player);
    }

    @Nullable
    @Override
    public Map<UUID, Double> getAllTokens() {
        return BeastTokensAPI.getTokensManager().getAllPlayersBalance();
    }

    @Override
    public boolean isActiveMigration() {
        return true;
    }

    @Override
    public void setTokens(OfflinePlayer player, double amount) {
        if (player.isOnline())
            BeastTokensAPI.getTokensManager().setTokens(player.getPlayer(), amount);
    }

    @Override
    public String getName() {
        return "BeastTokens";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void refreshStatus() {
        enabled = Bukkit.getPluginManager().isPluginEnabled(getName());
    }
}
