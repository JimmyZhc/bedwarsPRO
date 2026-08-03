package io.jmmym.bedwarspro.listener;

import io.jmmym.bedwarspro.BedwarsPRO;
import org.bukkit.event.Listener;

public abstract class BaseListener implements Listener {

  public BaseListener() {
    this.registerEvents();
  }

  private void registerEvents() {
    BedwarsPRO.getInstance().getServer().getPluginManager()
        .registerEvents(this, BedwarsPRO.getInstance());
  }

}
