package com.quests;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class QuestListener implements Listener {

    private final QuestPlugin plugin;

    public QuestListener(QuestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        plugin.onBlockBreak(e.getPlayer(), e.getBlock().getType());
    }
}
