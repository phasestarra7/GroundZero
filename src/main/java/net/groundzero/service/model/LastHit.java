package net.groundzero.service.model;

import java.util.UUID;

/**
 * Minimal "who hurt whom" snapshot used for kill-credit and combat tags.
 */
public final class LastHit {
    public final UUID victim;       // damaged player
    public final UUID attacker;     // nullable for environment
    public final DamageKind kind;   // classification
    public final String weaponId;   // nullable; e.g., custom weapon key
    public final double amount;     // raw damage amount at record time (hp)
    public final long timestamp;    // System.currentTimeMillis()

    public LastHit(UUID victim, UUID attacker, DamageKind kind,
                   String weaponId, double amount, long timestamp) {
        this.victim = victim;
        this.attacker = attacker;
        this.kind = kind;
        this.weaponId = weaponId;
        this.amount = amount;
        this.timestamp = timestamp;
    }
}
