package me.jadenp.nottokenspremium;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;

public class KillRewards implements Listener {
    private static File killRewardsFile;
    private Map<>

    public KillRewards() {}

    public static void loadKillRewards() {
        killRewardsFile = new File(NotTokensPremium.getInstance().getDataFolder() + File.separator + "kill-rewards.yml");
        NotTokensPremium.getInstance().saveResource("kill-rewards", false);
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(killRewardsFile);

    }

    public static File getKillRewardsFile() {
        return killRewardsFile;
    }
}
