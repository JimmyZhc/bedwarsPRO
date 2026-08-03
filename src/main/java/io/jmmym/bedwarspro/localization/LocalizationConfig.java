package io.jmmym.bedwarspro.localization;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class LocalizationConfig extends YamlConfiguration {

  private String locale;

  public LocalizationConfig(String locale) {
    this.locale = locale;
    this.loadLocale();
  }

  @Override
  public Object get(String path) {
    return this.getString(path);
  }

  public Object get(String path, Map<String, String> params) {
    return this.getFormatString(path, params);
  }

  public String getFormatString(String path, Map<String, String> params) {
    String str = this.getString(path);
    for (String key : params.keySet()) {
      str = str.replace("$" + key.toLowerCase() + "$", params.get(key));
    }

    return ChatColor.translateAlternateColorCodes('&', str);
  }

  @SuppressWarnings("unchecked")
  public String getPlayerLocale(Player player) {
    try {
      Method getHandleMethod = BedwarsPRO.getInstance().getCraftBukkitClass("entity.CraftPlayer")
          .getMethod("getHandle", new Class[]{});
      getHandleMethod.setAccessible(true);
      Object nmsPlayer = getHandleMethod.invoke(player, new Object[]{});

      Field localeField = nmsPlayer.getClass().getDeclaredField("locale");
      localeField.setAccessible(true);
      return localeField.get(nmsPlayer).toString().split("_")[0].toLowerCase();
    } catch (Exception ex) {
      BedwarsPRO.getInstance().getBugsnag().notify(ex);
      return BedwarsPRO.getInstance().getFallbackLocale();
    }
  }

  @Override
  public String getString(String path) {
    if (super.get(path) == null) {
      BedwarsPRO.getInstance().getServer().getConsoleSender()
          .sendMessage(ChatWriter
              .pluginMessage(ChatColor.GOLD + "No translation found for: \"" + path + "\""));
      return "LOCALE_NOT_FOUND";
    }

    return ChatColor.translateAlternateColorCodes('&', super.getString(path));
  }

  public void loadLocale() {
    File locFile =
        new File(
            BedwarsPRO.getInstance().getDataFolder().getPath() + "/locale/" + this.locale + ".yml");
    BufferedReader reader = null;
    InputStream inputStream = null;
    if (locFile.exists()) {
      try {
        inputStream = new FileInputStream(locFile);
      } catch (FileNotFoundException e) {
        // NO ERROR
      }
      BedwarsPRO.getInstance().getServer().getConsoleSender().sendMessage(ChatWriter
          .pluginMessage(ChatColor.GOLD + "Using your custom locale \"" + this.locale + "\"."));
    } else {
      if (inputStream == null) {
        inputStream = BedwarsPRO.getInstance().getResource("locale/" + this.locale + ".yml");
      }
      if (inputStream == null) {
        BedwarsPRO.getInstance().getServer().getConsoleSender()
            .sendMessage(ChatWriter.pluginMessage(ChatColor.GOLD + "The locale \"" + this.locale
                + "\" defined in your config is not available. Using fallback locale: "
                + BedwarsPRO.getInstance().getFallbackLocale()));
        inputStream = BedwarsPRO.getInstance()
            .getResource("locale/" + BedwarsPRO.getInstance().getFallbackLocale() + ".yml");
      }
    }
    try {
      reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
      this.load(reader);
    } catch (Exception e) {
      BedwarsPRO.getInstance().getServer().getConsoleSender().sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + "Failed to load localization language!"));
      return;
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

}
