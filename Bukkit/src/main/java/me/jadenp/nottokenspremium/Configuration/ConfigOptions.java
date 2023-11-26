package me.jadenp.nottokenspremium;

import org.bukkit.configuration.file.FileConfiguration;

public class ConfigOptions {
    public static int tokenMessageInterval;

    public static void loadConfigOptions(){
        NotTokensPremium.getInstance().saveDefaultConfig();
        FileConfiguration config = NotTokensPremium.getInstance().getConfig();
    }
}
