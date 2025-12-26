package net.groundzero.service.combat;

import net.groundzero.app.Core;
import net.groundzero.service.record.DeathCause;
import net.groundzero.service.record.LastHit;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles kill credit and scoring on death.
 * Uses Core.gameConfig.combatWindowTicks as the combat window.
 *
 * Death messages are generated based on DeathCause.
 */
public final class CombatOutcomeService {

    /**
     * Normal death during the match.
     */
    public void handlePlayerDeath(Player victim, String vanillaMsg) {
        if (victim == null || !Core.session.state().isIngame()) return;
        applyDeathScoring(victim.getUniqueId(), victim.getName());
    }

    /**
     * Logout during the match. Treated as a death for scoring.
     */
    public void handleLogoutDeath(Player victim) {
        if (victim == null || !Core.session.state().isIngame()) return;

        UUID victimId = victim.getUniqueId();
        String victimName = victim.getName();

        double vScore = Core.session.getScoreMap().getOrDefault(victimId, 0.0);

        LastHit last = Core.damageService.peekLastHit(victimId);
        UUID aId = resolveAttackerInWindow(last);

        if (aId != null) {
            double loss = Math.max(0.0, vScore * clamp01(Core.gameConfig.deathPenaltyPercent));
            double aScore = Core.session.getScoreMap().getOrDefault(aId, 0.0);
            double gain = Math.max(0.0, vScore * clamp01(Core.gameConfig.killStealPercent));

            Core.session.getScoreMap().put(victimId, Math.max(0.0, vScore - loss));
            Core.session.getScoreMap().put(aId, Math.max(0.0, aScore + gain));

            Player a = Bukkit.getPlayer(aId);
            String aName = (a != null ? a.getName() : "Unknown");

            Core.notifier.broadcast(
                    Bukkit.getOnlinePlayers(),
                    Sound.ENTITY_PLAYER_LEVELUP,
                    Notifier.PitchLevel.MID,
                    false,
                    "&a" + victimName + " &flogged out and was killed by &a" + aName,
                    "&a" + aName + "&f : &e+" + fmt(gain) + " points",
                    "&a" + victimName + "&f : &c-" + fmt(loss) + " points"
            );
        } else {
            double loss = Math.max(0.0, vScore * clamp01(Core.gameConfig.nonPlayerDeathPenaltyPercent));
            Core.session.getScoreMap().put(victimId, Math.max(0.0, vScore - loss));

            Core.notifier.broadcast(
                    Bukkit.getOnlinePlayers(),
                    Sound.BLOCK_NOTE_BLOCK_BASS,
                    Notifier.PitchLevel.MID,
                    false,
                    "&a" + victimName + " &flogged out",
                    "&a" + victimName + "&f : &c-" + fmt(loss) + " points"
            );
        }
    }

    /* =========================================================
     * internal scoring
     * ========================================================= */

    private void applyDeathScoring(UUID victimId, String victimName) {
        double vScore = Core.session.getScoreMap().getOrDefault(victimId, 0.0);

        LastHit last = Core.damageService.peekLastHit(victimId);
        UUID aId = resolveAttackerInWindow(last);

        if (aId != null) {
            double loss = Math.max(0.0, vScore * clamp01(Core.gameConfig.deathPenaltyPercent));
            double aScore = Core.session.getScoreMap().getOrDefault(aId, 0.0);
            double gain = Math.max(0.0, vScore * clamp01(Core.gameConfig.killStealPercent));

            Core.session.getScoreMap().put(victimId, Math.max(0.0, vScore - loss));
            Core.session.getScoreMap().put(aId, Math.max(0.0, aScore + gain));

            Player a = Bukkit.getPlayer(aId);
            String aName = (a != null ? a.getName() : "Unknown");

            String deathLine = buildDeathLineWithAttacker(last, victimName, aName);

            Core.notifier.broadcast(
                    Bukkit.getOnlinePlayers(),
                    Sound.ENTITY_PLAYER_LEVELUP,
                    Notifier.PitchLevel.HIGH,
                    false,
                    deathLine,
                    "&a" + aName + "&f : &e+" + fmt(gain) + " points",
                    "&a" + victimName + "&f : &c-" + fmt(loss) + " points"
            );
        } else {
            double loss = Math.max(0.0, vScore * clamp01(Core.gameConfig.nonPlayerDeathPenaltyPercent));
            Core.session.getScoreMap().put(victimId, Math.max(0.0, vScore - loss));

            String deathLine = buildDeathLineNoAttacker(last, victimName);

            Core.notifier.broadcast(
                    Bukkit.getOnlinePlayers(),
                    Sound.BLOCK_NOTE_BLOCK_BASS,
                    Notifier.PitchLevel.MID,
                    false,
                    deathLine,
                    "&a" + victimName + "&f : &c-" + fmt(loss) + " points"
            );
        }
    }

