package com.casualsmp.tournament;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class Arena {

    private final String name;
    private String worldName;
    private Location pos1;
    private Location pos2;
    private Location redSpawn;
    private Location blueSpawn;
    private Location spectatorSpawn;
    private boolean enabled;
    private boolean occupied;
    private String assignedMatchId;

    public Arena(String name) {
        this.name = name;
        this.enabled = true;
        this.occupied = false;
        this.assignedMatchId = null;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public World getWorld() {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        return Bukkit.getWorld(worldName);
    }

    public boolean isWorldLoaded() {
        return getWorld() != null;
    }

    public Location getPos1() {
        return pos1 != null ? pos1.clone() : null;
    }

    public void setPos1(Location pos1) {
        if (pos1 != null) {
            this.pos1 = pos1.clone();
            if (pos1.getWorld() != null) {
                this.worldName = pos1.getWorld().getName();
            }
        } else {
            this.pos1 = null;
        }
    }

    public Location getPos2() {
        return pos2 != null ? pos2.clone() : null;
    }

    public void setPos2(Location pos2) {
        if (pos2 != null) {
            this.pos2 = pos2.clone();
            if (pos2.getWorld() != null) {
                this.worldName = pos2.getWorld().getName();
            }
        } else {
            this.pos2 = null;
        }
    }

    public Location getRedSpawn() {
        return redSpawn != null ? redSpawn.clone() : null;
    }

    public void setRedSpawn(Location redSpawn) {
        if (redSpawn != null) {
            this.redSpawn = redSpawn.clone();
            if (redSpawn.getWorld() != null) {
                this.worldName = redSpawn.getWorld().getName();
            }
        } else {
            this.redSpawn = null;
        }
    }

    public Location getBlueSpawn() {
        return blueSpawn != null ? blueSpawn.clone() : null;
    }

    public void setBlueSpawn(Location blueSpawn) {
        if (blueSpawn != null) {
            this.blueSpawn = blueSpawn.clone();
            if (blueSpawn.getWorld() != null) {
                this.worldName = blueSpawn.getWorld().getName();
            }
        } else {
            this.blueSpawn = null;
        }
    }

    public Location getSpectatorSpawn() {
        return spectatorSpawn != null ? spectatorSpawn.clone() : null;
    }

    public void setSpectatorSpawn(Location spectatorSpawn) {
        if (spectatorSpawn != null) {
            this.spectatorSpawn = spectatorSpawn.clone();
            if (spectatorSpawn.getWorld() != null) {
                this.worldName = spectatorSpawn.getWorld().getName();
            }
        } else {
            this.spectatorSpawn = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
        if (!occupied) {
            this.assignedMatchId = null;
        }
    }

    public String getAssignedMatchId() {
        return assignedMatchId;
    }

    public void setAssignedMatchId(String assignedMatchId) {
        this.assignedMatchId = assignedMatchId;
        this.occupied = (assignedMatchId != null);
    }

    public boolean hasBoundaries() {
        return pos1 != null && pos2 != null;
    }

    public boolean hasSpawns() {
        return redSpawn != null && blueSpawn != null && spectatorSpawn != null;
    }

    public boolean isComplete() {
        return worldName != null && !worldName.isBlank() && hasBoundaries() && hasSpawns();
    }

    public boolean isReadyForMatch() {
        return enabled && !occupied && isComplete() && isWorldLoaded();
    }

    public boolean isInside(Location loc) {
        if (loc == null || !hasBoundaries()) {
            return false;
        }
        World w = getWorld();
        if (w == null || loc.getWorld() == null || !loc.getWorld().getUID().equals(w.getUID())) {
            return false;
        }
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());

        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getY() >= minY && loc.getY() <= maxY
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }
}
