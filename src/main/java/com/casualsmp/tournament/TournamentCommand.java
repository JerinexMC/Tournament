package com.casualsmp.tournament;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TournamentCommand implements CommandExecutor, TabCompleter {

    private final CasualTournament plugin;

    public TournamentCommand(CasualTournament plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.getTournamentGui().openMainMenu(player);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "setlobby" -> handleSetLobby(sender);
            case "join" -> handleJoin(sender, args);
            case "team" -> handleTeam(sender, args);
            case "arena" -> handleArena(sender, args);
            case "create" -> handleCreate(sender, args);
            case "start" -> handleStart(sender);
            case "stop" -> handleStopOrCancel(sender, false);
            case "pause" -> handlePause(sender);
            case "resume" -> handleResume(sender);
            case "cancel" -> handleStopOrCancel(sender, true);
            case "info" -> handleInfo(sender);
            case "list" -> handleList(sender);
            case "bracket" -> handleBracket(sender);
            case "status" -> handleStatus(sender);
            case "forcewinner" -> handleForceWinner(sender, args);
            case "setreward" -> handleSetReward(sender, args);
            case "reload" -> handleReload(sender);
            case "stats" -> handleStats(sender, args);
            case "gui", "menu" -> {
                if (!(sender instanceof Player p)) {
                    MessageUtil.send(sender, "Only players can open the GUI menu.");
                    return true;
                }
                plugin.getTournamentGui().openMainMenu(p);
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.sendPrefixed(sender, plugin, "&6&lCasualTournament Commands:");
        MessageUtil.send(sender, "&e/tournament &7- Open GUI menu");
        MessageUtil.send(sender, "&e/tournament join solo &7- Register as a solo player");
        MessageUtil.send(sender, "&e/tournament team <create|invite|accept|leave|disband|info>");
        MessageUtil.send(sender, "&e/tournament <info|list|bracket|status|stats [player]>");
        if (sender.hasPermission("tournament.admin")) {
            MessageUtil.send(sender, "&cAdmin: &e/tournament create <size>, start, stop, pause, resume, cancel, setlobby, forcewinner <id> <team>, setreward <cmd>, reload, arena ...");
        }
    }

    private void handleSetLobby(CommandSender sender) {
        if (!sender.hasPermission("tournament.admin")) {
            MessageUtil.sendPrefixed(sender, plugin, "&cYou do not have permission to do that.");
            return;
        }
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "Only players can set the lobby location.");
            return;
        }
        plugin.getTournamentManager().setWaitingLobby(player.getLocation());
        MessageUtil.sendPrefixed(sender, plugin, "&aWaiting lobby set to your current location.");
    }

    private void handleJoin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tournament.join")) {
            MessageUtil.sendPrefixed(sender, plugin, "&cYou do not have permission to join tournaments.");
            return;
        }
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "Only players can register.");
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("solo")) {
            MessageUtil.sendPrefixed(sender, plugin, "&cUsage: /tournament join solo");
            return;
        }
        Tournament t = plugin.getTournamentManager().getCurrentTournament();
        if (t == null || t.getState() != TournamentState.REGISTRATION) {
            MessageUtil.sendPrefixed(sender, plugin, "&cTournament registration is not currently open.");
            return;
        }
        if (!t.isSoloRegistrationEnabled()) {
            MessageUtil.sendPrefixed(sender, plugin, "&cSolo registration is disabled for this tournament.");
            return;
        }
        String err = plugin.getTeamManager().joinSolo(player, t.getMaxTeams());
        if (err != null) {
            MessageUtil.sendPrefixed(sender, plugin, "&c" + err);
            return;
        }
        MessageUtil.sendPrefixed(sender, plugin, "&aYou registered in the solo queue!");
        plugin.getTournamentManager().teleportToWaitingLobby(player);
    }

    private void handleTeam(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tournament.team")) {
            MessageUtil.sendPrefixed(sender, plugin, "&cYou do not have permission to manage teams.");
            return;
        }
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "Only players can manage teams.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendPrefixed(sender, plugin, "&cUsage: /tournament team <create|invite|accept|leave|disband|info>");
            return;
        }
        Tournament t = plugin.getTournamentManager().getCurrentTournament();
        int teamSize = t != null ? t.getTeamSize() : Math.max(1, plugin.getConfig().getInt("tournament.min-team-size", 1));
        int maxTeams = t != null ? t.getMaxTeams() : Math.max(2, plugin.getConfig().getInt("tournament.max-teams", 64));

        String action = args[1].toLowerCase(Locale.ROOT);
        TeamManager tm = plugin.getTeamManager();

        switch (action) {
            case "create" -> {
                if (t == null || t.getState() != TournamentState.REGISTRATION) {
                    MessageUtil.sendPrefixed(sender, plugin, "&cRegistration is not open.");
                    return;
                }
                if (args.length < 3) {
                    MessageUtil.sendPrefixed(sender, plugin, "&cUsage: /tournament team create <name>");
                    return;
                }
                String err = tm.createTeam(player, args[2], teamSize, maxTeams);
                if (err != null) {
                    MessageUtil.sendPrefixed(sender, plugin, "&c" + err);
                    return;
                }
                MessageUtil.sendPrefixed(sender, plugin, "&aCreated team &e" + args[2] + "&a! You are the captain.");
                plugin.getTournamentManager().teleportToWaitingLobby(player);
            }
            case "invite" -> {
                if (args.length < 3) {
                    MessageUtil.sendPrefixed(sender, plugin, "&cUsage: /tournament team invite <player>");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null || !target.isOnline()) {
                    MessageUtil.sendPrefixed(sender, plugin, "&cPlayer '" + args[2] + "' is not online.");
                    return;
                }
                String err = tm.invitePlayer(player, target, teamSize);
                if (err != null) {
                    MessageUtil.sendPrefixed(sender, plugin, "&c" + err);
                    return;
                }
                MessageUtil.sendPrefixed(sender, plugin, "&aInvited &e" + target.getName() + "&a to your team.");
                MessageUtil.sendPrefixed(target, plugin, "&e" + player.getName() + " &ainvited you to join their tournament team! Use &e/tournament team accept&a.");
            }
            case "accept" -> {
                String err = tm.acceptInvite(player, teamSize);
                if (err != null) {
                    MessageUtil.sendPrefixed(sender, plugin, "&c" + err);
                    return;
                }
                Team joined = tm.getTeamOfPlayer(player.getUniqueId());
                MessageUtil.sendPrefixed(sender, plugin, "&aYou joined team &e" + (joined != null ? joined.getName() : "") + "&a!");
                plugin.getTournamentManager().teleportToWaitingLobby(player);
            }
            case "leave" -> {
                String err = tm.leaveTeam(player);
                if (err != null) {
                    MessageUtil.sendPrefixed(sender, plugin, "&c" + err);
                    return;
                }
                MessageUtil.sendPrefixed(sender, plugin, "&eYou left your team.");
            }
            case "disband" -> {
                String err = tm.disbandTeam(player);
                if (err != null) {
                    MessageUtil.sendPrefixed(sender, plugin, "&c" + err);
                    return;
                }
                MessageUtil.sendPrefixed(sender, plugin, "&eYou disbanded your team.");
            }
            case "info" -> {
                Team team = tm.getTeamOfPlayer(player.getUniqueId());
                if (team == null) {
                    MessageUtil.sendPrefixed(sender, plugin, "&7You are not in a team.");
                    return;
                }
                MessageUtil.sendPrefixed(sender, plugin, "&6Team &e" + team.getName() + " &7(" + team.size() + "/" + teamSize + ")");
                MessageUtil.send(sender, "&7Captain: &f" + plugin.getStatisticsManager().resolvePlayerName(team.getCaptain()));
                MessageUtil.send(sender, "&7Members: &f" + String.join(", ", team.getMemberNames()));
            }
            default -> MessageUtil.sendPrefixed(sender, plugin, "&cUsage: /tournament team <create|invite|accept|leave|disband|info>");
        }
    }

    private void handleArena(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tournament.admin")) {
            MessageUtil.sendPrefixed(sender, plugin, "&cYou do not have permission to manage arenas.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendPrefixed(sender, plugin, "&cUsage: /tournament arena <create|pos1|pos2|setspawn|list|info|delete|enable|disable> ...");
            return;
        }

        String op = args[1].toLowerCase(Locale.ROOT);
        ArenaManager am = plugin.getArenaManager();

        if (op.equals("list")) {
            MessageUtil.sendPrefixed(sender, plugin, "&6Arenas (" + am.getAllArenas().size() + "):");
            for (Arena a : am.getAllArenas()) {
                MessageUtil.send(sender, "&e" + a.getName() + " &7| Complete: " + (a.isComplete() ? "&aYes" : "&cNo")
                        + " &7| Enabled: " + (a.isEnabled() ? "&aYes" : "&cNo")
                        + " &7| Occupied: " + (a.isOccupied() ? "&cYes (" + a.getAssignedMatchId() + ")" : "&aNo"));
            }
            return;
        }

        if (args.length < 3) {
            MessageUtil.sendPrefixed(sender, plugin, "&cPlease specify an arena name.");
            return;
        }
        String name = args[2];

        switch (op) {
            case "create" -> {
                if (!am.createArena(name)) {
                    MessageUtil.sendPrefixed(sender, plugin, "&cArena '" + name + "' already exists or is invalid.");
                } else {
                    MessageUtil.sendPrefixed(sender, plugin, "&aCreated arena '&e" + name + "&a'. Set pos1, pos2, and spawns next.");
                }
            }
            case "pos1" -> {
                if (!(sender instanceof Player p)) {
                    MessageUtil.send(sender, "Players only.");
                    return;
                }
                if (!am.setPos1(name, p.getLocation())) {
                    MessageUtil.sendPrefixed(sender, plugin, "&cArena '" + name + "' not found.");
                } else {
                    MessageUtil.sendPrefixed(sender, plugin, "&aSet Position 1 for arena '&e" + name + "&a'.");
                }
            }
            case "pos2" -> {
                if (!(sender instanceof Player p)) {
                    MessageUtil.send(sender, "Players only.");
                    return;
                }
                if (!am.setPos2(name, p.getLocation())) {
                    MessageUtil.sendPrefixed(sender, plugin, "&cArena '" + name + "' not found.");
                } else {
                    MessageUtil.sendPrefixed(sender, plugin, "&aSet Position 2 for arena '&e" + name + "&a'.");
                }
            }
            case "setspawn" -> {
                if (!(sender instanceof Player p)) {
                    MessageUtil.send(sender, "Players only.");
                    return;
                }
                if (args.length < 4) {
                    MessageUtil.sendPrefixed(sender, plugin, "&cUsage: /tournament arena setspawn <name> <red|blue|spectator>");
                    return;
                }
                String type = args[3].toLowerCase(Locale.ROOT);
                Location loc = p.getLocation();
                boolean ok = switch (type) {
                    case "red" -> am.setRedSpawn(name, loc);
                    case "blue" -> am.setBlueSpawn(name, loc);
                    case "spectator", "spec" -> am.setSpectatorSpawn(name, loc);
                    default -> false;
                };
                if (!ok) {
                    MessageUtil.sendPrefixed(sender, plugin, "&cInvalid spawn type or arena not found.");
                } else {
                    MessageUtil.sendPrefixed(sender, plugin, "&aSet &e" + type + " &aspawn for arena '&e" + name + "&a'.");
                }
            }
            case "info" -> {
                Arena a = am.getArena(name);
                if (a == null) {
                    MessageUtil.sendPrefixed(sender, plugin, "&cArena '" + name + "' not found.");
                    return;
                }
                MessageUtil.sendPrefixed(sender, plugin, "&6Arena Info: &e" + a.getName());
                MessageUtil.send(sender, "&7World: &f" + a.getWorldName() + " &7(Loaded: " + a.isWorldLoaded() + ")");
                MessageUtil.send(sender, "&7Boundaries: " + (a.hasBoundaries() ? "&aSet" : "&cMissing"));
                MessageUtil.send(sender, "&7Spawns (Red/Blue/Spec): "
                        + (a.getRedSpawn() != null ? "&aR" : "&cR") + "&7/"
                        + (a.getBlueSpawn() != null ? "&aB" : "&cB") + "&7/"
                        + (a.getSpectatorSpawn() != null ? "&aS" : "&cS"));
                MessageUtil.send(sender, "&7Enabled: " + a.isEnabled() + " &7| Occupied: " + a.isOccupied());
            }
            case "delete" -> {
                if (!am.deleteArena(name)) {
                    MessageUtil.sendPrefixed(sender, plugin, "&cArena '" + name + "' not found.");
                } else {
                    MessageUtil.sendPrefixed(sender, plugin, "&aDeleted arena '&e" + name + "&a'.");
                }
            }
            case "enable" -> {
                if (!am.setEnabled(name, true)) {
                    MessageUtil.sendPrefixed(sender, plugin, "&cArena '" + name + "' not found.");
                } else {
                    MessageUtil.sendPrefixed(sender, plugin, "&aEnabled arena '&e" + name + "&a'.");
                }
            }
            case "disable" -> {
                if (!am.setEnabled(name, false)) {
                    MessageUtil.sendPrefixed(sender, plugin, "&cArena '" + name + "' not found.");
                } else {
                    MessageUtil.sendPrefixed(sender, plugin, "&eDisabled arena '&e" + name + "&e'.");
                }
            }
            default -> MessageUtil.sendPrefixed(sender, plugin, "&cUnknown arena subcommand.");
        }
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tournament.admin")) {
            MessageUtil.sendPrefixed(sender, plugin, "&cYou do not have permission to create tournaments.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendPrefixed(sender, plugin, "&cUsage: /tournament create <team-size>");
            return;
        }
        int teamSize;
        try {
            teamSize = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MessageUtil.sendPrefixed(sender, plugin, "&cInvalid team size number: " + args[1]);
            return;
        }
        String err = plugin.getTournamentManager().createTournament(sender, teamSize);
        if (err != null) {
            MessageUtil.sendPrefixed(sender, plugin, "&c" + err);
        } else {
            MessageUtil.sendPrefixed(sender, plugin, "&aCreated &e" + teamSize + "v" + teamSize + " &atournament!");
        }
    }

    private void handleStart(CommandSender sender) {
        if (!sender.hasPermission("tournament.admin")) {
            MessageUtil.sendPrefixed(sender, plugin, "&cYou do not have permission to start tournaments.");
            return;
        }
        String err = plugin.getTournamentManager().startTournament(sender);
        if (err != null) {
            MessageUtil.sendPrefixed(sender, plugin, "&c" + err);
        } else {
            MessageUtil.sendPrefixed(sender, plugin, "&aTournament started!");
        }
    }

    private void handleStopOrCancel(CommandSender sender, boolean cancel) {
        if (!sender.hasPermission("tournament.admin")) {
            MessageUtil.sendPrefixed(sender, plugin, "&cYou do not have permission to do that.");
            return;
        }
        String err = plugin.getTournamentManager().stopOrCancelTournament(cancel);
        if (err != null) {
            MessageUtil.sendPrefixed(sender, plugin, "&c" + err);
        } else {
            MessageUtil.sendPrefixed(sender, plugin, cancel ? "&eTournament cancelled." : "&eTournament stopped.");
        }
    }

    private void handlePause(CommandSender sender) {
        if (!sender.hasPermission("tournament.admin")) {
            MessageUtil.sendPrefixed(sender, plugin, "&cYou do not have permission to pause tournaments.");
            return;
        }
        String err = plugin.getTournamentManager().pauseTournament();
        if (err != null) {
            MessageUtil.sendPrefixed(sender, plugin, "&c" + err);
        } else {
            MessageUtil.sendPrefixed(sender, plugin, "&eTournament paused.");
        }
    }

    private void handleResume(CommandSender sender) {
        if (!sender.hasPermission("tournament.admin")) {
            MessageUtil.sendPrefixed(sender, plugin, "&cYou do not have permission to resume tournaments.");
            return;
        }
        String err = plugin.getTournamentManager().resumeTournament();
        if (err != null) {
            MessageUtil.sendPrefixed(sender, plugin, "&c" + err);
        } else {
            MessageUtil.sendPrefixed(sender, plugin, "&aTournament resumed.");
        }
    }

    private void handleInfo(CommandSender sender) {
        Tournament t = plugin.getTournamentManager().getCurrentTournament();
        if (t == null) {
            MessageUtil.sendPrefixed(sender, plugin, "&7No tournament is currently configured.");
            return;
        }
        MessageUtil.sendPrefixed(sender, plugin, "&6Tournament Info &7[ID: " + t.getId() + "]");
        MessageUtil.send(sender, "&7Mode: &e" + t.getModeLabel() + " &7| State: &a" + t.getState());
        MessageUtil.send(sender, "&7Teams Registered: &e" + plugin.getTeamManager().getAllTeams().size() + "&7/&e" + t.getMaxTeams()
                + " &7| Solo Queue: &e" + plugin.getTeamManager().getSoloQueue().size());
        MessageUtil.send(sender, "&7Ready Arenas: &e" + plugin.getArenaManager().countReadyArenas()
                + "&7/&e" + plugin.getArenaManager().getAllArenas().size());
    }

    private void handleList(CommandSender sender) {
        Collection<Team> teams = plugin.getTeamManager().getAllTeams();
        MessageUtil.sendPrefixed(sender, plugin, "&6Registered Teams (" + teams.size() + "):");
        for (Team team : teams) {
            MessageUtil.send(sender, "&e" + team.getName() + " &7(" + team.size() + "): &f" + String.join(", ", team.getMemberNames()));
        }
        int soloCount = plugin.getTeamManager().getSoloQueue().size();
        if (soloCount > 0) {
            MessageUtil.send(sender, "&bSolo Queue Players: &f" + soloCount);
        }
    }

    private void handleBracket(CommandSender sender) {
        BracketManager bm = plugin.getBracketManager();
        if (bm.getTotalRounds() == 0) {
            MessageUtil.sendPrefixed(sender, plugin, "&7No bracket has been generated yet.");
            return;
        }
        MessageUtil.sendPrefixed(sender, plugin, "&6&lTournament Bracket:");
        for (var entry : bm.getRounds().entrySet()) {
            MessageUtil.send(sender, "&6--- Round " + entry.getKey() + " ---");
            for (Match m : entry.getValue()) {
                String red = m.getTeamRed() != null ? m.getTeamRed().getName() : "TBD";
                String blue = m.isByeMatch() ? "BYE" : (m.getTeamBlue() != null ? m.getTeamBlue().getName() : "TBD");
                String win = m.getWinner() != null ? " &8[Winner: &a" + m.getWinner().getName() + "&8]" : "";
                MessageUtil.send(sender, "&7[" + m.getMatchId() + "] &e" + red + " &7vs &b" + blue + " &8(" + m.getStatus() + ")" + win);
            }
        }
    }

    private void handleStatus(CommandSender sender) {
        List<Match> active = plugin.getTournamentManager().getActiveMatches();
        MessageUtil.sendPrefixed(sender, plugin, "&6Active Matches (" + active.size() + "):");
        for (Match m : active) {
            String red = m.getTeamRed() != null ? m.getTeamRed().getName() : "TBD";
            String blue = m.getTeamBlue() != null ? m.getTeamBlue().getName() : "TBD";
            MessageUtil.send(sender, "&7[" + m.getMatchId() + "] &e" + red + " &7vs &b" + blue
                    + " &7| Arena: &e" + m.getAssignedArenaName()
                    + " &7| Status: &a" + m.getStatus());
        }
    }

    private void handleForceWinner(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tournament.admin")) {
            MessageUtil.sendPrefixed(sender, plugin, "&cYou do not have permission to force match winners.");
            return;
        }
        if (args.length < 3) {
            MessageUtil.sendPrefixed(sender, plugin, "&cUsage: /tournament forcewinner <match-id> <team>");
            return;
        }
        String err = plugin.getTournamentManager().forceWinner(args[1], args[2]);
        if (err != null) {
            MessageUtil.sendPrefixed(sender, plugin, "&c" + err);
        } else {
            MessageUtil.sendPrefixed(sender, plugin, "&aForced winner &e" + args[2] + " &afor match &e" + args[1] + "&a.");
        }
    }

    private void handleSetReward(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tournament.admin")) {
            MessageUtil.sendPrefixed(sender, plugin, "&cYou do not have permission to set rewards.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendPrefixed(sender, plugin, "&cUsage: /tournament setreward <command with %player%>");
            return;
        }
        String cmd = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList("rewards.winner"));
        list.add(cmd);
        plugin.getConfig().set("rewards.winner", list);
        plugin.saveConfig();

        Tournament t = plugin.getTournamentManager().getCurrentTournament();
        if (t != null) {
            t.getWinnerRewardCommands().add(cmd);
        }
        MessageUtil.sendPrefixed(sender, plugin, "&aAdded winner reward command: &f" + cmd);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("tournament.admin")) {
            MessageUtil.sendPrefixed(sender, plugin, "&cYou do not have permission to reload the plugin.");
            return;
        }
        plugin.reloadConfig();
        MessageUtil.sendPrefixed(sender, plugin, "&aCasualTournament configuration reloaded.");
    }

    private void handleStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tournament.stats")) {
            MessageUtil.sendPrefixed(sender, plugin, "&cYou do not have permission to view statistics.");
            return;
        }
        UUID targetId;
        String targetName;
        if (args.length >= 2) {
            Player online = Bukkit.getPlayerExact(args[1]);
            if (online != null) {
                targetId = online.getUniqueId();
                targetName = online.getName();
            } else {
                OfflinePlayer off = Bukkit.getOfflinePlayer(args[1]);
                targetId = off.getUniqueId();
                targetName = off.getName() != null ? off.getName() : args[1];
            }
        } else if (sender instanceof Player p) {
            targetId = p.getUniqueId();
            targetName = p.getName();
        } else {
            MessageUtil.send(sender, "Usage: /tournament stats <player>");
            return;
        }

        StatisticsManager.PlayerStats stats = plugin.getStatisticsManager().getOrCreateStats(targetId);
        MessageUtil.sendPrefixed(sender, plugin, "&6Statistics for &e" + targetName + "&6:");
        MessageUtil.send(sender, "&7Tournaments Played: &f" + stats.getTournamentsPlayed() + " &7| Won: &a" + stats.getTournamentsWon());
        MessageUtil.send(sender, "&7Championships: &6" + stats.getChampionships());
        MessageUtil.send(sender, "&7Matches Played: &f" + stats.getMatchesPlayed()
                + " &7| Won: &a" + stats.getMatchesWon() + " &7| Losses: &c" + stats.getMatchLosses());
        MessageUtil.send(sender, "&7Eliminations: &e" + stats.getEliminations());
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.addAll(List.of("join", "team", "info", "list", "bracket", "status", "stats", "gui"));
            if (sender.hasPermission("tournament.admin")) {
                suggestions.addAll(List.of("create", "start", "stop", "pause", "resume", "cancel", "setlobby", "arena", "forcewinner", "setreward", "reload"));
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "join" -> suggestions.add("solo");
                case "team" -> suggestions.addAll(List.of("create", "invite", "accept", "leave", "disband", "info"));
                case "arena" -> {
                    if (sender.hasPermission("tournament.admin")) {
                        suggestions.addAll(List.of("create", "pos1", "pos2", "setspawn", "list", "info", "delete", "enable", "disable"));
                    }
                }
                case "create" -> {
                    if (sender.hasPermission("tournament.admin")) {
                        suggestions.addAll(List.of("1", "2", "3", "4", "5", "6", "10"));
                    }
                }
                case "forcewinner" -> {
                    if (sender.hasPermission("tournament.admin")) {
                        for (Match m : plugin.getTournamentManager().getActiveMatches()) {
                            suggestions.add(m.getMatchId());
                        }
                    }
                }
                case "stats" -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        suggestions.add(p.getName());
                    }
                }
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String sub2 = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("team") && sub2.equals("invite")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    suggestions.add(p.getName());
                }
            } else if (sub.equals("arena") && !sub2.equals("list") && !sub2.equals("create") && sender.hasPermission("tournament.admin")) {
                suggestions.addAll(plugin.getArenaManager().getArenaNames());
            } else if (sub.equals("forcewinner") && sender.hasPermission("tournament.admin")) {
                Match m = plugin.getBracketManager().getMatchById(args[1]);
                if (m != null) {
                    if (m.getTeamRed() != null) suggestions.add(m.getTeamRed().getName());
                    if (m.getTeamBlue() != null) suggestions.add(m.getTeamBlue().getName());
                }
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("setspawn") && sender.hasPermission("tournament.admin")) {
                suggestions.addAll(List.of("red", "blue", "spectator"));
            }
        }

        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String s : suggestions) {
            if (s.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                filtered.add(s);
            }
        }
        return filtered;
    }
}
