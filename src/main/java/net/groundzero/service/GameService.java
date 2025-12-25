package net.groundzero.service;

/**
 * Common interface for all game services.
 *
 * Lifecycle:
 * - start(): Called when game begins (RUNNING state)
 * - stop(): Called when game ends (unregister from TickBus, etc.)
 * - reset(): Called to clear all state (Maps, flags, etc.)
 *
 * Non-tick services can use default start/stop implementations.
 */
public interface GameService {

    /**
     * Start the service (register to TickBus, etc.)
     * Override for tick-based services.
     */
    default void start() {}

    /**
     * Stop the service (unregister from TickBus, etc.)
     * Override for tick-based services.
     */
    default void stop() {}

    /**
     * Reset all state. Called on game end/cancel.
     * All services must implement this.
     */
    void reset();
}