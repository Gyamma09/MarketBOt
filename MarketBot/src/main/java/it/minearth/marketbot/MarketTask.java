package it.minearth.marketbot;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import me.gypopo.economyshopgui.EconomyShopGUI;
import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.gypopo.economyshopgui.objects.shops.ShopSection;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

        if (esgui == null) {
            EconomyShopGUI esguiPlugin = (EconomyShopGUI) plugin.getServer()
                    .getPluginManager().getPlugin("EconomyShopGUI-Premium");
            if (esguiPlugin == null) {
                plugin.getLogger().severe("EconomyShopGUI-Premium non trovato!");
                return;
            }
            esgui = new EconomyShopGUIHook(esguiPlugin);
        }

        int positiveCount = 0;
        int negativeCount = 0;
        List<ItemResult> results = new ArrayList<>();

        for (String path : itemPaths) {
            String[] parts = path.split("\\.");
            String sectionName = parts[parts.length - 2];
            String itemKey     = parts[parts.length - 1];

            try {
                ShopSection section = esgui.getShopSection(sectionName);
                if (section == null) {
                    plugin.getLogger().warning("Sezione non trovata: " + sectionName);
                    continue;
                }

                Material targetMaterial = Material.matchMaterial(itemKey);
                ShopItem shopItem = null;
                for (ShopItem si : section.getShopItems()) {
                    if (si.getName().equalsIgnoreCase(itemKey) ||
                        (targetMaterial != null && si.getItemToGive().getType() == targetMaterial)) {
                        shopItem = si;
                        break;
                    }
                }

                if (shopItem == null) {
                    plugin.getLogger().warning("Item non trovato nella sezione " + sectionName + ": " + itemKey);
                    continue;
                }

                org.bukkit.inventory.ItemStack stack = shopItem.getItemToGive();

                Double sellObj = esgui.getItemSellPrice(shopItem, stack);
                Double buyObj  = esgui.getItemBuyPrice(shopItem, stack);
                double currentSell = (sellObj != null) ? sellObj : 0;
                double currentBuy  = (buyObj  != null) ? buyObj  : 0;

                double baseSell = shopItem.getSellPriceRaw(1);
                double baseBuy  = shopItem.getBuyPriceRaw(1);

                double diffSell = currentSell - baseSell;
                double diffBuy  = currentBuy  - baseBuy;

                double pctSell = (baseSell > 0) ? (diffSell / baseSell) * 100 : 0;
                double pctBuy  = (baseBuy  > 0) ? (diffBuy  / baseBuy)  * 100 : 0;

                // Variazione totale sul nome: usa sell come indicatore principale
                double pctTotal = pctSell;

                String displayName = shopItem.getName();

                results.add(new ItemResult(displayName, currentSell, currentBuy,
                        diffSell, diffBuy, pctSell, pctBuy, pctTotal));

                if (diffSell > 0) positiveCount++;
                else if (diffSell < 0) negativeCount++;

            } catch (Exception e) {
                plugin.getLogger().warning("Errore processando item " + path + ": " + e.getMessage());
            }
        }

        if (results.isEmpty()) {
            plugin.getLogger().warning("Nessun item valido trovato.");
            return;
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < results.size(); i++) {
            ItemResult r = results.get(i);

            // Riga nome: colorata con + (verde) o - (rosso) in base alla variazione sell
            String namePrefix;
            if (r.diffSell > 0)      namePrefix = "+";
            else if (r.diffSell < 0) namePrefix = "-";
            else                     namePrefix = " ";

            String nameLine = String.format("%s%-13s  (%+.1f%%)%n",
                    namePrefix, r.name, r.pctTotal);

            // Righe Buy e Sell: sempre neutre (iniziano con spazio)
            String buyLine = String.format("  Buy   %7.2f  %s%+.2f (%+.1f%%)%n",
                    r.buyPrice, arrow(r.diffBuy), r.diffBuy, r.pctBuy);
            String sellLine = String.format("  Sell  %7.2f  %s%+.2f (%+.1f%%)%n",
                    r.sellPrice, arrow(r.diffSell), r.diffSell, r.pctSell);

            sb.append(nameLine);
            sb.append(buyLine);
            sb.append(sellLine);

            // Separatore tra item (tranne l'ultimo)
            if (i < results.size() - 1) {
                sb.append("  ").append("─".repeat(38)).append("\n");
            }
        }

        String description = "```diff\n" + sb + "```";

        Color embedColor;
        if (positiveCount > negativeCount)      embedColor = new Color(0x2ECC71);
        else if (negativeCount > positiveCount) embedColor = new Color(0xE74C3C);
        else                                    embedColor = new Color(0x95A5A6);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊  Mercato — Aggiornamento Prezzi")
                .setDescription(description)
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

    private record ItemResult(
            String name,
            double sellPrice, double buyPrice,
            double diffSell,  double diffBuy,
            double pctSell,   double pctBuy,
            double pctTotal
    ) {}
}
