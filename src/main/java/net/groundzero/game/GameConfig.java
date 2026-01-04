package net.groundzero.game;

/**
 * Central place for tunable game parameters.
 * All timing values are in ticks (20 ticks = 1 second).
 */
public final class GameConfig {

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

    /* ===== ui periods ===== */
    public int scoreboardUpdatePeriodTicks = 1;
    public int actionBarUpdatePeriodTicks = 1;
    public int actionBarForceUpdatePeriodTicks = 10;

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

    /* ===== item price ===== */
    public int assaultPrice = 100;
    public int autoPrice = 100;
    public int sniperPrice = 100;
    public int rpgPrice = 100;
    public int stunPrice = 100;
    public int smokePrice = 100;

    public int medkitPrice = 100;
    public int blocksPrice = 100;
    public int bridgePrice = 100;
    public int bunkerPrice = 100;
    public int antiExpPrice = 100;
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
    public int missileAbmPrice = 100;

    /* ===== item income ===== */
    public double assaultIncome = 0.1;
    public double autoIncome = 0.1;
    public double sniperIncome = 0.1;
    public double rpgIncome = 0.1;
    public double stunIncome = 0.1;
    public double smokeIncome = 0.1;

    public double medkitIncome = 0.1;
    public double blocksIncome = 0.1;
    public double bridgeIncome = 0.1;
    public double bunkerIncome = 0.1;
    public double antiExpIncome = 0.1;
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
    public double missileAbmIncome = 0.1;

    /* ===== item amount ===== */
    public int assaultAmount = 30;   // magazine size
    public int autoAmount = 60;      // magazine size
    public int sniperAmount = 5;     // magazine size
    public int rpgAmount = 5;        // magazine size
    public int stunAmount = 1;
    public int smokeAmount = 1;

    public int medkitAmount = 1;
    public int blocksAmount = 64;
    public int bridgeAmount = 1;
    public int bunkerAmount = 1;
    public int antiExpAmount = 1;
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

    /* ===== item cooldown ===== */
    public int assaultCooldownTicksL = 0;
    public int assaultCooldownTicksR = 20;
    public int autoCooldownTicksL = 20;
    public int autoCooldownTicksR = 20;
    public int sniperCooldownTicksL = 100;
    public int sniperCooldownTicksR = 20;
    public int rpgCooldownTicksL = 0;
    public int rpgCooldownTicksR = 50;
    public int stunCooldownTicksR = 20;
    public int smokeCooldownTicksR = 20;

    public int medkitCooldownTicksR = 20;
    public int blocksCooldownTicksR = 0;
    public int bridgeCooldownTicksR = 20;
    public int bunkerCooldownTicksR = 20;
    public int antiExpCooldownTicksR = 20;
    public int pearlCooldownTicksR = 20;

    public int aerialSimpleCooldownTicksR = 20;
    public int aerialArrowCooldownTicksR = 20;
    public int aerialClusterCooldownTicksR = 20;
    public int aerialSpreaderCooldownTicksR = 20;
    public int aerialCarpetCooldownTicksR = 20;
    public int aerialHackCooldownTicksR = 20;

    public int missileSimpleCooldownTicksL = 20;
    public int missileSimpleCooldownTicksR = 20;
    public int missilePoisonCooldownTicksL = 20;
    public int missilePoisonCooldownTicksR = 20;
    public int missileBunkerCooldownTicksL = 20;
    public int missileBunkerCooldownTicksR = 20;
    public int missileHighExpCooldownTicksL = 20;
    public int missileHighExpCooldownTicksR = 20;
    public int missileNuclearCooldownTicksL = 20;
    public int missileNuclearCooldownTicksR = 20;
    public int missileAbmCooldownTicksL = 20;
    public int missileAbmCooldownTicksR = 20;

    /* ===== Assault Rifle ===== */
    public double assaultDamage = 5.0;
    public double assaultProjectileSpeed = 5.0;
    public double assaultSpread = 0.05;
    public int assaultMagazineSize = 30;
    public int assaultReloadTicks = 60;          // 3 seconds
    public double assaultRecoilPitch = 0.75;      // upward kick
    public double assaultRecoilYaw = 0.5;        // left/right range
    public int assaultRecoilRecoveryTicks = 4;   // ticks to recover

    /* ===== Auto Rifle ===== */
    public double autoDamage = 5.0;
    public double autoProjectileSpeed = 5.0;
    public double autoSpread = 0.0025;            // x #
    public int autoMagazineSize = 60;
    public int autoReloadTicks = 100;             // 5 seconds
    public double autoRecoilPitch = 0.075;        // x #
    public double autoRecoilYaw = 0.05;           // x #
    public int autoRecoilRecoveryTicks = 2;

    public int autoOverloadGainPerTick = 5;
    public int autoOverloadLossPerTick = 1;
    public int autoOverloadConsumePerShot = 5;
    public int autoOverloadMax = 999;
    public int autoFireStartDelayTicks = 10;
    public int autoFireIntervalTicks = 2;

    /* ===== Sniper Rifle ===== */
    public double sniperDamage = 20.0;
    public double sniperProjectileSpeed = 15.0;
    public double sniperSpread = 0.00;            // no spread when scoped
    public int sniperMagazineSize = 5;
    public int sniperReloadTicks = 100;          // 5 seconds
    public double sniperRecoilPitch = 15.0;
    public double sniperRecoilYaw = 1.0;
    public int sniperRecoilRecoveryTicks = 20;

    /* ===== RPG ===== */
    public double rpgDamage = 0.0;              // direct hit (explosion separate)
    public double rpgProjectileSpeed = 2.5;
    public double rpgSpread = 0.01;
    public int rpgMagazineSize = 5;
    public int rpgReloadTicks = 100;             // 5 seconds
    public double rpgRecoilPitch = 0.75;
    public double rpgRecoilYaw = 0.5;
    public int rpgRecoilRecoveryTicks = 4;

    public double rpgExplosionDamage = 20.0;
    public double rpgBlastRadius = 4.0;

    public double rpgRocketJumpVelocity = 1.5;
    public double rpgRocketJumpSelfDamage = 5.0;

    public GameConfig() {}
}