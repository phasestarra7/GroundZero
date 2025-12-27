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

public class AutoModelHandler implements ModelHandler{

    private static final float SCALE_X = 0.15f;
    private static final float SCALE_Y = 0.15f;
    private static final float SCALE_Z = 0.4f;
    private static final Material MATERIAL = Material.YELLOW_CONCRETE;

    @Override
    public ModelType getModelType() {
        return ModelType.AUTO_BULLET;
    }

    @Override
    public Display createModel(Entity anchor) {
        if (anchor == null || anchor.getWorld() == null) return null;

        return anchor.getWorld().spawn(anchor.getLocation(), BlockDisplay.class, display -> {
            display.setBlock(MATERIAL.createBlockData());

            Transformation transform = ModelTransformHelper.createCentered(
                    anchor.getVelocity(), SCALE_X, SCALE_Y, SCALE_Z
            );
            display.setTransformation(transform);

            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(2.5f);
            display.setShadowRadius(0f);
            display.setShadowStrength(0f);
            display.setInterpolationDuration(1);
            display.setInterpolationDelay(0);
        });
    }

    @Override
    public void updateRotation(Display display, Vector velocity) {
        if (!(display instanceof BlockDisplay bd)) return;
        if (velocity == null || velocity.lengthSquared() < 0.001) return;

        Transformation transform = ModelTransformHelper.createCentered(velocity, SCALE_X, SCALE_Y, SCALE_Z);
        bd.setTransformation(transform);
        bd.setInterpolationDelay(0);
    }

    @Override
    public void onTick(Display display, Entity anchor, int ticksAlive) {
        if (anchor == null || anchor.getWorld() == null) return;

        // Trail particle every tick
        anchor.getWorld().spawnParticle(
                Particle.SMOKE,
                anchor.getLocation(),
                1, 0, 0, 0, 0, null, true // count, offset3, speed, (data), force
        );
    }
}
