package me.jadenp.bungeecord;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.io.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static me.jadenp.bungeecord.NotTokensPremium.*;

public class ProxyMessaging implements Listener {

    @EventHandler
    public void onPluginMessageEvent(PluginMessageEvent event) {
        Logger logger = NotTokensPremium.getInstance().getLogger();
        if (!event.getTag().equals("nottokens:main"))
            return;
        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String channel = in.readUTF();
        //logger.info(channel);
        if (channel.equalsIgnoreCase("Forward")) {
            in.readUTF(); // ALL
            String subChannel = in.readUTF();
            switch (subChannel) {
                case "ServerTokenUpdate": {
                    short len = in.readShort();
                    byte[] msgbytes = new byte[len];
                    in.readFully(msgbytes);
                    //logger.info("Length: " + len);
                    DataInputStream msgIn = new DataInputStream(new ByteArrayInputStream(msgbytes));
                    int maxReceive = 2000;
                    while (maxReceive > 0) {
                        try {
                            String uuid = msgIn.readUTF();
                            double tokenChange = msgIn.readDouble();
                            ///logger.info(uuid + " : " + tokenChange);
                            try {
                                editTokens(UUID.fromString(uuid), tokenChange);
                            } catch (IllegalArgumentException e) {
                                logger.warning("[NotTokensPremium] Could not get a uuid from the text: " + uuid + " (" + tokenChange + " token change)");
                            }
                        } catch (EOFException e) {
                            //logger.warn("Reached End");
                            break;
                        } catch (IOException e) {
                            logger.warning("[NotTokensPremium] Error receiving message from backend!");
                            logger.warning(e.toString());
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
                            editTokens(UUID.fromString(uuid), tokenChange);
                        } catch (IllegalArgumentException e) {
                            logger.warning("[NotTokensPremium] Could not get a uuid from the text: " + uuid + " (" + tokenChange + " token change)");
                        }
                    } catch (IOException e) {
                        logger.warning("[NotTokensPremium] Error receiving message from backend!");
                        logger.warning(e.toString());
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
                        // send msg to all other servers
                        for (Map.Entry<String, ServerInfo> entry : getInstance().getProxy().getServers().entrySet()) {
                            if (event.getSender().getSocketAddress().equals(entry.getValue().getSocketAddress())) {
                                continue; // same server
                            }
                            if (entry.getValue().getPlayers().isEmpty()) {
                                try {
                                    logPlayer(entry.getValue(), UUID.fromString(uuid), playerName);
                                } catch (IllegalArgumentException e) {
                                    logger.warning("[NotTokensPremium] Could not get a uuid from the text: " + uuid + " (" + playerName + ")");
                                }
                            }
                        }

                    } catch (IOException e) {
                        logger.warning("[NotTokensPremium] Error receiving message from backend!");
                        logger.warning(e.toString());
                    }
                    break;
                }
            }
            for (Map.Entry<String, ServerInfo> entry : getInstance().getProxy().getServers().entrySet()) {
                if (event.getSender().getSocketAddress().equals(entry.getValue().getSocketAddress())) {
                    continue; // same server
                }
                sendMessage(entry.getValue(), event.getData());
            }
        } else if (channel.equals("PlayerList")) {
            StringBuilder builder = new StringBuilder();
            ServerInfo sender = null;
            for (Map.Entry<String, ServerInfo> entry : getInstance().getProxy().getServers().entrySet()) {
                if (event.getSender().getSocketAddress().equals(entry.getValue().getSocketAddress())) {
                    sender = entry.getValue();
                    continue; // same server
                }
                for (ProxiedPlayer player : entry.getValue().getPlayers())
                    builder.append(player.getName()).append(":").append(player.getUniqueId().toString()).append(",");
            }
            if (builder.length() > 0)
                builder.deleteCharAt(builder.length()-1);
            ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
            DataOutputStream msgout = new DataOutputStream(msgbytes);
            try {
                msgout.writeUTF("PlayerList");
                msgout.writeUTF(builder.toString());
            } catch (IOException e) {
                logger.warning("[NotTokensPremium] Failed to prepare a proxy player list");
                logger.warning(e.toString());
                return;
            }
            if (sender == null || !sendMessage(sender, msgbytes.toByteArray())){
                logger.warning("[NotTokensPremium] Failed to send a proxy player list");
            }
        }
    }

    public ProxyMessaging(){
    }


    /**
     * Sends a message to a server
     * @param info Server to send data to
     * @param data data to be sent
     * @return true if the message was sent successfully
     */
    public static boolean sendMessage(ServerInfo info, byte[] data) {
        if (!info.getPlayers().isEmpty()) {
            info.sendData("nottokens:main", data);
            return true;
        }
         return false;
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        Server connectedServer = event.getServer();
        if (connectedServer.getInfo().getPlayers().isEmpty()) {
            NotTokensPremium.getInstance().getProxy().getScheduler().schedule(NotTokensPremium.getInstance(), () -> {
                ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
                DataOutputStream msgout = new DataOutputStream(msgbytes);
                try {
                    msgout.writeUTF("ReceiveConnection");
                    msgout.writeShort(networkTokens.size());
                    // write player data
                    for (Map.Entry<UUID, Double> savedTokens : networkTokens.entrySet()) {
                        msgout.writeUTF(savedTokens.getKey().toString());
                        msgout.writeDouble(savedTokens.getValue());
                    }
                } catch (IOException e) {
                    NotTokensPremium.getInstance().getLogger().warning("[NotTokensPremium] Failed to prepare a proxy connection request");
                    NotTokensPremium.getInstance().getLogger().warning(e.toString());
                    return;
                }
                if (!sendMessage(connectedServer.getInfo(), msgbytes.toByteArray())){
                    NotTokensPremium.getInstance().getLogger().warning("[NotTokensPremium] Failed to send a proxy connection request");
                }
            }, 2L, TimeUnit.SECONDS);

            NotTokensPremium.getInstance().getProxy().getScheduler().schedule(NotTokensPremium.getInstance(), () -> {
                if (!playersToLog.containsKey(connectedServer.getInfo())) {
                    return;
                }
                ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
                DataOutputStream msgout = new DataOutputStream(msgbytes);
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                try {
                    for (Map.Entry<UUID, String> entry : playersToLog.get(connectedServer.getInfo()).entrySet()) {
                        msgout.writeUTF(entry.getKey().toString() + ":" + entry.getValue());
                    }
                    out.writeShort(msgbytes.toByteArray().length); // This is the length.
                    out.write(msgbytes.toByteArray()); // This is the message.
                    out.writeUTF("Forward");
                    out.writeUTF("ALL");
                    out.writeUTF("LogAllPlayers");
                    out.writeShort(msgbytes.toByteArray().length);

                } catch (IOException e) {
                    NotTokensPremium.getInstance().getLogger().warning("[NotTokensPremium] Failed to prepare a proxy log message");
                    NotTokensPremium.getInstance().getLogger().warning(e.toString());
                    return;
                }
                if (!sendMessage(connectedServer.getInfo(), msgbytes.toByteArray())){
                    NotTokensPremium.getInstance().getLogger().warning("[NotTokensPremium] Failed to send a proxy log message");
                } else {
                    playersToLog.remove(connectedServer.getInfo());
                }
            }, 3L, TimeUnit.SECONDS);
        }
    }
}

