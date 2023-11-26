package me.jadenp.nottokenspremium;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

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
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", new ProxyMessaging());
    }

    @Override
    public void onDisable(){

    }
}
