package nauka1.Aradium1;

import nauka1.Aradium1.effect.itemki.*;
import nauka1.Aradium1.ory.advanced.*;
import nauka1.Aradium1.ory.normal.*;
import nauka1.Aradium1.ory.primitive.*;
import nauka1.Aradium1.special.item.duch;
import nauka1.Aradium1.zbroje.comand.gold;
import nauka1.Aradium1.zbroje.comand.leather;
import nauka1.Aradium1.zbroje.item.leatcher;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.Objects;
import java.util.logging.Level;


public final class Nauka extends JavaPlugin {

    @Override
    public void onEnable() {

        super.onEnable();
        this.getLogger().log(Level.INFO, "[Nauka] Enabled");

        registersilaEventListeners();
        registerarrmorEventListeners();
        registerhealthEventListeners();
        registerheistEventListeners();
        registerregenerationEventListeners();
        registerskokEventListeners();
        registerspeedEventListeners();
        registerspecialEventListeners();
        oregenerate();
        folder();

    }


    private void registersilaEventListeners() {        //siła
        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new strenght.HipersilaItemListener(), this); // items
        pm.registerEvents(new strenght.Hipersila2ItemListener(), this);
        pm.registerEvents(new strenght.Hipersila3ItemListener(), this);
        pm.registerEvents(new strenght.Hipersila4ItemListener(), this);

