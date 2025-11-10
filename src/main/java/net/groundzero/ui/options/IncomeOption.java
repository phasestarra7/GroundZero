package net.groundzero.ui.options;

import org.bukkit.Material;

/** Income multiplier choices: [multiplier, label, material, slot] */
public enum IncomeOption {
    X0_5(0.5, "×0.5", Material.RED_WOOL,   10),
    X1_0(1.0, "×1.0", Material.YELLOW_WOOL,12),
    X2_0(2.0, "×2.0", Material.LIME_WOOL,  14),
    X4_0(4.0, "×4.0", Material.BLUE_WOOL,  16);

    public final double multiplier;
    public final String label;
    public final Material material;
    public final int slot;

    IncomeOption(double multiplier, String label, Material material, int slot) {
        this.multiplier = multiplier;
        this.label = label;
        this.material = material;
        this.slot = slot;
    }
}
