package net.groundzero.listener.combat;

import net.groundzero.app.Core;
import net.groundzero.listener.BaseListener;
import net.groundzero.service.ProjectileService;
import net.groundzero.service.ProjectileService.Payload;
import net.groundzero.service.model.DamageKind;
import net.groundzero.service.model.DeathCause;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

public final class CombatListener extends BaseListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow)) return;
        if (!ProjectileService.isOurArrow(arrow)) return;
        Core.schedulers.runLater(arrow::remove, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        final Entity victimEnt = e.getEntity();
        if (!(victimEnt instanceof LivingEntity victim)) return;
        if (victim instanceof LivingEntity le && Core.damageService.isProcessingDamage(le)) return;

        // 1) Projectile path
        if (e.getDamager() instanceof Arrow arrow) {
            if (ProjectileService.isOurArrow(arrow)) {
                final Payload payload = ProjectileService.readArrowPayload(arrow);
                if (payload == null) {
                    Core.schedulers.runLater(arrow::remove, 1L);
                    return;
                }

                e.setCancelled(true);

                final UUID attackerId = payload.owner();
                DeathCause cause = mapWeaponIdToCause(payload.weaponId());

                if (victim instanceof Player) {
                    Core.damageService.recordHit(
                            victim.getUniqueId(),
                            attackerId,
                            DamageKind.PROJECTILE,
                            cause,
                            payload.weaponId(),
                            payload.baseDamage()
                    );
                }

                Core.damageService.applyProjectileDamage(attackerId, victim, payload);
                Core.schedulers.runLater(arrow::remove, 1L);
                return;
            } else {
                // VANILLA arrow
                ProjectileSource src = arrow.getShooter();
                if (src instanceof Player attackerPlayer && victim instanceof Player) {
                    DeathCause cause = Core.damageService.mapVanillaCause(e.getCause(), true);
                    Core.damageService.recordHit(
                            victim.getUniqueId(),
                            attackerPlayer.getUniqueId(),
                            DamageKind.VANILLA,
                            cause,
                            null,
                            e.getFinalDamage()
                    );
                }
                return;
            }
        }

        // 2) Melee P2P
        if (e.getDamager() instanceof Player attackerP && victim instanceof Player) {
            DeathCause cause = Core.damageService.mapVanillaCause(e.getCause(), true);
            Core.damageService.recordHit(
                    victim.getUniqueId(),
                    attackerP.getUniqueId(),
                    DamageKind.VANILLA,
                    cause,
                    null,
                    e.getFinalDamage()
            );
        } else if (victim instanceof Player && !(e.getDamager() instanceof Player)) {
            // Mob → Player (기록은 하되 attacker는 null, MOB cause로)
            DeathCause cause = DeathCause.MOB;
            Core.damageService.recordHit(
                    victim.getUniqueId(),
                    null,  // attacker 없음
                    DamageKind.VANILLA,
                    cause,
                    null,
                    e.getFinalDamage()
            );
        }
    }

    /**
     * Handle environment damage (fall, lava, fire, etc.)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        if (Core.damageService.isProcessingDamage(victim)) return;
        if (!Core.session.state().isIngame()) return;

        DeathCause cause = Core.damageService.mapVanillaCause(e.getCause(), false);
        Core.damageService.recordHit(
                victim.getUniqueId(),
                null,
                DamageKind.VANILLA,
                cause,
                null,
                e.getFinalDamage()
        );
    }

    private DeathCause mapWeaponIdToCause(String weaponId) {
        if (weaponId == null || weaponId.isEmpty()) return DeathCause.UNKNOWN;
        String lower = weaponId.toLowerCase();

        if (lower.contains("assault")) return DeathCause.ASSAULT;
        if (lower.contains("auto")) return DeathCause.AUTO;
        if (lower.contains("sniper")) return DeathCause.SNIPER;
        if (lower.contains("concussive")) return DeathCause.CONCUSSIVE;
        if (lower.contains("rpg")) return DeathCause.RPG;
        if (lower.contains("smoke")) return DeathCause.SMOKE; // just placeholder
        if (lower.contains("aerial_simple")) return DeathCause.AERIAL_SIMPLE;
        if (lower.contains("aerial_arrow")) return DeathCause.AERIAL_ARROW;
        if (lower.contains("aerial_cluster")) return DeathCause.AERIAL_CLUSTER;
        if (lower.contains("aerial_random")) return DeathCause.AERIAL_RANDOM;
        if (lower.contains("aerial_carpet")) return DeathCause.AERIAL_CARPET;
        if (lower.contains("aerial_hack")) return DeathCause.AERIAL_HACK;
        if (lower.contains("missile_simple")) return DeathCause.MISSILE_SIMPLE;
        if (lower.contains("missile_poison")) return DeathCause.MISSILE_POISON;
        if (lower.contains("missile_bunker")) return DeathCause.MISSILE_BUNKER_BUSTER;
        if (lower.contains("missile_he") || lower.contains("high_explosive")) return DeathCause.MISSILE_HIGH_EXPLOSIVE;
        if (lower.contains("missile_nuclear") || lower.contains("nuke")) return DeathCause.MISSILE_NUCLEAR;
        if (lower.contains("missile_abm") || lower.contains("abm")) return DeathCause.MISSILE_ABM;

        return DeathCause.UNKNOWN;
    }
}