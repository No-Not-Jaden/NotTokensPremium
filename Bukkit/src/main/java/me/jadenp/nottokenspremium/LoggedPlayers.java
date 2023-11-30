package me.jadenp.nottokenspremium;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class LoggedPlayers implements Listener {

    private static final Map<String, UUID> nameUUIDMap = new HashMap<>();
    private static final Map<UUID, String> UUIDNameMap = new HashMap<>();
    private static final List<String> onlinePlayers = new ArrayList<>();

    /**
     * Load logged players from tokensHolder file
     */
    public static void loadLoggedPlayers() {
        File tokensHolder = new File(NotTokensPremium.getInstance().getDataFolder() + File.separator + "tokensHolder.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(tokensHolder);
        if (!configuration.isConfigurationSection("logged-names"))
            return;
        for (String key : Objects.requireNonNull(configuration.getConfigurationSection("logged-names")).getKeys(false)) {
            try {
                logPlayer(configuration.getString("logged-names." + key), UUID.fromString(key));
            } catch (IllegalArgumentException e) {
                Bukkit.getLogger().warning("Could not get uuid of logged player: " + key + " = " + configuration.getString("logged-names." + key));
            }
        }
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
     * Write the logged players to the configuration
     * @param configuration YamlConfiguration to write to
     */
    public static void write(YamlConfiguration configuration){
        for (Map.Entry<UUID, String> entry : UUIDNameMap.entrySet()) {
            configuration.set("logged-names." + entry.getKey().toString(), entry.getValue());
        }
    }

    /**
     * Log new players and record the online players
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!UUIDNameMap.containsKey(event.getPlayer().getUniqueId())) {
            logPlayer(event.getPlayer().getName(), event.getPlayer().getUniqueId());
            if (ProxyMessaging.isConnected())
                ProxyMessaging.logNewPlayer(event.getPlayer());
        }
        if (!onlinePlayers.contains(event.getPlayer().getName()))
            onlinePlayers.add(event.getPlayer().getName());
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
        if (ProxyMessaging.isConnected())
            // request players on the network from the proxy
            ProxyMessaging.requestPlayerList();
        else
            receiveNetworkPlayers(new ArrayList<>());
    }

    public static List<String> getOnlinePlayerNames() {
        return onlinePlayers;
    }

    public static List<String> getAllPlayerNames(){
        return new ArrayList<>(nameUUIDMap.keySet());
    }

}
