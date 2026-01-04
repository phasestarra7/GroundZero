package net.groundzero.service.model;

import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Interface for projectile/vehicle visual model handlers.
 *
 * All handlers use List<Display> for multi-part model support.
 */
public interface ModelHandler {

    /**
     * Get the model type this handler manages.
     */
    ModelType getModelType();

    /**
     * Create visual model(s) for an anchor entity.
     *
     * @param anchor The invisible anchor entity (Arrow, etc.)
     * @return List of Display entities (can be single or multiple parts)
     */
    List<Display> createModels(Entity anchor);

    /**
     * Update rotation for all displays based on velocity.
     * Called every tick for BULLET category models.
     *
     * @param displays All displays attached to this anchor
     * @param velocity Current velocity of the anchor
     */
    void updateRotation(List<Display> displays, Vector velocity);

    /**
     * Per-tick effects (particles, sounds, etc.)
     *
     * @param displays   All displays attached to this anchor
     * @param anchor     The anchor entity
     * @param ticksAlive How many ticks since spawn
     */
    void onTick(List<Display> displays, Entity anchor, int ticksAlive);
}