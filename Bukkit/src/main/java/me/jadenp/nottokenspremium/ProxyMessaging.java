package me.jadenp.nottokenspremium;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.tokens.Token;

import java.io.*;
import java.util.*;

public class ProxyMessaging implements PluginMessageListener, Listener {

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] bytes) {
        if (!channel.equalsIgnoreCase("BungeeCord"))
            return;
        ByteArrayDataInput in = ByteStreams.newDataInput(bytes);
        String subchannel = in.readUTF();
        switch (subchannel) {
            case "PlayerList":
                String server = in.readUTF(); // this is currently not used but could be in the future

                String playerList = in.readUTF(); // CSV (Comma-Separated Values)

                String[] splitList = playerList.split(",");
                // send them over to LoggedPlayers
                LoggedPlayers.receiveNetworkPlayers(List.of(splitList));
                break;
            case "LogPlayer": {
                short len = in.readShort();
                byte[] msgbytes = new byte[len];
                in.readFully(msgbytes);

                DataInputStream msgIn = new DataInputStream(new ByteArrayInputStream(msgbytes));
                try {
                    String playerName = msgIn.readUTF();
                    String uuid = msgIn.readUTF();
                    try {
                        LoggedPlayers.logPlayer(playerName, UUID.fromString(uuid));
                    } catch (IllegalArgumentException e) {
                        Bukkit.getLogger().warning("[NotTokensPremium] Could not get a uuid from the text: " + uuid + " (" + playerName + ")");
                    }
                } catch (IOException e) {
                    Bukkit.getLogger().warning("[NotTokensPremium] Error receiving message from proxy!");
                    Bukkit.getLogger().warning(e.toString());
                }
                break;
            }
            case "ServerTokenMessage": {
                short len = in.readShort();
                byte[] msgbytes = new byte[len];
                in.readFully(msgbytes);

                DataInputStream msgIn = new DataInputStream(new ByteArrayInputStream(msgbytes));
                int maxReceive = 200;
                while (maxReceive > 0) {
                    try {
                        String uuid = msgIn.readUTF();
                        double tokenChange = msgIn.readDouble();
                        try {
                            TokenManager.editTokensSilently(UUID.fromString(uuid), tokenChange);
                        } catch (IllegalArgumentException e) {
                            Bukkit.getLogger().warning("[NotTokensPremium] Could not get a uuid from the text: " + uuid + " (" + tokenChange + " token change)");
                        }
                    } catch (EOFException e) {
                        Bukkit.getLogger().info("Reached End");
                        break;
                    } catch (IOException e) {
                        Bukkit.getLogger().warning("[NotTokensPremium] Error receiving message from proxy!");
                        Bukkit.getLogger().warning(e.toString());
                    }
                    maxReceive--;
                }
                break;
            }
            case "PlayerTokenMessage": {
                short len = in.readShort();
                byte[] msgbytes = new byte[len];
                in.readFully(msgbytes);

                DataInputStream msgIn = new DataInputStream(new ByteArrayInputStream(msgbytes));
                try {
                    String uuid = msgIn.readUTF();
                    double tokenChange = msgIn.readDouble();
                    try {
                        TokenManager.editTokensSilently(UUID.fromString(uuid), tokenChange);
                    } catch (IllegalArgumentException e) {
                        Bukkit.getLogger().warning("[NotTokensPremium] Could not get a uuid from the text: " + uuid + " (" + tokenChange + " token change)");
                    }
                } catch (IOException e) {
                    Bukkit.getLogger().warning("[NotTokensPremium] Error receiving message from proxy!");
                    Bukkit.getLogger().warning(e.toString());
                }
                break;
            }
        }
    }

    private static boolean connected = false;
    public ProxyMessaging(){
        connected = true;
    }

    public static boolean isConnected() {
        return connected;
    }

    /**
     * Transmit tokens to other server on leave
     * This is important to avoid withdrawing more tokens than the player has
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!TokenManager.isUsingTransmission())
            return;
        double transmission = TokenManager.separateTransmission(event.getPlayer().getUniqueId());
        if (transmission != 0)
            if (!sendPlayerTokenUpdate(event.getPlayer(), transmission))
                Bukkit.getLogger().warning("[NotTokensPremium] Error transmitting " + transmission + " tokens to the proxy for " + event.getPlayer().getName());
    }

    /**
     * Sends a message to the backend server
     * @param identifier message identifier
     * @param data data to be sent
     * @return true if the message was sent successfully
     */
    public static boolean sendMessage(String identifier, byte[] data) {
         Bukkit.getServer().sendPluginMessage(NotTokensPremium.getInstance(), identifier, data);
         // return is for future compatibility
         return true;
    }

    /**
     * Sends a message to the backend server
     * @param identifier message identifier
     * @param data data to be sent
     * @param player player to send the message through
     * @return true if the message was sent successfully
     */
    public static boolean sendMessage(String identifier, byte[] data, Player player) {
        player.sendPluginMessage(NotTokensPremium.getInstance(), identifier, data);
        // return is for future compatibility
        return true;
    }

    /**
     * Send a token update for a single player
     * @param player Player to be updated
     * @param tokenChange Tokens to be changed from the player's balance
     * @return True if the message was successful
     */
    public static boolean sendPlayerTokenUpdate(Player player, double tokenChange) {
        try {
            byte[] message = wrapGlobalMessage(encodeMessage(player.getUniqueId().toString(), tokenChange), "PlayerTokenUpdate");
            return sendMessage("BungeeCord", message, player);
        } catch (IOException e) {
            Bukkit.getLogger().warning("Could not send a token update for " + player.getName());
            Bukkit.getLogger().warning(e.toString());
            return false;
        }
    }

    /**
     * Send token updates for multiple players
     * @param playerTokens Map of uuid of player and amount of tokens to be changed
     * @return True if the message was successful
     */
    public static boolean sendServerTokenUpdate(Map<UUID, Double> playerTokens) {
        try {
            byte[] message = wrapGlobalMessage(encodeMessage(playerTokens), "ServerTokenUpdate");
            return sendMessage("BungeeCord", message);
        } catch (IOException e) {
            Bukkit.getLogger().warning("Could not send a server token update.");
            Bukkit.getLogger().warning(e.toString());
            return false;
        }
    }

    /**
     * Wraps a message in the needed bytes to send the message globally
     * @param stream A ByteAArrayOutputStream to be sent as the message
     * @return A byte[] ready to be sent as a message
     */
    private static byte[] wrapGlobalMessage(ByteArrayOutputStream stream, String channel) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF("ALL"); // This is the target server. "ALL" will message all servers apart from the one sending the message
        out.writeUTF(channel); // This is the channel.

        out.writeShort(stream.toByteArray().length); // This is the length.
        out.write(stream.toByteArray()); // This is the message.

        return out.toByteArray();
    }

    /**
     * Encodes a message in a byte array to be sent to all the other servers on the network
     * @param message Message to be sent
     * @param value Value to be encoded with the message
     * @return A ByteArrayOutputStream with the message ready to be wrapped
     * @throws IOException if an I/O error occurs
     */
    private static ByteArrayOutputStream encodeMessage(String message, double value) throws IOException {
        ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
        DataOutputStream msgout = new DataOutputStream(msgbytes);
        msgout.writeUTF(message);
        msgout.writeDouble(value);
        return msgbytes;
    }

    /**
     * Encodes a message in a byte array to be sent to all the other servers on the network.
     * This function will encode every value in the map to the message as a string and double
     * @param playerTokens Map to encode
     * @return A ByteArrayOutputStream with the message ready to be wrapped
     * @throws IOException if an I/O error occurs
     */
    private static ByteArrayOutputStream encodeMessage(Map<UUID, Double> playerTokens) throws IOException {
        ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
        DataOutputStream msgout = new DataOutputStream(msgbytes);
        for (Map.Entry<UUID, Double> entry : playerTokens.entrySet()) {
            msgout.writeUTF(entry.getKey().toString());
            msgout.writeDouble(entry.getValue());
        }
        return msgbytes;
    }

    /**
     * Request the player list from all servers
     * @return True if the request was successful
     */
    public static boolean requestPlayerList(){
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("PlayerList");
        out.writeUTF("ALL");
        return sendMessage("BungeeCord", out.toByteArray());
    }

    /**
     * Sends a player to the network to be logged
     * @param player Player to be logged
     * @return True if the message was successful
     */
    public static boolean logNewPlayer(Player player) {
        ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
        DataOutputStream msgout = new DataOutputStream(msgbytes);
        try {
            msgout.writeUTF(player.getName());
            msgout.writeUTF(player.getUniqueId().toString());
        } catch (IOException e) {
            Bukkit.getLogger().warning("[NotTokensPremium] Error logging new player to the network.");
            Bukkit.getLogger().warning(e.toString());
            return false;
        }
        return sendMessage("BungeeCord", wrapGlobalMessage(msgbytes, "LogPlayer"), player);
    }


}
