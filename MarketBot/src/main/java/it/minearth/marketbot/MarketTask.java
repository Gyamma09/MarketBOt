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

                // Prezzo attuale (con dynamic pricing applicato)
                Double sellObj = esgui.getItemSellPrice(shopItem, stack);
                Double buyObj  = esgui.getItemBuyPrice(shopItem, stack);
                double currentSell = (sellObj != null) ? sellObj : 0;
                double currentBuy  = (buyObj  != null) ? buyObj  : 0;

                // Prezzo base dal config di ESGUI (prima del dynamic pricing)
                double baseSell = shopItem.getSellPriceRaw();
                double baseBuy  = shopItem.getBuyPriceRaw();

                // Variazione rispetto al prezzo base del config
                double diffSell = currentSell - baseSell;
                double diffBuy  = currentBuy  - baseBuy;

                double pctSell = (baseSell > 0) ? (diffSell / baseSell) * 100 : 0;
                double pctBuy  = (baseBuy  > 0) ? (diffBuy  / baseBuy)  * 100 : 0;

                String displayName = shopItem.getName();

                results.add(new ItemResult(displayName, currentSell, currentBuy,
                        diffSell, diffBuy, pctSell, pctBuy));

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

        // Intestazione
        String header = String.format("  %-13s %7s  %-18s %7s  %s%n",
                "Item", "Vend.", "Var. vend.", "Acq.", "Var. acq.");
        String separator = "  " + "─".repeat(62) + "\n";

        StringBuilder positive = new StringBuilder();
        StringBuilder negative = new StringBuilder();
        StringBuilder neutral  = new StringBuilder();

        for (ItemResult r : results) {
            String sellVar = String.format("%s%+.2f (%+.1f%%)", arrow(r.diffSell), r.diffSell, r.pctSell);
            String buyVar  = String.format("%s%+.2f (%+.1f%%)", arrow(r.diffBuy),  r.diffBuy,  r.pctBuy);

            String line = String.format("%-13s %7.2f  %-18s %7.2f  %s%n",
                    r.name, r.sellPrice, sellVar, r.buyPrice, buyVar);

            // NB: in diff syntax +/- deve essere il PRIMO carattere della riga (senza spazi prima)
            if (r.diffSell > 0)      positive.append("+").append(line);
            else if (r.diffSell < 0) negative.append("-").append(line);
            else                     neutral.append(" ").append(line);
        }

        String description = "```diff\n" + header + separator
                + positive + negative + neutral + "```";

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
            double pctSell,   double pctBuy
    ) {}
}
