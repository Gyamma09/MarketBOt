package it.minearth.marketbot;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

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

    /** Ultima percentuale per cui è stato mandato un alert boom/crash */
    public double getLastAlertPct(String item) {
        return yaml.getDouble(sanitize(item) + ".lastAlertPct", 0);
    }

    public void saveLastAlertPct(String item, double pct) {
        yaml.set(sanitize(item) + ".lastAlertPct", pct);
        save();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Rimuove caratteri problematici per le chiavi YAML */
    private String sanitize(String key) {
        return key.replace(".", "_").replace(" ", "_").toUpperCase();
    }
}
