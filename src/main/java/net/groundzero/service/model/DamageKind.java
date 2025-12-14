package net.groundzero.service.model;

/** Classifies how the damage originated. Extend as needed. */
public enum DamageKind {
    VANILLA,        // melee
    PROJECTILE,    // our arrows, bullets, etc.
    TNT,     // our TNT/missile payloads
    POISON,        // our DoT engine (poison/withering)
    MISSILE,   // lava, fall, fire, void, etc.
    OTHER          // fallback
}
