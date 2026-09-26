package net.penguinmafia.market;

import org.bukkit.plugin.java.JavaPlugin;

public final class PenguinMafiaMarket extends JavaPlugin {

    private Economy economy;
    private MarketManager marketManager;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        this.economy = new Economy(this);
        this.marketManager = new MarketManager(this, economy);

        MarketCommand command = new MarketCommand(this, economy, marketManager);
        getCommand("bm").setExecutor(command);
        getCommand("bm").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(new MarketGUIListener(marketManager, economy), this);

        getCommand("coins").setExecutor(new CoinsCommand(economy));

        BalanceCommand balanceCommand = new BalanceCommand(economy);
        getCommand("bal").setExecutor(balanceCommand);
        getCommand("bal").setTabCompleter(balanceCommand);

        getLogger().info("Penguin Mafia Black Market enabled. " + marketManager.getListingCount() + " listings loaded.");
    }

    @Override
    public void onDisable() {
        if (economy != null) economy.save();
        if (marketManager != null) marketManager.save();
    }

    public Economy getEconomy() {
        return economy;
    }

    public MarketManager getMarketManager() {
        return marketManager;
    }
}
