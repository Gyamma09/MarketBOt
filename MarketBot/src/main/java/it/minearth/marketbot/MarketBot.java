package it.minearth.marketbot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * MarketBot v1.0.1
 */
public class MarketBot extends JavaPlugin {

    private PriceStorage storage;
    private MarketTask marketTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

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

        getLogger().info("MarketBot v1.0.1 avviato! Aggiornamento ogni " +
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
        long intervalTicks = intervalHours * 60 * 60 * 20;

        marketTask = new MarketTask(this, storage);
        marketTask.runTaskTimer(this, 20 * 60, intervalTicks);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        switch (command.getName().toLowerCase()) {

            case "updatemarket" -> {
                if (!sender.hasPermission("marketbot.update")) {
                    sender.sendMessage("§cNon hai il permesso per usare questo comando.");
                    return true;
                }
                sender.sendMessage("§aInvio aggiornamento prezzi su Discord...");
                getServer().getScheduler().runTaskAsynchronously(this, () -> {
                    marketTask.sendMarketUpdate();
                    sender.sendMessage("§aAggiornamento inviato!");
                });
            }

            case "marketbot" -> {
                if (args.length == 0) {
                    sender.sendMessage("§6MarketBot §7v1.0.1");
                    sender.sendMessage("§7/marketbot reload §8— §fRicarica config e ripianifica il task");
                    sender.sendMessage("§7/updatemarket §8— §fForza aggiornamento su Discord");
                    return true;
                }
                if (args[0].equalsIgnoreCase("reload")) {
                    if (!sender.hasPermission("marketbot.reload")) {
                        sender.sendMessage("§cNon hai il permesso per usare questo comando.");
                        return true;
                    }
                    reloadPlugin();
                    sender.sendMessage("§aMarketBot ricaricato! Intervallo: §f" +
                            getConfig().getInt("update-interval-hours", 3) + "h");
                }
            }
        }

        return true;
    }

    public void reloadPlugin() {
        reloadConfig();
        storage.reload();
        scheduleTask();
        getLogger().info("MarketBot ricaricato.");
    }
}
