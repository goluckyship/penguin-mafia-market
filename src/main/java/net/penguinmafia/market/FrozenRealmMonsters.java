package net.penguinmafia.market;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
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
 * Frozen Reavers: a rare, tougher variant of the ordinary hostile mobs that
 * spawn naturally in the Frozen Realm. Nowhere else on the server has
 * monsters that drop Frozen Coins - this is the one place it happens, which
 * is the point: it gives players a reason to go out and fight there, not
 * just visit for the cabins/bridges.
 *
 * A Reaver is just a normal hostile mob (skeleton, zombie, stray, whatever
 * naturally spawned) picked at spawn time, tagged with a
 * PersistentDataContainer marker, renamed, buffed, and given icy gear so it
 * reads as special at a glance. The tag is what EntityDeathEvent actually
 * checks - nothing else about the mob (name, gear) is load-bearing, so a
 * resource-pack change or a plugin that strips custom names can't break the
 * coin drop.
 */
public class FrozenRealmMonsters implements Listener {

    private static final NamespacedKey FROZEN_REALM_KEY = NamespacedKey.fromString("penguinmafia:frozen_realm");

    /** Fraction of natural hostile spawns in the Frozen Realm that become Reavers. */
    private static final double SPAWN_CHANCE = 0.12;

    private static final long MIN_COINS = 15L;
    private static final long MAX_COINS = 50L;

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
        if (!(event.getEntity() instanceof Monster monster)) return;

        if (random.nextDouble() >= SPAWN_CHANCE) return;

        makeReaver(monster);
    }

    private void makeReaver(Monster monster) {
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

        int amount = (int) (MIN_COINS + random.nextInt((int) (MAX_COINS - MIN_COINS + 1)));
        event.getDrops().add(economy.coinItem(amount));

        Player killer = entity.getKiller();
        if (killer != null) {
            killer.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Frozen Reaver defeated!"
                    + ChatColor.RESET + ChatColor.GRAY + " Dropped " + amount + " Frozen Coins.");
        }
    }
}
