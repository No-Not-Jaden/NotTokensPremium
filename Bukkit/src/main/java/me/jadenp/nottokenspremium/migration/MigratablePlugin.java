package me.jadenp.nottokenspremium.migration;

import org.bukkit.OfflinePlayer;
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

    /**
     * Get all the tokens for this server
     * @return All the tokens from this plugin
     */
    @Nullable
    Map<UUID, Double> getAllTokens();

    /**
     * If the tokens have to be actively migrated when players come online.
     * If active migration is true, getAllTokens() will return null and setTokens() must be used with an online player
     * @return True if tokens have to be migrated when the player is online
     */
    boolean isActiveMigration();

    /**
     * Set the tokens of a player
     * @param player Player to set the tokens of
     * @param amount New token balance
     */
    void setTokens(OfflinePlayer player, double amount);

    /**
     * Name of the plugin. This should work with the Bukkit.getPluginManager().isPluginEnabled(String pluginName) method
     * @return The name of the plugin
     */
    String getName();

    /**
     * Check if the plugin is enabled.
     * @return True if the external plugin is enabled
     */
    boolean isEnabled();

    /**
     * Checks to see if the plugin is still enabled.
     */
    void refreshStatus();


}
