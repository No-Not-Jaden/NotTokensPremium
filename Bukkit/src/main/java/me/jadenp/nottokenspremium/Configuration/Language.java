package me.jadenp.nottokenspremium.Configuration;

import me.clip.placeholderapi.PlaceholderAPI;
import me.jadenp.nottokenspremium.NotTokensPremium;
import me.jadenp.nottokenspremium.TokenManager;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.md_5.bungee.api.ChatColor.COLOR_CHAR;

public class Language {
    private static boolean papiEnabled;

    public static String prefix;
    public static String balance;
    public static String adminAdd;
    public static String playerReceive;
    public static String adminRemove;
    public static String playerTake;
    public static String adminSet;
    public static String playerSet;
    public static String unknownCommand;
    public static String unknownPlayer;
    public static String reducedMessage;
    public static String otherTokens;
    public static String adminGiveAll;
    public static String insufficientTokens;
    public static String unknownAmount;
    public static String leaderboard;
    public static String leaderboardRank;
    public static String adminRemoveAll;
    public static String insufficientExchange;
    public static String deposit;
    public static String withdraw;
    public static String transferOwn;
    public static String killReward;

    public static void loadLanguageOptions(){
        papiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        File languageFile = getLanguageFile();
        if (!languageFile.exists())
            NotTokensPremium.getInstance().saveResource("language.yml", false);

        // fill in any default options that aren't present
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(languageFile);
        configuration.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(NotTokensPremium.getInstance().getResource("language.yml")))));
        for (String key : Objects.requireNonNull(configuration.getDefaults()).getKeys(true)) {
            if (!configuration.isSet(key))
                configuration.set(key, configuration.getDefaults().get(key));
        }
        try {
            configuration.save(languageFile);
        } catch (IOException e) {
            Bukkit.getLogger().warning(e.toString());
        }


        prefix = configuration.getString("prefix");
        balance = configuration.getString("balance");
        adminAdd = configuration.getString("admin-add");
        playerReceive = configuration.getString("player-receive");
        adminRemove = configuration.getString("admin-remove");
        playerTake = configuration.getString("player-take");
        adminSet = configuration.getString("admin-set");
        playerSet = configuration.getString("player-set");
        unknownCommand = configuration.getString("unknown-command");
        unknownPlayer = configuration.getString("unknown-player");
        reducedMessage = configuration.getString("reduced-message");
        otherTokens = configuration.getString("other-tokens");
        adminGiveAll = configuration.getString("admin-give-all");
        insufficientTokens = configuration.getString("insufficient-tokens");
        unknownAmount = configuration.getString("unknown-amount");
        leaderboard = configuration.getString("leaderboard");
        leaderboardRank = configuration.getString("leaderboard-rank");
        adminRemoveAll = configuration.getString("admin-remove-all");
        insufficientExchange = configuration.getString("insufficient-exchange");
        deposit = configuration.getString("deposit");
        withdraw = configuration.getString("withdraw");
        transferOwn = configuration.getString("transfer-own");
        killReward = configuration.getString("kill-reward");
    }

    public static String parse(String text) {
        if (papiEnabled)
            text = PlaceholderAPI.setPlaceholders(null, text);
        return color(text);
    }

    public static String parse(String text, OfflinePlayer player) {
        if (player != null && player.getName() != null) {
            text = text.replaceAll("\\{player}", Matcher.quoteReplacement(player.getName()));
            text = text.replaceAll("\\{sender}", Matcher.quoteReplacement(player.getName()));
        }
        if (papiEnabled)
            text = PlaceholderAPI.setPlaceholders(player, text);
        return parse(text);
    }

    public static String parse(String text, String replacement, OfflinePlayer player) {
        text = text.replaceAll("\\{player}", Matcher.quoteReplacement(replacement));
        text = text.replaceAll("\\{sender}", Matcher.quoteReplacement(replacement));
        text = text.replaceAll("\\{amount}", Matcher.quoteReplacement(NumberFormatting.currencyPrefix + NumberFormatting.formatNumber(NumberFormatting.parseCurrency(replacement)) + NumberFormatting.currencySuffix));
        return parse(text, player);
    }

    public static String parse(String text, String replacement, double amount, OfflinePlayer player) {
        text = text.replaceAll("\\{amount}", Matcher.quoteReplacement(NumberFormatting.currencyPrefix + NumberFormatting.formatNumber(amount) + NumberFormatting.currencySuffix));
        text = text.replaceAll("\\{tokens}", Matcher.quoteReplacement(NumberFormatting.currencyPrefix + NumberFormatting.formatNumber(amount) + NumberFormatting.currencySuffix));
        return parse(text, replacement, player);
    }

    public static String parse(String text, double amount, OfflinePlayer player) {
        text = text.replaceAll("\\{amount}", Matcher.quoteReplacement(NumberFormatting.currencyPrefix + NumberFormatting.formatNumber(amount) + NumberFormatting.currencySuffix));
        text = text.replaceAll("\\{tokens}", Matcher.quoteReplacement(NumberFormatting.currencyPrefix + NumberFormatting.formatNumber(amount) + NumberFormatting.currencySuffix));
        double balance = ItemExchange.autoExchange && player.isOnline() ? TokenManager.getTokens(player.getUniqueId()) + ItemExchange.getBalance(player.getPlayer()) * ItemExchange.getValue() : TokenManager.getTokens(player.getUniqueId());
        text = text.replaceAll("\\{balance}", Matcher.quoteReplacement(NumberFormatting.currencyPrefix + NumberFormatting.formatNumber(balance) + NumberFormatting.currencySuffix));
        return parse(text, player);
    }

    public static String parse(String text, double amount, long time, OfflinePlayer player) {
        text = text.replaceAll("\\{time}", Matcher.quoteReplacement(formatTime(time)));
        return parse(text, amount, player);
    }

    /**
     * Get a formatted time value from milliseconds in the form (hour)h (minute)m (second)s
     * @param ms Time in milliseconds
     * @return The formatted time
     */
    public static String formatTime(long ms) {
        long hours = ms / 3600000L;
        ms = ms % 3600000L;
        long minutes = ms / 60000L;
        ms = ms % 60000L;
        long seconds = ms / 1000L;
        String time = "";
        if (hours > 0) time += hours + "h ";
        if (minutes > 0) time += minutes + "m ";
        if (seconds > 0) time += seconds + "s";
        return time;
    }

    public static File getLanguageFile() {
        return new File(NotTokensPremium.getInstance().getDataFolder() + File.separator + "language.yml");
    }

    private static String color(String str){
        str = ChatColor.translateAlternateColorCodes('&', str);
        return translateHexColorCodes("&#","", str);
    }
    private static String translateHexColorCodes(String startTag, String endTag, String message)
    {
        final Pattern hexPattern = Pattern.compile(startTag + "([A-Fa-f0-9]{6})" + endTag);
        Matcher matcher = hexPattern.matcher(message);
        StringBuilder buffer = new StringBuilder(message.length() + 4 * 8);
        while (matcher.find())
        {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, COLOR_CHAR + "x"
                    + COLOR_CHAR + group.charAt(0) + COLOR_CHAR + group.charAt(1)
                    + COLOR_CHAR + group.charAt(2) + COLOR_CHAR + group.charAt(3)
                    + COLOR_CHAR + group.charAt(4) + COLOR_CHAR + group.charAt(5)
            );
        }
        return matcher.appendTail(buffer).toString();
    }

}
