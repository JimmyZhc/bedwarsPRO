package io.jmmym.bedwarspro.commands;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.utils.ChatWriter;
import io.jmmym.bedwarspro.utils.HastebinUtility;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ItemsPasteCommand extends BaseCommand implements ICommand {

  public ItemsPasteCommand(BedwarsPRO plugin) {
    super(plugin);
  }

  @Override
  public boolean execute(final CommandSender sender, ArrayList<String> args) {
    if (!super.hasPermission(sender) && !sender.isOp()) {
      return false;
    }

    if (!(sender instanceof Player)) {
      return false;
    }

    final Player player = (Player) sender;
    BedwarsPRO.getInstance().getServer().getScheduler()
        .runTaskAsynchronously(BedwarsPRO.getInstance(),
            new Runnable() {
              @Override
              public void run() {
                try {

                  ItemStack[] playerItems = player.getInventory().getContents();

                  String uploadConfigFile;

                  ArrayList<Map<String, Object>> itemsList = new ArrayList<>();
                  for (ItemStack item : playerItems) {
                    if (item == null) {
                      continue;
                    }
                    itemsList.add(item.serialize());
                  }

                  YamlConfiguration uploadConfig = new YamlConfiguration();
                  uploadConfig.set("sampleItems", itemsList);

                  StringBuilder b = new StringBuilder();
                  b.append(
                      "# Welcome to this paste\n# This might help you to better add your custom items to BedwarsPRO's shop/shop.yml\n\n");
                  b.append(uploadConfig.saveToString());
                  b.append("\n");
                  b.append(
                      "\n# This is not a working shop - it's just a list of items you can add to your shop!");

                  String link = HastebinUtility.upload(b.toString());
                  sender.sendMessage(ChatWriter
                      .pluginMessage(ChatColor.GREEN + "Success! Items pasted on " + link));
                } catch (IOException e) {
                  e.printStackTrace();
                }
              }
            });
    return true;
  }

  @Override
  public String[] getArguments() {
    return new String[]{};
  }

  @Override
  public String getCommand() {
    return "itemspaste";
  }

  @Override
  public String getDescription() {
    return BedwarsPRO._l("commands.itemspaste.desc");
  }

  @Override
  public String getName() {
    return BedwarsPRO._l("commands.itemspaste.name");
  }

  @Override
  public String getPermission() {
    return "setup";
  }

}
