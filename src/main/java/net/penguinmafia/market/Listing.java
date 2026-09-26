package net.penguinmafia.market;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Listing {
    public final int id;
    public final UUID seller;
    public final String sellerName;
    public final ItemStack item;
    public final long price;

    public Listing(int id, UUID seller, String sellerName, ItemStack item, long price) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.item = item;
        this.price = price;
    }
}
