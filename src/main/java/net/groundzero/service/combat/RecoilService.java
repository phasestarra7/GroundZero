package net.groundzero.service.combat;

import io.papermc.paper.entity.TeleportFlag;
import net.groundzero.app.Core;
import net.groundzero.service.GameService;
import net.groundzero.service.tick.TickBus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Weapon recoil service (TickBus-based).
 *
 * Applies immediate view kick, then smoothly recovers over multiple ticks.
 * Recovery deltas are accumulated per-tick to handle rapid fire correctly.
 *
 * Problem solved:
 * - Old approach: Multiple Scheduler tasks for same tick → multiple setRotation calls → mouse movement lost
 * - New approach: Accumulate recovery deltas per tick → single setRotation call per tick
 *
 * Example (recoveryTicks=3, firing at t, t+1, t+2):
 * - Shot at t:   schedules recovery for t+1, t+2, t+3
 * - Shot at t+1: schedules recovery for t+2, t+3, t+4
 * - Shot at t+2: schedules recovery for t+3, t+4, t+5
 *
 * Result:
 * - t+1: delta from shot 1
 * - t+2: delta from shot 1 + shot 2
 * - t+3: delta from shot 1 + shot 2 + shot 3
 * - t+4: delta from shot 2 + shot 3
 * - t+5: delta from shot 3
 */
public final class RecoilService implements TickBus.Tickable, GameService {

    private static final Random RNG = new Random();

    /**
     * Recovery delta for a single tick.
     * Values are accumulated when multiple shots overlap.
     */
    private static final class RecoveryDelta {
        double pitch;
        double yaw;

        void add(double p, double y) {
            this.pitch += p;
            this.yaw += y;
        }
    }

    /**
     * Per-player scheduled recovery.
     * Key: absolute tick number, Value: accumulated (pitch, yaw) to recover
     */
    private final Map<UUID, Map<Integer, RecoveryDelta>> scheduledRecovery = new ConcurrentHashMap<>();

    private boolean running = false;

    /* ===================== Lifecycle ===================== */

    @Override
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
    }

    @Override
    public void reset() {
        scheduledRecovery.clear();
    }

    /* ===================== Public API ===================== */

    /**
     * Apply recoil with explicit values.
     *
     * @param player        Target player
     * @param recoilPitch   Upward kick amount (positive = up)
     * @param recoilYaw     Left/right random range
     * @param recoveryTicks Ticks to recover (0 = no recovery)
     */
    public void applyRecoil(Player player, double recoilPitch, double recoilYaw, int recoveryTicks) {
        if (player == null || !player.isOnline()) return;
        if (recoilPitch <= 0 && recoilYaw <= 0) return;

        // Random yaw direction (left or right)
        double actualYaw = (RNG.nextDouble() * 2 - 1) * recoilYaw;
        // Pitch always goes up (negative = up in Minecraft)
        double actualPitch = -recoilPitch;

        // 1. Apply immediate kick
        applyRelativeRotation(player, actualYaw, actualPitch);

        // 2. Schedule gradual recovery
        if (recoveryTicks <= 0) return;

        UUID playerId = player.getUniqueId();
        int currentTick = Core.tickBus.getCurrentTick();

        // Calculate per-tick recovery step
        double pitchStep = actualPitch / recoveryTicks;
        double yawStep = actualYaw / recoveryTicks;

        // Get or create player's schedule map
        Map<Integer, RecoveryDelta> playerSchedule = scheduledRecovery
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        // Add recovery delta to each future tick
        for (int i = 1; i <= recoveryTicks; i++) {
            int targetTick = currentTick + i;
            RecoveryDelta delta = playerSchedule.computeIfAbsent(targetTick, k -> new RecoveryDelta());
            // Subtract the recoil step (recover towards original)
            delta.add(-pitchStep, -yawStep);
        }
    }

    private static void applyRelativeRotation(Player player, double yawDelta, double pitchDelta) {
        if (player == null || !player.isOnline()) return;
        if (yawDelta == 0.0 && pitchDelta == 0.0) return;

        Location base = player.getLocation();

        float yaw = base.getYaw() + (float) yawDelta;
        float pitch = clampPitch(base.getPitch() + (float) pitchDelta);

        Location target = new Location(
                base.getWorld(),
                base.getX(), base.getY(), base.getZ(),
                yaw, pitch
        );

        // Also keep velocity so "teleporting to same place" doesn't kill movement feel.
        player.teleport(
                target,
                PlayerTeleportEvent.TeleportCause.PLUGIN,
                TeleportFlag.Relative.X,
                TeleportFlag.Relative.Y,
                TeleportFlag.Relative.Z,
                TeleportFlag.Relative.YAW,
                TeleportFlag.Relative.PITCH
        );
    }

    /* ===================== Tick Processing ===================== */

    @Override
    public void onTick(int currentTick) {
        if (!running) return;
        if (scheduledRecovery.isEmpty()) return;

        Iterator<Map.Entry<UUID, Map<Integer, RecoveryDelta>>> playerIt = scheduledRecovery.entrySet().iterator();

        while (playerIt.hasNext()) {
            Map.Entry<UUID, Map<Integer, RecoveryDelta>> playerEntry = playerIt.next();
            UUID playerId = playerEntry.getKey();
            Map<Integer, RecoveryDelta> schedule = playerEntry.getValue();

            // Get this tick's recovery delta
            RecoveryDelta delta = schedule.remove(currentTick);

            if (delta != null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    // Apply accumulated recovery in single setRotation call
                    applyRelativeRotation(player, delta.yaw, delta.pitch);
                }
            }

            // Clean up empty player schedules
            if (schedule.isEmpty()) {
                playerIt.remove();
            }
        }

        // Cleanup old entries (in case player disconnected mid-recovery)
        cleanupOldEntries(currentTick);
    }

    /* ===================== Internal ===================== */

    private static float clampPitch(float pitch) {
        return Math.max(-90f, Math.min(90f, pitch));
    }

    /**
     * Remove entries older than current tick (stale data from disconnected players).
     */
    private void cleanupOldEntries(int currentTick) {
        for (Map<Integer, RecoveryDelta> schedule : scheduledRecovery.values()) {
            schedule.keySet().removeIf(tick -> tick < currentTick);
        }
    }

    /**
     * Clear all scheduled recovery for a player (e.g., on death).
     */
    public void clearPlayer(UUID playerId) {
        scheduledRecovery.remove(playerId);
    }
}