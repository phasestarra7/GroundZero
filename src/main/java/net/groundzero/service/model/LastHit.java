package net.groundzero.service.model;

import java.util.UUID;

/**
 * Minimal "who hurt whom" snapshot used for kill-credit and combat tags.
 */
public final class LastHit {
    public final UUID victim;       // damaged player
    public final UUID attacker;     // nullable for environment
    public final DamageKind kind;   // high-level classification (VANILLA, PROJECTILE, TNT, etc.)
    public final DeathCause cause;  // detailed cause for death message
    public final String weaponId;   // nullable; e.g., custom weapon key like "gz_sniper"
    public final double amount;     // raw damage amount at record time (hp)
    public final int tick;          // remainingTicks snapshot for combat window check

    public LastHit(UUID victim, UUID attacker, DamageKind kind, DeathCause cause,
                   String weaponId, double amount, int tick) {
        this.victim = victim;
        this.attacker = attacker;
        this.kind = kind;
        this.cause = cause;
        this.weaponId = weaponId;
        this.amount = amount;
        this.tick = tick;
    }
}