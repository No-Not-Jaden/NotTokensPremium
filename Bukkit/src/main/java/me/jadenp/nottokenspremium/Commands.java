package me.jadenp.nottokenspremium;

import me.jadenp.nottokenspremium.Configuration.Language;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class Commands implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
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
            if (sender.hasPermission("nottokens.top"))
                sender.sendMessage(ChatColor.YELLOW + "/token top" + ChatColor.GOLD + " View the players with the most tokens");
            if (sender.hasPermission("nottokens.viewother"))
                sender.sendMessage(ChatColor.YELLOW + "/token (player)" + ChatColor.GOLD + " View a player's current token balance");
            if (sender.hasPermission("nottokens.edit")) {
                sender.sendMessage(ChatColor.YELLOW + "/token give (player) (amount)" + ChatColor.GOLD + " Give a player tokens");
                sender.sendMessage(ChatColor.YELLOW + "/token remove (player) (amount)" + ChatColor.GOLD + " Remove a player's tokens");
                sender.sendMessage(ChatColor.YELLOW + "/token set (player) (amount)" + ChatColor.GOLD + " Set a player's tokens");
                sender.sendMessage(ChatColor.YELLOW + "/token giveall (amount) <online/offline>" + ChatColor.GOLD + " Give tokens to all offline or online players");
                sender.sendMessage(ChatColor.YELLOW + "/token removeall (amount) <online/offline>" + ChatColor.GOLD + " Remove tokens from all offline or online players");
            }
            if (sender.hasPermission("nottokens.admin")) {
                sender.sendMessage(ChatColor.YELLOW + "/token reload" + ChatColor.GOLD + " Reloads this plugin");
            }
            sender.sendMessage(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "                                              ");
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
            // get amount
            double amount;
            try {
                amount = NumberFormatting.tryParse(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Language.parse(Language.prefix + Language.unknownAmount, args[2], parser));
                return true;
            }
            // check balance
            double balance = TokenManager.getTokens(parser.getUniqueId());
            if (balance < amount) {
                sender.sendMessage(Language.parse(Language.prefix + Language.insufficientTokens, balance, parser));
                return true;
            }
            // transfer
            if (TokenManager.removeTokens(parser.getUniqueId(), amount))
                TokenManager.giveTokens(receiver.getUniqueId(), amount);
            sender.sendMessage(Language.parse(Language.prefix + Language.adminAdd, LoggedPlayers.getPlayerName(receiver.getUniqueId()), amount, parser));
            if (receiver.isOnline())
                receiver.getPlayer().sendMessage(Language.parse(Language.prefix + Language.playerReceive, sender.getName(), amount, receiver));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String s, String[] args) {
        if (!command.getName().equalsIgnoreCase("token"))
            return null;
        List<String> tab = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("nottokens.balance"))
                tab.add("help");
            if (sender instanceof Player && sender.hasPermission("nottokens.transfer"))
                tab.add("transfer");
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
            if (sender.hasPermission("nottokens.admin"))
                tab.add("reload");
        } else if (args.length == 2) {
            if (sender.hasPermission("nottokens.transfer") && args[0].equalsIgnoreCase("transfer"))
                tab.addAll(LoggedPlayers.getOnlinePlayerNames());
            if (sender.hasPermission("nottokens.edit") && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove")))
                tab.addAll(LoggedPlayers.getOnlinePlayerNames());
        } else if (args.length == 4) {
            if (sender.hasPermission("nottokens.edit") && (args[0].equalsIgnoreCase("giveall")  || args[0].equalsIgnoreCase("removeall"))) {
                tab.add("online");
                tab.add("offline");
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
