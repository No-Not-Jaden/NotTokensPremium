package me.jadenp.nottokenspremium.migration;

import me.jadenp.nottokenspremium.NotTokensPremium;
import me.jadenp.nottokenspremium.TokenManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class MigrationManager {
    // to be read from file (if present)
    private static MigrationType currentMigrationType = MigrationType.NONE;
    private static MigratablePlugin currentExternalTokenPlugin = null; // set with command or from file
    private static final List<UUID> migratedPlayers = new ArrayList<>(); // ^^^
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
        // The external token values replace the current values in NotTokens
        // Current token values are deleted, and external token values are transferred, with their values on the external plugin removed
        REPLACE,
        // No migration
        NONE
    }

    private static MigratablePlugin[] migratablePlugins = new MigratablePlugin[0];

    /**
     * Register migration
     */
    public static void loadConfig() {
        File migrationFile = getMigrationFile();
        // create file if it doesn't exist
        if (!migrationFile.exists())
            NotTokensPremium.getInstance().saveResource("migration.yml", false);
        // get configuration
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(migrationFile);
        // load available migration plugins
        migratablePlugins = new MigratablePlugin[]{new TokenManagerClass(), new BeastTokensClass()};
        // get the current migration type from file
        try {
            currentMigrationType = MigrationType.valueOf(configuration.getString("migration-type"));
        } catch (IllegalArgumentException e) {
            currentMigrationType = MigrationType.NONE;
        }
        // get the current migration plugin if there is a migration type set
        if (currentMigrationType != MigrationType.NONE) {
            String plugin = configuration.getString("plugin");
            for (MigratablePlugin pl : migratablePlugins)
                if (pl.getName().equalsIgnoreCase(plugin)) {
                    currentExternalTokenPlugin = pl;
                    break;
                }
            // reset migration type if there is not migratable plugin
            if (currentExternalTokenPlugin == null)
                currentMigrationType = MigrationType.NONE;
        }
        // get migrated players
        List<String> uuidPlayers = configuration.getStringList("migrated-players");
        migratedPlayers.clear();
        for (String uuid : uuidPlayers)
            migratedPlayers.add(UUID.fromString(uuid));

        // auto-save
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    save();
                } catch (IOException e) {
                    Bukkit.getLogger().warning("[NotTokensPremium] Error auto saving migration");
                }
            }
        }.runTaskTimer(NotTokensPremium.getInstance(), 20 * 60 * 15 + 20, 20 * 60 * 15); // 15 min

    }

    /**
     * Save the migration data to file
     */
    public static void save() throws IOException {
        // create new configuration
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("migration-type", currentMigrationType.toString()); // migration type
        String pluginName = currentExternalTokenPlugin != null ? currentExternalTokenPlugin.getName() : "NONE"; // plugin name or "NONE" if null
        configuration.set("plugin", pluginName); // plugin name
        configuration.set("migrated-players", migratedPlayers.stream().map(UUID::toString).collect(Collectors.toList())); // migrated players
        configuration.save(getMigrationFile()); // save
    }

    /**
     * migration.yml
     * @return The file to store migration data
     */
    private static File getMigrationFile() {
        return new File(NotTokensPremium.getInstance().getDataFolder() + File.separator + "migration.yml");
    }

    public static MigratablePlugin getCurrentExternalTokenPlugin() {
        return currentExternalTokenPlugin;
    }

    /**
     * Start/Stop migration of tokens.
     * To be used manually only (e.g. through a command)
     * @param migrationType Type of migration
     * @param pluginName Name of the plugin to migrate, or "None"
     * @throws RuntimeException If the plugin is not migratable or isn't enabled
     */
    public static void migrateTokens(MigrationType migrationType, String pluginName) throws RuntimeException {
        MigratablePlugin migratablePlugin = getMigratablePlugin(pluginName);
        // check if plugin is enabled
        if (migratablePlugin != null && !Bukkit.getPluginManager().isPluginEnabled(migratablePlugin.getName())) {
            // plugin not enabled
            throw new RuntimeException("External plugin is not enabled!");
        }
        // set current migration values
        currentExternalTokenPlugin = migratablePlugin; // can be set to null if pluginName is "None"
        currentMigrationType = migrationType;
        migratedPlayers.clear(); // clear history of migrated players

        // start server migration
        // This will either migrate all tokens or only tokens of online players depending on if the current plugin has active migration or not
        migrateServer();
    }

    /**
     * Get a migratable plugin from the plugin name
     * @param pluginName Name of the plugin
     * @return The MigratablePlugin associated with the pluginName or null if "None" was entered for the plugin name
     */
    @Nullable
    private static MigratablePlugin getMigratablePlugin(String pluginName) {
        MigratablePlugin migratablePlugin = null;
        // find migratable plugin from name
        for (MigratablePlugin plugin : migratablePlugins) {
            if (pluginName.equalsIgnoreCase(plugin.getName())) {
                migratablePlugin = plugin;
                break;
            }
        }
        // check if a plugin was found
        if (migratablePlugin == null && !pluginName.equalsIgnoreCase("None")) {
            // not a migratable plugin
            throw new RuntimeException("External plugin is not migratable!");
        }
        return migratablePlugin;
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
    public static void onJoin(Player player) {
        try {
            if (currentExternalTokenPlugin != null && currentExternalTokenPlugin.isActiveMigration() && currentExternalTokenPlugin.isEnabled())
                migratePlayer(player);
        } catch (NoClassDefFoundError e) {
            Bukkit.getLogger().warning("[NotTokensPremium] Could not migrate player's tokens. Is the external plugin still enabled?");
            Bukkit.getLogger().warning("You will have to reload NotTokens to refresh the connection.");
            currentExternalTokenPlugin.refreshStatus();
        }
    }

    /**
     * Refreshes the status of the current external token plugin
     */
    public static void refreshCurrentExternalTokenPlugin(){
        if (currentExternalTokenPlugin != null)
            currentExternalTokenPlugin.refreshStatus();
    }

    /**
     * Migrate tokens from the currentExternalTokenPlugin into this plugin
     * @param player Online player to migrate
     */
    private static void migratePlayer(Player player) {
        // checks if migration should happen
        if (currentMigrationType == MigrationType.NONE)
            return;
        if (migratedPlayers.contains(player.getUniqueId()))
            return;
        // get tokens
        double externalTokens = currentExternalTokenPlugin.getTokens(player);
        double currentTokens = TokenManager.getTokens(player.getUniqueId());
        // modify token values for each plugin
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
            case REPLACE:
                TokenManager.setTokens(player.getUniqueId(), externalTokens);
                currentExternalTokenPlugin.setTokens(player, 0);
                break;
        }
        // keeping track of who has been migrated already
        migratedPlayers.add(player.getUniqueId());
    }

    /**
     * Migrate the currentExternalTokenPlugin's tokens
     * If the external plugin uses active migration, then only online players will be migrated;
     * otherwise, all the tokens will be migrated
     */
    private static void migrateServer() {
        if (currentMigrationType == MigrationType.NONE)
            return;
        if (currentExternalTokenPlugin.isActiveMigration()) {
            // can only migrate online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                migratePlayer(player);
            }
            return;
        }
        // can migrate the whole server at once
        Map<UUID, Double> tokens = currentExternalTokenPlugin.getAllTokens();
        // we know tokens will not be null because this plugin does not use active migration
        assert tokens != null;
        // modify token values depending on the migration type
        switch (currentMigrationType) {
            case ADD:
                // add all tokens
                tokens.forEach(TokenManager::giveTokens);
                break;
            case COPY:
                // delete any tokens recorded in NotTokens, but not in the external plugin
                for (Map.Entry<UUID, Double> currentTokensEntry : TokenManager.getAllTokens().entrySet()) {
                    if (tokens.containsKey(currentTokensEntry.getKey()))
                        continue;
                    TokenManager.setTokens(currentTokensEntry.getKey(), 0); // remove all current tokens
                }
                tokens.forEach(TokenManager::setTokens);
                break;
            case SWAP:
                // transfer any tokens recorded in NotTokens, but not in the external plugin
                for (Map.Entry<UUID, Double> currentTokensEntry : TokenManager.getAllTokens().entrySet()) {
                    if (tokens.containsKey(currentTokensEntry.getKey()))
                        continue;
                    currentExternalTokenPlugin.setTokens(Bukkit.getOfflinePlayer(currentTokensEntry.getKey()), currentTokensEntry.getValue()); // set external token value
                    TokenManager.setTokens(currentTokensEntry.getKey(), 0); // remove all current tokens
                }
                // swap all tokens in
                for (Map.Entry<UUID, Double> externalTokensEntry : tokens.entrySet()) {
                    // set external token value to the current one
                    currentExternalTokenPlugin.setTokens(Bukkit.getOfflinePlayer(externalTokensEntry.getKey()), TokenManager.getTokens(externalTokensEntry.getKey()));
                    // set current token value to the external one
                    TokenManager.setTokens(externalTokensEntry.getKey(), externalTokensEntry.getValue());
                }
                break;
            case TRANSFER:
                // add all tokens to current storage & remove from external plugin
                for (Map.Entry<UUID, Double> externalTokensEntry : tokens.entrySet()) {
                    TokenManager.giveTokens(externalTokensEntry.getKey(), externalTokensEntry.getValue()); // give to current token value
                    currentExternalTokenPlugin.setTokens(Bukkit.getOfflinePlayer(externalTokensEntry.getKey()), 0); // remove all external tokens
                }
                break;
            case REPLACE:
                // delete all current tokens
                for (Map.Entry<UUID, Double> currentTokensEntry : TokenManager.getAllTokens().entrySet()) {
                    TokenManager.setTokens(currentTokensEntry.getKey(), 0);
                }
                tokens.forEach(TokenManager::setTokens); // add all external tokens
                break;
        }

    }
}
