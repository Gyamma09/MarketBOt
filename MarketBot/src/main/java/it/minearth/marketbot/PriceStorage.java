package it.tuoserver.marketbot;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Gestisce la persistenza dei prezzi precedenti su file prices.yml
 * Salva: ultimo prezzo di vendita e acquisto per ogni item
 */
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

    /** Restituisce l'ultimo prezzo di vendita salvato, o -1 se non esiste */
    public double getLastSellPrice(String item) {
        return yaml.getDouble(item.toUpperCase() + ".sell", -1);
    }

    /** Restituisce l'ultimo prezzo di acquisto salvato, o -1 se non esiste */
    public double getLastBuyPrice(String item) {
        return yaml.getDouble(item.toUpperCase() + ".buy", -1);
    }

    /** Salva i prezzi attuali come "ultimi prezzi" */
    public void savePrice(String item, double sell, double buy) {
        yaml.set(item.toUpperCase() + ".sell", sell);
        yaml.set(item.toUpperCase() + ".buy", buy);
        try {
            yaml.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
