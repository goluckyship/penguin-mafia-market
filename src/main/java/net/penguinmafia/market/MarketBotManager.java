package net.penguinmafia.market;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the Black Market feeling alive even when no real players are
 * selling: every REFRESH_INTERVAL, the "Black Market Dealer" (a system
 * seller, not a real player) clears its old stock and lists a fresh batch
 * of up to TARGET_COUNT listings, pulled at random from a curated,
 * vanilla-survival-obtainable item pool and priced off BASE_PRICES.
 *
 * Deliberately excludes anything creative-only, admin-only, or otherwise
 * not obtainable by a normal survival player - no dragon eggs, spawners,
 * command/structure blocks, barriers, etc (see BLOCKED, enforced on top of
 * the curated pool as a belt-and-suspenders check). Rare/end-game items
 * that genuinely are vanilla-obtainable (netherite, elytra, totems...) are
 * allowed, just priced steeply so they aren't a cheap shortcut.
 */
public class MarketBotManager {

    public static final UUID SELLER_ID = UUID.fromString("00000000-0000-4000-a000-000000000bee");
    public static final String SELLER_NAME = "Black Market Dealer";

    private static final int TARGET_COUNT = 1000;
    private static final long REFRESH_INTERVAL_TICKS = 20L * 60L * 10L; // 10 minutes

    /**
     * Materials that must never appear in the Black Market no matter what,
     * even if someone adds one to BASE_PRICES below by mistake later -
     * creative-only / admin-only / structurally special blocks that a
     * normal survival player could never legitimately hold.
     */
    private static final Set<Material> BLOCKED = EnumSet.of(
            Material.DRAGON_EGG, Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK, Material.COMMAND_BLOCK_MINECART,
            Material.STRUCTURE_BLOCK, Material.STRUCTURE_VOID, Material.BARRIER,
            Material.DEBUG_STICK, Material.JIGSAW, Material.LIGHT, Material.BEDROCK,
            Material.END_PORTAL_FRAME, Material.SPAWNER, Material.REINFORCED_DEEPSLATE,
            Material.PETRIFIED_OAK_SLAB, Material.KNOWLEDGE_BOOK
    );

    /** material -> fair base price per single item, hand-tuned by rarity/usefulness. */
    private static final Map<Material, Long> BASE_PRICES = new LinkedHashMap<>();
    static {
        // --- Common building blocks / resources: cheap, sell in bulk ---
        put(Material.DIRT, 1); put(Material.COBBLESTONE, 2); put(Material.STONE, 3);
        put(Material.SAND, 2); put(Material.GRAVEL, 2); put(Material.OAK_LOG, 5);
        put(Material.SPRUCE_LOG, 5); put(Material.BIRCH_LOG, 5); put(Material.JUNGLE_LOG, 5);
        put(Material.ACACIA_LOG, 5); put(Material.DARK_OAK_LOG, 5); put(Material.MANGROVE_LOG, 5);
        put(Material.CHERRY_LOG, 6); put(Material.OAK_PLANKS, 3); put(Material.GLASS, 4);
        put(Material.SANDSTONE, 4); put(Material.NETHERRACK, 3); put(Material.BASALT, 4);
        put(Material.CLAY_BALL, 4); put(Material.TERRACOTTA, 5); put(Material.WHITE_WOOL, 6);
        put(Material.WHITE_CONCRETE, 8); put(Material.ANDESITE, 3); put(Material.DIORITE, 3);
        put(Material.GRANITE, 3); put(Material.DEEPSLATE, 4); put(Material.TUFF, 4);
        put(Material.MOSS_BLOCK, 10); put(Material.MUD, 3); put(Material.PACKED_ICE, 12);

        // --- Common farm/mob-drop resources ---
        put(Material.COAL, 18); put(Material.IRON_INGOT, 30); put(Material.COPPER_INGOT, 14);
        put(Material.REDSTONE, 10); put(Material.LAPIS_LAZULI, 10); put(Material.QUARTZ, 16);
        put(Material.FLINT, 6); put(Material.STRING, 5); put(Material.FEATHER, 6);
        put(Material.LEATHER, 12); put(Material.BONE, 6); put(Material.GUNPOWDER, 14);
        put(Material.ROTTEN_FLESH, 1); put(Material.SPIDER_EYE, 8); put(Material.SLIME_BALL, 20);
        put(Material.INK_SAC, 6); put(Material.GLOW_INK_SAC, 20); put(Material.HONEYCOMB, 14);
        put(Material.HONEY_BOTTLE, 18); put(Material.SUGAR, 5); put(Material.WHEAT, 5);
        put(Material.CARROT, 4); put(Material.POTATO, 4); put(Material.BEETROOT, 4);
        put(Material.APPLE, 10); put(Material.BREAD, 8); put(Material.EGG, 4);
        put(Material.MILK_BUCKET, 10); put(Material.COD, 6); put(Material.SALMON, 7);
        put(Material.KELP, 2); put(Material.BAMBOO, 2); put(Material.SUGAR_CANE, 3);
        put(Material.CACTUS, 3); put(Material.PUMPKIN, 10); put(Material.MELON, 6);
        put(Material.CHORUS_FRUIT, 12); put(Material.NETHER_WART, 10); put(Material.OBSIDIAN, 35);
        put(Material.GLOWSTONE_DUST, 14); put(Material.BLAZE_POWDER, 40); put(Material.BLAZE_ROD, 70);
        put(Material.MAGMA_CREAM, 30); put(Material.ENDER_PEARL, 90); put(Material.EXPERIENCE_BOTTLE, 60);
        put(Material.RABBIT_FOOT, 50); put(Material.RABBIT_HIDE, 10); put(Material.PHANTOM_MEMBRANE, 60);
        put(Material.GHAST_TEAR, 150); put(Material.TURTLE_SCUTE, 80);
        put(Material.AMETHYST_SHARD, 35); put(Material.PRISMARINE_SHARD, 12); put(Material.PRISMARINE_CRYSTALS, 20);
        put(Material.NAUTILUS_SHELL, 220); put(Material.DRAGON_BREATH, 300);

        // --- Uncommon ---
        put(Material.GOLD_INGOT, 60); put(Material.GOLD_NUGGET, 8);

        // --- Rare ---
        put(Material.DIAMOND, 350); put(Material.EMERALD, 220); put(Material.HEART_OF_THE_SEA, 1200);
        put(Material.NETHERITE_SCRAP, 1500); put(Material.ANCIENT_DEBRIS, 1800);
        put(Material.SHULKER_SHELL, 900); put(Material.TRIDENT, 4000); put(Material.NETHER_STAR, 6000);

        // --- Very rare / end-game - vanilla-obtainable, priced steeply so they're a real sink ---
        put(Material.NETHERITE_INGOT, 5500); put(Material.ENCHANTED_GOLDEN_APPLE, 8000);
        put(Material.TOTEM_OF_UNDYING, 7000); put(Material.ELYTRA, 9000);

        // --- Plain, unenchanted tools/armor - flat prices, not tier-derived ---
        put(Material.IRON_SWORD, 200); put(Material.IRON_PICKAXE, 220); put(Material.IRON_AXE, 220);
        put(Material.DIAMOND_SWORD, 900); put(Material.DIAMOND_PICKAXE, 1000); put(Material.DIAMOND_AXE, 1000);
        put(Material.DIAMOND_HELMET, 700); put(Material.DIAMOND_CHESTPLATE, 1100);
        put(Material.DIAMOND_LEGGINGS, 1000); put(Material.DIAMOND_BOOTS, 650);
        put(Material.NETHERITE_SWORD, 6500); put(Material.NETHERITE_PICKAXE, 7000);
        put(Material.NETHERITE_HELMET, 5500); put(Material.NETHERITE_CHESTPLATE, 8500);
        put(Material.NETHERITE_LEGGINGS, 7500); put(Material.NETHERITE_BOOTS, 5000);
        put(Material.BOW, 150); put(Material.CROSSBOW, 200); put(Material.SHIELD, 120);
    }

