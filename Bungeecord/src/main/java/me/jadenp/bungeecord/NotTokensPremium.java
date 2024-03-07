package me.jadenp.bungeecord;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class NotTokensPremium extends Plugin {
    public static Map<UUID, Double> networkTokens;
    public static final Map<ServerInfo, Map<UUID, String>> playersToLog = new HashMap<>();
    private static NotTokensPremium instance;


    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        getProxy().registerChannel("nottokens:main");
        getProxy().getPluginManager().registerListener(this, new ProxyMessaging());
        File tokensHolder = new File(getDataFolder() + File.separator + "tokensholder.json");

        File directory = getDataFolder();
        try {
            if (directory.mkdir()) {
                getLogger().info("[NotTokensPremium] Created a new NotTokensPremium directory file.");
            }
            if (tokensHolder.createNewFile()) {
                getLogger().info("[NotTokensPremium] Created a new tokens holder file.");
            }
            read();
        } catch (IOException e) {
            getLogger().info(e.toString());
            throw new RuntimeException();
        }

        getProxy().getScheduler().schedule(this, () -> {
            try {
                save();
            } catch (IOException e) {
                getLogger().warning("[NotTokensPremium] Could not auto save tokens to file!");
                getLogger().warning(e.toString());
            }
        }, 10,10, TimeUnit.MINUTES);
    }

    public static NotTokensPremium getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        try {
            save();
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }


    public static void logPlayer(ServerInfo server, UUID uuid, String name) {
        Map<UUID, String> loggedPlayers = playersToLog.containsKey(server) ? playersToLog.get(server) : new HashMap<>();
        loggedPlayers.put(uuid, name);
        playersToLog.put(server, loggedPlayers);
    }

    private void save() throws IOException {
        File tokensHolder = new File(getDataFolder() + File.separator + "tokensholder.json");
        Gson gson = new Gson();
        Type typeObject = new TypeToken<Map<UUID, Double>>() {}.getType();
        FileWriter writer = new FileWriter(tokensHolder);
        gson.toJson(networkTokens, typeObject, writer);
        writer.close();
    }

    private void read() throws IOException {
        File tokensHolder = new File(getDataFolder() + File.separator + "tokensholder.json");
        Gson gson = new Gson();
        Type typeObject = new TypeToken<Map<UUID, Double>>() {}.getType();
        JsonReader reader = new JsonReader(new FileReader(tokensHolder));
        networkTokens = gson.fromJson(reader, typeObject);
        reader.close();
        if (networkTokens == null)
            networkTokens = new HashMap<>();
    }

    public static void editTokens(UUID uuid, double change) {
        if (networkTokens.containsKey(uuid))
            networkTokens.replace(uuid, networkTokens.get(uuid) + change);
        else
            networkTokens.put(uuid, change);
        //logger.info("tokens: " + networkTokens.get(uuid));
    }
}
