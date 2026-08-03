package io.jmmym.bedwarspro.shop.Specials;

import io.jmmym.bedwarspro.BedwarsPRO;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

public abstract class SpecialItem {

  private static List<Class<? extends SpecialItem>> availableSpecials =
      new ArrayList<Class<? extends SpecialItem>>();

  public static List<Class<? extends SpecialItem>> getSpecials() {
    return SpecialItem.availableSpecials;
  }

  public static void loadSpecials() {
    SpecialItem.availableSpecials.add(RescuePlatform.class);
    SpecialItem.availableSpecials.add(Trap.class);
    SpecialItem.availableSpecials.add(MagnetShoe.class);
    SpecialItem.availableSpecials.add(ProtectionWall.class);
    SpecialItem.availableSpecials.add(WarpPowder.class);
    SpecialItem.availableSpecials.add(TNTSheep.class);
    SpecialItem.availableSpecials.add(Tracker.class);
    SpecialItem.availableSpecials.add(ArrowBlocker.class);
    BedwarsPRO.getInstance().getServer().getPluginManager()
        .registerEvents(new RescuePlatformListener(),
            BedwarsPRO.getInstance());
    BedwarsPRO.getInstance().getServer().getPluginManager().registerEvents(new TrapListener(),
        BedwarsPRO.getInstance());
    BedwarsPRO.getInstance().getServer().getPluginManager().registerEvents(new MagnetShoeListener(),
        BedwarsPRO.getInstance());
    BedwarsPRO.getInstance().getServer().getPluginManager()
        .registerEvents(new ProtectionWallListener(),
            BedwarsPRO.getInstance());
    BedwarsPRO.getInstance().getServer().getPluginManager().registerEvents(new WarpPowderListener(),
        BedwarsPRO.getInstance());
   BedwarsPRO.getInstance().getServer().getPluginManager().registerEvents(new TNTSheepListener(),
        BedwarsPRO.getInstance());
    BedwarsPRO.getInstance().getServer().getPluginManager().registerEvents(new TrackerListener(),
        BedwarsPRO.getInstance());
    BedwarsPRO.getInstance().getServer().getPluginManager()
        .registerEvents(new ArrowBlockerListener(),
            BedwarsPRO.getInstance());
  }

  public abstract Material getActivatedMaterial();

  public abstract Material getItemMaterial();

}
