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
    public double deathPenaltyPercent = 0.10;
    public double nonPlayerDeathPenaltyPercent = 0.05;
    public int combatWindowTicks = 200; // used as: combat window, logout grace, idle reset window

    /* ===== camping / idle-timer config ===== */
    public int campWarnTicks = 90 * 20;
    public int campFirstPenaltyTicks = 120 * 20;
    public int campPenaltyIntervalTicks = 60 * 20;
    public double campPenaltyPercent = 0.05;
    public int campMaxStacks = 3;

    public GameConfig() {}
}
