package net.groundzero.game;

/** Single source of truth for the high-level phase machine. */
public enum GameState {
    IDLE,
    COUNTDOWN_BEFORE_VOTING,
    VOTING_MAP_SIZE,
    VOTING_INCOME_MULTIPLIER,
    VOTING_GAME_MODE,
    COUNTDOWN_BEFORE_START,
    RUNNING,
    ENDED
}
