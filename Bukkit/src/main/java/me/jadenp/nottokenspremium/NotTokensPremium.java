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

        // register plugin messaging to a proxy
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", new ProxyMessaging());

        // register PlaceholderAPI Expansion
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
            new TokenExpansion().register();
    }

    @Override
    public void onDisable(){

    }
}
