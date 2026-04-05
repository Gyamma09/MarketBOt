package it.minearth.marketbot;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import me.gypopo.economyshopgui.EconomyShopGUI;
import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.gypopo.economyshopgui.objects.shops.ShopSection;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MarketTask extends BukkitRunnable {

    private final MarketBot plugin;
    private final PriceStorage storage;
    private EconomyShopGUIHook esgui;

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
        List<String> itemPaths = plugin.getConfig().getStringList("items");

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

        // Inizializza l'hook se non ancora fatto
        if (esgui == null) {
            plugin.getLogger().info("Sezioni disponibili: " + esgui.getShopSections());
            EconomyShopGUI esguiPlugin = (EconomyShopGUI) plugin.getServer()
                    .getPluginManager().getPlugin("EconomyShopGUI-Premium");
            if (esguiPlugin == null) {
                plugin.getLogger().severe("EconomyShopGUI-Premium non trovato!");
                return;
            }
            esgui = new EconomyShopGUIHook(esguiPlugin);
        }

        StringBuilder table = new StringBuilder();
        table.append(String.format("%-14s %8s %8s %8s %8s%n",
                "Item", "Vendita", "Var.", "Acquisto", "Var."));
        table.append("─".repeat(54)).append("\n");

        int positiveCount = 0;
        int negativeCount = 0;
        List<ItemResult> results = new ArrayList<>();

        for (String path : itemPaths) {
            // path formato: "ShopName.sectionName.itemKey" es. "Ores.page1.4"
            String[] parts = path.split("\\.");
            if (parts.length < 3) {
                plugin.getLogger().warning("Path non valido (usa formato NomeShop.pagina.numero): " + path);
                continue;
            }

            try {
                String sectionName = parts[0];
                String itemKey = parts[1];

                ShopSection section = esgui.getShopSection(sectionName);
                if (section == null) {
                    plugin.getLogger().warning("Sezione non trovata: " + sectionName);
                    continue;
                }

                ShopItem shopItem = section.getShopItem(itemKey);
                if (shopItem == null) {
                    plugin.getLogger().warning("Item non trovato nella sezione " + sectionName + ": " + itemKey);
                    continue;
                }

                // Prendi i prezzi con ItemStack dell'item
                org.bukkit.inventory.ItemStack stack = shopItem.getItemToGive();

                Double sellObj = esgui.getItemSellPrice(shopItem, stack);
                Double buyObj  = esgui.getItemBuyPrice(shopItem, stack);

                double currentSell = (sellObj != null) ? sellObj : 0;
                double currentBuy  = (buyObj  != null) ? buyObj  : 0;

                double lastSell = storage.getLastSellPrice(path);
                double lastBuy  = storage.getLastBuyPrice(path);
                boolean firstRun = (lastSell == -1);

                double diffSell = firstRun ? 0 : currentSell - lastSell;
                double diffBuy  = firstRun ? 0 : currentBuy  - lastBuy;

                // Nome leggibile: usa il material dell'item
                String displayName = shopItem.getName();

                results.add(new ItemResult(displayName, currentSell, currentBuy,
                        diffSell, diffBuy, firstRun));

                if (diffSell > 0) positiveCount++;
                else if (diffSell < 0) negativeCount++;

                storage.savePrice(path, currentSell, currentBuy);

            } catch (Exception e) {
                plugin.getLogger().warning("Errore processando item " + path + ": " + e.getMessage());
            }
        }

        if (results.isEmpty()) {
            plugin.getLogger().warning("Nessun item valido trovato.");
            return;
        }

        for (ItemResult r : results) {
            String sellVar = r.firstRun ? "  —" :
                    String.format("%s%+.2f", arrow(r.diffSell), r.diffSell);
            String buyVar  = r.firstRun ? "  —" :
                    String.format("%s%+.2f", arrow(r.diffBuy), r.diffBuy);

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
            double diffSell, double diffBuy,
            boolean firstRun
    ) {}
}
