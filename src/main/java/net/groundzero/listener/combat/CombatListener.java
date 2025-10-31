package net.groundzero.listener.combat;

import net.groundzero.listener.BaseListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

public final class CombatListener extends BaseListener implements Listener {

    @EventHandler
    public void onAnyDamage(EntityDamageEvent e) {
        // TODO: Global guards per phase (e.g., disable damage outside RUNNING)
        // if (Core.game.state() != GameState.RUNNING) e.setCancelled(true);
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        // TODO: Resolve attacker/victim, teamkill rules, headshot modifiers, etc.
        // TODO: Delegate to DamageService or Game rules
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        // TODO: Mark projectile (PDC) with shooter, weapon id, etc.
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        // TODO: Read projectile PDC, apply AoE / knockback / visual effects via services
    }
}
