package net.penguinmafia.market;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PenguinMafiaMarket extends JavaPlugin {

    private Economy economy;
    private MarketManager marketManager;
    private boolean datapackNeedsRestart = false;

    @Override
    public void onLoad() {
        // Runs before the world loads, so if the bundled data pack (custom
        // biomes, structures, mobs) isn't installed yet, this is the last
        // chance to drop it in before Minecraft reads world/datapacks/ for
        // this boot.
        datapackNeedsRestart = DatapackInstaller.installIfNeeded(this);
    }

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        this.economy = new Economy(this);
        this.marketManager = new MarketManager(this, economy);

        MarketCommand command = new MarketCommand(this, economy, marketManager);
        getCommand("bm").setExecutor(command);
        getCommand("bm").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(new MarketGUIListener(marketManager, economy), this);

        getLogger().info("Penguin Mafia Black Market enabled. " + marketManager.getListingCount() + " listings loaded.");

        if (datapackNeedsRestart) {
            getLogger().warning("==================================================================");
            getLogger().warning("Penguin Mafia data pack was just installed into world/datapacks/.");
            getLogger().warning("Minecraft only loads new biomes/structures at world startup, so");
            getLogger().warning("please STOP and START (not /reload) this server ONE more time for");
            getLogger().warning("the custom biomes, mobs and structures to take effect.");
            getLogger().warning("==================================================================");
            getServer().getPluginManager().registerEvents(new RestartReminder(), this);
        }
    }

    private class RestartReminder implements Listener {
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            if (player.isOp()) {
                player.sendMessage("§6[Penguin Mafia] §7The bundled data pack was just installed - "
                        + "please restart the server once (not /reload) so the custom biomes take effect.");
            }
        }
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
