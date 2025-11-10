package net.groundzero.listener.combat;

import net.groundzero.app.Core;
import net.groundzero.listener.BaseListener;
import net.groundzero.service.ProjectileService;
import net.groundzero.service.ProjectileService.Payload;
import net.groundzero.service.model.DamageKind;
import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

/**
 * CombatListener:
 * - Distinguish vanilla vs our projectiles.
 * - Our projectiles: cancel vanilla damage and route to DamageService.
 * - Vanilla damage: let it flow, but record LastHit for kill credit pipeline.
 */
public final class CombatListener extends BaseListener implements Listener {

    // --- Our projectile entity or block collision: clean up if needed ---
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow)) return;
        if (!ProjectileService.isOurArrow(arrow)) return;

        // Use Core.schedulers for consistency
        Core.schedulers.runLater(arrow::remove, 1L);
    }

    // --- central damage router ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        final Entity victimEnt = e.getEntity();
        if (!(victimEnt instanceof LivingEntity victim)) return;
        if (victim instanceof LivingEntity le && Core.damageService.isCustomHit(le)) return;

        // 1) Projectile path
        if (e.getDamager() instanceof Arrow arrow) {
            if (ProjectileService.isOurArrow(arrow)) {
                // OUR arrow: cancel vanilla and route to DamageService
                final Payload payload = ProjectileService.readArrowPayload(arrow);

                if (payload == null) {
                    // corrupted tag → just remove safely
                    Core.schedulers.runLater(arrow::remove, 1L);
                    return;
                }

                e.setCancelled(true);

                final UUID attackerId = payload.owner();
                // Optional: attacker Player, may be null if offline

                // Victim is player → record for kill credit
                if (victim instanceof Player) {
                    Core.damageService.recordHit(
                            victim.getUniqueId(),
                            attackerId,                 // keep UUID even if attacker is offline
                            DamageKind.PROJECTILE,      // or ARROW if you split kinds
                            payload.weaponId(),
                            payload.baseDamage()
                    );
                }

                // Apply our custom projectile damage (prefer UUID-first API)
                // Recommend: applyProjectileDamage(UUID attackerId, LivingEntity victim, Payload payload)
                Core.damageService.applyProjectileDamage(attackerId, victim, payload);

                Core.schedulers.runLater(arrow::remove, 1L);
                return;
            } else {
                // VANILLA arrow: only P2P should count for kill credit
                ProjectileSource src = arrow.getShooter();
                if (src instanceof Player attackerPlayer && victim instanceof Player) {
                    Core.damageService.recordHit(
                            victim.getUniqueId(),
                            attackerPlayer.getUniqueId(),
                            DamageKind.VANILLA,
                            null,
                            e.getFinalDamage()
                    );
                }
                return; // let vanilla damage proceed
            }
        }

        // 2) Non-projectile entity damage (melee, mob hits, etc.)
        // Only P2P should record hit for kill credit; otherwise ignore.
        if (e.getDamager() instanceof Player attackerP && victim instanceof Player) {
            Core.damageService.recordHit(
                    victim.getUniqueId(),
                    attackerP.getUniqueId(),
                    DamageKind.VANILLA,
                    null,
                    e.getFinalDamage()
            );
        }
    }
}
