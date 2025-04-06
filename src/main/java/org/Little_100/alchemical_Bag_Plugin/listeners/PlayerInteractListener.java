package org.Little_100.alchemical_Bag_Plugin.listeners;

import org.Little_100.alchemical_Bag_Plugin.Alchemical_Bag_Plugin;
import org.Little_100.alchemical_Bag_Plugin.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.DyeColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import java.util.HashMap;
import java.util.Map;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.inventory.EquipmentSlot;

public class PlayerInteractListener implements Listener {

    private final Alchemical_Bag_Plugin plugin;
    private final InventoryManager inventoryManager; // 获取 InventoryManager 实例

    private static final Map<Color, DyeColor> BUKKIT_TO_DYE_COLOR = new HashMap<>();

    static {
        // 初始化颜色映射
        BUKKIT_TO_DYE_COLOR.put(Color.WHITE, DyeColor.WHITE);
        BUKKIT_TO_DYE_COLOR.put(Color.ORANGE, DyeColor.ORANGE);
        BUKKIT_TO_DYE_COLOR.put(Color.PURPLE, DyeColor.MAGENTA);
        BUKKIT_TO_DYE_COLOR.put(Color.AQUA, DyeColor.LIGHT_BLUE);
        BUKKIT_TO_DYE_COLOR.put(Color.YELLOW, DyeColor.YELLOW);
        BUKKIT_TO_DYE_COLOR.put(Color.LIME, DyeColor.LIME);
        BUKKIT_TO_DYE_COLOR.put(Color.FUCHSIA, DyeColor.PINK);
        BUKKIT_TO_DYE_COLOR.put(Color.GRAY, DyeColor.GRAY);
        BUKKIT_TO_DYE_COLOR.put(Color.SILVER, DyeColor.LIGHT_GRAY);
        BUKKIT_TO_DYE_COLOR.put(Color.BLUE, DyeColor.CYAN);
        BUKKIT_TO_DYE_COLOR.put(Color.RED, DyeColor.RED);
        BUKKIT_TO_DYE_COLOR.put(Color.BLACK, DyeColor.BLACK);
        BUKKIT_TO_DYE_COLOR.put(Color.fromRGB(102, 76, 51), DyeColor.BROWN);
        BUKKIT_TO_DYE_COLOR.put(Color.fromRGB(0, 128, 0), DyeColor.GREEN);
        BUKKIT_TO_DYE_COLOR.put(Color.fromRGB(128, 0, 128), DyeColor.PURPLE);
        BUKKIT_TO_DYE_COLOR.put(Color.fromRGB(51, 76, 178), DyeColor.BLUE);
    }

    // 默认颜色
    private static final Color DEFAULT_LEATHER_COLOR = Bukkit.getServer().getItemFactory().getDefaultLeatherColor();

    public PlayerInteractListener(Alchemical_Bag_Plugin plugin) {
        this.plugin = plugin;
        this.inventoryManager = plugin.getInventoryManager(); // 获取实例
    }

    public static String getBagColorIdentifier(Color color) {
        if (color == null) {
            return "DEFAULT"; // 如果颜色为 null，视为默认
        }
        // 检查是否是默认颜色
        if (color.equals(DEFAULT_LEATHER_COLOR)) {
            return "DEFAULT";
        }

        DyeColor dye = BUKKIT_TO_DYE_COLOR.get(color);
        if (dye != null) {
            return dye.name();
        }
        return "RGB_" + color.getRed() + "_" + color.getGreen() + "_" + color.getBlue();
    }

    public static ChatColor getChatColor(String colorIdentifier) {
        if (colorIdentifier == null || colorIdentifier.equals("DEFAULT") || colorIdentifier.startsWith("RGB_")) {
            return ChatColor.WHITE;
        }
        try {
            DyeColor dye = DyeColor.valueOf(colorIdentifier.toUpperCase());
            switch (dye) {
                case WHITE: return ChatColor.WHITE;
                case ORANGE: return ChatColor.GOLD;
                case MAGENTA: return ChatColor.LIGHT_PURPLE;
                case LIGHT_BLUE: return ChatColor.AQUA;
                case YELLOW: return ChatColor.YELLOW;
                case LIME: return ChatColor.GREEN;
                case PINK: return ChatColor.LIGHT_PURPLE;
                case GRAY: return ChatColor.DARK_GRAY;
                case LIGHT_GRAY: return ChatColor.GRAY;
                case CYAN: return ChatColor.DARK_AQUA;
                case PURPLE: return ChatColor.DARK_PURPLE;
                case BLUE: return ChatColor.BLUE;
                case BROWN: return ChatColor.DARK_RED;
                case GREEN: return ChatColor.DARK_GREEN;
                case RED: return ChatColor.RED;
                case BLACK: return ChatColor.BLACK;
                default: return ChatColor.WHITE;
            }
        } catch (IllegalArgumentException e) {
            return ChatColor.WHITE;
        }
    }

    // --- 处理右键方块/空气 ---
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 只处理主手的右键动作，避免副手或其他交互触发两次
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        // 只处理右键空气或方块的动作
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand(); // 获取主手物品

        // 检查物品是否是皮革马铠
        if (itemInHand != null && itemInHand.getType() == Material.LEATHER_HORSE_ARMOR) {
            // 阻止默认的交互行为 (比如给马穿戴，或者与方块交互)
            event.setCancelled(true);
            LeatherArmorMeta meta = (LeatherArmorMeta) itemInHand.getItemMeta();
            if (meta == null) {
                 plugin.getLogger().warning("玩家 " + player.getName() + " 手中的皮革马铠没有 MetaData？");
                 return;
            }

            // 获取颜色并转换为标识符
            Color bukkitColor = meta.getColor();
            String bagColorIdentifier = getBagColorIdentifier(bukkitColor);

            // 从数据库加载该颜色对应的背包内容
            ItemStack[] inventoryContents = plugin.getDatabaseManager().loadInventory(player.getUniqueId(), bagColorIdentifier);

            // 创建虚拟背包 (大箱子大小)
            String inventoryTitle = getChatColor(bagColorIdentifier) + bagColorIdentifier + " 炼金术袋子"; // 使用颜色代码
            // 防止标题过长
            if (inventoryTitle.length() > 32) {
                inventoryTitle = inventoryTitle.substring(0, 32);
            }
            Inventory bagInventory = Bukkit.createInventory(null, 54, inventoryTitle);

            // 将加载的物品放入虚拟背包
            bagInventory.setContents(inventoryContents);

            // 告知 InventoryManager 玩家打开了哪个背包
            inventoryManager.openBagInventory(player.getUniqueId(), bagColorIdentifier, bagInventory);

            // 为玩家打开虚拟背包
            player.openInventory(bagInventory);
        }
    }

    // --- **** 新增：处理右键实体 **** ---
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // 只处理主手的交互
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // 检查玩家是否正拿着皮革马铠右键实体
        if (itemInHand != null && itemInHand.getType() == Material.LEATHER_HORSE_ARMOR) {
            // 是皮革马铠，取消事件，阻止给实体穿戴
            event.setCancelled(true);
        }
    }
}
