package me.jadenp.nottokenspremium;

import me.jadenp.nottokenspremium.settings.ConfigOptions;
import me.jadenp.nottokenspremium.settings.ItemExchange;
import me.jadenp.nottokenspremium.settings.Language;
import me.jadenp.nottokenspremium.settings.NumberFormatting;
import me.jadenp.nottokenspremium.migration.MigratablePlugin;
import me.jadenp.nottokenspremium.migration.MigrationManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;

public class Commands implements CommandExecutor, TabCompleter {
    public Commands(){}
    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String s, String[] args) {
        if (!command.getName().equalsIgnoreCase("token"))
            return true;
        if (args.length == 0) {
            // /tokens
            if (sender instanceof Player)
                sender.sendMessage(Language.parse(Language.prefix + Language.balance, TokenManager.getTokens(((Player) sender).getUniqueId()), (Player) sender));
            else
                sender.sendMessage(Language.parse(Language.prefix + ChatColor.DARK_AQUA + "You can't have tokens. Do " + ChatColor.AQUA + "/token help" + ChatColor.DARK_AQUA + " for a list of commands."));
            return true;
        }

        Player parser = sender instanceof Player ? (Player) sender : null;

        if (args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(Language.parse(Language.prefix + ChatColor.DARK_AQUA + "Commands:"));
            sender.sendMessage(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "                                              ");
            if (sender instanceof Player && sender.hasPermission("nottokens.balance"))
                sender.sendMessage(ChatColor.YELLOW + "/token " + ChatColor.GOLD + " View your current token balance");
            if (sender instanceof Player && sender.hasPermission("nottokens.transfer"))
                sender.sendMessage(ChatColor.YELLOW + "/token transfer (player) (amount) " + ChatColor.GOLD + " Transfer your tokens to another player");
            if (sender instanceof Player && sender.hasPermission("nottokens.exchange") && ItemExchange.enabled) {
                sender.sendMessage(ChatColor.YELLOW + "/token deposit (amount/all) " + ChatColor.GOLD + " Deposit " + ItemExchange.getObject() + " into your account for " + ItemExchange.getValue() + " tokens each.");
                sender.sendMessage(ChatColor.YELLOW + "/token withdraw (amount/all) " + ChatColor.GOLD + " Withdraw " + ItemExchange.getObject() + " from your account requiring " + ItemExchange.getValue() + " tokens each.");
            }
            if (sender.hasPermission("nottokens.top"))
                sender.sendMessage(ChatColor.YELLOW + "/token top " + ChatColor.GOLD + " View the players with the most tokens");
            if (sender.hasPermission("nottokens.viewother"))
                sender.sendMessage(ChatColor.YELLOW + "/token (player) " + ChatColor.GOLD + " View a player's current token balance");
            if (sender.hasPermission("nottokens.edit")) {
                sender.sendMessage(ChatColor.YELLOW + "/token give (player) (amount) " + ChatColor.GOLD + " Give a player tokens");
                sender.sendMessage(ChatColor.YELLOW + "/token remove (player) (amount) " + ChatColor.GOLD + " Remove a player's tokens");
                sender.sendMessage(ChatColor.YELLOW + "/token set (player) (amount) " + ChatColor.GOLD + " Set a player's tokens");
                sender.sendMessage(ChatColor.YELLOW + "/token giveall (amount) <online/offline> " + ChatColor.GOLD + " Give tokens to all offline or online players");
                sender.sendMessage(ChatColor.YELLOW + "/token removeall (amount) <online/offline> " + ChatColor.GOLD + " Remove tokens from all offline or online players");
            }
            if (sender.hasPermission("nottokens.admin")) {
                sender.sendMessage(ChatColor.YELLOW + "/token migrate (plugin name) (SWAP/ADD/TRANSFER/COPY/REPLACE/NONE) " + ChatColor.GOLD + " Migrates another plugin's tokens into NotTokens");
                sender.sendMessage(ChatColor.YELLOW + "/token reload " + ChatColor.GOLD + " Reloads this plugin");
            }
            sender.sendMessage(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "                                              ");
        } else if (args[0].equalsIgnoreCase("migrate")) {
            // /token migrate (plugin name) (SWAP/ADD/TRANSFER/COPY/NONE)
            if (!sender.hasPermission("nottokens.admin") || !(sender instanceof Player)) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownCommand, parser));
                return true;
            }
            // wrong number of arguments
            if (args.length != 3) {
                sender.sendMessage(Language.parse(Language.prefix + ChatColor.YELLOW + "/token migrate (plugin name) (SWAP/ADD/TRANSFER/COPY/NONE)" + ChatColor.GOLD + " Migrates another plugin's tokens into NotTokens", parser));
                return true;
            }
            MigrationManager.MigrationType migrationType;
            try {
                migrationType = MigrationManager.MigrationType.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                // unknown migration type
                sender.sendMessage(Language.parse(Language.prefix + ChatColor.YELLOW + "/token migrate (plugin name) (SWAP/ADD/TRANSFER/COPY/NONE)" + ChatColor.GOLD + " Migrates another plugin's tokens into NotTokens", parser));
                return true;
            }
            try {
                MigrationManager.migrateTokens(migrationType, args[1]); // do migration
                MigratablePlugin plugin = MigrationManager.getCurrentExternalTokenPlugin(); // specified plugin
                if (plugin == null || migrationType == MigrationManager.MigrationType.NONE) {
                    // migration stopped
                    sender.sendMessage(Language.parse(Language.prefix + ChatColor.RED + "Migration has been stopped."));
                    TransactionLogs.log(parser.getName() + " has stopped migration.");
                    return true;
                }
                // migration status - active or complete
                if (plugin.isActiveMigration()) {
                    sender.sendMessage(Language.parse(Language.prefix + ChatColor.YELLOW + "Migration has started. Players will have their tokens automatically migrated when they join."));
                    sender.sendMessage(Language.parse(Language.prefix + ChatColor.YELLOW + "To stop migration, do " + ChatColor.GOLD + "/nottokens migrate NONE NONE"));
                    TransactionLogs.log(parser.getName() + " has started migration. Plugin: " + plugin.getName() + " Type: " + migrationType.name());
                } else {
                    sender.sendMessage(Language.parse(Language.prefix + ChatColor.GREEN + "Migration has been completed."));
                    TransactionLogs.log(parser.getName() + " has completed a migration. Plugin: " + plugin.getName() + " Type: " + migrationType.name());
                }
            } catch (RuntimeException e) {
                // error in migration
                sender.sendMessage(Language.parse(Language.prefix + ChatColor.RED + "Could not migrate plugin: " + ChatColor.DARK_RED + e.getMessage()));
                Bukkit.getLogger().warning("[NotTokensPremium] Migration Error: ");
                Bukkit.getLogger().warning(e.toString());
            }
        } else if (args[0].equalsIgnoreCase("transfer")) {
            // /token transfer
            if (!sender.hasPermission("nottokens.transfer") || !(sender instanceof Player)) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownCommand, parser));
                return true;
            }
            // wrong number of arguments
            if (args.length != 3) {
                sender.sendMessage(Language.parse(Language.prefix + ChatColor.YELLOW + "/token transfer (player) (amount) " + ChatColor.GOLD + " Transfer your tokens to another player", parser));
                return true;
            }
            // get player
            OfflinePlayer receiver = LoggedPlayers.getPlayer(args[1]);
            if (receiver == null) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownPlayer, args[1], parser));
                return true;
            }
            // sending to yourself
            if (receiver.getUniqueId().equals(parser.getUniqueId())) {
                sender.sendMessage(Language.parse(Language.prefix + Language.transferOwn, parser));
                return true;
            }
            // get amount
            double amount;
            try {
                amount = NumberFormatting.tryParse(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownAmount, args[2], parser));
                return true;
            }
            // check balance
            double balance = ItemExchange.autoExchange ? TokenManager.getTokens(parser.getUniqueId()) + ItemExchange.getBalance(parser) * ItemExchange.getValue() : TokenManager.getTokens(parser.getUniqueId());
            if (balance < amount) {
                sender.sendMessage(Language.parse(Language.prefix + Language.insufficientTokens, amount, parser));
                return true;
            }
            // transfer
            if (TokenManager.removeTokens(parser.getUniqueId(), amount))
                sender.sendMessage(Language.parse(Language.prefix + Language.adminAdd, LoggedPlayers.getPlayerName(receiver.getUniqueId()), amount, parser));
            if (TokenManager.giveTokens(receiver.getUniqueId(), amount) && receiver.isOnline())
                Objects.requireNonNull(receiver.getPlayer()).sendMessage(Language.parse(Language.prefix + Language.playerReceive, sender.getName(), amount, receiver));
            TransactionLogs.log(parser.getName() + " transferred " + amount + " tokens to " + LoggedPlayers.getPlayerName(receiver.getUniqueId()) +". Balances: " +  NumberFormatting.formatNumber(TokenManager.getTokens(parser.getUniqueId())) + " : " + NumberFormatting.formatNumber(TokenManager.getTokens(receiver.getUniqueId())));
            return true;
        } else if (args[0].equalsIgnoreCase("top")) {
            // /token top
            if (!sender.hasPermission("nottokens.top")) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownCommand, parser));
                return true;
            }
            sender.sendMessage(Language.parse(Language.prefix + Language.leaderboard, parser));
            int rank = 1;
            for (Map.Entry<UUID, Double> entry : TokenManager.getTopTokens(10).entrySet()) {
                sender.sendMessage(Language.parse(Language.leaderboardRank.replaceAll("\\{rank}", Matcher.quoteReplacement(rank + "")), entry.getValue(), Bukkit.getOfflinePlayer(entry.getKey())));
                rank++;
            }
            sender.sendMessage(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "                                           ");
            return true;
        } else if (args[0].equalsIgnoreCase("give")) {
            // /token give (player) (amount)
            if (!sender.hasPermission("nottokens.edit")) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownCommand, parser));
                return true;
            }
            // wrong number of arguments
            if (args.length != 3) {
                sender.sendMessage(Language.parse(Language.prefix + ChatColor.YELLOW + "/token give (player) (amount) " + ChatColor.GOLD + " Give a player tokens", parser));
                return true;
            }
            // get player
            OfflinePlayer receiver = LoggedPlayers.getPlayer(args[1]);
            if (receiver == null) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownPlayer, args[1], parser));
                return true;
            }
            // get amount
            double amount;
            try {
                amount = NumberFormatting.tryParse(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownAmount, args[2], parser));
                return true;
            }
            if (TokenManager.giveTokens(receiver.getUniqueId(), amount) && receiver.isOnline() && !((sender instanceof Player) && ((Player) sender).getUniqueId().equals(receiver.getUniqueId())))
                Objects.requireNonNull(receiver.getPlayer()).sendMessage(Language.parse(Language.prefix + Language.playerReceive, sender.getName(), amount, parser));
            sender.sendMessage(Language.parse(Language.prefix + Language.adminAdd, LoggedPlayers.getPlayerName(receiver.getUniqueId()), amount, parser));
            String senderName = sender instanceof Player ? parser.getName() : "CONSOLE";
            TransactionLogs.log(senderName + " gave " + amount + " tokens to " + LoggedPlayers.getPlayerName(receiver.getUniqueId()) +". Balance: " +  NumberFormatting.formatNumber(TokenManager.getTokens(receiver.getUniqueId())));
            return true;
        } else if (args[0].equalsIgnoreCase("remove")) {
            // /token remove (player) (amount)
            if (!sender.hasPermission("nottokens.edit")) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownCommand, parser));
                return true;
            }
            // wrong number of arguments
            if (args.length != 3) {
                sender.sendMessage(Language.parse(Language.prefix + ChatColor.YELLOW + "/token remove (player) (amount)" + ChatColor.GOLD + " Remove a player's tokens", parser));
                return true;
            }
            // get player
            OfflinePlayer receiver = LoggedPlayers.getPlayer(args[1]);
            if (receiver == null) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownPlayer, args[1], parser));
                return true;
            }
            // get amount
            double amount;
            try {
                amount = NumberFormatting.tryParse(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownAmount, args[2], parser));
                return true;
            }
            // check if they have enough tokens
            double balance = ItemExchange.autoExchange && receiver.isOnline() ? TokenManager.getTokens(receiver.getUniqueId()) + ItemExchange.getBalance((Player) receiver) * ItemExchange.getValue() : TokenManager.getTokens(receiver.getUniqueId());
            if (amount > balance && !ConfigOptions.negativeTokens) {
                amount = balance;
            }
            // remove tokens & send messages
            if (TokenManager.removeTokens(receiver.getUniqueId(), amount) && receiver.isOnline() && !((sender instanceof Player) && ((Player) sender).getUniqueId().equals(receiver.getUniqueId())))
                Objects.requireNonNull(receiver.getPlayer()).sendMessage(Language.parse(Language.prefix + Language.playerTake, sender.getName(), amount, parser));
            sender.sendMessage(Language.parse(Language.prefix + Language.adminRemove, LoggedPlayers.getPlayerName(receiver.getUniqueId()), amount, parser));
            String senderName = sender instanceof Player ? parser.getName() : "CONSOLE";
            TransactionLogs.log(senderName + " removed " + amount + " tokens from " + LoggedPlayers.getPlayerName(receiver.getUniqueId()) +". Balance: " +  NumberFormatting.formatNumber(TokenManager.getTokens(receiver.getUniqueId())));
            return true;
        } else if (args[0].equalsIgnoreCase("set")) {
            // /token set (player) (amount)
            if (!sender.hasPermission("nottokens.edit")) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownCommand, parser));
                return true;
            }
            // wrong number of arguments
            if (args.length != 3) {
                sender.sendMessage(Language.parse(Language.prefix + ChatColor.YELLOW + "/token set (player) (amount)" + ChatColor.GOLD + " Set a player's tokens", parser));
                return true;
            }
            // get player
            OfflinePlayer receiver = LoggedPlayers.getPlayer(args[1]);
            if (receiver == null) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownPlayer, args[1], parser));
                return true;
            }
            // get amount
            double amount;
            try {
                amount = NumberFormatting.tryParse(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownAmount, args[2], parser));
                return true;
            }
            // check for negative values
            if (amount < 0 && !ConfigOptions.negativeTokens)
                amount = 0;

            // set tokens and send messages
            if (TokenManager.setTokens(receiver.getUniqueId(), amount) && receiver.isOnline() && !((sender instanceof Player) && ((Player) sender).getUniqueId().equals(receiver.getUniqueId())))
                Objects.requireNonNull(receiver.getPlayer()).sendMessage(Language.parse(Language.prefix + Language.playerSet, sender.getName(), amount, parser));
            sender.sendMessage(Language.parse(Language.prefix + Language.adminSet, LoggedPlayers.getPlayerName(receiver.getUniqueId()), amount, parser));
            String senderName = sender instanceof Player ? parser.getName() : "CONSOLE";
            TransactionLogs.log(senderName + " set " + LoggedPlayers.getPlayerName(receiver.getUniqueId()) +"'s balance to " + amount);
            return true;
        } else if (args[0].equalsIgnoreCase("giveall")) {
            // /token giveall (amount) (online/offline)
            if (!sender.hasPermission("nottokens.edit")) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownCommand, parser));
                return true;
            }
            // wrong number of arguments
            if (args.length != 2 && args.length != 3) {
                sender.sendMessage(Language.parse(Language.prefix + ChatColor.YELLOW + "/token giveall (amount) <online/offline>" + ChatColor.GOLD + " Give tokens to all offline or online players", parser));
                return true;
            }
            // get amount
            double amount;
            try {
                amount = NumberFormatting.tryParse(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownAmount, args[1], parser));
                return true;
            }
            if (args.length == 3 && args[2].equalsIgnoreCase("offline")) {
                for (UUID uuid : LoggedPlayers.getAllUUIDs()) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    if (TokenManager.giveTokens(uuid, amount) && player.isOnline() && !((sender instanceof Player) && ((Player) sender).getUniqueId().equals(player.getUniqueId())))
                        Objects.requireNonNull(player.getPlayer()).sendMessage(Language.parse(Language.prefix + Language.playerReceive, sender.getName(), amount, parser));
                    String senderName = sender instanceof Player ? parser.getName() : "CONSOLE";
                    TransactionLogs.log(senderName + " gave " + amount + " tokens to " + LoggedPlayers.getPlayerName(player.getUniqueId()) +". (giveall) Balance: " +  NumberFormatting.formatNumber(TokenManager.getTokens(player.getUniqueId())));
                }
                // send message
                sender.sendMessage(Language.parse(Language.prefix + Language.adminGiveAll.replaceAll("\\{amount}", Matcher.quoteReplacement(LoggedPlayers.getAllUUIDs().size() + "")), amount, parser));
            } else {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (TokenManager.giveTokens(player.getUniqueId(), amount) && !((sender instanceof Player) && ((Player) sender).getUniqueId().equals(player.getUniqueId())))
                        player.sendMessage(Language.parse(Language.prefix + Language.playerReceive, sender.getName(), amount, parser));
                    String senderName = sender instanceof Player ? parser.getName() : "CONSOLE";
                    TransactionLogs.log(senderName + " gave " + amount + " tokens to " + LoggedPlayers.getPlayerName(player.getUniqueId()) +". (giveall) Balance: " +  NumberFormatting.formatNumber(TokenManager.getTokens(player.getUniqueId())));
                }
                // send message
                sender.sendMessage(Language.parse(Language.prefix + Language.adminGiveAll.replaceAll("\\{amount}", Matcher.quoteReplacement(Bukkit.getOnlinePlayers().size() + "")), amount, parser));
            }
        } else if (args[0].equalsIgnoreCase("removeall")) {
            // /token removeall (amount) (online/offline)
            if (!sender.hasPermission("nottokens.edit")) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownCommand, parser));
                return true;
            }
            // wrong number of arguments
            if (args.length != 2 && args.length != 3) {
                sender.sendMessage(Language.parse(Language.prefix + ChatColor.YELLOW + "/token removeall (amount) <online/offline>" + ChatColor.GOLD + " Remove tokens from all offline or online players", parser));
                return true;
            }
            // get amount
            double amount;
            try {
                amount = NumberFormatting.tryParse(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownAmount, args[1], parser));
                return true;
            }
            if (args.length == 3 && args[2].equalsIgnoreCase("offline")) {
                for (UUID uuid : LoggedPlayers.getAllUUIDs()) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    if (TokenManager.removeTokens(uuid, amount) && player.isOnline() && !((sender instanceof Player) && ((Player) sender).getUniqueId().equals(player.getUniqueId())))
                        Objects.requireNonNull(player.getPlayer()).sendMessage(Language.parse(Language.prefix + Language.playerTake, sender.getName(), amount, parser));
                    String senderName = sender instanceof Player ? parser.getName() : "CONSOLE";
                    TransactionLogs.log(senderName + " removed " + amount + " tokens from " + LoggedPlayers.getPlayerName(player.getUniqueId()) +". (removeall) Balance: " +  NumberFormatting.formatNumber(TokenManager.getTokens(player.getUniqueId())));
                }
                // send message
                sender.sendMessage(Language.parse(Language.prefix + Language.adminRemoveAll.replaceAll("\\{amount}", Matcher.quoteReplacement(LoggedPlayers.getAllUUIDs().size() + "")), amount, parser));
            } else {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (TokenManager.removeTokens(player.getUniqueId(), amount) && !((sender instanceof Player) && ((Player) sender).getUniqueId().equals(player.getUniqueId())))
                        player.sendMessage(Language.parse(Language.prefix + Language.playerTake, sender.getName(), amount, parser));
                    String senderName = sender instanceof Player ? parser.getName() : "CONSOLE";
                    TransactionLogs.log(senderName + " removed " + amount + " tokens from " + LoggedPlayers.getPlayerName(player.getUniqueId()) +". (removeall) Balance: " +  NumberFormatting.formatNumber(TokenManager.getTokens(player.getUniqueId())));
                }
                // send message
                sender.sendMessage(Language.parse(Language.prefix + Language.adminRemoveAll.replaceAll("\\{amount}", Matcher.quoteReplacement(Bukkit.getOnlinePlayers().size() + "")), amount, parser));
            }
        } else if (args[0].equalsIgnoreCase("reload")) {
            // /tokens reload
            if (!sender.hasPermission("nottokens.admin")) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownCommand, parser));
                return true;
            }
            ConfigOptions.loadConfigOptions();
            Language.loadLanguageOptions();
            sender.sendMessage(Language.parse(Language.prefix + ChatColor.GREEN + "Reloaded NotTokensPremium " + NotTokensPremium.getInstance().getDescription().getVersion()));
        } else if (args[0].equalsIgnoreCase("deposit")) {
            // /tokens deposit (amount/all)
            // permission check
            if (!sender.hasPermission("nottokens.exchange") || !(sender instanceof Player) || !ItemExchange.enabled) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownCommand, parser));
                return true;
            }
            // get amount
            Player player = (Player) sender;
            double itemBalance = ItemExchange.getBalance(player);
            double amount;
            if (args[1].equalsIgnoreCase("all")) {
                amount = itemBalance;
            } else {
                try {
                    amount = NumberFormatting.tryParse(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Language.parse(Language.prefix + Language.unknownAmount, args[1], parser));
                    return true;
                }
            }
            // item
            if (!ItemExchange.getObject().contains("%")) {
                amount = (long) amount;
            }
            // insufficient balance
            if (itemBalance < amount || amount == 0.0) {
                sender.sendMessage(Language.parse(Language.prefix + Language.insufficientExchange,NumberFormatting.formatNumber(itemBalance), parser));
                return true;
            }
            // deposit
            double tokensReceived = ItemExchange.deposit(player, amount);
            sender.sendMessage(Language.parse(Language.prefix + Language.deposit.replaceAll("\\{amount}", Matcher.quoteReplacement(NumberFormatting.formatNumber(amount))), tokensReceived, parser));
            TransactionLogs.log(player.getName() + " deposited " + amount + " exchange items in return for " + tokensReceived +" tokens. Balance: " +  NumberFormatting.formatNumber(TokenManager.getTokens(player.getUniqueId())));
        } else if (args[0].equalsIgnoreCase("withdraw")) {
            // /tokens withdraw (amount/all)
            // permission check
            if (!sender.hasPermission("nottokens.exchange") || !(sender instanceof Player) || !ItemExchange.enabled) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownCommand, parser));
                return true;
            }
            // get amount
            Player player = (Player) sender;
            double tokenBalance = TokenManager.getTokens(player.getUniqueId());
            double maxAmount;
            if (ItemExchange.getObject().contains("%"))
                maxAmount = tokenBalance / ItemExchange.getValue();
            else
                maxAmount = (long) (tokenBalance / ItemExchange.getValue());
            double amount;
            if (args[1].equalsIgnoreCase("all")) {
                amount = maxAmount;
            } else {
                try {
                    amount = NumberFormatting.tryParse(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Language.parse(Language.prefix + Language.unknownAmount, args[1], parser));
                    return true;
                }
            }
            // item
            if (!ItemExchange.getObject().contains("%")) {
                amount = (long) amount;
            }
            // insufficient balance
            if (maxAmount < amount || amount == 0.0) {
                sender.sendMessage(Language.parse(Language.prefix + Language.insufficientTokens, amount * ItemExchange.getValue(), parser));
                return true;
            }
            // withdraw
            double tokensRemoved = ItemExchange.withdraw(player, amount);
            sender.sendMessage(Language.parse(Language.prefix + Language.deposit.replaceAll("\\{amount}", Matcher.quoteReplacement(NumberFormatting.formatNumber(amount))), tokensRemoved, parser));
            TransactionLogs.log(player.getName() + " withdrew " + amount + " exchange items requiring a total of " + tokensRemoved +" tokens. Balance: " +  NumberFormatting.formatNumber(TokenManager.getTokens(player.getUniqueId())));
        } else {
            OfflinePlayer player = LoggedPlayers.getPlayer(args[0]);
            if (player != null) {
                // /tokens (player)
                // permission check
                if (!sender.hasPermission("nottokens.viewother")) {
                    sender.sendMessage(Language.parse(Language.prefix + Language.unknownCommand, parser));
                    return true;
                }
                // send token balance message
                sender.sendMessage(Language.parse(Language.prefix + Language.otherTokens, LoggedPlayers.getPlayerName(player.getUniqueId()), TokenManager.getTokens(player.getUniqueId()), parser));
            } else {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownCommand, parser));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String s, String[] args) {
        if (!command.getName().equalsIgnoreCase("token"))
            return null;
        List<String> tab = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("nottokens.balance"))
                tab.add("help");
            if (sender instanceof Player && sender.hasPermission("nottokens.transfer"))
                tab.add("transfer");
            if (sender instanceof Player && sender.hasPermission("nottokens.exchange") && ItemExchange.enabled) {
                tab.add("deposit");
                tab.add("withdraw");
            }
            if (sender.hasPermission("nottokens.top"))
                tab.add("top");
            if (sender.hasPermission("nottokens.viewother"))
                tab.addAll(LoggedPlayers.getOnlinePlayerNames());
            if (sender.hasPermission("nottokens.edit")) {
                tab.add("give");
                tab.add("set");
                tab.add("remove");
                tab.add("giveall");
                tab.add("removeall");
            }
            if (sender.hasPermission("nottokens.admin")) {
                tab.add("reload");
                tab.add("migrate");
            }
        } else if (args.length == 2) {
            if (sender.hasPermission("nottokens.transfer") && args[0].equalsIgnoreCase("transfer"))
                tab.addAll(LoggedPlayers.getOnlinePlayerNames());
            if (sender.hasPermission("nottokens.edit") && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove")))
                tab.addAll(LoggedPlayers.getOnlinePlayerNames());
            if (sender instanceof Player && sender.hasPermission("nottokens.exchange") && ItemExchange.enabled && (args[0].equalsIgnoreCase("deposit") || args[0].equalsIgnoreCase("withdraw"))) {
                tab.add("all");
            }
            if (sender.hasPermission("nottokens.admin") && args[0].equalsIgnoreCase("migrate")) {
                tab.addAll(MigrationManager.getEnabledPlugins());
                tab.add("NONE");
            }
        } else if (args.length == 3) {
            if (sender.hasPermission("nottokens.edit") && (args[0].equalsIgnoreCase("giveall")  || args[0].equalsIgnoreCase("removeall"))) {
                tab.add("online");
                tab.add("offline");
            }
            if (sender.hasPermission("nottokens.admin") && args[0].equalsIgnoreCase("migrate")) {
                tab.add("SWAP");
                tab.add("COPY");
                tab.add("TRANSFER");
                tab.add("ADD");
                tab.add("REPLACE");
                tab.add("NONE");
            }
        }

        String typed = args[args.length-1];
        tab.removeIf(test -> test.toLowerCase(Locale.ROOT).indexOf(typed.toLowerCase(Locale.ROOT)) != 0);
        if (args.length == 1 && tab.isEmpty() && sender.hasPermission("nottokens.viewother")) {
            tab.addAll(LoggedPlayers.getAllPlayerNames());
        }
        if (args.length == 2 && tab.isEmpty() && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove")))
            tab.addAll(LoggedPlayers.getAllPlayerNames());
        tab.removeIf(test -> test.toLowerCase(Locale.ROOT).indexOf(typed.toLowerCase(Locale.ROOT)) != 0);
        Collections.sort(tab);
        return tab;
    }
}
