package it.minearth.marketbot;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.objects.ShopItem;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MarketTask extends BukkitRunnable {

    private final MarketBot plugin;
    private final PriceStorage storage;

    public MarketTask(MarketBot plugin, PriceStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public void run() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::sendMarketUpdate);
    }

    public void sendMarketUpdate() {
        String channelId = plugin.getConfig().getString("discord-channel-id", "");
        List<String> items = plugin.getConfig().getStringList("items");

        if (channelId.isEmpty()) {
            plugin.getLogger().warning("discord-channel-id non configurato in config.yml!");
            return;
        }

        TextChannel channel = DiscordSRV.getPlugin()
                .getJda()
                .getTextChannelById(channelId);

        if (channel == null) {
            plugin.getLogger().warning("Canale Discord non trovato con ID: " + channelId);
            return;
        }

        StringBuilder table = new StringBuilder();
        table.append(String.format("%-14s %8s %8s %8s %8s%n",
                "Item", "Vendita", "Var.", "Acquisto", "Var."));
        table.append("─".repeat(54)).append("\n");

        int positiveCount = 0;
        int negativeCount = 0;
        List<ItemResult> results = new ArrayList<>();

        for (String itemName : items) {
            Material material = Material.matchMaterial(itemName);
            if (material == null) {
                plugin.getLogger().warning("Material non trovato: " + itemName);
                continue;
            }

            // Cerca lo ShopItem tramite il nome (stringa)
            ShopItem shopItem = EconomyShopGUIHook.getShopItem(itemName);
            if (shopItem == null) {
                plugin.getLogger().warning("Item non presente nello shop: " + itemName);
                continue;
            }

            double currentSell = EconomyShopGUIHook.getItemSellPrice(shopItem, null);
            double currentBuy  = EconomyShopGUIHook.getItemBuyPrice(shopItem, null);

            double lastSell = storage.getLastSellPrice(itemName);
            double lastBuy  = storage.getLastBuyPrice(itemName);

            boolean firstRun = (lastSell == -1);

            double diffSell = firstRun ? 0 : currentSell - lastSell;
            double diffBuy  = firstRun ? 0 : currentBuy  - lastBuy;

            results.add(new ItemResult(itemName, currentSell, currentBuy,
                    diffSell, diffBuy, firstRun));

            if (diffSell > 0) positiveCount++;
            else if (diffSell < 0) negativeCount++;

            storage.savePrice(itemName, currentSell, currentBuy);
        }

        if (results.isEmpty()) {
            plugin.getLogger().warning("Nessun item valido trovato.");
            return;
        }

        for (ItemResult r : results) {
            String sellVar = r.firstRun ? "  —" :
                    String.format("%s%+.2f", arrow(r.diffSell), r.diffSell);
            String buyVar  = r.firstRun ? "  —" :
                    String.format("%s%+.2f", arrow(r.diffBuy),  r.diffBuy);

            table.append(String.format("%-14s %8.2f %8s %8.2f %8s%n",
                    capitalize(r.name),
                    r.sellPrice, sellVar,
                    r.buyPrice,  buyVar));
        }

        Color embedColor;
        if (positiveCount > negativeCount)      embedColor = new Color(0x2ECC71);
        else if (negativeCount > positiveCount) embedColor = new Color(0xE74C3C);
        else                                    embedColor = new Color(0x95A5A6);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊  Mercato — Aggiornamento Prezzi")
                .setDescription("```\n" + table + "```")
                .setColor(embedColor)
                .setFooter("🔄 Prossimo aggiornamento tra " +
                        plugin.getConfig().getInt("update-interval-hours", 3) + " ore")
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> plugin.getLogger().info("Aggiornamento mercato inviato su Discord."),
                error   -> plugin.getLogger().severe("Errore invio Discord: " + error.getMessage())
        );
    }

    private String arrow(double diff) {
        if (diff > 0) return "▲";
        if (diff < 0) return "▼";
        return "●";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    private record ItemResult(
            String name,
            double sellPrice, double buyPrice,
            double diffSell,  double diffBuy,
            boolean firstRun
    ) {}
}
