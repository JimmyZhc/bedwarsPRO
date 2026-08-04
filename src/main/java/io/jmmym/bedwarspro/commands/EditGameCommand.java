package io.jmmym.bedwarspro.commands;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.utils.ChatWriter;
import java.util.ArrayList;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EditGameCommand extends BaseCommand implements ICommand {

  public EditGameCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!super.hasPermission(sender)) {
      return false;
    }

    if (!(sender instanceof Player)) {
      sender.sendMessage(ChatWriter.pluginMessage(ChatColor.RED + "该命令只能由玩家执行。"));
      return false;
    }

    Player player = (Player) sender;
    Game game = this.getPlugin().getGameManager().getGame(args.get(0));
    if (game == null) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED
          + BedwarsPRO._l(player, "errors.gamenotfound")));
      return false;
    }

    // EditGame.editGame 会在 EventListener 中通过 PlayerCommandPreprocessEvent 调用
    player.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN
        + "正在打开游戏 " + game.getName() + " 的编辑界面..."));
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{"name"};
  }

  @Override
  public String getCommand() {
    return "editgame";
  }

  @Override
  public String getDescription() {
    return "编辑已有游戏";
  }

  @Override
  public String getName() {
    return "编辑游戏";
  }

  @Override
  public String getPermission() {
    return "setup";
  }

}
