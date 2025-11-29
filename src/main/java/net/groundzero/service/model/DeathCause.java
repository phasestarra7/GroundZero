package net.groundzero.service.model;

/**
 * Detailed death cause for message generation.
 * Maps to vanilla DamageCause but also includes our custom weapon types.
 */
public enum DeathCause {

    // ========== Player Combat (Custom Weapons) ==========
    ASSAULT,            // Assault rifle arrow
    AUTO,               // Auto rifle arrow
    SNIPER,             // Sniper rifle arrow
    CONCUSSIVE,         // Concussive shell (stun arrow)
    RPG,                // RPG explosion
    SMOKE,              // Smoke grenade (no damage, but placeholder)

    // ========== Aerial Support ==========
    AERIAL_SIMPLE,
    AERIAL_ARROW,
    AERIAL_CLUSTER,
    AERIAL_RANDOM,
    AERIAL_CARPET,
    AERIAL_HACK,

    // ========== Missiles ==========
    MISSILE_SIMPLE,
    MISSILE_POISON,
    MISSILE_BUNKER_BUSTER,
    MISSILE_HIGH_EXPLOSIVE,
    MISSILE_NUCLEAR,
    MISSILE_ABM,

    // ========== Custom DoT ==========
    POISON_TICK,        // Our custom poison (not vanilla)

    // ========== Vanilla Combat ==========
    MELEE,              // ENTITY_ATTACK, ENTITY_SWEEP_ATTACK
    VANILLA_PROJECTILE, // Vanilla arrow/trident (not our custom)

    // ========== Vanilla Environment ==========
    FALL,
    VOID,
    LAVA,
    FIRE,
    FIRE_TICK,
    HOT_FLOOR,          // Magma block damage
    CAMPFIRE,           // Campfire / soul campfire
    DROWNING,
    SUFFOCATION,
    CRAMMING,
    EXPLOSION,          // Generic explosion (not player-caused)
    CACTUS,
    SWEET_BERRY,
    LIGHTNING,
    STARVATION,
    VANILLA_POISON,     // Vanilla poison effect
    WITHER,
    MAGIC,
    DRAGON_BREATH,
    THORNS,
    FALLING_BLOCK,
    FLY_INTO_WALL,      // Elytra crash
    FREEZE,
    SONIC_BOOM,         // Warden
    WORLD_BORDER,       // World border damage
    KILL,               // /kill, admin removal

    // ========== Mobs ==========
    MOB,                // Generic mob kill

    // ========== Fallback ==========
    UNKNOWN
}
