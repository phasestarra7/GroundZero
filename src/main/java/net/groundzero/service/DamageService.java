package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.service.model.DamageKind;
import net.groundzero.service.model.LastHit;
import net.groundzero.service.ProjectileService.Payload;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
    public static final String META_CUSTOM_HIT = "gz_custom_hit";

    /** victimId -> last hit snapshot */
    private final Map<UUID, LastHit> lastHitMap = new ConcurrentHashMap<>();

    /* ===================== last-hit API (kill credit) ===================== */

    /**
     * Record a hit snapshot for victim. Attacker can be offline; UUID is stored.
     */
    public void recordHit(UUID victim, UUID attacker, DamageKind kind,
                          String weaponId, double amount) {
        if (victim == null || kind == null) return;
        if (!Core.session.state().isIngame()) return;

        int snap = Core.session.remainingTicks();
        lastHitMap.put(victim, new LastHit(
                victim, attacker, kind, weaponId, amount, snap
        ));

        // Reset camping idle timer on combat event
        Core.combatIdleService.onCombatEvent(attacker, victim);
    }

    /** Read-only peek of last hit (maybe null). */
    public LastHit peekLastHit(UUID victim) {
        if (victim == null) return null;
        return lastHitMap.get(victim);
    }

    /** Clear a victim's last-hit snapshot (e.g., on respawn if desired). */
    public void clear(UUID victim) {
        if (victim == null) return;
        lastHitMap.remove(victim);
    }

    /* ===================== custom-damage helpers ===================== */

    /** Check if this entity is currently under our custom damage tick. */
    public boolean isCustomHit(LivingEntity le) {
        return le.hasMetadata(META_CUSTOM_HIT);
    }

    /**
     * Mark as our custom damage for 1 tick, then auto-clear.
     * This single flag is used by listeners to ignore recursive handling.
     */
    public void markCustomHit(LivingEntity le) {
        le.setMetadata(META_CUSTOM_HIT, new FixedMetadataValue(Core.plugin, true));
        // auto-clear next tick
        Bukkit.getScheduler().runTaskLater(Core.plugin, () -> {
            try {
                le.removeMetadata(META_CUSTOM_HIT, Core.plugin);
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
        markCustomHit(victim);

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
