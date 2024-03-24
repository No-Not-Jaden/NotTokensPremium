package me.jadenp.nottokenspremium;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
        Map<UUID, String> playersToConvert = new HashMap<>();
        List<String> badFormats = new ArrayList<>();
        for (String key : Objects.requireNonNull(configuration.getConfigurationSection("logged-names")).getKeys(false)) {
            if (configuration.isConfigurationSection("logged-names." + key)) {
                // old format
                String name = configuration.getString("logged-names." + key + ".name");
                String uuid = configuration.getString("logged-names." + key + ".uuid");
                if (uuid == null || name == null)
                    continue;
                try {
                    logPlayer(name, UUID.fromString(uuid));
                    playersToConvert.put(UUID.fromString(uuid), name);
                } catch (IllegalArgumentException ignored) {}
                badFormats.add(key);
            } else {
                // new format
                try {
                    logPlayer(configuration.getString("logged-names." + key), UUID.fromString(key));
                } catch (IllegalArgumentException e) {
                    Bukkit.getLogger().warning("Could not get uuid of logged player: " + key + " = " + configuration.getString("logged-names." + key));
                }
            }
        }
        if (!badFormats.isEmpty() || !playersToConvert.isEmpty()) {
            // remove bad formatting
            for (String key : badFormats) {
                configuration.set("logged-names." + key, null);
            }
            // add players with old format to the new format
            for (Map.Entry<UUID, String> entry : playersToConvert.entrySet()) {
                if (!configuration.isSet("logged-names." + entry.getKey().toString()))
                    configuration.set("logged-names." + entry.getKey().toString(), entry.getValue());
            }
            // save
            try {
                configuration.save(playerHolder);
            } catch (IOException e) {
                Bukkit.getLogger().warning("[NotTokensPremium] Could not update loggedplayers.yml!");
                Bukkit.getLogger().warning(e.toString());
            }
        }

        // check if names match up with Bukkit
        Map<UUID, String> nameUpdates = new HashMap<>();
        for (Map.Entry<UUID, String> entry : UUIDNameMap.entrySet()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            if (player.getName() != null && !player.getName().equals(entry.getValue())) {
                nameUpdates.put(entry.getKey(), player.getName());
                nameUUIDMap.remove(entry.getValue());
            }
        }
        UUIDNameMap.putAll(nameUpdates);
        nameUpdates.forEach((uuid, str) -> nameUUIDMap.put(str, uuid));

        new BukkitRunnable(){
            @Override
            public void run() {
                try {
                    save();
                } catch (IOException e) {
                    Bukkit.getLogger().warning("[NotTokensPremium] Error saving logged players!");
                }

                // check if new players should be logged
                if (TokenManager.getSQL().isConnected()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            Map<UUID, String> loggedPlayers = TokenManager.getData().getLoggedPlayers();
                            if (UUIDNameMap.size() == loggedPlayers.size()) // No difference
                                return;
                            List<UUID> confirmedPlayers = new ArrayList<>();
                            // check for players on database and not on server
                            for (Map.Entry<UUID, String> entry : loggedPlayers.entrySet()) {
                                if (isLogged(entry.getValue(), entry.getKey())) {
                                    confirmedPlayers.add(entry.getKey());
                                } else {
                                    logPlayer(entry.getValue(), entry.getKey());
                                }
                            }
                            // check for players on server and not on database
                            Map<UUID, String> unLoggedPlayers = new HashMap<>();
                            for (Map.Entry<UUID, String> entry : UUIDNameMap.entrySet()) {
                                if (confirmedPlayers.contains(entry.getKey()))
                                    continue;
                                unLoggedPlayers.put(entry.getKey(), entry.getValue());
                            }
                            TokenManager.getData().logPlayers(unLoggedPlayers);

                            Map<UUID, Double> allTokens = TokenManager.getData().getTopTokens(500);
                            for (Map.Entry<UUID, Double> entry : allTokens.entrySet()) {
                                getPlayerName(entry.getKey()); // make sure their name is recorded
                            }

                        }
                    }.runTaskAsynchronously(NotTokensPremium.getInstance());
                }
            }
        }.runTaskTimer(NotTokensPremium.getInstance(), 20 * 60 * 15 + 10, 20 * 60 * 15); // 15 min
    }

    public LoggedPlayers(){}

    /**
     * Add player to logs
     */
    public static void logPlayer(String name, UUID uuid){
        nameUUIDMap.put(name, uuid);
        UUIDNameMap.put(uuid, name);
        if (TokenManager.getSQL().isConnected()) {
            TokenManager.getData().logPlayer(uuid, name);
        }
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
        CompletableFuture<String> name = requestPlayerName(uuid);
        try {
            if (name.isDone())
                return name.get();
        } catch (CancellationException | ExecutionException | InterruptedException ignored) {}
        return uuid.toString();
    }

    /**
     * Request a player name from Mojang's API
     * @param uuid UUID of the player
     * @return A completable future string of the player's name
     */
    private static CompletableFuture<String> requestPlayerName(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
                InputStreamReader reader = new InputStreamReader(url.openStream());
                JsonObject input = NotTokensPremium.serverVersion >= 18 ? JsonParser.parseReader(reader).getAsJsonObject() : new JsonParser().parse(reader).getAsJsonObject();
                String name = input.get("name").getAsString();
                reader.close();
                logPlayer(name, uuid);
                return name;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
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
        if (TokenManager.getSQL().isConnected()) {
            String[] playerInfo = TokenManager.getData().getLoggedPlayer(name);
            if (playerInfo != null) {
                String realName = playerInfo[1];
                try {
                    UUID uuid = UUID.fromString(playerInfo[0]);
                    logPlayer(realName, uuid);
                    return Bukkit.getOfflinePlayer(uuid);
                } catch (IllegalArgumentException ignored) {}
            }
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
                                Bukkit.getLogger().info("[NotTokensPremium] Migrated local storage to database.");
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
        if (!(!Bukkit.getOnlinePlayers().isEmpty() && ProxyMessaging.hasConnectedBefore() && ProxyMessaging.requestPlayerList()))
            receiveNetworkPlayers(new ArrayList<>());
        if (TokenManager.getSQL().isConnected())
            TokenManager.getData().getOnlinePlayers().forEach(player -> onlinePlayers.add(getPlayerName(player.getUniqueId())));
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
        if (System.currentTimeMillis() - lastPlayerRequest > 15000) {
            updateOnlinePlayers();
            lastPlayerRequest = System.currentTimeMillis();
        }
        return new ArrayList<>(nameUUIDMap.keySet());
    }

    public static List<UUID> getAllUUIDs(){
        return new ArrayList<>(UUIDNameMap.keySet());
    }

}
