package net.groundzero.service.model;

import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 * Interface for projectile/vehicle visual model handlers.
 *
 * Each ModelType has its own handler that defines:
 * - How to create the Display entity (shape, scale, material)
 * - How to update rotation (if applicable)
 * - How to add effects (particles, sounds)
 *
 * Handlers are stateless - all state is in ModelData.
 */
public interface ModelHandler {

    /**
     * Get the model type this handler manages.
     */
    ModelType getModelType();

    /**
     * Create and attach Display entity to anchor.
     * Called once when projectile spawns.
     *
     * @param anchor The arrow/interaction entity to attach to
     * @return Created Display entity (as passenger of anchor)
     */
    Display createModel(Entity anchor);

    /**
     * Update model rotation based on velocity.
     * Called every tick for BULLET category.
     *
     * @param display The Display entity to update
     * @param velocity Current velocity vector of anchor
     */
    void updateRotation(Display display, Vector velocity);

    /**
     * Called every tick for visual effects (particles, trails).
     * Do NOT put game logic here.
     */
    default void onTick(Display display, Entity anchor, int ticksAlive) {}
}