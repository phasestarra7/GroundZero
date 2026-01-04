package net.groundzero.service.effect;

/**
 * Effect sources that can apply slowness/jump block to players.
 * Multiple sources can be active simultaneously - highest slowness wins.
 *
 * Duration types:
 * - Manual (endTick = 0): Toggle-based, removed explicitly
 * - Timed (endTick > 0): Auto-expires at specified tick
 */
public enum EffectSource {

    ASSAULT_ADS(2, true),      // Slowness 3 (amplifier 2), JumpBlock
    SNIPER_SCOPED(6, true),      // Slowness 7 (amplifier 6), JumpBlock
    STUN(6, true);       // Slowness 7 (amplifier 6), JumpBlock (Darkness is applied separately)

    public final int slownessAmplifier;  // 0-based (0 = level 1, 2 = level 3, etc.)
    public final boolean blocksJump;

    EffectSource(int slownessAmplifier, boolean blocksJump) {
        this.slownessAmplifier = slownessAmplifier;
        this.blocksJump = blocksJump;
    }
}