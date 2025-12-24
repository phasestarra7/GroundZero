package net.groundzero.service.combat;

import net.groundzero.app.Core;
import net.groundzero.service.tick.TickBus;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns and manages GroundZero TNT projectiles.
 *
 * Flow:
 * 1. spawnTntProjectile creates TNTPrimed entity with velocity
 * 2. TickBus checks every tick for collision (ground or player)
 * 3. On collision: setFuseTicks(0) → immediate explosion
 * 4. EntityExplodeEvent routes to DamageService for custom damage
 */
public final class TntService implements TickBus.Tickable {

    // PDC keys for custom TNT entities
    public static final NamespacedKey KEY_TNT_IS_GZ  = new NamespacedKey(Core.plugin, "gz_tnt_is_gz");
    public static final NamespacedKey KEY_TNT_WEAPON = new NamespacedKey(Core.plugin, "gz_tnt_weapon");
    public static final NamespacedKey KEY_TNT_OWNER  = new NamespacedKey(Core.plugin, "gz_tnt_owner");
    public static final NamespacedKey KEY_TNT_DAMAGE = new NamespacedKey(Core.plugin, "gz_tnt_damage");
    public static final NamespacedKey KEY_TNT_RADIUS = new NamespacedKey(Core.plugin, "gz_tnt_radius");

    // Active TNT tracking for collision detection
    private final Set<TNTPrimed> activeTnts = ConcurrentHashMap.newKeySet();
    private boolean running = false;

    // Collision detection threshold
    private static final double BLOCK_EPS = 0.02;
    private static final double ENTITY_EPS = 0.05;

    /** Options for spawning TNT projectiles */
    public static final class TntOptions {
        // Kinematics
        public double speed = 1;
        public boolean gravity = true;

        // Fuse
        public int fuseTicks = 1200;  // 60 seconds default (1200 ticks)

        // Identity & custom damage
        public String weaponId;           // REQUIRED (e.g., "gz_rpg")
        public double baseDamage = 20.0;  // base explosion damage
        public double blastRadius = 4.0;  // explosion radius

        // Cosmetics
        public boolean glowing = false;
        public String debugName = null;
    }

    /* ===================== Lifecycle ===================== */

    public void start() {
        if (running) return;
        running = true;
        Core.tickBus.register(this);
    }

    public void stop() {
        if (!running) return;
        running = false;
        Core.tickBus.unregister(this);

        // Clean up active TNTs
        for (TNTPrimed tnt : activeTnts) {
            try {
                if (tnt != null && tnt.isValid()) {
                    tnt.remove();
                }
            } catch (Throwable ignored) {}
        }
        activeTnts.clear();
    }

    public void reset() {
        stop();
    }

    /* ===================== TNT Spawning ===================== */

    /**
     * Spawn TNT projectile by UUID.
     * Returns the TNTPrimed entity or null on failure.
     */
    public TNTPrimed spawnTntProjectile(UUID shooterId, Location origin, Vector direction, TntOptions opt) {
        if (shooterId == null || origin == null || direction == null || opt == null) return null;
        if (opt.weaponId == null || opt.weaponId.isEmpty()) return null;

        World w = origin.getWorld();
        if (w == null) return null;

        Vector dir = direction.clone().normalize().multiply(opt.speed);

        TNTPrimed tnt = w.spawn(origin, TNTPrimed.class, t -> {
            // Infinite fuse (we handle detonation manually)
            t.setFuseTicks(opt.fuseTicks);

            // Apply velocity
            t.setVelocity(dir);
            t.setGravity(opt.gravity);
            t.setGlowing(opt.glowing);

            if (opt.debugName != null) {
                t.customName(net.kyori.adventure.text.Component.text(opt.debugName));
                t.setCustomNameVisible(true);
            }

            // Tag as our TNT projectile
            PersistentDataContainer pdc = t.getPersistentDataContainer();
            pdc.set(KEY_TNT_IS_GZ,  PersistentDataType.BYTE,   (byte)1);
            pdc.set(KEY_TNT_OWNER,  PersistentDataType.STRING, shooterId.toString());
            pdc.set(KEY_TNT_WEAPON, PersistentDataType.STRING, opt.weaponId);
            pdc.set(KEY_TNT_DAMAGE, PersistentDataType.DOUBLE, opt.baseDamage);
            pdc.set(KEY_TNT_RADIUS, PersistentDataType.DOUBLE, opt.blastRadius);
        });

        // Track for collision detection
        activeTnts.add(tnt);

        return tnt;
    }

