package me.jadenp.nottokenspremium.settings;

import me.jadenp.nottokenspremium.NotTokensPremium;
import me.jadenp.nottokenspremium.TokenManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;

import static me.jadenp.nottokenspremium.settings.NumberFormatting.tryParse;

public class ItemExchange {
    private static String object;
    private static double value;
    public static boolean enabled;
    private static List<String> addCommands;
    private static List<String> removeCommands;
    public static boolean autoExchange;
    private static int customModelData;
    private static boolean papiEnabled;

    public static void loadExchange(){
        FileConfiguration config = NotTokensPremium.getInstance().getConfig();
        enabled = config.getBoolean("item-exchange.enabled");
        object = config.getString("item-exchange.object");
        value = config.getDouble("item-exchange.value");
        autoExchange = config.getBoolean("item-exchange.auto-exchange");
        // checking for strings or lists from add and remove commands
        if (config.isString("item-exchange.add-commands")) {
            addCommands = Collections.singletonList(config.getString("item-exchange.add-commands"));
        } else {
            addCommands = config.getStringList("item-exchange.add-commands");
        }
        if (config.isString("item-exchange.remove-commands")) {
            removeCommands = Collections.singletonList(config.getString("item-exchange.remove-commands"));
        } else {
            removeCommands = config.getStringList("item-exchange.remove-commands");
        }

        papiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (object.contains("%") && !papiEnabled) {
            Bukkit.getLogger().warning("[NotTokensPremium] Detected a placeholder as item exchange object, but PlaceholderAPI is not enabled. Disabling item exchange.");
            enabled = false;
        }

        String customModelDataString = "-1";
        if (object.contains("<") && object.substring(object.indexOf("<")).contains(">")){
            // has custom model data
            customModelDataString = object.substring(object.indexOf("<") + 1, object.indexOf(">"));
            object = object.substring(0, object.indexOf("<")) + object.substring(object.indexOf(">") + 1);
        }
        try {
            customModelData = Integer.parseInt(customModelDataString);
        } catch (NumberFormatException e) {
            Bukkit.getLogger().warning("Could not get custom model data <" + customModelDataString + "> for currency: " + object);
            customModelData = -1;
        }
    }

