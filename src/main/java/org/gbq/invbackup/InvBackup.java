package org.gbq.invbackup;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class InvBackup extends JavaPlugin {

    private String worldName;
    private boolean worldCheckEnabled;
    private int saveInterval;
    private int maxVersions;
    @Override
    public void onEnable() {
        createFoldersAndFiles();
        saveDefaultConfig();

        saveInterval = getConfig().getInt("save-interval", 600) * 20;
        worldCheckEnabled = getConfig().getBoolean("check-world", true);
        worldName = getConfig().getString("world-name", "world");
        maxVersions = getConfig().getInt("max-versions", 100);

        this.getCommand("invbackup").setTabCompleter(new InvBackupTabCompleter());

        getLogger().info("每 " + (saveInterval / 20) / 60 + " 分钟保存一次库存");
        getLogger().info("世界检查: " + worldCheckEnabled);
        getLogger().info("默认世界: " + worldName);
        getLogger().info("最大版本数量: " + maxVersions);
        startInventorySaveTask();
    }

    private void createFoldersAndFiles() {

        File inventoryFolder = new File(getDataFolder(), "物品栏");
        if (!inventoryFolder.exists()) {
            inventoryFolder.mkdirs();
        }

        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();

                FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                config.set("save-interval", 600);
                config.set("check-world", true);
                config.set("world-name", "world");
                config.set("max-versions", 100);
                config.save(configFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void startInventorySaveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getWorld().getName().equals(worldName)) {
                        savePlayerInventory(player);
                    }
                }
            }
        }.runTaskTimer(this, 0, saveInterval);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("invbackup.admin")) {
            sender.sendMessage(ChatColor.RED + "您没有权限执行此命令。");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.YELLOW + "用法: ");
            sender.sendMessage(ChatColor.AQUA + "/invbackup save <玩家> - " + ChatColor.WHITE + "保存指定玩家的物品栏。");
            sender.sendMessage(ChatColor.AQUA + "/invbackup saveall - " + ChatColor.WHITE + "保存所有在线玩家的物品栏。");
            sender.sendMessage(ChatColor.AQUA + "/invbackup restore <玩家> [版本] - " + ChatColor.WHITE + "恢复指定玩家的物品栏。如果不指定版本，将恢复最新的保存。");
            sender.sendMessage(ChatColor.AQUA + "/invbackup toggoworldcheck <true|false> - " + ChatColor.WHITE + "开启/关闭恢复物品栏时的世界检查。");
            sender.sendMessage(ChatColor.AQUA + "/invbackup setworld <世界名称> - " + ChatColor.WHITE + "设置检查物品栏保存的世界.");
            sender.sendMessage(ChatColor.AQUA + "/invbackup setinterval <时间_分钟> - " + ChatColor.WHITE + "设置检查物品栏保存的世界。");
            sender.sendMessage(ChatColor.AQUA + "/invbackup setmaxversions <数量> - " + ChatColor.WHITE + "设置保存的最大版本数量。");
            sender.sendMessage(ChatColor.AQUA + "/invbackup reload - " + ChatColor.WHITE + "重新加载插件配置");
            return true;
        }
        // Обработка команды saveall
        if (args[0].equalsIgnoreCase("保存全部")) {
            saveAllPlayerInventories(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("开关世界检查")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "用法: /invbackup toggleworldcheck <true|false>");
                return true;
            }
            worldCheckEnabled = Boolean.parseBoolean(args[1]);
            getConfig().set("check-world", worldCheckEnabled);
            saveConfig();
            sender.sendMessage(ChatColor.YELLOW +  "世界检查 " + (worldCheckEnabled ? "已启用" : "已禁用") + "。");
            return true;
        }

        if (args[0].equalsIgnoreCase("设置世界")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "用法: /invbackup setworld <世界名称>");
                return true;
            }
            worldName = args[1];
            getConfig().set("world-name", worldName);
            saveConfig();
            sender.sendMessage(ChatColor.YELLOW + "物品栏保存的世界已设置为: " + worldName);
            return true;
        }

        if (args[0].equalsIgnoreCase(" 设置时间间隔")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "用法: /invbackup setinterval <时间_分钟>");
                return true;
            }
            try {
                int intervalInMinutes = Integer.parseInt(args[1]);
                saveInterval = intervalInMinutes * 60 * 20; // Преобразование минут в тики
                getConfig().set("save-interval", intervalInMinutes * 60); // Сохраняем интервал в минутах
                saveConfig();
                startInventorySaveTask(); // Перезапуск задачи с новым интервалом
                sender.sendMessage(ChatColor.YELLOW + "物品栏保存的时间间隔已设置为: " + intervalInMinutes + " 分钟。");
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "时间必须是数字。");
            }
            return true;
        }

        // Установка максимального количества версий
        if (args[0].equalsIgnoreCase(" 设置最大版本数")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "用法: /invbackup setmaxversions <数量>");
                return true;
            }
            try {
                maxVersions = Integer.parseInt(args[1]);
                getConfig().set("max-versions", maxVersions);
                saveConfig();
                sender.sendMessage(ChatColor.YELLOW + "最大版本数已设置为: " + maxVersions);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "数量必须是数字。");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("重载配置")) {
            reloadPlugin(sender);
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /invbackup <save|restore> <玩家> [版本]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "玩家未找到！");
            return true;
        }

        if (args[0].equalsIgnoreCase("保存")) {
            savePlayerInventory(target);
            sender.sendMessage(ChatColor.GREEN + "玩家 " + target.getName() + " 的物品栏已保存。");
        } else if (args[0].equalsIgnoreCase("恢复")) {

            if (args.length >= 3) {
                try {
                    int version = Integer.parseInt(args[2]);
                    restorePlayerInventory(target, version);
                    sender.sendMessage(ChatColor.GREEN + "玩家 " + target.getName() + " 的物品栏已从版本 " + version + " 恢复。");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "版本必须是数字。");
                }
            } else {
                restorePlayerInventory(target, -1);
                sender.sendMessage(ChatColor.GREEN + "玩家 " + target.getName() + " 的物品栏已从最近的备份恢复。");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "无效的命令！");
        }
        return true;
    }
    private void saveAllPlayerInventories(CommandSender sender) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayerInventory(player);
        }
        sender.sendMessage(ChatColor.GREEN + "所有在线玩家的物品栏已保存。");
    }
    private void savePlayerInventory(Player player) {
        File file = new File(getDataFolder() + "/inventories", player.getName() + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        List<ItemStack> items = Arrays.asList(player.getInventory().getContents());
        List<ItemStack> armor = Arrays.asList(player.getInventory().getArmorContents());
        ItemStack offHand = player.getInventory().getItemInOffHand();
        String timeStamp = new SimpleDateFormat("yy-MM-dd HH:mm:ss").format(new Date());
        String worldName = player.getWorld().getName();

        int versionCount = config.getConfigurationSection("versions") == null ? 0 : config.getConfigurationSection("versions").getKeys(false).size();

        if (versionCount >= maxVersions) {
            config.set("versions.1", null);

            // Сдвигаем все версии вниз
            for (int i = 1; i < maxVersions; i++) {
                config.set("versions." + i, config.get("versions." + (i + 1)));
            }
            config.set("versions." + maxVersions, null);
        }

        int newVersion = Math.min(versionCount + 1, maxVersions);
        config.set("versions." + newVersion + ".items", items);
        config.set("versions." + newVersion + ".armor", armor);
        config.set("versions." + newVersion + ".offHand", offHand);
        config.set("versions." + newVersion + ".timestamp", timeStamp);
        config.set("versions." + newVersion + ".world", worldName);

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void reloadPlugin(CommandSender sender) {
        reloadConfig();
        createFoldersAndFiles();
        saveInterval = getConfig().getInt("save-interval", 200) * 20;
        worldCheckEnabled = getConfig().getBoolean("check-world", true);
        worldName = getConfig().getString("world-name", "world");
        maxVersions = getConfig().getInt("max-versions", 100);

        // Логирование загруженных параметров
        sender.sendMessage(ChatColor.GREEN + "插件成功重新加载！");
        getLogger().info("插件 InvBackup 重新加载。");
        getLogger().info("每 " + saveInterval + " 个刻保存物品栏");
        getLogger().info("世界检查: " + (worldCheckEnabled ? "已启用" : "已禁用"));
        getLogger().info("默认世界: " + worldName);
        getLogger().info("最大版本数: " + maxVersions);
    }

    private void restorePlayerInventory(Player player, int version) {
        File file = new File(getDataFolder() + "/inventories", player.getName() + ".yml");
        if (!file.exists()) {
            player.sendMessage(ChatColor.RED + "您没有保存的物品栏。");
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = (version == -1) ? "versions." + config.getConfigurationSection("versions").getKeys(false).size() : "versions." + version;

        if (!config.contains(path)) {
            player.sendMessage(ChatColor.RED + "指定的版本未找到。");
            return;
        }

        if (worldCheckEnabled) {
            String savedWorld = config.getString(path + ".world");
            if (!player.getWorld().getName().equals(savedWorld)) {
                player.sendMessage(ChatColor.RED + "这个物品栏是在另一个世界保存的: " + savedWorld);
                return;
            }
        }

        ItemStack[] items = config.getList(path + ".items").toArray(new ItemStack[0]);
        ItemStack[] armor = config.getList(path + ".armor").toArray(new ItemStack[0]);
        ItemStack offHand = config.getItemStack(path + ".offHand");

        player.getInventory().setContents(items);
        player.getInventory().setArmorContents(armor);
        player.getInventory().setItemInOffHand(offHand);

        player.sendMessage(ChatColor.GREEN + "物品栏已成功恢复。");
    }
}
