package me.jadenp.nottokenspremium.migration;

import me.realized.tokenmanager.api.TokenManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.OptionalLong;

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

    @Override
    public void setTokens(Player player, double amount) {
        api.setTokens(player, (long) amount);
    }

    @Override
    public String getName() {
        return "TokenManager";
    }

    // get the plugin api if it hasn't been retrieved yet
    private void registerAPI(){
        if (api == null){
            api = (TokenManager) Bukkit.getServer().getPluginManager().getPlugin("TokenManager");
        }
    }

}
