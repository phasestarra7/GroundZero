package net.groundzero.service.combat;

import net.groundzero.app.Core;
import net.groundzero.service.model.DamageKind;
import net.groundzero.service.model.LastHit;
import net.groundzero.service.model.DeathCause;
import net.groundzero.service.combat.ProjectileService.Payload;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom damage pipeline helpers:
 * - Single metadata flag "META_CUSTOM_HIT" to mark our own custom damage tick.
 * - Record last-hit snapshot for kill credit (attacker UUID kept even if offline).
 * - Temporarily remove i-frames (noDamageTicks) so rapid hits are possible.
 *
 * NOTE:
 * - We DO NOT require the attacker to be online to record hit. Scoring uses UUID.
 * - For visual damage animation source: if attacker is online, pass Player as damager;
 *   otherwise call damage(amount) without a damager entity.
 */
public final class DamageService {

    /** Metadata key to mark "this tick is our custom damage" (skip listeners/knockback, etc.). */
    public static final String META_PROCESSING_DAMAGE = "gz_applying_damage_now";

    /** victimId -> last hit snapshot */
    private final Map<UUID, LastHit> lastHitMap = new ConcurrentHashMap<>();

    public void reset() {
        lastHitMap.clear();
    }

    /* ===================== last-hit API (kill credit) ===================== */

    /**
     * Record a hit snapshot for victim. Attacker can be offline; UUID is stored.
     */
    /**
     * Record a hit snapshot for victim with detailed DeathCause.
     * Attacker can be offline; UUID is stored.
     */
    public void recordHit(UUID victim, UUID attacker, DamageKind kind, DeathCause cause,
                          String weaponId, double amount) {
        if (victim == null || kind == null) return;
        if (!Core.session.state().isIngame()) return;

        int snap = Core.session.remainingTicks();

        // if there's no attacker, use lasthitmap's attacker data and only update the cause
        if (attacker == null) {
            LastHit existing = lastHitMap.get(victim);
            if (existing != null && existing.attacker != null) {
                int dt = existing.tick - snap;
                boolean stillInWindow = (dt >= 0) && (dt < Core.gameConfig.combatWindowTicks);
                if (stillInWindow) {
                    // Keep attacker, kind, weaponId, tick / update cause only
                    lastHitMap.put(victim, new LastHit(
                            victim,
                            existing.attacker,
                            existing.kind,      // kind 유지
                            cause,
                            existing.weaponId,
                            amount,
                            existing.tick
                    ));
                    return;
                }
            }
        }

        lastHitMap.put(victim, new LastHit(
                victim, attacker, kind, cause, weaponId, amount, snap
        ));

        // ✅ NEW: Idle Timer reset only for Player-Player interactions
        if (shouldResetIdleTimer(attacker, kind)) {
            Core.combatIdleService.onCombatEvent(attacker, victim);
        }
    }

    /**
     * Check if Idle Timer should be reset for this damage event.
     *
     * Criteria:
     * - Attacker must exist (Player-caused damage)
     * - DamageKind must be combat-related (not environment/mob)
     */
    private boolean shouldResetIdleTimer(UUID attacker, DamageKind kind) {
        if (attacker == null) return false;

        return switch (kind) {
            case VANILLA,      // Player melee, vanilla arrows
                 PROJECTILE,   // Custom weapons (assault, sniper, etc)
                 TNT,          // RPG, custom explosives
                 POISON,       // DoT effects
                 MISSILE       // Missiles (future)
                    -> true;
            case OTHER         // Environment, Mob
                    -> false;
        };
    }

    /** Read-only peek of last hit (maybe null). */
    public LastHit peekLastHit(UUID victim) {
        if (victim == null) return null;
        return lastHitMap.get(victim);
    }

    /** Clear a victim's last-hit snapshot (e.g., on respawn if desired). */
    private void clear(UUID victim) {
        if (victim == null) return;
        lastHitMap.remove(victim);
    }

    public void clearAllLastHits() {
        lastHitMap.clear();
    }

    /* ===================== DeathCause mapping ===================== */

