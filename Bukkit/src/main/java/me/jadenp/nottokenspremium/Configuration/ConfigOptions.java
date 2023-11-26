package me.jadenp.nottokenspremium.Configuration;

import me.jadenp.nottokenspremium.NotTokensPremium;
import me.jadenp.nottokenspremium.OldNotTokensAdapter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ConfigOptions {
    public static int tokenMessageInterval;
    public static boolean negativeTokens;
    public static List<String> leaderboardExclusion = new ArrayList<>();

    public static void loadConfigOptions(){
        NotTokensPremium.getInstance().saveDefaultConfig();
        FileConfiguration config = NotTokensPremium.getInstance().getConfig();

        // adapt old config
        if (!config.getBoolean("has-adapted") && OldNotTokensAdapter.getOldDirectory().exists()) {
            if (OldNotTokensAdapter.adaptTokens()) {
                Bukkit.getLogger().info("[NotTokensPremium] Adapted old tokens and configuration.");
                NotTokensPremium.getInstance().reloadConfig();
                config = NotTokensPremium.getInstance().getConfig();
            }
        }

        // fill in any missing default settings
        for (String key : Objects.requireNonNull(config.getDefaults()).getKeys(true)) {
            if (!config.isSet(key))
                config.set(key, config.getDefaults().get(key));
        }

        tokenMessageInterval = config.getInt("condense-spam");
        negativeTokens = config.getBoolean("negative-tokens");
        leaderboardExclusion = config.getStringList("leaderboard-exclusion");
    }
}
