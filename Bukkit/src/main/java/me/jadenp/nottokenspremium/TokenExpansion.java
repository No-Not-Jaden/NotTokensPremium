package me.jadenp.nottokenspremium;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.jadenp.nottokenspremium.configuration.Language;
import me.jadenp.nottokenspremium.configuration.NumberFormatting;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;

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
        if (identifier.equals("prefix")) {
            return Language.parse(NumberFormatting.currencyPrefix, player);
        }
        if (identifier.equals("suffix")) {
            return Language.parse(NumberFormatting.currencySuffix, player);
        }
        if(identifier.equals("amount")){
            return TokenManager.getTokens(player.getUniqueId()) + "";
        }
        if (identifier.startsWith("top")) {
            int amount;
            try {
                amount = Integer.parseInt(identifier.substring(identifier.indexOf("_") + 1));
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                return "";
            }
            LinkedHashMap<UUID, Double> top = TokenManager.getTopTokens(amount);
            if (top.size() < amount)
                return "";
            Iterator<Map.Entry<UUID, Double>> iterator = top.entrySet().iterator();
            Map.Entry<UUID, Double> entry = null;
            while (iterator.hasNext())
                entry = iterator.next();
            if (entry == null)
                return "";

            return Language.parse(Language.leaderboardRank.replaceAll("\\{rank}", Matcher.quoteReplacement(amount + "")), LoggedPlayers.getPlayerName(entry.getKey()), entry.getValue(), Bukkit.getOfflinePlayer(entry.getKey()));
        }

        return null;
    }
}
