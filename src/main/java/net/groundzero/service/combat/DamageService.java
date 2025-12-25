package net.groundzero.service.combat;

import net.groundzero.app.Core;
import net.groundzero.service.model.LastHit;
import net.groundzero.service.model.DeathCause;
import net.groundzero.service.combat.ProjectileService.Payload;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom damage pipeline:
 * - Records last-hit snapshot for kill credit
 * - Handles custom damage application (projectile, TNT, poison, missile)
 * - Maps vanilla damage causes to DeathCause
 *
 * LastHit handling:
 * - Player-caused damage (DeathCause.isPlayerCaused): Overwrite entire LastHit
 * - Environment/Mob damage (DeathCause.isEnvironment): Keep attacker if within combat window
 */
public final class DamageService {

    /** Metadata key to mark "this tick is our custom damage". */
    public static final String META_PROCESSING_DAMAGE = "gz_applying_damage_now";

    /** victimId -> last hit snapshot */
    private final Map<UUID, LastHit> lastHitMap = new ConcurrentHashMap<>();

    public void reset() {
        lastHitMap.clear();
    }

    /* ===================== last-hit API (kill credit) ===================== */

    /**
     * Record a hit snapshot for victim.
     *
     * @param victim   damaged player UUID
     * @param attacker attacker UUID (null for environment/mob)
     * @param cause    detailed death cause
     * @param weaponId custom weapon ID (nullable)
     * @param amount   damage amount
     */
    public void recordHit(UUID victim, UUID attacker, DeathCause cause,
                          String weaponId, double amount) {
        if (victim == null || cause == null) return;
        if (!Core.session.state().isIngame()) return;

        int snap = Core.session.remainingTicks();

        // Environment/Mob damage: keep existing attacker within combat window
        if (cause.isEnvironment()) {
            LastHit existing = lastHitMap.get(victim);
            if (existing != null && existing.attacker != null) {
                int dt = existing.tick - snap;
                boolean stillInWindow = (dt >= 0) && (dt < Core.gameConfig.combatWindowTicks);
                if (stillInWindow) {
                    // Keep attacker, weaponId, tick / update cause and amount only
                    lastHitMap.put(victim, new LastHit(
                            victim,
                            existing.attacker,
                            cause,
                            existing.weaponId,
                            amount,
                            existing.tick
                    ));
                    return;
                }
            }
            // Window expired or no existing attacker: record as pure environment
            lastHitMap.put(victim, new LastHit(victim, null, cause, null, amount, snap));
            return;
        }

        // Player-caused damage: overwrite entire LastHit
        lastHitMap.put(victim, new LastHit(victim, attacker, cause, weaponId, amount, snap));

        // Reset idle timer for player-vs-player combat
        if (cause.isPlayerCaused() && attacker != null) {
            Core.combatIdleService.onCombatEvent(attacker, victim);
        }
    }

    /** Read-only peek of last hit (maybe null). */
    public LastHit peekLastHit(UUID victim) {
        if (victim == null) return null;
        return lastHitMap.get(victim);
    }

    /** Clear a victim's last-hit snapshot (e.g., on respawn). */
    public void clearLastHit(UUID victim) {
        if (victim == null) return;
        lastHitMap.remove(victim);
    }

    public void clearAllLastHits() {
        lastHitMap.clear();
    }

    /* ===================== DeathCause mapping from vanilla ===================== */

    /**
     * Map vanilla DamageCause to our DeathCause enum.
     *
     * @param cause     Bukkit DamageCause
     * @param hasPlayer true if attacker was a player
     */
    public DeathCause mapVanillaCause(EntityDamageEvent.DamageCause cause, boolean hasPlayer) {
        if (cause == null) return DeathCause.UNKNOWN;

        return switch (cause) {
            case FALL -> DeathCause.FALL;
            case VOID -> DeathCause.VOID;
            case LAVA -> DeathCause.LAVA;
            case HOT_FLOOR -> DeathCause.HOT_FLOOR;
            case FIRE -> DeathCause.FIRE;
            case CAMPFIRE -> DeathCause.CAMPFIRE;
            case FIRE_TICK -> DeathCause.FIRE_TICK;
            case DROWNING -> DeathCause.DROWNING;
            case SUFFOCATION -> DeathCause.SUFFOCATION;
            case CRAMMING -> DeathCause.CRAMMING;
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> DeathCause.EXPLOSION;
            case CONTACT -> DeathCause.CACTUS;
            case LIGHTNING -> DeathCause.LIGHTNING;
            case STARVATION -> DeathCause.STARVATION;
            case POISON -> DeathCause.VANILLA_POISON;
            case WITHER -> DeathCause.WITHER;
            case MAGIC -> DeathCause.MAGIC;
            case DRAGON_BREATH -> DeathCause.DRAGON_BREATH;
            case THORNS -> DeathCause.THORNS;
            case FALLING_BLOCK -> DeathCause.FALLING_BLOCK;
            case FLY_INTO_WALL -> DeathCause.FLY_INTO_WALL;
            case FREEZE -> DeathCause.FREEZE;
            case SONIC_BOOM -> DeathCause.SONIC_BOOM;
            case WORLD_BORDER -> DeathCause.WORLD_BORDER;
            case KILL, SUICIDE -> DeathCause.KILL;
            case MELTING -> DeathCause.FIRE;
            case DRYOUT -> DeathCause.DROWNING;
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK ->
                    hasPlayer ? DeathCause.MELEE : DeathCause.MOB;
            case PROJECTILE ->
                    hasPlayer ? DeathCause.VANILLA_PROJECTILE : DeathCause.MOB;
            case CUSTOM -> DeathCause.UNKNOWN;
            default -> DeathCause.UNKNOWN;
        };
    }

