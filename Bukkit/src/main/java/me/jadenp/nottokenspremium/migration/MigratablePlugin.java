package me.jadenp.nottokenspremium.migration;

import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

public interface MigratablePlugin {
    /**
     * Get the tokens of an online player
     * @param player Player to get the tokens of
     * @return The number of tokens the player has
     */
    double getTokens(Player player);
    @Nullable
    Map<UUID, Double> getAllTokens();
    void setTokens(Player player, double amount);

    String getName();


}
