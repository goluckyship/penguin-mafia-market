package net.penguinmafia.market;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class PenguinMafiaMarket extends JavaPlugin {

    private Economy economy;
    private MarketManager marketManager;
    private AnnouncementManager announcementManager;
    private PlaytimeRewardManager playtimeRewardManager;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        this.economy = new Economy(this);
        this.marketManager = new MarketManager(this, economy);

        MarketPreferencesManager marketPreferencesManager = new MarketPreferencesManager(this);
        MarketGUI marketGUI = new MarketGUI(marketManager, marketPreferencesManager);

        MarketCommand command = new MarketCommand(this, economy, marketManager, marketGUI);
        getCommand("bm").setExecutor(command);
        getCommand("bm").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(
                new MarketGUIListener(this, marketManager, economy, marketGUI, marketPreferencesManager), this);

        getCommand("coins").setExecutor(new CoinsCommand(economy));

        BalanceCommand balanceCommand = new BalanceCommand(economy);
        getCommand("bal").setExecutor(balanceCommand);
        getCommand("bal").setTabCompleter(balanceCommand);

        PayCommand payCommand = new PayCommand(economy);
        getCommand("pay").setExecutor(payCommand);
        getCommand("pay").setTabCompleter(payCommand);

        getCommand("coinflip").setExecutor(new CoinflipCommand(economy));

        this.announcementManager = new AnnouncementManager(this);
        AnnouncementCommand announcementCommand = new AnnouncementCommand(announcementManager);
        getCommand("pa").setExecutor(announcementCommand);
        getCommand("pa").setTabCompleter(announcementCommand);

        this.playtimeRewardManager = new PlaytimeRewardManager(this, economy);
        getCommand("ptr").setExecutor(new PlaytimeRewardCommand(playtimeRewardManager));

        FrozenRealmStructures frozenRealmStructures = new FrozenRealmStructures(economy,
                new File(getDataFolder(), "frozen_realm_salts.properties"));
        getCommand("frozenrealm").setExecutor(new FrozenRealmCommand(frozenRealmStructures));
        getServer().getPluginManager().registerEvents(frozenRealmStructures, this);
        getServer().getPluginManager().registerEvents(new FrozenRealmMonsters(this, economy), this);
        FrozenRealmTimeLock.start(this);

        SandboxCommand.createOrLoad(this);
        getCommand("sandbox").setExecutor(new SandboxCommand());

        getCommand("autodeposit").setExecutor(new AutoDepositCommand(economy));
        AutoDepositManager.start(this, economy);

        AfkFarmManager afkFarmManager = new AfkFarmManager(this, economy);
        getServer().getPluginManager().registerEvents(afkFarmManager, this);
        getCommand("afk").setExecutor(new AfkCommand(afkFarmManager));

        GeneratorManager generatorManager = new GeneratorManager(this, economy);
        GeneratorCommand generatorCommand = new GeneratorCommand(generatorManager);
        getCommand("gen").setExecutor(generatorCommand);
        getCommand("gen").setTabCompleter(generatorCommand);

        getCommand("chat").setExecutor(new ChatCommand(economy));

        ShopGUI shopGUI = new ShopGUI(economy);
        getServer().getPluginManager().registerEvents(new ShopGUIListener(shopGUI), this);
        getCommand("shop").setExecutor(new ShopCommand(shopGUI));

        getCommand("baltop").setExecutor(new BalTopCommand(economy));

        InfoGUI infoGUI = new InfoGUI(this);
        getServer().getPluginManager().registerEvents(infoGUI, this);
        getCommand("info").setExecutor(new InfoCommand(infoGUI));

        getLogger().info("Penguin Mafia Black Market enabled. " + marketManager.getListingCount() + " listings loaded.");
    }

    @Override
    public void onDisable() {
        if (economy != null) economy.save();
        if (marketManager != null) marketManager.save();
        if (announcementManager != null) announcementManager.shutdown();
        if (playtimeRewardManager != null) playtimeRewardManager.shutdown();
    }

    public Economy getEconomy() {
        return economy;
    }

    public MarketManager getMarketManager() {
        return marketManager;
    }
}