    private UUID resolveAttackerInWindow(LastHit last) {
        if (last == null) return null;

        int nowTicks = Core.session.remainingTicks();
        int dt = last.tick - nowTicks;
        boolean inWindow = (dt >= 0) && (dt < Core.gameConfig.combatWindowTicks);

        return inWindow ? last.attacker : null;
    }

    /* =========================================================
     * Death message builders
     * ========================================================= */

    private String buildDeathLineWithAttacker(LastHit last, String victimName, String attackerName) {
        if (last == null) {
            return "&a" + victimName + " &fwas killed by &a" + attackerName;
        }

        DeathCause cause = (last.cause != null ? last.cause : DeathCause.UNKNOWN);
        String weapon = formatWeaponLabel(last.weaponId);

        return switch (cause) {
            // Custom Weapons (arrow-based)
            case ASSAULT, AUTO, SNIPER, CONCUSSIVE ->
                    "&a" + victimName + " &fwas shot by &a" + attackerName
                            + (weapon != null ? " &fusing &e" + weapon : "");

            // RPG / Custom TNT
            case RPG ->
                    "&a" + victimName + " &fwas blown up by &a" + attackerName + "&f's RPG";
            case CUSTOM_TNT ->
                    "&a" + victimName + " &fwas blown up by &a" + attackerName;

            // Poison
            case POISON_TICK ->
                    "&a" + victimName + " &fsuccumbed to poison from &a" + attackerName;

            // Aerial Support
            case AERIAL_SIMPLE, AERIAL_ARROW ->
                    "&a" + victimName + " &fwas hit by &a" + attackerName + "&f's airstrike";
            case AERIAL_CLUSTER ->
                    "&a" + victimName + " &fwas shredded by &a" + attackerName + "&f's cluster strike";
            case AERIAL_RANDOM ->
                    "&a" + victimName + " &fwas caught in &a" + attackerName + "&f's bombardment";
            case AERIAL_CARPET ->
                    "&a" + victimName + " &fwas obliterated by &a" + attackerName + "&f's carpet bombing";
            case AERIAL_HACK ->
                    "&a" + victimName + " &fwas hacked by &a" + attackerName;

            // Missiles
            case MISSILE_SIMPLE ->
                    "&a" + victimName + " &fwas hit by &a" + attackerName + "&f's missile";
            case MISSILE_POISON ->
                    "&a" + victimName + " &fwas poisoned by &a" + attackerName + "&f's chemical missile";
            case MISSILE_BUNKER_BUSTER ->
                    "&a" + victimName + " &fwas crushed by &a" + attackerName + "&f's bunker buster";
            case MISSILE_HIGH_EXPLOSIVE ->
                    "&a" + victimName + " &fwas vaporized by &a" + attackerName + "&f's HE missile";
            case MISSILE_NUCLEAR ->
                    "&a" + victimName + " &fwas nuked by &a" + attackerName;
            case MISSILE_ABM ->
                    "&a" + victimName + " &fwas intercepted by &a" + attackerName + "&f's ABM";

            // Vanilla Combat
            case MELEE ->
                    "&a" + victimName + " &fwas slain by &a" + attackerName;
            case VANILLA_PROJECTILE ->
                    "&a" + victimName + " &fwas shot by &a" + attackerName;

            // Environment with attacker credit
            case FALL ->
                    "&a" + victimName + " &fwas doomed to fall by &a" + attackerName;
            case VOID ->
                    "&a" + victimName + " &fdidn't want to live in the same world as &a" + attackerName;
            case LAVA ->
                    "&a" + victimName + " &ftried to swim in lava to escape &a" + attackerName;
            case FIRE, FIRE_TICK, CAMPFIRE ->
                    "&a" + victimName + " &fburned to death whilst fighting &a" + attackerName;
            case HOT_FLOOR ->
                    "&a" + victimName + " &fwalked into the danger zone due to &a" + attackerName;
            case DROWNING ->
                    "&a" + victimName + " &fdrowned whilst trying to escape &a" + attackerName;
            case SUFFOCATION, CRAMMING ->
                    "&a" + victimName + " &fsuffocated whilst fighting &a" + attackerName;
            case EXPLOSION ->
                    "&a" + victimName + " &fwas blown up by &a" + attackerName;
            case CACTUS ->
                    "&a" + victimName + " &fwas pricked to death whilst trying to escape &a" + attackerName;
            case SWEET_BERRY ->
                    "&a" + victimName + " &fwas poked to death whilst trying to escape &a" + attackerName;
            case LIGHTNING ->
                    "&a" + victimName + " &fwas struck by lightning whilst fighting &a" + attackerName;
            case STARVATION ->
                    "&a" + victimName + " &fstarved to death whilst fighting &a" + attackerName;
            case VANILLA_POISON ->
                    "&a" + victimName + " &fwas poisoned whilst fighting &a" + attackerName;
            case WITHER ->
                    "&a" + victimName + " &fwithered away whilst fighting &a" + attackerName;
            case MAGIC, DRAGON_BREATH ->
                    "&a" + victimName + " &fwas killed by magic whilst trying to escape &a" + attackerName;
            case THORNS ->
                    "&a" + victimName + " &fwas killed trying to hurt &a" + attackerName;
            case FALLING_BLOCK ->
                    "&a" + victimName + " &fwas squashed by &a" + attackerName;
            case FLY_INTO_WALL ->
                    "&a" + victimName + " &fexperienced kinetic energy whilst trying to escape &a" + attackerName;
            case FREEZE ->
                    "&a" + victimName + " &ffroze to death whilst fighting &a" + attackerName;
            case SONIC_BOOM ->
                    "&a" + victimName + " &fwas obliterated by a shriek whilst fighting &a" + attackerName;
            case WORLD_BORDER ->
                    "&a" + victimName + " &fleft the confines of this world whilst fighting &a" + attackerName;

            // Mobs / fallback
            case KILL, MOB, SMOKE, UNKNOWN ->
                    "&a" + victimName + " &fwas killed by &a" + attackerName;
            default ->
                    "&a" + victimName + " &fwas killed by &a" + attackerName;
        };
    }

