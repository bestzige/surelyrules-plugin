package dev.bestzige.surelyrules;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
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

    private static final Map<String, String> LEGACY_RULE_ALIASES;
    private static final Set<String> INVERTED_LEGACY_RULES;

    static {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("announce_advancements", "show_advancement_messages");
        aliases.put("do_daylight_cycle", "advance_time");
        aliases.put("do_weather_cycle", "advance_weather");
        aliases.put("do_mob_loot", "mob_drops");
        aliases.put("do_mob_spawning", "spawn_mobs");
        aliases.put("do_tile_drops", "block_drops");
        aliases.put("do_entity_drops", "entity_drops");
        aliases.put("do_fire_tick", "fire_spread_radius_around_player");
        aliases.put("allow_fire_ticks_away_from_player", "fire_spread_radius_around_player");
        aliases.put("natural_regeneration", "natural_health_regeneration");
        aliases.put("disable_elytra_movement_check", "elytra_movement_check");
        aliases.put("disable_raids", "raids");
        aliases.put("disable_player_movement_check", "player_movement_check");
        LEGACY_RULE_ALIASES = Map.copyOf(aliases);
        INVERTED_LEGACY_RULES = Set.of(
                "disable_elytra_movement_check",
                "disable_raids",
                "disable_player_movement_check"
        );
    }

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
            Map<String, Object> worldSettings = entry.getValue();

            if (worldName.matches(patternCache.get(pattern))) {
                rulesApplied = applyWorldSettingsFromConfig(world, worldSettings) || rulesApplied;
            }
        }

        if (rulesApplied) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[Surelyrules] Applied game rules to world: " + worldName);
        } else {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Surelyrules] No game rules matched for world: " + worldName);
        }
    }

    private boolean applyWorldSettingsFromConfig(World world, Map<String, Object> worldSettings) {
        boolean rulesApplied = false;

        for (Map.Entry<String, Object> entry : worldSettings.entrySet()) {
            String ruleName = entry.getKey();
            Object value = entry.getValue();

            if (ruleName.equalsIgnoreCase("difficulty")) {
                rulesApplied = applyDifficulty(world, value) || rulesApplied;
                continue;
            }

            String normalizedRuleName = normalizeRuleName(ruleName);
            Object effectiveValue = convertLegacyValue(normalizedRuleName, value);
            GameRule<?> gameRule = resolveGameRule(normalizedRuleName);

            if (gameRule != null) {
                try {
                    if (effectiveValue instanceof Boolean b) {
                        world.setGameRule((GameRule<Boolean>) gameRule, b);
                        rulesApplied = true;
                    } else if (effectiveValue instanceof Integer i) {
                        world.setGameRule((GameRule<Integer>) gameRule, i);
                        rulesApplied = true;
                    } else if (effectiveValue instanceof String s) {
                        world.setGameRule((GameRule<String>) gameRule, s);
                        rulesApplied = true;
                    } else {
                        getLogger().warning("Invalid value type for game rule: " + ruleName + ". Got: "
                                + (effectiveValue == null ? "null" : effectiveValue.getClass().getSimpleName()));
                    }
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Error applying game rule: " + ruleName + " with value: " + effectiveValue, e);
                }
            } else {
                getLogger().warning("Invalid game rule: " + ruleName);
            }
        }

        return rulesApplied;
    }

    private boolean applyDifficulty(World world, Object value) {
        if (!(value instanceof String configuredDifficulty)) {
            getLogger().warning("Invalid difficulty for world " + world.getName()
                    + ": expected peaceful, easy, normal, or hard, but got " + value);
            return false;
        }

        try {
            Difficulty difficulty = Difficulty.valueOf(configuredDifficulty.trim().toUpperCase(Locale.ROOT));
            world.setDifficulty(difficulty);
            return true;
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid difficulty for world " + world.getName() + ": " + configuredDifficulty
                    + ". Expected peaceful, easy, normal, or hard.");
            return false;
        }
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

    private GameRule<?> resolveGameRule(String normalizedRuleName) {
        String registryKey = LEGACY_RULE_ALIASES.getOrDefault(normalizedRuleName, normalizedRuleName);
        return Registry.GAME_RULE.get(NamespacedKey.minecraft(registryKey));
    }

    private Object convertLegacyValue(String normalizedRuleName, Object value) {
        if (value instanceof Boolean booleanValue) {
            if (normalizedRuleName.equals("do_fire_tick")) {
                return booleanValue ? 128 : 0;
            }
            if (INVERTED_LEGACY_RULES.contains(normalizedRuleName)) {
                return !booleanValue;
            }
        }
        return value;
    }

    private String normalizeRuleName(String ruleName) {
        if (ruleName.contains("_")) {
            return ruleName.toLowerCase(Locale.ROOT);
        }

        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < ruleName.length(); i++) {
            char c = ruleName.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                normalized.append('_');
            }
            normalized.append(Character.toLowerCase(c));
        }
        return normalized.toString();
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
