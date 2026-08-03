package io.jmmym.bedwarspro.commands;

import io.jmmym.bedwarspro.BedwarsPRO;
import java.util.ArrayList;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class AuthorCommand extends BaseCommand {

    public AuthorCommand(BedwarsPRO plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, ArrayList<String> args) {
        if (!sender.hasPermission("bw." + this.getPermission())) {
            return false;
        }

        sender.sendMessage(ChatColor.WHITE + "---------------------------------");
        sender.sendMessage(ChatColor.AQUA + "          BedwarsPRO");
        sender.sendMessage(ChatColor.GREEN + "版本: " + BedwarsPRO.getInstance().getDescription().getVersion());
        sender.sendMessage(ChatColor.GREEN + "作者: By JmmYm");
        sender.sendMessage(ChatColor.WHITE + "---------------------------------");
        return true;
    }

    @Override
    public String[] getArguments() {
        return new String[]{};
    }

    @Override
    public String getCommand() {
        return "author";
    }

    @Override
    public String getDescription() {
        return "显示插件作者信息";
    }

    @Override
    public String getName() {
        return "作者信息";
    }

    @Override
    public String getPermission() {
        return "base";
    }

}