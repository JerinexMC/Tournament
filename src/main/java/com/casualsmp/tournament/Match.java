package com.casualsmp.tournament;

import java.util.*;

public class Match {

    private final String matchId;
    private final int round;
    private final int indexInRound;
    private Team teamRed;
    private Team teamBlue;
    private final boolean byeMatch;
    private String assignedArenaName;
    private MatchStatus status;
    private long startTimeMillis;
    private long endTimeMillis;
    private Team winner;
    private Team loser;
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Map<UUID, Long> disconnectedAtMillis = new HashMap<>();
    private boolean combatEnabled;
    private boolean winnerProcessed;

    public Match(String matchId, int round, int indexInRound, Team teamRed, Team teamBlue, boolean byeMatch) {
        this.matchId = matchId;
        this.round = round;
        this.indexInRound = indexInRound;
        this.teamRed = teamRed;
        this.teamBlue = teamBlue;
        this.byeMatch = byeMatch;
        this.status = MatchStatus.WAITING;
        this.combatEnabled = false;
        this.winnerProcessed = false;
    }

    public String getMatchId() {
        return matchId;
    }

    public int getRound() {
        return round;
    }

    public int getIndexInRound() {
        return indexInRound;
    }

    public Team getTeamRed() {
        return teamRed;
    }

    public void setTeamRed(Team teamRed) {
        this.teamRed = teamRed;
    }

    public Team getTeamBlue() {
        return teamBlue;
    }

    public void setTeamBlue(Team teamBlue) {
        this.teamBlue = teamBlue;
    }

    public boolean isByeMatch() {
        return byeMatch;
    }

    public boolean hasBothTeams() {
        return teamRed != null && teamBlue != null;
    }

    public String getAssignedArenaName() {
        return assignedArenaName;
    }

    public void setAssignedArenaName(String assignedArenaName) {
        this.assignedArenaName = assignedArenaName;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public void setStatus(MatchStatus status) {
        this.status = status;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public void setStartTimeMillis(long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
    }

    public long getEndTimeMillis() {
        return endTimeMillis;
    }

    public void setEndTimeMillis(long endTimeMillis) {
        this.endTimeMillis = endTimeMillis;
    }

    public Team getWinner() {
        return winner;
    }

    public void setWinner(Team winner) {
        this.winner = winner;
    }

    public Team getLoser() {
        return loser;
    }

    public void setLoser(Team loser) {
        this.loser = loser;
    }

    public Set<UUID> getEliminatedPlayers() {
        return eliminatedPlayers;
    }

    public boolean isEliminated(UUID playerId) {
        return eliminatedPlayers.contains(playerId);
    }

    public void markEliminated(UUID playerId) {
        if (playerId != null) {
            eliminatedPlayers.add(playerId);
            disconnectedAtMillis.remove(playerId);
        }
    }

    public Map<UUID, Long> getDisconnectedAtMillis() {
        return disconnectedAtMillis;
    }

    public void recordDisconnect(UUID playerId) {
        if (playerId != null && !eliminatedPlayers.contains(playerId)) {
            disconnectedAtMillis.putIfAbsent(playerId, System.currentTimeMillis());
        }
    }

    public void clearDisconnect(UUID playerId) {
        disconnectedAtMillis.remove(playerId);
    }

    public boolean isCombatEnabled() {
        return combatEnabled;
    }

    public void setCombatEnabled(boolean combatEnabled) {
        this.combatEnabled = combatEnabled;
    }

    public boolean isWinnerProcessed() {
        return winnerProcessed;
    }

    public void setWinnerProcessed(boolean winnerProcessed) {
        this.winnerProcessed = winnerProcessed;
    }

    public boolean isParticipant(UUID playerId) {
        return (teamRed != null && teamRed.hasMember(playerId))
                || (teamBlue != null && teamBlue.hasMember(playerId));
    }

    public Team getTeamOf(UUID playerId) {
        if (teamRed != null && teamRed.hasMember(playerId)) return teamRed;
        if (teamBlue != null && teamBlue.hasMember(playerId)) return teamBlue;
        return null;
    }

    public Team getOpponentTeam(Team team) {
        if (team == null) return null;
        if (teamRed != null && teamRed.getName().equalsIgnoreCase(team.getName())) return teamBlue;
        if (teamBlue != null && teamBlue.getName().equalsIgnoreCase(team.getName())) return teamRed;
        return null;
    }

    public int getRemainingAliveCount(Team team) {
        if (team == null) return 0;
        int alive = 0;
        for (UUID member : team.getMembers()) {
            if (!eliminatedPlayers.contains(member)) {
                alive++;
            }
        }
        return alive;
    }
}