    /**
     * Spawn TNT at location and detonate immediately.
     * Used for RPG arrow collision, aerial strikes, etc.
     */
    public TNTPrimed detonateTntAtLocation(UUID shooterId, Location location, TntOptions opt) {
        if (shooterId == null || location == null || opt == null) return null;
        if (opt.weaponId == null || opt.weaponId.isEmpty()) return null;

        World w = location.getWorld();
        if (w == null) return null;

        TNTPrimed tnt = w.spawn(location, TNTPrimed.class, t -> {
            // Detonate immediately
            t.setFuseTicks(0);

            // No velocity (spawned in place)
            t.setVelocity(new Vector(0, 0, 0));
            t.setGravity(opt.gravity);
            t.setGlowing(opt.glowing);

            if (opt.debugName != null) {
                t.customName(net.kyori.adventure.text.Component.text(opt.debugName));
                t.setCustomNameVisible(true);
            }

            // Tag as our TNT
            PersistentDataContainer pdc = t.getPersistentDataContainer();
            pdc.set(KEY_TNT_IS_GZ,  PersistentDataType.BYTE,   (byte)1);
            pdc.set(KEY_TNT_WEAPON, PersistentDataType.STRING, opt.weaponId);
            pdc.set(KEY_TNT_OWNER,  PersistentDataType.STRING, shooterId.toString());
            pdc.set(KEY_TNT_DAMAGE, PersistentDataType.DOUBLE, opt.baseDamage);
            pdc.set(KEY_TNT_RADIUS, PersistentDataType.DOUBLE, opt.blastRadius);
        });

        // Don't track (already exploding)
        return tnt;
    }

    /* ===================== Tick-based Collision Detection ===================== */

    @Override
    public void onTick(int currentTick) {
        if (!running) return;
        if (activeTnts.isEmpty()) return;

        Iterator<TNTPrimed> it = activeTnts.iterator();
        while (it.hasNext()) {
            TNTPrimed tnt = it.next();

            // Remove if invalid
            if (tnt == null || !tnt.isValid() || tnt.isDead()) {
                it.remove();
                continue;
            }

            Location loc = tnt.getLocation();

            // Check if should explode
            if (hasEntityCollision(tnt) || hasBlockCollision(tnt)) {
                tnt.setFuseTicks(0);
                it.remove();
                continue;
            }
        }
    }

    /**
     * Returns true if TNT's bounding box overlaps any "blocking" block.
     * - Includes water/cobweb/carpet etc. if you want it to explode on those too.
     * - Uses block bounding boxes, so slabs/stairs/edges are handled correctly.
     */
    private boolean hasBlockCollision(TNTPrimed tnt) {
        BoundingBox box = tnt.getBoundingBox().expand(BLOCK_EPS);

        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(box.getMaxX());
        int minY = (int) Math.floor(box.getMinY());
        int maxY = (int) Math.floor(box.getMaxY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(box.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = tnt.getWorld().getBlockAt(x, y, z);
                    Material type = b.getType();

                    // Explode on *any* non-air contact (covers water, cobweb, carpet, etc.)
                    // If you want to ignore some, filter here.
                    if (type.isAir()) continue;

                    BoundingBox bb = b.getBoundingBox();
                    if (bb != null && bb.overlaps(box)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Returns true if TNT overlaps any LivingEntity bounding box.
     */
    private boolean hasEntityCollision(TNTPrimed tnt) {
        BoundingBox box = tnt.getBoundingBox().expand(ENTITY_EPS);

        // Broad-phase: small nearby query
        for (Entity e : tnt.getNearbyEntities(0.9, 0.9, 0.9)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le.isDead()) continue;

            if (le.getBoundingBox().overlaps(box)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detonate TNT immediately
     */
    private void detonate(TNTPrimed tnt) {
        if (tnt == null || !tnt.isValid()) return;
        tnt.setFuseTicks(0);
    }

    /* ===================== Payload Reading ===================== */

    /** Check if this TNT entity is ours */
    public static boolean isOurTnt(TNTPrimed tnt) {
        if (tnt == null) return false;
        return tnt.getPersistentDataContainer().has(KEY_TNT_IS_GZ, PersistentDataType.BYTE);
    }

    /** Extract payload from TNT entity; null if not ours or corrupted */
    public static Payload readTntPayload(TNTPrimed tnt) {
        if (tnt == null) return null;
        PersistentDataContainer pdc = tnt.getPersistentDataContainer();
        if (!pdc.has(KEY_TNT_IS_GZ, PersistentDataType.BYTE)) return null;

        try {
            String ownerStr = pdc.get(KEY_TNT_OWNER, PersistentDataType.STRING);
            String weaponId = pdc.get(KEY_TNT_WEAPON, PersistentDataType.STRING);
            Double damage = pdc.get(KEY_TNT_DAMAGE, PersistentDataType.DOUBLE);
            Double radius = pdc.get(KEY_TNT_RADIUS, PersistentDataType.DOUBLE);

            if (ownerStr == null || weaponId == null || damage == null || radius == null) return null;

            return new Payload(UUID.fromString(ownerStr), weaponId, damage, radius);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Payload data carried by TNT entity */
    public record Payload(UUID owner, String weaponId, double baseDamage, double blastRadius) {}
}