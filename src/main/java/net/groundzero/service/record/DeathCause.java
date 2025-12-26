package net.groundzero.service.record;

/**
 * Unified death cause for:
 * - Kill credit determination
 * - Death message generation
 * - Idle timer reset decisions
 *
 * AttackerType determines how LastHit should be handled:
 * - PLAYER: Overwrite entire LastHit, reset idle timer
 * - MOB/ENVIRONMENT: Keep existing attacker if within combat window, update cause only
 */
public enum DeathCause {

    // ========== Player Combat (Custom Weapons) ==========
    ASSAULT(AttackerType.PLAYER),
    AUTO(AttackerType.PLAYER),
    SNIPER(AttackerType.PLAYER),
    CONCUSSIVE(AttackerType.PLAYER),
    RPG(AttackerType.PLAYER),
    SMOKE(AttackerType.PLAYER),

    // ========== Custom TNT (RPG, etc.) ==========
    CUSTOM_TNT(AttackerType.PLAYER),

    // ========== Custom DoT ==========
    POISON_TICK(AttackerType.PLAYER),

    // ========== Aerial Support ==========
    AERIAL_SIMPLE(AttackerType.PLAYER),
    AERIAL_ARROW(AttackerType.PLAYER),
    AERIAL_CLUSTER(AttackerType.PLAYER),
    AERIAL_RANDOM(AttackerType.PLAYER),
    AERIAL_CARPET(AttackerType.PLAYER),
    AERIAL_HACK(AttackerType.PLAYER),

    // ========== Missiles ==========
    MISSILE_SIMPLE(AttackerType.PLAYER),
    MISSILE_POISON(AttackerType.PLAYER),
    MISSILE_BUNKER_BUSTER(AttackerType.PLAYER),
    MISSILE_HIGH_EXPLOSIVE(AttackerType.PLAYER),
    MISSILE_NUCLEAR(AttackerType.PLAYER),
    MISSILE_ABM(AttackerType.PLAYER),

    // ========== Vanilla Player Combat ==========
    MELEE(AttackerType.PLAYER),
    VANILLA_PROJECTILE(AttackerType.PLAYER),

    // ========== Mob ==========
    MOB(AttackerType.MOB),

    // ========== Environment ==========
    FALL(AttackerType.ENVIRONMENT),
    VOID(AttackerType.ENVIRONMENT),
    LAVA(AttackerType.ENVIRONMENT),
    FIRE(AttackerType.ENVIRONMENT),
    FIRE_TICK(AttackerType.ENVIRONMENT),
    HOT_FLOOR(AttackerType.ENVIRONMENT),
    CAMPFIRE(AttackerType.ENVIRONMENT),
    DROWNING(AttackerType.ENVIRONMENT),
    SUFFOCATION(AttackerType.ENVIRONMENT),
    CRAMMING(AttackerType.ENVIRONMENT),
    EXPLOSION(AttackerType.ENVIRONMENT),
    CACTUS(AttackerType.ENVIRONMENT),
    SWEET_BERRY(AttackerType.ENVIRONMENT),
    LIGHTNING(AttackerType.ENVIRONMENT),
    STARVATION(AttackerType.ENVIRONMENT),
    VANILLA_POISON(AttackerType.ENVIRONMENT),
    WITHER(AttackerType.ENVIRONMENT),
    MAGIC(AttackerType.ENVIRONMENT),
    DRAGON_BREATH(AttackerType.ENVIRONMENT),
    THORNS(AttackerType.ENVIRONMENT),
    FALLING_BLOCK(AttackerType.ENVIRONMENT),
    FLY_INTO_WALL(AttackerType.ENVIRONMENT),
    FREEZE(AttackerType.ENVIRONMENT),
    SONIC_BOOM(AttackerType.ENVIRONMENT),
    WORLD_BORDER(AttackerType.ENVIRONMENT),
    KILL(AttackerType.ENVIRONMENT),

    // ========== Fallback ==========
    UNKNOWN(AttackerType.ENVIRONMENT);

    public enum AttackerType {
        PLAYER,      // Player-caused damage (has attacker, resets idle)
        MOB,         // Mob damage (no attacker, no kill credit)
        ENVIRONMENT  // Environment damage (no attacker, keeps existing)
    }

    public final AttackerType attackerType;

    DeathCause(AttackerType type) {
        this.attackerType = type;
    }

    /**
     * Returns true if this is a player-caused damage.
     * Player damage: overwrite entire LastHit, reset idle timer.
     */
    public boolean isPlayerCaused() {
        return attackerType == AttackerType.PLAYER;
    }

    /**
     * Returns true if this is environment/mob damage.
     * Environment damage: keep existing attacker within combat window, update cause only.
     */
    public boolean isEnvironment() {
        return attackerType == AttackerType.ENVIRONMENT
                || attackerType == AttackerType.MOB;
    }
}