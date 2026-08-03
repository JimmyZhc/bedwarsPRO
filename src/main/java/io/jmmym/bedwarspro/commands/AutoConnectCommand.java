package io.jmmym.bedwarspro.commands;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameCheckCode;
import io.jmmym.bedwarspro.game.GameManager;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.utils.ChatWriter;
import io.jmmym.bedwarspro.utils.Utils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.ChatPaginator;
import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameCheckCode;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.util.ChatPaginator;
import org.bukkit.util.ChatPaginator.ChatPage;
import java.util.ArrayList;

import static org.bukkit.Bukkit.getServer;

public class AutoConnectCommand extends BaseCommand {

  private BedwarsPRO plugin = null;

  public AutoConnectCommand(BedwarsPRO plugin) {
    super(plugin);
  }

    @Override
    public boolean execute(CommandSender sender, ArrayList<String> args) {

        ArrayList<Game> showedGames = new ArrayList<Game>();
        ArrayList<Game> waitingGames = new ArrayList<Game>();
        List<Game> games = BedwarsPRO.getInstance().getGameManager().getGames();
        for (Game game : games) {
            GameCheckCode code = game.checkGame();
            if (code != GameCheckCode.OK && !sender.hasPermission("bw.setup")) {
                continue;
            }

            showedGames.add(game);
            if (game.getState() == GameState.WAITING) {
                waitingGames.add(game);
            }
        }

        if (waitingGames.size() > 0) {
            // 优先加入有人的地图，没有人则随机选择
            ArrayList<Game> hasPlayerGames = new ArrayList<Game>();
            for (Game g : waitingGames) {
                if (g.getPlayers().size() > 0) {
                    hasPlayerGames.add(g);
                }
            }
            ArrayList<Game> pool = hasPlayerGames.size() > 0 ? hasPlayerGames : waitingGames;
            Game target = pool.get(Utils.randInt(0, pool.size() - 1));
            sender.sendMessage(ChatColor.GREEN + "你已加入 " + ChatColor.YELLOW + target.getName() + ChatColor.GREEN + " 游戏中...");
            Player player = getServer().getPlayer(sender.getName());
            player.performCommand("bw join " + target.getName());
        } else if (showedGames.size() == 0) {
            sender.sendMessage(ChatColor.RED + "No Games :(");
        }

        return true;
    }

    @Override
    public String[] getArguments() {
      return new String[]{};
    }

    @Override
    public String getCommand() {
      return "autojoin";
    }

    @Override
    public String getDescription() {
      return "Auto connect to first lobby";
    }

    @Override
    public String getName() {
      return "autojoin";
    }

    @Override
    public String getPermission() {
      return "base";
    }
    @Override
    public boolean hasPermission(CommandSender sender){
    return true;
    }
  }
