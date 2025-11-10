package net.groundzero.ui.options;

import org.bukkit.Material;

/** Game modes: [index, label, material, slot] */
public enum GameModeOption {
    STANDARD(0, "STANDARD", Material.GRASS_BLOCK, 13);
    // Add more: HARDCORE(...), SNIPER_ONLY(...), etc.

    public final int index;
    public final String label;
    public final Material material;
    public final int slot;

    GameModeOption(int index, String label, Material material, int slot) {
        this.index = index;
        this.label = label;
        this.material = material;
        this.slot = slot;
    }
}
