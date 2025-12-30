package com.quests;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class QuestPlugin extends JavaPlugin {

    private static Economy econ;

    private final Map<Integer, Quest> quests = new HashMap<>();
    private final Map<UUID, Integer> playerQuestId = new HashMap<>();
    private final Map<UUID, Integer> playerProgress = new HashMap<>();
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        // Vault setup (must be before giving money)
        if (!setupEconomy()) {
            getLogger().severe("Vault not found or no economy provider. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("QuestPlugin enabled with Vault support!");

        loadQuests();
        getServer().getPluginManager().registerEvents(new QuestListener(this), this);

        // Actionbar updater
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    if (playerQuestId.containsKey(uuid)) {
                        int id = playerQuestId.get(uuid);
                        int progress = playerProgress.getOrDefault(uuid, 0);
                        Quest q = quests.get(id);
                        if (q == null) continue;
                        double percentage = ((double) progress / q.amount) * 100.0;

                        StringBuilder bar = new StringBuilder();
                        int filled = (int) (percentage / 10.0);
                        for (int i = 1; i <= 10; i++) bar.append(i <= filled ? "█" : "░");

                        player.sendActionBar("⛏ " + progress + "/" + q.amount + " [" + bar + "]");
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @Override
    public void onDisable() {
        getLogger().info("QuestPlugin disabled.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    private void loadQuests() {
        int c = 1;
        while (c <= 25) { quests.put(c, new Quest(Material.COAL_ORE, 16 + c, 50 + (c * 5), "§aEasy")); c++; }
        c = 26;
        while (c <= 50) { quests.put(c, new Quest(Material.IRON_ORE, 24 + c, 150 + (c * 6), "§eMedium")); c++; }
        c = 51;
        while (c <= 75) { quests.put(c, new Quest(Material.GOLD_ORE, 20 + c, 300 + (c * 7), "§6Hard")); c++; }
        c = 76;
        while (c <= 100) { quests.put(c, new Quest(Material.DIAMOND_ORE, 8 + c/2, 600 + (c * 10), "§cVery Hard")); c++; }

        Bukkit.getLogger().info("Quests loaded: " + quests.size());
    }

    // Simple command: /quest (assign or show) /quest stats /quest abandon
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        UUID uuid = player.getUniqueId();
        playerStats.putIfAbsent(uuid, new PlayerStats());

        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if (sub.equals("abandon")) {
                if (playerQuestId.containsKey(uuid)) {
                    playerQuestId.remove(uuid);
                    playerProgress.remove(uuid);
                    player.sendMessage("§cQuest abandoned!");
                } else player.sendMessage("§cYou have no active quest!");
                return true;
            } else if (sub.equals("stats")) {
                PlayerStats st = playerStats.get(uuid);
                double rate = st.questsAssigned > 0 ? ((double)st.questsCompleted / st.questsAssigned) * 100.0 : 0.0;
                player.sendMessage("");
                player.sendMessage("§6§lQUEST STATISTICS");
                player.sendMessage("§fCompleted: §a" + st.questsCompleted + "  §fAssigned: §e" + st.questsAssigned);
                player.sendMessage("§fEarned: §6$" + st.totalMoneyEarned + "  §fRate: §b" + String.format("%.1f", rate) + "%");
                player.sendMessage("");
                return true;
            }
        }

        if (!playerQuestId.containsKey(uuid)) {
            int id = random.nextInt(quests.size()) + 1;
            playerQuestId.put(uuid, id);
            playerProgress.put(uuid, 0);
            playerStats.get(uuid).questsAssigned++;
            Quest q = quests.get(id);
            player.sendMessage("§6§lNEW QUEST: §fMine §b" + q.block.name() + " §f(" + q.amount + ")");
            player.sendMessage("§fReward: §6$" + q.reward + " §fDifficulty: " + q.difficulty);
        } else {
            int id = playerQuestId.get(uuid);
            Quest q = quests.get(id);
            int prog = playerProgress.getOrDefault(uuid, 0);
            player.sendMessage("§6§lACTIVE QUEST");
            player.sendMessage("§fObjective: §eMine §b" + q.block.name());
            player.sendMessage("§fProgress: §a" + prog + "/" + q.amount);
            player.sendMessage("§fReward: §6$" + q.reward);
        }
        return true;
    }

    // Called by listener when block broken
    public void onBlockBreak(Player player, Material block) {
        UUID uuid = player.getUniqueId();
        if (!playerQuestId.containsKey(uuid)) return;
        int id = playerQuestId.get(uuid);
        Quest q = quests.get(id);
        if (q == null) return;
        if (q.block != block) return;

        int progress = playerProgress.getOrDefault(uuid, 0) + 1;
        playerProgress.put(uuid, progress);

        // actionbar immediately handled by scheduled task
        if (progress >= q.amount) {
            // give money via Vault
            if (econ != null) {
                econ.depositPlayer(player, q.reward);
            }
            PlayerStats st = playerStats.get(uuid);
            st.questsCompleted++;
            st.totalMoneyEarned += q.reward;

            player.sendTitle("§a§lQUEST COMPLETE!", "§6+$" + q.reward, 10, 60, 10);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

            playerQuestId.remove(uuid);
            playerProgress.remove(uuid);

            // auto assign a new one after 3s
            Bukkit.getScheduler().runTaskLater(this, () -> {
                int newid = random.nextInt(quests.size()) + 1;
                playerQuestId.put(uuid, newid);
                playerProgress.put(uuid, 0);
                playerStats.get(uuid).questsAssigned++;
                Quest nq = quests.get(newid);
                player.sendMessage("");
                player.sendMessage("§6§lNEW QUEST ASSIGNED!");
                player.sendMessage("§fObjective: §eMine §b" + nq.block.name());
                player.sendMessage("§fAmount: §a" + nq.amount + " blocks");
                player.sendMessage("§fReward: §6$" + nq.reward);
                player.sendMessage("");
            }, 60L);
        }
    }

    // Helper classes
    private static class Quest {
        final Material block;
        final int amount;
        final int reward;
        final String difficulty;
        Quest(Material block, int amount, int reward, String difficulty) {
            this.block = block; this.amount = amount; this.reward = reward; this.difficulty = difficulty;
        }
    }

    private static class PlayerStats {
        int questsCompleted = 0;
        int questsAssigned = 0;
        int totalMoneyEarned = 0;
    }
}
