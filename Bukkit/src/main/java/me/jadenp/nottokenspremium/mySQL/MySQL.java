package me.jadenp.nottokenspremium.mySQL;

import me.jadenp.nottokenspremium.NotTokensPremium;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MySQL {
    private Connection connection;

    private String host;
    private String port;
    private String database;
    private String username;
    private String password;
    private boolean useSSL;
    private boolean migrateLocalData;
    private int serverID;

    public MySQL(){
        readConfig();
    }

    private void readConfig(){
        Plugin plugin = NotTokensPremium.getInstance();
        host = (plugin.getConfig().isSet("database.host") ? plugin.getConfig().getString("database.host") : "localhost");
        port = (plugin.getConfig().isSet("database.port") ? plugin.getConfig().getString("database.port") : "3306");
        database = (plugin.getConfig().isSet("database.database") ? plugin.getConfig().getString("database.database") : "db");
        username = (plugin.getConfig().isSet("database.user") ? plugin.getConfig().getString("database.user") : "user");
        password = (plugin.getConfig().isSet("database.password") ? plugin.getConfig().getString("database.password") : "");
        useSSL = (plugin.getConfig().isSet("database.use-ssl") && plugin.getConfig().getBoolean("database.use-ssl"));
        migrateLocalData = plugin.getConfig().isSet("database.migrate-local-data") && plugin.getConfig().getBoolean("database.migrate-local-data");
        serverID = plugin.getConfig().isSet("database.server-id") ? plugin.getConfig().getInt("database.server-id") : 1;
    }

    public boolean isMigrateLocalData() {
        return migrateLocalData;
    }

    public int getServerID() {
        return serverID;
    }

    public String getDatabase() {
        return database;
    }

    public void reconnect() throws SQLException {
        if (isConnected())
            connection.close();
        connection = null;
        connect();
    }

    public boolean isConnected() {
        return connection != null;
    }

    public void connect() throws SQLException {
        if (!isConnected())
            connection = DriverManager.getConnection("jdbc:mysql://" +
                            host + ":" + port + "/" + database + "?useSSL=" + useSSL,
                    username, password);
    }

    public void disconnect(){
        if (isConnected())
            try {
                connection.close();
            } catch (SQLException e) {
                Bukkit.getLogger().warning(e.toString());
            }
        connection = null;
    }

    public Connection getConnection() {
        return connection;
    }


}
