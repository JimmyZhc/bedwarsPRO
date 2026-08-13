package io.jmmym.bedwarspro.scoreboard.config;

import net.citizensnpcs.api.CitizensAPI;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MapView.Scale;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.scoreboard.Main;
import io.jmmym.bedwarspro.scoreboard.utils.ColorUtil;

public class Config {

	private static FileConfiguration file_config;
	private static FileConfiguration language_config;
	public static boolean update_check_enabled;
	public static boolean update_check_report;
	public static boolean hide_player;
	public static boolean tab_health;
	public static boolean tag_health;
	public static boolean item_merge;
	public static boolean hunger_change;
	public static boolean clear_bottle;
	public static boolean fast_respawn;
	public static String date_format;
	public static boolean chat_format_enabled;
	public static boolean chat_format_chat_lobby;
	public static boolean chat_format_chat_all;
	public static boolean chat_format_chat_spectator;
	public static String chat_format_lobby;
	public static String chat_format_lobby_team;
	public static List<String> chat_format_all_prefix;
	public static String chat_format_ingame;
	public static String chat_format_ingame_all;
	public static String chat_format_spectator;
	public static boolean final_killed_enabled;
	public static String final_killed_message;
	public static List<String> timecommand_startcommand;
	public static boolean select_team_enabled;
	public static String select_team_status_select;
	public static String select_team_status_inteam;
	public static String select_team_status_team_full;
	public static String select_team_no_players;
	public static String select_team_item_name;
	public static List<String> select_team_item_lore;
	public static boolean lobby_block_enabled;
	public static int lobby_block_position_1_x;
	public static int lobby_block_position_1_y;
	public static int lobby_block_position_1_z;
	public static int lobby_block_position_2_x;
	public static int lobby_block_position_2_y;
	public static int lobby_block_position_2_z;
	public static boolean rejoin_enabled;
	public static String rejoin_message_rejoin;
	public static String rejoin_message_error;
	public static boolean bowdamage_enabled;
	public static String bowdamage_title;
	public static String bowdamage_subtitle;
	public static String bowdamage_message;
	public static boolean damagetitle_enabled;
	public static String damagetitle_title;
	public static String damagetitle_subtitle;
	public static boolean jointitle_enabled;
	public static String jointitle_title;
	public static String jointitle_subtitle;
	
