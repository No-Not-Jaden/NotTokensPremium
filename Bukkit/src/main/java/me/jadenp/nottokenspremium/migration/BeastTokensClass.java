package me.jadenp.nottokenspremium.migration;

import org.bukkit.entity.Player;

public class BeastTokensClass implements MigratablePlugin{
    public BeastTokensClass() {

    }
    @Override
    public double getTokens(Player player) {
        return 0;
    }

    @Override
    public void setTokens(Player player, double amount) {

    }

    @Override
    public String getName() {
        return null;
    }
}
