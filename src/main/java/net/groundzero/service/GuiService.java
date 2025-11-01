package net.groundzero.service;

import net.groundzero.ui.MenuType;
import net.groundzero.ui.holder.GroundZeroMenuHolder;
import net.groundzero.ui.options.GameModeOption;
import net.groundzero.ui.options.IncomeOption;
import net.groundzero.ui.options.MapSizeOption;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

import static org.bukkit.Bukkit.createInventory;

/**
 * GUI build/open/close only.
 * Voting-specific logic is handled in VoteService.
 */
public final class GuiService {

    private Inventory invMapSize;
    private Inventory invIncome;
    private Inventory invGameMode;
    private Inventory invShop;

    public GuiService() {}

    /* ----------------------- BUILDERS ----------------------- */

    public void newMapSize() {
        invMapSize = buildMapSize();
    }

    public void newIncome() {
        invIncome = buildIncome();
    }

    public void newGameMode() {
        invGameMode = buildGameMode();
    }

    public void newShop() {
        invShop = buildShop();
    }

    private Inventory buildMapSize() {
        Inventory inv = createInventory(
                new GroundZeroMenuHolder(MenuType.MAP_SIZE),
                27,
                "Choose Map Size"
        );

        for (MapSizeOption opt : MapSizeOption.values()) {
            inv.setItem(
                    opt.slot,
                    item(opt.material, "§b" + opt.label, votesLore(opt.label, 0))
            );
        }

        inv.setItem(26, cancelItem());
        return inv;
    }

    private Inventory buildIncome() {
        Inventory inv = createInventory(
                new GroundZeroMenuHolder(MenuType.INCOME_MULTIPLIER),
                27,
                "Choose Income Multiplier"
        );

        for (IncomeOption opt : IncomeOption.values()) {
            inv.setItem(
                    opt.slot,
                    item(opt.material, "§b" + opt.label, votesLore(opt.label, 0))
            );
        }

        inv.setItem(26, cancelItem());
        return inv;
    }

    private Inventory buildGameMode() {
        Inventory inv = createInventory(
                new GroundZeroMenuHolder(MenuType.GAME_MODE),
                27,
                "Choose Game Mode"
        );

        for (GameModeOption opt : GameModeOption.values()) {
            inv.setItem(
                    opt.slot,
                    item(opt.material, "§b" + opt.label, votesLore(opt.label, 0))
            );
        }

        inv.setItem(26, cancelItem());
        return inv;
    }

    private Inventory buildShop() {
        return createInventory(
                new GroundZeroMenuHolder(MenuType.SHOP),
                54,
                "Call Support"
        );
    }

    /* ----------------------- GETTERS (for VoteService) ----------------------- */

    /** Returns current MapSize inventory (creates if null). */
    public Inventory getMapSizeInventory() {
        if (invMapSize == null) {
            invMapSize = buildMapSize();
        }
        return invMapSize;
    }

    /** Returns current Income inventory (creates if null). */
    public Inventory getIncomeInventory() {
        if (invIncome == null) {
            invIncome = buildIncome();
        }
        return invIncome;
    }

    /** Returns current GameMode inventory (creates if null). */
    public Inventory getGameModeInventory() {
        if (invGameMode == null) {
            invGameMode = buildGameMode();
        }
        return invGameMode;
    }

    /** Returns current Shop inventory (creates if null). */
    public Inventory getShopInventory() {
        if (invShop == null) {
            invShop = buildShop();
        }
        return invShop;
    }

    /* ----------------------- OPENERS ----------------------- */

    public void openMapSize(Player p) {
        p.openInventory(getMapSizeInventory());
    }

    public void openIncome(Player p) {
        p.openInventory(getIncomeInventory());
    }

    public void openGameMode(Player p) {
        p.openInventory(getGameModeInventory());
    }

    public void openShop(Player p) {
        p.openInventory(getShopInventory());
    }

    /* ----------------------- CLOSE ALL GZ VIEWS ----------------------- */

    public void closeAllGZViews() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory() == null) continue;
            if (p.getOpenInventory().getTopInventory() == null) continue;
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof GroundZeroMenuHolder) {
                p.closeInventory();
            }
        }
    }

    /* ----------------------- HELPERS ----------------------- */

    private ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    // still used to build initial GUI (0 votes)
    private List<String> votesLore(String label, int count) {
        String click = "§fClick to vote §b" + label;
        if (count <= 0) {
            return Arrays.asList(
                    "",
                    click,
                    "§fVotes : §a- §f(§e0§f)"
            );
        }
        // (we may never reach here from builder, but keep for completeness)
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) bar.append(' ');
            bar.append('■');
        }
        return Arrays.asList(
                "",
                click,
                "§fVotes : §a" + bar + " §f(§e" + count + "§f)"
        );
    }

    private ItemStack cancelItem() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§cClose");
        meta.setLore(Arrays.asList(
                "",
                "§cCAUTION §f: This cancels the whole voting process"
        ));
        it.setItemMeta(meta);
        return it;
    }
}
