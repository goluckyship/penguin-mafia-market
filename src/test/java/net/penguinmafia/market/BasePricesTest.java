package net.penguinmafia.market;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs the Black Market Dealer's actual, live pricing data (BASE_PRICES,
 * BuildingBlockShop's material scan) through a battery of sanity checks, so
 * a pricing regression fails the build (mvn package runs this automatically)
 * instead of only surfacing once a player notices a wrong price in-game.
 *
 * These checks exist because this exact class of bug has shipped to the live
 * server more than once this session: a material silently missing from
 * BASE_PRICES (falling to the flat 15-coin catch-all with no one noticing
 * until a player found it), and the Heavy Core 73M-a-stack floor not
 * actually holding once the inflation multiplier's downside was accounted
 * for. Every assertion below maps to a real incident, not a hypothetical.
 */
class BasePricesTest {

    /** Mirrors MarketBotManager's own BLOCKED set - duplicated here deliberately, since a test that imported the
     *  private constant it's checking against wouldn't catch a bug in that constant itself. */
    private static final Set<Material> BLOCKED = EnumSet.of(
            Material.DRAGON_EGG, Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK, Material.COMMAND_BLOCK_MINECART,
            Material.STRUCTURE_BLOCK, Material.STRUCTURE_VOID, Material.BARRIER,
            Material.DEBUG_STICK, Material.JIGSAW, Material.LIGHT, Material.BEDROCK,
            Material.END_PORTAL_FRAME, Material.SPAWNER, Material.REINFORCED_DEEPSLATE,
            Material.PETRIFIED_OAK_SLAB, Material.KNOWLEDGE_BOOK
    );

    @Test
    void noBlockedMaterialIsEverPriced() {
        Map<Material, Long> prices = MarketBotManager.getBasePrices();
        for (Material blocked : BLOCKED) {
            assertFalse(prices.containsKey(blocked),
                    blocked + " is creative/admin-only and must never have a BASE_PRICES entry");
        }
    }

    @Test
    void everyPriceIsPositive() {
        MarketBotManager.getBasePrices().forEach((material, price) ->
                assertTrue(price > 0, material + " has a non-positive price: " + price));
    }

    @Test
    void noSpawnEggIsEverPriced() {
        // The villager egg is the one spawn egg members can buy anywhere on
        // the server, and it's sold through /shop, never /bm - the dealer
        // must never carry ANY spawn egg, villager included.
        MarketBotManager.getBasePrices().keySet().forEach(material ->
                assertFalse(material.name().endsWith("_SPAWN_EGG"),
                        material + " is a spawn egg and must never be in BASE_PRICES"));
    }

    @Test
    void enchantedBookHasNoFlatUnitPrice() {
        // Enchanted books are priced per-preset (ENCHANT_PRESETS) with a
        // real enchantment attached, never sold blank at some flat
        // BASE_PRICES unit price - see restockEnchantedBooks().
        assertFalse(MarketBotManager.getBasePrices().containsKey(Material.ENCHANTED_BOOK),
                "ENCHANTED_BOOK must not have a flat BASE_PRICES entry - it's priced per-preset");
    }

    @Test
    void everyOreAndValuableBlockFromTheScanHasAPrice() {
        Map<Material, Long> prices = MarketBotManager.getBasePrices();
        List<Material> missing = BuildingBlockShop.ORES_AND_VALUABLES.stream()
                .filter(m -> !prices.containsKey(m))
                .toList();
        assertTrue(missing.isEmpty(), "Ore/valuable material(s) scanned by BuildingBlockShop but missing "
                + "a BASE_PRICES entry (would fall through and never get stocked at all): " + missing);
    }

    @Test
    void everyArmorTrimFromTheScanIsPricedCorrectly() {
        Map<Material, Long> prices = MarketBotManager.getBasePrices();
        for (Material trim : BuildingBlockShop.ARMOR_TRIMS) {
            Long price = prices.get(trim);
            assertTrue(price != null, trim + " is a smithing template scanned by BuildingBlockShop but has "
                    + "no BASE_PRICES entry at all");
            long expected = trim.name().equals("NETHERITE_UPGRADE_SMITHING_TEMPLATE") ? 2500L : 350L;
            assertTrue(price == expected, trim + " priced at " + price + " but expected " + expected
                    + " - a trim this cheap is the exact bug a player found live (Nautilus Armor Trim at 14 coins)");
        }
    }

    @Test
    void everyCatchAllItemFromTheScanHasAPrice() {
        Map<Material, Long> prices = MarketBotManager.getBasePrices();
        List<Material> missing = BuildingBlockShop.EVERYTHING_ELSE.stream()
                .filter(m -> !prices.containsKey(m))
                .toList();
        assertTrue(missing.isEmpty(), "Material(s) scanned into EVERYTHING_ELSE but missing a BASE_PRICES "
                + "entry (would never be stocked by the dealer at all): " + missing);
    }

