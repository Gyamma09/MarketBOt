package it.minearth.marketbot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class MarketBot extends JavaPlugin {

    private PriceStorage storage;
    private MarketTask marketTask;
    private CrashCheckTask crashCheckTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (getServer().getPluginManager().getPlugin("EconomyShopGUI-Premium") == null) {
            getLogger().severe("EconomyShopGUI-Premium non trovato! Disabilito MarketBot.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        storage = new PriceStorage(this);
        scheduleMarketTask();
        scheduleCrashTask();

        getLogger().info("MarketBot v1.0.2 avviato!");
        getLogger().info("  Aggiornamento mercato ogni " + getConfig().getInt("update-interval-hours", 2) + " ore.");
        getLogger().info("  Controllo crash/boom ogni " + getConfig().getInt("crash-check-minutes", 10) + " minuti.");
    }

    @Override
    public void onDisable() {
        if (marketTask != null && !marketTask.isCancelled()) marketTask.cancel();
        if (crashCheckTask != null && !crashCheckTask.isCancelled()) crashCheckTask.cancel();
        getLogger().info("MarketBot disabilitato.");
    }

    private void scheduleMarketTask() {
        if (marketTask != null && !marketTask.isCancelled()) marketTask.cancel();

        long intervalHours = getConfig().getLong("update-interval-hours", 2);
        long intervalTicks = intervalHours * 60 * 60 * 20;

        marketTask = new MarketTask(this, storage);
        // Primo aggiornamento dopo 1 minuto dal boot
        marketTask.runTaskTimer(this, 20 * 60, intervalTicks);
    }

    private void scheduleCrashTask() {
        if (crashCheckTask != null && !crashCheckTask.isCancelled()) crashCheckTask.cancel();

        long intervalMinutes = getConfig().getLong("crash-check-minutes", 10);
        long intervalTicks = intervalMinutes * 60 * 20;

        crashCheckTask = new CrashCheckTask(this, storage);
        // Primo controllo dopo 2 minuti dal boot
        crashCheckTask.runTaskTimer(this, 20 * 60 * 2, intervalTicks);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        switch (command.getName().toLowerCase()) {

            case "updatemarket" -> {
                if (!sender.hasPermission("marketbot.update")) {
                    sender.sendMessage("§cNon hai il permesso.");
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
                    sender.sendMessage("§6MarketBot §7v1.0.2");
                    sender.sendMessage("§7/marketbot reload §8— §fRicarica config");
                    sender.sendMessage("§7/updatemarket §8— §fForza aggiornamento Discord");
                    return true;
                }
                if (args[0].equalsIgnoreCase("reload")) {
                    if (!sender.hasPermission("marketbot.reload")) {
                        sender.sendMessage("§cNon hai il permesso.");
                        return true;
                    }
                    reloadPlugin();
                    sender.sendMessage("§aMarketBot ricaricato!");
                }
            }
        }

        return true;
    }

    public void reloadPlugin() {
        reloadConfig();
        storage.reload();
        scheduleMarketTask();
        scheduleCrashTask();
        getLogger().info("MarketBot ricaricato.");
    }

    public PriceStorage getStorage() {
        return storage;
    }
}
