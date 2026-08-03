package io.jmmym.bedwarspro.utils;

import io.jmmym.bedwarspro.BedwarsPRO;
import org.bukkit.ChatColor;

public class ChatWriter {

  public static String pluginMessage(String str) {
    return ChatColor.translateAlternateColorCodes('&',
        BedwarsPRO.getInstance().getConfig().getString("chat-prefix",
            ChatColor.GRAY + "[" + ChatColor.AQUA + "BedWars" + ChatColor.GRAY + "]"))
        + " " + ChatColor.WHITE + str;
  }

}
