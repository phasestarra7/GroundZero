package net.groundzero.service.combat;

import net.groundzero.app.Core;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * Projectile flag constants.
 *
 * Flags are stored in arrow PDC and used to determine special behaviors
 * when the projectile hits something.
 *
 * Usage:
 * - Set flag in ArrowOptions.flags before spawning
 * - Check flag in CombatListener/ProjectileHitEvent to trigger behavior
 *
 * Bit flags allow combining multiple behaviors:
 *   opt.flags = FLAG_RPG_EXPLOSIVE | FLAG_SOMETHING_ELSE;
 */
public final class ProjectileFlagService {

    private ProjectileFlagService() {}

    /* ===================== Flag Constants ===================== */

    /**
     * No special behavior - normal projectile damage.
     */
    public static final int FLAG_NONE = 0;

    /**
     * RPG Explosive: Detonates TNT at impact location.
     * Arrow damage is ignored; explosion handles all damage.
     */
    public static final int FLAG_RPG_EXPLOSIVE = 1 << 0;  // 1

    /**
     * Stun Grenade: Disables opponent on hit.
     * (Future implementation)
     */
    public static final int FLAG_STUN = 1 << 1;     // 2

    /**
     * Smoke Grenade: Creates smoke particles on impact.
     * (Future implementation)
     */
    public static final int FLAG_SMOKE = 1 << 2;          // 4

    /**
     * Remote Hack: Redirects aerial strikes.
     * (Future implementation)
     */
    public static final int FLAG_REMOTE_HACK = 1 << 3;    // 16

    /**
     * ABM (Anti-Ballistic Missile): Destroys missiles on proximity.
     * (Future implementation)
     */
    public static final int FLAG_ABM = 1 << 4;            // 8

    /* ===================== Flag Utilities ===================== */

    /**
     * Check if a flag is set.
     *
     * @param flags    The combined flags value
     * @param flag     The specific flag to check
     * @return true if the flag is set
     */
    public static boolean hasFlag(int flags, int flag) {
        return (flags & flag) != 0;
    }

    /**
     * Set a flag.
     *
     * @param flags    The current flags value
     * @param flag     The flag to add
     * @return The new flags value with the flag set
     */
    public static int setFlag(int flags, int flag) {
        return flags | flag;
    }

    /**
     * Clear a flag.
     *
     * @param flags    The current flags value
     * @param flag     The flag to remove
     * @return The new flags value with the flag cleared
     */
    public static int clearFlag(int flags, int flag) {
        return flags & ~flag;
    }

    /* ===================== RPG Explosion Handler ===================== */

    /**
     * Handle RPG explosion on projectile hit.
     * Called from CombatListener when FLAG_RPG_EXPLOSIVE is detected.
     *
     * @param arrow   The RPG arrow
     * @param payload Arrow payload
     * @param e       ProjectileHitEvent
     */
    public static void handleRpgExplosion(Arrow arrow, ProjectileService.Payload payload, ProjectileHitEvent e) {
        Location explosionLoc;

        // Determine explosion location
        if (e.getHitEntity() != null) {
            explosionLoc = e.getHitEntity().getLocation();
        } else if (e.getHitBlock() != null) {
            explosionLoc = arrow.getLocation();
        } else {
            explosionLoc = arrow.getLocation();
        }

        // Build TntOptions for explosion
        TntService.TntOptions tntOpt = new TntService.TntOptions();
        tntOpt.weaponId = payload.weaponId();
        tntOpt.baseDamage = Core.gameConfig.rpgExplosionDamage;
        tntOpt.blastRadius = Core.gameConfig.rpgBlastRadius;
        tntOpt.gravity = false;
        tntOpt.glowing = false;
        tntOpt.debugName = null;

        // Detonate TNT at location (instant explosion)
        Core.tntService.detonateTntAtLocation(payload.owner(), explosionLoc, tntOpt);

        // Arrow removal handled by caller
    }
}