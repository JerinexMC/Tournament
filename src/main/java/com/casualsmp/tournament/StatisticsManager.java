package com.casualsmp.tournament;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StatisticsManager {

    public static class PlayerStats {
        private final UUID playerId;
        private int tournamentsPlayed;
        private int tournamentsWon;
        private int matchesPlayed;
        private int matchesWon;
        private int matchLosses;
        private int eliminations;
        private int championships;

        public PlayerStats(UUID playerId) {
            this.playerId = playerId;
        }

        public PlayerStats(UUID playerId, int tournamentsPlayed, int tournamentsWon,
                           int matchesPlayed, int matchesWon, int matchLosses,
                           int eliminations, int championships) {
            this.playerId = playerId;
            this.tournamentsPlayed = tournamentsPlayed;
            this.tournamentsWon = tournamentsWon;
            this.matchesPlayed = matchesPlayed;
            this.matchesWon = matchesWon;
            this.matchLosses = matchLosses;
            this.eliminations = eliminations;
            this.championships = championships;
        }

        public UUID getPlayerId() { return playerId; }
        public int getTournamentsPlayed() { return tournamentsPlayed; }
        public int getTournamentsWon() { return tournamentsWon; }
        public int getMatchesPlayed() { return matchesPlayed; }
        public int getMatchesWon() { return matchesWon; }
        public int getMatchLosses() { return matchLosses; }
        public int getEliminations() { return eliminations; }
        public int getChampionships() { return championships; }

        public void incrementTournamentsPlayed() { tournamentsPlayed++; }
        public void incrementTournamentsWon() { tournamentsWon++; }
        public void incrementMatchesPlayed() { matchesPlayed++; }
        public void incrementMatchesWon() { matchesWon++; }
        public void incrementMatchLosses() { matchLosses++; }
        public void incrementEliminations() { eliminations++; }
        public void incrementChampionships() { championships++; }
    }

    private final CasualTournament plugin;
    private final StorageManager storageManager;
    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();

    public StatisticsManager(CasualTournament plugin, StorageManager storageManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
    }

    public void loadAllStats() {
        statsCache.clear();
        statsCache.putAll(storageManager.loadAllStats());
        plugin.getLogger().info("Loaded statistics for " + statsCache.size() + " players.");
    }

    public PlayerStats getOrCreateStats(UUID playerId) {
        return statsCache.computeIfAbsent(playerId, PlayerStats::new);
    }

    public PlayerStats getStats(UUID playerId) {
        return statsCache.get(playerId);
    }

    public void recordTournamentPlayed(UUID playerId) {
        PlayerStats s = getOrCreateStats(playerId);
        s.incrementTournamentsPlayed();
        storageManager.savePlayerStatsAsync(s);
    }

    public void recordTournamentChampion(UUID playerId) {
        PlayerStats s = getOrCreateStats(playerId);
        s.incrementTournamentsWon();
        s.incrementChampionships();
        storageManager.savePlayerStatsAsync(s);
    }

    public void recordMatchPlayed(UUID playerId) {
        PlayerStats s = getOrCreateStats(playerId);
        s.incrementMatchesPlayed();
        storageManager.savePlayerStatsAsync(s);
    }

    public void recordMatchWon(UUID playerId) {
        PlayerStats s = getOrCreateStats(playerId);
        s.incrementMatchesWon();
        storageManager.savePlayerStatsAsync(s);
    }

    public void recordMatchLoss(UUID playerId) {
        PlayerStats s = getOrCreateStats(playerId);
        s.incrementMatchLosses();
        storageManager.savePlayerStatsAsync(s);
    }

    public void recordElimination(UUID killerId) {
        if (killerId == null) return;
        PlayerStats s = getOrCreateStats(killerId);
        s.incrementEliminations();
        storageManager.savePlayerStatsAsync(s);
    }

    public void flushAllSync() {
        for (PlayerStats s : statsCache.values()) {
            storageManager.savePlayerStatsSync(s);
        }
    }

    public String resolvePlayerName(UUID id) {
        var online = Bukkit.getPlayer(id);
        if (online != null) return online.getName();
        OfflinePlayer off = Bukkit.getOfflinePlayer(id);
        return off.getName() != null ? off.getName() : id.toString().substring(0, 8);
    }
}