	public static boolean destroyed_title_enabled;
	public static String destroyed_title_title;
	public static String destroyed_title_subtitle;
	public static boolean start_title_enabled;
	public static List<String> start_title_title;
	public static String start_title_subtitle;
	public static boolean victory_title_enabled;
	public static List<String> victory_title_title;
	public static String victory_title_subtitle;
	public static boolean play_sound_enabled;
	public static List<String> play_sound_sound_start;
	public static List<String> play_sound_sound_death;
	public static List<String> play_sound_sound_kill;
	public static List<String> play_sound_sound_upgrade;
	public static List<String> play_sound_sound_no_resource;
	public static List<String> play_sound_sound_sethealth;
	public static List<String> play_sound_sound_enable_witherbow;
	public static List<String> play_sound_sound_witherbow;
	public static List<String> play_sound_sound_deathmode;
	public static List<String> play_sound_sound_over;
	public static boolean spectator_enabled;
	public static boolean spectator_centre_enabled;
	public static double spectator_centre_height;
	public static String spectator_spectator_target_title;
	public static String spectator_spectator_target_subtitle;
	public static String spectator_quit_spectator_title;
	public static String spectator_quit_spectator_subtitle;
	public static boolean spectator_speed_enabled;
	public static int spectator_speed_slot;
	public static int spectator_speed_item;
	public static String spectator_speed_item_name;
	public static List<String> spectator_speed_item_lore;
	public static String spectator_speed_gui_title;
	public static String spectator_speed_no_speed;
	public static String spectator_speed_speed_1;
	public static String spectator_speed_speed_2;
	public static String spectator_speed_speed_3;
	public static String spectator_speed_speed_4;
	public static boolean spectator_fast_join_enabled;
	public static int spectator_fast_join_slot;
	public static int spectator_fast_join_item;
	public static String spectator_fast_join_item_name;
	public static List<String> spectator_fast_join_item_lore;
	public static String spectator_fast_join_group;
	public static boolean graffiti_enabled;
	public static boolean graffiti_holographic_enabled;
	public static String graffiti_holographic_text;
	public static boolean shop_enabled;
	public static String shop_item_shop_type;
	public static String shop_item_shop_skin;
	public static boolean shop_item_shop_look;
	public static List<String> shop_item_shop_name;
	public static boolean respawn_enabled;
	public static boolean respawn_centre_enabled;
	public static double respawn_centre_height;
	public static boolean respawn_protected_enabled;
	public static int respawn_protected_time;
	public static int respawn_respawn_delay;
	public static String respawn_countdown_title;
	public static String respawn_countdown_subtitle;
	public static String respawn_countdown_message;
	public static String respawn_respawn_title;
	public static String respawn_respawn_subtitle;
	public static String respawn_respawn_message;
	public static boolean giveitem_keeparmor;
	public static Map<String, Object> giveitem_armor_helmet_item;
	public static Map<String, Object> giveitem_armor_chestplate_item;
	public static Map<String, Object> giveitem_armor_leggings_item;
	public static Map<String, Object> giveitem_armor_boots_item;
	public static String giveitem_armor_helmet_give;
	public static String giveitem_armor_chestplate_give;
	public static String giveitem_armor_leggings_give;
	public static String giveitem_armor_boots_give;
	public static boolean giveitem_armor_helmet_move;
	public static boolean giveitem_armor_chestplate_move;
	public static boolean giveitem_armor_leggings_move;
	public static boolean giveitem_armor_boots_move;
	public static boolean sethealth_start_enabled;
	public static int sethealth_start_health;
	public static boolean resourcelimit_enabled;
	public static List<String[]> resourcelimit_limit;
	public static boolean spread_resource_enabled;
	public static boolean spread_resource_launch;
	public static double spread_resource_range;
	public static boolean game_chest_enabled;
	public static int game_chest_range;
	public static String game_chest_message;
	public static boolean invisibility_player_enabled;
	public static boolean invisibility_player_footstep;
	public static boolean invisibility_player_hide_particles;
	public static boolean invisibility_player_damage_show_player;
	public static boolean witherbow_enabled;
	public static int witherbow_gametime;
	public static String witherbow_already_starte;
	public static String witherbow_title;
	public static String witherbow_subtitle;
	public static String witherbow_message;
	public static boolean deathmode_enabled;
	public static int deathmode_gametime;
	public static String deathmode_title;
	public static String deathmode_subtitle;
	public static String deathmode_message;
	public static boolean deathitem_enabled;
	public static List<String> deathitem_items;
	public static boolean deathitem_item_name_chinesize;
	public static String deathitem_message;
	public static boolean nobreakbed_enabled;
	public static int nobreakbed_gametime;
	public static String nobreakbed_nobreakmessage;
	public static String nobreakbed_title;
	public static String nobreakbed_subtitle;
	public static String nobreakbed_message;
	public static boolean spawn_no_build_spawn_enabled;
	public static int spawn_no_build_spawn_range;
	public static boolean spawn_no_build_resource_enabled;
	public static int spawn_no_build_resource_range;
	public static String spawn_no_build_message;
	public static boolean holographic_resource_enabled;
	public static boolean holographic_bed_title_bed_alive_enabled;
	public static boolean holographic_bed_title_bed_destroyed_enabled;
	public static double holographic_resource_speed;
	public static List<String> holographic_resource;
	public static String holographic_bedtitle_bed_alive_title;
	public static String holographic_bedtitle_bed_destroyed_title;
	
	public static String actionbar;
	public static Map<String, Integer> timer;
	public static List<String> planinfo;
	public static String playertag_prefix;
	public static String playertag_suffix;
	public static int scoreboard_interval;
	public static String scoreboard_you;
	public static String scoreboard_team_bed_status_bed_alive;
	public static String scoreboard_team_bed_status_bed_destroyed;
	public static String scoreboard_team_bed_status_bed_alive_empty;
	public static String scoreboard_team_status_format_bed_alive;
	public static String scoreboard_team_status_format_bed_destroyed;
	public static String scoreboard_team_status_format_bed_alive_empty;
	public static String scoreboard_team_status_format_team_dead;
	public static boolean lobby_scoreboard_enabled;
	public static int lobby_scoreboard_interval;
	public static Map<String, List<String>> game_shop_item;
	public static Map<String, String> game_shop_shops;
	public static Map<String, Map<String, List<Location>>> game_team_spawner;
	public static Map<String, String> game_team_spawners;
	public static List<MapView> image_maps;

