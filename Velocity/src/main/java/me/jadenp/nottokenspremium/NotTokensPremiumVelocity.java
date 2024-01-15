package me.jadenp.nottokenspremium;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "nottokens",
        name = "NotTokens Premium",
        version = "1.0",
        authors = {"Not_Jaden"}
)
public class NotTokensPremiumVelocity {

    private final Logger logger;
    private final Path dataDirectory;
    private final ProxyServer proxy;
    public static final MinecraftChannelIdentifier IDENTIFIER = MinecraftChannelIdentifier.from("nottokenspremium:main");
    private static Map<UUID, Double> networkTokens;
    private static final Map<RegisteredServer, Map<UUID, String>> playersToLog = new HashMap<>();
    private static NotTokensPremiumVelocity instance;

    public static NotTokensPremiumVelocity getInstance() {
        return instance;
    }


    @Inject
    public NotTokensPremiumVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        instance = this;
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(IDENTIFIER);
        File tokensHolder = new File(dataDirectory + File.separator + "tokensholder.json");

        File directory = dataDirectory.toFile();
        try {
            if (directory.mkdir()) {
                logger.info("[NotTokensPremium] Created a new NotTokensPremium directory file.");
            }
            if (tokensHolder.createNewFile()) {
                logger.info("[NotTokensPremium] Created a new tokens holder file.");
            }
            read();
        } catch (IOException e) {
            logger.info(e.toString());
            throw new RuntimeException();
        }

