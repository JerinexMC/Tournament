package com.casualsmp.tournament;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TournamentManager {

    private final CasualTournament plugin;
    private Tournament currentTournament;
    private Location waitingLobby;

    private final Queue<Match> matchQueue = new LinkedList<>();
    private final Map<String, Match> liveMatches = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> matchTasks = new ConcurrentHashMap<>();
    private BukkitTask periodicTask;

    public TournamentManager(CasualTournament plugin) {
        this.plugin = plugin;
    }

    public Tournament getCurrentTournament() {
        return currentTournament;
    }

    public Location getWaitingLobby() {
        return waitingLobby != null ? waitingLobby.clone() : null;
    }

    public void setWaitingLobby(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        this.waitingLobby = loc.clone();
        String serialized = loc.getWorld().getName() + ";"
                + loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";"
                + loc.getYaw() + ";" + loc.getPitch();
        plugin.getStorageManager().saveMetadataAsync("waiting_lobby", serialized);
    }

    public void loadLobbyLocation() {
        String raw = plugin.getStorageManager().getMetadata("waiting_lobby");
        if (raw == null || raw.isBlank()) return;
        String[] parts = raw.split(";");
        if (parts.length < 4) return;
        World w = Bukkit.getWorld(parts[0]);
        if (w == null) {
            plugin.getLogger().warning("Saved waiting lobby world '" + parts[0] + "' is not loaded.");
            return;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0.0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0.0f;
            this.waitingLobby = new Location(w, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid waiting lobby coordinates in metadata: " + raw);
        }
    }

    public void recoverIncompleteTournamentState() {
        String lastId = plugin.getStorageManager().getMetadata("active_tournament_id");
        String lastState = plugin.getStorageManager().getMetadata("active_tournament_state");
        if (lastId != null && lastState != null) {
            if ("IN_PROGRESS".equals(lastState) || "REGISTRATION".equals(lastState) || "PAUSED".equals(lastState)) {
                plugin.getLogger().info("Recovered previous tournament '" + lastId + "' (was " + lastState + ") into PAUSED/safe recovery state without restarting matches or duplicating rewards.");
                this.currentTournament = new Tournament(
                        Math.max(1, plugin.getConfig().getInt("tournament.min-team-size", 1)),
                        Math.max(2, plugin.getConfig().getInt("tournament.min-teams", 2)),
                        Math.max(2, plugin.getConfig().getInt("tournament.max-teams", 64)),
                        plugin.getConfig().getBoolean("tournament.solo-registration", true),
                        plugin.getConfig().getBoolean("tournament.random-team-assignment", true)
                );
                this.currentTournament.setState(TournamentState.PAUSED);
            }
        }
    }

    public void startPeriodicTasks() {
        stopPeriodicTasks();
        this.periodicTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            plugin.getTeamManager().cleanupExpiredInvites();
            checkDisconnectGracePeriods();
            processMatchQueue();
        }, 20L, 20L);
    }

    public void stopPeriodicTasks() {
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
    }

    public void shutdownTasksAndSave() {
        stopPeriodicTasks();
        for (BukkitTask t : matchTasks.values()) {
            t.cancel();
        }
        matchTasks.clear();
        for (Match m : liveMatches.values()) {
            if (m.getAssignedArenaName() != null) {
                plugin.getArenaManager().releaseArena(m.getAssignedArenaName());
            }
        }
        liveMatches.clear();
        matchQueue.clear();
        if (currentTournament != null) {
            plugin.getStorageManager().saveMetadata("active_tournament_id", currentTournament.getId());
            plugin.getStorageManager().saveMetadata("active_tournament_state", currentTournament.getState().name());
            plugin.getStorageManager().saveTournamentRecordSync(currentTournament);
        }
    }

    public String createTournament(CommandSender sender, int teamSize) {
        int minSize = Math.max(1, plugin.getConfig().getInt("tournament.min-team-size", 1));
        int maxSize = Math.max(minSize, plugin.getConfig().getInt("tournament.max-team-size", 32));
        if (teamSize < minSize || teamSize > maxSize) {
            return "Team size must be between " + minSize + " and " + maxSize + ".";
        }
        if (currentTournament != null && (currentTournament.getState() == TournamentState.REGISTRATION
                || currentTournament.getState() == TournamentState.IN_PROGRESS
                || currentTournament.getState() == TournamentState.PAUSED)) {
            return "A tournament is already active (" + currentTournament.getState() + "). Stop or cancel it first.";
        }

        clearRuntimeMatches();
        plugin.getTeamManager().resetAll();
        plugin.getBracketManager().clear();
        plugin.getSpectatorManager().clearAll();

        int minTeams = Math.max(2, plugin.getConfig().getInt("tournament.min-teams", 2));
        int maxTeams = Math.max(minTeams, plugin.getConfig().getInt("tournament.max-teams", 64));
        boolean soloEnabled = plugin.getConfig().getBoolean("tournament.solo-registration", true);
        boolean randomAssign = plugin.getConfig().getBoolean("tournament.random-team-assignment", true);

        Tournament t = new Tournament(teamSize, minTeams, maxTeams, soloEnabled, randomAssign);
        t.getWinnerRewardCommands().addAll(plugin.getConfig().getStringList("rewards.winner"));
        t.getRunnerUpRewardCommands().addAll(plugin.getConfig().getStringList("rewards.runner-up"));
        this.currentTournament = t;

        plugin.getStorageManager().saveMetadataAsync("active_tournament_id", t.getId());
        plugin.getStorageManager().saveMetadataAsync("active_tournament_state", t.getState().name());
        plugin.getStorageManager().saveTournamentRecordAsync(t);

        String msg = plugin.getConfig().getString(
                "messages.registration-open",
                "&6&lTOURNAMENT &fRegistration is now open for &e%mode% &f(Max teams: &e%max_teams%&f)!"
        ).replace("%mode%", t.getModeLabel()).replace("%max_teams%", String.valueOf(t.getMaxTeams()));
        MessageUtil.broadcast(msg);
        return null;
    }

    public void teleportToWaitingLobby(Player player) {
        if (player == null || !player.isOnline()) return;
        if (waitingLobby != null && waitingLobby.getWorld() != null) {
            player.teleport(waitingLobby);
            MessageUtil.sendPrefixed(player, plugin, "You have been teleported to the tournament waiting lobby.");
        }
    }

    public String startTournament(CommandSender sender) {
        if (currentTournament == null) {
            return "No tournament exists. Use /tournament create <team-size> first.";
        }
        if (currentTournament.getState() != TournamentState.REGISTRATION) {
            return "Tournament cannot be started from state " + currentTournament.getState() + ".";
        }

        currentTournament.setState(TournamentState.IN_PROGRESS);
        plugin.getStorageManager().saveMetadataAsync("active_tournament_state", TournamentState.IN_PROGRESS.name());

        TeamManager tm = plugin.getTeamManager();
        int requiredSize = currentTournament.getTeamSize();

        if (currentTournament.isSoloRegistrationEnabled() && currentTournament.isRandomAssignmentEnabled() && !tm.getSoloQueue().isEmpty()) {
            List<UUID> ungrouped = tm.assignSoloPlayersRandomly(requiredSize, currentTournament.getMaxTeams());
            if (!ungrouped.isEmpty()) {
                for (UUID pid : ungrouped) {
                    tm.leaveSolo(pid);
                    Player p = Bukkit.getPlayer(pid);
                    if (p != null) {
                        MessageUtil.sendPrefixed(p, plugin, "Could not form a full " + requiredSize + "-player team for you; you were left out of the bracket.");
                    }
                }
            }
        }

        List<Team> validTeams = new ArrayList<>();
        for (Team team : tm.getAllTeams()) {
            if (team.size() == requiredSize) {
                validTeams.add(team);
            } else {
                for (UUID member : team.getMembers()) {
                    Player p = Bukkit.getPlayer(member);
                    if (p != null) {
                        MessageUtil.sendPrefixed(p, plugin, "&cYour team '" + team.getName() + "' had " + team.size() + "/" + requiredSize + " players and could not participate.");
                    }
                }
            }
        }

        if (validTeams.size() < currentTournament.getMinTeams()) {
            currentTournament.setState(TournamentState.REGISTRATION);
            plugin.getStorageManager().saveMetadataAsync("active_tournament_state", TournamentState.REGISTRATION.name());
            return "Not enough valid teams (" + validTeams.size() + "/" + currentTournament.getMinTeams() + " minimum required with exact size " + requiredSize + "). Registration reopened.";
        }

        for (Team team : validTeams) {
            for (UUID member : team.getMembers()) {
                plugin.getStatisticsManager().recordTournamentPlayed(member);
            }
        }

        BracketManager bm = plugin.getBracketManager();
        bm.generateBracket(validTeams);

        String startMsg = plugin.getConfig().getString(
                "messages.tournament-start",
                "&6&lTOURNAMENT &fThe tournament has officially &aSTARTED&f!"
        );
        MessageUtil.broadcast(startMsg);

        for (int r = 1; r <= bm.getTotalRounds(); r++) {
            for (Match m : bm.getRoundMatches(r)) {
                if (m.isByeMatch()) {
                    continue;
                }
                if (m.hasBothTeams() && m.getStatus() == MatchStatus.WAITING) {
                    enqueueMatch(m);
                }
            }
        }

        processMatchQueue();
        return null;
    }

    public synchronized void enqueueMatch(Match match) {
        if (match == null || match.isByeMatch() || !match.hasBothTeams()) return;
        if (match.getStatus() != MatchStatus.WAITING) return;
        if (!matchQueue.contains(match) && !liveMatches.containsKey(match.getMatchId())) {
            matchQueue.add(match);
        }
    }

    private boolean processingQueue = false;

    public synchronized void processMatchQueue() {
        if (processingQueue) {
            return;
        }
        if (currentTournament == null || currentTournament.getState() != TournamentState.IN_PROGRESS) {
            return;
        }
        processingQueue = true;
        try {
            while (!matchQueue.isEmpty()) {
                Match next = matchQueue.peek();
                if (next == null || !next.hasBothTeams() || next.getStatus() != MatchStatus.WAITING) {
                    matchQueue.poll();
                    continue;
                }
                Arena arena = plugin.getArenaManager().acquireAvailableArena(next.getMatchId());
                if (arena == null) {
                    break;
                }
                matchQueue.poll();
                launchMatchInArena(next, arena);
            }
        } finally {
            processingQueue = false;
        }
    }

    private void launchMatchInArena(Match match, Arena arena) {
        if (!arena.isReadyForMatch()) {
            arena.setOccupied(false);
            arena.setAssignedMatchId(null);
            enqueueMatch(match);
            return;
        }

        match.setAssignedArenaName(arena.getName());
        match.setStatus(MatchStatus.PREPARING);
        match.setCombatEnabled(false);
        match.setStartTimeMillis(System.currentTimeMillis());
        liveMatches.put(match.getMatchId(), match);

        String assignedMsg = plugin.getConfig().getString(
                "messages.match-assigned",
                "&e%red% &7vs &b%blue% &fassigned to arena &e%arena%&f!"
        ).replace("%red%", match.getTeamRed().getName())
         .replace("%blue%", match.getTeamBlue().getName())
         .replace("%arena%", arena.getName());
        MessageUtil.broadcast(assignedMsg);

        teleportTeamToSpawnSpread(match.getTeamRed(), arena.getRedSpawn());
        teleportTeamToSpawnSpread(match.getTeamBlue(), arena.getBlueSpawn());

        if (arena.getSpectatorSpawn() != null && arena.getSpectatorSpawn().getWorld() != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (plugin.getSpectatorManager().isSpectatingMatch(online.getUniqueId(), match.getMatchId())) {
                    online.teleport(arena.getSpectatorSpawn());
                }
            }
        }

        int prepSeconds = Math.max(0, plugin.getConfig().getInt("match.preparation-seconds", 3));
        BukkitTask prepTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (match.getStatus() == MatchStatus.CANCELLED || match.getStatus() == MatchStatus.FINISHED) {
                return;
            }
            startCountdownPhase(match);
        }, prepSeconds * 20L);
        matchTasks.put(match.getMatchId() + ":prep", prepTask);
    }

    private void teleportTeamToSpawnSpread(Team team, Location baseSpawn) {
        if (team == null || baseSpawn == null || baseSpawn.getWorld() == null) return;
        List<Player> members = team.getOnlineMembers();
        int count = members.size();
        for (int i = 0; i < count; i++) {
            Player p = members.get(i);
            plugin.getInventoryManager().backupAndPrepareForMatch(p);
            plugin.getStatisticsManager().recordMatchPlayed(p.getUniqueId());

            double angle = (2.0 * Math.PI * i) / Math.max(1, count);
            double radius = count > 1 ? 1.5 : 0.0;
            Location spot = baseSpawn.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
            p.teleport(spot);
        }
    }

    private void startCountdownPhase(Match match) {
        match.setStatus(MatchStatus.COUNTDOWN);
        int countdownSeconds = Math.max(1, plugin.getConfig().getInt("match.countdown-seconds", 5));
        final int[] remaining = {countdownSeconds};

        BukkitTask cdTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (match.getStatus() != MatchStatus.COUNTDOWN) {
                return;
            }
            if (remaining[0] > 0) {
                String tickMsg = plugin.getConfig().getString(
                        "messages.countdown-tick",
                        "&e%red% &7vs &b%blue% &fstarting in &c%seconds% &fseconds!"
                ).replace("%red%", match.getTeamRed().getName())
                 .replace("%blue%", match.getTeamBlue().getName())
                 .replace("%seconds%", String.valueOf(remaining[0]));

                sendToMatchParticipantsAndSpectators(match, tickMsg);
                for (Player p : getMatchOnlinePlayers(match)) {
                    MessageUtil.sendTitle(p, "&c" + remaining[0], "&7Get ready!", 0, 22, 2);
                }
                remaining[0]--;
            } else {
                BukkitTask self = matchTasks.remove(match.getMatchId() + ":cd");
                if (self != null) self.cancel();
                activateMatchCombat(match);
            }
        }, 0L, 20L);
        matchTasks.put(match.getMatchId() + ":cd", cdTask);
    }

    private void activateMatchCombat(Match match) {
        match.setStatus(MatchStatus.ACTIVE);
        match.setCombatEnabled(true);
        match.setStartTimeMillis(System.currentTimeMillis());

        String fightMsg = plugin.getConfig().getString("messages.fight", "&c&lFIGHT!");
        sendToMatchParticipantsAndSpectators(match, fightMsg);
        for (Player p : getMatchOnlinePlayers(match)) {
            MessageUtil.sendTitle(p, fightMsg, "", 0, 25, 5);
        }

        int timeoutSeconds = Math.max(30, plugin.getConfig().getInt("match.timeout-seconds", 300));
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (match.getStatus() == MatchStatus.ACTIVE) {
                int redAlive = match.getRemainingAliveCount(match.getTeamRed());
                int blueAlive = match.getRemainingAliveCount(match.getTeamBlue());
                Team winner = redAlive >= blueAlive ? match.getTeamRed() : match.getTeamBlue();
                finishMatch(match, winner);
            }
        }, timeoutSeconds * 20L);
        matchTasks.put(match.getMatchId() + ":timeout", timeoutTask);
    }

    public void handlePlayerEliminated(Player victim, Player killer) {
        if (victim == null) return;
        Match match = findMatchForPlayer(victim.getUniqueId());
        if (match == null) return;
        if (match.getStatus() != MatchStatus.ACTIVE && match.getStatus() != MatchStatus.COUNTDOWN && match.getStatus() != MatchStatus.PREPARING) {
            return;
        }
        if (match.isEliminated(victim.getUniqueId())) return;

        match.markEliminated(victim.getUniqueId());
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            plugin.getStatisticsManager().recordElimination(killer.getUniqueId());
        }

        Arena arena = plugin.getArenaManager().getArena(match.getAssignedArenaName());
        Location specLoc = arena != null ? arena.getSpectatorSpawn() : waitingLobby;
        plugin.getSpectatorManager().addSpectator(victim, match, specLoc);
        MessageUtil.sendPrefixed(victim, plugin, "&cYou have been eliminated! You are now spectating the match.");

        Team victimTeam = match.getTeamOf(victim.getUniqueId());
        if (victimTeam != null && match.getRemainingAliveCount(victimTeam) <= 0) {
            Team winningTeam = match.getOpponentTeam(victimTeam);
            finishMatch(match, winningTeam);
        }
    }

    public void handlePlayerDisconnect(Player player) {
        if (player == null) return;
        UUID pid = player.getUniqueId();
        Match match = findMatchForPlayer(pid);
        if (match != null && (match.getStatus() == MatchStatus.ACTIVE || match.getStatus() == MatchStatus.COUNTDOWN || match.getStatus() == MatchStatus.PREPARING)) {
            boolean countsAsLoss = plugin.getConfig().getBoolean("match.disconnect-counts-as-loss", true);
            int graceSeconds = plugin.getConfig().getInt("match.disconnect-grace-seconds", 15);
            if (countsAsLoss && graceSeconds <= 0) {
                match.markEliminated(pid);
                Team victimTeam = match.getTeamOf(pid);
                if (victimTeam != null && match.getRemainingAliveCount(victimTeam) <= 0) {
                    finishMatch(match, match.getOpponentTeam(victimTeam));
                }
            } else {
                match.recordDisconnect(pid);
            }
        }
    }

    public void handlePlayerJoin(Player player) {
        if (player == null) return;
        UUID pid = player.getUniqueId();

        Match activeMatch = findMatchForPlayer(pid);
        boolean inActiveMatch = activeMatch != null && !activeMatch.isEliminated(pid)
                && (activeMatch.getStatus() == MatchStatus.ACTIVE
                || activeMatch.getStatus() == MatchStatus.COUNTDOWN
                || activeMatch.getStatus() == MatchStatus.PREPARING);

        if (inActiveMatch) {
            activeMatch.clearDisconnect(pid);
            Arena arena = plugin.getArenaManager().getArena(activeMatch.getAssignedArenaName());
            if (arena != null && arena.getRedSpawn() != null && activeMatch.getTeamRed() != null && activeMatch.getTeamRed().hasMember(pid)) {
                player.teleport(arena.getRedSpawn());
            } else if (arena != null && arena.getBlueSpawn() != null && activeMatch.getTeamBlue() != null && activeMatch.getTeamBlue().hasMember(pid)) {
                player.teleport(arena.getBlueSpawn());
            }
        } else if (plugin.getInventoryManager().hasSnapshot(pid)) {
            plugin.getInventoryManager().restoreIfOnline(pid, true);
        }
    }

    private void checkDisconnectGracePeriods() {
        int graceSeconds = Math.max(1, plugin.getConfig().getInt("match.disconnect-grace-seconds", 15));
        long graceMillis = graceSeconds * 1000L;
        long now = System.currentTimeMillis();

        for (Match match : new ArrayList<>(liveMatches.values())) {
            if (match.getStatus() != MatchStatus.ACTIVE && match.getStatus() != MatchStatus.COUNTDOWN && match.getStatus() != MatchStatus.PREPARING) {
                continue;
            }
            List<UUID> expired = new ArrayList<>();
            for (Map.Entry<UUID, Long> entry : match.getDisconnectedAtMillis().entrySet()) {
                if (now - entry.getValue() >= graceMillis) {
                    expired.add(entry.getKey());
                }
            }
            for (UUID pid : expired) {
                match.markEliminated(pid);
                Team victimTeam = match.getTeamOf(pid);
                if (victimTeam != null && match.getRemainingAliveCount(victimTeam) <= 0) {
                    finishMatch(match, match.getOpponentTeam(victimTeam));
                    break;
                }
            }
        }
    }

    public String forceWinner(String matchId, String teamName) {
        Match match = plugin.getBracketManager().getMatchById(matchId);
        if (match == null) {
            return "Match '" + matchId + "' not found.";
        }
        if (match.getStatus() == MatchStatus.FINISHED || match.getStatus() == MatchStatus.CANCELLED) {
            return "Match '" + matchId + "' is already " + match.getStatus() + ".";
        }
        Team chosen = null;
        if (match.getTeamRed() != null && match.getTeamRed().getName().equalsIgnoreCase(teamName)) {
            chosen = match.getTeamRed();
        } else if (match.getTeamBlue() != null && match.getTeamBlue().getName().equalsIgnoreCase(teamName)) {
            chosen = match.getTeamBlue();
        }
        if (chosen == null) {
            return "Team '" + teamName + "' is not one of the teams in match '" + matchId + "'.";
        }
        finishMatch(match, chosen);
        return null;
    }

    public synchronized void finishMatch(Match match, Team winner) {
        if (match == null || match.isWinnerProcessed()) return;
        match.setWinnerProcessed(true);
        match.setStatus(MatchStatus.FINISHED);
        match.setCombatEnabled(false);
        match.setEndTimeMillis(System.currentTimeMillis());

        Team loser = match.getOpponentTeam(winner);
        match.setWinner(winner);
        match.setLoser(loser);

        cancelMatchTasks(match.getMatchId());
        liveMatches.remove(match.getMatchId());

        if (match.getAssignedArenaName() != null) {
            plugin.getArenaManager().releaseArena(match.getAssignedArenaName());
        }

        String winnerName = winner != null ? winner.getName() : "None";
        String loserName = loser != null ? loser.getName() : "None";
        String winMsg = plugin.getConfig().getString(
                "messages.match-winner",
                "&a%winner% &fhas won the match against &7%loser%&f!"
        ).replace("%winner%", winnerName).replace("%loser%", loserName);
        MessageUtil.broadcast(winMsg);

        if (winner != null) {
            for (UUID member : winner.getMembers()) {
                plugin.getStatisticsManager().recordMatchWon(member);
            }
        }
        if (loser != null) {
            for (UUID member : loser.getMembers()) {
                plugin.getStatisticsManager().recordMatchLoss(member);
            }
        }

        Set<UUID> toRestore = new HashSet<>();
        if (winner != null) toRestore.addAll(winner.getMembers());
        if (loser != null) toRestore.addAll(loser.getMembers());

        plugin.getSpectatorManager().clearMatchSpectators(match.getMatchId());

        BracketManager bm = plugin.getBracketManager();
        if (bm.isGrandFinal(match)) {
            completeTournament(winner, loser);
            for (UUID pid : toRestore) {
                plugin.getInventoryManager().restoreIfOnline(pid, true);
            }
            return;
        }

        Match nextMatch = bm.advanceWinnerToNextRound(match, winner);
        if (nextMatch != null) {
            int nextRound = nextMatch.getRound();
            String nextRoundMsg = plugin.getConfig().getString(
                    "messages.next-round",
                    "&6&lROUND %round% &fMatches are now forming!"
            ).replace("%round%", String.valueOf(nextRound));
            MessageUtil.broadcast(nextRoundMsg);

            if (bm.isGrandFinal(nextMatch) && nextMatch.hasBothTeams()) {
                String gfMsg = plugin.getConfig().getString(
                        "messages.grand-final",
                        "&6&lGRAND FINAL &e%red% &7vs &b%blue%&f!"
                ).replace("%red%", nextMatch.getTeamRed().getName())
                 .replace("%blue%", nextMatch.getTeamBlue().getName());
                MessageUtil.broadcast(gfMsg);
            }

            if (nextMatch.hasBothTeams() && nextMatch.getStatus() == MatchStatus.WAITING) {
                enqueueMatch(nextMatch);
            }
        }

        for (UUID pid : toRestore) {
            if (findMatchForPlayer(pid) == null) {
                plugin.getInventoryManager().restoreIfOnline(pid, true);
            }
        }

        processMatchQueue();
    }

    private void completeTournament(Team champion, Team runnerUp) {
        if (currentTournament == null) return;
        currentTournament.setState(TournamentState.COMPLETED);
        currentTournament.setChampionTeamName(champion != null ? champion.getName() : "None");
        currentTournament.setRunnerUpTeamName(runnerUp != null ? runnerUp.getName() : "None");

        String champMsg = plugin.getConfig().getString(
                "messages.champion",
                "&6&lGRAND FINAL &8| &e&lCHAMPION: %champion%!"
        ).replace("%champion%", currentTournament.getChampionTeamName());
        MessageUtil.broadcast(champMsg);

        if (champion != null) {
            for (UUID member : champion.getMembers()) {
                plugin.getStatisticsManager().recordTournamentChampion(member);
            }
        }

        distributeRewardsOnce(champion, runnerUp);
        plugin.getStorageManager().saveMetadataAsync("active_tournament_state", TournamentState.COMPLETED.name());
        plugin.getStorageManager().saveTournamentRecordAsync(currentTournament);
    }

    private void distributeRewardsOnce(Team champion, Team runnerUp) {
        if (currentTournament == null || currentTournament.isRewardsDistributed()) {
            return;
        }
        currentTournament.setRewardsDistributed(true);

        boolean useVault = plugin.getConfig().getBoolean("rewards.use-vault", false) && plugin.getVaultEconomy() != null;
        double vaultWinner = plugin.getConfig().getDouble("rewards.vault-winner-amount", 0.0);
        double vaultRunnerUp = plugin.getConfig().getDouble("rewards.vault-runner-up-amount", 0.0);

        if (champion != null) {
            for (UUID memberId : champion.getMembers()) {
                String playerName = plugin.getStatisticsManager().resolvePlayerName(memberId);
                for (String cmd : currentTournament.getWinnerRewardCommands()) {
                    executeRewardCommand(cmd, playerName);
                }
                if (useVault && vaultWinner > 0) {
                    plugin.getVaultEconomy().depositPlayer(Bukkit.getOfflinePlayer(memberId), vaultWinner);
                }
            }
        }

        if (runnerUp != null) {
            for (UUID memberId : runnerUp.getMembers()) {
                String playerName = plugin.getStatisticsManager().resolvePlayerName(memberId);
                for (String cmd : currentTournament.getRunnerUpRewardCommands()) {
                    executeRewardCommand(cmd, playerName);
                }
                if (useVault && vaultRunnerUp > 0) {
                    plugin.getVaultEconomy().depositPlayer(Bukkit.getOfflinePlayer(memberId), vaultRunnerUp);
                }
            }
        }
    }

    private void executeRewardCommand(String template, String playerName) {
        if (template == null || template.isBlank() || playerName == null) return;
        String parsed = template.replace("%player%", playerName).trim();
        if (parsed.startsWith("/")) {
            parsed = parsed.substring(1);
        }
        final String finalCmd = parsed;
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
    }

    public String pauseTournament() {
        if (currentTournament == null || currentTournament.getState() != TournamentState.IN_PROGRESS) {
            return "No in-progress tournament to pause.";
        }
        currentTournament.setState(TournamentState.PAUSED);
        for (Match m : liveMatches.values()) {
            m.setCombatEnabled(false);
        }
        plugin.getStorageManager().saveMetadataAsync("active_tournament_state", TournamentState.PAUSED.name());
        return null;
    }

    public String resumeTournament() {
        if (currentTournament == null || currentTournament.getState() != TournamentState.PAUSED) {
            return "No paused tournament to resume.";
        }
        currentTournament.setState(TournamentState.IN_PROGRESS);
        for (Match m : liveMatches.values()) {
            if (m.getStatus() == MatchStatus.ACTIVE) {
                m.setCombatEnabled(true);
            }
        }
        plugin.getStorageManager().saveMetadataAsync("active_tournament_state", TournamentState.IN_PROGRESS.name());
        processMatchQueue();
        return null;
    }

    public String stopOrCancelTournament(boolean cancelled) {
        if (currentTournament == null) {
            return "No active tournament.";
        }
        currentTournament.setState(cancelled ? TournamentState.CANCELLED : TournamentState.COMPLETED);
        for (Match m : liveMatches.values()) {
            m.setStatus(MatchStatus.CANCELLED);
            m.setCombatEnabled(false);
            cancelMatchTasks(m.getMatchId());
            if (m.getAssignedArenaName() != null) {
                plugin.getArenaManager().releaseArena(m.getAssignedArenaName());
            }
        }
        liveMatches.clear();
        matchQueue.clear();
        plugin.getSpectatorManager().clearAll();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getInventoryManager().hasSnapshot(p.getUniqueId())) {
                plugin.getInventoryManager().restoreIfOnline(p.getUniqueId(), true);
            }
        }

        plugin.getStorageManager().saveMetadataAsync("active_tournament_state", currentTournament.getState().name());
        plugin.getStorageManager().saveTournamentRecordAsync(currentTournament);
        return null;
    }

    private void clearRuntimeMatches() {
        for (String key : new ArrayList<>(matchTasks.keySet())) {
            BukkitTask task = matchTasks.remove(key);
            if (task != null) task.cancel();
        }
        for (Match m : liveMatches.values()) {
            if (m.getAssignedArenaName() != null) {
                plugin.getArenaManager().releaseArena(m.getAssignedArenaName());
            }
        }
        liveMatches.clear();
        matchQueue.clear();
    }

    private void cancelMatchTasks(String matchId) {
        List<String> keys = new ArrayList<>();
        for (String k : matchTasks.keySet()) {
            if (k.startsWith(matchId + ":")) {
                keys.add(k);
            }
        }
        for (String k : keys) {
            BukkitTask t = matchTasks.remove(k);
            if (t != null) t.cancel();
        }
    }

    public Match findMatchForPlayer(UUID playerId) {
        if (playerId == null) return null;
        for (Match m : liveMatches.values()) {
            if (m.isParticipant(playerId)) {
                return m;
            }
        }
        return null;
    }

    public List<Match> getActiveMatches() {
        return new ArrayList<>(liveMatches.values());
    }

    private List<Player> getMatchOnlinePlayers(Match match) {
        List<Player> list = new ArrayList<>();
        if (match.getTeamRed() != null) list.addAll(match.getTeamRed().getOnlineMembers());
        if (match.getTeamBlue() != null) list.addAll(match.getTeamBlue().getOnlineMembers());
        return list;
    }

    private void sendToMatchParticipantsAndSpectators(Match match, String message) {
        Set<UUID> recipients = new HashSet<>();
        if (match.getTeamRed() != null) recipients.addAll(match.getTeamRed().getMembers());
        if (match.getTeamBlue() != null) recipients.addAll(match.getTeamBlue().getMembers());
        for (UUID uid : recipients) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null && p.isOnline()) {
                MessageUtil.send(p, message);
            }
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.getSpectatorManager().isSpectatingMatch(online.getUniqueId(), match.getMatchId())) {
                MessageUtil.send(online, message);
            }
        }
    }
}
