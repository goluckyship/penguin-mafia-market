package net.penguinmafia.market;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * One-time cleanup for the Heavy Core pricing bug: an earlier build briefly
 * let Heavy Core (and, by extension, any Mace crafted from one at a
 * smithing table) sell through /bm's "everything else" catch-all for 15
 * coins, before it was caught and repriced to a fair ~1.35 million.
 *
 * This only confiscates Heavy Core/Mace from players the TransactionLedger
 * actually shows bought Heavy Core from the Black Market Dealer BEFORE the
 * fix's cutoff timestamp - never from a player who got one legitimately (a
 * real Trial Chamber vault drop, or a purchase after this fix at the new
 * fair price). That cutoff is recorded once, the first time this class ever
 * runs, and reused on every later restart - it does NOT slide forward - so
 * a player who buys a Heavy Core next week at the corrected price is never
 * retroactively flagged just because the ledger now contains their
 * purchase too. Tracked in contraband_sweep.yml, which also records which
 * players have already been swept, so it only ever runs once per player.
 *
 * Caveat: the ledger only keeps its most recent entries server-wide, so a
 * glitched purchase old enough to have scrolled out of it won't be caught
 * here - this is a best-effort sweep against what's still on record, not a
 * guarantee every glitched Heavy Core gets found.
 */
public class ContrabandSweep implements Listener {

    private static final Material[] CONFISCATED = { Material.HEAVY_CORE, Material.MACE };

    private final PenguinMafiaMarket plugin;
    private final Set<UUID> flaggedBuyers;
    private final File file;
    private final YamlConfiguration config;

    public ContrabandSweep(PenguinMafiaMarket plugin, TransactionLedger ledger) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "contraband_sweep.yml");
        this.config = YamlConfiguration.loadConfiguration(file);

        long cutoff = config.getLong("_cutoff_millis", -1L);
        if (cutoff <= 0) {
            // First time this fix has ever run - everything bought up to
            // right now is suspect, nothing bought after ever will be.
            cutoff = System.currentTimeMillis();
            config.set("_cutoff_millis", cutoff);
            save();
        }

        this.flaggedBuyers = ledger.getBuyersOf(MarketBotManager.SELLER_ID, "heavy core", cutoff);
        plugin.getLogger().info("Contraband sweep: " + flaggedBuyers.size()
                + " player(s) on record as having bought Heavy Core from the Black Market Dealer before the fix.");
    }

    /** Runs once at startup for anyone already online when this fix is deployed. */
    public void sweepOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sweep(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sweep(event.getPlayer());
    }

    private void sweep(Player player) {
        UUID id = player.getUniqueId();
        if (!flaggedBuyers.contains(id)) return; // never bought Heavy Core from the dealer - nothing to claw back

        String key = id.toString();
        if (config.getBoolean(key, false)) return; // already swept this player once

        PlayerInventory inv = player.getInventory();
        int removed = 0;

        ItemStack[] main = inv.getContents();
        int removedMain = strip(main);
        if (removedMain > 0) inv.setContents(main);
        removed += removedMain;

        ItemStack[] armor = inv.getArmorContents();
        int removedArmor = strip(armor);
        if (removedArmor > 0) inv.setArmorContents(armor);
        removed += removedArmor;

        ItemStack offhand = inv.getItemInOffHand();
        if (isContraband(offhand)) {
            removed += offhand.getAmount();
            inv.setItemInOffHand(null);
        }

        Inventory enderChest = player.getEnderChest();
        ItemStack[] enderContents = enderChest.getContents();
        int removedEnder = strip(enderContents);
        if (removedEnder > 0) enderChest.setContents(enderContents);
        removed += removedEnder;

        config.set(key, true);
        save();

        if (removed > 0) {
            player.sendMessage(ChatColor.RED + "Our records show you bought a Heavy Core from the Black Market "
                    + "Dealer while it was mispriced. " + removed + " Heavy Core/Mace item(s) were removed from "
                    + "your inventory as a result - sorry for the inconvenience.");
            plugin.getLogger().info("Contraband sweep: removed " + removed
                    + " Heavy Core/Mace item(s) from " + player.getName());
        }
    }

    private int strip(ItemStack[] contents) {
        int removed = 0;
        for (int i = 0; i < contents.length; i++) {
            if (isContraband(contents[i])) {
                removed += contents[i].getAmount();
                contents[i] = null;
            }
        }
        return removed;
    }

    private boolean isContraband(ItemStack item) {
        if (item == null) return false;
        for (Material bad : CONFISCATED) {
            if (item.getType() == bad) return true;
        }
        return false;
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save contraband_sweep.yml: " + e.getMessage());
        }
    }
}
