package it.minearth.marketbot;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PriceStorage {

    private final File file;
    private YamlConfiguration yaml;

    public PriceStorage(MarketBot plugin) {
        this.file = new File(plugin.getDataFolder(), "prices.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public double getLastSellPrice(String item) {
        return yaml.getDouble(sanitize(item) + ".sell", -1);
    }

    public double getLastBuyPrice(String item) {
        return yaml.getDouble(sanitize(item) + ".buy", -1);
    }

    public void savePrice(String item, double sell, double buy) {
        yaml.set(sanitize(item) + ".sell", sell);
        yaml.set(sanitize(item) + ".buy", buy);
        save();
    }

    public double getLastAlertPct(String item) {
        return yaml.getDouble(sanitize(item) + ".lastAlertPct", 0);
    }

    public void saveLastAlertPct(String item, double pct) {
        yaml.set(sanitize(item) + ".lastAlertPct", pct);
        save();
    }

    /**
     * Aggiunge un message ID alla lista e restituisce quello da eliminare
     * (se la lista supera maxMessages). Restituisce null se non c'è nulla da eliminare.
     */
    public String addMessageId(String messageId, int maxMessages) {
        List<String> ids = new ArrayList<>(yaml.getStringList("message-history"));
        ids.add(messageId);

        String toDelete = null;
        if (ids.size() > maxMessages) {
            toDelete = ids.remove(0); // rimuove il più vecchio
        }

        yaml.set("message-history", ids);
        save();
        return toDelete;
    }

    /** Restituisce tutti gli ID messaggi salvati */
    public List<String> getMessageHistory() {
        return yaml.getStringList("message-history");
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String sanitize(String key) {
        return key.replace(".", "_").replace(" ", "_").toUpperCase();
    }
}
