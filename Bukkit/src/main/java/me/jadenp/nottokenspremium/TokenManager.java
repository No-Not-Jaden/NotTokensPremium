package me.jadenp.nottokenspremium;

import me.jadenp.nottokenspremium.settings.ConfigOptions;
import me.jadenp.nottokenspremium.settings.ItemExchange;
import me.jadenp.nottokenspremium.settings.Language;
import me.jadenp.nottokenspremium.settings.NumberFormatting;
import me.jadenp.nottokenspremium.mySQL.MySQL;
import me.jadenp.nottokenspremium.mySQL.SQLGetter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TokenManager {
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
     * The current token balances on the server.
     * This is accurate unless SQL is connected
     */
    private static final Map<UUID, Double> currentServerTokens = new HashMap<>();

    /**
     * Get the active SQL Connection
     * @return The active SQL Connection
     */
    public static MySQL getSQL() {
        return SQL;
    }

    /**
     * Get the SQLGetter object as data
     * @return The SQL Data
     */
    public static SQLGetter getData() {
        return data;
    }

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
        tryToConnect(); // try to connect to database


        try {
            if (!NotTokensPremium.getInstance().firstStart) {
                SQL.reconnect();
            }
        } catch (SQLException ignored) {}

        // auto save task
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    autoSaveTokens();
                } catch (IOException e) {
                    Bukkit.getLogger().warning("[NotTokensPremium] Error auto saving tokens!");
                }
            }
        }.runTaskTimer(NotTokensPremium.getInstance(), 20 * 60 * 15, 20 * 60 * 15); // 15 min

    }

    public static void startAutoConnectTask() {
        if (autoConnectTask != null) {
            autoConnectTask.cancel();
        }
        if (ConfigOptions.autoConnect) {
            autoConnectTask = new BukkitRunnable() {
                @Override
                public void run() {
                    tryToConnect();
                }
            }.runTaskTimer(NotTokensPremium.getInstance(), 600, 600);
        }
    }

    /**
     * Save needed tokens on shutdown
     */
    public static void saveTokens() throws IOException {
        if (ProxyMessaging.hasConnectedBefore()) {
            YamlConfiguration configuration = new YamlConfiguration();
            for (Map.Entry<UUID, Double> entry : tokenTransmissionQueue.entrySet()) {
                configuration.set(entry.getKey().toString(), entry.getValue());
            }
            configuration.save(tokensHolder);
        } else if (!SQL.isConnected()) {
            saveLocalTokens();
        }
    }

    /**
     * Only save the tokens if not connected to any external storage
     */
    public static void autoSaveTokens() throws IOException {
        if (!ProxyMessaging.hasConnectedBefore() && !SQL.isConnected())
            saveLocalTokens();
        TransactionLogs.saveTransactions();
    }

    /**
     * Saves tokens to a file
     * @throws IOException if an error occurs saving tokens
     */
    public static void saveLocalTokens() throws IOException {
        YamlConfiguration configuration = new YamlConfiguration();
        for (Map.Entry<UUID, Double> entry : currentServerTokens.entrySet()) {
            configuration.set(entry.getKey().toString(), entry.getValue());
        }
        configuration.save(tokensHolder);
    }

    /**
     * Checks if tokens are be saved locally
     * @return True if there are tokens in currentServerTokens
     */
    public static boolean isSavingLocally() {
        return currentServerTokens.entrySet().stream().anyMatch(entry -> entry.getValue() != 0.0);
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
                data.createOnlinePlayerTable();
                // usually would migrate tokens here, but this doesn't work if a proxy is connected
                int rows = data.removeExtraData();
                if (NotTokensPremium.getInstance().firstStart) {
                    Bukkit.getLogger().info("Cleared up " + rows + " unused rows in the database!");
                }
                data.refreshOnlinePlayers();
            }
        }
        return true;
    }

    /**
     * Migrate current server tokens to a connected SQL database.
     * To migrate, these requirements will be checked:
     * 1) SQL is connected
     * 2) There are locally stored tokens
     * 3) migrate-local-data is set to true in the config
     * 4) There is at least 1 player online
     * @return True if tokens were migrated
     */
    public static boolean migrateToSQL(){
        boolean change = false;
        // check if migration should occur
        if (SQL.isConnected() && !currentServerTokens.isEmpty() && SQL.isMigrateLocalData() && !Bukkit.getOnlinePlayers().isEmpty()) {
            //Bukkit.getLogger().info("[NotTokensPremium] Migrating local storage to database.");
            // add entries to database
            for (Map.Entry<UUID, Double> entry : currentServerTokens.entrySet()) {
                if (entry.getValue() != 0.0) {
                    data.giveTokens(entry.getKey(), entry.getValue());
                    change = true;
                }
            }
            if (change) {
                if (ProxyMessaging.hasConnectedBefore())
                    eraseProxyTokens();
                currentServerTokens.clear();
                TransactionLogs.log("Migrated all tokens to an SQL database.");
            }
        }
        return change;
    }

    /**
     * Erases all tokens from the proxy. This should be followed up with a currentServerTokens.clear();
     */
    private static void eraseProxyTokens() {
        // load up this map with the negative values of current tokens
        Map<UUID, Double> playerTokens = new HashMap<>();
        for (Map.Entry<UUID, Double> entry : currentServerTokens.entrySet()) {
            playerTokens.put(entry.getKey(), -1 * entry.getValue());
        }
        tokenTransmissionQueue.clear();
        // transmit to proxy or save to queue if no messages can be sent (no players online)
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            tokenTransmissionQueue.putAll(playerTokens);
        } else {
            ProxyMessaging.sendServerTokenUpdate(playerTokens);
        }
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
            if (ConfigOptions.leaderboardExclusion.stream().flatMap(name -> Stream.of(name.toLowerCase())).collect(Collectors.toList()).contains(LoggedPlayers.getPlayerName(entry.getKey()).toLowerCase()))
                continue;
            cutList.put(entry.getKey(), entry.getValue());
            amount--;
            if (amount == 0)
                break;
        }
        return cutList;
    }

    /**
     * Get a map of all the recorded tokens
     * @return All saved tokens
     */
    public static Map<UUID, Double> getAllTokens() {
        if (SQL.isConnected())
            return data.getTopTokens(LoggedPlayers.getAllUUIDs().size());
        return currentServerTokens;
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
     * @return true if a message should be sent to the player
     */
    public static boolean giveTokens(UUID uuid, double amount) {
        return setTokens(uuid, getTokens(uuid) + amount);
    }

    /**
     * Remove tokens from a player.
     *
     * @param uuid UUID of player to have tokens removed
     * @param amount Amount of tokens to be removed
     * @return true if a message should be sent to the player
     */
    public static boolean removeTokens(UUID uuid, double amount) {
        // auto exchange
        if (getTokens(uuid) - amount < 0) {
            Player player = Bukkit.getPlayer(uuid);
            if (ItemExchange.autoExchange && player != null) {
                double requiredItems = amount * -1 / ItemExchange.getValue();
                if (ItemExchange.getObject().contains("%")) {
                    if (requiredItems - (long) requiredItems > 0) {
                        requiredItems++;
                        requiredItems = (long) requiredItems;
                    }
                    double refund = requiredItems * ItemExchange.getValue() + amount;
                    if (requiredItems <= ItemExchange.getBalance(player)) {
                        ItemExchange.deposit(player, requiredItems);
                        return setTokens(uuid, getTokens(uuid) - amount + refund);
                    } else {
                        ItemExchange.deposit(player, ItemExchange.getBalance(player));
                    }
                } else {
                    ItemExchange.deposit(player, Math.min(requiredItems, ItemExchange.getBalance(player)));
                }
            }

        }
        return setTokens(uuid, getTokens(uuid) - amount);
    }

    /***
     * Sets the tokens of a player.
     * @param uuid UUID of player to set the tokens of
     * @param amount New amount of tokens
     * @return true if a message should be sent to the player
     */
    public static boolean setTokens(UUID uuid, double amount) {
        // change in token balance for the player
        double currentTokens = getTokens(uuid);
        // make sure the amount isn't below 0 if negative tokens is false

        if (!ConfigOptions.negativeTokens && amount < 0)
            amount = 0;
        double difference = amount - currentTokens;
        if (difference == 0)
            return false;
        // change current tokens
        if (!SQL.isConnected()) {
            currentServerTokens.put(uuid, amount);
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                // update token transmission queue if the server is empty
                if (tokenTransmissionQueue.containsKey(uuid))
                    tokenTransmissionQueue.replace(uuid, tokenTransmissionQueue.get(uuid) + difference);
                else
                    tokenTransmissionQueue.put(uuid, difference);
            } else {
                ProxyMessaging.sendPlayerTokenUpdate(uuid, difference);
            }
        } else {
            // update SQL database if it is connected
            // this could be replaced with data.setTokens(uuid, amount);
            // the only difference is the sql command - UPDATE is more efficient than REPLACE
            if (difference < 0)
                data.removeTokens(uuid, -1 * difference);
            else if (difference > 0)
                data.giveTokens(uuid, difference);
        }

        // check to send message or add to queue
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (player.isOnline()) {
            if (tokenMessagingQueue.containsKey(uuid)) {
                tokenMessagingQueue.get(uuid).addTokenChange(difference);
                return false;
            } else {
                // add new obj to queue
                tokenMessagingQueue.put(uuid, new TokenMessage());
                return true;
            }
        }
        return false;
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
     * This is to be triggered by a message from the proxy
     * @param serverTokens Tokens stored on the proxy
     */
    public static void connectProxy(Map<UUID, Double> serverTokens) {
        if (!tokenTransmissionQueue.isEmpty()) {
            // send local changes
            if (Bukkit.getOnlinePlayers().isEmpty())
                return; // nothing to receive current tokens
            ProxyMessaging.sendServerTokenUpdate(tokenTransmissionQueue);
        }
        // replace current tokens with tokens from the proxy
        currentServerTokens.clear();
        /*for (Map.Entry<UUID, Double> entry : serverTokens.entrySet())
            Bukkit.getLogger().info(entry.getKey() + ":" + entry.getValue());*/
        currentServerTokens.putAll(serverTokens);
        // add local changes to current tokens to sync the amounts
        for (Map.Entry<UUID, Double> entry : tokenTransmissionQueue.entrySet()) {
            if (currentServerTokens.containsKey(entry.getKey()))
                currentServerTokens.replace(entry.getKey(), currentServerTokens.get(entry.getKey()) + entry.getValue());
            else
                currentServerTokens.put(entry.getKey(), entry.getValue());
        }
        tokenTransmissionQueue.clear(); // clear local changes
        Bukkit.getLogger().info("[NotTokensPremium] Connected to proxy!");
    }


    /**
     * Get the tokens corresponding to a UUID. If an SQL server is connected, tokens will be retried from there.
     * @param uuid UUID that holds tokens
     * @return The amount of tokens corresponding to the UUID or 0 if this UUID hasn't been recorded yet.
     */
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
                        continue;
                    if (entry.getValue().getTokenChange() == 0.0)
                        continue;
                    // send message
                    OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                    if (player.isOnline()) {
                        Objects.requireNonNull(player.getPlayer()).sendMessage(Language.parse(Language.prefix + Language.reducedMessage, entry.getValue().getTokenChange(), System.currentTimeMillis() - entry.getValue().getLastMessageTime(), player));
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



}
