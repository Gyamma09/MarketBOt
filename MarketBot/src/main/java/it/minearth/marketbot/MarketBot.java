package it.tuoserver.marketbot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * MarketBot — Plugin Paper 1.21.1
 * Legge i prezzi da EconomyShopGUI Premium e li manda su Discord via DiscordSRV
 * ogni N ore (configurabile), mostrando la variazione rispetto all'aggiornamento precedente.
 */
public class MarketBot extends JavaPlugin {

    private PriceStorage storage;
    private MarketTask marketTask;

    @Override
    public void onEnable() {
        // Salva il config.yml di default se non esiste
        saveDefaultConfig();

        // Controlla dipendenze
        if (getServer().getPluginManager().getPlugin("EconomyShopGUI-Premium") == null) {
            getLogger().severe("EconomyShopGUI-Premium non trovato! Disabilito MarketBot.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (getServer().getPluginManager().getPlugin("DiscordSRV") == null) {
            getLogger().severe("DiscordSRV non trovato! Disabilito MarketBot.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        storage = new PriceStorage(this);

        scheduleTask();

        getLogger().info("MarketBot avviato! Aggiornamento ogni " +
                getConfig().getInt("update-interval-hours", 3) + " ore.");
    }

    @Override
    public void onDisable() {
        if (marketTask != null && !marketTask.isCancelled()) {
            marketTask.cancel();
        }
        getLogger().info("MarketBot disabilitato.");
    }

    private void scheduleTask() {
        if (marketTask != null && !marketTask.isCancelled()) {
            marketTask.cancel();
        }

        long intervalHours = getConfig().getLong("update-interval-hours", 3);
        // Converti ore in tick (1 secondo = 20 tick)
        long intervalTicks = intervalHours * 60 * 60 * 20;

        marketTask = new MarketTask(this, storage);
        // Primo aggiornamento dopo 1 minuto dal boot, poi ogni N ore
        marketTask.runTaskTimer(this, 20 * 60, intervalTicks);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!command.getName().equalsIgnoreCase("updatemarket")) return false;

        if (!sender.hasPermission("marketbot.update")) {
            sender.sendMessage("§cNon hai il permesso per usare questo comando.");
            return true;
        }

        sender.sendMessage("§aInvio aggiornamento prezzi su Discord...");
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            marketTask.sendMarketUpdate();
            sender.sendMessage("§aAggiornamento inviato!");
        });
        return true;
    }

    /** Ricarica config e ripianifica il task (utile se cambi l'intervallo) */
    public void reloadPlugin() {
        reloadConfig();
        storage.reload();
        scheduleTask();
    }
}
