package net.groundzero.service.combat;

import net.groundzero.app.Core;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Random;

/**
 * Weapon recoil service.
 *
 * Applies immediate view kick, then smoothly recovers over multiple ticks.
 * Recovery is relative to current position, preserving player mouse movement.
 *
 * Usage:
 *   Core.recoilService.applyRecoil(player, recoilPitch, recoilYaw, recoveryTicks);
 *
 * All values should be explicitly set by the handler based on weapon config and conditions
 * (e.g., ADS mode, overdrive state, etc.)
 */
public final class RecoilService {

    private static final Random RNG = new Random();

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

        // Apply immediate kick
        Location loc = player.getLocation();
        float newPitch = clampPitch(loc.getPitch() + (float) actualPitch);
        float newYaw = loc.getYaw() + (float) actualYaw;
        player.setRotation(newYaw, newPitch);

        // Schedule gradual recovery
        if (recoveryTicks <= 0) return;

        double pitchStep = actualPitch / recoveryTicks;
        double yawStep = actualYaw / recoveryTicks;

        for (int i = 1; i <= recoveryTicks; i++) {
            Core.schedulers.runLater(() -> {
                if (!player.isOnline()) return;

                Location current = player.getLocation();
                // Subtract the recoil step (recover towards original)
                float recoveredPitch = clampPitch(current.getPitch() - (float) pitchStep);
                float recoveredYaw = current.getYaw() - (float) yawStep;
                player.setRotation(recoveredYaw, recoveredPitch);
            }, i);
        }
    }

    /**
     * Clamp pitch to valid range [-90, 90]
     */
    private float clampPitch(float pitch) {
        return Math.max(-90f, Math.min(90f, pitch));
    }
}