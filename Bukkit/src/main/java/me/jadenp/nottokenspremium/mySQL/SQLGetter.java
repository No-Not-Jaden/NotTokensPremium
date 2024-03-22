package me.jadenp.nottokenspremium.mySQL;

import me.jadenp.nottokenspremium.LoggedPlayers;
import me.jadenp.nottokenspremium.NotTokensPremium;
import me.jadenp.nottokenspremium.TokenManager;
import me.jadenp.nottokenspremium.settings.ConfigOptions;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static me.jadenp.nottokenspremium.NotTokensPremium.debug;
import static me.jadenp.nottokenspremium.TokenManager.tryToConnect;

public class SQLGetter {

    private final MySQL SQL;
    private long nextReconnectAttempt;
    private int reconnectAttempts;

    public SQLGetter(MySQL SQL) {
        this.SQL = SQL;
        nextReconnectAttempt = System.currentTimeMillis();
        reconnectAttempts = 0;
    }

    /**
     * Create an SQL table to store tokens. If the old NotTokens database is present, then the tokens will be updated to a double data type.
     */
    public void createTable() {
        PreparedStatement ps;
        try {
            // create table
            ps = SQL.getConnection().prepareStatement("CREATE TABLE IF NOT EXISTS player_tokens" +
                    "(" +
                    "    uuid CHAR(36) NOT NULL," +
                    "    tokens FLOAT(53) DEFAULT 0 NOT NULL," +
                    "    PRIMARY KEY (uuid)" +
                    ");");
            ps.executeUpdate();
            // update old table
            ps = SQL.getConnection().prepareStatement("select column_name,data_type from information_schema.columns where table_schema = '" + SQL.getDatabase() + "' and table_name = 'player_tokens' and column_name = 'tokens';");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                if (rs.getString("data_type").equalsIgnoreCase("bigint")) {
                    Bukkit.getLogger().info("[NotTokensPremium] Updating SQL data type for tokens.");
                    ps = SQL.getConnection().prepareStatement("ALTER TABLE player_tokens MODIFY COLUMN tokens FLOAT(53);");
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            Bukkit.getLogger().warning("[NotTokensPremium] Lost connection with database, will try to reconnect.");
            SQL.disconnect();
            if (!tryToConnect()) {
                Bukkit.getScheduler().runTaskLater(NotTokensPremium.getInstance(), () -> {
                    if (tryToConnect()) {
                        createTable();
                    }
                }, 40L);
            } else {
                createTable();
            }
        }
    }

    public void createLoggedPlayerTable() {
        PreparedStatement ps;
        try {
            ps = SQL.getConnection().prepareStatement("CREATE TABLE IF NOT EXISTS token_players" +
                    "(" +
                    "    uuid CHAR(36) NOT NULL," +
                    "    name VARCHAR(16) NOT NULL," +
                    "    id INT DEFAULT 0 NOT NULL," +
                    "    PRIMARY KEY (uuid)" +
                    ");");
            ps.executeUpdate();
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            Bukkit.getLogger().warning("[NotTokensPremium] Lost connection with database, will try to reconnect.");
            SQL.disconnect();
            if (!tryToConnect()) {
                Bukkit.getScheduler().runTaskLater(NotTokensPremium.getInstance(), () -> {
                    if (tryToConnect()) {
                        createTable();
                    }
                }, 40L);
            } else {
                createLoggedPlayerTable();
            }
        }
    }

    private long lastPlayerListRequest = 0;
    private final List<OfflinePlayer> onlinePlayers = new ArrayList<>();

    public List<OfflinePlayer> getOnlinePlayers() {
        if (lastPlayerListRequest + 5000 > System.currentTimeMillis()) {
            if (onlinePlayers.isEmpty())
                onlinePlayers.addAll(Bukkit.getOnlinePlayers());
            return onlinePlayers;
        }
        lastPlayerListRequest = System.currentTimeMillis();
        return getNetworkPlayers();
    }

    private List<OfflinePlayer> getNetworkPlayers() {
        List<OfflinePlayer> networkPlayers = new ArrayList<>();
        try {
            PreparedStatement ps = SQL.getConnection().prepareStatement("SELECT uuid FROM token_players WHERE id != 0;");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                try {
                    networkPlayers.add(Bukkit.getOfflinePlayer(UUID.fromString(uuid)));
                } catch (IllegalArgumentException e) {
                    Bukkit.getLogger().warning("[NotTokensPremium] Removing invalid UUID on database: " + uuid);
                    PreparedStatement ps1 = SQL.getConnection().prepareStatement("DELETE FROM token_players WHERE uuid = ?;");
                    ps1.setString(1, uuid);
                    ps1.executeUpdate();
                }
            }
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            if (reconnect()) {
                return getNetworkPlayers();
            }
        }
        onlinePlayers.clear();
        onlinePlayers.addAll(networkPlayers);
        return networkPlayers;
    }

