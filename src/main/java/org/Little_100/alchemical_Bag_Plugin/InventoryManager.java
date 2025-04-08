package org.Little_100.alchemical_Bag_Plugin;

import org.Little_100.alchemical_Bag_Plugin.listeners.PlayerInteractListener;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class InventoryManager implements Listener {

    private final Alchemical_Bag_Plugin plugin;
    // 存储玩家 UUID 和他们当前打开的虚拟背包
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    // 存储玩家 UUID 和他们打开的背包颜色标识符
    private final Map<UUID, String> openBagColors = new HashMap<>();


    public InventoryManager(Alchemical_Bag_Plugin plugin) {
        this.plugin = plugin;
    }

    // 当玩家通过 PlayerInteractListener 打开背包时调用此方法
    public void openBagInventory(UUID playerUUID, String bagColor, Inventory inventory) {
        openInventories.put(playerUUID, inventory);
        openBagColors.put(playerUUID, bagColor);
    }

    public void handleInventoryClose(UUID playerUUID) {
        Inventory closedInventory = openInventories.remove(playerUUID);
        String closedBagColor = openBagColors.remove(playerUUID);
        if (closedInventory != null && closedBagColor != null) {
            plugin.getDatabaseManager().saveInventory(playerUUID, closedBagColor, closedInventory.getContents());
            plugin.debugLog("玩家 " + playerUUID + " 的 " + closedBagColor + " 背包已通过 handleInventoryClose 保存。"); // 调试信息
        }
    }

    // 插件禁用时保存所有打开的背包
    public void saveAllOpenInventories() {
        plugin.debugLog("正在保存所有打开的炼金术袋子...");
        Map<UUID, Inventory> inventoriesToSave = new HashMap<>(openInventories);
        for (Map.Entry<UUID, Inventory> entry : inventoriesToSave.entrySet()) {
            UUID uuid = entry.getKey();
            Inventory inv = entry.getValue();
            String color = openBagColors.get(uuid);
            if (color != null) {
                plugin.getDatabaseManager().saveInventory(uuid, color, inv.getContents());
                plugin.debugLog("已保存玩家 " + uuid + " 的 " + color + " 背包 (插件禁用)。");
            } else {
                plugin.getLogger().warning("无法找到玩家 " + uuid + " 打开背包的颜色信息，无法保存！(插件禁用)");
            }
        }
        openInventories.clear();
        openBagColors.clear();
        plugin.debugLog("所有打开的炼金术袋子保存完毕 (插件禁用)。");
    }

    // --- Bukkit 事件监听器方法 ---

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Inventory closedInventory = event.getInventory(); // 获取关闭的背包

        if (openInventories.containsKey(playerUUID) && openInventories.get(playerUUID) == closedInventory) {
            String closedBagColor = openBagColors.get(playerUUID); // 获取对应的颜色

            openInventories.remove(playerUUID);
            openBagColors.remove(playerUUID);

            if (closedBagColor != null) {
                // 保存背包内容到数据库
                plugin.getDatabaseManager().saveInventory(playerUUID, closedBagColor, closedInventory.getContents());
                plugin.debugLog("玩家 " + player.getName() + " 关闭了 " + closedBagColor + " 炼金术袋子，内容已保存。");
            } else {
                plugin.getLogger().warning("玩家 " + player.getName() + " 关闭了一个炼金术袋子，但无法找到其颜色信息！");
            }
        }
        // 如果关闭的不是我们管理的背包，则忽略
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();
        Inventory clickedInventory = event.getInventory(); // 获取被点击的背包

        // 检查被点击的背包是否是炼金术袋子
        if (openInventories.containsKey(playerUUID) && openInventories.get(playerUUID) == clickedInventory) {
            // 是炼金术袋子，检查是否尝试放入同色袋子

            String currentBagColor = openBagColors.get(playerUUID);
            if (currentBagColor == null) {
                event.setCancelled(true);
                plugin.getLogger().warning("玩家 " + player.getName() + " 在炼金术袋子中点击，但找不到当前背包颜色！");
                return;
            }

            // 获取玩家光标上的物品 (尝试放入的物品)
            ItemStack cursorItem = event.getCursor();
            // 获取被点击的槽位中的物品 (如果玩家是 shift 点击放入)
            ItemStack currentItem = event.getCurrentItem();

            // --- 检查放入操作 (光标上有物品，点击空格子) ---
            if (cursorItem != null && cursorItem.getType() == Material.LEATHER_HORSE_ARMOR && event.getClickedInventory() == clickedInventory) {
                if (isSameColorBag(cursorItem, currentBagColor)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "你不能将相同颜色的炼金术袋子放入其中！");
                    return; // 阻止放入
                }
            }

            // --- 检查 Shift 点击操作 (从玩家背包移动到炼金术袋子) ---
            if (event.isShiftClick() && event.getClickedInventory() != clickedInventory && currentItem != null && currentItem.getType() == Material.LEATHER_HORSE_ARMOR) {
                // 检查要移动的物品 (currentItem) 是否是同色袋子
                if (isSameColorBag(currentItem, currentBagColor)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "你不能将相同颜色的炼金术袋子放入其中！");
                    return; // 阻止放入
                }
            }

            // --- 检查数字键交换操作 (按下数字键，将物品从快捷栏换入) ---
            if (event.getAction().name().contains("HOTBAR") && event.getClickedInventory() == clickedInventory) {
                // 获取热键对应的物品
                ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                if (hotbarItem != null && hotbarItem.getType() == Material.LEATHER_HORSE_ARMOR) {
                    if (isSameColorBag(hotbarItem, currentBagColor)) {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.RED + "你不能将相同颜色的炼金术袋子放入其中！");
                        return; // 阻止放入
                    }
                }
            }

            // 如果需要完全阻止拖拽，最好监听 InventoryDragEvent
            if (event.getAction().name().contains("DROP") || event.getAction().name().contains("PICKUP")) {
                // 检查光标或当前物品
                if (cursorItem != null && cursorItem.getType() == Material.LEATHER_HORSE_ARMOR && isSameColorBag(cursorItem, currentBagColor)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "你不能将相同颜色的炼金术袋子放入其中！");
                    return;
                }
            }


        }
    }

    private boolean isSameColorBag(ItemStack item, String targetColorIdentifier) {
        if (item == null || item.getType() != Material.LEATHER_HORSE_ARMOR || !item.hasItemMeta()) {
            return false; // 不是有效的皮革马铠
        }
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta == null) return false;

        String itemColorIdentifier = PlayerInteractListener.getBagColorIdentifier(meta.getColor());
        return itemColorIdentifier.equals(targetColorIdentifier);
    }
}