    private static void doAddCommands(Player player, double amount) {
        for (String cmd : addCommands) {
            cmd = cmd.replaceAll("\\{player}", Matcher.quoteReplacement(player.getName())).replaceAll("\\{amount}", Matcher.quoteReplacement(String.format("%.8f", amount)));
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), Language.parse(cmd, player));
        }
    }

    private static void doRemoveCommands(Player player, double amount) {
        for (String cmd : removeCommands) {
            cmd = cmd.replaceAll("\\{player}", Matcher.quoteReplacement(player.getName())).replaceAll("\\{amount}", Matcher.quoteReplacement(String.format("%.8f", amount)));
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), Language.parse(cmd, player));
        }
    }

    public static String getObject() {
        return object;
    }

    public static double getValue() {
        return value;
    }

    public static boolean checkBalance(Player player, double amount) {
        double balance = getBalance(player);
        return balance >= amount;
    }

    /**
     * Get the balance of a player. Checks if the currency is a placeholder and parses it, otherwise, gets the amount of items matching the currency material
     *
     * @param player Player to get balance from
     * @return Balance of player
     */
    public static double getBalance(Player player) {
        if (object == null){
            Bukkit.getLogger().warning("[NotBounties] Cannot get balance of player because there is nothing setup for currency!");
        }
        return getBalance(player, object, customModelData);
    }

    private static double getBalance(Player player, String currencyName, int customModelData){
        if (currencyName.contains("%")) {
            if (papiEnabled) {
                // using placeholderAPI
                String placeholder = Language.parse(currencyName, player);
                try {
                    return tryParse(placeholder);
                } catch (NumberFormatException e) {
                    Bukkit.getLogger().warning("Error getting a number from the currency placeholder " + currencyName + "!");
                    return 0;
                }
            } else {
                Bukkit.getLogger().warning("Currency " + currencyName + " for token exchange object is a placeholder but PlaceholderAPI is not enabled!");
                return 0;
            }
        } else {
            // item
            return checkAmount(player, Material.valueOf(currencyName), customModelData);
        }
    }
    /**
     * Check the amount of items matching a material
     *
     * @param player   Player whose inventory will be searched
     * @param material Material to check for
     * @return amount of items in the players inventory that are a certain material
     */
    private static int checkAmount(Player player, Material material, int customModelData) {
        int amount = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack content : contents) {
            if (content != null) {
                if (isCorrectMaterial(content, material, customModelData)) {
                    amount += content.getAmount();
                }
            }
        }
        return amount;
    }

    public static boolean isCorrectMaterial(ItemStack item, Material material, int customModelData) {
        if (NotTokensPremium.serverVersion <= 13)
            return item.getType().equals(material);
        return item.getType().equals(material) &&
                (customModelData == -1 || (item.hasItemMeta() && Objects.requireNonNull(item.getItemMeta()).hasCustomModelData() && item.getItemMeta().getCustomModelData() == customModelData));
    }

    /**
     * Deposit an amount of the object and receive equivalent tokens
     * @param player Player making the transaction
     * @param amount Amount of the object to be deposited
     * @return The amount of tokens the player has received
     */
    public static double deposit(Player player, double amount) {
        double receiveAmount;
        if (!object.contains("%")) {
            removeItem(player, Material.valueOf(object), (long) amount, customModelData);
            receiveAmount = (long) amount;
        } else {
            receiveAmount = amount;
        }
        doRemoveCommands(player, receiveAmount);
        TokenManager.giveTokens(player.getUniqueId(), receiveAmount * value);
        return receiveAmount * value;
    }

    /**
     * Withdraw an amount of the object and lose equivalent tokens
     * @param player Player making the transaction
     * @param amount Amount of the object to be withdrawn
     * @return The amount of tokens that were removed from the player's account
     */
    public static double withdraw(Player player, double amount) {
        double removeAmount;
        if (!object.contains("%")) {
            ItemStack item = new ItemStack(Material.valueOf(object));
            if (customModelData != -1 && NotTokensPremium.serverVersion >= 14) {
                ItemMeta meta = item.getItemMeta();
                assert meta != null;
                meta.setCustomModelData(customModelData);
                item.setItemMeta(meta);
            }
            givePlayer(player, item, (long) amount);
            removeAmount = (long) amount;
        } else {
            removeAmount = amount;
        }
        doAddCommands(player, removeAmount);
        TokenManager.removeTokens(player.getUniqueId(), removeAmount * value);
        return removeAmount * value;
    }

    public static void removeItem(Player player, Material material, long amount, int customModelData) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                if (isCorrectMaterial(contents[i], material, customModelData)) {
                    if (contents[i].getAmount() > amount) {
                        contents[i] = new ItemStack(contents[i].getType(), (int) (contents[i].getAmount() - amount));
                        break;
                    } else if (contents[i].getAmount() < amount) {
                        amount -= contents[i].getAmount();
                        contents[i] = null;
                    } else {
                        contents[i] = null;
                        break;
                    }
                }
            }
        }
        player.getInventory().setContents(contents);
        player.playSound(player.getLocation(), Sound.UI_TOAST_OUT, 1, 1);
    }


    public static void givePlayer(Player p, ItemStack itemStack, long amount) {
        new BukkitRunnable() {
            long toGive = amount;
            @Override
            public void run() {
                if (toGive <= 0) {
                    this.cancel();
                    return;
                }
                if (toGive > itemStack.getMaxStackSize()) {
                    itemStack.setAmount(itemStack.getMaxStackSize());
                    toGive -= itemStack.getMaxStackSize();
                } else {
                    itemStack.setAmount((int) toGive);
                    toGive = 0;
                }
                HashMap<Integer, ItemStack> leftOver = new HashMap<>((p.getInventory().addItem(itemStack)));
                if (!leftOver.isEmpty()) {
                    Location loc = p.getLocation();
                    p.getWorld().dropItem(loc, leftOver.get(0));
                }
                p.playSound(p.getLocation(), Sound.UI_TOAST_IN, 1, 1);

            }
        }.runTaskTimer(NotTokensPremium.getInstance(), 0, 5);
    }


}
