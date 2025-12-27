package net.groundzero.service.model;

import net.groundzero.app.Core;
import net.groundzero.service.GameService;
import net.groundzero.service.model.handler.AssaultModelHandler;
import net.groundzero.service.model.handler.AutoModelHandler;
import net.groundzero.service.model.handler.SniperModelHandler;
import net.groundzero.service.tick.TickBus;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages visual models for projectiles and vehicles.
 *
 * Responsibilities:
 * - Register and lookup ModelHandlers by type
 * - Track active models (anchor → ModelData)
 * - Per-tick position sync via teleport
 * - Per-tick rotation updates for BULLET category
 * - Cleanup when anchor is removed
 *
 * Flow:
 * 1. Handler calls attachModel() after spawning anchor (Arrow, etc.)
 * 2. Service creates Display via handler (NOT as passenger - we teleport manually)
 * 3. Every tick: teleport to anchor, update rotations, call onTick(), cleanup dead anchors
 * 4. When anchor dies: remove Display, call onRemove()
 */
public final class ProjectileModelService implements TickBus.Tickable, GameService {

    // Handler registry: ModelType → Handler
    private final Map<ModelType, ModelHandler> handlers = new EnumMap<>(ModelType.class);

    // Active models: anchorId → ModelData
    private final Map<UUID, ModelData> activeModels = new ConcurrentHashMap<>();

    // Maximum lifetime before forced cleanup (20 seconds = 400 ticks)
    private static final int MAX_LIFETIME_TICKS = 400;

    private boolean running = false;

    /* ===================== Lifecycle ===================== */

    @Override
    public void start() {
        if (running) return;
        running = true;
        registerHandlers();
        Core.tickBus.register(this);
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        Core.tickBus.unregister(this);

        // Remove all active display entities
        for (ModelData data : activeModels.values()) {
            removeModelInternal(data);
        }
        activeModels.clear();
    }

    @Override
    public void reset() {
        // Remove all displays on reset too
        for (ModelData data : activeModels.values()) {
            removeModelInternal(data);
        }
        activeModels.clear();
    }

    /* ===================== Handler Registration ===================== */

    private void registerHandlers() {
        // Clear existing
        handlers.clear();

        // Register all handlers
        registerHandler(new AssaultModelHandler());
        registerHandler(new AutoModelHandler());
        registerHandler(new SniperModelHandler());
//        registerHandler(new rpgModelHandler());
//        registerHandler(new concussiveModelHandler());
//        registerHandler(new smokeModelHandler());
//
//        registerHandler(new aerialSimpleModelHandler());
//        registerHandler(new aerialArrowModelHandler());
//        registerHandler(new aerialClusterModelHandler());
//        registerHandler(new aerialSpreaderModelHandler());
//        registerHandler(new aerialCarpetModelHandler());
//        registerHandler(new aerialHackHandler());
//
//        registerHandler(new missileSimpleModelHandler());
//        registerHandler(new missilePoisonModelHandler());
//        registerHandler(new missileBunkerModelHandler());
//        registerHandler(new missileHighExpModelHandler());
//        registerHandler(new missileNuclearHandler());
//        registerHandler(new missileAbmHandler());
    }

    private void registerHandler(ModelHandler handler) {
        handlers.put(handler.getModelType(), handler);
    }

    public ModelHandler getHandler(ModelType type) {
        return handlers.get(type);
    }

    /* ===================== Model Attachment ===================== */

    /**
     * Attach a visual model to an anchor entity.
     * NOTE: Does NOT use passenger - we manually teleport every tick.
     */
    public boolean attachModel(Entity anchor, ModelType type) {
        if (anchor == null || type == null) return false;
        if (!running) {
            Core.plugin.getLogger().warning("[ProjectileModelService] Not running, cannot attach");
            return false;
        }

        ModelHandler handler = handlers.get(type);
        if (handler == null) {
            Core.plugin.getLogger().warning("[ProjectileModelService] No handler for type: " + type);
            return false;
        }

        if (activeModels.containsKey(anchor.getUniqueId())) {
            return false;
        }

        try {
            Display display = handler.createModel(anchor);
            if (display == null) {
                Core.plugin.getLogger().warning("[ProjectileModelService] Handler returned null display");
                return false;
            }

            int currentTick = Core.tickBus.getCurrentTick();
            ModelData data = new ModelData(anchor, display, type, handler, currentTick);
            activeModels.put(anchor.getUniqueId(), data);

            return true;
        } catch (Exception e) {
            Core.plugin.getLogger().warning("[ProjectileModelService] Failed to attach: " + e.getMessage());
            return false;
        }
    }

    /**
     * Manually remove model for an anchor.
     */
    public void removeModel(UUID anchorId) {
        if (anchorId == null) return;

        ModelData data = activeModels.remove(anchorId);
        if (data != null) {
            removeModelInternal(data);
        }
    }

    /* ===================== Tick Processing ===================== */

    @Override
    public void onTick(int currentTick) {
        if (!running) return;
        if (activeModels.isEmpty()) return;

        Iterator<Map.Entry<UUID, ModelData>> it = activeModels.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, ModelData> entry = it.next();
            ModelData data = entry.getValue();

            // Check if anchor still valid
            if (!data.isValid()) {
                removeModelInternal(data);
                it.remove();
                continue;
            }

            // Check if it's stupidly living long, aka. stuck
            int ticksAlive = data.getTicksAlive(currentTick);
            if (ticksAlive > MAX_LIFETIME_TICKS) {
                removeModelInternal(data);
                it.remove();
                continue;
            }

            Entity anchor = data.getAnchor();
            Display display = data.getDisplay();
            ModelHandler handler = data.getHandler();

            // === CRITICAL: Teleport display to anchor position ===
            Location a = anchor.getLocation();
            Location dst = new Location(a.getWorld(), a.getX(), a.getY(), a.getZ(), 0f, 0f);
            display.teleport(dst);

            // Update rotation for BULLET category
            if (data.getType().needsRotationUpdate()) {
                Vector velocity = anchor.getVelocity();
                handler.updateRotation(display, velocity);
            }

            // Call handler's onTick for visual effects (particles, etc.)
            handler.onTick(display, anchor, ticksAlive);
        }
    }

    /* ===================== Internal Cleanup ===================== */

    private void removeModelInternal(ModelData data) {
        if (data == null) return;

        Display display = data.getDisplay();
        if (display != null) {
            try { display.remove(); } catch (Exception ignored) {}
        }

        // remove arrow too, not being simulated
        Entity anchor = data.getAnchor();
        if (anchor != null && anchor.isValid()) {
            try { anchor.remove(); } catch (Exception ignored) {}
        }
    }

    /* ===================== Query ===================== */

    public boolean hasModel(UUID anchorId) {
        return activeModels.containsKey(anchorId);
    }

    public int getActiveModelCount() {
        return activeModels.size();
    }
}