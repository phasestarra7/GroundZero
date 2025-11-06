package net.groundzero.game;

/**
 * Central place for tunable game parameters.
 * You asked to keep combat-related timings and steal percentages here.
 */
public class GameConfig {

    // match length: 20 ticks * 60 sec * 20 min = 20 minutes
    public int matchDurationTicks = 20 * 60 * 20;

    // base resources
    public double basePlasma = 0.0;
    public double baseIncomePerSecond = 10.0;
    public double baseScore = 100.0;

    /* ===== combat-related config ===== */

    public double killStealPercent = 0.10;
    public double nonPlayerDeathPenaltyPercent = 0.10;
    public long combatLogoutGraceMillis = 10_000L; // 10 seconds

    public GameConfig() {}
}
