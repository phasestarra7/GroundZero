package net.groundzero.service.record;

import org.bukkit.event.entity.EntityDamageEvent;

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
    RPG(AttackerType.PLAYER),
    STUN(AttackerType.PLAYER),
    SMOKE(AttackerType.PLAYER),

    // ========== Custom DoT ==========
    POISON_TICK(AttackerType.PLAYER),  // TODO : this may need to be removed as it collides with missile_poison

    // ========== Aerial Support ==========
    AERIAL_SIMPLE(AttackerType.PLAYER),
    AERIAL_ARROW(AttackerType.PLAYER),
    AERIAL_CLUSTER(AttackerType.PLAYER),
    AERIAL_SPREADER(AttackerType.PLAYER),
    AERIAL_CARPET(AttackerType.PLAYER),
    AERIAL_HACK(AttackerType.PLAYER),

    // ========== Missiles ==========
    MISSILE_SIMPLE(AttackerType.PLAYER),
    MISSILE_POISON(AttackerType.PLAYER),
    MISSILE_BUNKER(AttackerType.PLAYER),
    MISSILE_HIGHEXP(AttackerType.PLAYER),
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

    /* ===================== Mapping: WeaponId → DeathCause ===================== */

    /**
     * Map custom weapon ID to DeathCause.
     * Used by: CombatListener, DamageService.applyTntDamage, etc.
     *
     * @param weaponId Custom weapon ID (e.g., "gz_rpg", "gz_assault")
     * @return Corresponding DeathCause, or UNKNOWN if not matched
     */
    public static DeathCause fromWeaponId(String weaponId) {
        if (weaponId == null || weaponId.isEmpty()) return UNKNOWN;
        String lower = weaponId.toLowerCase();

        // Personal weapons
        if (lower.contains("assault")) return ASSAULT;
        if (lower.contains("auto")) return AUTO;
        if (lower.contains("sniper")) return SNIPER;
        if (lower.contains("rpg")) return RPG;
        if (lower.contains("stun")) return STUN;
        if (lower.contains("smoke")) return SMOKE;

        // Aerial
        if (lower.contains("aerial_simple")) return AERIAL_SIMPLE;
        if (lower.contains("aerial_arrow")) return AERIAL_ARROW;
        if (lower.contains("aerial_cluster")) return AERIAL_CLUSTER;
        if (lower.contains("aerial_spreader")) return AERIAL_SPREADER;
        if (lower.contains("aerial_carpet")) return AERIAL_CARPET;
        if (lower.contains("aerial_hack")) return AERIAL_HACK;

        // Missiles
        if (lower.contains("missile_simple")) return MISSILE_SIMPLE;
        if (lower.contains("missile_poison")) return MISSILE_POISON;
        if (lower.contains("missile_bunker")) return MISSILE_BUNKER;
        if (lower.contains("missile_highexp")) return MISSILE_HIGHEXP;
        if (lower.contains("missile_nuclear")) return MISSILE_NUCLEAR;
        if (lower.contains("missile_abm")) return MISSILE_ABM;

        // Poison (for PoisonService)
        if (lower.contains("poison")) return POISON_TICK;

        return UNKNOWN;
    }

    /* ===================== Mapping: Vanilla → DeathCause ===================== */

    /**
     * Map vanilla DamageCause to DeathCause.
     * Used by: CombatListener.onEntityDamage (environment damage)
     *
     * @param cause     Bukkit DamageCause
     * @param hasPlayer true if attacker was a player (for ENTITY_ATTACK, PROJECTILE)
     * @return Corresponding DeathCause
     */
    public static DeathCause fromVanillaCause(EntityDamageEvent.DamageCause cause, boolean hasPlayer) {
        if (cause == null) return UNKNOWN;

        return switch (cause) {
            case FALL -> FALL;
            case VOID -> VOID;
            case LAVA -> LAVA;
            case HOT_FLOOR -> HOT_FLOOR;
            case FIRE -> FIRE;
            case CAMPFIRE -> CAMPFIRE;
            case FIRE_TICK -> FIRE_TICK;
            case DROWNING -> DROWNING;
            case SUFFOCATION -> SUFFOCATION;
            case CRAMMING -> CRAMMING;
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> EXPLOSION;
            case CONTACT -> CACTUS;
            case LIGHTNING -> LIGHTNING;
            case STARVATION -> STARVATION;
            case POISON -> VANILLA_POISON;
            case WITHER -> WITHER;
            case MAGIC -> MAGIC;
            case DRAGON_BREATH -> DRAGON_BREATH;
            case THORNS -> THORNS;
            case FALLING_BLOCK -> FALLING_BLOCK;
            case FLY_INTO_WALL -> FLY_INTO_WALL;
            case FREEZE -> FREEZE;
            case SONIC_BOOM -> SONIC_BOOM;
            case WORLD_BORDER -> WORLD_BORDER;
            case KILL, SUICIDE -> KILL;
            case MELTING -> FIRE;
            case DRYOUT -> DROWNING;
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> hasPlayer ? MELEE : MOB;
            case PROJECTILE -> hasPlayer ? VANILLA_PROJECTILE : MOB;
            case CUSTOM -> UNKNOWN;
            default -> UNKNOWN;
        };
    }
}