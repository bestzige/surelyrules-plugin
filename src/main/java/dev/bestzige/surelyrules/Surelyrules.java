package dev.bestzige.surelyrules;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class Surelyrules extends JavaPlugin {

    private final Map<String, Map<String, Object>> cachedWorldConfigs = new ConcurrentHashMap<>();
    private final Map<String, String> patternCache = new HashMap<>();

    @Override
    public void onEnable() {
        loadConfig();
        cacheConfiguration();
        loadListeners();
        applyGameRulesToAllWorlds();
        getLogger().info("Surelyrules plugin enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Surelyrules plugin disabled.");
    }

    private void loadConfig() {
        saveDefaultConfig();
        reloadConfig();
    }

    private void loadListeners() {
        getServer().getPluginManager().registerEvents(new WorldLoadListener(this), this);
    }

    public void applyGameRulesToAllWorlds() {
        for (World world : Bukkit.getWorlds()) {
            applyGameRulesToWorld(world);
        }
    }

    public void applyGameRulesToWorld(World world) {
        String worldName = world.getName();

        boolean rulesApplied = false;
        for (Map.Entry<String, Map<String, Object>> entry : cachedWorldConfigs.entrySet()) {
            String pattern = entry.getKey();
            Map<String, Object> gameRules = entry.getValue();

            if (worldName.matches(patternCache.get(pattern))) {
                rulesApplied = applyGameRulesFromConfig(world, gameRules) || rulesApplied;
            }
        }

        if (rulesApplied) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[Surelyrules] Applied game rules to world: " + worldName);
        } else {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Surelyrules] No game rules matched for world: " + worldName);
        }
    }

    private boolean applyGameRulesFromConfig(World world, Map<String, Object> gameRules) {
        boolean rulesApplied = false;

        for (Map.Entry<String, Object> entry : gameRules.entrySet()) {
            String ruleName = entry.getKey();
            Object value = entry.getValue();
            GameRule<?> gameRule = GameRule.getByName(ruleName);

            if (gameRule != null) {
                try {
                    switch (value) {
                        case Boolean b when gameRule.getType() == Boolean.class ->
                                world.setGameRule((GameRule<Boolean>) gameRule, b);
                        case Integer i when gameRule.getType() == Integer.class ->
                                world.setGameRule((GameRule<Integer>) gameRule, i);
                        case String s when gameRule.getType() == String.class ->
                                world.setGameRule((GameRule<String>) gameRule, s);
                        case null, default -> {
                            assert value != null;
                            getLogger().warning("Invalid value type for game rule: " + ruleName + ". Expected: "
                                    + gameRule.getType().getSimpleName() + ", but got: " + value.getClass().getSimpleName());
                            continue;
                        }
                    }
                    rulesApplied = true;
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Error applying game rule: " + ruleName + " with value: " + value, e);
                }
            } else {
                getLogger().warning("Invalid game rule: " + ruleName);
            }
        }

        return rulesApplied;
    }

    private void cacheConfiguration() {
        cachedWorldConfigs.clear();
        patternCache.clear();

        FileConfiguration config = getConfig();
        ConfigurationSection worldsSection = config.getConfigurationSection("worlds");

        if (worldsSection == null) {
            worldsSection = config.createSection("worlds");
        }

        for (String pattern : worldsSection.getKeys(false)) {
            ConfigurationSection worldSection = worldsSection.getConfigurationSection(pattern);
            if (worldSection != null) {
                cachedWorldConfigs.put(pattern, worldSection.getValues(false));
                patternCache.put(pattern, convertWildcardToRegex(pattern));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("surelyrules")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.GREEN + "Surelyrules plugin by BestZige.");
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                cacheConfiguration();
                applyGameRulesToAllWorlds();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded and game rules applied.");
                return true;
            }

            sender.sendMessage(ChatColor.RED + "Invalid command.");
            return true;
        }
        return false;
    }

    private String convertWildcardToRegex(String wildcard) {
        StringBuilder sb = new StringBuilder();
        for (char c : wildcard.toCharArray()) {
            switch (c) {
                case '*':
                    sb.append(".*");
                    break;
                case '?':
                    sb.append(".");
                    break;
                default:
                    if ("\\.[]{}()^$|/+".indexOf(c) >= 0) {
                        sb.append("\\");
                    }
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }
}