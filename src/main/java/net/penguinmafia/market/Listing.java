package net.penguinmafia.market;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Listing {
    public final int id;
    public final UUID seller;
    public final String sellerName;
    public final ItemStack item;
    /**
     * Not final: the Black Market Dealer tops up its own listings in place
     * (adding quantity and the matching price) instead of deleting and
     * recreating them on every restock - see MarketBotManager.refresh().
     */
    public long price;

    public Listing(int id, UUID seller, String sellerName, ItemStack item, long price) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.item = item;
        this.price = price;
    }
}
