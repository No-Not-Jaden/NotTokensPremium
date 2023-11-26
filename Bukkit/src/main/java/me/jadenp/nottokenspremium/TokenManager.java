package me.jadenp.nottokenspremium;

import me.jadenp.nottokenspremium.Configuration.ConfigOptions;
import me.jadenp.nottokenspremium.Configuration.Language;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class TokenManager {
    private static BukkitTask tokenTransmissionTask = null;
    private static BukkitTask tokenMessagingTask = null;
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
     * Loads any locally saved tokens into the system
     */
    public static void loadLocalTokens() {
        tokensHolder = new File(NotTokensPremium.getInstance().getDataFolder() + File.separator + "tokensHolder.yml");

    }

    public static File getTokensHolder() {
        return tokensHolder;
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
        double difference = getTokens(uuid) - amount;
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
        return true;
    }

    /**
     * Edit the player's tokens without sending a message or triggering a transmission
     * @param uuid UUID of player
     * @param amount Token change amount
     */
    public static void editTokensSilently(UUID uuid, double amount) {
        currentServerTokens.put(uuid, getTokens(uuid) + amount);
    }

    public static double getTokens(UUID uuid) {
        if (currentServerTokens.containsKey(uuid))
            return currentServerTokens.get(uuid);
        return 0;
    }

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

            }
        }.runTaskTimerAsynchronously(NotTokensPremium.getInstance(), 50, 21);
    }
}
