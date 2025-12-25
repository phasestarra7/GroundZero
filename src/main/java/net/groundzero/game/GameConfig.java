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

    /* ===== actionbar ===== */
    public int actionBarIntervalTicks = 10;

    /* ===== gui, itemdata ===== */
    public int assaultPrice = 100;
    public int autoPrice = 100;
    public int sniperPrice = 100;
    public int RPGPrice = 100;
    public int concussivePrice = 100;
    public int smokePrice = 100;

    public int medkitPrice = 100;
    public int blocksPrice = 100;
    public int bridgePrice = 100;
    public int bunkerPrice = 100;
    public int antiexpPrice = 100;
    public int pearlPrice = 100;

    public int aerialSimplePrice = 100;
    public int aerialArrowPrice = 100;
    public int aerialClusterPrice = 100;
    public int aerialSpreaderPrice = 100;
    public int aerialCarpetPrice = 100;
    public int aerialHackPrice = 100;

    public int missileSimplePrice = 100;
    public int missilePoisonPrice = 100;
    public int missileBunkerPrice = 100;
    public int missileHighExpPrice = 100;
    public int missileNuclearPrice = 100;
    public int missileABMPrice = 100;

    public double assaultIncome = 0.1;
    public double autoIncome = 0.1;
    public double sniperIncome = 0.1;
    public double RPGIncome = 0.1;
    public double concussiveIncome = 0.1;
    public double smokeIncome = 0.1;

    public double medkitIncome = 0.1;
    public double blocksIncome = 0.1;
    public double bridgeIncome = 0.1;
    public double bunkerIncome = 0.1;
    public double antiexpIncome = 0.1;
    public double pearlIncome = 0.1;

    public double aerialSimpleIncome = 0.1;
    public double aerialArrowIncome = 0.1;
    public double aerialClusterIncome = 0.1;
    public double aerialSpreaderIncome = 0.1;
    public double aerialCarpetIncome = 0.1;
    public double aerialHackIncome = 0.1;

    public double missileSimpleIncome = 0.1;
    public double missilePoisonIncome = 0.1;
    public double missileBunkerIncome = 0.1;
    public double missileHighExpIncome = 0.1;
    public double missileNuclearIncome = 0.1;
    public double missileABMIncome = 0.1;

    public int assaultAmount = 30;
    public int autoAmount = 60;
    public int sniperAmount = 5;
    public int rpgAmount = 5;
    // above is magazine size
    public int concussiveAmount = 1;
    public int smokeAmount = 1;

    public int medkitAmount = 1;
    public int blocksAmount = 64;
    public int bridgeAmount = 1;
    public int bunkerAmount = 1;
    public int antiexpAmount = 1;
    public int pearlAmount = 1;

    public int aerialSimpleAmount = 1;
    public int aerialArrowAmount = 1;
    public int aerialClusterAmount = 1;
    public int aerialSpreaderAmount = 1;
    public int aerialCarpetAmount = 1;
    public int aerialHackAmount = 1;

    public int missileSimpleAmount = 1;
    public int missilePoisonAmount = 1;
    public int missileBunkerAmount = 1;
    public int missileHighExpAmount = 1;
    public int missileNuclearAmount = 1;
    public int missileAbmAmount = 1;

    /* ===== items ===== */
    public double assaultDamage = 5.0;
    public double assaultProjectileSpeed = 5.0;
    public double assaultSpread = 0.02;
    public int assaultCooldownTicks = 0;
    public int assaultMagazineSize = 30;

    public GameConfig() {}
}