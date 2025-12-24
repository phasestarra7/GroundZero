package net.groundzero.listener.combat;

import net.groundzero.app.Core;
import net.groundzero.listener.BaseListener;
import net.groundzero.service.combat.PoisonService;
import net.groundzero.service.combat.ProjectileService;
import net.groundzero.service.combat.ProjectileService.Payload;
import net.groundzero.service.combat.TntService;
import net.groundzero.service.model.DeathCause;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

/**
 * Combat event listener.
 *
 * Damage pipelines:
 * 1. PROJECTILE - Custom arrows (assault, auto, sniper, concussive)
 * 2. TNT - Custom TNT explosions (handled by TntService, placeholder here)
 * 3. POISON - Custom DoT (handled by PoisonService, placeholder here)
 * 4. MISSILE - Missile explosions (handled by MissileService, placeholder here)
 * 5. VANILLA - Melee, vanilla arrows, tridents
 * 6. ENVIRONMENT - Fall, lava, void, etc.
 */
public final class CombatListener extends BaseListener implements Listener {

    /* ==================== PROJECTILE HIT (cleanup) ==================== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow)) return;
        if (!ProjectileService.isOurArrow(arrow)) return;
        Core.schedulers.runLater(arrow::remove, 1L);
    }

    /* ==================== ENTITY DAMAGE BY ENTITY ==================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        final Entity victimEnt = e.getEntity();
        if (!(victimEnt instanceof LivingEntity victim)) return;
        if (Core.damageService.isProcessingDamage(victim)) return;

        /* ===== 1. PROJECTILE: Our custom arrows ===== */
        if (e.getDamager() instanceof Arrow arrow && ProjectileService.isOurArrow(arrow)) {
            final Payload payload = ProjectileService.readArrowPayload(arrow);
            if (payload == null) {
                Core.schedulers.runLater(arrow::remove, 1L);
                return;
            }

            e.setCancelled(true);

            final UUID attackerId = payload.owner();
            DeathCause cause = mapWeaponIdToCause(payload.weaponId());

            if (victim instanceof Player pVictim) {
                Core.damageService.recordHit(
                        pVictim.getUniqueId(),
                        attackerId,
                        cause,
                        payload.weaponId(),
                        payload.baseDamage()
                );
            }

            Core.damageService.applyCustomDamage(attackerId, victim, payload.baseDamage());
            Core.schedulers.runLater(arrow::remove, 1L);
            return;
        }

        /* ===== 2. TNT: Cancel vanilla damage (custom damage applied in EntityExplodeEvent) ===== */
        if (e.getDamager() instanceof TNTPrimed tnt) {
            if (TntService.isOurTnt(tnt)) {
                e.setCancelled(true);  // Block vanilla damage only
            }
            return;
        }

        /* ===== 3. POISON: Handled by PoisonService (tick-based DoT) ===== */
        // PoisonService applies damage via TickBus
        // No event handling needed here

        /* ===== 4. MISSILE: Placeholder - handled by MissileService ===== */
        // MissileService will:
        // - Apply explosion damage to entities in radius
        // - Call recordHit with appropriate MISSILE_* cause

        /* ===== 5. VANILLA: Arrows, projectiles from players ===== */
        if (e.getDamager() instanceof Arrow arrow) {
            ProjectileSource src = arrow.getShooter();
            if (src instanceof Player attackerP && victim instanceof Player pVictim) {
                Core.damageService.recordHit(
                        pVictim.getUniqueId(),
                        attackerP.getUniqueId(),
                        DeathCause.VANILLA_PROJECTILE,
                        null,
                        e.getFinalDamage()
                );
            }
            return;
        }

        /* ===== 6. VANILLA: Melee P2P ===== */
        if (e.getDamager() instanceof Player attackerP && victim instanceof Player pVictim) {
            Core.damageService.recordHit(
                    pVictim.getUniqueId(),
                    attackerP.getUniqueId(),
                    DeathCause.MELEE,
                    null,
                    e.getFinalDamage()
            );
            return;
        }