        proxy.getScheduler().buildTask(this, () -> {
            try {
                save();
            } catch (IOException e) {
                logger.warn("[NotTokensPremium] Could not auto save tokens to file!");
                logger.warn(e.toString());
            }
        }).repeat(10L, TimeUnit.MINUTES).schedule();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {

        try {
            save();
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        //logger.info(event.getSource().toString());
        //logger.info(event.getSource().getClass().toString());
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }

        ServerConnection backend = (ServerConnection) event.getSource();
        // Ensure the identifier is what you expect before trying to handle the data
        if (event.getIdentifier() != NotTokensPremiumVelocity.IDENTIFIER) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String channel = in.readUTF();
        logger.info("Channel: " + channel);
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
                                logger.warn("[NotTokensPremium] Could not get a uuid from the text: " + uuid + " (" + tokenChange + " token change)");
                            }
                        } catch (EOFException e) {
                            //logger.warn("Reached End");
                            break;
                        } catch (IOException e) {
                            logger.warn("[NotTokensPremium] Error receiving message from backend!");
                            logger.warn(e.toString());
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
                            logger.warn("[NotTokensPremium] Could not get a uuid from the text: " + uuid + " (" + tokenChange + " token change)");
                        }
                    } catch (IOException e) {
                        logger.warn("[NotTokensPremium] Error receiving message from backend!");
                        logger.warn(e.toString());
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
                        for (RegisteredServer server : proxy.getAllServers()) {
                            if (backend.getServer().equals(server))
                                continue;
                            if (server.getPlayersConnected().isEmpty())
                                try {
                                    logPlayer(server, UUID.fromString(uuid), playerName);
                                } catch (IllegalArgumentException e) {
                                    logger.warn("[NotTokensPremium] Could not get a uuid from the text: " + uuid + " (" + playerName + ")");
                                }
                        }

                    } catch (IOException e) {
                        logger.warn("[NotTokensPremium] Error receiving message from backend!");
                        logger.warn(e.toString());
                    }
                    break;
                }
            }
            for (RegisteredServer server : proxy.getAllServers()) {
                if (backend.getServer().equals(server))
                    continue;
                sendServerMessage(server, event.getData());
            }
        } else if (channel.equals("PlayerList")) {
            StringBuilder builder = new StringBuilder();
            for (RegisteredServer server : proxy.getAllServers()) {
                if (backend.getServer().equals(server))
                    continue;
                for (Player player : server.getPlayersConnected())
                    builder.append(player.getUsername()).append(":").append(player.getUniqueId().toString()).append(",");
            }
            if (builder.length() > 0)
                builder.deleteCharAt(builder.length()-1);
            ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
            DataOutputStream msgout = new DataOutputStream(msgbytes);
            try {
                msgout.writeUTF("PlayerList");
                msgout.writeUTF(builder.toString());
            } catch (IOException e) {
                logger.warn("[NotTokensPremium] Failed to prepare a proxy player list");
                logger.warn(e.toString());
                return;
            }
            if (!backend.sendPluginMessage(IDENTIFIER, msgbytes.toByteArray())){
                logger.warn("[NotTokensPremium] Failed to send a proxy player list");
            }
        }
    }

    public void sendServerMessage(RegisteredServer server, byte[] data) {
        if (!server.getPlayersConnected().isEmpty())
            server.sendPluginMessage(IDENTIFIER, data);
    }

    public void logPlayer(RegisteredServer server, UUID uuid, String name) {
        Map<UUID, String> loggedPlayers = playersToLog.containsKey(server) ? playersToLog.get(server) : new HashMap<>();
        loggedPlayers.put(uuid, name);
        playersToLog.put(server, loggedPlayers);
    }

    private void save() throws IOException {
        File tokensHolder = new File(dataDirectory + File.separator + "tokensholder.json");
        Gson gson = new Gson();
        Type typeObject = new TypeToken<Map<UUID, Double>>() {}.getType();
        FileWriter writer = new FileWriter(tokensHolder);
        gson.toJson(networkTokens, typeObject, writer);
        writer.close();
    }

    private void read() throws IOException {
        File tokensHolder = new File(dataDirectory + File.separator + "tokensholder.json");
        Gson gson = new Gson();
        Type typeObject = new TypeToken<Map<UUID, Double>>() {}.getType();
        JsonReader reader = new JsonReader(new FileReader(tokensHolder));
        networkTokens = gson.fromJson(reader, typeObject);
        reader.close();
        if (networkTokens == null)
            networkTokens = new HashMap<>();
    }

    private void editTokens(UUID uuid, double change) {
        if (networkTokens.containsKey(uuid))
            networkTokens.replace(uuid, networkTokens.get(uuid) + change);
        else
            networkTokens.put(uuid, change);
        //logger.info("tokens: " + networkTokens.get(uuid));
    }

    @Subscribe
    public void onServerConnect(ServerPostConnectEvent event, Continuation continuation) {
        Optional<ServerConnection> connectedServer = event.getPlayer().getCurrentServer();
        if (connectedServer.isPresent() && connectedServer.get().getServer().getPlayersConnected().size() == 1) {
            RegisteredServer server = connectedServer.get().getServer();
            proxy.getScheduler()
                    .buildTask(this, () -> {
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
                            logger.warn("[NotTokensPremium] Failed to prepare a proxy connection request");
                            logger.warn(e.toString());
                            return;
                        }
                        if (!server.sendPluginMessage(IDENTIFIER, msgbytes.toByteArray())){
                            logger.warn("[NotTokensPremium] Failed to send a proxy connection request");
                        }
                    })
                    .delay(2L, TimeUnit.SECONDS)
                    .schedule();
            proxy.getScheduler()
                    .buildTask(this, () -> {
                        if (!playersToLog.containsKey(server)) {
                            return;
                        }
                        ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
                        DataOutputStream msgout = new DataOutputStream(msgbytes);
                        ByteArrayDataOutput out = ByteStreams.newDataOutput();
                        try {
                            for (Map.Entry<UUID, String> entry : playersToLog.get(server).entrySet()) {
                                msgout.writeUTF(entry.getKey().toString() + ":" + entry.getValue());
                            }
                            out.writeShort(msgbytes.toByteArray().length); // This is the length.
                            out.write(msgbytes.toByteArray()); // This is the message.
                            out.writeUTF("Forward");
                            out.writeUTF("ALL");
                            out.writeUTF("LogAllPlayers");
                            out.writeShort(msgbytes.toByteArray().length);

                        } catch (IOException e) {
                            logger.warn("[NotTokensPremium] Failed to prepare a proxy log message");
                            logger.warn(e.toString());
                            return;
                        }
                        if (!server.sendPluginMessage(IDENTIFIER, msgbytes.toByteArray())){
                            logger.warn("[NotTokensPremium] Failed to send a proxy log message");
                        } else {
                            playersToLog.remove(server);
                        }
                    })
                    .delay(3L, TimeUnit.SECONDS)
                    .schedule();
        }
        continuation.resume();
    }

}
