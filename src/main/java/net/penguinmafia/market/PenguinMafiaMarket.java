package net.penguinmafia.market;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class PenguinMafiaMarket extends JavaPlugin {

    private Economy economy;
    private MarketManager marketManager;
    private AnnouncementManager announcementManager;
    private PlaytimeRewardManager playtimeRewardManager;
    private JobsManager jobsManager;
    private AuctionManager auctionManager;
    private ModerationManager moderationManager;
    private BountyManager bountyManager;
    private TransactionLedger transactionLedger;
    private WarehouseManager warehouseManager;
    private DailyRewardManager dailyRewardManager;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        this.economy = new Economy(this);
        this.marketManager = new MarketManager(this, economy);
        this.transactionLedger = new TransactionLedger(this);
        this.marketManager.setLedger(transactionLedger);
        MarketBotManager marketBotManager = MarketBotManager.start(this, marketManager);

        MarketPreferencesManager marketPreferencesManager = new MarketPreferencesManager(this);
        MarketGUI marketGUI = new MarketGUI(marketManager, marketPreferencesManager);

        MarketCommand command = new MarketCommand(this, economy, marketManager, marketGUI, marketBotManager, transactionLedger);
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

        this.jobsManager = new JobsManager(this);
        getServer().getPluginManager().registerEvents(new JobsListener(jobsManager, economy), this);
        JobsCommand jobsCommand = new JobsCommand(jobsManager);
        getCommand("job").setExecutor(jobsCommand);
        getCommand("job").setTabCompleter(jobsCommand);

        this.auctionManager = AuctionManager.start(this, economy);
        getServer().getPluginManager().registerEvents(auctionManager, this);
        AuctionCommand auctionCommand = new AuctionCommand(auctionManager);
        getCommand("auction").setExecutor(auctionCommand);
        getCommand("auction").setTabCompleter(auctionCommand);

        this.moderationManager = new ModerationManager(this);
        getServer().getPluginManager().registerEvents(new ModerationListener(moderationManager), this);
        ModerationCommand moderationCommand = new ModerationCommand(moderationManager);
        getCommand("mod").setExecutor(moderationCommand);
        getCommand("mod").setTabCompleter(moderationCommand);

        this.bountyManager = new BountyManager(this, economy);
        getServer().getPluginManager().registerEvents(bountyManager, this);
        BountyCommand bountyCommand = new BountyCommand(bountyManager);
        getCommand("bounty").setExecutor(bountyCommand);
        getCommand("bounty").setTabCompleter(bountyCommand);

        this.warehouseManager = new WarehouseManager(this, economy);
        WarehouseGUIListener warehouseGUIListener = new WarehouseGUIListener(warehouseManager);
        getServer().getPluginManager().registerEvents(warehouseGUIListener, this);
        WarehouseCommand warehouseCommand = new WarehouseCommand(warehouseManager, warehouseGUIListener);
        getCommand("warehouse").setExecutor(warehouseCommand);
        getCommand("warehouse").setTabCompleter(warehouseCommand);

        this.dailyRewardManager = new DailyRewardManager(this, economy);
        DailyRewardCommand dailyRewardCommand = new DailyRewardCommand(dailyRewardManager);
        getCommand("daily").setExecutor(dailyRewardCommand);
        getCommand("daily").setTabCompleter(dailyRewardCommand);

        AuraManager auraManager = AuraManager.start(this);
        getServer().getPluginManager().registerEvents(auraManager, this);
        AuraCommand auraCommand = new AuraCommand(auraManager);
        getCommand("aura").setExecutor(auraCommand);
        getCommand("aura").setTabCompleter(auraCommand);

        ContrabandSweep contrabandSweep = new ContrabandSweep(this);
        contrabandSweep.sweepOnlinePlayers();
        getServer().getPluginManager().registerEvents(contrabandSweep, this);

        getLogger().info("Penguin Mafia Black Market enabled. " + marketManager.getListingCount() + " listings loaded.");
    }

    @Override
    public void onDisable() {
        if (economy != null) economy.save();
        // saveNow(), not save() - Bukkit won't run a newly-scheduled async task
        // (what the normal save() path defers to) once the plugin is disabling,
        // so this has to write synchronously right here or the last few moments
        // of market activity would be lost on every shutdown/restart.
        if (marketManager != null) marketManager.saveNow();
        if (announcementManager != null) announcementManager.shutdown();
        if (playtimeRewardManager != null) playtimeRewardManager.shutdown();
        if (jobsManager != null) jobsManager.save();
        if (auctionManager != null) auctionManager.save();
        if (moderationManager != null) moderationManager.save();
        if (bountyManager != null) bountyManager.save();
        if (warehouseManager != null) warehouseManager.save();
        if (dailyRewardManager != null) dailyRewardManager.save();
    }

    public Economy getEconomy() {
        return economy;
    }

    public MarketManager getMarketManager() {
        return marketManager;
    }
}
