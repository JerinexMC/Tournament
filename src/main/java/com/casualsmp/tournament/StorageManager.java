package com.casualsmp.tournament;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StorageManager {

    private final CasualTournament plugin;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CasualTournament-SQLite");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;

    public StorageManager(CasualTournament plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                return false;
            }
            File dbFile = new File(plugin.getDataFolder(), "tournament.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            try (Statement st = connection.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS arenas (
                        name TEXT PRIMARY KEY,
                        world TEXT,
                        pos1_x REAL, pos1_y REAL, pos1_z REAL,
                        pos2_x REAL, pos2_y REAL, pos2_z REAL,
                        red_x REAL, red_y REAL, red_z REAL, red_yaw REAL, red_pitch REAL,
                        blue_x REAL, blue_y REAL, blue_z REAL, blue_yaw REAL, blue_pitch REAL,
                        spec_x REAL, spec_y REAL, spec_z REAL, spec_yaw REAL, spec_pitch REAL,
                        enabled INTEGER NOT NULL DEFAULT 1
                    );
                    """);

                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS metadata (
                        key TEXT PRIMARY KEY,
                        value TEXT
                    );
                    """);

                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        uuid TEXT PRIMARY KEY,
                        tournaments_played INTEGER NOT NULL DEFAULT 0,
                        tournaments_won INTEGER NOT NULL DEFAULT 0,
                        matches_played INTEGER NOT NULL DEFAULT 0,
                        matches_won INTEGER NOT NULL DEFAULT 0,
                        match_losses INTEGER NOT NULL DEFAULT 0,
                        eliminations INTEGER NOT NULL DEFAULT 0,
                        championships INTEGER NOT NULL DEFAULT 0
                    );
                    """);

                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS inventory_backups (
                        uuid TEXT PRIMARY KEY,
                        data BLOB NOT NULL
                    );
                    """);

                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tournament_records (
                        id TEXT PRIMARY KEY,
                        team_size INTEGER NOT NULL,
                        state TEXT NOT NULL,
                        champion TEXT,
                        runner_up TEXT,
                        rewards_distributed INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    );
                    """);
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("SQLite initialization error: " + e.getMessage());
            return false;
        }
    }

    public synchronized void close() {
        dbExecutor.shutdown();
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing SQLite connection: " + e.getMessage());
            }
        }
    }

    public synchronized void saveMetadata(String key, String value) {
        if (connection == null) return;
        String sql = "INSERT INTO metadata(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed saving metadata '" + key + "': " + e.getMessage());
        }
    }

    public void saveMetadataAsync(String key, String value) {
        dbExecutor.submit(() -> saveMetadata(key, value));
    }

    public synchronized String getMetadata(String key) {
        if (connection == null) return null;
        String sql = "SELECT value FROM metadata WHERE key = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed reading metadata '" + key + "': " + e.getMessage());
        }
        return null;
    }

    public synchronized void saveArenaSync(Arena arena) {
        if (connection == null || arena == null) return;
        String sql = """
            INSERT INTO arenas(
                name, world,
                pos1_x, pos1_y, pos1_z,
                pos2_x, pos2_y, pos2_z,
                red_x, red_y, red_z, red_yaw, red_pitch,
                blue_x, blue_y, blue_z, blue_yaw, blue_pitch,
                spec_x, spec_y, spec_z, spec_yaw, spec_pitch,
                enabled
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(name) DO UPDATE SET
                world = excluded.world,
                pos1_x = excluded.pos1_x, pos1_y = excluded.pos1_y, pos1_z = excluded.pos1_z,
                pos2_x = excluded.pos2_x, pos2_y = excluded.pos2_y, pos2_z = excluded.pos2_z,
                red_x = excluded.red_x, red_y = excluded.red_y, red_z = excluded.red_z, red_yaw = excluded.red_yaw, red_pitch = excluded.red_pitch,
                blue_x = excluded.blue_x, blue_y = excluded.blue_y, blue_z = excluded.blue_z, blue_yaw = excluded.blue_yaw, blue_pitch = excluded.blue_pitch,
                spec_x = excluded.spec_x, spec_y = excluded.spec_y, spec_z = excluded.spec_z, spec_yaw = excluded.spec_yaw, spec_pitch = excluded.spec_pitch,
                enabled = excluded.enabled
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, arena.getName());
            ps.setString(2, arena.getWorldName());

            setNullableXYZ(ps, 3, arena.getPos1());
            setNullableXYZ(ps, 6, arena.getPos2());
            setNullableFullLoc(ps, 9, arena.getRedSpawn());
            setNullableFullLoc(ps, 14, arena.getBlueSpawn());
            setNullableFullLoc(ps, 19, arena.getSpectatorSpawn());

            ps.setInt(24, arena.isEnabled() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed saving arena '" + arena.getName() + "': " + e.getMessage());
        }
    }

    public void saveArenaAsync(Arena arena) {
        dbExecutor.submit(() -> saveArenaSync(arena));
    }

    public synchronized void deleteArenaAsync(String arenaName) {
        dbExecutor.submit(() -> {
            if (connection == null) return;
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM arenas WHERE LOWER(name) = LOWER(?)")) {
                ps.setString(1, arenaName);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed deleting arena '" + arenaName + "': " + e.getMessage());
            }
        });
    }

    public synchronized List<Arena> loadArenas() {
        List<Arena> list = new ArrayList<>();
        if (connection == null) return list;
        String sql = "SELECT * FROM arenas";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Arena arena = new Arena(rs.getString("name"));
                String worldName = rs.getString("world");
                arena.setWorldName(worldName);
                World world = worldName != null ? Bukkit.getWorld(worldName) : null;

                Location p1 = readXYZ(rs, world, "pos1_x", "pos1_y", "pos1_z");
                Location p2 = readXYZ(rs, world, "pos2_x", "pos2_y", "pos2_z");
                Location red = readFullLoc(rs, world, "red_x", "red_y", "red_z", "red_yaw", "red_pitch");
                Location blue = readFullLoc(rs, world, "blue_x", "blue_y", "blue_z", "blue_yaw", "blue_pitch");
                Location spec = readFullLoc(rs, world, "spec_x", "spec_y", "spec_z", "spec_yaw", "spec_pitch");

                if (p1 != null) arena.setPos1(p1);
                if (p2 != null) arena.setPos2(p2);
                if (red != null) arena.setRedSpawn(red);
                if (blue != null) arena.setBlueSpawn(blue);
                if (spec != null) arena.setSpectatorSpawn(spec);

                arena.setEnabled(rs.getInt("enabled") == 1);
                list.add(arena);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed loading arenas: " + e.getMessage());
        }
        return list;
    }

    private void setNullableXYZ(PreparedStatement ps, int idx, Location loc) throws SQLException {
        if (loc == null) {
            ps.setNull(idx, Types.REAL);
            ps.setNull(idx + 1, Types.REAL);
            ps.setNull(idx + 2, Types.REAL);
        } else {
            ps.setDouble(idx, loc.getX());
            ps.setDouble(idx + 1, loc.getY());
            ps.setDouble(idx + 2, loc.getZ());
        }
    }

    private void setNullableFullLoc(PreparedStatement ps, int idx, Location loc) throws SQLException {
        if (loc == null) {
            ps.setNull(idx, Types.REAL);
            ps.setNull(idx + 1, Types.REAL);
            ps.setNull(idx + 2, Types.REAL);
            ps.setNull(idx + 3, Types.REAL);
            ps.setNull(idx + 4, Types.REAL);
        } else {
            ps.setDouble(idx, loc.getX());
            ps.setDouble(idx + 1, loc.getY());
            ps.setDouble(idx + 2, loc.getZ());
            ps.setFloat(idx + 3, loc.getYaw());
            ps.setFloat(idx + 4, loc.getPitch());
        }
    }

    private Location readXYZ(ResultSet rs, World world, String xCol, String yCol, String zCol) throws SQLException {
        double x = rs.getDouble(xCol);
        if (rs.wasNull()) return null;
        double y = rs.getDouble(yCol);
        double z = rs.getDouble(zCol);
        return new Location(world, x, y, z);
    }

    private Location readFullLoc(ResultSet rs, World world, String xCol, String yCol, String zCol, String yawCol, String pitchCol) throws SQLException {
        double x = rs.getDouble(xCol);
        if (rs.wasNull()) return null;
        double y = rs.getDouble(yCol);
        double z = rs.getDouble(zCol);
        float yaw = rs.getFloat(yawCol);
        float pitch = rs.getFloat(pitchCol);
        return new Location(world, x, y, z, yaw, pitch);
    }

    public synchronized void savePlayerStatsSync(StatisticsManager.PlayerStats stats) {
        if (connection == null || stats == null) return;
        String sql = """
            INSERT INTO player_stats(
                uuid, tournaments_played, tournaments_won, matches_played,
                matches_won, match_losses, eliminations, championships
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                tournaments_played = excluded.tournaments_played,
                tournaments_won = excluded.tournaments_won,
                matches_played = excluded.matches_played,
                matches_won = excluded.matches_won,
                match_losses = excluded.match_losses,
                eliminations = excluded.eliminations,
                championships = excluded.championships
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, stats.getPlayerId().toString());
            ps.setInt(2, stats.getTournamentsPlayed());
            ps.setInt(3, stats.getTournamentsWon());
            ps.setInt(4, stats.getMatchesPlayed());
            ps.setInt(5, stats.getMatchesWon());
            ps.setInt(6, stats.getMatchLosses());
            ps.setInt(7, stats.getEliminations());
            ps.setInt(8, stats.getChampionships());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed saving player stats for " + stats.getPlayerId() + ": " + e.getMessage());
        }
    }

    public void savePlayerStatsAsync(StatisticsManager.PlayerStats stats) {
        dbExecutor.submit(() -> savePlayerStatsSync(stats));
    }

    public synchronized Map<UUID, StatisticsManager.PlayerStats> loadAllStats() {
        Map<UUID, StatisticsManager.PlayerStats> map = new HashMap<>();
        if (connection == null) return map;
        String sql = "SELECT * FROM player_stats";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                UUID pid = UUID.fromString(rs.getString("uuid"));
                StatisticsManager.PlayerStats ps = new StatisticsManager.PlayerStats(
                        pid,
                        rs.getInt("tournaments_played"),
                        rs.getInt("tournaments_won"),
                        rs.getInt("matches_played"),
                        rs.getInt("matches_won"),
                        rs.getInt("match_losses"),
                        rs.getInt("eliminations"),
                        rs.getInt("championships")
                );
                map.put(pid, ps);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed loading player stats: " + e.getMessage());
        }
        return map;
    }

    public synchronized void saveInventorySnapshotSync(InventorySnapshot snapshot) {
        if (connection == null || snapshot == null) return;
        String sql = "INSERT INTO inventory_backups(uuid, data) VALUES(?, ?) ON CONFLICT(uuid) DO UPDATE SET data = excluded.data";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, snapshot.getPlayerId().toString());
            ps.setBytes(2, snapshot.serialize());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed saving inventory backup for " + snapshot.getPlayerId() + ": " + e.getMessage());
        }
    }

    public void saveInventorySnapshotAsync(InventorySnapshot snapshot) {
        dbExecutor.submit(() -> saveInventorySnapshotSync(snapshot));
    }

    public synchronized void deleteInventorySnapshotSync(UUID playerId) {
        if (connection == null || playerId == null) return;
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM inventory_backups WHERE uuid = ?")) {
            ps.setString(1, playerId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed deleting inventory backup for " + playerId + ": " + e.getMessage());
        }
    }

    public void deleteInventorySnapshotAsync(UUID playerId) {
        dbExecutor.submit(() -> deleteInventorySnapshotSync(playerId));
    }

    public synchronized Map<UUID, InventorySnapshot> loadInventorySnapshots() {
        Map<UUID, InventorySnapshot> map = new HashMap<>();
        if (connection == null) return map;
        String sql = "SELECT uuid, data FROM inventory_backups";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                byte[] bytes = rs.getBytes("data");
                if (bytes != null && bytes.length > 0) {
                    InventorySnapshot snap = InventorySnapshot.deserialize(bytes);
                    map.put(snap.getPlayerId(), snap);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed loading inventory backups: " + e.getMessage());
        }
        return map;
    }

    public synchronized void saveTournamentRecordSync(Tournament t) {
        if (connection == null || t == null) return;
        String sql = """
            INSERT INTO tournament_records(id, team_size, state, champion, runner_up, rewards_distributed, created_at)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                state = excluded.state,
                champion = excluded.champion,
                runner_up = excluded.runner_up,
                rewards_distributed = excluded.rewards_distributed
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, t.getId());
            ps.setInt(2, t.getTeamSize());
            ps.setString(3, t.getState().name());
            ps.setString(4, t.getChampionTeamName());
            ps.setString(5, t.getRunnerUpTeamName());
            ps.setInt(6, t.isRewardsDistributed() ? 1 : 0);
            ps.setLong(7, t.getCreatedAtMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed saving tournament record: " + e.getMessage());
        }
    }

    public void saveTournamentRecordAsync(Tournament t) {
        dbExecutor.submit(() -> saveTournamentRecordSync(t));
    }
}
