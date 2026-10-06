package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /guiedit - edit the /info menu and the /shop catalog/prices from in game,
 * no jar rebuild or file editing needed. Every change saves to
 * gui_config.yml immediately and shows up the next time anyone opens the
 * menu. Gated by the penguinmafia.guiedit permission (ops have it by
 * default; LuckPerms can grant it to a staff rank).
 */
public class GuiEditCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "penguinmafia.guiedit";

    private final InfoGUI infoGUI;
    private final ShopGUI shopGUI;
    private final GuiEditor editor;

    public GuiEditCommand(InfoGUI infoGUI, ShopGUI shopGUI, GuiEditor editor) {
        this.infoGUI = infoGUI;
        this.shopGUI = shopGUI;
        this.editor = editor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use /guiedit.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "editor":
            case "edit": {
                if (!(sender instanceof Player player)) return playersOnly(sender);
                String which = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                if (which.equals("info")) editor.openInfo(player);
                else if (which.equals("shop")) editor.openShop(player, 0);
                else return usage(sender, "/guiedit editor <info|shop>");
                return true;
            }
            case "info":
                return handleInfo(sender, args);
            case "shop":
                return handleShop(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }

    // ------------------------------------------------------------------
    // /guiedit info ...
    // ------------------------------------------------------------------

    private boolean handleInfo(CommandSender sender, String[] args) {
        GuiConfig config = GuiConfig.get();
        List<GuiConfig.InfoEntry> entries = config.info();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";

        switch (action) {
            case "list": {
                sender.sendMessage(ChatColor.AQUA + "/info menu - " + entries.size() + "/" + GuiConfig.MAX_INFO_ENTRIES + " slots used:");
                for (int i = 0; i < entries.size(); i++) {
                    GuiConfig.InfoEntry entry = entries.get(i);
                    sender.sendMessage(ChatColor.GRAY + "#" + (i + 1) + " " + ChatColor.WHITE + entry.icon.name()
                            + ChatColor.GRAY + " - " + GuiConfig.colorize(entry.name) + ChatColor.GRAY
                            + " (" + entry.lore.size() + " lore line" + (entry.lore.size() == 1 ? "" : "s") + ")");
                }
                return true;
            }
            case "open": {
                if (!(sender instanceof Player player)) return playersOnly(sender);
                infoGUI.open(player);
                return true;
            }
            case "reset": {
                config.resetInfo(InfoGUI.defaultEntries());
                sender.sendMessage(ChatColor.GRAY + "/info menu reset to the built-in default.");
                return true;
            }
            case "add": {
                if (args.length < 4) return usage(sender, "/guiedit info add <icon> <name...>");
                if (entries.size() >= GuiConfig.MAX_INFO_ENTRIES) {
                    sender.sendMessage(ChatColor.RED + "The menu is full (" + GuiConfig.MAX_INFO_ENTRIES + " slots) - remove one first.");
                    return true;
                }
                Material icon = material(sender, args[2]);
                if (icon == null) return true;
                entries.add(new GuiConfig.InfoEntry(icon, join(args, 3), new ArrayList<>()));
                config.save();
                sender.sendMessage(ChatColor.GRAY + "Added #" + entries.size() + ". Add lore lines with /guiedit info addlore " + entries.size() + " <text>.");
                return true;
            }
            default:
                break;
        }

        // Everything below edits an existing entry: /guiedit info <action> <#> ...
        if (args.length < 3) return usage(sender, "/guiedit info " + action + " <#> ...");
        GuiConfig.InfoEntry entry = entryAt(sender, entries, args[2]);
        if (entry == null) return true;
        int index = entries.indexOf(entry);

        switch (action) {
            case "remove":
                entries.remove(index);
                config.save();
                sender.sendMessage(ChatColor.GRAY + "Removed #" + (index + 1) + ".");
                return true;
            case "setname":
                if (args.length < 4) return usage(sender, "/guiedit info setname <#> <name...>");
                entry.name = join(args, 3);
                config.save();
                sender.sendMessage(ChatColor.GRAY + "Renamed #" + (index + 1) + " to " + GuiConfig.colorize(entry.name));
                return true;
            case "seticon": {
                if (args.length < 4) return usage(sender, "/guiedit info seticon <#> <icon>");
                Material icon = material(sender, args[3]);
                if (icon == null) return true;
                entry.icon = icon;
                config.save();
                sender.sendMessage(ChatColor.GRAY + "#" + (index + 1) + " icon is now " + icon.name() + ".");
                return true;
            }
            case "addlore":
                if (args.length < 4) return usage(sender, "/guiedit info addlore <#> <text...>");
                entry.lore.add(join(args, 3));
                config.save();
                sender.sendMessage(ChatColor.GRAY + "Added lore line " + entry.lore.size() + " to #" + (index + 1) + ".");
                return true;
            case "setlore": {
                if (args.length < 5) return usage(sender, "/guiedit info setlore <#> <line#> <text...>");
                Integer line = number(sender, args[3], entry.lore.size(), "lore line");
                if (line == null) return true;
                entry.lore.set(line - 1, join(args, 4));
                config.save();
                sender.sendMessage(ChatColor.GRAY + "Lore line " + line + " of #" + (index + 1) + " updated.");
                return true;
            }
            case "removelore": {
                if (args.length < 4) return usage(sender, "/guiedit info removelore <#> <line#>");
                Integer line = number(sender, args[3], entry.lore.size(), "lore line");
                if (line == null) return true;
                entry.lore.remove(line - 1);
                config.save();
                sender.sendMessage(ChatColor.GRAY + "Lore line " + line + " of #" + (index + 1) + " removed.");
                return true;
            }
            case "clearlore":
                entry.lore.clear();
                config.save();
                sender.sendMessage(ChatColor.GRAY + "Cleared all lore on #" + (index + 1) + ".");
                return true;
            case "move": {
                if (args.length < 4) return usage(sender, "/guiedit info move <#> <new position>");
                Integer target = number(sender, args[3], entries.size(), "position");
                if (target == null) return true;
                entries.remove(index);
                entries.add(target - 1, entry);
                config.save();
                sender.sendMessage(ChatColor.GRAY + "Moved to position " + target + ".");
                return true;
            }
            case "show": {
                sender.sendMessage(ChatColor.AQUA + "#" + (index + 1) + " " + entry.icon.name() + " - " + GuiConfig.colorize(entry.name));
                for (int i = 0; i < entry.lore.size(); i++) {
                    sender.sendMessage(ChatColor.GRAY + "  " + (i + 1) + ". " + GuiConfig.colorize(entry.lore.get(i)));
                }
                return true;
            }
            default:
                return usage(sender, "/guiedit help");
        }
    }

    // ------------------------------------------------------------------
    // /guiedit shop ...
    // ------------------------------------------------------------------

    private boolean handleShop(CommandSender sender, String[] args) {
        GuiConfig config = GuiConfig.get();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";

        switch (action) {
            case "list": {
                sender.sendMessage(ChatColor.AQUA + "/shop - " + config.shopCatalog().size() + " items. Price overrides:");
                Map<Material, Long> stack = config.stackPriceOverrides();
                Map<Material, Long> unit = config.unitPriceOverrides();
                if (stack.isEmpty() && unit.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "  none - everything is " + CoinFormat.formatWithExact(ShopGUI.PRICE_PER_STACK)
                            + " per stack of 64 (villager egg is " + CoinFormat.formatWithExact(ShopGUI.CUSTOM_UNIT_PRICE.get(Material.VILLAGER_SPAWN_EGG)) + " each).");
                }
                stack.forEach((m, p) -> sender.sendMessage(ChatColor.GRAY + "  " + m.name() + ": " + CoinFormat.formatWithExact(p) + " per stack"));
                unit.forEach((m, p) -> sender.sendMessage(ChatColor.GRAY + "  " + m.name() + ": " + CoinFormat.formatWithExact(p) + " each"));
                return true;
            }
            case "open": {
                if (!(sender instanceof Player player)) return playersOnly(sender);
                shopGUI.openCatalog(player, 0);
                return true;
            }
            case "reset":
                config.resetShop();
                sender.sendMessage(ChatColor.GRAY + "/shop reset: all price overrides cleared, removed items restored, added items dropped.");
                return true;
            default:
                break;
        }

        if (args.length < 3) return usage(sender, "/guiedit shop " + action + " <material> ...");
        Material material = material(sender, args[2]);
        if (material == null) return true;

        switch (action) {
            case "add":
                if (config.shopContains(material)) {
                    sender.sendMessage(ChatColor.RED + material.name() + " is already in the shop.");
                    return true;
                }
                config.shopAdd(material);
                sender.sendMessage(ChatColor.GRAY + "Added " + material.name() + " to the shop at the normal "
                        + CoinFormat.formatWithExact(ShopGUI.PRICE_PER_STACK) + "/stack. Change it with /guiedit shop price or unit.");
                return true;
            case "remove":
                if (!config.shopContains(material)) {
                    sender.sendMessage(ChatColor.RED + material.name() + " isn't in the shop.");
                    return true;
                }
                config.shopRemove(material);
                sender.sendMessage(ChatColor.GRAY + "Removed " + material.name() + " from the shop.");
                return true;
            case "price":
            case "unit": {
                if (args.length < 4) return usage(sender, "/guiedit shop " + action + " <material> <coins>");
                Long coins = coins(sender, args[3]);
                if (coins == null) return true;
                if (action.equals("price")) config.setStackPrice(material, coins);
                else config.setUnitPrice(material, coins);
                sender.sendMessage(ChatColor.GRAY + material.name() + " now costs " + CoinFormat.formatWithExact(coins)
                        + (action.equals("price") ? " per stack of 64." : " each."));
                if (!config.shopContains(material)) {
                    sender.sendMessage(ChatColor.YELLOW + "(It isn't in the shop yet - /guiedit shop add " + material.name().toLowerCase(Locale.ROOT) + ")");
                }
                return true;
            }
            case "resetprice":
                config.resetPrice(material);
                sender.sendMessage(ChatColor.GRAY + material.name() + " is back to its default price.");
                return true;
            default:
                return usage(sender, "/guiedit help");
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void sendHelp(CommandSender sender) {
        String[] lines = {
                "/guiedit editor info|shop  - VISUAL editor: move items with your mouse",
                "/guiedit info list | show <#> | open | reset",
                "/guiedit info add <icon> <name...>   (use &c-style color codes)",
                "/guiedit info remove <#> | move <#> <pos> | setname <#> <name...> | seticon <#> <icon>",
                "/guiedit info addlore <#> <text...> | setlore <#> <line#> <text...> | removelore <#> <line#> | clearlore <#>",
                "/guiedit shop list | open | reset",
                "/guiedit shop add <material> | remove <material>",
                "/guiedit shop price <material> <coins per stack of 64> | unit <material> <coins each> | resetprice <material>",
                "Changes save instantly and show the next time anyone opens the menu."
        };
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "GUI editor");
        for (String line : lines) sender.sendMessage(ChatColor.GRAY + line);
    }

    private boolean usage(CommandSender sender, String usage) {
        sender.sendMessage(ChatColor.RED + "Usage: " + usage);
        return true;
    }

    private boolean playersOnly(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Only players can open a GUI.");
        return true;
    }

    private Material material(CommandSender sender, String name) {
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem() || material.isLegacy()) {
            sender.sendMessage(ChatColor.RED + "\"" + name + "\" isn't an item. Use a material name like diamond_block.");
            return null;
        }
        return material;
    }

    private GuiConfig.InfoEntry entryAt(CommandSender sender, List<GuiConfig.InfoEntry> entries, String arg) {
        Integer number = number(sender, arg, entries.size(), "entry");
        return number == null ? null : entries.get(number - 1);
    }

    /** Parses a 1-based position within [1, max]; tells the sender and returns null if it's not valid. */
    private Integer number(CommandSender sender, String arg, int max, String what) {
        try {
            int value = Integer.parseInt(arg.replace("#", ""));
            if (value >= 1 && value <= max) return value;
        } catch (NumberFormatException ignored) {
            // fall through to the message below
        }
        sender.sendMessage(ChatColor.RED + "That's not a valid " + what + " number (1-" + max + ").");
        return null;
    }

    private Long coins(CommandSender sender, String arg) {
        try {
            long value = Long.parseLong(arg.replace(",", ""));
            if (value > 0) return value;
        } catch (NumberFormatException ignored) {
            // fall through
        }
        sender.sendMessage(ChatColor.RED + "Price must be a whole number greater than 0.");
        return null;
    }

    private static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        if (args.length == 1) return filter(List.of("editor", "info", "shop", "help"), args[0]);
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("editor")) return filter(List.of("info", "shop"), args[1]);
            return args[0].equalsIgnoreCase("info")
                    ? filter(List.of("list", "show", "open", "add", "remove", "move", "setname", "seticon",
                            "addlore", "setlore", "removelore", "clearlore", "reset"), args[1])
                    : args[0].equalsIgnoreCase("shop")
                    ? filter(List.of("list", "open", "add", "remove", "price", "unit", "resetprice", "reset"), args[1])
                    : List.of();
        }
        if (args.length == 3) {
            String sub = args[1].toLowerCase(Locale.ROOT);
            boolean wantsMaterial = (args[0].equalsIgnoreCase("info") && sub.equals("add"))
                    || (args[0].equalsIgnoreCase("shop") && List.of("add", "remove", "price", "unit", "resetprice").contains(sub));
            if (wantsMaterial) return materials(args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("info") && args[1].equalsIgnoreCase("seticon")) {
            return materials(args[3]);
        }
        return List.of();
    }

    private List<String> materials(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isItem() || material.isLegacy()) continue;
            String name = material.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(lower)) out.add(name);
            if (out.size() >= 40) break; // keep the tab-complete list short
        }
        return out;
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(prefix.toLowerCase(Locale.ROOT))) out.add(option);
        }
        return out;
    }
}
