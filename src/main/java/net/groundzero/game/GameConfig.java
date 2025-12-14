package net.groundzero.game;

/**
 * Central place for tunable game parameters.
 * All timing values are in ticks (20 ticks = 1 second).
 */
public class GameConfig {

    /* ===== match timing ===== */
    // match length: 20 ticks * 60 sec * 20 min = 20 minutes
    public int matchDurationTicks = 20 * 60 * 20;

    // match end result printing delay
    public int matchEndDelayTicks = 10 * 20;

    /* ===== voting phase timing ===== */
    // countdown before voting starts
    public int preVoteCountdownTicks = 5 * 20;

    // voting duration (10 seconds)
    public int voteCountdownTicks = 10 * 20;

    // delay before revealing vote result (2 seconds)
    public int voteRevealDelayTicks = 2 * 20;

    // pause between vote phases (3 seconds)
    public int votePhasePauseTicks = 3 * 20;

    // final countdown before game start
    public int finalCountdownTicks = 5 * 20;

    /* ===== respawn timing ===== */
    // time player stays in spectator before respawn (5 seconds)
    public int respawnDelayTicks = 5 * 20;

    /* ===== base resources ===== */
    public double basePlasma = 0.0;
    public double baseIncomePerSecond = 10.0;
    public double baseScore = 100.0;

    /* ===== combat-related config ===== */
    public double killStealPercent = 0.10;
    public double deathPenaltyPercent = 0.10;
    public double nonPlayerDeathPenaltyPercent = 0.05;
    public int combatWindowTicks = 200; // 10 seconds: combat window, logout grace, idle reset window

    /* ===== camping / idle-timer config ===== */
    public int campWarnTicks = 90 * 20;           // 90 seconds
    public int campFirstPenaltyTicks = 120 * 20;  // 120 seconds (2 minutes)
    public int campPenaltyIntervalTicks = 60 * 20; // 60 seconds (1 minute)
    public double campPenaltyPercent = 0.05;
    public int campMaxStacks = 3;

    /* ===== items ===== */
    public double assaultDamage = 5.0;
    public double assaultProjectileSpeed = 3.0;
    public double assaultSpread = 0.02;
    public int assaultCooldownTicks = 3;
    public int assaultMagazineSize = 30;

    public GameConfig() {}
}