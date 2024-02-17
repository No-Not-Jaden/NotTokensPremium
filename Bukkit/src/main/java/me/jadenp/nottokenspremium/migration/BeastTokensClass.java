package me.jadenp.nottokenspremium.migration;

import me.mraxetv.beasttokens.api.BeastTokensAPI;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public class BeastTokensClass implements MigratablePlugin{
    public BeastTokensClass() {

    }
    @Override
    public double getTokens(Player player) {
        BeastTokensAPI.getTokensManager().getAllPlayersBalance();
        return 0;
    }

    @Nullable
    @Override
    public Map<UUID, Double> getAllTokens() {
        return null;
    }

    @Override
    public void setTokens(Player player, double amount) {

    }

    @Override
    public String getName() {
        return "BeastTokens";
    }
}
