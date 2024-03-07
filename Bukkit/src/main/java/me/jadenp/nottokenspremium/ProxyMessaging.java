package me.jadenp.nottokenspremium;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

public class ProxyMessaging implements PluginMessageListener, Listener {
    private static boolean connectedBefore = false;

    /**
     * Check if the proxy has been connected since server start
     * @return True if the proxy has connected to the plugin since the server has started
     */
    public static boolean hasConnectedBefore() {
        return connectedBefore;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] bytes) {
        if (!channel.equals("nottokens:main"))
            return;
        connectedBefore = true;
        ByteArrayDataInput in = ByteStreams.newDataInput(bytes);
        String subChannel = in.readUTF();
        //Bukkit.getLogger().info(subChannel);
        switch (subChannel) {
            case "ReceiveConnection":
                short savedPlayers = in.readShort();
                Map<UUID, Double> networkTokens = new HashMap<>();
                for (short i = 0; i < savedPlayers; i++) {
                    try {
                        UUID uuid = UUID.fromString(in.readUTF());
                        double amount = in.readDouble();
                        networkTokens.put(uuid, amount);
                    } catch (IllegalArgumentException e) {
                        Bukkit.getLogger().warning("[NotTokensPremium] Error reading uuid from proxy message!");
                    }
                }
                TokenManager.connectProxy(networkTokens);
                break;
            case "PlayerList":
                String playerList = in.readUTF(); // CSV (Comma-Separated Values)

                String[] splitList = playerList.split(",");
                //Bukkit.getLogger().info("Received PlayerList: " + Arrays.toString(splitList));
                // send them over to LoggedPlayers
                LoggedPlayers.clearOnlinePlayers();
                LoggedPlayers.receiveNetworkPlayers(List.of(splitList));
                break;
            case "Forward": {
                in.readUTF(); // ALL
                String subSubChannel = in.readUTF();
                switch (subSubChannel) {
                    case "ServerTokenUpdate": {
                        short len = in.readShort();
                        byte[] msgbytes = new byte[len];
                        in.readFully(msgbytes);

                        DataInputStream msgIn = new DataInputStream(new ByteArrayInputStream(msgbytes));
                        int maxReceive = 2000;
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
                                //Bukkit.getLogger().info("Reached End");
                                break;
                            } catch (IOException e) {
                                Bukkit.getLogger().warning("[NotTokensPremium] Error receiving message from proxy!");
                                Bukkit.getLogger().warning(e.toString());
                            }
                            maxReceive--;
                        }
                        break;
                    }
                    case "PlayerTokenUpdate": {
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
                    case "LogAllPlayers": {
                        short len = in.readShort();
                        byte[] msgbytes = new byte[len];
                        in.readFully(msgbytes);

                        DataInputStream msgIn = new DataInputStream(new ByteArrayInputStream(msgbytes));
                        try {
                            for (int i = 0; i < 2000; i++) {
                                String msg = msgIn.readUTF();
                                String playerName = msg.substring(msg.indexOf(":") + 1);
                                String uuid = msg.substring(0, msg.indexOf(":"));
                                try {
                                    LoggedPlayers.logPlayer(playerName, UUID.fromString(uuid));
                                } catch (IllegalArgumentException e) {
                                    Bukkit.getLogger().warning("[NotTokensPremium] Could not get a uuid from the text: " + uuid + " (" + playerName + ")");
                                }
                            }

                        } catch (EOFException e) {
                            //Bukkit.getLogger().info("Reached End");
                            break;
                        } catch (IOException e) {
                            Bukkit.getLogger().warning("[NotTokensPremium] Error receiving message from proxy!");
                            Bukkit.getLogger().warning(e.toString());
                        }
                        break;
                    }
                }
                break;
            }

        }
    }

    public ProxyMessaging(){
    }


    /**
     * Sends a message to the backend server
     * @param identifier message identifier
     * @param data data to be sent
     * @return true if the message was sent successfully
     */
    public static boolean sendMessage(String identifier, byte[] data) {
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            sendMessage(identifier, data, Bukkit.getOnlinePlayers().iterator().next());
            return true;
        }
         return false;
    }

    /**
     * Sends a message to the backend server
     *
     * @param identifier message identifier
     * @param data       data to be sent
     * @param player     player to send the message through
     */
    public static void sendMessage(String identifier, byte[] data, Player player) {
        //Bukkit.getLogger().info("Sending: " + identifier + " with " + player.getName());
        player.sendPluginMessage(NotTokensPremium.getInstance(), identifier, data);
        // return is for future compatibility
    }

    /**
     * Send a token update for a single player
     *
     * @param uuid        UUID of player to be updated
     * @param tokenChange Tokens to be changed from the player's balance
     */
    public static void sendPlayerTokenUpdate(UUID uuid, double tokenChange) {
        // send proxy message
        try {
            byte[] message = wrapGlobalMessage(encodeMessage(uuid.toString(), tokenChange), "PlayerTokenUpdate");
            sendMessage("nottokens:main", message);
        } catch (IOException e) {
            Bukkit.getLogger().warning("Could not send a token update for " + LoggedPlayers.getPlayerName(uuid));
            Bukkit.getLogger().warning(e.toString());
        }
    }

    /**
     * Send token updates for multiple players
     *
     * @param playerTokens Map of uuid of player and amount of tokens to be changed
     */
    public static void sendServerTokenUpdate(Map<UUID, Double> playerTokens) {
        // send proxy message
        try {
            byte[] message = wrapGlobalMessage(encodeMessage(playerTokens), "ServerTokenUpdate");
            sendMessage("nottokens:main", message);
        } catch (IOException e) {
            Bukkit.getLogger().warning("Could not send a server token update.");
            Bukkit.getLogger().warning(e.toString());
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
        return sendMessage("nottokens:main", out.toByteArray());
    }

    /**
     * Sends a player to the network to be logged
     * @param player Player to be logged
     */
    public static void logNewPlayer(Player player) {
        logNewPlayer(player.getName(), player.getUniqueId());
    }

    /**
     * Sends a player to the network to be logged
     *
     * @param playerName The name of the player
     * @param uuid       The UUID of the player
     */
    public static void logNewPlayer(String playerName, UUID uuid) {
        ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
        DataOutputStream msgout = new DataOutputStream(msgbytes);
        try {
            msgout.writeUTF(playerName);
            msgout.writeUTF(uuid.toString());
        } catch (IOException e) {
            Bukkit.getLogger().warning(e.toString());
            return;
        }
        sendMessage("nottokens:main", wrapGlobalMessage(msgbytes, "LogPlayer"));
    }



}
