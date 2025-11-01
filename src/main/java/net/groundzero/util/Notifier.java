package net.groundzero.util;

import net.groundzero.app.Core;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Unified messaging & sound helper.
 *
 * After small refactor:
 * - We do NOT call Core.game.forEachParticipant()/forAll() here anymore.
 * - Instead we loop over:
 *      * Core.game.session().getParticipantsView()
 *      * Bukkit.getOnlinePlayers()
 *   passed in by the caller.
 * - Old style broadcastToAll(...) / broadcast(...) are kept but now delegate
 *   to the new iterable-based methods so other files don't break.
 */
public final class Notifier {

    public enum PitchLevel {
        HIGH(1.2599f),
        MID(1.0000f),
        LOW(0.7937f),
        OK(1.1892f),
        ERR(0.8909f);

        public final float v;
        PitchLevel(float v) { this.v = v; }
        public float get() { return v; }
    }

    private static final String PFX_MSG_OK = "&7GroundZero &f| &f";
    private static final String PFX_MSG_ERR = "&7GroundZero &f| &c";
    private static final String PFX_BC_OK  = "&bGroundZero &f| &f";
    private static final String PFX_BC_ERR = "&bGroundZero &f| &c";

    private String c(String s) {
        return s == null ? "" : s.replace('&', '§');
    }

    /* ----------------------- player message (keep varargs) ----------------------- */

    public void message(Player p, String... lines) {
        if (p == null || lines == null || lines.length == 0) return;
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, PitchLevel.OK.v);
        for (String s : lines) {
            p.sendMessage(c(PFX_MSG_OK + (s == null ? "" : s)));
        }
    }

    public void messageError(Player p, String... lines) {
        if (p == null || lines == null || lines.length == 0) return;
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, PitchLevel.ERR.v);
        for (String s : lines) {
            p.sendMessage(c(PFX_MSG_ERR + (s == null ? "" : s)));
        }
    }

    public void message(CommandSender sender, String... lines) {
        if (sender == null || lines == null || lines.length == 0) return;
        for (String s : lines) {
            sender.sendMessage(c(PFX_MSG_OK + (s == null ? "" : s)));
        }
    }

    public void messageError(CommandSender sender, String... lines) {
        if (sender == null || lines == null || lines.length == 0) return;
        for (String s : lines) {
            sender.sendMessage(c(PFX_MSG_ERR + (s == null ? "" : s)));
        }
    }

    /* ===================== NEW: iterable-based core ===================== */

    /**
     * Core broadcast used by everything else.
     */
    public void broadcast(Iterable<?> targets,
                          Sound sound,
                          PitchLevel pitch,
                          boolean isError,
                          String... lines) {
        if (targets == null || lines == null || lines.length == 0) return;

        final boolean playSound = !(sound == null && pitch == null);
        final Sound snd = (sound == null ? Sound.BLOCK_NOTE_BLOCK_PLING : sound);
        final float pv = (pitch == null ? PitchLevel.MID.v : pitch.v);
        final String pfx = isError ? PFX_BC_ERR : PFX_BC_OK;

        for (Object o : targets) {
            Player p = asPlayer(o);
            if (p == null) continue;

            if (playSound) {
                p.playSound(p.getLocation(), snd, 1.0f, pv);
            }
            for (String line : lines) {
                p.sendMessage(c(pfx + (line == null ? "" : line)));
            }
        }
    }

    /**
     * Sound-only broadcast (this가 네가 말한 "sound도 브로드캐스팅처럼" 그거)
     */
    public void broadcastSound(Iterable<?> targets,
                               Sound sound,
                               PitchLevel pitch) {
        if (targets == null || sound == null || pitch == null) return;
        for (Object o : targets) {
            Player p = asPlayer(o);
            if (p == null) continue;
            p.playSound(p.getLocation(), sound, 1.0f, pitch.get());
        }
    }

    private Player asPlayer(Object o) {
        if (o instanceof Player p) {
            return (p.isOnline() ? p : null);
        }
        if (o instanceof UUID id) {
            Player p = Bukkit.getPlayer(id);
            return (p != null && p.isOnline()) ? p : null;
        }
        return null;
    }

    /* ===================== SOUND HELPERS (participants / all) ===================== */

    public void sound(Player p, Sound sound, PitchLevel pitch) {
        if (p == null) return;
        p.playSound(p.getLocation(), sound, 1.0f, pitch.get());
    }

    public void sound(UUID id, Sound sound, PitchLevel pitch) {
        Player p = Bukkit.getPlayer(id);
        if (p != null && p.isOnline()) {
            sound(p, sound, pitch);
        }
    }

    /**
     * was: soundToParticipants(...) but now we do not call GameManager.
     */
    public void soundToParticipants(Sound sound, PitchLevel pitch) {
        broadcastSound(Core.game.session().getParticipantsView(), sound, pitch);
    }

    public void soundToAll(Sound sound, PitchLevel pitch) {
        broadcastSound(Bukkit.getOnlinePlayers(), sound, pitch);
    }
}
