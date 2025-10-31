package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.ui.MenuType;
import net.groundzero.ui.holder.GroundZeroMenuHolder;
import net.groundzero.ui.options.GameModeOption;
import net.groundzero.ui.options.IncomeOption;
import net.groundzero.ui.options.MapSizeOption;
import net.groundzero.util.Notifier;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.bukkit.Bukkit.createInventory;

public final class GuiService {

    private Inventory invMapSize;
    private Inventory invIncome;
    private Inventory invGameMode;
    private Inventory invShop;

    public GuiService() {}

    /* ----------------------- BUILD ----------------------- */

    public void newMapSize() {
        invMapSize = buildMapSize();
    }

    public void newIncome() {
        invIncome = buildIncome();
    }

    public void newGameMode() {
        invGameMode = buildGameMode();
    }

    private Inventory buildMapSize() {
        Inventory inv = createInventory(new GroundZeroMenuHolder(net.groundzero.ui.MenuType.MAP_SIZE),
                27, "Choose Map Size");
        for (MapSizeOption opt : MapSizeOption.values()) {
            inv.setItem(opt.slot, item(opt.material, "§b" + opt.label, votesLore(opt.label, 0)));
        }
        inv.setItem(26, cancelItem());
        return inv;
    }

    private Inventory buildIncome() {
        Inventory inv = createInventory(new GroundZeroMenuHolder(net.groundzero.ui.MenuType.INCOME_MULTIPLIER),
                27, "Choose Income Multiplier");
        for (IncomeOption opt : IncomeOption.values()) {
            inv.setItem(opt.slot, item(opt.material, "§b" + opt.label, votesLore(opt.label, 0)));
        }
        inv.setItem(26, cancelItem());
        return inv;
    }

    private Inventory buildGameMode() {
        Inventory inv = createInventory(new GroundZeroMenuHolder(net.groundzero.ui.MenuType.GAME_MODE),
                27, "Choose Game Mode");
        for (GameModeOption opt : GameModeOption.values()) {
            inv.setItem(opt.slot, item(opt.material, "§b" + opt.label, votesLore(opt.label, 0)));
        }
        inv.setItem(26, cancelItem());
        return inv;
    }

    private Inventory buildShop() {
        Inventory inv = createInventory(new GroundZeroMenuHolder(MenuType.SHOP),
                54, "Call Support");
        return inv;
    }

    /* ----------------------- OPEN ----------------------- */

    public void openMapSize(Player p) {
        if (invMapSize == null) invMapSize = buildMapSize();
        p.openInventory(invMapSize);
    }
    public void openIncome(Player p) {
        if (invIncome == null) invIncome = buildIncome();
        p.openInventory(invIncome);
    }
    public void openGameMode(Player p) {
        if (invGameMode == null) invGameMode = buildGameMode();
        p.openInventory(invGameMode);
    }
    public void openShop(Player p) {
        if (invShop == null) invShop = buildShop();
        p.openInventory(invShop);
    }

    /* ----------------------- REFRESH (vote bars) ----------------------- */

    public void refreshMapSizeVotes(int size50, int size100, int size200, int size400) {
        if (invMapSize == null) return;
        setVotes(invMapSize, MapSizeOption.SIZE_50.slot,  MapSizeOption.SIZE_50.label,  size50);
        setVotes(invMapSize, MapSizeOption.SIZE_100.slot, MapSizeOption.SIZE_100.label, size100);
        setVotes(invMapSize, MapSizeOption.SIZE_200.slot, MapSizeOption.SIZE_200.label, size200);
        setVotes(invMapSize, MapSizeOption.SIZE_400.slot, MapSizeOption.SIZE_400.label, size400);
    }

    public void refreshIncomeVotes(int x05, int x10, int x20, int x40) {
        if (invIncome == null) return;
        setVotes(invIncome, IncomeOption.X0_5.slot, IncomeOption.X0_5.label, x05);
        setVotes(invIncome, IncomeOption.X1_0.slot, IncomeOption.X1_0.label, x10);
        setVotes(invIncome, IncomeOption.X2_0.slot, IncomeOption.X2_0.label, x20);
        setVotes(invIncome, IncomeOption.X4_0.slot, IncomeOption.X4_0.label, x40);
    }