    private String buildDeathLineNoAttacker(LastHit last, String victimName) {
        if (last == null) {
            return "&a" + victimName + " &fdied";
        }

        DeathCause cause = (last.cause != null ? last.cause : DeathCause.UNKNOWN);

        return switch (cause) {
            case FALL ->
                    "&a" + victimName + " &ffell from a high place";
            case VOID ->
                    "&a" + victimName + " &ffell out of the world";
            case LAVA ->
                    "&a" + victimName + " &ftried to swim in lava";
            case FIRE ->
                    "&a" + victimName + " &fwent up in flames";
            case FIRE_TICK ->
                    "&a" + victimName + " &fburned to death";
            case HOT_FLOOR ->
                    "&a" + victimName + " &fdiscovered the floor was lava";
            case CAMPFIRE ->
                    "&a" + victimName + " &fburned to death";
            case DROWNING ->
                    "&a" + victimName + " &fdrowned";
            case SUFFOCATION ->
                    "&a" + victimName + " &fsuffocated in a wall";
            case CRAMMING ->
                    "&a" + victimName + " &fwas squished too much";
            case EXPLOSION ->
                    "&a" + victimName + " &fblew up";
            case CACTUS ->
                    "&a" + victimName + " &fwas pricked to death";
            case SWEET_BERRY ->
                    "&a" + victimName + " &fwas poked to death by a sweet berry bush";
            case LIGHTNING ->
                    "&a" + victimName + " &fwas struck by lightning";
            case STARVATION ->
                    "&a" + victimName + " &fstarved to death";
            case VANILLA_POISON ->
                    "&a" + victimName + " &fwas poisoned";
            case WITHER ->
                    "&a" + victimName + " &fwithered away";
            case MAGIC ->
                    "&a" + victimName + " &fwas killed by magic";
            case DRAGON_BREATH ->
                    "&a" + victimName + " &fwas roasted in dragon breath";
            case THORNS ->
                    "&a" + victimName + " &fwas killed by thorns";
            case FALLING_BLOCK ->
                    "&a" + victimName + " &fwas squashed by a falling block";
            case FLY_INTO_WALL ->
                    "&a" + victimName + " &fexperienced kinetic energy";
            case FREEZE ->
                    "&a" + victimName + " &ffroze to death";
            case SONIC_BOOM ->
                    "&a" + victimName + " &fwas obliterated by a sonically-charged shriek";
            case WORLD_BORDER ->
                    "&a" + victimName + " &fleft the confines of this world";
            case KILL ->
                    "&a" + victimName + " &fwas removed from the game";
            case MOB ->
                    "&a" + victimName + " &fwas killed";
            default ->
                    "&a" + victimName + " &fdied";
        };
    }

    /* =========================================================
     * Helpers
     * ========================================================= */

    private String formatWeaponLabel(String weaponId) {
        if (weaponId == null || weaponId.isEmpty()) return null;

        String id = weaponId;
        if (id.startsWith("gz_")) {
            id = id.substring(3);
        }

        id = id.replace('_', ' ').replace('-', ' ');

        String[] parts = id.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }
}