        pm.registerEvents(new nauka1.Aradium1.effect.comand.strenght.sila1(), this); // command
        pm.registerEvents(new nauka1.Aradium1.effect.comand.strenght.sila2(), this);
        pm.registerEvents(new nauka1.Aradium1.effect.comand.strenght.sila3(), this);
        pm.registerEvents(new nauka1.Aradium1.effect.comand.strenght.sila4(), this);
    }


    private void registerhealthEventListeners() {       //health
        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new health.healthItemListener(), this);  // items
        pm.registerEvents(new health.health2ItemListener(), this);
        pm.registerEvents(new health.health3ItemListener(), this);
        pm.registerEvents(new health.health4ItemListener(), this);


        pm.registerEvents(new nauka1.Aradium1.effect.comand.health.health1(), this);  // command
        pm.registerEvents(new nauka1.Aradium1.effect.comand.health.health2(), this);
        pm.registerEvents(new nauka1.Aradium1.effect.comand.health.health3(), this);
        pm.registerEvents(new nauka1.Aradium1.effect.comand.health.health4(), this);
    }


    private void registerheistEventListeners() {        //heist
        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new heist.heistItemListener(), this); // items
        pm.registerEvents(new heist.heist2ItemListener(), this);
        pm.registerEvents(new heist.heist3ItemListener(), this);
        pm.registerEvents(new heist.heist4ItemListener(), this);


        pm.registerEvents(new nauka1.Aradium1.effect.comand.heist.heist1(), this);  // command
        pm.registerEvents(new nauka1.Aradium1.effect.comand.heist.heist2(), this);
        pm.registerEvents(new nauka1.Aradium1.effect.comand.heist.heist3(), this);
        pm.registerEvents(new nauka1.Aradium1.effect.comand.heist.heist4(), this);
    }


    private void registerregenerationEventListeners() {     //regeneration
        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new regeneration.regenerationItemListener(), this);  // items
        pm.registerEvents(new regeneration.regeneration2ItemListener(), this);
        pm.registerEvents(new regeneration.regeneration3ItemListener(), this);
        pm.registerEvents(new regeneration.regeneration4ItemListener(), this);


        pm.registerEvents(new nauka1.Aradium1.effect.comand.regeneration.regeneration1(), this);  // command
        pm.registerEvents(new nauka1.Aradium1.effect.comand.regeneration.regeneration2(), this);
        pm.registerEvents(new nauka1.Aradium1.effect.comand.regeneration.regeneration3(), this);
        pm.registerEvents(new nauka1.Aradium1.effect.comand.regeneration.regeneration4(), this);
    }


    private void registerskokEventListeners() {         //skok
        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new jump.zajoncItemListener(), this);  // items
        pm.registerEvents(new jump.zajonc2ItemListener(), this);
        pm.registerEvents(new jump.zajonc3ItemListener(), this);
        pm.registerEvents(new jump.zajonc4ItemListener(), this);


        pm.registerEvents(new nauka1.Aradium1.effect.comand.jump.skok1(), this);  // command
        pm.registerEvents(new nauka1.Aradium1.effect.comand.jump.skok2(), this);
        pm.registerEvents(new nauka1.Aradium1.effect.comand.jump.skok3(), this);
        pm.registerEvents(new nauka1.Aradium1.effect.comand.jump.skok4(), this);
    }


    private void registerspeedEventListeners() {             //speed
        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new speed.speedItemListener(), this);  // items
        pm.registerEvents(new speed.speed2ItemListener(), this);
        pm.registerEvents(new speed.speed3ItemListener(), this);
        pm.registerEvents(new speed.speed4ItemListener(), this);


        pm.registerEvents(new nauka1.Aradium1.effect.comand.speed.speed1(), this);  // command
        pm.registerEvents(new nauka1.Aradium1.effect.comand.speed.speed2(), this);
        pm.registerEvents(new nauka1.Aradium1.effect.comand.speed.speed3(), this);
        pm.registerEvents(new nauka1.Aradium1.effect.comand.speed.speed4(), this);
    }


    private void registerspecialEventListeners() {             //special
        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new duch.duch1ItemListener(), this);  // items


        pm.registerEvents(new nauka1.Aradium1.special.comand.duch.duch1(), this); //command
    }

    private void registerarrmorEventListeners() {      //zbroje
        PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(new leather.leather1(), this);  // command
        pm.registerEvents(new leather.leather2(), this);
        pm.registerEvents(new leather.leather3(), this);
        pm.registerEvents(new leather.leather4(), this);
        pm.registerEvents(new gold.gold1(), this);
        pm.registerEvents(new gold.gold2(), this);
        pm.registerEvents(new gold.gold3(), this);
        pm.registerEvents(new gold.gold4(), this);

        pm.registerEvents(new leatcher.leatcherarmorListener1(),this);  // items
        pm.registerEvents(new leatcher.leatcherarmorListener2(),this);
        pm.registerEvents(new leatcher.leatcherarmorListener3(),this);
        pm.registerEvents(new leatcher.leatcherarmorListener4(),this);
        pm.registerEvents(new nauka1.Aradium1.zbroje.item.gold.goldarmorListener1(),this);
        pm.registerEvents(new nauka1.Aradium1.zbroje.item.gold.goldarmorListener2(),this);
        pm.registerEvents(new nauka1.Aradium1.zbroje.item.gold.goldarmorListener3(),this);
        pm.registerEvents(new nauka1.Aradium1.zbroje.item.gold.goldarmorListener4(),this);
    }


    private void oregenerate() {

        Objects.requireNonNull(getCommand("diamondregion")).setExecutor(new diamondore.DiamondRegionCommand()); //diamond
        Objects.requireNonNull(getCommand("coalregion")).setExecutor(new coalore.CoalRegionCommand());  //coal
        Objects.requireNonNull(getCommand("goldregion")).setExecutor(new goldore.GoldRegionCommand());  //gold
        Objects.requireNonNull(getCommand("ironregion")).setExecutor(new ironore.ironoreRegionCommand());  //iron
        Objects.requireNonNull(getCommand("woodregion")).setExecutor(new wood.woodRegionCommand());  //wood
        Objects.requireNonNull(getCommand("lapisregion")).setExecutor(new lapis.lapisRegionCommand());  //lapis
        Objects.requireNonNull(getCommand("debrisregion")).setExecutor(new debris.debrisRegionCommand());  //debris
        Objects.requireNonNull(getCommand("obsydianregion")).setExecutor(new obsydian.obsydianRegionCommand());  //obsydian
        Objects.requireNonNull(getCommand("prismarinregion")).setExecutor(new prismarin.prismarinRegionCommand());  //prismarin
        Objects.requireNonNull(getCommand("redstoneregion")).setExecutor(new redstone.redstoneRegionCommand());  //redstone
        Objects.requireNonNull(getCommand("dirtregion")).setExecutor(new dirt.dirtRegionCommand());  //dirt
        Objects.requireNonNull(getCommand("emeraldregion")).setExecutor(new emerald.emeraldRegionCommand());  //emerald
        Objects.requireNonNull(getCommand("copperregion")).setExecutor(new copper.copperRegionCommand());   //copper
        Objects.requireNonNull(getCommand("cobelregion")).setExecutor(new cobel.cobelRegionCommand());  //cobel
        Objects.requireNonNull(getCommand("netherrackregion")).setExecutor(new netherrack.netherrackRegionCommand());  //netherrack

    }


    private void folder() {

        File pluginFolder = new File("plugins/Naukaconf");
        if (!pluginFolder.exists()) {
            pluginFolder.mkdir();
        }

        File textureFolder = new File("plugins/Naukaconf/textures");
        if (!textureFolder.exists()) {
            textureFolder.mkdir();
        }

        File generatorFolder = new File("plugins/Naukaconf/generators");
        if (!generatorFolder.exists()) {
            generatorFolder.mkdir();
        }

    }

}