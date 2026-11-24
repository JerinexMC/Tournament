package com.casualsmp.tournament;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class SpectatorManager {

    private final CasualTournament plugin;
    private final Map<UUID, String> spectatorMatchMap = new HashMap<>();

    public SpectatorManager(CasualTournament plugin) {
        this.plugin = plugin;
    }

    public void addSpectator(Player player, Match match, Location spectatorSpawn) {
        if (player == null || !player.isOnline() || match == null) return;
        if (!plugin.getConfig().getBoolean("spectator.enabled", true)) return;

        spectatorMatchMap.put(player.getUniqueId(), match.getMatchId());
        player.setGameMode(GameMode.SPECTATOR);
        if (spectatorSpawn != null && spectatorSpawn.getWorld() != null) {
            player.teleport(spectatorSpawn);
        }
    }

    public void removeSpectator(UUID playerId) {
        if (playerId != null) {
            spectatorMatchMap.remove(playerId);
        }
    }

    public boolean isSpectatingMatch(UUID playerId, String matchId) {
        if (playerId == null || matchId == null) return false;
        String assigned = spectatorMatchMap.get(playerId);
        return matchId.equals(assigned);
    }

    public boolean isSpectatingAnyMatch(UUID playerId) {
        return playerId != null && spectatorMatchMap.containsKey(playerId);
    }

    public String getSpectatedMatchId(UUID playerId) {
        return spectatorMatchMap.get(playerId);
    }

    public void clearMatchSpectators(String matchId) {
        if (matchId == null) return;
        spectatorMatchMap.entrySet().removeIf(e -> matchId.equals(e.getValue()));
    }

    public void clearAll() {
        spectatorMatchMap.clear();
    }
}
