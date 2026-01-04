package net.groundzero.service.model.handler;

import net.groundzero.service.model.ModelHandler;
import net.groundzero.service.model.ModelTransformHelper;
import net.groundzero.service.model.ModelType;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * RPG Rocket - multipart model.
 *
 * Structure:
 *   (+Z) [Warhead]====[Body] (-Z)
 *
 * Parts:
 * - Body: thin long cylinder (centered at anchor)
 * - Warhead: thick front tip (offset forward)
 */
public final class RpgModelHandler implements ModelHandler {

    // Body
    private static final float BODY_X = 0.15f;
    private static final float BODY_Y = 0.15f;
    private static final float BODY_Z = 0.5f;
    private static final Material BODY_MATERIAL = Material.GRAY_CONCRETE;

    // Warhead
    private static final float HEAD_X = 0.25f;
    private static final float HEAD_Y = 0.25f;
    private static final float HEAD_Z = 0.15f;
    private static final Material HEAD_MATERIAL = Material.RED_CONCRETE;

    // Warhead offset: attach to front of body
    // from center + body/2 (now we're at end of body) + head/2 (head's offset)
    private static final float HEAD_OFFSET_Z = (BODY_Z / 2f) + (HEAD_Z / 2f);

    @Override
    public ModelType getModelType() {
        return ModelType.RPG_ROCKET;
    }

    @Override
    public List<Display> createModels(Entity anchor) {
        if (anchor == null || anchor.getWorld() == null) return List.of();

        List<Display> displays = new ArrayList<>();
        Vector velocity = anchor.getVelocity();

        // Body (centered)
        BlockDisplay body = anchor.getWorld().spawn(anchor.getLocation(), BlockDisplay.class, d -> {
            d.setBlock(BODY_MATERIAL.createBlockData());
            d.setTransformation(ModelTransformHelper.createCentered(velocity, BODY_X, BODY_Y, BODY_Z));
            ModelTransformHelper.applySettings(d);
        });
        displays.add(body);

        // Warhead (front)
        BlockDisplay head = anchor.getWorld().spawn(anchor.getLocation(), BlockDisplay.class, d -> {
            d.setBlock(HEAD_MATERIAL.createBlockData());
            d.setTransformation(ModelTransformHelper.createWithOffset(
                    velocity,
                    HEAD_X, HEAD_Y, HEAD_Z,
                    0f, 0f, HEAD_OFFSET_Z
            ));
            ModelTransformHelper.applySettings(d);
        });
        displays.add(head);

        return displays;
    }

    @Override
    public void updateRotation(List<Display> displays, Vector velocity) {
        if (displays.size() < 2) return;
        if (velocity == null || velocity.lengthSquared() < 0.001) return;

        // Body
        if (displays.get(0) instanceof BlockDisplay body) {
            Transformation t = ModelTransformHelper.createCentered(velocity, BODY_X, BODY_Y, BODY_Z);
            body.setTransformation(t);
            body.setInterpolationDelay(0);
        }

        // Warhead
        if (displays.get(1) instanceof BlockDisplay head) {
            Transformation t = ModelTransformHelper.createWithOffset(
                    velocity,
                    HEAD_X, HEAD_Y, HEAD_Z,
                    0f, 0f, HEAD_OFFSET_Z
            );
            head.setTransformation(t);
            head.setInterpolationDelay(0);
        }
    }

    @Override
    public void onTick(List<Display> displays, Entity anchor, int ticksAlive) {
        if (anchor == null || anchor.getWorld() == null) return;

        // Smoke trail
        anchor.getWorld().spawnParticle(
                Particle.CAMPFIRE_COSY_SMOKE,
                anchor.getLocation(),
                2, 0.05, 0.05, 0.05, 0.01, null, true
        );

        // Flame trail
        anchor.getWorld().spawnParticle(
                Particle.FLAME,
                anchor.getLocation(),
                1, 0.02, 0.02, 0.02, 0.005, null, true
        );
    }
}