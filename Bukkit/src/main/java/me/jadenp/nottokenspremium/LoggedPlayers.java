package me.jadenp.nottokenspremium;

import me.jadenp.nottokenspremium.Configuration.Language;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static me.jadenp.nottokenspremium.Configuration.ConfigOptions.updateNotification;

public class LoggedPlayers implements Listener {

    private static final Map<String, UUID> nameUUIDMap = new HashMap<>();
    private static final Map<UUID, String> UUIDNameMap = new HashMap<>();
    private static final List<String> onlinePlayers = new ArrayList<>();
    private static List<UUID> newPlayers = new ArrayList<>();
    private static long lastProxyRequest = 0;
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
            if (ProxyMessaging.isConnected()) {
                if (!ProxyMessaging.logNewPlayer(event.getPlayer()))
                    Bukkit.getLogger().warning("[NotTokensPremium] Error logging player to the proxy!");
                if (!newPlayers.isEmpty()) {
                    List<UUID> failedAttempts = new ArrayList<>();
                    for (UUID uuid : newPlayers) {
                        if (!ProxyMessaging.logNewPlayer(UUIDNameMap.get(uuid), uuid)) {
                            failedAttempts.add(uuid);
                        }
                    }
                    newPlayers = new ArrayList<>(failedAttempts);
                }
            } else {
                newPlayers.add(event.getPlayer().getUniqueId());
                // request proxy connection with the player
                if (System.currentTimeMillis() - lastProxyRequest >= 15000 * 60) {
                    ProxyMessaging.requestConnection();
                    lastProxyRequest = System.currentTimeMillis();
                }
            }
        }
        if (!onlinePlayers.contains(event.getPlayer().getName()))
            onlinePlayers.add(event.getPlayer().getName());

        // check for updates
        if (updateNotification && !NotTokensPremium.latestVersion) {
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
    }

    /**
     * Updates the current online players with the online players on the current server and the players parameter
     * @param players Additional players to add to the online players list
     */
    public static void receiveNetworkPlayers(List<String> players) {
        onlinePlayers.clear();
        onlinePlayers.addAll(players);
        Bukkit.getOnlinePlayers().forEach(player -> onlinePlayers.add(player.getName()));
    }

    /**
     * Updates online players with accurate values
     */
    public static void updateOnlinePlayers() {
        if (!(ProxyMessaging.isConnected() && ProxyMessaging.requestPlayerList()))
            receiveNetworkPlayers(new ArrayList<>());
    }

    public static List<String> getOnlinePlayerNames() {
        return onlinePlayers;
    }

    public static List<String> getAllPlayerNames() {
        if (ProxyMessaging.isConnected() && System.currentTimeMillis() - lastPlayerRequest > 15000) {
            updateOnlinePlayers();
            lastPlayerRequest = System.currentTimeMillis();
        }
        return new ArrayList<>(nameUUIDMap.keySet());
    }

    public static List<UUID> getAllUUIDs(){
        return new ArrayList<>(UUIDNameMap.keySet());
    }

}
