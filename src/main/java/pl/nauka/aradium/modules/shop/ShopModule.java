package pl.nauka.aradium.modules.shop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.AbstractModule;

import java.io.File;
import java.util.List;

/**
 * Moduł Sklepu GUI (UltimateShop Clone) zarządzający konfiguracjami shops/*.yml i otwartymi GUI.
 */
public class ShopModule extends AbstractModule {

    private static final List<String> DEFAULT_SHOP_RESOURCES = List.of(
            "shops/main.yml",
            "shops/blocks.yml"
    );

    private ShopManager shopManager;
    private ShopGUI shopGUI;

    public ShopModule(AradiumPlugin plugin) {
        super(plugin, "shop");
    }

    @Override
    protected void onEnable() {
        saveDefaultShopResources();
        this.shopManager = new ShopManager(plugin.getLogger());
        this.shopManager.loadFromDirectory(new File(plugin.getDataFolder(), "shops"));
        this.shopGUI = new ShopGUI(plugin, shopManager);

        registerCommand("shop", new ShopCommand(plugin, this));
        registerListener(new ShopListener(this));
    }

    @Override
    protected void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof ShopInventoryHolder) {
                player.closeInventory();
            }
        }
    }

    private void saveDefaultShopResources() {
        File shopsDir = new File(plugin.getDataFolder(), "shops");
        if (!shopsDir.exists()) {
            shopsDir.mkdirs();
        }
        for (String resourcePath : DEFAULT_SHOP_RESOURCES) {
            File target = new File(plugin.getDataFolder(), resourcePath);
            if (!target.exists() && plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
            }
        }
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public ShopGUI getShopGUI() {
        return shopGUI;
    }
}
