package dev.bestzige.surelyrules;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

public class WorldLoadListener implements Listener {
    private final Surelyrules plugin;

    public WorldLoadListener(Surelyrules plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        this.plugin.applyGameRulesToWorld(event.getWorld());
    }
}
