package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Visual, mouse-driven editors opened with /guiedit editor info|shop.
 *
 * Info editor: the real /info menu laid out as items. Drag items to reorder,
 * click an item in YOUR inventory to add it as a new entry (nothing is taken
 * from you), shift-click an entry (or drop it on the barrier) to delete it,
 * rename with an anvil. Closing the window saves.
 *
 * Shop editor: the shop catalog, paged. Left-click an item then type its new
 * price per stack in chat, shift-click removes it, click an item in your own
 * inventory to add it to the shop. Edits save instantly.
 */
public class GuiEditor implements Listener {

    private static final int SIZE = 54;
    private static final int ENTRY_SLOTS = 45;
    private static final int TRASH_SLOT = 53;
    private static final int HELP_SLOT = 49;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 47;

    private static final String INFO_TITLE = ChatColor.DARK_RED + "" + ChatColor.BOLD + "Edit /info - close to save";
    private static final String SHOP_TITLE = ChatColor.DARK_RED + "" + ChatColor.BOLD + "Edit /shop";

    private static final class InfoEditHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static final class ShopEditHolder implements InventoryHolder {
        final int page;
        ShopEditHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return null; }
    }

    private final Plugin plugin;
    /** Players we're waiting on for a typed price, and for which item. */
    private final Map<UUID, Material> awaitingPrice = new ConcurrentHashMap<>();

    public GuiEditor(Plugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Info editor
    // ------------------------------------------------------------------

    public void openInfo(Player player) {
        Inventory inv = Bukkit.createInventory(new InfoEditHolder(), SIZE, INFO_TITLE);
        int slot = 0;
        for (GuiConfig.InfoEntry entry : GuiConfig.get().info()) {
            if (slot >= ENTRY_SLOTS) break;
            ItemStack item = new ItemStack(entry.icon);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(GuiConfig.colorize(entry.name));
            List<String> lore = new ArrayList<>();
            for (String line : entry.lore) lore.add(GuiConfig.colorize(line));
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = ENTRY_SLOTS; i < SIZE; i++) inv.setItem(i, filler);
        inv.setItem(HELP_SLOT, named(Material.PAPER, ChatColor.YELLOW + "How this works",
                ChatColor.GRAY + "Drag entries to reorder them.",
                ChatColor.GRAY + "Click an item in YOUR inventory",
                ChatColor.GRAY + "to add it as a new entry.",
                ChatColor.GRAY + "Shift-click an entry to delete it.",
                ChatColor.GRAY + "Rename with an anvil (use & colors).",
                ChatColor.GRAY + "Lore: /guiedit info addlore <#> <text>",
                ChatColor.GREEN + "Close this window to save."));
        inv.setItem(TRASH_SLOT, named(Material.BARRIER, ChatColor.RED + "Trash",
                ChatColor.GRAY + "Drop an entry here to delete it."));
        player.openInventory(inv);
    }

    private void saveInfo(Inventory inv) {
        List<GuiConfig.InfoEntry> entries = GuiConfig.get().info();
        entries.clear();
        for (int i = 0; i < ENTRY_SLOTS; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            ItemMeta meta = item.getItemMeta();
            String name = meta != null && meta.hasDisplayName()
                    ? GuiConfig.toRaw(meta.getDisplayName()) : pretty(item.getType());
            List<String> lore = new ArrayList<>();
            if (meta != null && meta.hasLore() && meta.getLore() != null) {
                for (String line : meta.getLore()) lore.add(GuiConfig.toRaw(line));
            }
            entries.add(new GuiConfig.InfoEntry(item.getType(), name, lore));
        }
        GuiConfig.get().save();
    }

    // ------------------------------------------------------------------
    // Shop editor
    // ------------------------------------------------------------------

    public void openShop(Player player, int page) {
        List<Material> catalog = GuiConfig.get().shopCatalog();
        int pages = Math.max(1, (int) Math.ceil(catalog.size() / (double) ENTRY_SLOTS));
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = Bukkit.createInventory(new ShopEditHolder(page), SIZE,
                SHOP_TITLE + ChatColor.GRAY + " (" + (page + 1) + "/" + pages + ")");
        for (int i = 0; i < ENTRY_SLOTS; i++) {
            int idx = page * ENTRY_SLOTS + i;
            if (idx >= catalog.size()) break;
            Material m = catalog.get(idx);
            Long unit = GuiConfig.get().unitPrice(m);
            Long stack = GuiConfig.get().stackPrice(m);
            String price = unit != null
                    ? CoinFormat.formatWithExact(unit) + " each"
                    : CoinFormat.formatWithExact(stack != null ? stack : ShopGUI.PRICE_PER_STACK) + " per stack";
            inv.setItem(i, named(m, ChatColor.WHITE + pretty(m),
                    ChatColor.GRAY + "Price: " + ChatColor.GOLD + price,
                    "",
                    ChatColor.YELLOW + "Left-click: set price (type it in chat)",
                    ChatColor.YELLOW + "Right-click: reset to default price",
                    ChatColor.RED + "Shift-click: remove from shop"));
        }
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = ENTRY_SLOTS; i < SIZE; i++) inv.setItem(i, filler);
        if (page > 0) inv.setItem(PREV_SLOT, named(Material.ARROW, ChatColor.AQUA + "Previous page"));
        if (page < pages - 1) inv.setItem(NEXT_SLOT, named(Material.ARROW, ChatColor.AQUA + "Next page"));
        inv.setItem(HELP_SLOT, named(Material.PAPER, ChatColor.YELLOW + "How this works",
                ChatColor.GRAY + "Click an item in YOUR inventory",
                ChatColor.GRAY + "to add it to the shop.",
                ChatColor.GRAY + "Everything saves instantly."));
        player.openInventory(inv);
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        boolean info = holder instanceof InfoEditHolder;
        boolean shop = holder instanceof ShopEditHolder;
        if (!info && !shop) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ClickType click = event.getClick();
        // Anything that could smuggle items in/out of the window is blocked.
        if (click == ClickType.NUMBER_KEY || click == ClickType.DOUBLE_CLICK || click == ClickType.DROP
                || click == ClickType.CONTROL_DROP || click == ClickType.SWAP_OFFHAND
                || click == ClickType.CREATIVE || click == ClickType.UNKNOWN) {
            event.setCancelled(true);
            return;
        }

        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < SIZE;

        // Clicking your own inventory = "add this item".
        if (!inTop) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (raw < 0 || clicked == null || clicked.getType().isAir()) return;
            if (info) addInfoEntry(event.getView().getTopInventory(), clicked, player);
            else addShopItem(clicked.getType(), player, ((ShopEditHolder) holder).page);
            return;
        }

        if (shop) {
            event.setCancelled(true);
            handleShopClick(event, player, ((ShopEditHolder) holder).page);
            return;
        }

        // Info editor, top inventory.
        if (raw >= ENTRY_SLOTS) {
            event.setCancelled(true);
            if (raw == TRASH_SLOT && event.getCursor() != null && !event.getCursor().getType().isAir()) {
                player.setItemOnCursor(null); // dropped on the barrier = deleted
            }
            return;
        }
        if (click.isShiftClick()) {
            event.setCancelled(true);
            event.getView().getTopInventory().setItem(raw, null); // shift-click deletes
            return;
        }
        // Plain left/right pickup, place and swap are left alone - that's the drag-to-reorder.
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ShopEditHolder) {
            event.setCancelled(true);
            return;
        }
        if (!(holder instanceof InfoEditHolder)) return;
        for (int raw : event.getRawSlots()) {
            if (raw >= ENTRY_SLOTS) { // footer row or the player's own inventory
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof InfoEditHolder) {
            if (event.getPlayer() instanceof Player player) player.setItemOnCursor(null);
            saveInfo(event.getInventory());
            event.getPlayer().sendMessage(ChatColor.GREEN + "/info menu saved ("
                    + GuiConfig.get().info().size() + " entries). Use /info to see it.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaitingPrice.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Material material = awaitingPrice.remove(player.getUniqueId());
        if (material == null) return;
        event.setCancelled(true);
        String text = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.GRAY + "Price change cancelled.");
            } else {
                try {
                    long price = Long.parseLong(text.replace(",", ""));
                    if (price <= 0) throw new NumberFormatException();
                    GuiConfig.get().setStackPrice(material, price);
                    player.sendMessage(ChatColor.GREEN + pretty(material) + " now costs "
                            + CoinFormat.formatWithExact(price) + " per stack of 64.");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "That's not a valid price - nothing changed.");
                }
            }
            openShop(player, pageOf(material));
        });
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void addInfoEntry(Inventory inv, ItemStack source, Player player) {
        for (int i = 0; i < ENTRY_SLOTS; i++) {
            ItemStack existing = inv.getItem(i);
            if (existing == null || existing.getType().isAir()) {
                ItemMeta srcMeta = source.getItemMeta();
                ItemStack item = new ItemStack(source.getType());
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(srcMeta != null && srcMeta.hasDisplayName()
                        ? srcMeta.getDisplayName() : pretty(source.getType()));
                if (srcMeta != null && srcMeta.hasLore() && srcMeta.getLore() != null) meta.setLore(srcMeta.getLore());
                item.setItemMeta(meta);
                inv.setItem(i, item);
                return;
            }
        }
        player.sendMessage(ChatColor.RED + "The menu is full (" + ENTRY_SLOTS + " entries).");
    }

    private void addShopItem(Material material, Player player, int page) {
        if (GuiConfig.get().shopContains(material)) {
            player.sendMessage(ChatColor.RED + pretty(material) + " is already in the shop.");
            return;
        }
        GuiConfig.get().shopAdd(material);
        player.sendMessage(ChatColor.GREEN + "Added " + pretty(material) + " to the shop.");
        openShop(player, pageOf(material));
    }

    private void handleShopClick(InventoryClickEvent event, Player player, int page) {
        int raw = event.getRawSlot();
        if (raw == PREV_SLOT && page > 0) { openShop(player, page - 1); return; }
        if (raw == NEXT_SLOT) { openShop(player, page + 1); return; }
        if (raw >= ENTRY_SLOTS) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) return;
        Material material = item.getType();
        if (event.getClick().isShiftClick()) {
            GuiConfig.get().shopRemove(material);
            player.sendMessage(ChatColor.GRAY + "Removed " + pretty(material) + " from the shop.");
            openShop(player, page);
        } else if (event.getClick().isRightClick()) {
            GuiConfig.get().resetPrice(material);
            player.sendMessage(ChatColor.GRAY + pretty(material) + " is back to its default price.");
            openShop(player, page);
        } else {
            awaitingPrice.put(player.getUniqueId(), material);
            player.closeInventory();
            player.sendMessage(ChatColor.AQUA + "Type the new price per stack of 64 for "
                    + ChatColor.WHITE + pretty(material) + ChatColor.AQUA + " in chat (or type cancel).");
        }
    }

    private int pageOf(Material material) {
        int idx = GuiConfig.get().shopCatalog().indexOf(material);
        return idx < 0 ? 0 : idx / ENTRY_SLOTS;
    }

    private static ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static String pretty(Material material) {
        StringBuilder out = new StringBuilder();
        for (String word : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
