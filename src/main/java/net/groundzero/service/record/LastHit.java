package net.groundzero.service.record;

import java.util.UUID;

/**
 * Snapshot of "who hurt whom" for kill credit and death messages.
 *
 * Key fields:
 * - attacker: null for environment/mob, UUID for player-caused
 * - cause: detailed cause for death message generation
 * - tick: remainingTicks snapshot for combat window check
 */
public final class LastHit {
    public final UUID victim;
    public final UUID attacker;     // nullable for environment/mob
    public final DeathCause cause;  // unified cause (replaces DamageKind)
    public final String weaponId;   // nullable; e.g., "gz_sniper"
    public final double amount;     // raw damage amount (hp)
    public final int tick;          // remainingTicks snapshot

    public LastHit(UUID victim, UUID attacker, DeathCause cause,
                   String weaponId, double amount, int tick) {
        this.victim = victim;
        this.attacker = attacker;
        this.cause = cause;
        this.weaponId = weaponId;
        this.amount = amount;
        this.tick = tick;
    }
}