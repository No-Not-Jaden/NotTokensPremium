package me.jadenp.nottokenspremium;

import me.jadenp.nottokenspremium.Configuration.ConfigOptions;
import me.jadenp.nottokenspremium.Configuration.Language;
import me.jadenp.nottokenspremium.Configuration.NumberFormatting;
import me.jadenp.nottokenspremium.SQL.MySQL;
import me.jadenp.nottokenspremium.SQL.SQLGetter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class TokenManager {
    private static BukkitTask tokenTransmissionTask = null;
    private static BukkitTask tokenMessagingTask = null;
    private static MySQL SQL;
    private static SQLGetter data;
    private static BukkitTask autoConnectTask = null;
    /**
     * File to save tokens locally
     */
    private static File tokensHolder;
    /**
     * Tokens queueing up for a message to the player.
     * This is to reduce the spam of new tokens.
     */
    private static final Map<UUID, TokenMessage> tokenMessagingQueue = new HashMap<>();
    /**
     * Tokens queueing up to be transmitted to the network.
     * This is if a proxy is being used and an SQL server is not connected
     */
    private static final Map<UUID, Double> tokenTransmissionQueue = new HashMap<>();
    /**
     * The current token balances on the server
     */
    private static final Map<UUID, Double> currentServerTokens = new HashMap<>();

    /**
     * Load token manager system
     * This should only be run on startup
     */
    public static void loadTokenManager() {
        tokensHolder = new File(NotTokensPremium.getInstance().getDataFolder() + File.separator + "tokensHolder.yml");
        // tokens will only be stored locally if there is no sql connection and no proxy connection or if tokens could not be transmitted before shutdown
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(tokensHolder);
        for (String uuid : configuration.getKeys(false)) {
            try {
                currentServerTokens.put(UUID.fromString(uuid), configuration.getDouble(uuid));
            } catch (IllegalArgumentException e) {
                Bukkit.getLogger().warning("[NotTokensPremium] Invalid UUID stored in tokensHolder.yml: " + uuid);
            }
        }

        SQL = new MySQL();
        data = new SQLGetter(SQL);
        if (!tryToConnect()) {
            Bukkit.getLogger().info("[NotTokensPremium] Database not connected.");
        }


        if (autoConnectTask != null) {
            autoConnectTask.cancel();
        }
        try {
            if (!NotTokensPremium.getInstance().firstStart) {
                SQL.reconnect();
            }
        } catch (SQLException ignored) {}
        if (ConfigOptions.autoConnect) {
            autoConnectTask = new BukkitRunnable() {
                @Override
                public void run() {
                    tryToConnect();
                }
            }.runTaskTimer(NotTokensPremium.getInstance(), 600, 600);
        }

        // auto save task
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    autoSaveTokens();
                } catch (IOException e) {
                    Bukkit.getLogger().warning("[NotTokensPremium] Error auto saving tokens!");
                }
                // send a proxy message to load any tokens
                if (tokenTransmissionTask == null && !Bukkit.getOnlinePlayers().isEmpty())
                    ProxyMessaging.requestConnection();
            }
        }.runTaskTimer(NotTokensPremium.getInstance(), 20 * 60 * 15, 20 * 60 * 15); // 15 min

    }

    /**
     * Save tokens to a local file
     * Tokens will not be saved if an SQL database is connected or all the tokens are stored on the proxy
     */
    public static void saveTokens() throws IOException {
        if (tokenTransmissionTask != null) {
            YamlConfiguration configuration = new YamlConfiguration();
            for (Map.Entry<UUID, Double> entry : tokenTransmissionQueue.entrySet()) {
                configuration.set(entry.getKey().toString(), entry.getValue());
            }
            configuration.save(tokensHolder);
        } else {
            autoSaveTokens();
        }
    }

    /**
     * Only save the tokens if not connected to any external storage
     */
    public static void autoSaveTokens() throws IOException {
        YamlConfiguration configuration = new YamlConfiguration();
        for (Map.Entry<UUID, Double> entry : currentServerTokens.entrySet()) {
            configuration.set(entry.getKey().toString(), entry.getValue());
        }
        configuration.save(tokensHolder);
    }

    /**
     * Try to connect to the SQL Database. Data will be migrated if that option is enabled.
     * @return True if the database was connected successfully
     */
    public static boolean tryToConnect(){
        if (!SQL.isConnected()) {
            try {
                SQL.connect();
            } catch (SQLException e) {
                //e.printStackTrace();
                return false;
            }

            if (SQL.isConnected()) {
                Bukkit.getLogger().info("[NotTokensPremium] SQL database is connected!");
                data.createTable();
                if (!currentServerTokens.isEmpty() && SQL.isMigrateLocalData()) {
                    Bukkit.getLogger().info("[NotTokensPremium] Migrating local storage to database.");
                    // add entries to database
                    for (Map.Entry<UUID, Double> entry : currentServerTokens.entrySet()) {
                        if (entry.getValue() != 0L)
                            data.giveTokens(entry.getKey(), entry.getValue());
                    }
                    currentServerTokens.clear();
                }
                int rows = data.removeExtraData();
                if (NotTokensPremium.getInstance().firstStart) {
                    Bukkit.getLogger().info("Cleared up " + rows + " unused rows in the database!");
                }
            }
        }
        return true;
    }

    /**
     * Get the players with the higher number of tokens in descending order
     * @param amount Amount of players to get
     * @return A LinkedHashMap with the players with the highest number of tokens at the beginning
     */
    public static LinkedHashMap<UUID, Double> getTopTokens(int amount) {
        if (SQL.isConnected())
            return data.getTopTokens(amount);
        LinkedHashMap<UUID, Double> sortedList = sortByValue(currentServerTokens);
        LinkedHashMap<UUID, Double> cutList = new LinkedHashMap<>();
        for (Map.Entry<UUID, Double> entry : sortedList.entrySet()) {
            cutList.put(entry.getKey(), entry.getValue());
            amount--;
            if (amount == 0)
                break;
        }
        return cutList;
    }

    /**
     * Returns a sorted LinkedHashMap with higher values at the top
     * @param hm HashMap to sort
     * @return Sorted HashMap
     */
    public static LinkedHashMap<UUID, Double> sortByValue(Map<UUID, Double> hm) {
        // Create a list from elements of HashMap
        List<Map.Entry<UUID, Double>> list =
                new LinkedList<>(hm.entrySet());

        // Sort the list
        list.sort((o1, o2) -> (o2.getValue()).compareTo(o1.getValue()));

        // put data from sorted list to hashmap
        LinkedHashMap<UUID, Double> temp = new LinkedHashMap<>();
        for (Map.Entry<UUID, Double> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }


    /**
     * Give a player tokens.
     *
     * @param uuid UUID of player to give tokens to
     * @param amount Amount of tokens to be given
     * @return true if the tokens were given successfully
     */
    public static boolean giveTokens(UUID uuid, double amount) {
        return setTokens(uuid, getTokens(uuid) + amount);
    }

    /**
     * Remove tokens from a player.
     *
     * @param uuid UUID of player to have tokens removed
     * @param amount Amount of tokens to be removed
     * @return true if the tokens were removed successfully
     */
    public static boolean removeTokens(UUID uuid, double amount) {
        return setTokens(uuid, getTokens(uuid) - amount);
    }

    /***
     * Sets the tokens of a player.
     * @param uuid UUID of player to set the tokens of
     * @param amount New amount of tokens
     * @return True if the transaction was successful
     */
    public static boolean setTokens(UUID uuid, double amount) {
        // change in token balance for the player
        double currentTokens = getTokens(uuid);
        // make sure the amount isn't below 0 if negative tokens is false
        if (amount < 0 && !ConfigOptions.negativeTokens) {
            amount = 0;
        }
        double difference = amount - currentTokens;
        // change current tokens
        currentServerTokens.put(uuid, amount);
        // update token transmission queue if it is connected
        if (tokenTransmissionTask != null)
            if (tokenTransmissionQueue.containsKey(uuid))
                tokenTransmissionQueue.replace(uuid, tokenTransmissionQueue.get(uuid) + difference);
            else
                tokenTransmissionQueue.put(uuid, difference);
        // check to send message or add to queue
        if (tokenMessagingQueue.containsKey(uuid)) {
            tokenMessagingQueue.get(uuid).addTokenChange(difference);
        } else {
            // send message
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            if (player.isOnline()) {
                String message = difference > 0 ? Language.playerReceive : Language.playerTake;
                Objects.requireNonNull(player.getPlayer()).sendMessage(Language.parse(message, Math.abs(difference), player));
                // add new obj to queue
                tokenMessagingQueue.put(uuid, new TokenMessage());
            }
        }
        // update SQL database if it is connected
        if (SQL.isConnected()) {
            if (difference < 0)
                data.removeTokens(uuid, -1 * amount);
            else if (difference > 0)
                data.giveTokens(uuid, amount);
        }
        return true;
    }

    /**
     * Edit the player's tokens without sending a message or triggering a transmission
     * @param uuid UUID of player
     * @param amount Token change amount
     */
    public static void editTokensSilently(UUID uuid, double amount) {
        double newTokens = getTokens(uuid) + amount;
        currentServerTokens.put(uuid, newTokens);
        TransactionLogs.log(LoggedPlayers.getPlayerName(uuid) + " is having tokens updated from the proxy. (" + NumberFormatting.formatNumber(amount) + ") Total: " +  NumberFormatting.formatNumber(newTokens));
    }

    /**
     * Connect to the proxy and load all tokens stored there
     * @param serverTokens Tokens stored on the proxy
     */
    public static void connectProxy(Map<UUID, Double> serverTokens) {
        if (tokenMessagingTask != null)
            return; // already connected
        if (!serverTokens.isEmpty()) {
            if (Bukkit.getOnlinePlayers().isEmpty())
                return; // nothing to receive current tokens
            ProxyMessaging.sendServerTokenUpdate(currentServerTokens);
        }
        currentServerTokens.putAll(serverTokens);
        beginTokenTransmission();
        Bukkit.getLogger().info("[NotTokensPremium] Connected to proxy!");
    }

    public static double getTokens(UUID uuid) {
        if (SQL.isConnected())
            return data.getTokens(uuid);
        if (currentServerTokens.containsKey(uuid))
            return currentServerTokens.get(uuid);
        return 0;
    }

    /**
     * Begin sending condensed spam token messages
     */
    public static void beginTokenMessaging(){
        if (tokenMessagingTask != null)
            tokenMessagingTask.cancel();
        tokenMessagingTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, TokenMessage> entry : tokenMessagingQueue.entrySet()) {
                    if (System.currentTimeMillis() - entry.getValue().getLastMessageTime() < ConfigOptions.tokenMessageInterval * 1000L)
                        return;
                    // send message
                    OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                    if (player.isOnline()) {
                        Objects.requireNonNull(player.getPlayer()).sendMessage(Language.parse(Language.reducedMessage, entry.getValue().getTokenChange(), System.currentTimeMillis() - entry.getValue().getLastMessageTime(), player));
                    }
                }
                tokenMessagingQueue.entrySet().removeIf(uuidTokenMessageEntry -> System.currentTimeMillis() - uuidTokenMessageEntry.getValue().getLastMessageTime() >= ConfigOptions.tokenMessageInterval * 1000L);
            }
        }.runTaskTimer(NotTokensPremium.getInstance(), 100, 20);
    }

    /**
     * Cancel sending condensed spam token messages
     */
    public static void cancelTokenMessaging(){
        tokenMessagingTask.cancel();
        tokenMessagingTask = null;
    }

    public static boolean isTokenMessagingActing() {
        return tokenMessagingTask != null;
    }

    /**
     * Returns the queued transmission for the player matching the specified uuid and removes the transmission from the queue
     * @param uuid UUID of the player to get the transmission from
     * @return The amount of tokens to be changed
     */
    public static double separateTransmission(UUID uuid) {
        if (tokenTransmissionQueue.containsKey(uuid)) {
            double transmission = tokenTransmissionQueue.get(uuid);
            tokenTransmissionQueue.remove(uuid);
            return transmission;
        }
        return 0;
    }

    public static boolean isUsingTransmission(){
        return tokenTransmissionTask != null;
    }

    public static void beginTokenTransmission() {
        if (tokenTransmissionTask != null) {
            tokenTransmissionTask.cancel();
        }
        tokenTransmissionTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (tokenTransmissionQueue.isEmpty())
                    return;
                if (Bukkit.getOnlinePlayers().isEmpty())
                    return;
                if (ProxyMessaging.sendServerTokenUpdate(tokenTransmissionQueue))
                    tokenTransmissionQueue.clear();
            }
        }.runTaskTimerAsynchronously(NotTokensPremium.getInstance(), 50, 21);
    }
}
