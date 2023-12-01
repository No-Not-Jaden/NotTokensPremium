package me.jadenp.nottokenspremium;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAddon;
import me.jadenp.nottokenspremium.Configuration.ConfigOptions;
import me.jadenp.nottokenspremium.Configuration.Language;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

/**
 * tokens convert to item or command with withdraw/deposit
 */
public class NotTokensPremium extends JavaPlugin {
    private static NotTokensPremium instance;
    public boolean firstStart = false;

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
                Bukkit.getLogger().info("Connected to Skript");
            } catch (IOException e) {
                Bukkit.getLogger().warning(e.toString());
            }
        }

        // load configurations
        ConfigOptions.loadConfigOptions();
        Language.loadLanguageOptions();
        LoggedPlayers.loadLoggedPlayers();
        TransactionLogs.loadTransactionLogs();
        TokenManager.loadTokenManager();

        Bukkit.getPluginCommand("nottokens").setExecutor(new Commands());

        // register plugin messaging to a proxy
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", new ProxyMessaging());

        // register PlaceholderAPI Expansion
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
            new TokenExpansion().register();

        // update checker
        if (updateNotification) {
            new UpdateChecker(this, 104484).getVersion(version -> {
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
                    Bukkit.getLogger().info("[NotBounties] A new update is available. Current version: " + getDescription().getVersion() + " Latest version: " + version);
                    Bukkit.getLogger().info("[NotBounties] Download a new version here:" + " https://www.spigotmc.org/resources/104484/");
                }
            });
        }

        firstStart = true;

    }

    @Override
    public void onDisable(){

    }
}