    @Test
    void handToolTiersAreNotFallingToTheCatchAllDefault() {
        // These were the exact materials found completely missing from
        // BASE_PRICES during the first repricing pass, silently falling to
        // the flat 15-coin catch-all no matter their tier.
        // Wooden tools are deliberately priced at the same 15 coins as the
        // catch-all (they're genuinely the bottom tier, same as the hand-
        // tuned WOODEN_SWORD/WOODEN_PICKAXE/WOODEN_AXE) so they're excluded
        // here - every tier above wood should clearly out-price the default.
        Map<Material, Long> prices = MarketBotManager.getBasePrices();
        Material[] mustNotBeFlatDefault = {
                Material.STONE_HOE, Material.IRON_HOE, Material.GOLDEN_HOE, Material.DIAMOND_HOE,
                Material.STONE_SHOVEL, Material.IRON_SHOVEL, Material.GOLDEN_SHOVEL,
                Material.DIAMOND_SHOVEL, Material.NETHERITE_AXE, Material.NETHERITE_HOE, Material.NETHERITE_SHOVEL,
                Material.BEACON, Material.END_CRYSTAL, Material.ENCHANTING_TABLE, Material.DRAGON_HEAD,
                Material.WITHER_SKELETON_SKULL
        };
        for (Material material : mustNotBeFlatDefault) {
            Long price = prices.get(material);
            assertTrue(price != null && price > 15,
                    material + " is priced at " + price + " - looks like it fell through to the flat catch-all");
        }
    }

    /**
     * The user's explicit, non-negotiable requirement: a full stack of 64
     * Heavy Cores must price at a minimum of 73,000,000 coins EVEN AT THE
     * WORST-CASE roll - lowest price variance (-15%) and, since Heavy
     * Core/Mace clamp the inflation multiplier's floor to 1.0x (see
     * refresh()'s effectiveMultiplier), the lowest multiplier they can ever
     * actually see. A plain BASE_PRICES unit price alone can't guarantee
     * this once inflation scaling exists - this test is what caught that
     * the first version of the inflation system could let a stack drop to
     * ~36.7M whenever the server's coin supply shrank below baseline.
     */
    @Test
    void heavyCoreStackNeverDropsBelowSeventyThreeMillion() {
        long unitPrice = MarketBotManager.getBasePrices().get(Material.HEAVY_CORE);
        double worstCaseVariance = 0.85;
        double worstCaseMultiplierForHeavyCore = 1.0; // clamped floor, see refresh()
        long worstCaseStackPrice = Math.round(unitPrice * 64 * worstCaseVariance * worstCaseMultiplierForHeavyCore);
        assertTrue(worstCaseStackPrice >= 73_000_000L,
                "A stack of 64 Heavy Cores can drop to " + worstCaseStackPrice
                        + " under worst-case variance/inflation - must never be able to go under 73,000,000");
    }

    @Test
    void maceIsPricedAtLeastAsHighAsHeavyCore() {
        // A Mace is a Heavy Core plus a Breeze Rod and crafting effort, so
        // it should never be worth less than the Heavy Core it's made from.
        Map<Material, Long> prices = MarketBotManager.getBasePrices();
        long heavyCore = prices.get(Material.HEAVY_CORE);
        long mace = prices.get(Material.MACE);
        assertTrue(mace >= heavyCore, "MACE (" + mace + ") is priced below HEAVY_CORE (" + heavyCore + ")");
    }

    @Test
    void oreTierPricesIncreaseWithRarity() {
        // Spot-checks the tier table actually produces a sane rarity
        // ordering rather than, say, netherite accidentally being cheaper
        // than coal because of a substring-match collision.
        Map<Material, Long> prices = MarketBotManager.getBasePrices();
        assertTrue(prices.get(Material.DEEPSLATE_DIAMOND_ORE) > prices.get(Material.DEEPSLATE_IRON_ORE));
        assertTrue(prices.get(Material.DEEPSLATE_IRON_ORE) > prices.get(Material.DEEPSLATE_COAL_ORE));
        assertTrue(prices.get(Material.ANCIENT_DEBRIS) > prices.get(Material.DIAMOND_ORE));
        assertTrue(prices.get(Material.RAW_GOLD_BLOCK) > prices.get(Material.RAW_IRON_BLOCK));
    }

    @Test
    void noDuplicateListingWouldEverCollideWithABlockedMaterial() {
        // Belt-and-suspenders: nothing in any of BuildingBlockShop's three
        // scanned lists is also in BLOCKED - the scan's own banned set
        // should already guarantee this, but a drift between the two
        // banned lists (BuildingBlockShop's vs MarketBotManager's) would
        // otherwise go unnoticed.
        for (Material material : BuildingBlockShop.ORES_AND_VALUABLES) {
            assertFalse(BLOCKED.contains(material), material + " is both scanned as an ore/valuable and blocked");
        }
        for (Material material : BuildingBlockShop.ARMOR_TRIMS) {
            assertFalse(BLOCKED.contains(material), material + " is both scanned as a trim and blocked");
        }
        for (Material material : BuildingBlockShop.EVERYTHING_ELSE) {
            assertFalse(BLOCKED.contains(material), material + " is both scanned into EVERYTHING_ELSE and blocked");
        }
    }

    @Test
    void scannedListsAreNotSuspiciouslyEmpty() {
        // If BuildingBlockShop's static initializer ever stops running
        // before these are read (a class-loading-order regression), every
        // other test in this file would pass vacuously while the live
        // server silently stocked almost nothing. Catches that directly.
        if (BuildingBlockShop.ORES_AND_VALUABLES.isEmpty()) fail("ORES_AND_VALUABLES is empty - scan didn't run");
        if (BuildingBlockShop.ARMOR_TRIMS.isEmpty()) fail("ARMOR_TRIMS is empty - scan didn't run");
        if (BuildingBlockShop.EVERYTHING_ELSE.size() < 100) {
            fail("EVERYTHING_ELSE has only " + BuildingBlockShop.EVERYTHING_ELSE.size()
                    + " material(s) - expected hundreds; the scan likely didn't run");
        }
    }
}
