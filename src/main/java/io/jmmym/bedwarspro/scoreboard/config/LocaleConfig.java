package io.jmmym.bedwarspro.scoreboard.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;

import lombok.Getter;
import io.jmmym.bedwarspro.scoreboard.Main;

public class LocaleConfig {

	@Getter
	private EnumLocale pluginLocale;
	private Map<String, Object> language;

	public LocaleConfig() {
		language = new HashMap<String, Object>();
	}

	private void loadLanguage() {
		language.put("version", "版本");
		language.put("author", "作者");
		language.put("website", "网站");
		language.put("loading", "§f开始加载插件...");
		language.put("loading_failed", "§c插件加载失败！");
		language.put("BedwarsPRO_incompatible", "§c错误: §f暂不兼容该版本 §aBedwarsPRO§f！");
		language.put("no_BedwarsPRO", "§c错误: §f缺少必要前置 §aBedwarsPRO");
		language.put("no_citizens", "§f你必须安装 §cCitizens §f才能启用商店！");
		language.put("no_protocollib", "§c错误: §f缺少必要前置 §aProtocolLib");
		language.put("bedwarsxp", "§c错误: §f暂不支持该版本§aBedwarsXP");
		language.put("config_failed", "§c错误: §f配置文件加载失败！");
		language.put("register_listener", "§f正在注册监听器...");
		language.put("listener_success", "§a监听器注册成功！");
		language.put("listener_failed", "§c错误: §f监听器注册失败！");
		language.put("register_command", "§f正在注册指令...");
		language.put("command_success", "§a指令注册成功！");
		language.put("command_failed", "§c错误: §f指令注册失败！");
		language.put("load_success", "§a插件加载成功！");
		language.put("loading_config", "§f正在加载配置文件...");
		language.put("saved_config", "§a默认配置文件已保存！");
		language.put("config_success", "§a配置文件加载成功！");
		language.put("update_checking", "§b§lBWSBA §f>> §a正在检测更新...");
		language.put("no_update", "§b§lBWSBA §f>> §a您使用的已是最新版本！");
		language.put("update_check_failed", "§b§lBWSBA §f>> §c检测更新失败，请检查服务器网络连接！");
		language.put("update_info", "检测到版本更新！");
		language.put("running_version", "当前版本");
		language.put("update_version", "更新版本");
		language.put("updates", "更新内容");
		language.put("update_download", "更新地址");
	}

	public void loadLocaleConfig() {
		File folder = new File(Main.getInstance().getDataFolder(), "/");
		if (!folder.exists()) {
			folder.mkdirs();
		}
		File file = new File(folder.getAbsolutePath() + "/config.yml");
		if (file.exists()) {
			pluginLocale = getLocaleByName(YamlConfiguration.loadConfiguration(file).getString("locale", "zh_CN"));
		} else {
			pluginLocale = EnumLocale.ZH_CN;
		}
		loadLanguage();
		saveLocale();
	}

	public Object getLanguage(String str) {
		return language.getOrDefault(str, "null");
	}

	public String getSystemLocaleName() {
		return "zh_CN";
	}

	public EnumLocale getSystemLocale() {
		return EnumLocale.ZH_CN;
	}

	private EnumLocale getLocaleByName(String name) {
		return EnumLocale.getByName(name);
	}

	public void saveResource(String resourcePath) {
		try {
			InputStream input = Main.getInstance().getResource("bwsba-locale/zh_CN/" + resourcePath);
			if (input != null) {
				writeToLocal(Main.getInstance().getDataFolder().getPath() + "/" + resourcePath, input);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void saveLocale() {
		File folder = new File(Main.getInstance().getDataFolder(), "/locale");
		if (!folder.exists()) {
			folder.mkdirs();
		}
		File locale_folder = new File(folder.getPath(), "/zh_CN");
		if (!locale_folder.exists()) {
			locale_folder.mkdirs();
		}
		for (String file : new String[] { "config.yml", "language.yml", "team_shop.yml" }) {
			try {
				InputStream input = Main.getInstance().getResource("bwsba-locale/zh_CN/" + file);
				if (input != null) {
					writeToLocal(folder.getPath() + "/zh_CN/" + file, input);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
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
}
