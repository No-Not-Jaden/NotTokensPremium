package me.jadenp.nottokenspremium;

import me.jadenp.nottokenspremium.migration.MigrationManager;
import me.jadenp.nottokenspremium.settings.Language;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static me.jadenp.nottokenspremium.settings.ConfigOptions.updateNotification;

public class LoggedPlayers implements Listener {

    private static final Map<String, UUID> nameUUIDMap = new HashMap<>();
    private static final Map<UUID, String> UUIDNameMap = new HashMap<>();
    private static final List<String> onlinePlayers = new ArrayList<>();
    private static long lastPlayerRequest = 0;

    /**
     * Load logged players from tokensHolder file
     */
    public static void loadLoggedPlayers() {
        File playerHolder = new File(NotTokensPremium.getInstance().getDataFolder() + File.separator + "loggedplayers.yml");
        try {
            if (playerHolder.createNewFile()) {
                Bukkit.getLogger().info("[NotTokensPremium] Created a new logged players file.");
            }
        } catch (IOException e) {
            Bukkit.getLogger().warning("[NotTokensPremium] Could not check for existing logged players file!");
            Bukkit.getLogger().warning(e.toString());
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(playerHolder);
        if (!configuration.isConfigurationSection("logged-names"))
            return;
        for (String key : Objects.requireNonNull(configuration.getConfigurationSection("logged-names")).getKeys(false)) {
            try {
                logPlayer(configuration.getString("logged-names." + key), UUID.fromString(key));
            } catch (IllegalArgumentException e) {
                Bukkit.getLogger().warning("Could not get uuid of logged player: " + key + " = " + configuration.getString("logged-names." + key));
            }
        }

        new BukkitRunnable(){
            @Override
            public void run() {
                try {
                    save();
                } catch (IOException e) {
                    Bukkit.getLogger().warning("[NotTokensPremium] Error saving logged players!");
                }
            }
        }.runTaskTimer(NotTokensPremium.getInstance(), 20 * 60 * 15 + 10, 20 * 60 * 15);
    }

    public LoggedPlayers(){}

    /**
     * Add player to logs
     */
    public static void logPlayer(String name, UUID uuid){
        nameUUIDMap.put(name, uuid);
        UUIDNameMap.put(uuid, name);
    }

    /**
     * Checks if a player is logged already
     * @param name Name of the player
     * @param uuid UUID of the player
     * @return true if the player is already logged
     */
    public static boolean isLogged(String name, UUID uuid) {
        if (!nameUUIDMap.containsKey(name) || !nameUUIDMap.get(name).equals(uuid))
            return false;
        return UUIDNameMap.containsKey(uuid) && UUIDNameMap.get(uuid).equals(name);
    }

    /**
     * Get a player name from a UUID. This method checks logged players, then tries to get a name from Bukkit.
     * If no name can be found, the UUID is returned in string form
     * @param uuid UUID of player
     * @return Name of player
     */
    public static @Nonnull String getPlayerName(UUID uuid) {
        if (UUIDNameMap.containsKey(uuid))
            return UUIDNameMap.get(uuid);
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (player.getName() != null)
            return player.getName();
        return uuid.toString();
    }

    /**
     * Get an offline player from their name.
     * @param name Name of the player
     * @return OfflinePlayer with the name or null if no player could be found
     */
    public static @Nullable OfflinePlayer getPlayer(String name) {
        if (nameUUIDMap.containsKey(name))
            return Bukkit.getOfflinePlayer(nameUUIDMap.get(name));
        for (Map.Entry<String, UUID> entry : nameUUIDMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name))
                return Bukkit.getOfflinePlayer(entry.getValue());
        }
        return Bukkit.getPlayer(name);
    }

    /**
     * Save the logged players to the configuration
     */
    public static void save() throws IOException {
        File playerHolder = new File(NotTokensPremium.getInstance().getDataFolder() + File.separator + "loggedplayers.yml");
        YamlConfiguration configuration = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : UUIDNameMap.entrySet()) {
            configuration.set("logged-names." + entry.getKey().toString(), entry.getValue());
        }
        configuration.save(playerHolder);
    }

    /**
     * Log new players and record the online players
     * Check for updates
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!UUIDNameMap.containsKey(event.getPlayer().getUniqueId())) {
            logPlayer(event.getPlayer().getName(), event.getPlayer().getUniqueId());
            ProxyMessaging.logNewPlayer(event.getPlayer());
        }
        if (!onlinePlayers.contains(event.getPlayer().getName()))
            onlinePlayers.add(event.getPlayer().getName());

        // check for updates
        if (updateNotification && !NotTokensPremium.latestVersion && NotTokensPremium.resourceID != 0) {
            if (event.getPlayer().hasPermission("nottokens.admin")) {
                new UpdateChecker(NotTokensPremium.getInstance(), NotTokensPremium.resourceID).getVersion(version -> {
                    if (NotTokensPremium.getInstance().getDescription().getVersion().contains("dev"))
                        return;
                    if (NotTokensPremium.getInstance().getDescription().getVersion().equals(version))
                        return;
                    event.getPlayer().sendMessage(Language.parse(Language.prefix, event.getPlayer()) + ChatColor.YELLOW + "A new update is available. Current version: " + ChatColor.GOLD + NotTokensPremium.getInstance().getDescription().getVersion() + ChatColor.YELLOW + " Latest version: " + ChatColor.GREEN + version);
                    event.getPlayer().sendMessage(ChatColor.YELLOW + "Download a new version here:" + ChatColor.GRAY + " https://www.spigotmc.org/resources//");
                });
            }
        }
        // update network players
        new BukkitRunnable() {
            @Override
            public void run() {
                LoggedPlayers.getAllPlayerNames();
            }
        }.runTaskLater(NotTokensPremium.getInstance(), 5);

        if (TokenManager.getSQL().isConnected()) {
            TokenManager.getData().login(event.getPlayer());
            // migrate local tokens
            // delay is to wait if a proxy will connect
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (TokenManager.isSavingLocally()) {
                            if (TokenManager.migrateToSQL()) {
                                Bukkit.getLogger().info("[NotTokensPremium] Migrating local storage to database.");
                            }
                        }
                    }
                }.runTaskLater(NotTokensPremium.getInstance(), 100L);
        }

        MigrationManager.onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        onlinePlayers.remove(event.getPlayer().getName());
        if (TokenManager.getSQL().isConnected())
            TokenManager.getData().logout(event.getPlayer());
    }

    /**
     * Updates the current online players with the online players on the current server and the players parameter
     * @param players Additional players coming in the form of (username):(uuid)
     */
    public static void receiveNetworkPlayers(List<String> players) {
        for (String player : players) {
            if (player.isEmpty())
                continue;
            String name = player.substring(0, player.indexOf(":"));
            onlinePlayers.add(name);
            try {
                UUID uuid = UUID.fromString(player.substring(player.indexOf(":") + 1));
                if (!isLogged(name, uuid))
                    logPlayer(name, uuid);
            } catch (IllegalArgumentException ignored) {}
        }
        Bukkit.getOnlinePlayers().forEach(player -> onlinePlayers.add(player.getName()));
    }

    /**
     * Updates online players with accurate values
     */
    public static void updateOnlinePlayers() {
        onlinePlayers.clear();
        if (!(!Bukkit.getOnlinePlayers().isEmpty() && ProxyMessaging.requestPlayerList()))
            receiveNetworkPlayers(new ArrayList<>());
        if (TokenManager.getSQL().isConnected())
            TokenManager.getData().getOnlinePlayers().forEach(player -> onlinePlayers.add(player.getName()));
    }

    /**
     * Clear the recorded online players
     */
    public static void clearOnlinePlayers() {
        onlinePlayers.clear();
    }

    public static List<String> getOnlinePlayerNames() {
        return onlinePlayers;
    }

    public static List<String> getAllPlayerNames() {
        if (!Bukkit.getOnlinePlayers().isEmpty() && System.currentTimeMillis() - lastPlayerRequest > 15000) {
            updateOnlinePlayers();
            lastPlayerRequest = System.currentTimeMillis();
        }
        return new ArrayList<>(nameUUIDMap.keySet());
    }

    public static List<UUID> getAllUUIDs(){
        return new ArrayList<>(UUIDNameMap.keySet());
    }

}
