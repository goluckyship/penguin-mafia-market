package net.penguinmafia.market;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the Black Market feeling alive even when no real players are
 * selling: every REFRESH_INTERVAL, the "Black Market Dealer" (a system
 * seller, not a real player) rolls a fresh batch of up to TARGET_COUNT
 * items from a curated, vanilla-survival-obtainable item pool, priced off
 * BASE_PRICES, and adds them on top of whatever it already has listed -
 * topping up its existing listing for a material (more quantity, more
 * price) instead of deleting and relisting everything from scratch. Past,
 * still-unsold listings are never removed or replaced by a restock; an op
 * can also trigger an extra round early with /bm restock.
 *
 * Deliberately excludes anything creative-only, admin-only, or otherwise
 * not obtainable by a normal survival player - no dragon eggs, spawners,
 * command/structure blocks, barriers, etc (see BLOCKED, enforced on top of
 * the curated pool as a belt-and-suspenders check). Rare/end-game items
 * that genuinely are vanilla-obtainable (netherite, elytra, totems...) are
 * allowed, just priced steeply so they aren't a cheap shortcut.
 *
 * Pool and stack sizes are tuned generous on purpose: a wide variety of
 * blocks/food/tools/decor beyond just raw resources, firework rockets
 * stocked extra heavily as a specialty, and per-listing quantities rolled
 * roughly tenfold over a bare-bones shop (still capped at each material's
 * real stack limit, so tools/armor stay realistic at 1).
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

        // --- Rockets - the requested specialty item; weighted extra-heavy in the pool below ---
        put(Material.FIREWORK_ROCKET, 20);

        // --- More wood/wool/decor variety ---
        put(Material.SPRUCE_PLANKS, 3); put(Material.BIRCH_PLANKS, 3); put(Material.JUNGLE_PLANKS, 3);
        put(Material.ACACIA_PLANKS, 3); put(Material.DARK_OAK_PLANKS, 3); put(Material.MANGROVE_PLANKS, 3);
        put(Material.CHERRY_PLANKS, 4); put(Material.BAMBOO_PLANKS, 3);
        put(Material.ORANGE_WOOL, 6); put(Material.MAGENTA_WOOL, 6); put(Material.LIGHT_BLUE_WOOL, 6);
        put(Material.YELLOW_WOOL, 6); put(Material.LIME_WOOL, 6); put(Material.PINK_WOOL, 6);
        put(Material.GRAY_WOOL, 6); put(Material.LIGHT_GRAY_WOOL, 6); put(Material.CYAN_WOOL, 6);
        put(Material.PURPLE_WOOL, 6); put(Material.BLUE_WOOL, 6); put(Material.BROWN_WOOL, 6);
        put(Material.GREEN_WOOL, 6); put(Material.RED_WOOL, 6); put(Material.BLACK_WOOL, 6);
        put(Material.BLACK_CONCRETE, 8); put(Material.RED_CONCRETE, 8); put(Material.BLUE_CONCRETE, 8);
        put(Material.WHITE_GLAZED_TERRACOTTA, 20); put(Material.BLUE_GLAZED_TERRACOTTA, 20);
        put(Material.POPPY, 4); put(Material.DANDELION, 4); put(Material.BLUE_ORCHID, 6);
        put(Material.ALLIUM, 6); put(Material.AZURE_BLUET, 6); put(Material.ORANGE_TULIP, 5);
        put(Material.PINK_TULIP, 5); put(Material.RED_TULIP, 5); put(Material.WHITE_TULIP, 5);
        put(Material.OXEYE_DAISY, 4); put(Material.CORNFLOWER, 5); put(Material.LILY_OF_THE_VALLEY, 8);
        put(Material.SUNFLOWER, 8); put(Material.LILAC, 8); put(Material.ROSE_BUSH, 8); put(Material.PEONY, 8);

        // --- Food variety ---
        put(Material.BEEF, 6); put(Material.COOKED_BEEF, 12); put(Material.PORKCHOP, 6);
        put(Material.COOKED_PORKCHOP, 12); put(Material.CHICKEN, 4); put(Material.COOKED_CHICKEN, 8);
        put(Material.MUTTON, 5); put(Material.COOKED_MUTTON, 10); put(Material.RABBIT, 5);
        put(Material.COOKED_RABBIT, 10); put(Material.BAKED_POTATO, 6); put(Material.PUMPKIN_PIE, 16);
        put(Material.COOKIE, 3); put(Material.CAKE, 40); put(Material.GOLDEN_CARROT, 40);
        put(Material.GOLDEN_APPLE, 300); put(Material.MUSHROOM_STEW, 10); put(Material.RABBIT_STEW, 14);
        put(Material.SUSPICIOUS_STEW, 10); put(Material.DRIED_KELP, 2); put(Material.SWEET_BERRIES, 4);
        put(Material.GLOW_BERRIES, 6); put(Material.TROPICAL_FISH, 10); put(Material.PUFFERFISH, 8);
        put(Material.FERMENTED_SPIDER_EYE, 18); put(Material.GLISTERING_MELON_SLICE, 30);

        // --- Redstone & utility blocks ---
        put(Material.REPEATER, 20); put(Material.COMPARATOR, 30); put(Material.PISTON, 25);
        put(Material.STICKY_PISTON, 35); put(Material.OBSERVER, 40); put(Material.HOPPER, 60);
        put(Material.DROPPER, 15); put(Material.DISPENSER, 20); put(Material.NOTE_BLOCK, 10);
        put(Material.TARGET, 25); put(Material.TRIPWIRE_HOOK, 8); put(Material.DAYLIGHT_DETECTOR, 50);
        put(Material.LECTERN, 20); put(Material.COMPOSTER, 15); put(Material.BARREL, 15);
        put(Material.SMOKER, 25); put(Material.BLAST_FURNACE, 35); put(Material.CARTOGRAPHY_TABLE, 20);
        put(Material.FLETCHING_TABLE, 20); put(Material.SMITHING_TABLE, 20); put(Material.GRINDSTONE, 25);
        put(Material.STONECUTTER, 25); put(Material.LOOM, 15); put(Material.LODESTONE, 80);
        put(Material.RESPAWN_ANCHOR, 400); put(Material.CRYING_OBSIDIAN, 60);

        // --- Nether, deep dark & nature decoration ---
        put(Material.WARPED_STEM, 8); put(Material.CRIMSON_STEM, 8); put(Material.WARPED_NYLIUM, 6);
        put(Material.CRIMSON_NYLIUM, 6); put(Material.WARPED_FUNGUS, 5); put(Material.CRIMSON_FUNGUS, 5);
        put(Material.SHROOMLIGHT, 15); put(Material.NETHER_WART_BLOCK, 10); put(Material.SOUL_SAND, 5);
        put(Material.SOUL_SOIL, 5); put(Material.MAGMA_BLOCK, 10); put(Material.BLACKSTONE, 4);
        put(Material.GILDED_BLACKSTONE, 60); put(Material.SCULK, 20); put(Material.GLOW_LICHEN, 8);
        put(Material.MOSS_CARPET, 6); put(Material.AZALEA, 10); put(Material.FLOWERING_AZALEA, 15);
        put(Material.MANGROVE_ROOTS, 8); put(Material.MUD_BRICKS, 5); put(Material.PACKED_MUD, 4);
        put(Material.CALCITE, 4); put(Material.DRIPSTONE_BLOCK, 6); put(Material.POINTED_DRIPSTONE, 10);
        put(Material.AMETHYST_BLOCK, 50);

        // --- Other tool/armor tiers and handy gear ---
        put(Material.WOODEN_SWORD, 15); put(Material.WOODEN_PICKAXE, 15); put(Material.WOODEN_AXE, 15);
        put(Material.STONE_SWORD, 30); put(Material.STONE_PICKAXE, 35); put(Material.STONE_AXE, 35);
        put(Material.GOLDEN_SWORD, 80); put(Material.GOLDEN_PICKAXE, 90); put(Material.GOLDEN_AXE, 90);
        put(Material.GOLDEN_HELMET, 70); put(Material.GOLDEN_CHESTPLATE, 110); put(Material.GOLDEN_LEGGINGS, 100);
        put(Material.GOLDEN_BOOTS, 65); put(Material.LEATHER_HELMET, 40); put(Material.LEATHER_CHESTPLATE, 60);
        put(Material.LEATHER_LEGGINGS, 55); put(Material.LEATHER_BOOTS, 35); put(Material.CHAINMAIL_HELMET, 120);
        put(Material.CHAINMAIL_CHESTPLATE, 180); put(Material.CHAINMAIL_LEGGINGS, 160); put(Material.CHAINMAIL_BOOTS, 110);
        put(Material.IRON_HELMET, 150); put(Material.IRON_CHESTPLATE, 240); put(Material.IRON_LEGGINGS, 220);
        put(Material.IRON_BOOTS, 130); put(Material.FISHING_ROD, 90); put(Material.SHEARS, 60);
        put(Material.FLINT_AND_STEEL, 70); put(Material.COMPASS, 50); put(Material.CLOCK, 50);
        put(Material.SPYGLASS, 300); put(Material.LEAD, 20); put(Material.SADDLE, 150);
        put(Material.CARROT_ON_A_STICK, 60);

        // --- Music discs - rare creeper-blast-drop collectibles, fun chase items ---
        put(Material.MUSIC_DISC_13, 500); put(Material.MUSIC_DISC_CAT, 500); put(Material.MUSIC_DISC_BLOCKS, 500);
        put(Material.MUSIC_DISC_CHIRP, 500); put(Material.MUSIC_DISC_FAR, 500); put(Material.MUSIC_DISC_MALL, 500);
        put(Material.MUSIC_DISC_MELLOHI, 500); put(Material.MUSIC_DISC_STAL, 500); put(Material.MUSIC_DISC_STRAD, 500);
        put(Material.MUSIC_DISC_WARD, 500); put(Material.MUSIC_DISC_11, 500); put(Material.MUSIC_DISC_WAIT, 500);
        put(Material.MUSIC_DISC_PIGSTEP, 800); put(Material.MUSIC_DISC_OTHERSIDE, 800);
        put(Material.MUSIC_DISC_5, 800); put(Material.MUSIC_DISC_RELIC, 800);
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
        // Rockets are the requested specialty item - stock the pool with nine extra
        // entries (ten total, counting the one in BASE_PRICES) so they're drawn roughly
        // ten times as often as an ordinary single-weighted item below.
        for (int i = 0; i < 9; i++) {
            pool.add(Material.FIREWORK_ROCKET);
        }
    }

    /** Starts the restock timer - an immediate first stock, then every REFRESH_INTERVAL after that. */
    public static MarketBotManager start(PenguinMafiaMarket plugin, MarketManager market) {
        MarketBotManager manager = new MarketBotManager(plugin, market);
        new BukkitRunnable() {
            @Override
            public void run() {
                manager.refresh();
            }
        }.runTaskTimer(plugin, 100L, REFRESH_INTERVAL_TICKS);
        return manager;
    }

    /**
     * Rolls a fresh batch of up to TARGET_COUNT items and adds them to the
     * dealer's stock - never wipes or replaces what's already listed. For
     * each roll, an existing dealer listing of that same material gets
     * topped up (its quantity and price both increased) instead of a brand
     * new listing being created next to it; a material only gets a new
     * listing the first time it comes up, or again later if every earlier
     * listing of it happened to get fully bought out. Once a material's
     * listing is already sitting at a full stack, further rolls of it this
     * round are skipped rather than spawning a second listing for the
     * overflow. Also the handler behind the op-only /bm restock command, so
     * the exact same logic runs whether it's the timer or a person firing
     * it early.
     *
     * @return how many item types received new stock this round
     */
    public int refresh() {
        Map<Material, Listing> existingByMaterial = new HashMap<>();
        for (Listing listing : market.getListingsBy(SELLER_ID)) {
            existingByMaterial.putIfAbsent(listing.item.getType(), listing);
        }

        int restocked = 0;
        for (int i = 0; i < TARGET_COUNT; i++) {
            Material material = pool.get(random.nextInt(pool.size()));
            if (BLOCKED.contains(material)) continue; // safety net, should never actually trigger

            long unitPrice = BASE_PRICES.get(material);
            int rolled = rollAmount(unitPrice, material);
            // +/-15% variance so the dealer doesn't look like a flat, copy-pasted price list
            double variance = 0.85 + random.nextDouble() * 0.30;
            int maxStack = material.getMaxStackSize();

            Listing existing = existingByMaterial.get(material);
            if (existing != null) {
                int currentAmount = existing.item.getAmount();
                if (currentAmount >= maxStack) continue; // already a full stack, skip this roll

                int added = Math.min(rolled, maxStack - currentAmount);
                long addedPrice = Math.max(1, Math.round(unitPrice * added * variance));

                existing.item.setAmount(currentAmount + added);
                existing.price += addedPrice;
                restocked++;
            } else {
                int amount = Math.min(rolled, maxStack);
                long price = Math.max(1, Math.round(unitPrice * amount * variance));

                Listing created = market.createSystemListing(SELLER_ID, SELLER_NAME,
                        new ItemStack(material, amount), price);
                existingByMaterial.put(material, created);
                restocked++;
            }
        }

        market.save();
        plugin.getLogger().info("Black Market Dealer added stock to " + restocked
                + " item type(s) (existing listings left in place).");
        return restocked;
    }

    /**
     * Cheap/common items list in bigger stacks; expensive/rare items list in small
     * quantities. Ranges are rolled roughly tenfold over the original bare-bones shop,
     * then capped at the material's real max stack size - so a stack of dirt can
     * genuinely jump from "1-64" to "10-640 capped at 64", while a sword or piece of
     * armor (max stack 1) always lands on exactly 1 no matter how high the roll goes.
     */
    private int rollAmount(long unitPrice, Material material) {
        int amount;
        if (unitPrice >= 1000) amount = 10 * (1 + random.nextInt(2));   // was 1-2, now 10-20
        else if (unitPrice >= 100) amount = 10 * (1 + random.nextInt(5));  // was 1-5, now 10-50
        else if (unitPrice >= 20) amount = 10 * (1 + random.nextInt(16));  // was 1-16, now 10-160
        else amount = 10 * (1 + random.nextInt(64));                       // was 1-64, now 10-640
        return Math.min(amount, material.getMaxStackSize());
    }
}
