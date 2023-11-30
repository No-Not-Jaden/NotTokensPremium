package me.jadenp.nottokenspremium;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

/**
 * Keep track of every transaction that is made
 */
public class TransactionLogs {
    private static final List<String> transactions = new ArrayList<>();
    private static final Date now = new Date();
    private static final SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy");
    private static final SimpleDateFormat formatExact = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    private static File today;

    /**
     * Create a new log file for the day, or load a previous one from the day.
     */
    public static void loadTransactionLogs() {
        File records = new File(NotTokensPremium.getInstance().getDataFolder() + File.separator + "records");


        today = new File(records + File.separator + format.format(now) + ".txt");


        if (records.mkdir())
            Bukkit.getLogger().info("[NotTokensPremium] Created new records directory.");
        try {
            if (today.createNewFile()) {
                Bukkit.getLogger().info("[NotTokensPremium] Created new log file for today.");
            } else {
                Scanner scanner = new Scanner(today);
                while (scanner.hasNextLine()) {
                    String data = scanner.nextLine();
                    transactions.add(data);
                }
                scanner.close();
            }
        } catch (IOException e) {
            Bukkit.getLogger().warning("[NotTokensPremium] Error creating a log file for this session!");
            Bukkit.getLogger().warning(e.toString());
        }

    }

    /**
     * Add a message to the transaction logs
     * @param message Message to be added
     */
    public static void log(String message) {
        transactions.add("[" + formatExact.format(now) + "] " + message);
    }

    /**
     * Save the logs to today's file
     */
    public static void saveTransactions(){
        try {
            PrintWriter writer = new PrintWriter(today.getPath(), StandardCharsets.UTF_8);
            for (String s : transactions) {
                writer.println(s);
            }
            writer.close();
        } catch (IOException e) {
            Bukkit.getLogger().warning("[NotTokensPremium] Error in saving transactions!");
            Bukkit.getLogger().warning(e.toString());
        }
    }

}
