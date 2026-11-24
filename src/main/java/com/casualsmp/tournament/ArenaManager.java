package com.casualsmp.tournament;

import org.bukkit.Location;

import java.util.*;

public class ArenaManager {

    private final CasualTournament plugin;
    private final StorageManager storageManager;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(CasualTournament plugin, StorageManager storageManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
    }

    public void loadArenas() {
        arenas.clear();
        List<Arena> loaded = storageManager.loadArenas();
        for (Arena arena : loaded) {
            arena.setOccupied(false);
            arena.setAssignedMatchId(null);
            arenas.put(arena.getName().toLowerCase(Locale.ROOT), arena);
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " arenas from storage.");
    }

    public void saveArena(Arena arena) {
        if (arena == null) return;
        storageManager.saveArenaAsync(arena);
    }

    public void saveAllArenasSync() {
        for (Arena arena : arenas.values()) {
            storageManager.saveArenaSync(arena);
        }
    }

    public boolean createArena(String name) {
        if (name == null || name.isBlank()) return false;
        String key = name.toLowerCase(Locale.ROOT);
        if (arenas.containsKey(key)) return false;
        Arena arena = new Arena(name);
        arenas.put(key, arena);
        saveArena(arena);
        return true;
    }

    public boolean deleteArena(String name) {
        if (name == null) return false;
        String key = name.toLowerCase(Locale.ROOT);
        Arena removed = arenas.remove(key);
        if (removed == null) return false;
        storageManager.deleteArenaAsync(removed.getName());
        return true;
    }

    public Arena getArena(String name) {
        if (name == null) return null;
        return arenas.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Arena> getAllArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public List<String> getArenaNames() {
        List<String> names = new ArrayList<>();
        for (Arena a : arenas.values()) {
            names.add(a.getName());
        }
        return names;
    }

    public boolean setPos1(String name, Location loc) {
        Arena arena = getArena(name);
        if (arena == null || loc == null) return false;
        arena.setPos1(loc);
        saveArena(arena);
        return true;
    }

    public boolean setPos2(String name, Location loc) {
        Arena arena = getArena(name);
        if (arena == null || loc == null) return false;
        arena.setPos2(loc);
        saveArena(arena);
        return true;
    }

    public boolean setRedSpawn(String name, Location loc) {
        Arena arena = getArena(name);
        if (arena == null || loc == null) return false;
        arena.setRedSpawn(loc);
        saveArena(arena);
        return true;
    }

    public boolean setBlueSpawn(String name, Location loc) {
        Arena arena = getArena(name);
        if (arena == null || loc == null) return false;
        arena.setBlueSpawn(loc);
        saveArena(arena);
        return true;
    }

    public boolean setSpectatorSpawn(String name, Location loc) {
        Arena arena = getArena(name);
        if (arena == null || loc == null) return false;
        arena.setSpectatorSpawn(loc);
        saveArena(arena);
        return true;
    }

    public boolean setEnabled(String name, boolean enabled) {
        Arena arena = getArena(name);
        if (arena == null) return false;
        arena.setEnabled(enabled);
        saveArena(arena);
        return true;
    }

    public synchronized Arena acquireAvailableArena(String matchId) {
        for (Arena arena : arenas.values()) {
            if (arena.isReadyForMatch()) {
                arena.setAssignedMatchId(matchId);
                return arena;
            }
        }
        return null;
    }

    public synchronized void releaseArena(String arenaName) {
        if (arenaName == null) return;
        Arena arena = getArena(arenaName);
        if (arena != null) {
            arena.setOccupied(false);
            arena.setAssignedMatchId(null);
        }
    }

    public int countReadyArenas() {
        int count = 0;
        for (Arena arena : arenas.values()) {
            if (arena.isReadyForMatch()) count++;
        }
        return count;
    }
}
