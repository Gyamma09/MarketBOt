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
import java.util.List;

public class CrashCheckTask extends BukkitRunnable {

    private final MarketBot plugin;
    private final PriceStorage storage;
    private EconomyShopGUIHook esgui;

    public CrashCheckTask(MarketBot plugin, PriceStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public void run() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::checkCrash);
    }

    public void checkCrash() {
        String webhookUrl = plugin.getConfig().getString("webhook-url", "");
        List<String> itemPaths = plugin.getConfig().getStringList("items");
        double threshold = plugin.getConfig().getDouble("crash-boom-threshold", 30.0);

        if (webhookUrl.isEmpty()) return;

        if (esgui == null) {
            EconomyShopGUI esguiPlugin = (EconomyShopGUI) plugin.getServer()
                    .getPluginManager().getPlugin("EconomyShopGUI-Premium");
            if (esguiPlugin == null) return;
            esgui = new EconomyShopGUIHook(esguiPlugin);
        }

        for (String path : itemPaths) {
            String[] parts = path.split("\\.");
            String sectionName = parts[parts.length - 2];
            String itemKey     = parts[parts.length - 1];

            try {
                ShopSection section = esgui.getShopSection(sectionName);
                if (section == null) continue;

                Material targetMaterial = Material.matchMaterial(itemKey);
                ShopItem shopItem = null;
                for (ShopItem si : section.getShopItems()) {
                    if (si.getName().equalsIgnoreCase(itemKey) ||
                        (targetMaterial != null && si.getItemToGive().getType() == targetMaterial)) {
                        shopItem = si;
                        break;
                    }
                }
                if (shopItem == null) continue;

                org.bukkit.inventory.ItemStack stack = shopItem.getItemToGive();
                Double sellObj = esgui.getItemSellPrice(shopItem, stack);
                double currentSell = (sellObj != null) ? sellObj : 0;
                double baseSell = shopItem.getSellPriceRaw(1);
                double pctSell = (baseSell > 0) ? ((currentSell - baseSell) / baseSell) * 100 : 0;

                if (Math.abs(pctSell) >= threshold) {
                    // Controlla se abbiamo già mandato un alert per questo item a questa soglia
                    // per evitare spam ogni 10 minuti
                    double lastAlertPct = storage.getLastAlertPct(path);
                    // Manda alert solo se la % è cambiata di almeno 5 punti dall'ultimo alert
                    if (Math.abs(pctSell - lastAlertPct) >= 5.0) {
                        sendCrashAlert(webhookUrl, shopItem.getName(), pctSell);
                        storage.saveLastAlertPct(path, pctSell);
                    }
                } else {
                    // Reset alert se torna sotto soglia
                    storage.saveLastAlertPct(path, 0);
                }

            } catch (Exception e) {
                plugin.getLogger().warning("CrashCheck errore su " + path + ": " + e.getMessage());
            }
        }
    }

    private void sendCrashAlert(String webhookUrl, String itemName, double pct) {
        String emoji    = pct > 0 ? "🚀" : "🚨";
        String type     = pct > 0 ? "BOOM" : "CRASH";
        int alertColor  = pct > 0 ? 0x2ECC71 : 0xE74C3C;
        String mention  = "@everyone";

        String json = "{"
                + "\"content\":\"" + mention + "\","
                + "\"username\":\"📊 Mercato\","
                + "\"embeds\":[{"
                + "\"title\":\"" + emoji + "  " + type + " del Mercato!\","
                + "\"description\":\"**" + escapeJson(itemName) + "** ha raggiunto "
                + String.format("**%+.1f%%**", pct) + " rispetto al prezzo base!\","
                + "\"color\":" + alertColor
                + "}]}";

        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "MarketBot/1.0.2");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            conn.getResponseCode();
            conn.disconnect();
            plugin.getLogger().info("Alert boom/crash inviato per " + itemName);
        } catch (Exception e) {
            plugin.getLogger().severe("Errore invio alert: " + e.getMessage());
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
