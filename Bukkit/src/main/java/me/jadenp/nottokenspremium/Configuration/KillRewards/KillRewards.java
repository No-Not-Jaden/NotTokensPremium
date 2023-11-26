package me.jadenp.nottokenspremium.Configuration.KillRewards;

import me.jadenp.nottokenspremium.NotTokensPremium;
import me.jadenp.nottokenspremium.TokenManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class KillRewards implements Listener {
    private static File killRewardsFile;
    private static final Map<EntityType, KillReward> rewardMap = new HashMap<>();
    private static boolean enabled;
    private static KillReward allMobsReward;
    private static boolean allMobsEnabled;

    public KillRewards() {}

    /**
     * Load kill rewards from the kill-rewards.yml file
     */
    public static void loadKillRewards() {
        killRewardsFile = new File(NotTokensPremium.getInstance().getDataFolder() + File.separator + "kill-rewards.yml");
        NotTokensPremium.getInstance().saveResource("kill-rewards", false);
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(killRewardsFile);

        // make sure some default information is present
        boolean changes = false;
        if (!configuration.isSet("enabled")) {
            configuration.set("enabled", false);
            changes = true;
        }
        if (!configuration.isSet("all-mobs")) {
            configuration.set("all-mobs.enabled", false);
            configuration.set("all-mobs.amount", 1.0);
            configuration.set("all-mobs.rate", 0.1);
            changes = true;
        }
        // save if there were changes
        if (changes)
            try {
                configuration.save(killRewardsFile);
            } catch (IOException e) {
                Bukkit.getLogger().warning("Error saving kill-rewards.yml");
                Bukkit.getLogger().warning(e.toString());
            }

        enabled = configuration.getBoolean("enabled");
        allMobsEnabled = configuration.getBoolean("all-mobs.enabled");
        if (allMobsEnabled)
            allMobsReward = new KillReward(configuration.getDouble("all-mobs.amount"), configuration.getDouble("all-mobs.rate"));
        rewardMap.clear();
        for (String key : configuration.getKeys(false)) {
            if (key.equals("enabled") || key.equals("all-mobs"))
                continue;
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(key.toUpperCase());
            } catch (IllegalArgumentException e) {
                Bukkit.getLogger().warning("[NotTokens] Unknown entity type: " + key);
                continue;
            }
            double amount = configuration.isSet(key + ".amount") ? configuration.getDouble(key + ".amount") : 1;
            double rate = configuration.isSet(key + ".rate") ? configuration.getDouble(key + ".rate") : 0.1;
            rewardMap.put(entityType, new KillReward(amount, rate));
        }
    }

    public static File getKillRewardsFile() {
        return killRewardsFile;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!enabled)
            return;
        if (event.getEntity().getKiller() == null)
            return;
        KillReward reward = null;
        // look for any kill rewards for this mob
        if (allMobsEnabled)
            reward = allMobsReward;
        if (rewardMap.containsKey(event.getEntity().getType()))
            reward = rewardMap.get(event.getEntity().getType());
        // return if there is no reward
        if (reward == null)
            return;
        // give reward
        if (reward.giveReward())
            TokenManager.giveTokens(event.getEntity().getKiller().getUniqueId(), reward.getAmount());
    }
}
