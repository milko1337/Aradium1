package nauka1.nauka.zbroje.item;
import static nauka1.nauka.imports.material.*;
import static nauka1.nauka.imports.name.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

public class gold {

    public static class goldarmorListener1 implements Listener {

        @EventHandler
        public void playerEquipsArmourLeather1(InventoryCloseEvent event) {
            Player player = (Player) event.getPlayer();
            ItemStack helmet = player.getInventory().getHelmet();
            ItemStack chestplate = player.getInventory().getChestplate();
            ItemStack leggings = player.getInventory().getLeggings();
            ItemStack boots = player.getInventory().getBoots();


            if (helmet != null && helmet.getType() == itemgoldMaterial1 &&
                    chestplate != null && chestplate.getType() == itemgoldrMaterial2 &&
                    leggings != null && leggings.getType() == itemgoldMaterial3 &&
                    boots != null && boots.getType() == itemgoldMaterial4) {

                ItemMeta meta1 = helmet.getItemMeta();
                ItemMeta meta2 = chestplate.getItemMeta();
                ItemMeta meta3 = leggings.getItemMeta();
                ItemMeta meta4 = boots.getItemMeta();


                if (meta1 != null && meta1.hasDisplayName() && meta1.getDisplayName().equals(itemgoldName1 + "5") &&
                        meta2 != null && meta2.hasDisplayName() && meta2.getDisplayName().equals(itemgoldName2 + "5") &&
                        meta3 != null && meta3.hasDisplayName() && meta3.getDisplayName().equals(itemgoldName3 + "5") &&
                        meta4 != null && meta4.hasDisplayName() && meta4.getDisplayName().equals(itemgoldName4 + "5")) {


                    Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(30);

                }

            } else {

                Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(20);
            }
        }
    }

    public static class goldarmorListener2 implements Listener {

        @EventHandler
        public void playerEquipsArmourLeather2(InventoryCloseEvent event) {
            Player player = (Player) event.getPlayer();
            ItemStack helmet = player.getInventory().getHelmet();
            ItemStack chestplate = player.getInventory().getChestplate();
            ItemStack leggings = player.getInventory().getLeggings();
            ItemStack boots = player.getInventory().getBoots();


            if (helmet != null && helmet.getType() == itemgoldMaterial1 &&
                    chestplate != null && chestplate.getType() == itemgoldrMaterial2 &&
                    leggings != null && leggings.getType() == itemgoldMaterial3 &&
                    boots != null && boots.getType() == itemgoldMaterial4) {

                ItemMeta meta1 = helmet.getItemMeta();
                ItemMeta meta2 = chestplate.getItemMeta();
                ItemMeta meta3 = leggings.getItemMeta();
                ItemMeta meta4 = boots.getItemMeta();


                if (meta1 != null && meta1.hasDisplayName() && meta1.getDisplayName().equals(itemgoldName1 + "6") &&
                        meta2 != null && meta2.hasDisplayName() && meta2.getDisplayName().equals(itemgoldName2 + "6") &&
                        meta3 != null && meta3.hasDisplayName() && meta3.getDisplayName().equals(itemgoldName3 + "6") &&
                        meta4 != null && meta4.hasDisplayName() && meta4.getDisplayName().equals(itemgoldName4 + "6")) {


                    Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(32);

                }

            } else {

                Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(20);
            }
        }
    }

    public static class goldarmorListener3 implements Listener {

        @EventHandler
        public void playerEquipsArmourLeather3(InventoryCloseEvent event) {
            Player player = (Player) event.getPlayer();
            ItemStack helmet = player.getInventory().getHelmet();
            ItemStack chestplate = player.getInventory().getChestplate();
            ItemStack leggings = player.getInventory().getLeggings();
            ItemStack boots = player.getInventory().getBoots();


            if (helmet != null && helmet.getType() == itemgoldMaterial1 &&
                    chestplate != null && chestplate.getType() == itemgoldrMaterial2 &&
                    leggings != null && leggings.getType() == itemgoldMaterial3 &&
                    boots != null && boots.getType() == itemgoldMaterial4) {

                ItemMeta meta1 = helmet.getItemMeta();
                ItemMeta meta2 = chestplate.getItemMeta();
                ItemMeta meta3 = leggings.getItemMeta();
                ItemMeta meta4 = boots.getItemMeta();


                if (meta1 != null && meta1.hasDisplayName() && meta1.getDisplayName().equals(itemgoldName1 + "7") &&
                        meta2 != null && meta2.hasDisplayName() && meta2.getDisplayName().equals(itemgoldName2 + "7") &&
                        meta3 != null && meta3.hasDisplayName() && meta3.getDisplayName().equals(itemgoldName3 + "7") &&
                        meta4 != null && meta4.hasDisplayName() && meta4.getDisplayName().equals(itemgoldName4 + "7")) {


                    Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(34);

                }

            } else {

                Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(20);
            }
        }
    }

    public static class goldarmorListener4 implements Listener {

        @EventHandler
        public void playerEquipsArmourLeather4(InventoryCloseEvent event) {
            Player player = (Player) event.getPlayer();
            ItemStack helmet = player.getInventory().getHelmet();
            ItemStack chestplate = player.getInventory().getChestplate();
            ItemStack leggings = player.getInventory().getLeggings();
            ItemStack boots = player.getInventory().getBoots();


            if (helmet != null && helmet.getType() == itemgoldMaterial1 &&
                    chestplate != null && chestplate.getType() == itemgoldrMaterial2 &&
                    leggings != null && leggings.getType() == itemgoldMaterial3 &&
                    boots != null && boots.getType() == itemgoldMaterial4) {

                ItemMeta meta1 = helmet.getItemMeta();
                ItemMeta meta2 = chestplate.getItemMeta();
                ItemMeta meta3 = leggings.getItemMeta();
                ItemMeta meta4 = boots.getItemMeta();


                if (meta1 != null && meta1.hasDisplayName() && meta1.getDisplayName().equals(itemgoldName1 + "8") &&
                        meta2 != null && meta2.hasDisplayName() && meta2.getDisplayName().equals(itemgoldName2 + "8") &&
                        meta3 != null && meta3.hasDisplayName() && meta3.getDisplayName().equals(itemgoldName3 + "8") &&
                        meta4 != null && meta4.hasDisplayName() && meta4.getDisplayName().equals(itemgoldName4 + "8")) {


                    Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(36);

                }

            } else {

                Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(20);
            }
        }
    }

}