    /**
     * Get all the logged players from the database
     *
     * @return The logged players
     */
    public Map<UUID, String> getLoggedPlayers() {
        try {
            PreparedStatement ps = SQL.getConnection().prepareStatement("SELECT uuid, name FROM token_players;");
            ResultSet rs = ps.executeQuery();
            Map<UUID, String> loggedPlayers = new HashMap<>();
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String name = rs.getString("name");
                try {
                    loggedPlayers.put(UUID.fromString(uuid), name);
                } catch (IllegalArgumentException e) {
                    Bukkit.getLogger().warning("[NotTokensPremium] Removing invalid UUID on database: " + uuid);
                    PreparedStatement ps1 = SQL.getConnection().prepareStatement("DELETE FROM token_players WHERE uuid = ?;");
                    ps1.setString(1, uuid);
                    ps1.executeUpdate();
                }
            }
            return loggedPlayers;
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            if (reconnect()) {
                return getLoggedPlayers();
            }
        }
        return new HashMap<>();
    }

    /**
     * Get the uuid and name of a logged player
     * @param name Name of the player (not case-sensitive)
     * @return The UUID and name of the player, or null if the player hasn't been logged.
     * If the array is not null, the length will be 2 with the uuid and name inside.
     */
    public @Nullable String[] getLoggedPlayer(String name) {
        try {
            PreparedStatement ps = SQL.getConnection().prepareStatement("SELECT uuid, name FROM token_players WHERE UPPER(name) = UPPER(?);");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String uuid = rs.getString("uuid");
                String realName = rs.getString("name");
                return new String[]{uuid,realName};
            }
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            if (reconnect()) {
                return getLoggedPlayer(name);
            }
        }
        return null;
    }

    /**
     * Log players to the SQL Database
     *
     * @param UUIDNameMap Players to log
     */
    public void logPlayers(Map<UUID, String> UUIDNameMap) {
        if (UUIDNameMap.isEmpty())
            return;
        StringBuilder builder = new StringBuilder();
        builder.append("INSERT IGNORE INTO token_players(uuid, name, id) VALUES(?, ?, ?);".repeat(UUIDNameMap.size()));

        try {
            PreparedStatement ps = SQL.getConnection().prepareStatement(builder.toString());
            int i = 1;
            for (Map.Entry<UUID, String> entry : UUIDNameMap.entrySet()) {
                ps.setString(i, entry.getKey().toString());
                ps.setString(i + 1, entry.getValue());
                ps.setInt(i + 2, 0);
                i += 3;
            }

            ps.executeUpdate();
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            if (reconnect()) {
                logPlayers(UUIDNameMap);
            }
        }
    }

    public void logPlayer(UUID uuid, String name) {
        try {
            PreparedStatement ps = SQL.getConnection().prepareStatement("INSERT IGNORE INTO token_players(uuid, name, id) VALUES(?, ?, ?);");
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setInt(3, 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            if (reconnect()) {
                logPlayer(uuid, name);
            }
        }
    }

    public void refreshOnlinePlayers() {
        try {
            PreparedStatement ps = SQL.getConnection().prepareStatement("UPDATE token_players SET id = 0 WHERE id = ?;");
            ps.setInt(1, SQL.getServerID());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            if (reconnect()) {
                refreshOnlinePlayers();
            }
        }
        for (Player player : Bukkit.getOnlinePlayers())
            login(player);
    }

    public void logout(Player player) {
        try {
            PreparedStatement ps = SQL.getConnection().prepareStatement("UPDATE token_players SET id = 0 WHERE uuid = ? AND id = ?;");
            ps.setString(1, player.getUniqueId().toString());
            ps.setInt(2, SQL.getServerID());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            if (reconnect()) {
                logout(player);
            }
        }
    }

    public void login(Player player) {
        try {
            PreparedStatement ps = SQL.getConnection().prepareStatement("INSERT INTO token_players(uuid, name, id) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE id = ?;");
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, player.getName());
            ps.setInt(3, SQL.getServerID());
            ps.setInt(4, SQL.getServerID());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            if (reconnect()) {
                login(player);
            }
        }
    }


    /**
     * Give tokens to a player
     *
     * @param uuid   UUID of the player
     * @param amount Amount of tokens to give
     */
    public void giveTokens(UUID uuid, double amount) {
        try {
            PreparedStatement ps = SQL.getConnection().prepareStatement("INSERT INTO player_tokens(uuid, tokens) VALUES(?, ?) ON DUPLICATE KEY UPDATE tokens = tokens + ?;");
            ps.setString(1, uuid.toString());
            ps.setDouble(2, amount);
            ps.setDouble(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            if (reconnect()) {
                giveTokens(uuid, amount);
            } else {
                Bukkit.getScheduler().runTaskLater(NotTokensPremium.getInstance(), () -> TokenManager.giveTokens(uuid, amount), 20L);
            }
        }
    }

    /**
     * Get the tokens of a player
     *
     * @param uuid UUID of the player
     * @return The number of tokens the player has
     */
    public double getTokens(UUID uuid) {
        try {
            PreparedStatement ps = SQL.getConnection().prepareStatement("SELECT tokens FROM player_tokens WHERE uuid = ?;");
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            double tokens;
            if (rs.next()) {
                tokens = rs.getDouble("tokens");
                return tokens;
            }
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            if (reconnect()) {
                return getTokens(uuid);
            }
        }
        return 0;
    }

    /**
     * Remove tokens from a player
     *
     * @param uuid   UUID of the player
     * @param amount Amount of tokens to remove
     */
    public void removeTokens(UUID uuid, double amount) {
        try {
            PreparedStatement ps = ConfigOptions.negativeTokens ? SQL.getConnection().prepareStatement("UPDATE player_tokens SET tokens = tokens - ? WHERE uuid = ?;") : SQL.getConnection().prepareStatement("UPDATE player_tokens SET tokens = tokens - ? WHERE uuid = ? AND tokens >= ?;");
            ps.setDouble(1, amount);
            ps.setString(2, uuid.toString());
            ps.setDouble(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            if (reconnect()) {
                removeTokens(uuid, amount);
            } else {
                Bukkit.getScheduler().runTaskLater(NotTokensPremium.getInstance(), () -> TokenManager.removeTokens(uuid, amount), 20L);
            }
        }
    }

    /**
     * Set the tokens of a player
     *
     * @param uuid   UUID of the player
     * @param amount Amount of tokens to be set as the new balance
     */
    public void setTokens(UUID uuid, double amount) {
        try {
            PreparedStatement ps = SQL.getConnection().prepareStatement("REPLACE player_tokens(uuid, tokens) VALUES(? ,?);");
            ps.setString(1, uuid.toString());
            ps.setDouble(2, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            if (reconnect()) {
                setTokens(uuid, amount);
            } else {
                Bukkit.getScheduler().runTaskLater(NotTokensPremium.getInstance(), () -> TokenManager.setTokens(uuid, amount), 20L);
            }
        }
    }

    /**
     * Get an ordered list of players with the top tokens
     *
     * @param results amount of players to get
     * @return A LinkedHashMap with the players with the most tokens in the front of the map
     */
    public LinkedHashMap<UUID, Double> getTopTokens(int results) {
        List<String> hiddenNames = ConfigOptions.leaderboardExclusion;

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < hiddenNames.size(); i++) {
            OfflinePlayer player = LoggedPlayers.getPlayer(hiddenNames.get(i));
            if (player == null) {
                builder = new StringBuilder();
                Bukkit.getLogger().warning("[NotTokensPremium] Error getting player: " + hiddenNames.get(i) + " from excluded players!");
                break;
            }
            String uuid = player.getUniqueId().toString();
            if (uuid == null)
                continue;
            if (i < hiddenNames.size() - 1)
                builder.append(uuid).append("' AND uuid != '");
            else
                builder.append(uuid);
        }

        try {
            PreparedStatement ps = SQL.getConnection().prepareStatement("SELECT uuid, tokens FROM player_tokens WHERE uuid != '" + builder + "' ORDER BY tokens DESC LIMIT ?;");
            ps.setInt(1, results);
            ResultSet rs = ps.executeQuery();
            LinkedHashMap<UUID, Double> tokens = new LinkedHashMap<>();
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                tokens.put(UUID.fromString(uuid), rs.getDouble("tokens"));
            }
            return tokens;
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            if (reconnect()) {
                return getTopTokens(results);
            }
        }
        return new LinkedHashMap<>();
    }

    /**
     * Remove extra 0's in the database
     *
     * @return The amount of entries that were changed
     */
    public int removeExtraData() {
        try {
            PreparedStatement ps = SQL.getConnection().prepareStatement("DELETE FROM player_tokens WHERE tokens = ?;");
            ps.setDouble(1, 0L);
            return ps.executeUpdate();
        } catch (SQLException e) {
            if (debug) {
                Bukkit.getLogger().warning(e.toString());
            }
            if (reconnect()) {
                return removeExtraData();
            }
        }
        return 0;
    }

    /**
     * Reconnect to the database. A maximum of 3 connection attempts can occur every 5 seconds.
     *
     * @return True if the database was reconnected
     */
    private boolean reconnect() {
        if (System.currentTimeMillis() > nextReconnectAttempt) {
            reconnectAttempts++;
            SQL.disconnect();
            if (reconnectAttempts < 2) {
                Bukkit.getLogger().warning("[NotTokensPremium] Lost connection with database, will try to reconnect.");
            }
            if (reconnectAttempts >= 3) {
                reconnectAttempts = 0;
                nextReconnectAttempt = System.currentTimeMillis() + 5000L;
            }

            if (!tryToConnect()) {
                if (reconnectAttempts < 2)
                    Bukkit.getScheduler().runTaskLater(NotTokensPremium.getInstance(), TokenManager::tryToConnect, 20L);
                return false;
            }
            return true;
        }
        return false;
    }


}