        /* ===== 7. MOB: Mob -> Player ===== */
        if (victim instanceof Player pVictim && !(e.getDamager() instanceof Player)) {
            Core.damageService.recordHit(
                    pVictim.getUniqueId(),
                    null,
                    DeathCause.MOB,
                    null,
                    e.getFinalDamage()
            );
        }
    }

    /* ==================== TNT EXPLOSION ==================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!(e.getEntity() instanceof TNTPrimed tnt)) return;
        if (!TntService.isOurTnt(tnt)) return;

        TntService.Payload payload = TntService.readTntPayload(tnt);
        if (payload == null) return;

        // Don't cancel event - let blocks explode normally
        // Vanilla damage already cancelled in EntityDamageByEntityEvent

        // Apply custom damage to entities in blast radius
        Location center = tnt.getLocation();
        Core.damageService.applyTntDamage(
                payload.owner(),
                center,
                payload.blastRadius(),
                payload.baseDamage(),
                payload.weaponId()
        );
    }

    /* ==================== ENVIRONMENT DAMAGE ==================== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent e) {
        // Skip if already handled by EntityDamageByEntityEvent
        if (e instanceof EntityDamageByEntityEvent) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        if (Core.damageService.isProcessingDamage(victim)) return;
        if (!Core.session.state().isIngame()) return;

        // Block vanilla WITHER damage if player has custom poison
        if (e.getCause() == EntityDamageEvent.DamageCause.WITHER) {
            if (PoisonService.isCustomPoisonEffect(victim)) {
                e.setCancelled(true);
                return;
            }
        }

        DeathCause cause = Core.damageService.mapVanillaCause(e.getCause(), false);

        Core.damageService.recordHit(
                victim.getUniqueId(),
                null,
                cause,
                null,
                e.getFinalDamage()
        );
    }

    /* ==================== WEAPON ID -> DEATH CAUSE ==================== */

    private DeathCause mapWeaponIdToCause(String weaponId) {
        if (weaponId == null || weaponId.isEmpty()) return DeathCause.UNKNOWN;
        String lower = weaponId.toLowerCase();

        // Personal weapons
        if (lower.contains("assault")) return DeathCause.ASSAULT;
        if (lower.contains("auto")) return DeathCause.AUTO;
        if (lower.contains("sniper")) return DeathCause.SNIPER;
        if (lower.contains("concussive")) return DeathCause.CONCUSSIVE;
        if (lower.contains("rpg")) return DeathCause.RPG;
        if (lower.contains("smoke")) return DeathCause.SMOKE;

        // Aerial
        if (lower.contains("aerial_simple")) return DeathCause.AERIAL_SIMPLE;
        if (lower.contains("aerial_arrow")) return DeathCause.AERIAL_ARROW;
        if (lower.contains("aerial_cluster")) return DeathCause.AERIAL_CLUSTER;
        if (lower.contains("aerial_random")) return DeathCause.AERIAL_RANDOM;
        if (lower.contains("aerial_carpet")) return DeathCause.AERIAL_CARPET;
        if (lower.contains("aerial_hack")) return DeathCause.AERIAL_HACK;

        // Missiles
        if (lower.contains("missile_simple")) return DeathCause.MISSILE_SIMPLE;
        if (lower.contains("missile_poison")) return DeathCause.MISSILE_POISON;
        if (lower.contains("missile_bunker")) return DeathCause.MISSILE_BUNKER_BUSTER;
        if (lower.contains("missile_highexp")) return DeathCause.MISSILE_HIGH_EXPLOSIVE;
        if (lower.contains("missile_nuclear")) return DeathCause.MISSILE_NUCLEAR;
        if (lower.contains("missile_abm")) return DeathCause.MISSILE_ABM;

        // TNT
        if (lower.contains("tnt")) return DeathCause.CUSTOM_TNT;

        return DeathCause.UNKNOWN;
    }
}