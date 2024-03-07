package me.jadenp.nottokenspremium.settings;

import me.jadenp.nottokenspremium.NotTokensPremium;
import me.jadenp.nottokenspremium.OldNotTokensAdapter;
import me.jadenp.nottokenspremium.TokenManager;
import me.jadenp.nottokenspremium.settings.KillRewards.KillRewards;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ConfigOptions {
    public static int tokenMessageInterval;
    public static boolean negativeTokens;
    public static List<String> leaderboardExclusion = new ArrayList<>();
    public static boolean autoConnect;
    public static boolean updateNotification;
    public static boolean sendBStats;

    public static void loadConfigOptions(){
        NotTokensPremium.getInstance().saveDefaultConfig();
        NotTokensPremium.getInstance().reloadConfig();
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
           // Bukkit.getLogger().info("[key] " + key);
            if (!config.isSet(key)) {
                //Bukkit.getLogger().info("Not set -> " + config.getDefaults().get(key));
                config.set(key, config.getDefaults().get(key));
            }
        }

        NotTokensPremium.getInstance().saveConfig();

        tokenMessageInterval = config.getInt("condense-spam");
        negativeTokens = config.getBoolean("negative-tokens");
        leaderboardExclusion = config.getStringList("leaderboard-exclusion");
        autoConnect = config.getBoolean("database.auto-connect");
        updateNotification = config.getBoolean("update-notification");
        sendBStats = config.getBoolean("send-bstats");

        // update condensed spam option
        if (tokenMessageInterval > 0 && !TokenManager.isTokenMessagingActing())
            TokenManager.beginTokenMessaging();
        else if (tokenMessageInterval <= 0 && TokenManager.isTokenMessagingActing())
            TokenManager.cancelTokenMessaging();

        // load other config sections
        ItemExchange.loadExchange();
        NumberFormatting.setCurrencyOptions(Objects.requireNonNull(config.getConfigurationSection("number-formatting")));
        KillRewards.loadKillRewards();
    }
}
