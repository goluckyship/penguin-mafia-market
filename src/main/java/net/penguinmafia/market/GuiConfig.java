package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything /guiedit can change, persisted in gui_config.yml so edits made
 * in-game survive restarts and take effect the very next time anyone opens
 * the menu (both GUIs rebuild from this every time they open - there's no
 * cached copy to refresh).
 *
 * Two areas:
 *  - /info menu entries (icon, name, lore lines), seeded from the built-in
 *    defaults the first time this file has no "info" section.
 *  - /shop: per-stack price overrides, per-item ("unit") price overrides,
 *    items removed from the catalog, and extra items added to it.
 *
 * Text is stored with '&' color codes so the yaml stays hand-editable too;
 * it's translated to real color codes only when displayed.
 */
public class GuiConfig {

    public static final int MAX_INFO_ENTRIES = 45; // bottom row of the 54-slot menu is the close button

    /** One /info menu slot. Plain data, mutable on purpose so /guiedit can edit entries in place. */
    public static class InfoEntry {
        public Material icon;
        public String name;
        public final List<String> lore = new ArrayList<>();

        public InfoEntry(Material icon, String name, List<String> lore) {
            this.icon = icon;
            this.name = name;
            this.lore.addAll(lore);
        }
    }

    private static GuiConfig instance;

    /** The live config (set once by the plugin at startup, before either GUI is built). */
    public static GuiConfig get() {
        return instance;
    }

    public static GuiConfig load(PenguinMafiaMarket plugin, List<InfoEntry> defaultInfo) {
        instance = new GuiConfig(plugin, defaultInfo);
        return instance;
    }

    private final PenguinMafiaMarket plugin;
    private final File file;
    private final YamlConfiguration config;

    private final List<InfoEntry> info = new ArrayList<>();
    private final Map<Material, Long> stackPrices = new LinkedHashMap<>();
    private final Map<Material, Long> unitPrices = new LinkedHashMap<>();
    private final Set<Material> shopRemoved = new LinkedHashSet<>();
    private final List<Material> shopAdded = new ArrayList<>();

    private GuiConfig(PenguinMafiaMarket plugin, List<InfoEntry> defaultInfo) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gui_config.yml");
        this.config = YamlConfiguration.loadConfiguration(file);

