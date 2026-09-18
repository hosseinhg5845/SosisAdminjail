package dev.sosis.adminjail;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Database {
    private final SosisAdminjailPlugin plugin;
    private Connection connection;
    private MongoClient mongoClient;
    private MongoCollection<Document> mongoCollection;
    private String storageType;

    public Database(SosisAdminjailPlugin plugin) {
        this.plugin = plugin;
    }

    public Connection getConnection() {
        return connection;
    }

    public void connect() throws SQLException {
        File storageFile = new File(plugin.getDataFolder(), "storage.yml");
        YamlConfiguration storageConfig = YamlConfiguration.loadConfiguration(storageFile);
        storageType = storageConfig.getString("storage-type", "H2");

        if (storageType.equalsIgnoreCase("MONGODB")) {
            String uri = storageConfig.getString("mongodb.uri", "mongodb://localhost:27017");
            String database = storageConfig.getString("mongodb.database", "adminjail");
            String collection = storageConfig.getString("mongodb.collection", "jailed_players");
            mongoClient = MongoClients.create(uri);
            MongoDatabase db = mongoClient.getDatabase(database);
            mongoCollection = db.getCollection(collection);
            plugin.getDebugLogger().info("Connected to MongoDB");
        } else if (storageType.equalsIgnoreCase("MYSQL")) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                try {
                    Class.forName("com.mysql.jdbc.Driver");
                } catch (ClassNotFoundException ignored) {}
            }
            String host = storageConfig.getString("mysql.host", "localhost");
            int port = storageConfig.getInt("mysql.port", 3306);
            String db = storageConfig.getString("mysql.database", "minecraft");
            String user = storageConfig.getString("mysql.username", "root");
            String pass = storageConfig.getString("mysql.password", "");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + db +
                    "?useSSL=false&autoReconnect=true&serverTimezone=UTC&allowPublicKeyRetrieval=true";

            connection = DriverManager.getConnection(url, user, pass);
            plugin.getDebugLogger().info("Connected to MySQL");
        } else {
            try {
                Class.forName("org.h2.Driver");
            } catch (ClassNotFoundException ignored) {}
            String path = plugin.getDataFolder().getAbsolutePath() + File.separator + "database";
            connection = DriverManager.getConnection("jdbc:h2:file:" + path + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
            plugin.getDebugLogger().info("Connected to H2");
        }

        if (!storageType.equalsIgnoreCase("MONGODB")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS jailed_players (uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(16), needed_blocks INT, broken_blocks INT, jail_name VARCHAR(32), reason TEXT, status VARCHAR(20), inventory BLOB)");
                stmt.execute("CREATE TABLE IF NOT EXISTS jail_locations (name VARCHAR(32) PRIMARY KEY, world VARCHAR(32), x DOUBLE, y DOUBLE, z DOUBLE, yaw FLOAT, pitch FLOAT)");
                stmt.execute("CREATE TABLE IF NOT EXISTS spawn_locations (name VARCHAR(32) PRIMARY KEY, world VARCHAR(32), x DOUBLE, y DOUBLE, z DOUBLE, yaw FLOAT, pitch FLOAT)");
            } catch (SQLException e) {
                plugin.getDebugLogger().warning("Failed to create tables: " + e.getMessage());
            }
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getDebugLogger().warning("Error disconnecting from database: " + e.getMessage());
        }
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    private byte[] serializeInventory(List<ItemStack> items) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(baos);
             BukkitObjectOutputStream bois = new BukkitObjectOutputStream(gzip)) {
            bois.writeObject(items.toArray(new ItemStack[0]));
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private List<ItemStack> deserializeInventory(byte[] data) {
        if (data == null || data.length == 0) {
            return new ArrayList<>();
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             GZIPInputStream gzip = new GZIPInputStream(bais);
             BukkitObjectInputStream bois = new BukkitObjectInputStream(gzip)) {
            Object obj = bois.readObject();
            if (obj instanceof ItemStack[]) {
                return new ArrayList<>(Arrays.asList((ItemStack[]) obj));
            }
            return new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveJailedPlayerSync(OfflineData data) {
        if (data == null) {
            return;
        }

        if (storageType.equalsIgnoreCase("MONGODB")) {
            try {
                Document doc = new Document("uuid", data.getUuid().toString())
                        .append("player_name", data.getPlayerName())
                        .append("needed_blocks", data.getNeededBlocks())
                        .append("broken_blocks", data.getBrokenBlocks())
                        .append("jail_name", data.getJailName())
                        .append("reason", data.getReason())
                        .append("status", data.getStatus() != null ? data.getStatus() : "jailed");
                mongoCollection.insertOne(doc);
            } catch (Exception e) {
                plugin.getDebugLogger().severe("Failed to save to MongoDB: " + e.getMessage());
            }
        } else {
            String sql = "INSERT INTO jailed_players (uuid, player_name, needed_blocks, broken_blocks, jail_name, reason, status, inventory) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE player_name=?, needed_blocks=?, broken_blocks=?, jail_name=?, reason=?, status=?, inventory=?";

            try {
                if (connection == null || connection.isClosed()) {
                    return;
                }

                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    byte[] invData = serializeInventory(data.getSavedInventory());
                    String status = data.getStatus() != null ? data.getStatus() : "jailed";
                    ps.setString(1, data.getUuid().toString());
                    ps.setString(2, data.getPlayerName());
                    ps.setInt(3, data.getNeededBlocks());
                    ps.setInt(4, data.getBrokenBlocks());
                    ps.setString(5, data.getJailName());
                    ps.setString(6, data.getReason());
                    ps.setString(7, status);
                    ps.setBytes(8, invData);
                    ps.setString(9, data.getPlayerName());
                    ps.setInt(10, data.getNeededBlocks());
                    ps.setInt(11, data.getBrokenBlocks());
                    ps.setString(12, data.getJailName());
                    ps.setString(13, data.getReason());
                    ps.setString(14, status);
                    ps.setBytes(15, invData);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getDebugLogger().severe("Failed to save player: " + e.getMessage());
            }
        }
    }

    public void saveJailedPlayer(OfflineData data) {
        if (data == null) {
            return;
        }

        if (Bukkit.isPrimaryThread()) {
            saveJailedPlayerSync(data);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            saveJailedPlayerSync(data);
        });
    }

    public void removeJailedPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }

        if (storageType.equalsIgnoreCase("MONGODB")) {
            try {
                mongoCollection.deleteOne(new Document("uuid", uuid.toString()));
            } catch (Exception e) {
                plugin.getDebugLogger().severe("Failed to remove from MongoDB: " + e.getMessage());
            }
        } else {
            try {
                if (connection == null || connection.isClosed()) {
                    return;
                }
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM jailed_players WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getDebugLogger().severe("Failed to remove player: " + e.getMessage());
            }
        }
    }

    public void removeJailedPlayerByName(String name) {
        if (name == null || name.isEmpty()) {
            return;
        }

        if (storageType.equalsIgnoreCase("MONGODB")) {
            try {
                mongoCollection.deleteOne(new Document("player_name", name));
            } catch (Exception e) {
                plugin.getDebugLogger().severe("Failed to remove from MongoDB: " + e.getMessage());
            }
        } else {
            try {
                if (connection == null || connection.isClosed()) {
                    return;
                }
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM jailed_players WHERE player_name = ?")) {
                    ps.setString(1, name);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getDebugLogger().severe("Failed to remove player: " + e.getMessage());
            }
        }
    }

    public Map<String, OfflineData> loadAllJailedPlayers() {
        Map<String, OfflineData> result = new HashMap<>();

        if (storageType.equalsIgnoreCase("MONGODB")) {
            try {
                for (Document doc : mongoCollection.find()) {
                    try {
                        String status = doc.getString("status");
                        OfflineData data = new OfflineData(
                                UUID.fromString(doc.getString("uuid")),
                                doc.getString("player_name"),
                                new ArrayList<>(),
                                doc.getInteger("needed_blocks"),
                                doc.getInteger("broken_blocks"),
                                doc.getString("jail_name"),
                                doc.getString("reason"),
                                status != null ? status : "jailed"
                        );
                        result.put(doc.getString("player_name").toLowerCase(), data);
                    } catch (Exception e) {
                        plugin.getDebugLogger().warning("Failed to load player from MongoDB: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                plugin.getDebugLogger().severe("Failed to load from MongoDB: " + e.getMessage());
            }
        } else {
            try {
                if (connection == null || connection.isClosed()) {
                    return result;
                }
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM jailed_players")) {
                    while (rs.next()) {
                        try {
                            String status = rs.getString("status");
                            if (status == null) {
                                status = "jailed";
                            }
                            OfflineData data = new OfflineData(
                                    UUID.fromString(rs.getString("uuid")),
                                    rs.getString("player_name"),
                                    deserializeInventory(rs.getBytes("inventory")),
                                    rs.getInt("needed_blocks"),
                                    rs.getInt("broken_blocks"),
                                    rs.getString("jail_name"),
                                    rs.getString("reason"),
                                    status
                            );
                            result.put(rs.getString("player_name").toLowerCase(), data);
                        } catch (Exception e) {
                            plugin.getDebugLogger().warning("Failed to load player: " + e.getMessage());
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getDebugLogger().severe("Failed to load players: " + e.getMessage());
            }
        }

        return result;
    }

    public void saveJailLocation(String name, Location loc) {
        if (name == null || name.isEmpty() || loc == null || storageType.equalsIgnoreCase("MONGODB")) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO jail_locations (name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE world=?, x=?, y=?, z=?, yaw=?, pitch=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, loc.getWorld().getName());
                ps.setDouble(3, loc.getX());
                ps.setDouble(4, loc.getY());
                ps.setDouble(5, loc.getZ());
                ps.setFloat(6, loc.getYaw());
                ps.setFloat(7, loc.getPitch());
                ps.setString(8, loc.getWorld().getName());
                ps.setDouble(9, loc.getX());
                ps.setDouble(10, loc.getY());
                ps.setDouble(11, loc.getZ());
                ps.setFloat(12, loc.getYaw());
                ps.setFloat(13, loc.getPitch());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getDebugLogger().severe("Failed to save jail location: " + e.getMessage());
            }
        });
    }

    public void deleteJailLocation(String name) {
        if (name == null || name.isEmpty() || storageType.equalsIgnoreCase("MONGODB")) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM jail_locations WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getDebugLogger().severe("Failed to delete jail location: " + e.getMessage());
            }
        });
    }

    public Map<String, Location> loadAllJailLocations() {
        Map<String, Location> result = new HashMap<>();

        if (!storageType.equalsIgnoreCase("MONGODB")) {
            try {
                if (connection == null || connection.isClosed()) {
                    return result;
                }
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM jail_locations")) {
                    while (rs.next()) {
                        try {
                            World w = Bukkit.getWorld(rs.getString("world"));
                            if (w != null) {
                                Location loc = new Location(
                                        w,
                                        rs.getDouble("x"),
                                        rs.getDouble("y"),
                                        rs.getDouble("z"),
                                        rs.getFloat("yaw"),
                                        rs.getFloat("pitch")
                                );
                                result.put(rs.getString("name"), loc);
                            }
                        } catch (Exception e) {
                            plugin.getDebugLogger().warning("Failed to load jail location: " + e.getMessage());
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getDebugLogger().severe("Failed to load jail locations: " + e.getMessage());
            }
        }

        return result;
    }

    public void saveSpawnLocation(String name, Location loc) {
        if (name == null || name.isEmpty() || loc == null || storageType.equalsIgnoreCase("MONGODB")) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO spawn_locations (name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE world=?, x=?, y=?, z=?, yaw=?, pitch=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, loc.getWorld().getName());
                ps.setDouble(3, loc.getX());
                ps.setDouble(4, loc.getY());
                ps.setDouble(5, loc.getZ());
                ps.setFloat(6, loc.getYaw());
                ps.setFloat(7, loc.getPitch());
                ps.setString(8, loc.getWorld().getName());
                ps.setDouble(9, loc.getX());
                ps.setDouble(10, loc.getY());
                ps.setDouble(11, loc.getZ());
                ps.setFloat(12, loc.getYaw());
                ps.setFloat(13, loc.getPitch());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getDebugLogger().severe("Failed to save spawn location: " + e.getMessage());
            }
        });
    }

    public void deleteSpawnLocation(String name) {
        if (name == null || name.isEmpty() || storageType.equalsIgnoreCase("MONGODB")) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM spawn_locations WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getDebugLogger().severe("Failed to delete spawn location: " + e.getMessage());
            }
        });
    }

    public Map<String, Location> loadAllSpawnLocations() {
        Map<String, Location> result = new HashMap<>();

        if (!storageType.equalsIgnoreCase("MONGODB")) {
            try {
                if (connection == null || connection.isClosed()) {
                    return result;
                }
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM spawn_locations")) {
                    while (rs.next()) {
                        try {
                            World w = Bukkit.getWorld(rs.getString("world"));
                            if (w != null) {
                                Location loc = new Location(
                                        w,
                                        rs.getDouble("x"),
                                        rs.getDouble("y"),
                                        rs.getDouble("z"),
                                        rs.getFloat("yaw"),
                                        rs.getFloat("pitch")
                                );
                                result.put(rs.getString("name"), loc);
                            }
                        } catch (Exception e) {
                            plugin.getDebugLogger().warning("Failed to load spawn location: " + e.getMessage());
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getDebugLogger().severe("Failed to load spawn locations: " + e.getMessage());
            }
        }

        return result;
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public String getStorageType() {
        return storageType;
    }
}