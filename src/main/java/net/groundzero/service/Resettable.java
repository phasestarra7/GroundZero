// service/Resettable.java
package net.groundzero.service;

/**
 * Marker interface for services that hold per-session state.
 * Called by GameManager on game end/cancel to ensure clean state.
 */
public interface Resettable {
    /**
     * Clear all session-specific state.
     * Called when a match ends or is canceled.
     */
    void reset();
}