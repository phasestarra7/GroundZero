package net.groundzero.util;

import net.groundzero.app.Core;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Unified messaging & sound helper.
 *
 * Player messages (msg/msgError) keep NOTE_BLOCK_BELL with fixed pitches.
 * Broadcasts (participants / all) take (Sound, PitchLevel) from caller.
 */
public final class Notifier {

    /* ---------- constants ---------- */
    public static final float PITCH_MSG_OK  = 1.1892f;
    public static final float PITCH_MSG_ERR = 0.8909f;

    public enum PitchLevel {
        HIGH(1.2599f), MID(1.0000f), LOW(0.7937f);
        public final float v;
        PitchLevel(float v) { this.v = v; }
    }

    private static final char CC = '§';
    private static final String PFX_MSG_OK  = "&7GroundZero &f| &f";
    private static final String PFX_MSG_ERR = "&7GroundZero &f| &c";
    private static final String PFX_BC_OK   = "&bGroundZero &f| &f";
    private static final String PFX_BC_ERR  = "&bGroundZero &f| &c";

    private String c(String s) { return s == null ? "" : s.replace('&', CC); }

    /* ---------- player message (single / varargs) ---------- */
    public void message(Player p, String line) {
        if (p == null || line == null) return;
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, PITCH_MSG_OK);
        p.sendMessage(c(PFX_MSG_OK + line));
    }
    public void message(Player p, String... lines) {
        if (p == null || lines == null || lines.length == 0) return;
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, PITCH_MSG_OK);
        for (String s : lines) p.sendMessage(c(PFX_MSG_OK + (s == null ? "" : s)));
    }
    public void messageError(Player p, String line) {
        if (p == null || line == null) return;
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, PITCH_MSG_ERR);
        p.sendMessage(c(PFX_MSG_ERR + line));
    }
    public void messageError(Player p, String... lines) {
        if (p == null || lines == null || lines.length == 0) return;
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, PITCH_MSG_ERR);
        for (String s : lines) p.sendMessage(c(PFX_MSG_ERR + (s == null ? "" : s)));
    }

    // normal: single line
    public void broadcast(Sound sound, PitchLevel pitch, String line) {
        if (line == null) return;

        boolean playSound = !(sound == null && pitch == null);

        Sound s = (sound == null ? Sound.BLOCK_NOTE_BLOCK_PLING : sound);
        float pv = (pitch == null ? PitchLevel.MID.v : pitch.v);

        Core.game.forEachParticipant(p -> {
            if (playSound) p.playSound(p.getLocation(), s, 1.0f, pv);
            p.sendMessage(c(PFX_BC_OK + line));
        });
    }

    // normal: multi-line
    public void broadcast(Sound sound, PitchLevel pitch, String... lines) {
        if (lines == null || lines.length == 0) return;

        boolean playSound = !(sound == null && pitch == null);

        Sound s = (sound == null ? Sound.BLOCK_NOTE_BLOCK_PLING : sound);
        float pv = (pitch == null ? PitchLevel.MID.v : pitch.v);

        Core.game.forEachParticipant(p -> {
            if (playSound) p.playSound(p.getLocation(), s, 1.0f, pv);
            for (String line : lines)
                p.sendMessage(c(PFX_BC_OK + (line == null ? "" : line)));
        });
    }

    // error: single line
    public void broadcastError(Sound sound, PitchLevel pitch, String line) {
        if (line == null) return;

        boolean playSound = !(sound == null && pitch == null);

        Sound s = (sound == null ? Sound.BLOCK_NOTE_BLOCK_PLING : sound);
        float pv = (pitch == null ? PitchLevel.MID.v : pitch.v);

        Core.game.forEachParticipant(p -> {
            if (playSound) p.playSound(p.getLocation(), s, 1.0f, pv);
            p.sendMessage(c(PFX_BC_ERR + line));
        });
    }

    // error: multi-line
    public void broadcastError(Sound sound, PitchLevel pitch, String... lines) {
        if (lines == null || lines.length == 0) return;

        boolean playSound = !(sound == null && pitch == null);

        Sound s = (sound == null ? Sound.BLOCK_NOTE_BLOCK_PLING : sound);
        float pv = (pitch == null ? PitchLevel.MID.v : pitch.v);

        Core.game.forEachParticipant(p -> {
            if (playSound) p.playSound(p.getLocation(), s, 1.0f, pv);
            for (String line : lines)
                p.sendMessage(c(PFX_BC_ERR + (line == null ? "" : line)));
        });
    }

/* =================================================
     ALL ONLINE BROADCAST
   ================================================= */

    // normal: single line
    public void broadcastToAll(Sound sound, PitchLevel pitch, String line) {
        if (line == null) return;

        boolean playSound = !(sound == null && pitch == null);

        Sound s = (sound == null ? Sound.BLOCK_NOTE_BLOCK_PLING : sound);
        float pv = (pitch == null ? PitchLevel.MID.v : pitch.v);

        Core.game.forAll(p -> {
            if (playSound) p.playSound(p.getLocation(), s, 1.0f, pv);
            p.sendMessage(c(PFX_BC_OK + line));
        });
    }

    // normal: multi-line
    public void broadcastToAll(Sound sound, PitchLevel pitch, String... lines) {
        if (lines == null || lines.length == 0) return;

        boolean playSound = !(sound == null && pitch == null);

        Sound s = (sound == null ? Sound.BLOCK_NOTE_BLOCK_PLING : sound);
        float pv = (pitch == null ? PitchLevel.MID.v : pitch.v);

        Core.game.forAll(p -> {
            if (playSound) p.playSound(p.getLocation(), s, 1.0f, pv);
            for (String line : lines)
                p.sendMessage(c(PFX_BC_OK + (line == null ? "" : line)));
        });
    }

    // error: single line
    public void broadcastToAllError(Sound sound, PitchLevel pitch, String line) {
        if (line == null) return;

        boolean playSound = !(sound == null && pitch == null);

        Sound s = (sound == null ? Sound.BLOCK_NOTE_BLOCK_PLING : sound);
        float pv = (pitch == null ? PitchLevel.MID.v : pitch.v);

        Core.game.forAll(p -> {
            if (playSound) p.playSound(p.getLocation(), s, 1.0f, pv);
            p.sendMessage(c(PFX_BC_ERR + line));
        });
    }

    // error: multi-line
    public void broadcastToAllError(Sound sound, PitchLevel pitch, String... lines) {
        if (lines == null || lines.length == 0) return;

        boolean playSound = !(sound == null && pitch == null);

        Sound s = (sound == null ? Sound.BLOCK_NOTE_BLOCK_PLING : sound);
        float pv = (pitch == null ? PitchLevel.MID.v : pitch.v);

        Core.game.forAll(p -> {
            if (playSound) p.playSound(p.getLocation(), s, 1.0f, pv);
            for (String line : lines)
                p.sendMessage(c(PFX_BC_ERR + (line == null ? "" : line)));
        });
    }
}
