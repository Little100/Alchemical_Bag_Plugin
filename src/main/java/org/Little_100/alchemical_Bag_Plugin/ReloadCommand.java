package org.Little_100.alchemical_Bag_Plugin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final Alchemical_Bag_Plugin plugin;

    public ReloadCommand(Alchemical_Bag_Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 检查权限
        if (!sender.hasPermission("alchemicalbag.reload")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令！");
            return true;
        }

        // 执行重载逻辑
        try {
            plugin.reloadConfig();

            plugin.unregisterRecipes();

            plugin.registerConfiguredRecipes();

            sender.sendMessage(ChatColor.GREEN + plugin.getName() + " 配置已成功重载！");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "重载配置时发生错误，请查看控制台日志！");
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "执行 /" + label + " 命令时出错：", e);
        }

        return true; // 命令已处理
    }
}