package com.casualsmp.tournament;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

public class InventorySnapshot {

    private final UUID playerId;
    private final ItemStack[] storageContents;
    private final ItemStack[] armorContents;
    private final ItemStack offhandItem;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final float exp;
    private final int level;
    private final List<PotionEffect> potionEffects;
    private final GameMode gameMode;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public InventorySnapshot(Player player) {
        this.playerId = player.getUniqueId();
        this.storageContents = cloneItemArray(player.getInventory().getStorageContents());
        this.armorContents = cloneItemArray(player.getInventory().getArmorContents());
        this.offhandItem = player.getInventory().getItemInOffHand() != null
                ? player.getInventory().getItemInOffHand().clone()
                : null;
        this.health = player.getHealth();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.exp = player.getExp();
        this.level = player.getLevel();
        this.potionEffects = new ArrayList<>(player.getActivePotionEffects());
        this.gameMode = player.getGameMode();
        Location loc = player.getLocation();
        this.worldName = loc.getWorld() != null ? loc.getWorld().getName() : null;
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
        this.yaw = loc.getYaw();
        this.pitch = loc.getPitch();
    }

    private InventorySnapshot(
            UUID playerId,
            ItemStack[] storageContents,
            ItemStack[] armorContents,
            ItemStack offhandItem,
            double health,
            int foodLevel,
            float saturation,
            float exp,
            int level,
            List<PotionEffect> potionEffects,
            GameMode gameMode,
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        this.playerId = playerId;
        this.storageContents = storageContents;
        this.armorContents = armorContents;
        this.offhandItem = offhandItem;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.exp = exp;
        this.level = level;
        this.potionEffects = potionEffects;
        this.gameMode = gameMode;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    private static ItemStack[] cloneItemArray(ItemStack[] source) {
        if (source == null) return new ItemStack[0];
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] != null ? source[i].clone() : null;
        }
        return copy;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Location getSavedLocation() {
        if (worldName == null) return null;
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    public void applyTo(Player player, boolean restoreLocation) {
        if (player == null || !player.isOnline()) return;

        player.getInventory().clear();
        player.getInventory().setStorageContents(cloneItemArray(storageContents));
        player.getInventory().setArmorContents(cloneItemArray(armorContents));
        player.getInventory().setItemInOffHand(offhandItem != null ? offhandItem.clone() : null);

        double maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                ? player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                : 20.0;
        double safeHealth = Math.max(1.0, Math.min(health, maxHp));
        player.setHealth(safeHealth);
        player.setFoodLevel(Math.max(0, Math.min(20, foodLevel)));
        player.setSaturation(Math.max(0.0f, Math.min(20.0f, saturation)));
        player.setExp(Math.max(0.0f, Math.min(0.999f, exp)));
        player.setLevel(Math.max(0, level));

        for (PotionEffect active : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(active.getType());
        }
        for (PotionEffect effect : potionEffects) {
            player.addPotionEffect(effect);
        }

        if (gameMode != null) {
            player.setGameMode(gameMode);
        }

        if (restoreLocation) {
            Location savedLoc = getSavedLocation();
            if (savedLoc != null) {
                player.teleport(savedLoc);
            }
        }
        player.updateInventory();
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(playerId.toString());
            oos.writeInt(storageContents.length);
            for (ItemStack item : storageContents) {
                oos.writeObject(item);
            }
            oos.writeInt(armorContents.length);
            for (ItemStack item : armorContents) {
                oos.writeObject(item);
            }
            oos.writeObject(offhandItem);
            oos.writeDouble(health);
            oos.writeInt(foodLevel);
            oos.writeFloat(saturation);
            oos.writeFloat(exp);
            oos.writeInt(level);
            oos.writeInt(potionEffects.size());
            for (PotionEffect effect : potionEffects) {
                oos.writeObject(effect);
            }
            oos.writeObject(gameMode != null ? gameMode.name() : GameMode.SURVIVAL.name());
            oos.writeObject(worldName);
            oos.writeDouble(x);
            oos.writeDouble(y);
            oos.writeDouble(z);
            oos.writeFloat(yaw);
            oos.writeFloat(pitch);
            oos.flush();
        }
        return baos.toByteArray();
    }

    public static InventorySnapshot deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            UUID pid = UUID.fromString((String) ois.readObject());
            int sLen = ois.readInt();
            ItemStack[] storage = new ItemStack[sLen];
            for (int i = 0; i < sLen; i++) {
                storage[i] = (ItemStack) ois.readObject();
            }
            int aLen = ois.readInt();
            ItemStack[] armor = new ItemStack[aLen];
            for (int i = 0; i < aLen; i++) {
                armor[i] = (ItemStack) ois.readObject();
            }
            ItemStack offhand = (ItemStack) ois.readObject();
            double hp = ois.readDouble();
            int food = ois.readInt();
            float sat = ois.readFloat();
            float xp = ois.readFloat();
            int lvl = ois.readInt();
            int pCount = ois.readInt();
            List<PotionEffect> effects = new ArrayList<>(pCount);
            for (int i = 0; i < pCount; i++) {
                effects.add((PotionEffect) ois.readObject());
            }
            String gmName = (String) ois.readObject();
            GameMode gm = GameMode.valueOf(gmName);
            String wName = (String) ois.readObject();
            double px = ois.readDouble();
            double py = ois.readDouble();
            double pz = ois.readDouble();
            float pyaw = ois.readFloat();
            float ppitch = ois.readFloat();

            return new InventorySnapshot(pid, storage, armor, offhand, hp, food, sat, xp, lvl, effects, gm, wName, px, py, pz, pyaw, ppitch);
        }
    }
}
