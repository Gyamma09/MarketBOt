package it.minearth.marketbot;

import me.gypopo.economyshopgui.EconomyShopGUI;
import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.gypopo.economyshopgui.objects.shops.ShopSection;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MarketTask extends BukkitRunnable {

    private final MarketBot plugin;
    private final PriceStorage storage;
    private EconomyShopGUIHook esgui;

    private static final String BANNER_URL = "https://i.imgur.com/iSe2ZRj.jpeg";
    private static final String FOOTER_ICON_URL = "https://i.imgur.com/9uJ1ua3.png";

    public MarketTask(MarketBot plugin, PriceStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public void run() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::sendMarketUpdate);
    }

    public void sendMarketUpdate() {
        String webhookUrl = plugin.getConfig().getString("webhook-url", "");
        List<String> itemPaths = plugin.getConfig().getStringList("items");

        if (webhookUrl.isEmpty()) {
            plugin.getLogger().warning("webhook-url non configurato in config.yml!");
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
                    plugin.getLogger().warning("Item non trovato: " + itemKey);
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

                results.add(new ItemResult(shopItem.getName(), currentSell, currentBuy,
                        diffSell, diffBuy, pctSell, pctBuy));

                if (diffSell > 0) positiveCount++;
                else if (diffSell < 0) negativeCount++;

            } catch (Exception e) {
                plugin.getLogger().warning("Errore processando " + path + ": " + e.getMessage());
            }
        }

        if (results.isEmpty()) {
            plugin.getLogger().warning("Nessun item valido trovato.");
            return;
        }

        int color;
        if (positiveCount > negativeCount)      color = 0x2ECC71;
        else if (negativeCount > positiveCount) color = 0xE74C3C;
        else                                    color = 0x95A5A6;

        // Fields: 3 per riga (inline: true)
        // Discord mette 3 inline fields per riga automaticamente
        StringBuilder fields = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            ItemResult r = results.get(i);

            String prefix = r.diffSell > 0 ? "+" : r.diffSell < 0 ? "-" : " ";

            String fieldValue =
                "```diff\n"
                + prefix + r.name + "  (" + String.format("%+.1f%%", r.pctSell) + ")\n"
                + String.format("  Acq: $%.2f %s%+.2f\n", r.buyPrice,  arrow(r.diffBuy),  r.diffBuy)
                + String.format("  Vend: $%.2f %s%+.2f\n", r.sellPrice, arrow(r.diffSell), r.diffSell)
                + "```";

            fields.append("{");
            fields.append("\"name\":\"\\u200b\",");
            fields.append("\"value\":\"").append(escapeJson(fieldValue)).append("\",");
            fields.append("\"inline\":true");
            fields.append("}");

            // Ogni 3 item aggiungi un field vuoto per forzare a capo se necessario
            if ((i + 1) % 3 == 0 && i < results.size() - 1) {
                fields.append(",{\"name\":\"\\u200b\",\"value\":\"\\u200b\",\"inline\":false}");
            }

            if (i < results.size() - 1) fields.append(",");
        }

        String timestamp = Instant.now().toString();
        int intervalHours = plugin.getConfig().getInt("update-interval-hours", 3);

        String json = "{"
                + "\"username\":\"📊 Mercato\","
                + "\"embeds\":[{"
                + "\"title\":\"Aggiornamento Prezzi\","
                + "\"color\":" + color + ","
                + "\"fields\":[" + fields + "],"
                + "\"image\":{\"url\":\"" + BANNER_URL + "\"},"
                + "\"footer\":{"
                + "\"text\":\"🔄 Prossimo aggiornamento tra " + intervalHours + " ore\","
                + "\"icon_url\":\"" + FOOTER_ICON_URL + "\""
                + "},"
                + "\"timestamp\":\"" + timestamp + "\""
                + "}]}";

        sendWebhook(webhookUrl, json);
    }

    private void sendWebhook(String webhookUrl, String json) {
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "MarketBot/1.0.1");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 204 || responseCode == 200) {
                plugin.getLogger().info("Aggiornamento mercato inviato su Discord.");
            } else {
                plugin.getLogger().warning("Webhook risposta inattesa: " + responseCode);
            }
            conn.disconnect();

        } catch (Exception e) {
            plugin.getLogger().severe("Errore invio webhook: " + e.getMessage());
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
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
