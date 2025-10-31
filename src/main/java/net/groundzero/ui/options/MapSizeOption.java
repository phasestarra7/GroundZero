package net.groundzero.ui.options;

import org.bukkit.Material;

/** Map size choices: [size, label, material, slot] */
public enum MapSizeOption {
    SIZE_50( 50,  "50×50",   Material.RED_WOOL,   10),
    SIZE_100(100, "100×100", Material.YELLOW_WOOL,12),
    SIZE_200(200, "200×200", Material.LIME_WOOL,  14),
    SIZE_400(400, "400×400", Material.BLUE_WOOL,  16);

    public final int size;
    public final String label;
    public final Material material;
    public final int slot;

    MapSizeOption(int size, String label, Material material, int slot) {
        this.size = size;
        this.label = label;
        this.material = material;
        this.slot = slot;
    }
}
