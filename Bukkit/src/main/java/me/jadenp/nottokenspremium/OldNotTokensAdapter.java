package me.jadenp.nottokenspremium;

import me.jadenp.nottokenspremium.Configuration.Language;
import me.jadenp.nottokenspremium.Configuration.KillRewards.KillRewards;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

public class OldNotTokensAdapter {
    /**
     * Adapts the old config and tokens from the old plugin into this one
     * @return True if all files were present to adapt. False if some files are missing.
     */
    public static boolean adaptTokens() {


        File oldDirectory = getOldDirectory();
        if (!oldDirectory.exists())
            return false;
        File oldConfig = new File(oldDirectory + File.separator + "config.yml");
        if (!oldConfig.exists())
            return false;
        File oldTokensHolder = new File(oldDirectory + File.separator + "tokensHolder.yml");
        if (!oldTokensHolder.exists())
            return false;

        YamlConfiguration oldConfiguration = YamlConfiguration.loadConfiguration(oldConfig);
        YamlConfiguration languageConfig = YamlConfiguration.loadConfiguration(Language.getLanguageFile());
        YamlConfiguration killRewardsConfig = YamlConfiguration.loadConfiguration(KillRewards.getKillRewardsFile());
        YamlConfiguration oldTokensHolderConfig = YamlConfiguration.loadConfiguration(oldTokensHolder);
        FileConfiguration newConfig = NotTokensPremium.getInstance().getConfig();

        newConfig.set("has-adapted", true);
        // read language
        if (oldConfiguration.isSet("prefix"))
            languageConfig.set("prefix", oldConfiguration.get("prefix"));
        if (oldConfiguration.isSet("balance"))
            languageConfig.set("balance", oldConfiguration.get("balance"));
        if (oldConfiguration.isSet("admin-add"))
            languageConfig.set("admin-add", oldConfiguration.get("admin-add"));
        if (oldConfiguration.isSet("player-receive"))
            languageConfig.set("player-receive", oldConfiguration.get("player-receive"));
        if (oldConfiguration.isSet("admin-remove"))
            languageConfig.set("admin-remove", oldConfiguration.get("admin-remove"));
        if (oldConfiguration.isSet("player-take"))
            languageConfig.set("player-take", oldConfiguration.get("player-take"));
        if (oldConfiguration.isSet("admin-set"))
            languageConfig.set("admin-set", oldConfiguration.get("admin-set"));
        if (oldConfiguration.isSet("player-set"))
            languageConfig.set("player-set", oldConfiguration.get("player-set"));
        if (oldConfiguration.isSet("unknown-command"))
            languageConfig.set("unknown-command", oldConfiguration.get("unknown-command"));
        if (oldConfiguration.isSet("unknown-player"))
            languageConfig.set("unknown-player", oldConfiguration.get("unknown-player"));
        if (oldConfiguration.isSet("reduced-message"))
            languageConfig.set("reduced-message", oldConfiguration.get("reduced-message"));
        if (oldConfiguration.isSet("other-tokens"))
            languageConfig.set("other-tokens", oldConfiguration.get("other-tokens"));
        if (oldConfiguration.isSet("admin-give-all"))
            languageConfig.set("admin-give-all", oldConfiguration.get("admin-give-all"));
        if (oldConfiguration.isSet("insufficient-tokens"))
            languageConfig.set("insufficient-tokens", oldConfiguration.get("insufficient-tokens"));

        // save language file
        try {
            languageConfig.save(Language.getLanguageFile());
        } catch (IOException e) {
            Bukkit.getLogger().warning("Error updating language file!");
            Bukkit.getLogger().warning(e.toString());
        }

        // read kill-rewards
        if (oldConfiguration.isSet("kill-rewards.enabled"))
            killRewardsConfig.set("enabled", oldConfiguration.getBoolean("kill-rewards.enabled"));
        if (oldConfiguration.isSet("kill-rewards.all-mobs.enabled"))
            killRewardsConfig.set("all-mobs.enabled", oldConfiguration.getBoolean("kill-rewards.all-mobs.enabled"));
        if (oldConfiguration.isSet("kill-rewards.all-mobs.amount"))
            killRewardsConfig.set("all-mobs.amount", oldConfiguration.getDouble("kill-rewards.all-mobs.amount"));
        if (oldConfiguration.isSet("kill-rewards.all-mobs.rate"))
            killRewardsConfig.set("all-mobs.rate", oldConfiguration.getDouble("kill-rewards.all-mobs.rate"));
        int i = 1;
        while (oldConfiguration.isSet("kill-rewards." + i)) {
            killRewardsConfig.set(oldConfiguration.getString("kill-rewards." + i + ".name") + ".amount", oldConfiguration.getDouble("kill-rewards." + i + ".amount"));
            killRewardsConfig.set(oldConfiguration.getString("kill-rewards." + i + ".name") + ".rate", oldConfiguration.getDouble("kill-rewards." + i + ".rate"));
            i++;
        }

        // save kill-rewards file
        try {
            killRewardsConfig.save(KillRewards.getKillRewardsFile());
        } catch (IOException e) {
            Bukkit.getLogger().warning("Error updating kill-rewards file!");
            Bukkit.getLogger().warning(e.toString());
        }

        // read config
        // replace values in new config with ones from the old config
        for (String key : oldConfiguration.getKeys(true)) {
            if (newConfig.isSet(key))
                newConfig.set(key, oldConfiguration.get(key));
        }

        // save config
        NotTokensPremium.getInstance().saveConfig();

        // read tokens
        for (String token : oldTokensHolderConfig.getKeys(false)) {
            if (!token.equals("logged-names"))
                try {
                    if (!TokenManager.giveTokens(UUID.fromString(token), oldTokensHolderConfig.getLong(token)))
                        Bukkit.getLogger().warning("[NotTokens] Could not give " + LoggedPlayers.getPlayerName(UUID.fromString(token)) + " " + oldTokensHolderConfig.getLong(token) + " tokens!");
                } catch (IllegalArgumentException e) {
                    Bukkit.getLogger().warning("[NotTokens] Could not get a uuid from: " + token);
                }
        }
        return true;
    }

    public static File getOldDirectory(){
        String path = NotTokensPremium.getInstance().getDataFolder().getPath();
        Bukkit.getLogger().info(path);
        // delete the Premium from NotTokensPremium
        path = path.substring(path.length() - 7);
        return new File(path);
    }
}
