package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The /info menu: a GUI summarizing every player-facing command, shown
 * automatically a moment after a player joins and reopenable any time with
 * /info. Closing it is a single clear "OK" button rather than relying on
 * the inventory's corner X, since that's what was asked for.
 */
public class InfoGUI implements Listener {

    private static final String TITLE = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Penguin Mafia - How To Play";
    private static final int SIZE = 54;
    private static final int CLOSE_SLOT = 49;

    /** Marks an inventory as this menu, so the click listener can recognize it without comparing titles. */
    public static class InfoHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null; // never used; Bukkit only needs the holder identity here
        }
    }

    private static GuiConfig.InfoEntry entry(Material icon, String name, String... lore) {
        List<String> raw = new ArrayList<>();
        for (String line : lore) raw.add(GuiConfig.toRaw(line));
        return new GuiConfig.InfoEntry(icon, name, raw);
    }

    /** The built-in menu, used to seed gui_config.yml the first time and by "/guiedit info reset". */
    public static List<GuiConfig.InfoEntry> defaultEntries() {
        List<GuiConfig.InfoEntry> entries = new ArrayList<>();
        entries.add(entry(Material.CHEST, "/bm",
                ChatColor.GRAY + "Open the Black Market GUI -",
                ChatColor.GRAY + "search, sort, and filter by category.",
                ChatColor.GRAY + "sell <price>, list, cancel <id>,",
                ChatColor.GRAY + "balance, deposit, withdraw <amount>"));
        entries.add(entry(Material.GOLD_INGOT, "/bal [player]",
                ChatColor.GRAY + "Check your (or another player's)",
                ChatColor.GRAY + "Frozen Coin balance."));
        entries.add(entry(Material.NETHER_STAR, "/baltop (/bt)",
                ChatColor.GRAY + "See who has the most Frozen Coins."));
        entries.add(entry(Material.PAPER, "/pay <player> <amount>",
                ChatColor.GRAY + "Send Frozen Coins to another player."));
        entries.add(entry(Material.GOLD_NUGGET, "/coinflip <amount>",
                ChatColor.GRAY + "Bet coins on a 50/50 flip.",
                ChatColor.GRAY + "Win doubles it, lose forfeits it."));
        entries.add(entry(Material.EMERALD, "/shop",
                ChatColor.GRAY + "Opens a paged shop GUI - 1,000 coins",
                ChatColor.GRAY + "per stack. Hover for price, left-click",
                ChatColor.GRAY + "for a stack, right-click for more."));
        entries.add(entry(Material.HOPPER, "/autodeposit (/ad)",
                ChatColor.GRAY + "Toggle auto-depositing Frozen Coins",
                ChatColor.GRAY + "you pick up straight into your balance.",
                ChatColor.GRAY + "Blocks /bm withdraw while it's on."));
        entries.add(entry(Material.BOOK, "/chat",
                ChatColor.GRAY + "Toggle the balance-update chat",
                ChatColor.GRAY + "messages from Auto-Deposit/AFK Farm."));
        entries.add(entry(Material.BLUE_ICE, "/frozenrealm (/fr)",
                ChatColor.GRAY + "Teleport to the Frozen Realm dimension.",
                ChatColor.GRAY + "/frozenrealm back returns you home."));
        entries.add(entry(Material.OAK_SIGN, "/pa",
                ChatColor.GRAY + "Manage the looping announcement",
                ChatColor.GRAY + "broadcast (add, remove, list, on/off)."));
        entries.add(entry(Material.CLOCK, "/ptr",
                ChatColor.GRAY + "Manage passive playtime rewards -",
                ChatColor.GRAY + "a periodic coin payout for being online."));
        entries.add(entry(Material.KNOWLEDGE_BOOK, "/info",
                ChatColor.GRAY + "Reopens this menu any time."));
        return entries;
    }

    private final PenguinMafiaMarket plugin;

    public InfoGUI(PenguinMafiaMarket plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new InfoHolder(), SIZE, TITLE);

        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, filler);
        }

        int slot = 0;
        for (GuiConfig.InfoEntry entry : GuiConfig.get().info()) {
            if (slot >= SIZE - 9) break; // leave the bottom row free for the close button
            ItemStack item = namedItem(entry.icon, ChatColor.AQUA + "" + ChatColor.BOLD + GuiConfig.colorize(entry.name));
            ItemMeta meta = item.getItemMeta();
            List<String> lore = new ArrayList<>();
            for (String line : entry.lore) lore.add(GuiConfig.colorize(line));
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
        }

        ItemStack close = namedItem(Material.LIME_STAINED_GLASS_PANE,
                ChatColor.GREEN + "" + ChatColor.BOLD + "OK - Close This Menu");
        inv.setItem(CLOSE_SLOT, close);

        player.openInventory(inv);
    }

    private static ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) open(player);
        }, 30L); // half a second after join, so it doesn't fight the client's own join transition
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof InfoHolder)) return;

        // View/click-only menu - never let items be taken from it or moved in from the player's own inventory.
        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof InfoHolder)) {
            return;
        }

        if (event.getRawSlot() == CLOSE_SLOT) {
            HumanEntity who = event.getWhoClicked();
            who.closeInventory();
        }
    }
}