        if (config.isList("info")) {
            loadInfo();
        } else {
            info.addAll(defaultInfo);
            save(); // write the defaults out so they're visible/editable in the file right away
        }
        loadShop();
    }

    // ------------------------------------------------------------------
    // Loading / saving
    // ------------------------------------------------------------------

    private void loadInfo() {
        for (Map<?, ?> row : config.getMapList("info")) {
            Material icon = Material.matchMaterial(String.valueOf(row.get("icon")));
            if (icon == null) icon = Material.PAPER;
            String name = String.valueOf(row.get("name"));
            List<String> lore = new ArrayList<>();
            if (row.get("lore") instanceof List<?> list) {
                for (Object line : list) lore.add(String.valueOf(line));
            }
            info.add(new InfoEntry(icon, name, lore));
        }
    }

    private void loadShop() {
        ConfigurationSection stack = config.getConfigurationSection("shop.stack-prices");
        if (stack != null) {
            for (String key : stack.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material != null) stackPrices.put(material, stack.getLong(key));
            }
        }
        ConfigurationSection unit = config.getConfigurationSection("shop.unit-prices");
        if (unit != null) {
            for (String key : unit.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material != null) unitPrices.put(material, unit.getLong(key));
            }
        }
        for (String name : config.getStringList("shop.removed")) {
            Material material = Material.matchMaterial(name);
            if (material != null) shopRemoved.add(material);
        }
        for (String name : config.getStringList("shop.added")) {
            Material material = Material.matchMaterial(name);
            if (material != null && !shopAdded.contains(material)) shopAdded.add(material);
        }
    }

    public void save() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (InfoEntry entry : info) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("icon", entry.icon.name());
            row.put("name", toRaw(entry.name));
            List<String> lore = new ArrayList<>();
            for (String line : entry.lore) lore.add(toRaw(line));
            row.put("lore", lore);
            rows.add(row);
        }
        config.set("info", rows);

        config.set("shop.stack-prices", null);
        stackPrices.forEach((m, p) -> config.set("shop.stack-prices." + m.name(), p));
        config.set("shop.unit-prices", null);
        unitPrices.forEach((m, p) -> config.set("shop.unit-prices." + m.name(), p));
        config.set("shop.removed", shopRemoved.stream().map(Material::name).toList());
        config.set("shop.added", shopAdded.stream().map(Material::name).toList());

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save gui_config.yml: " + e.getMessage());
        }
    }

    /** Real color codes -> '&' codes, for storage. */
    public static String toRaw(String text) {
        return text.replace(ChatColor.COLOR_CHAR, '&');
    }

    /** '&' codes -> real color codes, for display. */
    public static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    // ------------------------------------------------------------------
    // /info menu
    // ------------------------------------------------------------------

    /** Live list - /guiedit edits it in place; callers that only read it shouldn't hold onto it. */
    public List<InfoEntry> info() {
        return info;
    }

    public void resetInfo(List<InfoEntry> defaults) {
        info.clear();
        info.addAll(defaults);
        save();
    }

    // ------------------------------------------------------------------
    // /shop
    // ------------------------------------------------------------------

    /** The catalog in display order: built-in categories minus anything removed, then anything added. */
    public List<Material> shopCatalog() {
        List<Material> all = new ArrayList<>();
        for (BuildingBlockShop.Category category : BuildingBlockShop.CATEGORIES) {
            for (Material material : category.blocks) {
                if (!shopRemoved.contains(material) && !all.contains(material)) all.add(material);
            }
        }
        for (Material material : shopAdded) {
            if (!shopRemoved.contains(material) && !all.contains(material)) all.add(material);
        }
        return all;
    }

    public boolean shopContains(Material material) {
        return shopCatalog().contains(material);
    }

    public void shopAdd(Material material) {
        shopRemoved.remove(material);
        boolean builtIn = BuildingBlockShop.CATEGORIES.stream().anyMatch(c -> c.blocks.contains(material));
        if (!builtIn && !shopAdded.contains(material)) shopAdded.add(material);
        save();
    }

    public void shopRemove(Material material) {
        shopAdded.remove(material);
        shopRemoved.add(material);
        save();
    }

    /** Per-stack-of-64 price override, or null to use the shop's normal rate. */
    public Long stackPrice(Material material) {
        return stackPrices.get(material);
    }

    /** Per-item price (built-in specials like the villager egg, or an op override), or null for normal per-stack pricing. */
    public Long unitPrice(Material material) {
        if (stackPrices.containsKey(material)) return null; // an explicit per-stack price beats a built-in per-item one
        Long override = unitPrices.get(material);
        return override != null ? override : ShopGUI.CUSTOM_UNIT_PRICE.get(material);
    }

    public void setStackPrice(Material material, long price) {
        stackPrices.put(material, price);
        unitPrices.remove(material); // an explicit per-stack price replaces a per-item one
        save();
    }

    public void setUnitPrice(Material material, long price) {
        unitPrices.put(material, price);
        stackPrices.remove(material);
        save();
    }

    /** Back to default pricing for this material (built-in per-item specials keep their built-in price). */
    public void resetPrice(Material material) {
        stackPrices.remove(material);
        unitPrices.remove(material);
        save();
    }

    public Map<Material, Long> stackPriceOverrides() {
        return Collections.unmodifiableMap(stackPrices);
    }

    public Map<Material, Long> unitPriceOverrides() {
        return Collections.unmodifiableMap(unitPrices);
    }

    public void resetShop() {
        stackPrices.clear();
        unitPrices.clear();
        shopRemoved.clear();
        shopAdded.clear();
        save();
    }
}
