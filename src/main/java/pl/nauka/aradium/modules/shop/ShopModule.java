package pl.nauka.aradium.modules.shop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.core.AbstractModule;

import java.io.File;
import java.util.List;

/**
 * Moduł Sklepu GUI (EconomyShopGUI / UltimateShop Clone) zarządzający konfiguracjami shops/*.yml,
 * ekranami wyboru ilości oraz komendami /shop, /sellall i /sellgui.
 */
public class ShopModule extends AbstractModule {

    private static final List<String> DEFAULT_SHOP_RESOURCES = List.of(
            "shops/main.yml",
            "shops/building.yml",
            "shops/blocks.yml",
            "shops/wood.yml",
            "shops/natural.yml",
            "shops/decorative_blocks.yml",
            "shops/ores.yml",
            "shops/farming.yml",
            "shops/food.yml",
            "shops/combat.yml",
            "shops/redstone.yml",
            "shops/magic.yml",
            "shops/mobdrops.yml",
            "shops/decoration.yml",
            "shops/music.yml",
            "shops/storage.yml",
            "shops/misc.yml"
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
        registerCommand("sellall", new SellAllCommand(plugin, this));
        registerCommand("sellgui", new SellGuiCommand(plugin, this));
        registerListener(new ShopListener(this));
    }

    @Override
    protected void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory topInventory = player.getOpenInventory().getTopInventory();
            if (topInventory.getHolder() instanceof ShopInventoryHolder holder) {
                if (holder.getScreenType() == ShopInventoryHolder.ScreenType.SELL_GUI && shopGUI != null) {
                    shopGUI.processSellGuiClose(player, topInventory);
                }
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