    private static void put(Material material, long price) {
        if (BLOCKED.contains(material)) return; // never let a blocked material sneak into the pool
        BASE_PRICES.put(material, price);
    }

    private final PenguinMafiaMarket plugin;
    private final MarketManager market;
    private final List<Material> pool;
    private final Random random = new Random();

    public MarketBotManager(PenguinMafiaMarket plugin, MarketManager market) {
        this.plugin = plugin;
        this.market = market;
        this.pool = new ArrayList<>(BASE_PRICES.keySet());
    }

    /** Starts the restock timer - an immediate first stock, then every REFRESH_INTERVAL after that. */
    public static void start(PenguinMafiaMarket plugin, MarketManager market) {
        MarketBotManager manager = new MarketBotManager(plugin, market);
        new BukkitRunnable() {
            @Override
            public void run() {
                manager.refresh();
            }
        }.runTaskTimer(plugin, 100L, REFRESH_INTERVAL_TICKS);
    }

    /** Clears the dealer's current stock and lists a fresh, randomly-rolled batch in its place. */
    public void refresh() {
        market.removeListingsBySeller(SELLER_ID);

        int listed = 0;
        for (int i = 0; i < TARGET_COUNT; i++) {
            Material material = pool.get(random.nextInt(pool.size()));
            if (BLOCKED.contains(material)) continue; // safety net, should never actually trigger

            long unitPrice = BASE_PRICES.get(material);
            int amount = rollAmount(unitPrice);
            // +/-15% variance so the dealer doesn't look like a flat, copy-pasted price list
            double variance = 0.85 + random.nextDouble() * 0.30;
            long price = Math.max(1, Math.round(unitPrice * amount * variance));

            market.createSystemListing(SELLER_ID, SELLER_NAME, new ItemStack(material, amount), price);
            listed++;
        }

        market.save();
        plugin.getLogger().info("Black Market Dealer restocked " + listed + " listings.");
    }

    /** Cheap/common items list in bigger stacks; expensive/rare items list in small quantities. */
    private int rollAmount(long unitPrice) {
        if (unitPrice >= 1000) return 1 + random.nextInt(2);  // 1-2
        if (unitPrice >= 100) return 1 + random.nextInt(5);   // 1-5
        if (unitPrice >= 20) return 1 + random.nextInt(16);   // 1-16
        return 1 + random.nextInt(64);                        // 1-64
    }
}
