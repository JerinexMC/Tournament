package com.casualsmp.tournament;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Tournament {

    private final String id;
    private final int teamSize;
    private TournamentState state;
    private final int minTeams;
    private final int maxTeams;
    private final boolean soloRegistrationEnabled;
    private final boolean randomAssignmentEnabled;
    private final long createdAtMillis;
    private final List<String> winnerRewardCommands = new ArrayList<>();
    private final List<String> runnerUpRewardCommands = new ArrayList<>();
    private boolean rewardsDistributed;
    private String championTeamName;
    private String runnerUpTeamName;

    public Tournament(int teamSize, int minTeams, int maxTeams, boolean soloRegistrationEnabled, boolean randomAssignmentEnabled) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.teamSize = teamSize;
        this.state = TournamentState.REGISTRATION;
        this.minTeams = minTeams;
        this.maxTeams = maxTeams;
        this.soloRegistrationEnabled = soloRegistrationEnabled;
        this.randomAssignmentEnabled = randomAssignmentEnabled;
        this.createdAtMillis = System.currentTimeMillis();
        this.rewardsDistributed = false;
    }

    public String getId() {
        return id;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public String getModeLabel() {
        return teamSize + "v" + teamSize;
    }

    public TournamentState getState() {
        return state;
    }

    public void setState(TournamentState state) {
        this.state = state;
    }

    public int getMinTeams() {
        return minTeams;
    }

    public int getMaxTeams() {
        return maxTeams;
    }

    public boolean isSoloRegistrationEnabled() {
        return soloRegistrationEnabled;
    }

    public boolean isRandomAssignmentEnabled() {
        return randomAssignmentEnabled;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public List<String> getWinnerRewardCommands() {
        return winnerRewardCommands;
    }

    public List<String> getRunnerUpRewardCommands() {
        return runnerUpRewardCommands;
    }

    public boolean isRewardsDistributed() {
        return rewardsDistributed;
    }

    public void setRewardsDistributed(boolean rewardsDistributed) {
        this.rewardsDistributed = rewardsDistributed;
    }

    public String getChampionTeamName() {
        return championTeamName;
    }

    public void setChampionTeamName(String championTeamName) {
        this.championTeamName = championTeamName;
    }

    public String getRunnerUpTeamName() {
        return runnerUpTeamName;
    }

    public void setRunnerUpTeamName(String runnerUpTeamName) {
        this.runnerUpTeamName = runnerUpTeamName;
    }
}
