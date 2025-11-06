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
    ENDED;

    /** @return true when we are not in any match. */
    public boolean isIdleLike() {
        return this == IDLE || this == ENDED;
    }

    /** @return true when we are before the actual match start (all voting/countdown). */
    public boolean isPregame() {
        return this == COUNTDOWN_BEFORE_VOTING
                || this == VOTING_MAP_SIZE
                || this == VOTING_INCOME_MULTIPLIER
                || this == VOTING_GAME_MODE
                || this == COUNTDOWN_BEFORE_START;
    }

    /**
     * "In game" here means actual running gameplay phases.
     * Later, when you add SUDDEN_DEATH / RED_ZONE / OVERTIME, put them here too.
     */
    public boolean isIngame() {
        return this == RUNNING;
    }
}
