package net.penguinmafia.market;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * One listing in the Auction House - distinct from a Black Market Listing in
 * that it runs for a fixed time and sells to the highest bidder instead of
 * the first buyer at a fixed price. currentBid/currentBidderId/
 * currentBidderName stay null/zero until someone places the first bid.
 */
public class Auction {
    public final int id;
    public final UUID sellerId;
    public final String sellerName;
    public final ItemStack item;
    public final long startingPrice;
    public final long endTimeMillis;

    public long currentBid;
    public UUID currentBidderId;
    public String currentBidderName;

    public Auction(int id, UUID sellerId, String sellerName, ItemStack item, long startingPrice, long endTimeMillis) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.item = item;
        this.startingPrice = startingPrice;
        this.endTimeMillis = endTimeMillis;
        this.currentBid = 0L;
        this.currentBidderId = null;
        this.currentBidderName = null;
    }

    public boolean hasBid() {
        return currentBidderId != null;
    }

    /** The price a new bid has to beat - the starting price if nothing's been bid yet, otherwise the current bid. */
    public long priceToBeat() {
        return hasBid() ? currentBid : startingPrice;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= endTimeMillis;
    }

    public long millisRemaining() {
        return Math.max(0L, endTimeMillis - System.currentTimeMillis());
    }
}
