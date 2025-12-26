package net.groundzero.service.model;

import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Utility for calculating rotations and transformations for projectile models.
 */
public final class ModelTransformHelper {

    private ModelTransformHelper() {}

    /**
     * Calculate quaternion rotation from velocity vector.
     * Aligns model's Z-axis (length) with velocity direction.
     */
    public static Quaternionf calculateRotation(Vector velocity) {
        if (velocity == null || velocity.lengthSquared() < 0.001) {
            return new Quaternionf();
        }

        Vector dir = velocity.clone().normalize();

        double yaw = Math.atan2(dir.getX(), dir.getZ());
        double pitch = -Math.asin(dir.getY());

        Quaternionf rotation = new Quaternionf();
        rotation.rotateY((float) yaw);
        rotation.rotateX((float) pitch);

        return rotation;
    }

    /**
     * Create centered transformation (model center at anchor).
     */
    public static Transformation createCentered(Vector velocity, float scaleX, float scaleY, float scaleZ) {
        return create(velocity, scaleX, scaleY, scaleZ, 0, 0, 0);
    }

    /**
     * Create transformation with local offset (offset applied after rotation).
     *
     * @param localOffsetX Left/Right offset (negative = left)
     * @param localOffsetY Up/Down offset
     * @param localOffsetZ Forward/Back offset
     */
    public static Transformation create(Vector velocity,
                                        float scaleX, float scaleY, float scaleZ,
                                        float localOffsetX, float localOffsetY, float localOffsetZ) {
        Quaternionf rotation = calculateRotation(velocity);

        // Center offset (so model center is at origin before local offset)
        float centerX = -scaleX / 2f + localOffsetX;
        float centerY = -scaleY / 2f + localOffsetY;
        float centerZ = -scaleZ / 2f + localOffsetZ;

        Vector3f translation = new Vector3f(centerX, centerY, centerZ);
        Vector3f scale = new Vector3f(scaleX, scaleY, scaleZ);
        Quaternionf rightRotation = new Quaternionf();

        return new Transformation(translation, rotation, scale, rightRotation);
    }
}