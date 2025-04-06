package org.Little_100.alchemical_Bag_Plugin;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private final Alchemical_Bag_Plugin plugin;
    private Connection connection;
    private final String dbName = "data.db"; // 数据库文件名
    private final String dbPath;

    // 表名和列名保持不变
    private final String tableName = "player_bags";
    private final String uuidColumn = "player_uuid";
    private final String colorColumn = "bag_color";
    private final String inventoryColumn = "inventory_contents";

    public DatabaseManager(Alchemical_Bag_Plugin plugin) {
        this.plugin = plugin;
        // 计算数据库文件的完整路径
        this.dbPath = plugin.getDataFolder().getAbsolutePath() + File.separator + dbName;
    }

    // 建立 SQLite 连接
    public boolean connect() {
        try {
            // 如果已有连接且有效，则不重新连接
            if (connection != null && !connection.isClosed()) {
                return true;
            }

            // 确保插件数据文件夹存在
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs(); // 如果不存在则创建文件夹
            }

            // 加载 SQLite JDBC 驱动

            String jdbcUrl = "jdbc:sqlite:" + dbPath; // SQLite 的 JDBC URL

            connection = DriverManager.getConnection(jdbcUrl);
            plugin.getLogger().info("成功连接到 SQLite 数据库: " + dbPath);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "无法连接到 SQLite 数据库!", e);
            return false;
        }
    }

    // 关闭数据库连接
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("已断开与 SQLite 数据库的连接。");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "关闭 SQLite 连接时出错!", e);
            }
        }
    }

    // 获取数据库连接
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect(); // 尝试重新连接
        }
        if (connection == null || connection.isClosed()) {
            throw new SQLException("无法获取 SQLite 数据库连接。");
        }
        return connection;
    }

    // 初始化数据库表
    public void initializeTable() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                uuidColumn + " TEXT NOT NULL, " + // UUID 通常存储为 TEXT
                colorColumn + " TEXT NOT NULL, " +
                inventoryColumn + " TEXT, " +
                "PRIMARY KEY (" + uuidColumn + ", " + colorColumn + ")" +
                ");";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) { // 使用 Statement 即可
            stmt.execute(createTableSQL);
            plugin.getLogger().info("SQLite 数据库表 '" + tableName + "' 初始化成功。");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "初始化 SQLite 数据库表 '" + tableName + "' 时出错!", e);
        }
    }

    // --- 物品数据加载与保存 ---

    public static String itemStackArrayToBase64(ItemStack[] items) throws IllegalStateException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("无法将 ItemStack[] 序列化为 Base64!", e);
        }
    }

    public static ItemStack[] itemStackArrayFromBase64(String data) throws IOException {
        if (data == null || data.isEmpty()) {
            return new ItemStack[54]; // 大箱子大小
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];
            if (size < 54) {
                ItemStack[] resizedItems = new ItemStack[54];
                for (int i = 0; i < size; i++) {
                    items[i] = (ItemStack) dataInput.readObject();
                    if (i < 54) {
                        resizedItems[i] = items[i];
                    }
                }
                return resizedItems;
            } else {
                for (int i = 0; i < size; i++) {
                    items[i] = (ItemStack) dataInput.readObject();
                }
                return items;
            }

        } catch (ClassNotFoundException e) {
            throw new IOException("无法找到 ItemStack 类进行反序列化!", e);
        } catch (IOException e) {
            throw new IOException("反序列化 Base64 数据时出错!", e);
        }
    }


    // 从数据库加载
    public ItemStack[] loadInventory(UUID playerUUID, String bagColor) {
        String selectSQL = "SELECT " + inventoryColumn + " FROM " + tableName +
                " WHERE " + uuidColumn + " = ? AND " + colorColumn + " = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, playerUUID.toString());
            pstmt.setString(2, bagColor);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String base64Data = rs.getString(inventoryColumn);
                return itemStackArrayFromBase64(base64Data);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "从 SQLite 加载背包数据时出错 (UUID: " + playerUUID + ", Color: " + bagColor + ")", e);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "反序列化 SQLite 背包数据时出错 (UUID: " + playerUUID + ", Color: " + bagColor + ")", e);
        }
        return new ItemStack[54];
    }

    // 保存到数据库
    public void saveInventory(UUID playerUUID, String bagColor, ItemStack[] items) {
        if (items == null) return;

        String base64Data;
        try {
            ItemStack[] itemsToSave = items;
            if (items.length != 54) {
                itemsToSave = new ItemStack[54];
                System.arraycopy(items, 0, itemsToSave, 0, Math.min(items.length, 54));
            }
            base64Data = itemStackArrayToBase64(itemsToSave);
        } catch (IllegalStateException e) {
            plugin.getLogger().log(Level.SEVERE, "序列化背包数据时出错 (UUID: " + playerUUID + ", Color: " + bagColor + ")", e);
            return;
        }

        String upsertSQL = "INSERT INTO " + tableName + " (" + uuidColumn + ", " + colorColumn + ", " + inventoryColumn + ") " +
                "VALUES (?, ?, ?) " +
                "ON CONFLICT(" + uuidColumn + ", " + colorColumn + ") DO UPDATE SET " +
                inventoryColumn + " = excluded." + inventoryColumn + ";"; // excluded 引用 VALUES 中的值

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(upsertSQL)) {

            pstmt.setString(1, playerUUID.toString());
            pstmt.setString(2, bagColor);
            pstmt.setString(3, base64Data);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "保存背包数据到 SQLite 时出错 (UUID: " + playerUUID + ", Color: " + bagColor + ")", e);
        }
    }
}