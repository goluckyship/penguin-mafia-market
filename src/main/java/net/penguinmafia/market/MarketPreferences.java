package net.penguinmafia.market;

/** A single player's remembered market search/sort/category settings. */
public class MarketPreferences {

    public String filter;
    public boolean sortDescending;
    public MarketCategory category;

    public MarketPreferences() {
        this(null, false, MarketCategory.ALL);
    }

    public MarketPreferences(String filter, boolean sortDescending, MarketCategory category) {
        this.filter = filter;
        this.sortDescending = sortDescending;
        this.category = category;
    }
}
