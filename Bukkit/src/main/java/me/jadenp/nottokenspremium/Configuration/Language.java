package me.jadenp.nottokenspremium.Configuration;

import me.clip.placeholderapi.PlaceholderAPI;
import me.jadenp.nottokenspremium.NotTokensPremium;
import me.jadenp.nottokenspremium.NumberFormatting;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.md_5.bungee.api.ChatColor.COLOR_CHAR;

public class Language {
    private static boolean papiEnabled;
    private static File languageFile;

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




    public static void loadLanguageOptions(){
        papiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        languageFile = new File(NotTokensPremium.getInstance().getDataFolder() + File.separator + "language.yml");
        NotTokensPremium.getInstance().saveResource("language.yml", false);

        // fill in any default options that aren't present
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(languageFile);
        for (String key : Objects.requireNonNull(configuration.getDefaults()).getKeys(true)) {
            if (!configuration.isSet(key))
                configuration.set(key, configuration.getDefaults().get(key));
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

    }

    public static String parse(String text) {
        if (papiEnabled)
            text = PlaceholderAPI.setPlaceholders(null, text);
        return color(text);
    }

    public static String parse(String text, OfflinePlayer player) {
        if (papiEnabled)
            text = PlaceholderAPI.setPlaceholders(player, text);
        if (player.getName() != null) {
            text = text.replaceAll("\\{player}", Matcher.quoteReplacement(player.getName()));
            text = text.replaceAll("\\{sender}", Matcher.quoteReplacement(player.getName()));
        }
        return parse(text);
    }

    public static String parse(String text, double amount, OfflinePlayer player) {
        text = text.replaceAll("\\{amount}", Matcher.quoteReplacement(NumberFormatting.formatNumber(amount)));
        text = text.replaceAll("\\{tokens}", Matcher.quoteReplacement(NumberFormatting.formatNumber(amount)));
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
        return languageFile;
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
