package net.groundzero.service.model;

/**
 * 18 projectile/vehicle model types.
 *
 * Categories:
 * - BULLET (4): Assault, Auto, Sniper, RPG - rotation follows velocity
 * - GRENADE (2): Concussive, Smoke - position only, no rotation
 * - AERIAL (6): Planes - rotation set once on spawn
 * - MISSILE (6): 3-phase rotation control
 */
public enum ModelType {

    /* ===== Bullets (rotation follows velocity) ===== */
    ASSAULT_BULLET(Category.BULLET),
    AUTO_BULLET(Category.BULLET),
    SNIPER_BULLET(Category.BULLET),
    RPG_ROCKET(Category.BULLET),

    /* ===== Grenades (position only, no rotation) ===== */
    CONCUSSIVE_SHELL(Category.GRENADE),
    SMOKE_GRENADE(Category.GRENADE),

    /* ===== Aerial (rotation fixed on spawn) ===== */
    AERIAL_SIMPLE_PLANE(Category.AERIAL),
    AERIAL_ARROW_PLANE(Category.AERIAL),
    AERIAL_CLUSTER_PLANE(Category.AERIAL),
    AERIAL_SPREADER_PLANE(Category.AERIAL),
    AERIAL_CARPET_PLANE(Category.AERIAL),
    AERIAL_HACK_DRONE(Category.AERIAL),

    /* ===== Missiles (3-phase rotation) ===== */
    MISSILE_SIMPLE(Category.MISSILE),
    MISSILE_POISON(Category.MISSILE),
    MISSILE_BUNKER(Category.MISSILE),
    MISSILE_HIGHEXP(Category.MISSILE),
    MISSILE_NUCLEAR(Category.MISSILE),
    MISSILE_ABM(Category.MISSILE);

    public final Category category;

    ModelType(Category category) {
        this.category = category;
    }

    public enum Category {
        BULLET,   // Rotation follows arrow velocity every tick
        GRENADE,  // Position only, no rotation update
        AERIAL,   // Rotation set once on spawn, then fixed
        MISSILE   // 3-phase rotation (up → horizontal → down)
    }

    /**
     * Whether this model type needs per-tick rotation updates.
     */
    public boolean needsRotationUpdate() {
        return category == Category.BULLET;
    }
}