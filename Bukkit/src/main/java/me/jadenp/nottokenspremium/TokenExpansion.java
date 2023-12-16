package me.jadenp.nottokenspremium;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.jadenp.nottokenspremium.Configuration.NumberFormatting;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class TokenExpansion extends PlaceholderExpansion {

    public TokenExpansion(){}

    @Override
    public boolean persist(){
        return true;
    }

    @Override
    public boolean canRegister(){
        return true;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "nottokens";
    }

    @Override
    public @NotNull String getAuthor() {
        return NotTokensPremium.getInstance().getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return NotTokensPremium.getInstance().getDescription().getVersion();
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String identifier){
        if(player == null){
            return "";
        }

        if(identifier.equals("amount_formatted")){
            return NumberFormatting.formatNumber(TokenManager.getTokens(player.getUniqueId()));
        }
        if(identifier.equals("amount")){
            return TokenManager.getTokens(player.getUniqueId()) + "";
        }

        return null;
    }
}
