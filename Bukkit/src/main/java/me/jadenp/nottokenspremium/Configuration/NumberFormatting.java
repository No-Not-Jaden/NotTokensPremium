package me.jadenp.nottokenspremium.Configuration;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.*;

public class NumberFormatting {
    public static String currencyPrefix = "";
    public static String currencySuffix = "";
    public static boolean useDivisions;
    public static DecimalFormat decimalFormat;
    public static Locale locale;
    public static LinkedHashMap<Long, String> nfDivisions = new LinkedHashMap<>();


    public static void setCurrencyOptions(ConfigurationSection numberFormatting) {


        if (numberFormatting.isSet("prefix"))
            currencyPrefix = Language.parse(Objects.requireNonNull(numberFormatting.getString("prefix")));
        if (numberFormatting.isSet("suffix"))
            currencySuffix = Language.parse(Objects.requireNonNull(numberFormatting.getString("suffix")));


        useDivisions = numberFormatting.getBoolean("use-divisions");

        String localeString = numberFormatting.getString("format-locale");
        String pattern = numberFormatting.getString("pattern");

        assert localeString != null;
        String[] localeSplit = localeString.split("-");
        locale = new Locale(localeSplit[0], localeSplit[1]);

        assert pattern != null;
        decimalFormat = new DecimalFormat(pattern, new DecimalFormatSymbols(locale));

        NumberFormatting.nfDivisions.clear();
        Map<Long, String> preDivisions = new HashMap<>();
        for (String s : Objects.requireNonNull(numberFormatting.getConfigurationSection("divisions")).getKeys(false)) {
            if (s.equals("decimals"))
                continue;
            try {
                preDivisions.put(Long.parseLong(s), numberFormatting.getString("divisions." + s));
            } catch (NumberFormatException e) {
                Bukkit.getLogger().warning("Division is not a number: " + s);
            }
        }
        NumberFormatting.nfDivisions = NumberFormatting.sortByValue(preDivisions);
    }

    public static String formatNumber(String number) {
        if (number.isEmpty())
            return "";
        if (number.startsWith(currencyPrefix) && !currencyPrefix.isEmpty())
            return currencyPrefix + formatNumber(number.substring(currencyPrefix.length()));
        if (number.endsWith(currencySuffix) && !currencySuffix.isEmpty())
            return formatNumber(number.substring(0, number.length() - currencySuffix.length())) + currencySuffix;
        if (isNumber(number))
            return formatNumber(tryParse(number));
        if (!isNumber(number.substring(0, 1))) {
            // first digit isn't a number
            return number.charAt(0) + formatNumber(number.substring(1));
        }
        return formatNumber(number.substring(0, number.length() - 1)) + number.charAt(number.length() - 1);

    }

    public static double findFirstNumber(String str) {
        if (str.isEmpty())
            return 0;
        if (isNumber(str))
            return Double.parseDouble(str);
        if (isNumber(str.substring(0, 1)))
            return findFirstNumber(str.substring(0, str.length() - 1));
        return findFirstNumber(str.substring(1));
    }


    public static boolean isNumber(String str) {
        try {
            tryParse(str);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    /**
     * Format a number with number formatting options in the config
     *
     * @param number Number to be formatted
     * @return formatted number
     */
    public static String formatNumber(double number) {
        if (useDivisions) {
            // set divisions
            return setDivision(number);
        }
        return RUZ(decimalFormat.format(number));
    }

    /**
     * Get the full number value without any formatting
     *
     * @param number to get
     * @return String value of number
     */
    public static String getValue(double number) {
        //return decimalFormat.format(number);
        return RUZ(String.format("%f",number));
    }

    /**
     * Remove Unnecessary Zeros (RUZ)
     *
     * @param value value to check zeros for
     * @return a value with no unnecessary Zeros
     */
    public static String RUZ(String value) {
        if (value.isEmpty())
            return "";
        while (value.contains(Character.toString(decimalFormat.getDecimalFormatSymbols().getDecimalSeparator())) && (value.charAt(value.length() - 1) == '0' || value.charAt(value.length() - 1) == decimalFormat.getDecimalFormatSymbols().getDecimalSeparator()))
            value = value.substring(0, value.length() - 1);
        return value;
    }

    public static String setDivision(Double number) {
        for (Map.Entry<Long, String> entry : nfDivisions.entrySet()) {
            if (number / entry.getKey() >= 1) {
                return RUZ(decimalFormat.format((double) number / entry.getKey())) + entry.getValue();
            }
        }
        return RUZ(decimalFormat.format(number));
    }




    public static double parseCurrency(String amount){
        amount = ChatColor.stripColor(amount);
        // remove currency prefix and suffix
        String blankPrefix = ChatColor.stripColor(NumberFormatting.currencyPrefix);
        String blankSuffix = ChatColor.stripColor(NumberFormatting.currencySuffix);
        if (!blankPrefix.isEmpty() && amount.startsWith(blankPrefix))
            amount = amount.substring(blankPrefix.length());
        if (!blankSuffix.isEmpty() && amount.endsWith(blankSuffix))
            amount = amount.substring(0, amount.length() - blankSuffix.length());
        // get division or remove any non-numbers from the end
        long multiplyValue = 1;
        StringBuilder divisionString = new StringBuilder();
        while (!amount.isEmpty() && !isNumber(amount.substring(amount.length()-1))) {
            divisionString.append(amount.substring(amount.length() - 1));
            amount = amount.substring(0, amount.length()-1);
        }
        if (amount.isEmpty())
            return 0;
        if (useDivisions && nfDivisions.containsValue(divisionString.toString())) {
            for (Map.Entry<Long, String> entry : nfDivisions.entrySet()) {
                if (entry.getValue().contentEquals(divisionString)) {
                    multiplyValue = entry.getKey();
                    break;
                }
            }
        }

        try {
            return tryParse(amount) * multiplyValue;
        } catch (NumberFormatException ignored){}
        return NumberFormatting.findFirstNumber(amount) * multiplyValue;
    }


    public static double tryParse(String number) throws NumberFormatException {
        double amount;
        try {
            amount = decimalFormat.parse(number).doubleValue();
            //balance = Double.parseDouble(PlaceholderAPI.setPlaceholders(player, NumberFormatting.currency));
        } catch (ParseException e) {
            amount = Double.parseDouble(number);
        }
        return amount;
    }

    public static LinkedHashMap<Long, String> sortByValue(Map<Long, String> hm) {
        // Create a list from elements of HashMap
        List<Map.Entry<Long, String>> list =
                new LinkedList<>(hm.entrySet());

        // Sort the list
        list.sort((o1, o2) -> (o2.getKey()).compareTo(o1.getKey()));

        // put data from sorted list to hashmap
        LinkedHashMap<Long, String> temp = new LinkedHashMap<>();
        for (Map.Entry<Long, String> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }
}
