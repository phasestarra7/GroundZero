package net.groundzero.service.model;

import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Helper for creating BlockDisplay transformations.
 *
 * Coordinate system (after rotation, local space):
 *   +X = right
 *   +Y = up
 *   +Z = forward (velocity direction)
 *
 * Transformation order:
 *   1. translation (position offset)
 *   2. leftRotation (pre-rotation, unused)
 *   3. scale (size)
 *   4. rightRotation (direction rotation)
 */
public final class ModelTransformHelper {

    private ModelTransformHelper() {}

    /**
     * Create transformation centered at anchor position.
     * Block center aligns with anchor location.
     *
     * @param velocity Direction vector for rotation
     * @param scaleX   Block scale X
     * @param scaleY   Block scale Y
     * @param scaleZ   Block scale Z (length along velocity)
     */
    public static Transformation createCentered(Vector velocity, float scaleX, float scaleY, float scaleZ) {
        return createWithOffset(velocity, scaleX, scaleY, scaleZ, 0f, 0f, 0f);
    }

    /**
     * Create transformation with local offset (applied AFTER rotation).
     *
     * @param velocity Direction vector for rotation
     * @param scaleX   Block scale X
     * @param scaleY   Block scale Y
     * @param scaleZ   Block scale Z (length along velocity)
     * @param offsetX  Local offset X (right, perpendicular to velocity)
     * @param offsetY  Local offset Y (up)
     * @param offsetZ  Local offset Z (forward, along velocity)
     */
    public static Transformation createWithOffset(
            Vector velocity,
            float scaleX, float scaleY, float scaleZ,
            float offsetX, float offsetY, float offsetZ
    ) {
        Quaternionf rotation = velocityToQuaternion(velocity);

        // Center offset: moves block center to origin (local coords)
        Vector3f centerOffset = new Vector3f(-scaleX / 2f, -scaleY / 2f, -scaleZ / 2f);

        // Additional offset (local coords)
        Vector3f localOffset = new Vector3f(offsetX, offsetY, offsetZ);

        // Both must be rotated to world coords
        centerOffset.rotate(rotation);
        localOffset.rotate(rotation);

        Vector3f translation = new Vector3f(
                centerOffset.x + localOffset.x,
                centerOffset.y + localOffset.y,
                centerOffset.z + localOffset.z
        );

        return new Transformation(
                translation,
                rotation,
                new Vector3f(scaleX, scaleY, scaleZ),
                new Quaternionf()
        );
    }

    /**
     * Convert velocity vector to quaternion rotation.
     * Results in the +Z axis pointing along velocity direction.
     */
    private static Quaternionf velocityToQuaternion(Vector velocity) {
        if (velocity == null || velocity.lengthSquared() < 0.001) {
            return new Quaternionf();
        }

        Vector dir = velocity.clone().normalize();

        // Yaw: rotation around Y axis (horizontal direction)
        float yaw = (float) Math.atan2(dir.getX(), dir.getZ());

        // Pitch: rotation around X axis (vertical angle)
        float pitch = (float) -Math.asin(dir.getY());

        Quaternionf q = new Quaternionf();
        q.rotateY(yaw);
        q.rotateX(pitch);
        return q;
    }

    /**
     * Apply default settings for display
     * e.g) brightness(to avoid color changing by light), distance(visible for longer distance)
     */
    public static void applySettings(Display d) {
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(2.5f);
        d.setShadowRadius(0f);
        d.setShadowStrength(0f);
        d.setInterpolationDuration(1);
        d.setInterpolationDelay(0);
    }
}