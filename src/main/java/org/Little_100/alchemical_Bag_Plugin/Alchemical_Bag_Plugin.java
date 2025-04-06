package org.Little_100.alchemical_Bag_Plugin;

import org.Little_100.alchemical_Bag_Plugin.listeners.PlayerInteractListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.DyeColor;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.configuration.ConfigurationSection;
import java.util.List;
import java.util.ArrayList;

public final class Alchemical_Bag_Plugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private InventoryManager inventoryManager;
    private final List<NamespacedKey> registeredRecipeKeys = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig(); // 保存或加载 config.yml

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().severe("数据库连接失败！插件将无法正常工作。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        databaseManager.initializeTable();

        inventoryManager = new InventoryManager(this);

        getServer().getPluginManager().registerEvents(new PlayerInteractListener(this), this);
        getServer().getPluginManager().registerEvents(inventoryManager, this);

        // --- 注册合成配方 (读取 config) ---
        registerConfiguredRecipes();

        this.getCommand("alchemicalbagreload").setExecutor(new ReloadCommand(this));

        getLogger().info("炼金术袋子插件已启用!");
    }

    @Override
    public void onDisable() {
        getLogger().info("正在关闭炼金术袋子插件...");
        if (inventoryManager != null) {
            inventoryManager.saveAllOpenInventories();
        }
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        // 移除合成配方
        unregisterRecipes();

        getLogger().info("炼金术袋子插件已禁用。");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    // --- 新的合成配方注册逻辑 ---

    public void registerConfiguredRecipes() {
        ConfigurationSection recipesSection = getConfig().getConfigurationSection("recipes");
        if (recipesSection == null) {
            getLogger().info("在 config.yml 中未找到 'recipes' 配置区域，不注册任何自定义配方。");
            return;
        }

        int registeredCount = 0;
        // 遍历 config 中定义的所有配方标识符 (如 "DEFAULT", "RED" 等)
        for (String colorIdentifier : recipesSection.getKeys(false)) {
            ConfigurationSection recipeConfig = recipesSection.getConfigurationSection(colorIdentifier);
            if (recipeConfig == null) continue; // 跳过无效配置

            // 检查该配方是否启用
            if (!recipeConfig.getBoolean("enabled", false)) { // 默认不启用
                getLogger().info("配方 '" + colorIdentifier + "' 在 config.yml 中被禁用。");
                continue;
            }

            // --- 读取配方细节 ---
            List<String> shapeList = recipeConfig.getStringList("shape");
            ConfigurationSection ingredientsSection = recipeConfig.getConfigurationSection("ingredients");

            // 验证配置是否完整
            if (shapeList.isEmpty() || ingredientsSection == null) {
                getLogger().warning("配方 '" + colorIdentifier + "' 的配置不完整 (缺少 shape 或 ingredients)，跳过注册。");
                continue;
            }

            ItemStack resultBag = createColoredBagItem(colorIdentifier); // 复用之前的创建物品逻辑
            if (resultBag == null) {
                getLogger().severe("无法为配方 '" + colorIdentifier + "' 创建结果物品！跳过注册。");
                continue;
            }

            NamespacedKey key = new NamespacedKey(this, "alchemicalbag_" + colorIdentifier.toLowerCase());

            ShapedRecipe recipe = new ShapedRecipe(key, resultBag);

            try {
                recipe.shape(shapeList.toArray(new String[0])); // 将 List<String> 转为 String[]
            } catch (IllegalArgumentException e) {
                getLogger().warning("配方 '" + colorIdentifier + "' 的 shape 配置无效: " + e.getMessage() + "，跳过注册。");
                continue;
            }

            boolean ingredientsValid = true;
            for (String ingredientKey : ingredientsSection.getKeys(false)) {
                if (ingredientKey.length() != 1) {
                    getLogger().warning("配方 '" + colorIdentifier + "' 的 ingredients 中包含无效的 Key '" + ingredientKey + "' (必须是单个字符)，跳过注册。");
                    ingredientsValid = false;
                    break;
                }
                char charKey = ingredientKey.charAt(0); // 获取字符 Key, e.g., 'L'

                String materialName = ingredientsSection.getString(ingredientKey);
                if (materialName == null || materialName.isEmpty()) {
                    getLogger().warning("配方 '" + colorIdentifier + "' 的 ingredient '" + charKey + "' 缺少材料名称，跳过注册。");
                    ingredientsValid = false;
                    break;
                }

                Material material = getMaterialFromString(materialName); // 将字符串转换为 Material
                if (material == null) {
                    getLogger().warning("配方 '" + colorIdentifier + "' 的 ingredient '" + charKey + "' 使用了无效的 Material 名称 '" + materialName + "'，跳过注册。");
                    ingredientsValid = false;
                    break;
                }

                // 设置配方成分
                recipe.setIngredient(charKey, material);
            }

            if (!ingredientsValid) {
                continue; // 如果材料设置失败，则不注册此配方
            }

            if (Bukkit.addRecipe(recipe)) {
                registeredRecipeKeys.add(key);
                registeredCount++;
            } else {
                getLogger().warning("无法注册 '" + colorIdentifier + "' 配方 (可能 Key 冲突或配方定义仍有问题?)");
            }
        }
        getLogger().info("从 config.yml 加载并注册了 " + registeredCount + " 个炼金术袋子配方。");
    }

    // 移除配方的逻辑
    public void unregisterRecipes() {
        getLogger().info("正在移除炼金术袋子自定义配方...");
        int count = 0;
        for (NamespacedKey key : registeredRecipeKeys) {
            if (Bukkit.removeRecipe(key)) {
                count++;
            }
        }
        getLogger().info("成功移除了 " + count + " 个炼金术袋子配方。");
        registeredRecipeKeys.clear();
    }

    // 创建物品的逻辑
    private ItemStack createColoredBagItem(String colorIdentifier) {
        ItemStack bag = new ItemStack(Material.LEATHER_HORSE_ARMOR);
        LeatherArmorMeta meta = (LeatherArmorMeta) bag.getItemMeta();
        if (meta == null) return null;

        Color bukkitColor;
        String displayName;

        if (colorIdentifier.equals("DEFAULT")) {
            bukkitColor = Bukkit.getServer().getItemFactory().getDefaultLeatherColor();
            displayName = "§f炼金术袋子"; // 白色名称
        } else if (colorIdentifier.startsWith("RGB_")) {
            getLogger().warning("尝试为 RGB 颜色 '" + colorIdentifier + "' 创建物品，这通常不应通过配方配置发生。");
            return null;
        } else {
            try {
                DyeColor dye = DyeColor.valueOf(colorIdentifier.toUpperCase());
                bukkitColor = dye.getColor();
                // 使用 PlayerInteractListener 的方法获取颜色代码和名称
                displayName = PlayerInteractListener.getChatColor(colorIdentifier) + colorIdentifier + " 炼金术袋子";
                if (displayName.length() > 40) {
                    displayName = displayName.substring(0, 40);
                }
            } catch (IllegalArgumentException e) {
                getLogger().severe("无法将配方标识符 '" + colorIdentifier + "' 解析为有效的颜色! 无法创建结果物品。");
                return null;
            }
        }

        meta.setColor(bukkitColor);
        meta.setDisplayName(displayName);
        bag.setItemMeta(meta);
        return bag;
    }

    private Material getMaterialFromString(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return null;
        }
        // 尝试直接匹配 Material 枚举名称 (忽略大小写)
        Material material = Material.matchMaterial(materialName.toUpperCase());
        if (material != null) {
            return material;
        }
        return null; // 找不到匹配的 Material
    }

    private Material getDyeMaterial(String colorIdentifier) {
        if (colorIdentifier == null || colorIdentifier.equals("DEFAULT") || colorIdentifier.startsWith("RGB_")) {
            return null;
        }
        try {
            DyeColor dye = DyeColor.valueOf(colorIdentifier.toUpperCase());
            switch (dye) {
                case WHITE: return Material.WHITE_DYE;
                case ORANGE: return Material.ORANGE_DYE;
                case MAGENTA: return Material.MAGENTA_DYE;
                case LIGHT_BLUE: return Material.LIGHT_BLUE_DYE;
                case YELLOW: return Material.YELLOW_DYE;
                case LIME: return Material.LIME_DYE;
                case PINK: return Material.PINK_DYE;
                case GRAY: return Material.GRAY_DYE;
                case LIGHT_GRAY: return Material.LIGHT_GRAY_DYE;
                case CYAN: return Material.CYAN_DYE;
                case PURPLE: return Material.PURPLE_DYE;
                case BLUE: return Material.BLUE_DYE;
                case BROWN: return Material.BROWN_DYE;
                case GREEN: return Material.GREEN_DYE;
                case RED: return Material.RED_DYE;
                case BLACK: return Material.BLACK_DYE;
                default: return null;
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}