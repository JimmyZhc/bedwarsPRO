package io.jmmym.bedwarspro.commands;

import com.google.common.collect.ImmutableMap;
import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.Team;
import io.jmmym.bedwarspro.utils.ChatWriter;
import io.jmmym.bedwarspro.utils.Utils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.material.Bed;

public class SetBedCommand extends BaseCommand implements ICommand {

  public SetBedCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(CommandSender sender, ArrayList<String> args) {
    if (!super.hasPermission(sender)) {
      return false;
    }

    Player player = (Player) sender;
    String team = args.get(1);

    Game game = this.getPlugin().getGameManager().getGame(args.get(0));
    if (game == null) {
      player.sendMessage(ChatWriter.pluginMessage(ChatColor.RED
          + BedwarsPRO
          ._l(sender, "errors.gamenotfound", ImmutableMap.of("game", args.get(0).toString()))));
      return false;
    }

    if (game.getState() == GameState.RUNNING) {
      sender.sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO
              ._l(sender, "errors.notwhilegamerunning")));
      return false;
    }

    Team gameTeam = game.getTeam(team);

    if (gameTeam == null) {
      player.sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(player, "errors.teamnotfound")));
      return false;
    }

    HashSet<Material> transparent = new HashSet<Material>();
    transparent.add(Material.AIR);

    Class<?> hashsetType = Utils.getGenericTypeOfParameter(player.getClass(), "getTargetBlock", 0);
    Method targetBlockMethod = null;
    Block targetBlock = null;

    // 1.7 compatible
    try {
      try {
        targetBlockMethod =
            player.getClass().getMethod("getTargetBlock", new Class<?>[]{Set.class, int.class});
      } catch (Exception ex) {
        BedwarsPRO.getInstance().getBugsnag().notify(ex);
        try {
          targetBlockMethod = player.getClass().getMethod("getTargetBlock",
              new Class<?>[]{HashSet.class, int.class});
        } catch (Exception exc) {
          BedwarsPRO.getInstance().getBugsnag().notify(exc);
          exc.printStackTrace();
        }
      }

      if (hashsetType.equals(Byte.class)) {
        targetBlock = (Block) targetBlockMethod.invoke(player, new Object[]{null, 15});
      } else {
        targetBlock = (Block) targetBlockMethod.invoke(player, new Object[]{transparent, 15});
      }

    } catch (Exception e) {
      BedwarsPRO.getInstance().getBugsnag().notify(e);
      e.printStackTrace();
    }

    Block standingBlock = player.getLocation().getBlock().getRelative(BlockFace.DOWN);

    if (targetBlock == null || standingBlock == null) {
      player.sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(player, "errors.bedtargeting")));
      return false;
    }

    Material targetMaterial = game.getTargetMaterial();
    boolean isBedTarget = targetMaterial == Material.BED_BLOCK || targetMaterial == Material.BED;
    boolean targetIsBed = targetBlock.getType() == Material.BED_BLOCK
        || targetBlock.getType() == Material.BED;
    boolean standingIsBed = standingBlock.getType() == Material.BED_BLOCK
        || standingBlock.getType() == Material.BED;
    if (isBedTarget) {
      if (!targetIsBed && !standingIsBed) {
        player.sendMessage(
            ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(player, "errors.bedtargeting")));
        return false;
      }
    } else if (targetBlock.getType() != targetMaterial
        && standingBlock.getType() != targetMaterial) {
      player.sendMessage(
          ChatWriter.pluginMessage(ChatColor.RED + BedwarsPRO._l(player, "errors.bedtargeting")));
      return false;
    }

    Block theBlock = null;
    if (isBedTarget) {
      if (targetBlock.getType() == Material.BED_BLOCK || targetBlock.getType() == Material.BED) {
        theBlock = targetBlock;
      } else {
        theBlock = standingBlock;
      }
    } else if (targetBlock.getType() == targetMaterial) {
      theBlock = targetBlock;
    } else {
      theBlock = standingBlock;
    }

    if (isBedTarget) {
      Block neighbor = null;
      Bed theBed = (Bed) theBlock.getState().getData();

      if (!theBed.isHeadOfBed()) {
        neighbor = theBlock;
        theBlock = Utils.getBedNeighbor(neighbor);
      } else {
        neighbor = Utils.getBedNeighbor(theBlock);
      }

      gameTeam.setTargets(theBlock, neighbor);
    } else {
      gameTeam.setTargets(theBlock, null);
    }

    player.sendMessage(ChatWriter.pluginMessage(ChatColor.GREEN + BedwarsPRO
        ._l(player, "success.bedset",
            ImmutableMap
                .of("team", gameTeam.getChatColor() + gameTeam.getName() + ChatColor.GREEN))));
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{"game", "team"};
  }

  @Override
  public String getCommand() {
    return "setbed";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.setbed.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.setbed.name");
  }

  @Override
  public String getPermission() {
    return "setup";
  }

}