    /**
     * Map vanilla DamageCause to our DeathCause enum.
     * Call this from CombatListener when recording vanilla damage.
     *
     * @param cause     Bukkit DamageCause
     * @param hasPlayer true if attacker was a player (for MELEE vs MOB distinction)
     */
    public DeathCause mapVanillaCause(EntityDamageEvent.DamageCause cause, boolean hasPlayer) {
        if (cause == null) return DeathCause.UNKNOWN;

        return switch (cause) {
            // Falls / void
            case FALL -> DeathCause.FALL;
            case VOID -> DeathCause.VOID;

            // Lava / hot floor / fire / campfire
            case LAVA -> DeathCause.LAVA;
            case HOT_FLOOR -> DeathCause.HOT_FLOOR;
            case FIRE -> DeathCause.FIRE;
            case CAMPFIRE -> DeathCause.CAMPFIRE;
            case FIRE_TICK -> DeathCause.FIRE_TICK;

            // Water / suffocation / cramming
            case DROWNING -> DeathCause.DROWNING;
            case SUFFOCATION -> DeathCause.SUFFOCATION;
            case CRAMMING -> DeathCause.CRAMMING;

            // Explosions
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> DeathCause.EXPLOSION;

            // Contact-type damage (cactus, dripstone, berry bush)
            // Note: SWEET_BERRY is set manually when we detect berry bush specifically.
            case CONTACT -> DeathCause.CACTUS;

            // Lightning
            case LIGHTNING -> DeathCause.LIGHTNING;

            // Hunger / status effects
            case STARVATION -> DeathCause.STARVATION;
            case POISON -> DeathCause.VANILLA_POISON;
            case WITHER -> DeathCause.WITHER;
            case MAGIC -> DeathCause.MAGIC;

            // Misc magic / dragon / thorns
            case DRAGON_BREATH -> DeathCause.DRAGON_BREATH;
            case THORNS -> DeathCause.THORNS;

            // Falling / kinetic / freeze / sonic
            case FALLING_BLOCK -> DeathCause.FALLING_BLOCK;
            case FLY_INTO_WALL -> DeathCause.FLY_INTO_WALL;
            case FREEZE -> DeathCause.FREEZE;
            case SONIC_BOOM -> DeathCause.SONIC_BOOM;

            // Border / kill / suicide
            case WORLD_BORDER -> DeathCause.WORLD_BORDER;
            case KILL -> DeathCause.KILL;
            case SUICIDE -> DeathCause.KILL;     // Treat as generic /kill

            // Snow golem melt / fish dry-out (players should not see these normally)
            case MELTING -> DeathCause.FIRE;      // Best-effort mapping
            case DRYOUT -> DeathCause.DROWNING;   // Best-effort mapping

            // Combat: player vs player / mob
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK ->
                    hasPlayer ? DeathCause.MELEE : DeathCause.MOB;
            case PROJECTILE ->
                    hasPlayer ? DeathCause.VANILLA_PROJECTILE : DeathCause.MOB;

            // Plugin custom sources
            case CUSTOM -> DeathCause.UNKNOWN;

            // Anything else in future versions
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
     * This single flag is used by listeners to ignore recursive handling.
     */
    public void markProcessingDamage(LivingEntity le) {
        le.setMetadata(META_PROCESSING_DAMAGE, new FixedMetadataValue(Core.plugin, true));
        // auto-clear next tick
        Core.schedulers.runLater(() -> {
            try {
                le.removeMetadata(META_PROCESSING_DAMAGE, Core.plugin);
            } catch (Throwable ignored) {}
        }, 1L);
    }

    /**
     * Apply custom damage while temporarily removing i-frames.
     * - If attackerId is online, pass Player as the damager for proper vanilla feedback.
     * - If attackerId is null or offline, call damage(amount) without a source.
     */
    public void applyProjectileDamage(UUID attackerId, LivingEntity victim, Payload payload) {
        if (victim == null || payload == null) return;
        if (!Core.session.state().isIngame()) return;

        final double amount = Math.max(0.0, payload.baseDamage());
        final Player attacker = (attackerId != null) ? Bukkit.getPlayer(attackerId) : null;

        // Mark this tick as our custom application to:
        //  - prevent recursive listener handling
        //  - allow knockback listeners to cancel knockback
        markProcessingDamage(victim);

        // Snapshot velocity to suppress vanilla knockback after damage
        final Vector preVel = victim.getVelocity();

        // Remove i-frames before damage (vanilla sets them when taking damage)
        final int oldNoDamageTicks = victim.getNoDamageTicks();
        victim.setNoDamageTicks(0);

        try {
            // Apply damage immediately (same tick)
            if (attacker != null && attacker.isOnline()) {
                // Use attacker entity for proper vanilla feedback when available
                victim.damage(amount, attacker);
            } else {
                // Offline/unknown attacker: still apply damage; scoring uses UUID elsewhere
                victim.damage(amount);
            }

            // Suppress knockback by restoring velocity right after damage
            victim.setVelocity(preVel);

            // Keep i-frames at 0 to allow rapid successive hits
            victim.setNoDamageTicks(0);

        } catch (Throwable t) {
            // If anything goes wrong, try to restore previous state minimally
            try { victim.setNoDamageTicks(oldNoDamageTicks); } catch (Throwable ignored) {}
            throw t;
        }
    }
}
