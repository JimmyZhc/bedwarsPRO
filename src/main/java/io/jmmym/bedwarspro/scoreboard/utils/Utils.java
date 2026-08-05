package io.jmmym.bedwarspro.scoreboard.utils;

import io.jmmym.bedwarspro.BedwarsPRO;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class Utils {

	private static Map<String, Class<?>> classes = new HashMap<String, Class<?>>();
	private static String version = null;

	public static String getVersion() {
		if (version == null) {
			version = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
		}
		return version;
	}

	public static Class<?> getNMSClass(String name) {
		if (!classes.containsKey(name)) {
			try {
				classes.put(name, Class.forName("net.minecraft.server." + getVersion() + "." + name));
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
		return classes.get(name);
	}

	public static Class<?> getClass(String name) {
		if (!classes.containsKey(name)) {
			try {
				classes.put(name, Class.forName("org.bukkit.craftbukkit." + getVersion() + "." + name));
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
		return classes.get(name);
	}

	public static void sendPacket(Player player, Object packet) {
		try {
			Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
			Object connection = craftPlayer.getClass().getField("playerConnection").get(craftPlayer);
			Method sendPacket = connection.getClass().getMethod("sendPacket", getNMSClass("Packet"));
			sendPacket.invoke(connection, packet);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 发送标题 (6参数版 - 接收者玩家, 淡入, 停留, 淡出, 主标题, 副标题)
	 */
	public static void sendTitle(Player player, Integer fadeIn, Integer stay, Integer fadeOut, String title, String subtitle) {
		try {
			Class<?> packetTitleClass = getNMSClass("PacketPlayOutTitle");
			Class<?> enumClass = packetTitleClass.getDeclaredClasses()[0];
			Class<?> chatClass = getNMSClass("IChatBaseComponent");
			Method chatMethod = chatClass.getDeclaredClasses()[0].getMethod("a", String.class);

			// 1. 发送 TIMES 包
			Object timesType = enumClass.getField("TIMES").get(null);
			Object emptyChat = chatMethod.invoke(null, "{\"text\":\"\"}");
			Constructor<?> timesCtor = packetTitleClass.getConstructor(enumClass, chatClass, Integer.TYPE, Integer.TYPE, Integer.TYPE);
			Object timesPacket = timesCtor.newInstance(timesType, emptyChat, fadeIn, stay, fadeOut);
			sendPacket(player, timesPacket);

			// 2. 发送 TITLE 包
			if (title != null) {
				Object titleType = enumClass.getField("TITLE").get(null);
				Object titleChat = chatMethod.invoke(null, "{\"text\":\"" + title + "\"}");
				Constructor<?> titleCtor = packetTitleClass.getConstructor(enumClass, chatClass);
				Object titlePacket = titleCtor.newInstance(titleType, titleChat);
				sendPacket(player, titlePacket);
			}

			// 3. 发送 SUBTITLE 包
			if (subtitle != null) {
				Object subtitleType = enumClass.getField("SUBTITLE").get(null);
				Object subtitleChat = chatMethod.invoke(null, "{\"text\":\"" + subtitle + "\"}");
				Constructor<?> subtitleCtor = packetTitleClass.getConstructor(enumClass, chatClass);
				Object subtitlePacket = subtitleCtor.newInstance(subtitleType, subtitleChat);
				sendPacket(player, subtitlePacket);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 发送标题 (7参数版 - 发送者, 接收者, 淡入, 停留, 淡出, 主标题, 副标题)
	 */
	public static void sendTitle(Player sender, Player receiver, Integer fadeIn, Integer stay, Integer fadeOut, String title, String subtitle) {
		sendTitle(receiver, fadeIn, stay, fadeOut, title, subtitle);
	}

	/**
	 * 发送消息给玩家
	 */
	public static void sendMessage(Player sender, Player receiver, String message) {
		receiver.sendMessage(message);
	}

	/**
	 * 发送 ActionBar 消息
	 */
	public static void sendPlayerActionbar(Player player, String message) {
		try {
			Class<?> packetClass = getNMSClass("PacketPlayOutChat");
			Class<?> chatClass = getNMSClass("IChatBaseComponent");
			Method chatMethod = chatClass.getDeclaredClasses()[0].getMethod("a", String.class);

			Object chatComponent = chatMethod.invoke(null, "{\"text\":\"" + message + "\"}");
			Constructor<?> ctor = packetClass.getConstructor(chatClass, byte.class);
			Object packet = ctor.newInstance(chatComponent, (byte) 2);
			sendPacket(player, packet);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void clearTitle(Player player) {
		sendTitle(player, 0, 0, 0, "", "");
	}

	public static String getFormattedTimeLeft(int time) {
		int minutes = time / 60;
		int seconds = time % 60;
		return String.format("%d:%02d", minutes, seconds);
	}
}
