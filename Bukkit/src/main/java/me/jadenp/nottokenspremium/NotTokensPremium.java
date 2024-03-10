package me.jadenp.nottokenspremium;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAddon;
import me.jadenp.nottokenspremium.settings.ConfigOptions;
import me.jadenp.nottokenspremium.settings.KillRewards.KillRewards;
import me.jadenp.nottokenspremium.settings.Language;
import me.jadenp.nottokenspremium.migration.MigrationManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Objects;

/**
 * mvn install:install-file -Dfile=C:\Users\jpate\IdeaProjects\mvnrepo\bt-api-3.13.3.jar -DgroupId=me.mraxetv.beasttokens.api -DartifactId=BeastTokensAPI -Dversion=3.13.3 -Dpackaging=jar -DgeneratePom=true
 * SQL player list -
 * migrate tokens -
 */
public class NotTokensPremium extends JavaPlugin {


    private static NotTokensPremium instance;
    public boolean firstStart = false;
    public static boolean latestVersion = true;
    public static int resourceID = 115480;
    public static int serverVersion = 20;
    public static NotTokensPremium getInstance() {
        return instance;
    }

    @Override
    public void onEnable(){
        instance = this;
        if (Bukkit.getPluginManager().isPluginEnabled("Skript")) {
            SkriptAddon addon = Skript.registerAddon(this);
            try {
                addon.loadClasses("me.jadenp.nottokenspremium", "Skripts");
                Bukkit.getLogger().info("[NotTokensPremium] Connected to Skript");
            } catch (IOException e) {
                Bukkit.getLogger().warning(e.toString());
            }
        }

        try {
            // get the text version - ex: 1.20.3
            String fullServerVersion = Bukkit.getBukkitVersion().substring(0, Bukkit.getBukkitVersion().indexOf("-"));
            fullServerVersion = fullServerVersion.substring(2); // remove the '1.' in the version
            if (fullServerVersion.contains("."))
                fullServerVersion = fullServerVersion.substring(0,fullServerVersion.indexOf(".")); // remove the last digits if any - ex .3
            serverVersion = Integer.parseInt(fullServerVersion);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            Bukkit.getLogger().warning("[NotTokensPremium] Could not get the server version. Some features may not function properly.");
        }

        // load configurations
        TokenManager.loadTokenManager();
        ConfigOptions.loadConfigOptions();
        TokenManager.startAutoConnectTask();
        Language.loadLanguageOptions();
        LoggedPlayers.loadLoggedPlayers();
        TransactionLogs.loadTransactionLogs();
        MigrationManager.loadConfig();


        Objects.requireNonNull(Bukkit.getPluginCommand("nottokens")).setExecutor(new Commands());
        Bukkit.getPluginManager().registerEvents(new LoggedPlayers(), this);
        Bukkit.getPluginManager().registerEvents(new KillRewards(), this);

        // register plugin messaging to a proxy
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "bungeecord:main");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "bungeecord:main", new ProxyMessaging());
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "nottokens:main");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "nottokens:main", new ProxyMessaging());

        // register PlaceholderAPI Expansion
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
            new TokenExpansion().register();

        // update checker
        if (ConfigOptions.updateNotification && resourceID != 0) {
            new UpdateChecker(this, resourceID).getVersion(version -> {
                if (getDescription().getVersion().contains("dev"))
                    return;
                if (getDescription().getVersion().equals(version))
                    return;
                // split the numbers
                String[] versionSplit = version.split("\\.");
                String[] currentSplit = getDescription().getVersion().split("\\.");
                // convert to integers
                int[] versionNumbers = new int[versionSplit.length];
                for (int i = 0; i < versionSplit.length; i++) {
                    try {
                        versionNumbers[i] = Integer.parseInt(versionSplit[i]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                int[] currentNumbers = new int[currentSplit.length];
                for (int i = 0; i < currentNumbers.length; i++) {
                    try {
                        currentNumbers[i] = Integer.parseInt(currentSplit[i]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                boolean needsUpdate = false;
                for (int i = 0; i < currentNumbers.length; i++) {
                    if (currentNumbers[i] < versionNumbers[i]) {
                        needsUpdate = true;
                        break;
                    }
                }
                if (!needsUpdate && currentNumbers.length < versionNumbers.length)
                    needsUpdate = true;
                latestVersion = !needsUpdate;

                if (needsUpdate) {
                    Bukkit.getLogger().info("[NotTokensPremium] A new update is available. Current version: " + getDescription().getVersion() + " Latest version: " + version);
                    Bukkit.getLogger().info("[NotTokensPremium] Download a new version here:" + " https://www.spigotmc.org/resources//");
                }
            });
        }

        firstStart = true;

        if (ConfigOptions.sendBStats) {
            int pluginId = 21262;
            new Metrics(this, pluginId);
        }

    }

    @Override
    public void onDisable(){
        try {
            TokenManager.saveTokens();
            LoggedPlayers.save();
            TransactionLogs.saveTransactions();
            MigrationManager.save();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }




}
