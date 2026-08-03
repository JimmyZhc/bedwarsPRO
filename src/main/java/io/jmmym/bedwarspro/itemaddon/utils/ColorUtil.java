package io.jmmym.bedwarspro.itemaddon.utils;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;

public class ColorUtil {

    public static String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static List<String> colorList(List<String> list) {
        if (list == null) return new ArrayList<>();
        List<String> colorList = new ArrayList<>();
        for (String l : list) {
            colorList.add(ChatColor.translateAlternateColorCodes('&', l));
        }
        return colorList;
    }

    public static String removeColor(String s) {
        return ChatColor.stripColor(s);
    }

    public static List<String> removeListColor(List<String> list) {
        List<String> colorList = new ArrayList<>();
        for (String l : list) {
            colorList.add(ChatColor.stripColor(l));
        }
        return colorList;
    }
}