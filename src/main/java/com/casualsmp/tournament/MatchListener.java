package com.casualsmp.tournament;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;

public class MatchListener implements Listener {

    private final CasualTournament plugin;

    public MatchListener(CasualTournament plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("protection.prevent-block-break", true)) return;
        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            if (arena.isOccupied() && arena.isInside(event.getBlock().getLocation())) {
                event.setCancelled(true);
                MessageUtil.sendPrefixed(event.getPlayer(), plugin, "&cYou cannot break blocks inside an active tournament arena.");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("protection.prevent-block-place", true)) return;
        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            if (arena.isOccupied() && arena.isInside(event.getBlock().getLocation())) {
                event.setCancelled(true);
                MessageUtil.sendPrefixed(event.getPlayer(), plugin, "&cYou cannot place blocks inside an active tournament arena.");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (plugin.getSpectatorManager().isSpectatingAnyMatch(p.getUniqueId())
                && plugin.getConfig().getBoolean("spectator.prevent-block-interaction", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null) return;

        if (plugin.getSpectatorManager().isSpectatingAnyMatch(attacker.getUniqueId())
                && plugin.getConfig().getBoolean("spectator.prevent-damage", true)) {
            event.setCancelled(true);
            return;
        }

        Match victimMatch = plugin.getTournamentManager().findMatchForPlayer(victim.getUniqueId());
        Match attackerMatch = plugin.getTournamentManager().findMatchForPlayer(attacker.getUniqueId());

        if (victimMatch != null && !victimMatch.isCombatEnabled()) {
            event.setCancelled(true);
            return;
        }
        if (attackerMatch != null && !attackerMatch.isCombatEnabled()) {
            event.setCancelled(true);
            return;
        }

        if (plugin.getConfig().getBoolean("protection.prevent-outsider-combat", true)) {
            if ((victimMatch == null) != (attackerMatch == null)) {
                event.setCancelled(true);
                return;
            }
            if (victimMatch != null && attackerMatch != null && !victimMatch.getMatchId().equals(attackerMatch.getMatchId())) {
                event.setCancelled(true);
                return;
            }
            if (victimMatch != null && victimMatch.getTeamOf(victim.getUniqueId()) != null
                    && victimMatch.getTeamOf(victim.getUniqueId()) == victimMatch.getTeamOf(attacker.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Match match = plugin.getTournamentManager().findMatchForPlayer(victim.getUniqueId());
        if (match == null) return;

        if (plugin.getInventoryManager().hasSnapshot(victim.getUniqueId())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        Player killer = victim.getKiller();
        plugin.getTournamentManager().handlePlayerEliminated(victim, killer);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        Match match = plugin.getTournamentManager().findMatchForPlayer(p.getUniqueId());
        if (match == null) return;
        if (match.getStatus() != MatchStatus.ACTIVE && match.getStatus() != MatchStatus.COUNTDOWN) return;
        if (match.isEliminated(p.getUniqueId())) return;

        Arena arena = plugin.getArenaManager().getArena(match.getAssignedArenaName());
        if (arena != null && arena.hasBoundaries() && !arena.isInside(event.getTo())) {
            Location safe = arena.getRedSpawn() != null ? arena.getRedSpawn() : event.getFrom();
            if (match.getTeamBlue() != null && match.getTeamBlue().hasMember(p.getUniqueId()) && arena.getBlueSpawn() != null) {
                safe = arena.getBlueSpawn();
            }
            event.setTo(safe);
            MessageUtil.sendPrefixed(p, plugin, "&cYou cannot leave the arena boundaries during an active match!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!plugin.getConfig().getBoolean("protection.prevent-unauthorized-teleport", true)) return;
        Player p = event.getPlayer();
        if (p.hasPermission("tournament.admin")) return;

        Match match = plugin.getTournamentManager().findMatchForPlayer(p.getUniqueId());
        if (match != null && !match.isEliminated(p.getUniqueId())
                && (match.getStatus() == MatchStatus.ACTIVE || match.getStatus() == MatchStatus.COUNTDOWN)) {
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND
                    || event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
                Arena arena = plugin.getArenaManager().getArena(match.getAssignedArenaName());
                if (arena != null && arena.hasBoundaries() && !arena.isInside(event.getTo())) {
                    event.setCancelled(true);
                    MessageUtil.sendPrefixed(p, plugin, "&cTeleportation outside the arena is blocked during an active match.");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (p.hasPermission("tournament.admin")) return;

        Match match = plugin.getTournamentManager().findMatchForPlayer(p.getUniqueId());
        if (match == null) return;

        String raw = event.getMessage().trim().toLowerCase(Locale.ROOT);
        List<String> blocked = plugin.getConfig().getStringList("protection.blocked-commands");
        for (String cmd : blocked) {
            if (cmd == null || cmd.isBlank()) continue;
            String normalized = cmd.trim().toLowerCase(Locale.ROOT);
            if (raw.equals(normalized) || raw.startsWith(normalized + " ")) {
                event.setCancelled(true);
                MessageUtil.sendPrefixed(p, plugin, "&cYou cannot use '" + cmd + "' during an active tournament match.");
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getTournamentManager().handlePlayerDisconnect(event.getPlayer());
        plugin.getSpectatorManager().removeSpectator(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getTournamentManager().handlePlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof TournamentGui) {
            event.setCancelled(true);
        }
    }
}
