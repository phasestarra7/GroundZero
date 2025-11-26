package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.service.model.DamageKind;
import net.groundzero.service.model.LastHit;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles kill credit and scoring on death.
 * Uses Core.gameConfig.combatWindowTicks as the shared combat window.
 */
public final class CombatOutcomeService {

    /**
     * Normal death during the match.
     * This overload does not know the vanilla death message.
     */
    public void handlePlayerDeath(Player victim) {
        handlePlayerDeath(victim, null);
    }

    /**
     * Normal death during the match, with the original vanilla death message.
     *
     * @param victim     player who died
     * @param vanillaMsg original vanilla death message (may be null)
     */
    public void handlePlayerDeath(Player victim, String vanillaMsg) {
        if (victim == null || !Core.session.state().isIngame()) return;
        applyDeathScoring(victim.getUniqueId(), victim.getName(), vanillaMsg);
    }

    /**
     * Logout during the match. Treated as a death for scoring/kill credit,
     * but the death message is intentionally simple:
     *  - attacker present  -> "<victim> was killed by <attacker>"
     *  - no attacker       -> "<victim> died"
     *
     * This does NOT use vanillaMsg, DamageKind, or weaponId.
     */
    public void handleLogoutDeath(Player victim) {
        if (victim == null || !Core.session.state().isIngame()) return;

        UUID victimId = victim.getUniqueId();
        String victimName = victim.getName();

        double vScore = Core.session.getScoreMap().getOrDefault(victimId, 0.0);

        // Resolve attacker within the same combat window as normal deaths.
        LastHit last = Core.damageService.peekLastHit(victimId);
        boolean inWindow = false;
        if (last != null) {
            int nowTicks = Core.session.remainingTicks();
            int dt = last.tick - nowTicks;
            inWindow = (dt >= 0) && (dt < Core.gameConfig.combatWindowTicks);
        }

        UUID aId = (inWindow && last != null ? last.attacker : null);

        if (aId != null) {
            // Attacker exists: same score rules as a normal kill.
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
                    "&a" + victimName + " &fwas killed by &a" + aName,
                    "&a" + aName + "&f : &e+" + fmt(gain) + " points",
                    "&a" + victimName + "&f : &c-" + fmt(loss) + " points"
            );
        } else {
            // No attacker credited.
            double loss = Math.max(0.0, vScore * clamp01(Core.gameConfig.nonPlayerDeathPenaltyPercent));
            Core.session.getScoreMap().put(victimId, Math.max(0.0, vScore - loss));

            Core.notifier.broadcast(
                    Bukkit.getOnlinePlayers(),
                    Sound.BLOCK_NOTE_BLOCK_BASS,
                    Notifier.PitchLevel.MID,
                    false,
                    "&a" + victimName + " &fdied",
                    "&a" + victimName + "&f : &c-" + fmt(loss) + " points"
            );
        }
    }

    /* =========================================================
     * internal scoring for normal deaths
     * ========================================================= */

    private void applyDeathScoring(UUID victimId, String victimName, String vanillaMsg) {
        double vScore = Core.session.getScoreMap().getOrDefault(victimId, 0.0);

        // Resolve attacker within window (environment/mob deaths included)
        LastHit last = Core.damageService.peekLastHit(victimId);
        boolean inWindow = false;
        if (last != null) {
            int nowTicks = Core.session.remainingTicks();
            int dt = last.tick - nowTicks;
            inWindow = (dt >= 0) && (dt < Core.gameConfig.combatWindowTicks);
        }

        UUID aId = (inWindow && last != null ? last.attacker : null);

        if (aId != null) {
            // Victim loses a fraction of their own score.
            double loss = Math.max(0.0, vScore * clamp01(Core.gameConfig.deathPenaltyPercent));
            double aScore = Core.session.getScoreMap().getOrDefault(aId, 0.0);

            // Attacker gains a fraction of the victim's score.
            double gain = Math.max(0.0, vScore * clamp01(Core.gameConfig.killStealPercent));

            Core.session.getScoreMap().put(victimId, Math.max(0.0, vScore - loss));
            Core.session.getScoreMap().put(aId, Math.max(0.0, aScore + gain));

            Player a = Bukkit.getPlayer(aId);
            String aName = (a != null ? a.getName() : "Unknown");

            // Unified death line based on DamageKind + vanilla message (reason only).
            String deathLine = buildDeathLine(last, vanillaMsg, victimName, aName);

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
            // No attacker in our combat window -> environment / suicide / out-of-window damage.
            double loss = Math.max(0.0, vScore * clamp01(Core.gameConfig.nonPlayerDeathPenaltyPercent));
            Core.session.getScoreMap().put(victimId, Math.max(0.0, vScore - loss));

            String deathLine;

            if (last != null && last.kind == DamageKind.VANILLA && vanillaMsg != null && !vanillaMsg.isEmpty()) {
                // Use vanilla reason, but ALWAYS strip any attacker part.
                // Example: "X was doomed to fall by Y" -> "X was doomed to fall"
                deathLine = stripAttackerFromVanilla(vanillaMsg);
            } else {
                deathLine = "&a" + victimName + " &fdied";
            }

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

    /**
     * Build a death line based on DamageKind, weaponId and vanilla message.
     * For VANILLA:
     *   - vanilla message attacker part is always stripped first
     *   - then, if our combatWindow says attacker exists, we append "by <attacker>"
     */
    private String buildDeathLine(LastHit last,
                                  String vanillaMsg,
                                  String victimName,
                                  String attackerName) {
        if (last == null) {
            return "&a" + victimName + " &fwas killed by &a" + victimName;
        }

        DamageKind kind = (last.kind != null ? last.kind : DamageKind.OTHER);

        switch (kind) {
            case VANILLA: {
                // 1) strip attacker from vanilla so we only keep the reason
                String base;
                if (vanillaMsg != null && !vanillaMsg.isEmpty()) {
                    base = stripAttackerFromVanilla(vanillaMsg);
                } else {
                    base = "&a" + victimName + " &fdied";
                }

                // 2) we are in the "attacker exists" branch, so we always append attacker here
                if (attackerName != null && !attackerName.isEmpty()) {
                    return base + " &7by &a" + attackerName;
                }
                return base;
            }

            case PROJECTILE: {
                // Example: "Jonghyun was killed by Alice using Sniper Rifle"
                String weaponLabel = formatWeaponLabel(last.weaponId);
                return "&a" + victimName + " &fwas killed by &a" + attackerName
                        + (weaponLabel != null ? " &fusing &e" + weaponLabel : "");
            }

            case TNT:
                // TODO: custom TNT pipeline messaging
                return "&a" + victimName + " &fwas blown up by &a" + attackerName + " &7(TODO TNT message)";

            case POISON:
                // TODO: custom DoT engine messaging
                return "&a" + victimName + " &fsuccumbed to poison from &a" + attackerName + " &7(TODO poison message)";

            case MISSILE:
                // TODO: missile / RPG pipeline messaging
                return "&a" + victimName + " &fwas destroyed by &a" + attackerName + "'s missile &7(TODO missile message)";

            case OTHER:
            default:
                // TODO: refine messaging for OTHER types
                return "&a" + victimName + " &fwas killed by &a" + attackerName + " &7(TODO generic message)";
        }
    }

    // All vanilla substrings that introduce an attacker or second entity/item part
    private static final String[] VANILLA_ATTACKER_PATTERNS = {
            " by ",                                 // was slain by, was shot by, was blown up by, etc.
            " using ",                              // using <item>, using magic
            " whilst fighting ",
            " while fighting ",
            " whilst trying to escape ",
            " while trying to escape ",
            " to escape ",                          // tried to swim in lava to escape <player/mob>
            " due to ",                             // due to a firework fired from <item> by <player/mob>, danger zone due to <player/mob>
            " trying to hurt ",                     // killed trying to hurt <player/mob>
            " didn't want to live in the same world as ",
            " because of "                          // died because of <player/mob>
    };

    /**
     * Remove attacker-specific suffix from a vanilla death message.
     *
     */
    private String stripAttackerFromVanilla(String vanillaMsg) {
        if (vanillaMsg == null || vanillaMsg.isEmpty()) {
            return "&f";
        }

        String plain = org.bukkit.ChatColor.stripColor(vanillaMsg);

        int cut = -1;
        for (String pat : VANILLA_ATTACKER_PATTERNS) {
            int idx = plain.indexOf(pat);
            if (idx >= 0 && (cut == -1 || idx < cut)) {
                cut = idx;
            }
        }

        if (cut >= 0) {
            plain = plain.substring(0, cut);
        }

        return "&f" + plain;
    }

    /**
     * Lightweight weaponId → label formatter.
     * For now we just prettify the internal id (gz_rifle_basic → "Rifle Basic").
     * Later you can plug this into ItemRegistry or custom display-name resolver.
     */
    private String formatWeaponLabel(String weaponId) {
        if (weaponId == null || weaponId.isEmpty()) return null;

        String id = weaponId;

        // Optional prefix strip (e.g. "gz_")
        if (id.startsWith("gz_")) {
            id = id.substring(3);
        }

        // Replace separators with spaces: "rifle_basic-iron" → "rifle basic iron"
        id = id.replace('_', ' ').replace('-', ' ');

        // Capitalize each word
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
