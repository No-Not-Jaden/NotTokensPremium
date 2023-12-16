package me.jadenp.nottokenspremium;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.bukkit.Bukkit;
import org.slf4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    private static Map<UUID, Double> networkTokens = new HashMap<>();


    @Inject
    public NotTokensPremiumVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        logger.info("Hello from NotTokens!");

    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(IDENTIFIER);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (event.getSource() == null) {
            return;
        }
        Player player = (Player) event.getSource();
        // Ensure the identifier is what you expect before trying to handle the data
        if (event.getIdentifier() != NotTokensPremiumVelocity.IDENTIFIER) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String channel = in.readUTF();
        if (channel.equalsIgnoreCase("RequestConnection")) {
            logger.info("Received a connection request!");
            Optional<ServerConnection> connection = player.getCurrentServer();
            if (connection.isPresent()) {
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
                    Bukkit.getLogger().warning(e.toString());
                }
               if (!connection.get().sendPluginMessage(IDENTIFIER, msgbytes.toByteArray())){
                   logger.warn("[NotTokensPremium] Failed to send a proxy connection request");
               }
            }
        } else if (channel.equalsIgnoreCase("Forward")) {
            in.readUTF(); // ALL
            String subChannel = in.readUTF();
            switch (subChannel) {
                case "ServerTokenMessage": {
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
                                editTokens(UUID.fromString(uuid), tokenChange);
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
                            editTokens(UUID.fromString(uuid), tokenChange);
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
    }

    private void save() throws IOException {
        File tokensHolder = new File(dataDirectory + File.separator + "tokensholder.yml");
        Gson gson = new Gson();
        Type typeObject = new TypeToken<Map<UUID, Double>>() {}.getType();
        gson.toJson(networkTokens, typeObject, new FileWriter(tokensHolder));
    }

    private void read() throws FileNotFoundException {
        File tokensHolder = new File(dataDirectory + File.separator + "tokensholder.yml");
        Gson gson = new Gson();
        Type typeObject = new TypeToken<Map<UUID, Double>>() {}.getType();
        networkTokens = gson.fromJson(new JsonReader(new FileReader(tokensHolder)), typeObject);
    }

    private void editTokens(UUID uuid, double change) {
        if (networkTokens.containsKey(uuid))
            networkTokens.put(uuid, networkTokens.get(uuid) + change);
        networkTokens.put(uuid, change);
    }
}
