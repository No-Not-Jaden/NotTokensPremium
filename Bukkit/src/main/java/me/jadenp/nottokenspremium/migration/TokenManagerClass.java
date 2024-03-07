package me.jadenp.nottokenspremium.migration;

import me.realized.tokenmanager.api.TokenManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

public class TokenManagerClass implements MigratablePlugin{
    private static TokenManager api;
    public TokenManagerClass(){
        registerAPI();
    }

    /**
     * Get the external token values of an online player
     * @return TokenManager tokens
     */
    @Override
    public double getTokens(Player player) {
        OptionalLong tokens = api.getTokens(player);
        if (tokens.isPresent())
            return tokens.getAsLong();
        return 0;
    }

    @Nullable
    @Override
    public Map<UUID, Double> getAllTokens() {
        return null;
    }

    @Override
    public boolean isActiveMigration() {
        return true;
    }

    @Override
    public void setTokens(OfflinePlayer player, double amount) {
        if (player.isOnline())
            api.setTokens(player.getPlayer(), (long) amount);
    }

    @Override
    public String getName() {
        return "TokenManager";
    }

    // get the plugin api if it hasn't been retrieved yet
    private void registerAPI(){
        try {
            if (api == null) {
                api = (TokenManager) Bukkit.getServer().getPluginManager().getPlugin("TokenManager");
            }
        } catch (ClassCastException e) {
            Bukkit.getLogger().warning("[NotTokensPremium] Could not connect with TokenManager! Is another external token plugin enabled?");
        }
    }

}
