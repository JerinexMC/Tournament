package com.casualsmp.tournament;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TournamentGui implements InventoryHolder {

    private final CasualTournament plugin;
    private Inventory lastInventory;

    public TournamentGui(CasualTournament plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return lastInventory;
    }

    public void openMainMenu(Player player) {
        String title = plugin.getConfig().getString("gui.main-title", "&6&lCasualTournament Menu");
        int size = plugin.getConfig().getInt("gui.size", 27);
        if (size < 9 || size > 54 || size % 9 != 0) {
            size = 27;
        }
        Inventory inv = Bukkit.createInventory(this, size, MessageUtil.colorize(title));
        this.lastInventory = inv;

        Tournament t = plugin.getTournamentManager().getCurrentTournament();
        Team playerTeam = plugin.getTeamManager().getTeamOfPlayer(player.getUniqueId());
        StatisticsManager.PlayerStats stats = plugin.getStatisticsManager().getOrCreateStats(player.getUniqueId());

        List<String> tInfoLore = new ArrayList<>();
        if (t == null) {
            tInfoLore.add("&7No active tournament.");
        } else {
            tInfoLore.add("&7ID: &f" + t.getId());
            tInfoLore.add("&7Mode: &e" + t.getModeLabel());
            tInfoLore.add("&7State: &a" + t.getState().name());
            tInfoLore.add("&7Teams: &e" + plugin.getTeamManager().getAllTeams().size() + "&7/&e" + t.getMaxTeams());
        }
        inv.setItem(10, createItem(Material.NETHER_STAR, "&6&lTournament Info", tInfoLore));

        List<String> regLore = new ArrayList<>();
        boolean reg = plugin.getTeamManager().isPlayerRegistered(player.getUniqueId());
        boolean solo = plugin.getTeamManager().isInSoloQueue(player.getUniqueId());
        regLore.add("&7Registered: " + (reg ? "&aYes" : "&cNo"));
        if (solo) {
            regLore.add("&7Mode: &eSolo Queue");
        } else if (playerTeam != null) {
            regLore.add("&7Team: &e" + playerTeam.getName());
        }
        inv.setItem(11, createItem(Material.WRITABLE_BOOK, "&e&lRegistration Status", regLore));

        List<String> teamLore = new ArrayList<>();
        if (playerTeam == null) {
            teamLore.add("&7You are not in a team.");
            teamLore.add("&7Use &f/tournament team create <name>");
        } else {
            teamLore.add("&7Name: &e" + playerTeam.getName());
            teamLore.add("&7Size: &f" + playerTeam.size() + (t != null ? "/" + t.getTeamSize() : ""));
            teamLore.add("&7Members: &f" + String.join(", ", playerTeam.getMemberNames()));
        }
        inv.setItem(12, createItem(Material.SHIELD, "&b&lTeam Information", teamLore));

        List<String> bracketLore = new ArrayList<>();
        BracketManager bm = plugin.getBracketManager();
        if (bm.getTotalRounds() == 0) {
            bracketLore.add("&7No bracket generated yet.");
        } else {
            bracketLore.add("&7Total Rounds: &e" + bm.getTotalRounds());
            for (var entry : bm.getRounds().entrySet()) {
                bracketLore.add("&7Round " + entry.getKey() + ": &f" + entry.getValue().size() + " matches");
            }
        }
        inv.setItem(14, createItem(Material.MAP, "&a&lBracket Overview", bracketLore));

        List<String> matchLore = new ArrayList<>();
        List<Match> activeMatches = plugin.getTournamentManager().getActiveMatches();
        matchLore.add("&7Active/Live Matches: &e" + activeMatches.size());
        int shown = 0;
        for (Match m : activeMatches) {
            if (shown++ >= 5) break;
            String red = m.getTeamRed() != null ? m.getTeamRed().getName() : "TBD";
            String blue = m.getTeamBlue() != null ? m.getTeamBlue().getName() : "BYE";
            matchLore.add("&7[" + m.getMatchId() + "] &e" + red + " &7vs &b" + blue + " &8(" + m.getStatus() + ")");
        }
        inv.setItem(15, createItem(Material.DIAMOND_SWORD, "&c&lMatch Status", matchLore));

        List<String> statLore = new ArrayList<>();
        statLore.add("&7Tournaments Played: &f" + stats.getTournamentsPlayed());
        statLore.add("&7Championships: &6" + stats.getChampionships());
        statLore.add("&7Matches Played: &f" + stats.getMatchesPlayed());
        statLore.add("&7Wins / Losses: &a" + stats.getMatchesWon() + "&7/&c" + stats.getMatchLosses());
        statLore.add("&7Eliminations: &e" + stats.getEliminations());
        inv.setItem(16, createItem(Material.PLAYER_HEAD, "&d&lYour Statistics", statLore));

        player.openInventory(inv);
    }

    private ItemStack createItem(Material material, String name, List<String> loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.colorize(name));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(MessageUtil.colorize(line));
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
