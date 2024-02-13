package me.jadenp.nottokenspremium.migration;

import me.jadenp.nottokenspremium.TokenManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MigrationManager {
    // to be read from file (if present)
    private static MigrationType currentMigrationType = MigrationType.NONE;
    private static MigratablePlugin currentExternalTokenPlugin; // set with command or from file
    private static List<UUID> migratedPlayers = new ArrayList<>(); // ^^^
    public enum MigrationType {
        // Switch current and external token values
        // Current token values are overridden with external token values and vice versa
        SWAP,
        // Add external values to current values - no modification of external values
        // External token values are added on top of current token values
        ADD,
        // All external tokens moved/added to current tokens - external token values are deleted
        // External token values are added to current token values, and external values are then deleted
        TRANSFER,
        // Duplicate external token values - previous current tokens are lost
        // Current token values are set to a copy of external token values
        COPY,
        // No migration
        NONE
    }

    private static MigratablePlugin[] migratablePlugins;

    /**
     * Register migration
     */
    public void loadConfiguration(ConfigurationSection configuration) {
        migratablePlugins = new MigratablePlugin[]{new TokenManagerClass(), new BeastTokensClass()};
    }

    public static void migrateTokens(MigrationType migrationType, String pluginName) throws RuntimeException {
        MigratablePlugin migratablePlugin = null;
        for (MigratablePlugin plugin : migratablePlugins) {
            if (pluginName.equalsIgnoreCase(plugin.getName())) {
                migratablePlugin = plugin;
                break;
            }
        }
        if (migratablePlugin == null && !pluginName.equalsIgnoreCase("None")) {
            // not a migratable plugin
            throw new RuntimeException("External plugin is not migratable!");
        }
        if (!Bukkit.getPluginManager().isPluginEnabled(migratablePlugin.getName())) {
            // plugin not enabled
            throw new RuntimeException("External plugin is not enabled!");
        }
        // set current migration values
        currentExternalTokenPlugin = migratablePlugin;
        currentMigrationType = migrationType;

        // migrate online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            migratePlayer(player);
        }
    }

    public static List<String> getEnabledPlugins(){
        List<String> plugins = new ArrayList<>();
        for (MigratablePlugin plugin : migratablePlugins) {
            if (Bukkit.getPluginManager().isPluginEnabled(plugin.getName()))
                plugins.add(plugin.getName());
        }
        return plugins;
    }

    /**
     * Try to migrate player's external tokens
     * @param player Online player to check external tokens of
     */
    public void onJoin(Player player) {
        migratePlayer(player);
    }

    /**
     * Migrate tokens from the currentExternalTokenPlugin into this plugin
     * @param player Online player to migrate
     */
    private static void migratePlayer(Player player) {
        if (currentMigrationType == MigrationType.NONE)
            return;
        if (migratedPlayers.contains(player.getUniqueId()))
            return;
        double externalTokens = currentExternalTokenPlugin.getTokens(player);
        double currentTokens = TokenManager.getTokens(player.getUniqueId());
        switch (currentMigrationType) {
            case ADD:
                TokenManager.giveTokens(player.getUniqueId(), externalTokens);
                break;
            case COPY:
                TokenManager.setTokens(player.getUniqueId(), externalTokens);
                break;
            case SWAP:
                TokenManager.setTokens(player.getUniqueId(), externalTokens);
                currentExternalTokenPlugin.setTokens(player, currentTokens);
                break;
            case TRANSFER:
                TokenManager.giveTokens(player.getUniqueId(), externalTokens);
                currentExternalTokenPlugin.setTokens(player, 0);
                break;
        }
        migratedPlayers.add(player.getUniqueId());
    }
}
