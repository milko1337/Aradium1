package pl.nauka.aradium.managers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.nauka.aradium.AradiumPlugin;
import pl.nauka.aradium.utils.ChatUtils;

import java.util.ArrayList;
import java.util.List;

public class MagicItemManager {

    private final AradiumPlugin plugin;

    public MagicItemManager(AradiumPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack createMagicItem(String type, int level) {
        String path = "magic_items." + type;
        if (!plugin.getConfig().contains(path)) return null;

        String matName = plugin.getConfig().getString(path + ".material", "DIRT");
        Material material = Material.matchMaterial(matName);
        if (material == null) material = Material.DIRT;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Name
        String name = plugin.getConfig().getString(path + ".name", type);
        meta.setDisplayName(ChatUtils.color(name + " " + level));

        // Lore
        String loreLine = plugin.getConfig().getString(path + ".lore", "");
        List<String> lore = new ArrayList<>();
        lore.add(ChatUtils.color(loreLine));
        meta.setLore(lore);

        // Enchant
        meta.addEnchant(Enchantment.DURABILITY, level, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // NBT Tags for safe identification
        NamespacedKey typeKey = new NamespacedKey(plugin, "magic_type");
        NamespacedKey levelKey = new NamespacedKey(plugin, "magic_level");
        
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);

        item.setItemMeta(meta);
        return item;
    }
}
