package io.jmmym.bedwarspro.updater;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.villager.ItemStackParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ConfigUpdater {

  @SuppressWarnings("unchecked")
  public void addConfigs() {
    // <1.1.3>
    BedwarsPRO.getInstance().getConfig().addDefault("check-updates", true);
    // </1.1.3>

    // <1.1.4>
    BedwarsPRO.getInstance().getConfig().addDefault("sign.first-line", "$title$");
    BedwarsPRO.getInstance().getConfig().addDefault("sign.second-line", "$regionname$");
    BedwarsPRO.getInstance().getConfig().addDefault("sign.third-line",
        "Players &7[&b$currentplayers$&7/&b$maxplayers$&7]");
    BedwarsPRO.getInstance().getConfig().addDefault("sign.fourth-line", "$status$");
    BedwarsPRO.getInstance().getConfig().addDefault("specials.rescue-platform.break-time", 10);
    BedwarsPRO.getInstance().getConfig().addDefault("specials.rescue-platform.using-wait-time", 20);
    BedwarsPRO.getInstance().getConfig().addDefault("explodes.destroy-worldblocks", false);
    BedwarsPRO.getInstance().getConfig().addDefault("explodes.destroy-beds", false);
    BedwarsPRO.getInstance().getConfig().addDefault("explodes.drop-blocking", false);
    BedwarsPRO.getInstance().getConfig().addDefault("rewards.enabled", false);

    List<String> defaultRewards = new ArrayList<String>();
    defaultRewards.add("/example {player} {score}");
    BedwarsPRO.getInstance().getConfig().addDefault("rewards.player-win", defaultRewards);
    BedwarsPRO.getInstance().getConfig().addDefault("rewards.player-end-game", defaultRewards);
    // </1.1.4>

    // <1.1.6>
    BedwarsPRO.getInstance().getConfig().addDefault("global-messages", true);
    BedwarsPRO.getInstance().getConfig().addDefault("player-settings.one-stack-on-shift", false);
    // </1.1.6>

    // <1.1.8>
    BedwarsPRO.getInstance().getConfig().addDefault("seperate-game-chat", true);
    BedwarsPRO.getInstance().getConfig().addDefault("seperate-spectator-chat", false);
    // </1.1.8>

    // <1.1.9>
    BedwarsPRO.getInstance().getConfig().addDefault("specials.trap.play-sound", true);
    // </1.1.9>

    // <1.1.11>
    BedwarsPRO.getInstance().getConfig().addDefault("specials.magnetshoe.probability", 75);
    BedwarsPRO.getInstance().getConfig().addDefault("specials.magnetshoe.boots", "IRON_BOOTS");
    // </1.1.11>

    // <1.1.13>
    BedwarsPRO.getInstance().getConfig().addDefault("specials.rescue-platform.block", "GLASS");
    BedwarsPRO.getInstance().getConfig().addDefault("specials.rescue-platform.block", "BLAZE_ROD");
    BedwarsPRO.getInstance().getConfig().addDefault("ingame-chatformat-all",
        "[$all$] <$team$>$player$: $msg$");
    BedwarsPRO.getInstance().getConfig().addDefault("ingame-chatformat", "<$team$>$player$: $msg$");
    // </1.1.13>

    // <1.1.14>
    BedwarsPRO.getInstance().getConfig().addDefault("overwrite-names", false);
    BedwarsPRO.getInstance().getConfig().addDefault("specials.protection-wall.break-time", 0);
    BedwarsPRO.getInstance().getConfig().addDefault("specials.protection-wall.wait-time", 20);
    BedwarsPRO.getInstance().getConfig().addDefault("specials.protection-wall.can-break", true);
    BedwarsPRO.getInstance().getConfig().addDefault("specials.protection-wall.item", "BRICK");
    BedwarsPRO.getInstance().getConfig().addDefault("specials.protection-wall.block", "SANDSTONE");
    BedwarsPRO.getInstance().getConfig().addDefault("specials.protection-wall.width", 4);
    BedwarsPRO.getInstance().getConfig().addDefault("specials.protection-wall.height", 4);
    BedwarsPRO.getInstance().getConfig().addDefault("specials.protection-wall.distance", 2);

    if (BedwarsPRO.getInstance().getCurrentVersion().startsWith("v1_8")) {
      BedwarsPRO.getInstance().getConfig().addDefault("bed-sound", "ENDERDRAGON_GROWL");
    } else {
      BedwarsPRO.getInstance().getConfig().addDefault("bed-sound", "ENTITY_ENDERDRAGON_GROWL");
    }

    try {
      Sound.valueOf(
          BedwarsPRO.getInstance().getStringConfig("bed-sound", "ENDERDRAGON_GROWL").toUpperCase());
    } catch (Exception e) {
      if (BedwarsPRO.getInstance().getCurrentVersion().startsWith("v1_8")) {
        BedwarsPRO.getInstance().getConfig().set("bed-sound", "ENDERDRAGON_GROWL");
      } else {
        BedwarsPRO.getInstance().getConfig().set("bed-sound", "ENTITY_ENDERDRAGON_GROWL");
      }
    }
    // </1.1.14>

    // <1.1.15>
    BedwarsPRO.getInstance().getConfig().addDefault("store-game-records", true);
    BedwarsPRO.getInstance().getConfig().addDefault("store-game-records-holder", true);
    BedwarsPRO.getInstance().getConfig().addDefault("statistics.scores.record", 100);
    BedwarsPRO.getInstance().getConfig().addDefault("game-block", "BED_BLOCK");
    // </1.1.15>

    // <1.2.0>
    BedwarsPRO.getInstance().getConfig().addDefault("titles.win.enabled", true);
    BedwarsPRO.getInstance().getConfig().addDefault("titles.win.title-fade-in", 1.5);
    BedwarsPRO.getInstance().getConfig().addDefault("titles.win.title-stay", 5.0);
    BedwarsPRO.getInstance().getConfig().addDefault("titles.win.title-fade-out", 2.0);
    BedwarsPRO.getInstance().getConfig().addDefault("titles.win.subtitle-fade-in", 1.5);
    BedwarsPRO.getInstance().getConfig().addDefault("titles.win.subtitle-stay", 5.0);
    BedwarsPRO.getInstance().getConfig().addDefault("titles.win.subtitle-fade-out", 2.0);
    BedwarsPRO.getInstance().getConfig().addDefault("titles.map.enabled", false);
    BedwarsPRO.getInstance().getConfig().addDefault("titles.map.title-fade-in", 1.5);
    BedwarsPRO.getInstance().getConfig().addDefault("titles.map.title-stay", 5.0);
    BedwarsPRO.getInstance().getConfig().addDefault("titles.map.title-fade-out", 2.0);
    BedwarsPRO.getInstance().getConfig().addDefault("titles.map.subtitle-fade-in", 1.5);
    BedwarsPRO.getInstance().getConfig().addDefault("titles.map.subtitle-stay", 5.0);
    BedwarsPRO.getInstance().getConfig().addDefault("titles.map.subtitle-fade-out", 2.0);
    BedwarsPRO.getInstance().getConfig().addDefault("player-drops", false);
    BedwarsPRO.getInstance().getConfig().addDefault("bungeecord.spigot-restart", true);
    BedwarsPRO.getInstance().getConfig().addDefault("place-in-liquid", true);
    BedwarsPRO.getInstance().getConfig().addDefault("friendlybreak", true);
    BedwarsPRO.getInstance().getConfig().addDefault("breakable-blocks", Arrays.asList("none"));
    BedwarsPRO.getInstance().getConfig().addDefault("update-infos", true);
    BedwarsPRO.getInstance().getConfig().addDefault("lobby-chatformat", "$player$: $msg$");
    // <1.2.0>

    // <1.2.1>
    BedwarsPRO.getInstance().getConfig().addDefault("statistics.bed-destroyed-kills", false);
    BedwarsPRO.getInstance().getConfig().addDefault("rewards.player-destroy-bed",
        Arrays.asList("/example {player} {score}"));
    BedwarsPRO.getInstance().getConfig().addDefault("rewards.player-kill",
        Arrays.asList("/example {player} 10"));
    BedwarsPRO.getInstance().getConfig().addDefault("specials.tntsheep.fuse-time", 8.0);
    BedwarsPRO.getInstance().getConfig().addDefault("titles.countdown.enabled", true);
    BedwarsPRO.getInstance().getConfig().addDefault("titles.countdown.format", "&3{countdown}");
    BedwarsPRO.getInstance().getConfig().addDefault("specials.tntsheep.speed", 0.4D);
    // </1.2.1>

    // <1.2.2>
    BedwarsPRO.getInstance().getConfig().addDefault("global-autobalance", false);
    BedwarsPRO.getInstance().getConfig().addDefault("scoreboard.format-bed-destroyed",
        "&c$status$ $team$");
    BedwarsPRO
        .getInstance().getConfig().addDefault("scoreboard.format-bed-alive", "&a$status$ $team$");
    BedwarsPRO
        .getInstance().getConfig().addDefault("scoreboard.format-title", "&e$region$&f - $time$");
    BedwarsPRO.getInstance().getConfig().addDefault("teamname-on-tab", false);
    // </1.2.2>

    // <1.2.3>
    BedwarsPRO.getInstance().getConfig().addDefault("bungeecord.motds.full", "&c[Full]");
    BedwarsPRO.getInstance().getConfig().addDefault("teamname-in-chat", false);
    BedwarsPRO.getInstance().getConfig().addDefault("hearts-on-death", true);
    BedwarsPRO.getInstance().getConfig().addDefault("lobby-scoreboard.title", "&eBEDWARS");
    BedwarsPRO.getInstance().getConfig().addDefault("lobby-scoreboard.enabled", true);
    BedwarsPRO.getInstance().getConfig().addDefault("lobby-scoreboard.content",
        Arrays.asList("", "&fMap: &2$regionname$", "&fPlayers: &2$players$&f/&2$maxplayers$", "",
            "&fWaiting ...", ""));
    BedwarsPRO.getInstance().getConfig().addDefault("jointeam-entity.show-name", true);
    // </1.2.3>

    // <1.2.6>
    BedwarsPRO.getInstance().getConfig().addDefault("die-on-void", false);
    BedwarsPRO.getInstance().getConfig().addDefault("global-chat-after-end", true);
    // </1.2.6>

    // <1.2.7>
    BedwarsPRO.getInstance().getConfig().addDefault("holographic-stats.show-prefix", false);
    BedwarsPRO.getInstance().getConfig().addDefault("holographic-stats.name-color", "&7");
    BedwarsPRO.getInstance().getConfig().addDefault("holographic-stats.value-color", "&e");
    BedwarsPRO.getInstance().getConfig().addDefault("holographic-stats.head-line",
        "Your &eBEDWARS&f stats");
    BedwarsPRO.getInstance().getConfig().addDefault("lobby-gamemode", 0);
    BedwarsPRO.getInstance().getConfig().addDefault("statistics.show-on-game-end", true);
    BedwarsPRO.getInstance().getConfig().addDefault("allow-crafting", false);
    // </1.2.7>

    // <1.2.8>
    BedwarsPRO.getInstance().getConfig().addDefault("specials.tntsheep.explosion-factor", 1.0);
    BedwarsPRO.getInstance().getConfig().addDefault("bungeecord.full-restart", true);
    BedwarsPRO.getInstance().getConfig().addDefault("lobbytime-full", 15);
    BedwarsPRO.getInstance().getConfig().addDefault("bungeecord.endgame-in-lobby", true);
    // </1.2.8>

    // <1.3.0>
    BedwarsPRO.getInstance().getConfig().addDefault("hearts-in-halfs", true);
    // </1.3.0>

    // <1.3.1>
    if (BedwarsPRO.getInstance().getConfig().isString("chat-to-all-prefix")) {
      String chatToAllPrefixString = BedwarsPRO.getInstance().getConfig()
          .getString("chat-to-all-prefix");
      BedwarsPRO.getInstance().getConfig().set("chat-to-all-prefix",
          Arrays.asList(chatToAllPrefixString));
    }
    if (BedwarsPRO.getInstance().getConfig().isList("breakable-blocks")) {
      List<String> breakableBlocks =
          (List<String>) BedwarsPRO.getInstance().getConfig().getList("breakable-blocks");
      BedwarsPRO.getInstance().getConfig().set("breakable-blocks.list", breakableBlocks);
    }
    BedwarsPRO.getInstance().getConfig().addDefault("breakable-blocks.use-as-blacklist", false);
    // </1.3.1>

    // <1.3.2>
    BedwarsPRO.getInstance().getConfig().addDefault("statistics.player-leave-kills", false);

    List<PotionEffect> oldPotions = new ArrayList<PotionEffect>();

    if (BedwarsPRO.getInstance().getConfig().getBoolean("specials.trap.blindness.enabled")) {
      oldPotions.add(new PotionEffect(PotionEffectType.BLINDNESS,
          BedwarsPRO.getInstance().getConfig().getInt("specials.trap.duration"),
          BedwarsPRO.getInstance().getConfig().getInt("specials.trap.blindness.amplifier"), true,
          BedwarsPRO.getInstance().getConfig().getBoolean("specials.trap.show-particles")));
    }
    if (BedwarsPRO.getInstance().getConfig().getBoolean("specials.trap.slowness.enabled")) {
      oldPotions.add(new PotionEffect(PotionEffectType.SLOW,
          BedwarsPRO.getInstance().getConfig().getInt("specials.trap.duration"),
          BedwarsPRO.getInstance().getConfig().getInt("specials.trap.slowness.amplifier"), true,
          BedwarsPRO.getInstance().getConfig().getBoolean("specials.trap.show-particles")));
    }
    if (BedwarsPRO.getInstance().getConfig().getBoolean("specials.trap.weakness.enabled")) {
      oldPotions.add(new PotionEffect(PotionEffectType.WEAKNESS,
          BedwarsPRO.getInstance().getConfig().getInt("specials.trap.duration"),
          BedwarsPRO.getInstance().getConfig().getInt("specials.trap.weakness.amplifier"), true,
          BedwarsPRO.getInstance().getConfig().getBoolean("specials.trap.show-particles")));
    }
    BedwarsPRO.getInstance().getConfig().addDefault("specials.trap.effects", oldPotions);
    BedwarsPRO.getInstance().getConfig().set("specials.trap.duration", null);
    BedwarsPRO.getInstance().getConfig().set("specials.trap.blindness", null);
    BedwarsPRO.getInstance().getConfig().set("specials.trap.slowness", null);
    BedwarsPRO.getInstance().getConfig().set("specials.trap.weakness", null);
    BedwarsPRO.getInstance().getConfig().set("specials.trap.show-particles", null);

    List<PotionEffect> potionEffectList = new ArrayList<>();
    potionEffectList.add(new PotionEffect(PotionEffectType.BLINDNESS, 5 * 20, 2, true, true));
    potionEffectList.add(new PotionEffect(PotionEffectType.WEAKNESS, 5 * 20, 2, true, true));
    potionEffectList.add(new PotionEffect(PotionEffectType.SLOW, 5 * 20, 2, true, true));
    BedwarsPRO.getInstance().getConfig().addDefault("specials.trap.effects", potionEffectList);
    // </1.3.2>

    // <1.3.3>
    BedwarsPRO.getInstance().getConfig().addDefault("show-team-in-actionbar", false);
    BedwarsPRO.getInstance().getConfig().addDefault("send-error-data", true);
    BedwarsPRO.getInstance().getConfig().addDefault("player-settings.old-shop-as-default", false);
    // </1.3.3>

    // <1.3.4>
    BedwarsPRO.getInstance().getConfig().addDefault("keep-inventory-on-death", false);
    BedwarsPRO.getInstance().getConfig().addDefault("use-internal-shop", true);
    BedwarsPRO.getInstance().getConfig().addDefault("save-inventory", true);
    BedwarsPRO.getInstance().getConfig().addDefault("specials.arrow-blocker.protection-time", 10);
    BedwarsPRO.getInstance().getConfig().addDefault("specials.arrow-blocker.using-wait-time", 30);
    BedwarsPRO.getInstance().getConfig().addDefault("specials.arrow-blocker.item", "ender_eye");
    // </1.3.4>

    // <1.3.5>
    BedwarsPRO.getInstance().getConfig().addDefault("spawn-resources-in-chest", true);
    BedwarsPRO.getInstance().getConfig().addDefault("database.table-prefix", "bw_");
    BedwarsPRO.getInstance().getConfig().addDefault("quickstash-database.enabled", false);
    Object ressourceObject = BedwarsPRO.getInstance().getConfig().get("ressource");
    if (ressourceObject != null) {
      BedwarsPRO.getInstance().getConfig().set("resource", ressourceObject);
      BedwarsPRO.getInstance().getConfig().set("ressource", null);
    }

    ConfigurationSection resourceSection = BedwarsPRO.getInstance().getConfig()
        .getConfigurationSection("resource");
    if (resourceSection != null) {
      for (Entry<String, Object> entry : resourceSection.getValues(false).entrySet()) {
        if (!BedwarsPRO.getInstance().getConfig().isList("resource." + entry.getKey() + ".item")) {
          ItemStackParser parser = new ItemStackParser(entry.getValue());
          ItemStack item = parser.parse();
          if (item != null) {
            List<Map<String, Object>> itemList = new ArrayList<>();
            itemList.add(item.serialize());
            resourceSection.set(entry.getKey() + ".item", itemList);
            resourceSection.set(entry.getKey() + ".amount", null);
            resourceSection.set(entry.getKey() + ".name", null);
          }
        }
      }
    }
    // </1.3.5>
  }
}
