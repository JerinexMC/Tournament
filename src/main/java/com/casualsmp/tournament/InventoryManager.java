package com.casualsmp.tournament;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryManager {

    private final CasualTournament plugin;
    private final StorageManager storageManager;
    private final Map<UUID, InventorySnapshot> snapshots = new ConcurrentHashMap<>();

    public InventoryManager(CasualTournament plugin, StorageManager storageManager) {
        this.plugin = plugin;
        this.storageManager = storageManager;
    }

    public void loadPendingBackups() {
        Map<UUID, InventorySnapshot> loaded = storageManager.loadInventorySnapshots();
        snapshots.clear();
        snapshots.putAll(loaded);
        plugin.getLogger().info("Loaded " + snapshots.size() + " pending player inventory backups.");
    }

    public boolean hasSnapshot(UUID playerId) {
        return playerId != null && snapshots.containsKey(playerId);
    }

    public InventorySnapshot getSnapshot(UUID playerId) {
        return snapshots.get(playerId);
    }

    /**
     * Captures the player's full survival state before match entry and persists a backup to SQLite.
     * Never overwrites an already-saved snapshot for the same player so original items are protected.
     */
    public void backupAndPrepareForMatch(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID pid = player.getUniqueId();

        if (!snapshots.containsKey(pid)) {
            InventorySnapshot snap = new InventorySnapshot(player);
            snapshots.put(pid, snap);
            storageManager.saveInventorySnapshotAsync(snap);
        }

        if (plugin.getConfig().getBoolean("inventory.clear-effects-on-start", true)) {
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }
        }

        double maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                ? player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                : 20.0;
        player.setHealth(maxHp);
        player.setFoodLevel(20);
        player.setSaturation(10.0f);
        player.setGameMode(GameMode.SURVIVAL);

        if (plugin.getConfig().getBoolean("inventory.use-tournament-kit", false)) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setItemInOffHand(null);
            applyTournamentKit(player);
        }
        player.updateInventory();
    }

    private void applyTournamentKit(Player player) {
        String helmetName = plugin.getConfig().getString("inventory.kit-helmet", "DIAMOND_HELMET");
        String chestName = plugin.getConfig().getString("inventory.kit-chestplate", "DIAMOND_CHESTPLATE");
        String legsName = plugin.getConfig().getString("inventory.kit-leggings", "DIAMOND_LEGGINGS");
        String bootsName = plugin.getConfig().getString("inventory.kit-boots", "DIAMOND_BOOTS");

        player.getInventory().setHelmet(parseMaterialStack(helmetName, 1));
        player.getInventory().setChestplate(parseMaterialStack(chestName, 1));
        player.getInventory().setLeggings(parseMaterialStack(legsName, 1));
        player.getInventory().setBoots(parseMaterialStack(bootsName, 1));

        List<String> items = plugin.getConfig().getStringList("inventory.kit-items");
        for (String entry : items) {
            if (entry == null || entry.isBlank()) continue;
            String[] parts = entry.split(":");
            Material mat = Material.matchMaterial(parts[0].trim());
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(parts[1].trim())));
                } catch (NumberFormatException ignored) {
                }
            }
            if (mat != null && mat != Material.AIR) {
                player.getInventory().addItem(new ItemStack(mat, amount));
            }
        }
    }

    private ItemStack parseMaterialStack(String name, int amount) {
        if (name == null || name.isBlank() || name.equalsIgnoreCase("NONE")) {
            return null;
        }
        Material mat = Material.matchMaterial(name.trim());
        if (mat == null || mat == Material.AIR) {
            return null;
        }
        return new ItemStack(mat, amount);
    }

    /**
     * Restores the player's snapshot if online and removes the persisted snapshot.
     * If the player is offline, the snapshot remains stored until they join or server restarts.
     */
    public void restoreIfOnline(UUID playerId, boolean restoreLocation) {
        if (playerId == null) return;
        InventorySnapshot snap = snapshots.get(playerId);
        if (snap == null) return;

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            if (plugin.getConfig().getBoolean("inventory.restore-on-exit", true)) {
                snap.applyTo(player, restoreLocation);
            } else {
                player.setGameMode(GameMode.SURVIVAL);
            }
            snapshots.remove(playerId);
            storageManager.deleteInventorySnapshotAsync(playerId);
        }
    }

    public void restoreAllOnlineAndPersistPending() {
        for (UUID pid : new ArrayList<>(snapshots.keySet())) {
            InventorySnapshot snap = snapshots.get(pid);
            if (snap == null) continue;
            Player player = Bukkit.getPlayer(pid);
            if (player != null && player.isOnline()) {
                if (plugin.getConfig().getBoolean("inventory.restore-on-exit", true)) {
                    snap.applyTo(player, true);
                }
                snapshots.remove(pid);
                storageManager.deleteInventorySnapshotSync(pid);
            } else {
                storageManager.saveInventorySnapshotSync(snap);
            }
        }
    }
}
