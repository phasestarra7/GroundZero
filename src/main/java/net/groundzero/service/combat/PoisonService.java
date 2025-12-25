package net.groundzero.service.combat;

import net.groundzero.app.Core;
import net.groundzero.service.model.DeathCause;
import net.groundzero.service.tick.TickBus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.groundzero.service.GameService;
import org.bukkit.Location;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages poison DoT (Damage over Time) effects.
 *
 * Flow:
 * 1. MissileService calls applyPoisonArea() on MISSILE_POISON explosion
 * 2. TickBus applies damage every N ticks
 * 3. UI feedback via WITHER effect (amplifier 10 to distinguish from vanilla)
 * 4. Duration expires or overridden by stronger poison
 */
public final class PoisonService implements TickBus.Tickable, GameService {

    // WITHER amplifier to distinguish our poison from vanilla
    private static final int CUSTOM_POISON_AMPLIFIER = 9;

    private final Map<UUID, PoisonData> activePoisoned = new ConcurrentHashMap<>();
    private boolean running = false;

    /* ===================== Data Class ===================== */

    public static final class PoisonData {
        public final UUID attacker;
        public final String weaponId;
        public final double damagePerTick;
        public final int damageInterval;  // Apply damage every N ticks
        public int remainingTicks;         // Total duration remaining

        public PoisonData(UUID attacker, String weaponId, double damagePerTick,
                          int damageInterval, int remainingTicks) {
            this.attacker = attacker;
            this.weaponId = weaponId;
            this.damagePerTick = damagePerTick;
            this.damageInterval = damageInterval;
            this.remainingTicks = remainingTicks;
        }
    }

    /* ===================== Lifecycle ===================== */

    public void start() {
        if (running) return;
        running = true;
        Core.tickBus.register(this);
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        Core.tickBus.unregister(this);

        // Remove poison effects from players
        for (UUID id : activePoisoned.keySet()) {
            removePoisonEffect(id);
        }
    }


    @Override
    public void reset() {
        activePoisoned.clear();
    }

    /* ===================== Apply Poison ===================== */

    /**
     * Apply poison to area (called by MissileService on MISSILE_POISON explosion)
     */
    public void applyPoisonArea(Location center, double radius, UUID attacker, PoisonOptions opt) {
        if (center == null || center.getWorld() == null || opt == null) return;
        if (radius <= 0) return;
        if (!Core.session.state().isIngame()) return;

        double radiusSq = radius * radius;

        for (Entity ent : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(ent instanceof Player player)) continue;

            double distSq = ent.getLocation().distanceSquared(center);
            if (distSq > radiusSq) continue;

            applyPoison(player.getUniqueId(), attacker, opt);
        }
    }

    /**
     * Apply poison to single player
     */
    public void applyPoison(UUID victim, UUID attacker, PoisonOptions opt) {
        if (victim == null || opt == null) return;
        if (!Core.session.state().isIngame()) return;

        PoisonData existing = activePoisoned.get(victim);

        // If already poisoned, only replace if new duration is longer
        if (existing != null) {
            if (opt.durationTicks <= existing.remainingTicks) {
                return; // Keep existing stronger poison
            }
        }

        // Apply new poison
        PoisonData data = new PoisonData(
                attacker,
                opt.weaponId,
                opt.damagePerTick,
                opt.damageInterval,
                opt.durationTicks
        );

        activePoisoned.put(victim, data);
        applyPoisonEffect(victim, opt.durationTicks);
    }

    /* ===================== Poison Options ===================== */

    public static final class PoisonOptions {
        public String weaponId;         // REQUIRED (e.g., "gz_missile_poison")
        public double damagePerTick = 2.0;     // Damage per application
        public int damageInterval = 20;        // Apply every N ticks (20 = 1 second)
        public int durationTicks = 100;        // Total duration (100 = 5 seconds)
    }

    /* ===================== Tick Processing ===================== */

    @Override
    public void onTick(int currentTick) {
        if (!running) return;
        if (activePoisoned.isEmpty()) return;

        Iterator<Map.Entry<UUID, PoisonData>> it = activePoisoned.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, PoisonData> entry = it.next();
            UUID victimId = entry.getKey();
            PoisonData data = entry.getValue();

            Player victim = Bukkit.getPlayer(victimId);

            // Remove if player offline or dead
            if (victim == null || !victim.isOnline() || victim.isDead()) {
                removePoisonEffect(victimId);
                it.remove();
                continue;
            }

            // Apply damage every N ticks (using individual tick counter)
            if (data.remainingTicks % data.damageInterval == 0) {
                applyPoisonTick(victimId, victim, data);
            }

            // Decrease duration
            data.remainingTicks--;

            // Remove if expired
            if (data.remainingTicks <= 0) {
                removePoisonEffect(victimId);
                it.remove();
            }
        }
    }

    /**
     * Apply one tick of poison damage
     */
    private void applyPoisonTick(UUID victimId, Player victim, PoisonData data) {
        // Record hit for kill credit
        Core.damageService.recordHit(
                victimId,
                data.attacker,
                DeathCause.POISON_TICK,
                data.weaponId,
                data.damagePerTick
        );

        // Apply damage
        Core.damageService.applyCustomDamage(
                data.attacker,
                victim,
                data.damagePerTick
        );
    }

    /* ===================== UI Effects ===================== */

    /**
     * Apply WITHER visual effect (amplifier 10 to distinguish from vanilla)
     */
    private void applyPoisonEffect(UUID playerId, int durationTicks) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.WITHER,
                durationTicks,
                CUSTOM_POISON_AMPLIFIER,  // Level 11 displayed
                false,   // ambient
                true,    // particles
                true     // icon
        ));
    }

    /**
     * Remove WITHER effect (only if it's our custom poison)
     */
    private void removePoisonEffect(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        PotionEffect effect = player.getPotionEffect(PotionEffectType.WITHER);
        if (effect != null && effect.getAmplifier() >= CUSTOM_POISON_AMPLIFIER) {
            player.removePotionEffect(PotionEffectType.WITHER);
        }
    }

    /* ===================== Utilities ===================== */

    /**
     * Check if player has custom poison (used by CombatListener to block vanilla WITHER damage)
     */
    public boolean hasCustomPoison(UUID playerId) {
        return activePoisoned.containsKey(playerId);
    }

    /**
     * Check if WITHER effect is custom poison (amplifier check)
     */
    public static boolean isCustomPoisonEffect(Player player) {
        if (player == null) return false;
        PotionEffect effect = player.getPotionEffect(PotionEffectType.WITHER);
        return effect != null && effect.getAmplifier() >= CUSTOM_POISON_AMPLIFIER;
    }
}