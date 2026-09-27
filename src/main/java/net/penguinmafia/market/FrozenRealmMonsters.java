package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

/**
 * Frozen Reavers: the only mob that naturally spawns in the Frozen Realm.
 * Nowhere else on the server has monsters that drop Frozen Coins - this is
 * the one place it happens, which is the point: it gives players a reason
 * to go out and fight there, not just visit for the cabins/bridges.
 *
 * The Frozen Wastes biome's spawn table (see the penguin_frozen_realm
 * datapack) is restricted to husks only, and every natural husk spawn here
 * becomes a Reaver - tagged with a PersistentDataContainer marker, renamed,
 * buffed, and given icy gear so it reads as special at a glance. The tag is
 * what EntityDeathEvent actually checks - nothing else about the mob (name,
 * gear) is load-bearing, so a resource-pack change or a plugin that strips
 * custom names can't break the coin drop. Restricting to Husk here too
 * (not just "any Monster") means a stray egg-spawned or summoned mob of a
 * different type in this world still won't turn into a Reaver.
 */
public class FrozenRealmMonsters implements Listener {

    private static final NamespacedKey FROZEN_REALM_KEY = NamespacedKey.fromString("penguinmafia:frozen_realm");

    private static final long MIN_COINS = 15L;
    private static final long MAX_COINS = 50L;

    /**
     * Hard cap on how many Frozen Reavers can exist in the dimension at once.
     * Without this, a husk-only biome with nothing else competing for the
     * mob cap just keeps stacking packs on top of each other near players
     * (each one glowing, permanently sped up, and never despawning while
     * someone's nearby) until the server chokes on the entity count and
     * everything starts stuttering/rubber-banding. Once at the cap, new
     * natural spawns in the dimension are cancelled outright until some
     * Reavers are killed or wander off and despawn.
     */
    private static final int MAX_REAVERS = 24;

    private final Economy economy;
    private final NamespacedKey reaverKey;
    private final Random random = new Random();

    public FrozenRealmMonsters(PenguinMafiaMarket plugin, Economy economy) {
        this.economy = economy;
        this.reaverKey = new NamespacedKey(plugin, "frozen_reaver");
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        World world = event.getLocation().getWorld();
        if (world == null || FROZEN_REALM_KEY == null || !FROZEN_REALM_KEY.equals(world.getKey())) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (!(event.getEntity() instanceof Husk husk)) return;

        if (countReavers(world) >= MAX_REAVERS) {
            event.setCancelled(true);
            return;
        }

        makeReaver(husk);
    }

    private int countReavers(World world) {
        int count = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof LivingEntity living
                    && living.getPersistentDataContainer().has(reaverKey, PersistentDataType.BYTE)) {
                count++;
            }
        }
        return count;
    }

    private void makeReaver(Husk monster) {
        monster.getPersistentDataContainer().set(reaverKey, PersistentDataType.BYTE, (byte) 1);
        monster.setCustomName(ChatColor.AQUA + "" + ChatColor.BOLD + "Frozen Reaver");
        monster.setCustomNameVisible(true);
        monster.setGlowing(true);

        // Tougher than a normal spawn of the same type, so the coin payout
        // feels earned rather than free.
        AttributeInstance maxHealth = monster.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealth.getBaseValue() * 2.5);
            monster.setHealth(maxHealth.getValue());
        }
        AttributeInstance attackDamage = monster.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.setBaseValue(attackDamage.getBaseValue() * 1.5);
        }
        monster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, true, false));

        // Icy-themed gear so it's obviously not an ordinary mob. Drop chance
        // 0 so killing one doesn't also hand out free armor.
        EntityEquipment equipment = monster.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(icyLeather(Material.LEATHER_HELMET));
            equipment.setChestplate(icyLeather(Material.LEATHER_CHESTPLATE));
            equipment.setHelmetDropChance(0f);
            equipment.setChestplateDropChance(0f);
        }
    }

    private ItemStack icyLeather(Material piece) {
        ItemStack item = new ItemStack(piece);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(Color.fromRGB(0xAEEFFF));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.getPersistentDataContainer().has(reaverKey, PersistentDataType.BYTE)) return;

        // Reavers are a coin source, not a loot mob - strip whatever vanilla
        // husk drops (rotten flesh, the rare carrot/potato/iron ingot) would
        // otherwise be queued up, so Frozen Coins are the only thing that hits
        // the ground.
        event.getDrops().clear();

        int amount = (int) (MIN_COINS + random.nextInt((int) (MAX_COINS - MIN_COINS + 1)));
        event.getDrops().add(economy.coinItem(amount));

        Player killer = entity.getKiller();
        if (killer != null) {
            killer.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Frozen Reaver defeated!"
                    + ChatColor.RESET + ChatColor.GRAY + " Dropped " + amount + " Frozen Coins.");
        }
    }
}