	private static FileConfiguration getVerifiedConfig(String fileName) {
		Map<String, String> configVersion = new HashMap<String, String>();
		configVersion.put("config.yml", "23");
		configVersion.put("language.yml", "4");
		configVersion.put("bwsba-language.yml", "4");
		File file = new File(Main.getInstance().getDataFolder(), "/locale/" + fileName);
		// 迁移旧文件名/旧位置到 locale/ 下（根目录 language.yml、locale/language.yml、根目录 bwsba-language.yml），保留用户自定义内容
		if (!file.exists()) {
			File[] oldFiles = new File[] {
					new File(Main.getInstance().getDataFolder(), "/" + fileName),
					new File(Main.getInstance().getDataFolder(), "/language.yml"),
					new File(Main.getInstance().getDataFolder(), "/locale/language.yml") };
			for (File oldFile : oldFiles) {
				if (oldFile.exists()) {
					oldFile.renameTo(file);
					break;
				}
			}
		}
		if (!file.exists()) {
			Main.getInstance().getLocaleConfig().saveResource(fileName);
			// 等待文件创建完成
			int waitTicks = 0;
			while (!file.exists() && waitTicks < 20) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					break;
				}
				waitTicks++;
			}
			if (!file.exists()) {
				// 如果文件仍然不存在，创建一个空的配置文件
				try {
					file.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			return YamlConfiguration.loadConfiguration(file);
		}
		FileConfiguration config = YamlConfiguration.loadConfiguration(file);
		if (!config.contains("version") || !config.getString("version").equals(configVersion.getOrDefault(fileName, ""))) {
			file.renameTo(new File(Main.getInstance().getDataFolder(), "/locale/" + fileName.split("\\.")[0] + "_" + new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date()) + "_old.yml"));
			Main.getInstance().getLocaleConfig().saveResource(fileName);
			// 等待文件创建完成
			int waitTicks = 0;
			while (!file.exists() && waitTicks < 20) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					break;
				}
				waitTicks++;
			}
			if (!file.exists()) {
				try {
					file.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			config = YamlConfiguration.loadConfiguration(file);
		}
		return config;
	}

	public static void loadConfig() {
		Main.getInstance().getEditHolographicManager().removeAll();
		String prefix = "[" + Main.getInstance().getDescription().getName() + "] ";
		
		// 在方法开始处声明变量，确保所有代码都能访问
		FileConfiguration config = null;
		
		try {
			Bukkit.getConsoleSender().sendMessage(prefix + Main.getInstance().getLocaleConfig().getLanguage("loading_config"));
			File folder = new File(Main.getInstance().getDataFolder(), "/");
			if (!folder.exists()) {
				folder.mkdirs();
			}
			Main.getInstance().getLocaleConfig().loadLocaleConfig();
			file_config = Main.getInstance().getConfig();
			
			try {
				language_config = getVerifiedConfig("bwsba-language.yml");
			} catch (Exception e) {
				Bukkit.getConsoleSender().sendMessage(prefix + "加载 bwsba-language.yml 失败: " + e.getMessage());
				e.printStackTrace();
			}
			
			Bukkit.getConsoleSender().sendMessage(prefix + Main.getInstance().getLocaleConfig().getLanguage("saved_config"));
			Main.getInstance().reloadConfig();
			config = Main.getInstance().getConfig();
			
			Bukkit.getConsoleSender().sendMessage(prefix + "开始读取配置项...");
			
			try {
				update_check_enabled = config.getBoolean("update_check.enabled");
				update_check_report = config.getBoolean("update_check.report");
				hide_player = config.getBoolean("hide_player");
				tab_health = config.getBoolean("tab_health");
				tag_health = config.getBoolean("tag_health");
				item_merge = config.getBoolean("item_merge");
				hunger_change = config.getBoolean("hunger_change");
				clear_bottle = config.getBoolean("clear_bottle");
				fast_respawn = config.getBoolean("fast_respawn");
				date_format = config.getString("date_format");
			} catch (Exception e) {
				Bukkit.getConsoleSender().sendMessage(prefix + "读取基础配置失败: " + e.getMessage());
				e.printStackTrace();
			}
			
			try {
				chat_format_enabled = config.getBoolean("chat_format.enabled");
				chat_format_chat_lobby = config.getBoolean("chat_format.chat.lobby");
				chat_format_chat_all = config.getBoolean("chat_format.chat.all");
				chat_format_chat_spectator = config.getBoolean("chat_format.chat.spectator");
				chat_format_all_prefix = config.getStringList("chat_format.all_prefix");
				chat_format_lobby = ColorUtil.color(config.getString("chat_format.lobby"));
				chat_format_lobby_team = ColorUtil.color(config.getString("chat_format.lobby_team"));
				chat_format_ingame = ColorUtil.color(config.getString("chat_format.ingame"));
				chat_format_ingame_all = ColorUtil.color(config.getString("chat_format.ingame_all"));
				chat_format_spectator = ColorUtil.color(config.getString("chat_format.spectator"));
			} catch (Exception e) {
				Bukkit.getConsoleSender().sendMessage(prefix + "读取聊天格式配置失败: " + e.getMessage());
				e.printStackTrace();
			}
			
			try {
				final_killed_enabled = config.getBoolean("final_killed.enabled");
				final_killed_message = ColorUtil.color(config.getString("final_killed.message"));
				timecommand_startcommand = ColorUtil.colorList(config.getStringList("timecommand.startcommand"));
			} catch (Exception e) {
				Bukkit.getConsoleSender().sendMessage(prefix + "读取击杀/时间配置失败: " + e.getMessage());
				e.printStackTrace();
			}
			
			// 安全读取 giveitem 配置（可能不存在）
			try {
				giveitem_keeparmor = config.getBoolean("giveitem.keeparmor");
				List<?> helmetItemList = config.getList("giveitem.armor.helmet.item");
				if (helmetItemList != null && helmetItemList.size() > 0) {
					giveitem_armor_helmet_item = (Map<String, Object>) helmetItemList.get(0);
				}
				List<?> chestplateItemList = config.getList("giveitem.armor.chestplate.item");
				if (chestplateItemList != null && chestplateItemList.size() > 0) {
					giveitem_armor_chestplate_item = (Map<String, Object>) chestplateItemList.get(0);
				}
				List<?> leggingsItemList = config.getList("giveitem.armor.leggings.item");
				if (leggingsItemList != null && leggingsItemList.size() > 0) {
					giveitem_armor_leggings_item = (Map<String, Object>) leggingsItemList.get(0);
				}
				List<?> bootsItemList = config.getList("giveitem.armor.boots.item");
				if (bootsItemList != null && bootsItemList.size() > 0) {
					giveitem_armor_boots_item = (Map<String, Object>) bootsItemList.get(0);
				}
				giveitem_armor_helmet_give = config.getString("giveitem.armor.helmet.give");
				giveitem_armor_chestplate_give = config.getString("giveitem.armor.chestplate.give");
				giveitem_armor_leggings_give = config.getString("giveitem.armor.leggings.give");
				giveitem_armor_boots_give = config.getString("giveitem.armor.boots.give");
				giveitem_armor_helmet_move = config.getBoolean("giveitem.armor.helmet.move");
				giveitem_armor_chestplate_move = config.getBoolean("giveitem.armor.chestplate.move");
				giveitem_armor_leggings_move = config.getBoolean("giveitem.armor.leggings.move");
				giveitem_armor_boots_move = config.getBoolean("giveitem.armor.boots.move");
			} catch (Exception e) {
				Bukkit.getConsoleSender().sendMessage(prefix + "读取 giveitem 配置失败: " + e.getMessage());
				e.printStackTrace();
			}

			// 安全读取 respawn 配置
			try {
				respawn_enabled = config.getBoolean("respawn.enabled", true);
				respawn_centre_enabled = config.getBoolean("respawn.centre.enabled", true);
				respawn_centre_height = config.getDouble("respawn.centre.height", 120);
				respawn_protected_enabled = config.getBoolean("respawn.protected.enabled", true);
				respawn_protected_time = config.getInt("respawn.protected.time", 5);
				respawn_respawn_delay = config.getInt("respawn.respawn_delay", 5);
				respawn_countdown_title = ColorUtil.color(config.getString("respawn.countdown.title", "&e{respawntime}"));
				respawn_countdown_subtitle = ColorUtil.color(config.getString("respawn.countdown.subtitle", "&a秒后复活"));
				respawn_countdown_message = ColorUtil.color(config.getString("respawn.countdown.message", ""));
				respawn_respawn_title = ColorUtil.color(config.getString("respawn.respawn.title", "&a已重生"));
				respawn_respawn_subtitle = ColorUtil.color(config.getString("respawn.respawn.subtitle", ""));
				respawn_respawn_message = ColorUtil.color(config.getString("respawn.respawn.message", "&a你已重生！"));
			} catch (Exception e) {
				Bukkit.getConsoleSender().sendMessage(prefix + "读取 respawn 配置失败: " + e.getMessage());
				e.printStackTrace();
			}

			Bukkit.getConsoleSender().sendMessage(prefix + "配置文件加载成功！");
			
		} catch (Exception e) {
			Bukkit.getConsoleSender().sendMessage(prefix + "加载配置时发生严重错误: " + e.getMessage());
			e.printStackTrace();
			throw e;
		}
		// 以下是原有的配置读取代码（变量已在方法开始处声明）
		spread_resource_enabled = config.getBoolean("spread_resource.enabled");
		spread_resource_launch = config.getBoolean("spread_resource.launch");
		spread_resource_range = config.getDouble("spread_resource.range");
		game_chest_enabled = config.getBoolean("game_chest.enabled");
		game_chest_range = config.getInt("game_chest.range");
		game_chest_message = ColorUtil.color(config.getString("game_chest.message"));
		invisibility_player_enabled = config.getBoolean("invisibility_player.enabled");
		invisibility_player_footstep = config.getBoolean("invisibility_player.footstep");
		invisibility_player_hide_particles = config.getBoolean("invisibility_player.hide_particles");
		invisibility_player_damage_show_player = config.getBoolean("invisibility_player.damage_show_player");
		witherbow_enabled = config.getBoolean("witherbow.enabled");
		witherbow_gametime = config.getInt("witherbow.gametime");
		witherbow_already_starte = ColorUtil.color(config.getString("witherbow.already_starte"));
		witherbow_title = ColorUtil.color(config.getString("witherbow.title"));
		witherbow_subtitle = ColorUtil.color(config.getString("witherbow.subtitle"));
		witherbow_message = ColorUtil.color(config.getString("witherbow.message"));
		// 资源限制配置
		resourcelimit_enabled = config.getBoolean("resourcelimit.enabled");
		resourcelimit_limit = new ArrayList<String[]>();
		List<String> resourcelimitList = config.getStringList("resourcelimit.limit");
		if (resourcelimitList != null) {
			for (String s : resourcelimitList) {
				resourcelimit_limit.add(s.split(","));
			}
		}
		shop_enabled = config.getBoolean("shop.enabled", true);
		shop_item_shop_type = config.getString("shop.item_shop.type", "VILLAGER");
		shop_item_shop_skin = config.getString("shop.item_shop.skin", "Steve");
		shop_item_shop_look = config.getBoolean("shop.item_shop.look", false);
		shop_item_shop_name = ColorUtil.colorList(config.getStringList("shop.item_shop.name"));
		deathmode_enabled = config.getBoolean("deathmode.enabled");
		deathmode_gametime = config.getInt("deathmode.gametime");
		deathmode_title = ColorUtil.color(config.getString("deathmode.title"));
		deathmode_subtitle = ColorUtil.color(config.getString("deathmode.subtitle"));
		deathmode_message = ColorUtil.color(config.getString("deathmode.message"));
		deathitem_enabled = config.getBoolean("deathitem.enabled");
		deathitem_items = config.getStringList("deathitem.items");
		deathitem_item_name_chinesize = config.getBoolean("deathitem.item_name_chinesize");
		deathitem_message = ColorUtil.color(config.getString("deathitem.message"));
		nobreakbed_nobreakmessage = ColorUtil.color(config.getString("nobreakbed.nobreakmessage"));
		nobreakbed_enabled = config.getBoolean("nobreakbed.enabled");
		nobreakbed_gametime = config.getInt("nobreakbed.gametime");
		nobreakbed_title = ColorUtil.color(config.getString("nobreakbed.title"));
		nobreakbed_subtitle = ColorUtil.color(config.getString("nobreakbed.subtitle"));
		nobreakbed_message = ColorUtil.color(config.getString("nobreakbed.message"));
		spawn_no_build_spawn_enabled = config.getBoolean("spawn_no_build.spawn.enabled");
		spawn_no_build_spawn_range = config.getInt("spawn_no_build.spawn.range");
		spawn_no_build_resource_enabled = config.getBoolean("spawn_no_build.resource.enabled");
		spawn_no_build_resource_range = config.getInt("spawn_no_build.resource.range");
		spawn_no_build_message = ColorUtil.color(config.getString("spawn_no_build.message"));
		holographic_resource_enabled = config.getBoolean("holographic.resource.enabled");
		holographic_bed_title_bed_alive_enabled = config.getBoolean("holographic.bed_title.bed_alive.enabled");
		holographic_bed_title_bed_destroyed_enabled = config.getBoolean("holographic.bed_title.bed_destroyed.enabled");
		holographic_resource_speed = config.getDouble("holographic.resource.speed");
		ConfigurationSection holographicResourceSection = config.getConfigurationSection("holographic.resource.resources");
		holographic_resource = new ArrayList<String>();
		if (holographicResourceSection != null) {
			holographic_resource.addAll(holographicResourceSection.getKeys(false));
		}
		holographic_bedtitle_bed_destroyed_title = ColorUtil.color(config.getString("holographic.bed_title.bed_destroyed.title"));
		holographic_bedtitle_bed_alive_title = ColorUtil.color(config.getString("holographic.bed_title.bed_alive.title"));

		actionbar = ColorUtil.color(config.getString("actionbar"));
		timer = new HashMap<String, Integer>();
		ConfigurationSection timerSection = config.getConfigurationSection("timer");
		if (timerSection != null) {
			for (String w : timerSection.getKeys(false)) {
				timer.put(w, config.getInt("timer." + w));
			}
		}
		planinfo = new ArrayList<String>();
		ConfigurationSection planinfoSection = config.getConfigurationSection("planinfo");
		if (planinfoSection != null) {
			planinfo.addAll(planinfoSection.getKeys(false));
		}
		playertag_prefix = ColorUtil.color(config.getString("playertag.prefix"));
		playertag_suffix = ColorUtil.color(config.getString("playertag.suffix"));
		scoreboard_interval = config.getInt("scoreboard.interval");
		scoreboard_you = ColorUtil.color(config.getString("scoreboard.you"));
		scoreboard_team_bed_status_bed_alive = ColorUtil.color(config.getString("scoreboard.team_bed_status.bed_alive"));
		scoreboard_team_bed_status_bed_destroyed = ColorUtil.color(config.getString("scoreboard.team_bed_status.bed_destroyed"));
		scoreboard_team_bed_status_bed_alive_empty = ColorUtil.color(config.getString("scoreboard.team_bed_status.bed_alive_empty"));
		scoreboard_team_status_format_bed_alive = ColorUtil.color(config.getString("scoreboard.team_status_format.bed_alive"));
		scoreboard_team_status_format_bed_destroyed = ColorUtil.color(config.getString("scoreboard.team_status_format.bed_destroyed"));
		scoreboard_team_status_format_bed_alive_empty = ColorUtil.color(config.getString("scoreboard.team_status_format.bed_alive_empty"));
		scoreboard_team_status_format_team_dead = ColorUtil.color(config.getString("scoreboard.team_status_format.team_dead"));
		lobby_scoreboard_enabled = config.getBoolean("lobby_scoreboard.enabled");
		lobby_scoreboard_interval = config.getInt("lobby_scoreboard.interval");
		loadGameConfig();
		loadImages();
		if (fast_respawn) {
			BedwarsPRO.getInstance().getConfig().set("die-on-void", false);
			BedwarsPRO.getInstance().saveConfig();
		}
		Bukkit.getConsoleSender().sendMessage(prefix + Main.getInstance().getLocaleConfig().getLanguage("config_success"));
	}

	public static FileConfiguration getConfig() {
		return file_config;
	}

	private static String conflict(List<String> lines, String line) {
		String l = line;
		for (int i = 0; i == 0;) {
			l = l + "§r";
			if (!lines.contains(l)) {
				return l;
			}
		}
		return l;
	}

	public static void setShop(String game, Location location, String type) {
		File file = getGameFile();
		FileConfiguration filec = YamlConfiguration.loadConfiguration(file);
		List<String> loc = new ArrayList<String>();
		if (filec.getStringList(game + ".shop." + type) != null) {
			loc.addAll(filec.getStringList(game + ".shop." + type));
		}
		loc.add(location.getWorld().getName() + ", " + location.getX() + ", " + location.getY() + ", " + location.getZ() + ", " + location.getYaw() + ", " + location.getPitch());
		filec.set(game + ".shop." + type, loc);
		try {
			filec.save(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
		loadGameConfig();
	}

	public static void removeShop(String data) {
		File file = getGameFile();
		FileConfiguration filec = YamlConfiguration.loadConfiguration(file);
		String path = data.split(" - ")[0];
		List<String> loc = new ArrayList<String>();
		if (filec.getStringList(path) != null) {
			loc.addAll(filec.getStringList(path));
		}
		loc.remove(data.split(" - ")[1]);
		filec.set(path, loc);
		try {
			filec.save(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
		loadGameConfig();
	}

	public static void setTeamSpawner(String game, String team, Location location) {
		File file = getGameFile();
		FileConfiguration filec = YamlConfiguration.loadConfiguration(file);
		List<String> loc = new ArrayList<String>();
		if (filec.getStringList(game + ".team_spawner." + team) != null) {
			loc.addAll(filec.getStringList(game + ".team_spawner." + team));
		}
		loc.add(location.getWorld().getName() + ", " + location.getX() + ", " + location.getY() + ", " + location.getZ() + ", " + location.getYaw() + ", " + location.getPitch());
		filec.set(game + ".team_spawner." + team, loc);
		try {
			filec.save(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
		loadGameConfig();
	}

	private static void loadGameConfig() {
		File file = getGameFile();
		game_shop_item = new HashMap<String, List<String>>();
		game_shop_shops = new HashMap<String, String>();
		game_team_spawner = new HashMap<String, Map<String, List<Location>>>();
		game_team_spawners = new HashMap<String, String>();
		int shopId = 0;
		int spawnerId = 0;
		if (!file.exists()) {
			return;
		}
		FileConfiguration config = YamlConfiguration.loadConfiguration(file);
		for (String game : config.getKeys(false)) {
			ConfigurationSection configSec = config.getConfigurationSection(game);
			if (configSec.contains("shop")) {
				ConfigurationSection configss = configSec.getConfigurationSection("shop");
				if (configss.contains("item")) {
					game_shop_item.put(game, configss.getStringList("item"));
					for (String shop : configss.getStringList("item")) {
						game_shop_shops.put(shopId + "", game + ".shop.item - " + shop);
						shopId++;
					}
				}
			}
			if (configSec.contains("team_spawner")) {
				ConfigurationSection configst = configSec.getConfigurationSection("team_spawner");
				Map<String, List<Location>> map = new HashMap<String, List<Location>>();
				for (String team : configst.getKeys(false)) {
					List<Location> locs = new ArrayList<Location>();
					for (String loc : configst.getStringList(team)) {
						Location location = toLocation(loc);
						if (location != null) {
							locs.add(location);
						}
					}
					map.put(team, locs);
					for (String spawner : configst.getStringList(team)) {
						game_team_spawners.put(spawnerId + "", game + ".team_spawner." + team + " - " + spawner);
						spawnerId++;
					}
				}
				game_team_spawner.put(game, map);
			}
		}
	}

	private static Location toLocation(String loc) {
		try {
			String[] ary = loc.split(", ");
			if (Bukkit.getWorld(ary[0]) != null) {
				Location location = new Location(Bukkit.getWorld(ary[0]), Double.valueOf(ary[1]), Double.valueOf(ary[2]), Double.valueOf(ary[3]));
				if (ary.length > 4) {
					location.setYaw(Float.valueOf(ary[4]));
					location.setPitch(Float.valueOf(ary[5]));
				}
				return location;
			}
		} catch (Exception e) {
			return null;
		}
		return null;
	}

	public static void addShopNPC(Integer id) {
		File folder = getNPCFile();
		FileConfiguration config = YamlConfiguration.loadConfiguration(folder);
		List<String> npcs = new ArrayList<String>();
		if (config.getKeys(false).contains("npcs")) {
			npcs.addAll(config.getStringList("npcs"));
		}
		npcs.add(id + "");
		config.set("npcs", npcs);
		try {
			config.save(folder);
		} catch (IOException e) {
		}
	}

	private static void loadImages() {
		image_maps = new ArrayList<MapView>();
		File folder = new File(Main.getInstance().getDataFolder(), "/images");
		if (!folder.exists()) {
			folder.mkdirs();
		}
		// 版本日志 README.txt 每次启动都用 jar 内的最新内容覆盖，确保更新说明同步
		try {
			writeToLocal(folder.getPath() + "/README.txt", Main.getInstance().getResource("images/README.txt"));
		} catch (Exception e) {
		}
		// 默认涂鸦图片仅在首次生成时写入，避免覆盖玩家已放入的图片
		File defaultImage = new File(folder.getPath() + "/1.jpg");
		if (!defaultImage.exists()) {
			try {
				writeToLocal(defaultImage.getPath(), Main.getInstance().getResource("images/1.jpg"));
			} catch (Exception e) {
			}
		}
		for (File file : folder.listFiles()) {
			if (!isImage(file)) {
				continue;
			}
			try {
				MapView map = Bukkit.createMap(Bukkit.getWorlds().get(0));
				map.setCenterX(Integer.MAX_VALUE);
				map.setCenterZ(Integer.MAX_VALUE);
				BufferedImage bufferedImage = ImageIO.read(file);
				int x = (128 - bufferedImage.getWidth()) / 2;
				int y = (128 - bufferedImage.getHeight()) / 2;
				map.addRenderer(new MapRenderer() {

					@Override
					public void render(MapView mapView, MapCanvas mapCanvas, Player p) {
						mapCanvas.drawImage(x, y, bufferedImage);
					}
				});
				map.setScale(Scale.CLOSEST);
				image_maps.add(map);
			} catch (IOException e) {
			}
		}
	}

	private static boolean isImage(File file) {
		try {
			return ImageIO.read(file) != null;
		} catch (Exception ex) {
			return false;
		}
	}

	private static void writeToLocal(String destination, InputStream input) throws IOException {
		int index;
		byte[] bytes = new byte[1024];
		FileOutputStream downloadFile = new FileOutputStream(destination);
		while ((index = input.read(bytes)) != -1) {
			downloadFile.write(bytes, 0, index);
			downloadFile.flush();
		}
		downloadFile.close();
		input.close();
	}

	public static File getNPCFile() {
		File folder = new File(CitizensAPI.getDataFolder(), "/");
		if (!folder.exists()) {
			folder.mkdirs();
		}
		File file = new File(folder.getAbsolutePath() + "/npcs.yml");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
			}
		}
		return file;
	}

	private static File getGameFile() {
		File folder = new File(Main.getInstance().getDataFolder(), "/shop");
		if (!folder.exists()) {
			folder.mkdirs();
		}
		File file = new File(folder.getAbsolutePath() + "/game.yml");
		if (!file.exists()) {
			// 兼容旧版：把位于根目录的 game.yml 迁移到 shop/game.yml
			File oldFile = new File(Main.getInstance().getDataFolder(), "/game.yml");
			if (oldFile.exists()) {
				oldFile.renameTo(file);
			} else {
				try {
					file.createNewFile();
				} catch (IOException e) {
				}
			}
		}
		return file;
	}

	public static String getLanguage(String path) {
		return ColorUtil.color(language_config.getString(path, "null"));
	}

	public static List<String> getLanguageList(String path) {
		if (language_config.contains(path) && language_config.isList(path)) {
			return ColorUtil.colorList(language_config.getStringList(path));
		}
		return Arrays.asList("null");
	}
}
