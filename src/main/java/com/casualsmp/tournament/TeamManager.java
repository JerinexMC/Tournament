package com.casualsmp.tournament;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class TeamManager {

    public static class Invite {
        private final String teamName;
        private final UUID inviterId;
        private final UUID inviteeId;
        private final long expiresAtMillis;

        public Invite(String teamName, UUID inviterId, UUID inviteeId, long expiresAtMillis) {
            this.teamName = teamName;
            this.inviterId = inviterId;
            this.inviteeId = inviteeId;
            this.expiresAtMillis = expiresAtMillis;
        }

        public String getTeamName() {
            return teamName;
        }

        public UUID getInviterId() {
            return inviterId;
        }

        public UUID getInviteeId() {
            return inviteeId;
        }

        public long getExpiresAtMillis() {
            return expiresAtMillis;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }

    private final CasualTournament plugin;
    private final Map<String, Team> teamsByName = new LinkedHashMap<>();
    private final Map<UUID, String> playerTeamMap = new HashMap<>();
    private final Map<UUID, Invite> pendingInvites = new HashMap<>();
    private final Set<UUID> soloQueue = new LinkedHashSet<>();

    public TeamManager(CasualTournament plugin) {
        this.plugin = plugin;
    }

    public void resetAll() {
        teamsByName.clear();
        playerTeamMap.clear();
        pendingInvites.clear();
        soloQueue.clear();
    }

    public void cleanupExpiredInvites() {
        long now = System.currentTimeMillis();
        pendingInvites.entrySet().removeIf(entry -> now > entry.getValue().getExpiresAtMillis());
    }

    public Team getTeamByName(String name) {
        if (name == null) return null;
        return teamsByName.get(name.toLowerCase(Locale.ROOT));
    }

    public Team getTeamOfPlayer(UUID playerId) {
        if (playerId == null) return null;
        String teamKey = playerTeamMap.get(playerId);
        if (teamKey == null) return null;
        return teamsByName.get(teamKey);
    }

    public boolean isPlayerRegistered(UUID playerId) {
        return getTeamOfPlayer(playerId) != null || soloQueue.contains(playerId);
    }

    public boolean isInSoloQueue(UUID playerId) {
        return soloQueue.contains(playerId);
    }

    public Set<UUID> getSoloQueue() {
        return Collections.unmodifiableSet(soloQueue);
    }

    public Collection<Team> getAllTeams() {
        return Collections.unmodifiableCollection(teamsByName.values());
    }

    public int getTotalRegisteredUnitsCount() {
        return teamsByName.size() + soloQueue.size();
    }

    public String createTeam(Player captain, String rawName, int maxTeamSize, int maxTeamsLimit) {
        UUID uuid = captain.getUniqueId();
        if (isPlayerRegistered(uuid)) {
            return "You are already registered in a team or in the solo queue.";
        }
        if (rawName == null || rawName.isBlank() || rawName.length() > 20 || !rawName.matches("[A-Za-z0-9_-]+")) {
            return "Team name must be 1-20 alphanumeric/underscore/dash characters.";
        }
        String key = rawName.toLowerCase(Locale.ROOT);
        if (teamsByName.containsKey(key)) {
            return "A team with that name already exists.";
        }
        if (maxTeamSize < 1) {
            return "Invalid team size configuration.";
        }
        if (teamsByName.size() >= maxTeamsLimit) {
            return "Maximum number of registered teams (" + maxTeamsLimit + ") has been reached.";
        }

        Team team = new Team(rawName, uuid);
        teamsByName.put(key, team);
        playerTeamMap.put(uuid, key);
        pendingInvites.remove(uuid);
        return null;
    }

    public String invitePlayer(Player captain, Player target, int teamSize) {
        cleanupExpiredInvites();
        UUID capId = captain.getUniqueId();
        Team team = getTeamOfPlayer(capId);
        if (team == null) {
            return "You are not in a team.";
        }
        if (!team.isCaptain(capId)) {
            return "Only the team captain can invite teammates.";
        }
        if (team.size() >= teamSize) {
            return "Your team is already full (" + team.size() + "/" + teamSize + ").";
        }
        UUID targetId = target.getUniqueId();
        if (targetId.equals(capId)) {
            return "You cannot invite yourself.";
        }
        if (team.hasMember(targetId)) {
            return target.getName() + " is already on your team.";
        }
        if (getTeamOfPlayer(targetId) != null) {
            return target.getName() + " is already in another team.";
        }
        if (soloQueue.contains(targetId)) {
            return target.getName() + " is registered as solo and must leave solo queue first.";
        }

        int expirySeconds = Math.max(10, plugin.getConfig().getInt("tournament.invite-expiry-seconds", 60));
        long expiresAt = System.currentTimeMillis() + (expirySeconds * 1000L);
        pendingInvites.put(targetId, new Invite(team.getName(), capId, targetId, expiresAt));
        return null;
    }

    public String acceptInvite(Player player, int teamSize) {
        cleanupExpiredInvites();
        UUID playerId = player.getUniqueId();
        if (isPlayerRegistered(playerId)) {
            return "You are already in a team or solo queue.";
        }
        Invite invite = pendingInvites.remove(playerId);
        if (invite == null || invite.isExpired()) {
            return "You have no active team invitations.";
        }
        Team team = getTeamByName(invite.getTeamName());
        if (team == null) {
            return "The team that invited you no longer exists.";
        }
        if (team.size() >= teamSize) {
            return "Team " + team.getName() + " is already full (" + teamSize + " players).";
        }

        team.addMember(playerId);
        playerTeamMap.put(playerId, team.getName().toLowerCase(Locale.ROOT));
        return null;
    }

    public String leaveTeam(Player player) {
        UUID playerId = player.getUniqueId();
        Team team = getTeamOfPlayer(playerId);
        if (team == null) {
            return "You are not currently on a team.";
        }
        if (team.isCaptain(playerId)) {
            return "As the team captain, use /tournament team disband to disband the team or transfer leadership.";
        }
        team.removeMember(playerId);
        playerTeamMap.remove(playerId);
        return null;
    }

    public String disbandTeam(Player player) {
        UUID playerId = player.getUniqueId();
        Team team = getTeamOfPlayer(playerId);
        if (team == null) {
            return "You are not currently on a team.";
        }
        if (!team.isCaptain(playerId)) {
            return "Only the team captain can disband the team.";
        }
        for (UUID memberId : new ArrayList<>(team.getMembers())) {
            playerTeamMap.remove(memberId);
        }
        teamsByName.remove(team.getName().toLowerCase(Locale.ROOT));
        pendingInvites.entrySet().removeIf(e -> e.getValue().getTeamName().equalsIgnoreCase(team.getName()));
        return null;
    }

    public String joinSolo(Player player, int maxTeamsLimit) {
        UUID playerId = player.getUniqueId();
        if (isPlayerRegistered(playerId)) {
            return "You are already registered in a team or in the solo queue.";
        }
        if (getTotalRegisteredUnitsCount() >= maxTeamsLimit) {
            return "Tournament registration has reached its maximum capacity.";
        }
        soloQueue.add(playerId);
        pendingInvites.remove(playerId);
        return null;
    }

    public boolean leaveSolo(UUID playerId) {
        return soloQueue.remove(playerId);
    }

    /**
     * Groups solo players randomly into teams of size {@code teamSize}.
     * Leftover solo players that cannot form a full team of {@code teamSize} are returned as unassignable.
     */
    public List<UUID> assignSoloPlayersRandomly(int teamSize, int maxTeamsLimit) {
        List<UUID> ungrouped = new ArrayList<>();
        if (soloQueue.isEmpty() || teamSize < 1) {
            return ungrouped;
        }

        List<UUID> pool = new ArrayList<>(soloQueue);
        Collections.shuffle(pool, new Random());
        soloQueue.clear();

        int index = 0;
        int autoCounter = 1;
        while (index + teamSize <= pool.size()) {
            if (teamsByName.size() >= maxTeamsLimit) {
                break;
            }
            List<UUID> group = pool.subList(index, index + teamSize);
            index += teamSize;

            String baseName = "SoloTeam" + autoCounter++;
            while (teamsByName.containsKey(baseName.toLowerCase(Locale.ROOT))) {
                baseName = "SoloTeam" + autoCounter++;
            }

            UUID captain = group.get(0);
            Team autoTeam = new Team(baseName, captain);
            autoTeam.setAutoGenerated(true);
            for (UUID member : group) {
                autoTeam.addMember(member);
                playerTeamMap.put(member, baseName.toLowerCase(Locale.ROOT));
            }
            teamsByName.put(baseName.toLowerCase(Locale.ROOT), autoTeam);
        }

        while (index < pool.size()) {
            ungrouped.add(pool.get(index));
            index++;
        }
        return ungrouped;
    }

    public void registerGeneratedTeam(Team team) {
        if (team == null) return;
        String key = team.getName().toLowerCase(Locale.ROOT);
        teamsByName.put(key, team);
        for (UUID member : team.getMembers()) {
            playerTeamMap.put(member, key);
        }
    }
}