    /* ===================== custom-damage helpers ===================== */

    /** Check if this entity is currently under our custom damage tick. */
    public boolean isProcessingDamage(LivingEntity le) {
        return le.hasMetadata(META_PROCESSING_DAMAGE);
    }

    /**
     * Mark as our custom damage for 1 tick, then auto-clear.
     */
    public void markProcessingDamage(LivingEntity le) {
        le.setMetadata(META_PROCESSING_DAMAGE, new FixedMetadataValue(Core.plugin, true));
        Core.schedulers.runLater(() -> {
            try {
                le.removeMetadata(META_PROCESSING_DAMAGE, Core.plugin);
            } catch (Throwable ignored) {}
        }, 1L);
    }

    /**
     * Apply custom damage with i-frame removal and no knockback.
     * Used for: projectiles, TNT, poison, missiles, etc.
     */
    public void applyCustomDamage(UUID attackerId, LivingEntity victim, double amount) {
        if (victim == null || amount <= 0) return;
        if (!Core.session.state().isIngame()) return;

        final Player attacker = (attackerId != null) ? Bukkit.getPlayer(attackerId) : null;

        markProcessingDamage(victim);

        final Vector preVel = victim.getVelocity();
        final int oldNoDamageTicks = victim.getNoDamageTicks();
        victim.setNoDamageTicks(0);

        try {
            // CRITICAL: Set killer for mobs BEFORE damage
            // This ensures drops work correctly even though we cancelled original event
            if (victim instanceof Mob mob && attacker != null && attacker.isOnline()) {
                mob.setKiller(attacker);
            }

            if (attacker != null && attacker.isOnline()) {
                victim.damage(amount, attacker);
            } else {
                victim.damage(amount);
            }

            victim.setVelocity(preVel);
            victim.setNoDamageTicks(0);
        } catch (Throwable t) {
            try { victim.setNoDamageTicks(oldNoDamageTicks); } catch (Throwable ignored) {}
            throw t;
        }
    }

    /**
     * Apply projectile damage (Arrow-based weapons).
     * Wrapper for applyCustomDamage with payload extraction.
     */
    public void applyProjectileDamage(UUID attackerId, LivingEntity victim,
                                      net.groundzero.service.combat.ProjectileService.Payload payload) {
        if (payload == null) return;
        applyCustomDamage(attackerId, victim, payload.baseDamage());
    }

    /**
     * Apply TNT explosion damage to all entities in radius.
     * Uses distance-based damage falloff (linear: 100% at center, 0% at radius edge).
     *
     * @param attackerId TNT owner UUID
     * @param center     Explosion center
     * @param radius     Blast radius
     * @param baseDamage Maximum damage at center
     * @param weaponId   Weapon ID for death cause
     */
    public void applyTntDamage(UUID attackerId, Location center, double radius, double baseDamage, String weaponId) {
        if (center == null || center.getWorld() == null) return;
        if (radius <= 0 || baseDamage <= 0) return;
        if (!Core.session.state().isIngame()) return;

        World w = center.getWorld();
        double radiusSq = radius * radius;

        // Find all LivingEntities in blast radius
        for (org.bukkit.entity.Entity ent : w.getNearbyEntities(center, radius, radius, radius)) {
            if (!(ent instanceof LivingEntity victim)) continue;

            // Calculate distance from center
            double distSq = ent.getLocation().distanceSquared(center);
            if (distSq > radiusSq) continue;

            // Calculate damage falloff (linear: 100% at center, 0% at radius edge)
            double dist = Math.sqrt(distSq);
            double falloff = Math.max(0.0, 1.0 - (dist / radius));
            double finalDamage = baseDamage * falloff;

            if (finalDamage <= 0) continue;

            // Record hit for players
            if (victim instanceof Player pVictim) {
                recordHit(
                        pVictim.getUniqueId(),
                        attackerId,
                        DeathCause.CUSTOM_TNT,
                        weaponId,
                        finalDamage
                );
            }

            // Apply damage with i-frame removal and no knockback
            applyCustomDamage(attackerId, victim, finalDamage);
        }
    }
}