    public void refreshGameModeVotes(int standard /* add more later */) {
        if (invGameMode == null) return;
        setVotes(invGameMode, GameModeOption.STANDARD.slot, GameModeOption.STANDARD.label, standard);
    }

    /* ----------------------- RETAIN ONLY (ties) ----------------------- */

    /** 동률 아닌 슬롯은 전부 null로 (빈 칸) */
    public void retainOnlyMapSize(List<MapSizeOption> keep) {
        if (invMapSize == null) return;
        Set<Integer> keepSlots = keep.stream().map(o -> o.slot).collect(Collectors.toSet());
        for (MapSizeOption opt : MapSizeOption.values()) {
            if (!keepSlots.contains(opt.slot)) invMapSize.setItem(opt.slot, null);
        }
        invMapSize.setItem(26, cancelItem()); // barrier 유지
        Core.notify.soundToParticipants(Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
    }

    public void retainOnlyIncome(List<IncomeOption> keep) {
        if (invIncome == null) return;
        Set<Integer> keepSlots = keep.stream().map(o -> o.slot).collect(Collectors.toSet());
        for (IncomeOption opt : IncomeOption.values()) {
            if (!keepSlots.contains(opt.slot)) invIncome.setItem(opt.slot, null);
        }
        invIncome.setItem(26, cancelItem());
        Core.notify.soundToParticipants(Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
    }

    public void retainOnlyGameMode(List<GameModeOption> keep) {
        if (invGameMode == null) return;
        Set<Integer> keepSlots = keep.stream().map(o -> o.slot).collect(Collectors.toSet());
        for (GameModeOption opt : GameModeOption.values()) {
            if (!keepSlots.contains(opt.slot)) invGameMode.setItem(opt.slot, null);
        }
        invGameMode.setItem(26, cancelItem());
        Core.notify.soundToParticipants(Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
    }

    /* ----------------------- HIGHLIGHT (selected) ----------------------- */

    public void highlightMapSizeSelected(String label, int slot) {
        if (invMapSize == null) return;

        for (MapSizeOption opt : MapSizeOption.values()) {
            if (opt.slot != slot) invMapSize.setItem(opt.slot, null);
        }
        invMapSize.setItem(26, cancelItem());
        highlightOption(invMapSize, slot, "§d" + label);
    }

    public void highlightIncomeSelected(String label, int slot) {
        if (invIncome == null) return;

        for (MapSizeOption opt : MapSizeOption.values()) {
            if (opt.slot != slot) invIncome.setItem(opt.slot, null);
        }
        invIncome.setItem(26, cancelItem());
        highlightOption(invIncome, slot, "§d" + label);
    }

    public void highlightGameModeSelected(String label, int slot) {
        if (invGameMode == null) return;

        for (MapSizeOption opt : MapSizeOption.values()) {
            if (opt.slot != slot) invGameMode.setItem(opt.slot, null);
        }
        invGameMode.setItem(26, cancelItem());
        highlightOption(invGameMode, slot, "§d" + label);
    }

    private void highlightOption(Inventory inv, int slot, String name) {
        ItemStack it = inv.getItem(slot);
        if (it == null) return;

        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);

        inv.setItem(slot, it);
    }

    /* ----------------------- Close & Reset ----------------------- */

    public void closeALlGZViews() {
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            var top = p.getOpenInventory().getTopInventory();
            if (top == null) continue;

            var h = top.getHolder();
            if (h instanceof GroundZeroMenuHolder) {
                p.closeInventory();
            }
        }
    }

    /* ----------------------- Helpers ----------------------- */

    private ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    /** "Click to vote <label>" + "Votes : - (0)" / "Votes : ■ ■ (2)" */
    private List<String> votesLore(String label, int count) {
        String click = "§fClick to vote §b" + label;
        if (count <= 0) {
            return Arrays.asList("",
                    click,
                    "§fVotes : §a- §f(§e0§f)");
        }
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

    private void setVotes(Inventory inv, int slot, String label, int count) {
        ItemStack it = inv.getItem(slot);
        if (it == null) return;
        ItemMeta meta = it.getItemMeta();
        meta.setLore(votesLore(label, count));
        it.setItemMeta(meta);
    }

    private ItemStack cancelItem() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§cCancel (Back to IDLE)");
        m.setLore(Arrays.asList("",
                "§cCAUTION §f: This cancels the whole voting process"));
        it.setItemMeta(m);
        return it;
    }
}
