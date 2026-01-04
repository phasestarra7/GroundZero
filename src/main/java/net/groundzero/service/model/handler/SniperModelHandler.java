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
 * Sniper Rifle bullet - single part model.
 */
public final class SniperModelHandler implements ModelHandler {

    private static final float SCALE_X = 0.15f;
    private static final float SCALE_Y = 0.15f;
    private static final float SCALE_Z = 0.4f;
    private static final Material MATERIAL = Material.RED_CONCRETE;

    @Override
    public ModelType getModelType() {
        return ModelType.SNIPER_BULLET;
    }

    @Override
    public List<Display> createModels(Entity anchor) {
        if (anchor == null || anchor.getWorld() == null) return List.of();

        List<Display> displays = new ArrayList<>();

        BlockDisplay body = anchor.getWorld().spawn(anchor.getLocation(), BlockDisplay.class, d -> {
            d.setBlock(MATERIAL.createBlockData());
            d.setTransformation(ModelTransformHelper.createCentered(
                    anchor.getVelocity(), SCALE_X, SCALE_Y, SCALE_Z
            ));
            ModelTransformHelper.applySettings(d);
        });
        displays.add(body);

        return displays;
    }

    @Override
    public void updateRotation(List<Display> displays, Vector velocity) {
        if (displays.isEmpty()) return;
        if (velocity == null || velocity.lengthSquared() < 0.001) return;

        if (displays.get(0) instanceof BlockDisplay bd) {
            Transformation t = ModelTransformHelper.createCentered(velocity, SCALE_X, SCALE_Y, SCALE_Z);
            bd.setTransformation(t);
            bd.setInterpolationDelay(0);
        }
    }

    @Override
    public void onTick(List<Display> displays, Entity anchor, int ticksAlive) {
        if (anchor == null || anchor.getWorld() == null) return;

        anchor.getWorld().spawnParticle(
                Particle.SMOKE,
                anchor.getLocation(),
                1, 0, 0, 0, 0, null, true
        );
    }
}