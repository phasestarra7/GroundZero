package net.groundzero.service;

import net.groundzero.app.Core;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.bukkit.Location;

import java.util.Random;
import java.util.UUID;

/**
 * Spawns and tags GroundZero projectiles (Arrow-only for now).
 * We do NOT auto-remove arrows by lifetime anymore; we rely on vanilla unless a hit occurs.
 */
public final class ProjectileService {

    // PDC keys to mark our custom projectiles
    public static final NamespacedKey KEY_IS_GZ      = new NamespacedKey(Core.plugin, "gz_is_projectile");
    public static final NamespacedKey KEY_WEAPON_ID  = new NamespacedKey(Core.plugin, "gz_weapon_id");
    public static final NamespacedKey KEY_OWNER_ID   = new NamespacedKey(Core.plugin, "gz_owner_uuid");
    public static final NamespacedKey KEY_BASE_DMG   = new NamespacedKey(Core.plugin, "gz_base_damage");
    public static final NamespacedKey KEY_SHOT_ID    = new NamespacedKey(Core.plugin, "gz_shot_id");
    public static final NamespacedKey KEY_FLAGS      = new NamespacedKey(Core.plugin, "gz_flags");
    public static final NamespacedKey KEY_SPAWN_TICK = new NamespacedKey(Core.plugin, "gz_spawn_tick");
    public static final NamespacedKey KEY_LIFETIME   = new NamespacedKey(Core.plugin, "gz_lifetime");

    private static final Random RNG = new Random();

    private static Vector randomSpread(double s) {
        if (s <= 0.0) return new Vector(0, 0, 0);
        double rx = (RNG.nextDouble() * 2.0 - 1.0) * s;
        double ry = (RNG.nextDouble() * 2.0 - 1.0) * (s * 0.5); // lower vertical spread
        double rz = (RNG.nextDouble() * 2.0 - 1.0) * s;
        return new Vector(rx, ry, rz);
    }

    /** Options for spawning GroundZero arrows. */
    public static final class ArrowOptions {
        // Kinematics
        public double speed = 3.0;
        public double spread = 0.0;
        public boolean gravity = true;

        // Vanilla-like feel
        public boolean critical = false;
        public int knockbackStrength = 0;
        public int pierceLevel = 0;

        // Identity & custom damage
        public String weaponId;           // REQUIRED
        public double baseDamage = 6.0;   // vanilla fully charged damage

        // Lifecycle / pickup
        public int lifetimeTicks = 0;     // default 0 → let vanilla handle despawn/pickup
        public boolean disallowPickup = true;
        public boolean persistent = false;
        public boolean silent = true;

        // Cosmetics / debug
        public boolean glowing = false;
        public String debugName = null;

        // Bit flags for future behaviors (concussive/smoke/etc.)
        public int flags = 0;
    }

    /** Spawn and tag a GroundZero arrow; returns the Arrow or null on failure. */
    // in ProjectileService

    /** Spawn and tag a GroundZero arrow by UUID (preferred). Returns the Arrow or null on failure. */
    public Arrow spawnArrow(UUID shooterId, Location origin, Vector direction, ArrowOptions opt) {
        if (shooterId == null || origin == null || direction == null || opt == null) return null;
        if (opt.weaponId == null || opt.weaponId.isEmpty()) return null;

        World w = origin.getWorld();
        if (w == null) return null;

        // Try to fetch online Player only to set vanilla shooter (optional). Our pipeline uses UUID in PDC.
        Player shooterOnline = Bukkit.getPlayer(shooterId);

        Vector dir = direction.clone().normalize();
        dir.add(randomSpread(opt.spread)).normalize();

        Arrow arrow = w.spawn(origin, Arrow.class, a -> {
            // If the shooter is online, set as vanilla shooter to preserve knockback/crit attribution.
            // If offline, leave null; our own damage routing relies on PDC anyway.
            if (shooterOnline != null) a.setShooter(shooterOnline);

            // Kinematics / vanilla feel
            a.setVelocity(dir.multiply(opt.speed));           // speed applied to a unit direction vector
            a.setGravity(opt.gravity);
            a.setCritical(opt.critical);
            a.setKnockbackStrength(opt.knockbackStrength);
            a.setPierceLevel(Math.max(0, opt.pierceLevel));
            a.setSilent(opt.silent);
            a.setPersistent(opt.persistent);
            a.setGlowing(opt.glowing);
            if (opt.debugName != null) {
                a.customName(net.kyori.adventure.text.Component.text(opt.debugName));
                a.setCustomNameVisible(true);
            }
            a.setPickupStatus(opt.disallowPickup ? Arrow.PickupStatus.DISALLOWED
                    : Arrow.PickupStatus.ALLOWED);

            // Tag as our projectile (authoritative identity & damage live here)
            PersistentDataContainer pdc = a.getPersistentDataContainer();
            pdc.set(KEY_IS_GZ,      PersistentDataType.BYTE,   (byte)1);
            pdc.set(KEY_WEAPON_ID,  PersistentDataType.STRING,  opt.weaponId);
            pdc.set(KEY_OWNER_ID,   PersistentDataType.STRING,  shooterId.toString());
            pdc.set(KEY_BASE_DMG,   PersistentDataType.DOUBLE,  opt.baseDamage);
            pdc.set(KEY_SHOT_ID,    PersistentDataType.STRING,  UUID.randomUUID().toString());
            pdc.set(KEY_FLAGS,      PersistentDataType.INTEGER, opt.flags);
            pdc.set(KEY_SPAWN_TICK, PersistentDataType.INTEGER, Bukkit.getCurrentTick());
            pdc.set(KEY_LIFETIME,   PersistentDataType.INTEGER, Math.max(0, opt.lifetimeTicks));
        });

        // No auto-remove; listeners will remove on confirmed hit.
        return arrow;
    }

    /** Check if this arrow is ours. */
    public static boolean isOurArrow(Arrow a) {
        if (a == null) return false;
        return a.getPersistentDataContainer().has(KEY_IS_GZ, PersistentDataType.BYTE);
    }

    /** Extract payload; null if not ours or corrupted. */
    public static Payload readArrowPayload(Arrow a) {
        if (a == null) return null;
        PersistentDataContainer pdc = a.getPersistentDataContainer();
        if (!pdc.has(KEY_IS_GZ, PersistentDataType.BYTE)) return null;
        try {
            String weaponId = pdc.get(KEY_WEAPON_ID, PersistentDataType.STRING);
            String ownerStr = pdc.get(KEY_OWNER_ID, PersistentDataType.STRING);
            Double dmg = pdc.get(KEY_BASE_DMG, PersistentDataType.DOUBLE);
            Integer flags = pdc.get(KEY_FLAGS, PersistentDataType.INTEGER);
            if (weaponId == null || ownerStr == null || dmg == null) return null;
            return new Payload(UUID.fromString(ownerStr), weaponId, dmg, flags == null ? 0 : flags);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Minimal payload data carried by our arrow. */
    public record Payload(UUID owner, String weaponId, double baseDamage, int flags) {}
}