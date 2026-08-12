package io.jmmym.bedwarspro.listener;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.events.BedwarsOpenShopEvent;
import io.jmmym.bedwarspro.events.BedwarsPlayerSetNameEvent;
import io.jmmym.bedwarspro.game.BungeeGameCycle;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.Team;
import io.jmmym.bedwarspro.joinitem.JoinItem;
import io.jmmym.bedwarspro.shop.NewItemShop;
import io.jmmym.bedwarspro.utils.ChatWriter;
import io.jmmym.bedwarspro.villager.MerchantCategory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.material.Wool;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Wolf;
import org.bukkit.metadata.FixedMetadataValue;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
public class PlayerListener extends BaseListener {

  // 保存死亡玩家的绑定护甲，重生时归还
  private static Map<UUID, List<ItemStack>> savedNetherStars = new HashMap<>();
  // 保存死亡玩家的下界之星数量，重生时归还
  private static Map<UUID, Integer> savedNetherStarCount = new HashMap<>();

  private String getChatFormat(String format, Team team, boolean isSpectator, boolean all) {
    String form = format;

    if (all) {
      form = form.replace("$all$", BedwarsPRO._l("ingame.all") + ChatColor.RESET);
    }

    form = form.replace("$player$",
        ((!isSpectator && team != null) ? team.getChatColor() : "") + "%1$s" + ChatColor.RESET);
    form = form.replace("$msg$", "%2$s");

    if (isSpectator) {
      form = form.replace("$team$", BedwarsPRO._l("ingame.spectator"));
    } else if (team != null) {
      form = form.replace("$team$", team.getDisplayName() + ChatColor.RESET);
    }

    return ChatColor.translateAlternateColorCodes('&', form);
  }

  @SuppressWarnings("deprecation")
  private void inGameInteractEntity(PlayerInteractEntityEvent iee, Game game, Player player) {

    if (BedwarsPRO.getInstance().getCurrentVersion().startsWith("v1_8")) {
      if (iee.getPlayer().getItemInHand().getType().equals(Material.MONSTER_EGG)
          || iee.getPlayer().getItemInHand().getType().equals(Material.MONSTER_EGGS)
          || iee.getPlayer().getItemInHand().getType().equals(Material.DRAGON_EGG)) {
        iee.setCancelled(true);
        return;
      }
    } else {
      if (iee.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.MONSTER_EGG)
          || iee.getPlayer().getInventory().getItemInMainHand().getType()
          .equals(Material.MONSTER_EGGS)
          || iee.getPlayer().getInventory().getItemInMainHand().getType()
          .equals(Material.DRAGON_EGG)
          || iee.getPlayer().getInventory().getItemInOffHand().getType()
          .equals(Material.MONSTER_EGG)
          || iee.getPlayer().getInventory().getItemInOffHand().getType()
          .equals(Material.MONSTER_EGGS)
          || iee.getPlayer().getInventory().getItemInOffHand().getType()
          .equals(Material.DRAGON_EGG)) {
        iee.setCancelled(true);
        return;
      }
    }

    if (iee.getRightClicked() != null
        && !iee.getRightClicked().getType().equals(EntityType.VILLAGER)) {
      List<EntityType> preventClickTypes =
          Arrays.asList(EntityType.ITEM_FRAME, EntityType.ARMOR_STAND);

      if (preventClickTypes.contains(iee.getRightClicked().getType())) {
        iee.setCancelled(true);
      }

      return;
    }

    if (game.isSpectator(player)) {
      return;
    }

    if (!BedwarsPRO.getInstance().getBooleanConfig("use-build-in-shop", true)) {
      return;
    }

    iee.setCancelled(true);

    BedwarsOpenShopEvent openShopEvent =
        new BedwarsOpenShopEvent(game, player, game.getItemShopCategories(), iee.getRightClicked());
    BedwarsPRO.getInstance().getServer().getPluginManager().callEvent(openShopEvent);

    if (openShopEvent.isCancelled()) {
      return;
    }

    if (game.getPlayerSettings(player).useOldShop()) {
      MerchantCategory.openCategorySelection(player, game);
    } else {
      NewItemShop itemShop = game.getNewItemShop(player);
      if (itemShop == null) {
        itemShop = game.openNewItemShop(player);
      }

      itemShop.setCurrentCategory(null);
      itemShop.openCategoryInventory(player);
    }
  }

  /*
   * GAME
   */

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onChat(AsyncPlayerChatEvent ce) {
    if (ce.isCancelled()) {
      return;
    }

    Player player = ce.getPlayer();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (game == null) {
      boolean seperateGameChat = BedwarsPRO
          .getInstance().getBooleanConfig("seperate-game-chat", true);
      if (!seperateGameChat) {
        return;
      }

      Iterator<Player> recipients = ce.getRecipients().iterator();
      while (recipients.hasNext()) {
        Player recipient = recipients.next();
        Game recipientGame = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(recipient);
        if (recipientGame != null) {
          recipients.remove();
        }
      }
      return;
    }

    if (game.getState() == GameState.STOPPED) {
      return;
    }

    Team team = game.getPlayerTeam(player);
    String message = ce.getMessage();
    boolean isSpectator = game.isSpectator(player);

    String displayName = player.getDisplayName();
    String playerListName = player.getPlayerListName();

    if (BedwarsPRO.getInstance().getBooleanConfig("overwrite-names", false)) {
      if (team == null) {
        displayName = ChatColor.stripColor(player.getName());

        playerListName = ChatColor.stripColor(player.getName());
      } else {
        displayName = team.getChatColor() + ChatColor.stripColor(player.getName());
        playerListName = team.getChatColor() + ChatColor.stripColor(player.getName());
      }

    }

    if (BedwarsPRO.getInstance().getBooleanConfig("teamname-on-tab", false)) {
      if (team == null || isSpectator) {
        playerListName = ChatColor.stripColor(player.getDisplayName());
      } else {
        playerListName = team.getChatColor() + team.getName() + ChatColor.WHITE + " | "
            + team.getChatColor() + ChatColor.stripColor(player.getDisplayName());
      }
    }

    BedwarsPlayerSetNameEvent playerSetNameEvent =
        new BedwarsPlayerSetNameEvent(team, displayName, playerListName, player);
    BedwarsPRO.getInstance().getServer().getPluginManager().callEvent(playerSetNameEvent);

    if (!playerSetNameEvent.isCancelled()) {
      player.setDisplayName(playerSetNameEvent.getDisplayName());
      player.setPlayerListName(playerSetNameEvent.getPlayerListName());
    }

    if (game.getState() != GameState.RUNNING && game.getState() == GameState.WAITING) {
      String format = null;
      if (team == null) {
        format = this.getChatFormat(
            BedwarsPRO.getInstance().getStringConfig("lobby-chatformat", "$player$: $msg$"), null,
            false,
            true);
      } else {
        format = this.getChatFormat(
            BedwarsPRO.getInstance()
                .getStringConfig("ingame-chatformat", "<$team$>$player$: $msg$"),
            team, false, true);
      }

      ce.setFormat(format);

      if (!BedwarsPRO.getInstance().getBooleanConfig("seperate-game-chat", true)) {
        return;
      }

      Iterator<Player> recipiens = ce.getRecipients().iterator();
      while (recipiens.hasNext()) {
        Player recipient = recipiens.next();
        if (!game.isInGame(recipient)) {
          recipiens.remove();
        }
      }

      return;
    }

    @SuppressWarnings("unchecked")
    List<String> toAllPrefixList = (List<String>) BedwarsPRO.getInstance().getConfig()
        .getList("chat-to-all-prefix", Arrays.asList("@"));

    String toAllPrefix = null;

    for (String oneToAllPrefix : toAllPrefixList) {
      if (message.trim().startsWith(oneToAllPrefix)) {
        toAllPrefix = oneToAllPrefix;
      }
    }

    if (toAllPrefix != null || isSpectator || (game.getCycle().isEndGameRunning()
        && BedwarsPRO.getInstance().getBooleanConfig("global-chat-after-end", true))) {
      boolean seperateSpectatorChat =
          BedwarsPRO.getInstance().getBooleanConfig("seperate-spectator-chat", false);

      message = message.trim();
      String format = null;
      if (!isSpectator && !(game.getCycle().isEndGameRunning()
          && BedwarsPRO.getInstance().getBooleanConfig("global-chat-after-end", true))) {
        ce.setMessage(message.substring(toAllPrefix.length(), message.length()).trim());
        format = this
            .getChatFormat(BedwarsPRO.getInstance().getStringConfig("ingame-chatformat-all",
                "[$all$] <$team$>$player$: $msg$"), team, false, true);
      } else {
        ce.setMessage(message);
        format = this.getChatFormat(
            BedwarsPRO.getInstance()
                .getStringConfig("ingame-chatformat", "<$team$>$player$: $msg$"),
            team, isSpectator, true);
      }

      ce.setFormat(format);

      if (!BedwarsPRO.getInstance().isBungee() || seperateSpectatorChat) {
        Iterator<Player> recipiens = ce.getRecipients().iterator();
        while (recipiens.hasNext()) {
          Player recipient = recipiens.next();
          if (!game.isInGame(recipient)) {
            recipiens.remove();
            continue;
          }

          if (!seperateSpectatorChat || (game.getCycle().isEndGameRunning()
              && BedwarsPRO.getInstance().getBooleanConfig("global-chat-after-end", true))) {
            continue;
          }

          if (isSpectator && !game.isSpectator(recipient)) {
            recipiens.remove();
          } else if (!isSpectator && game.isSpectator(recipient)) {
            recipiens.remove();
          }
        }
      }
    } else {
      message = message.trim();
      ce.setMessage(message);
      ce.setFormat(this.getChatFormat(
          BedwarsPRO.getInstance().getStringConfig("ingame-chatformat", "<$team$>$player$: $msg$"),
          team,
          false, false));

      Iterator<Player> recipiens = ce.getRecipients().iterator();
      while (recipiens.hasNext()) {
        Player recipient = recipiens.next();
        if (!game.isInGame(recipient) || !team.isInTeam(recipient)) {
          recipiens.remove();
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onCommand(PlayerCommandPreprocessEvent pcpe) {
    Player player = pcpe.getPlayer();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (game == null) {
      return;
    }

    if (game.getState() == GameState.STOPPED) {
      return;
    }

    String message = pcpe.getMessage();
    if (!message.startsWith("/bw")) {

      for (String allowed : BedwarsPRO.getInstance().getAllowedCommands()) {
        if (!allowed.startsWith("/")) {
          allowed = "/" + allowed;
        }

        if (message.startsWith(allowed.trim())) {
          return;
        }
      }

      if (player.hasPermission("bw.cmd")) {
        return;
      }

      pcpe.setCancelled(true);
      return;
    }
  }

  @EventHandler
  public void onCraft(CraftItemEvent cie) {
    Player player = (Player) cie.getWhoClicked();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (game == null) {
      return;
    }

    if (game.getState() == GameState.STOPPED) {
      return;
    }

    if (BedwarsPRO.getInstance().getBooleanConfig("allow-crafting", false)) {
      return;
    }

    cie.setCancelled(true);
  }

  @EventHandler
  public void onDamage(EntityDamageEvent ede) {
    if (!(ede.getEntity() instanceof Player)) {
      if (!(ede instanceof EntityDamageByEntityEvent)) {
        return;
      }

      EntityDamageByEntityEvent edbee = (EntityDamageByEntityEvent) ede;
      if (edbee.getDamager() == null || !(edbee.getDamager() instanceof Player)) {
        return;
      }

      Player player = (Player) edbee.getDamager();
      Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

      if (game == null) {
        return;
      }

      if (game.getState() == GameState.WAITING) {
        ede.setCancelled(true);
      }

      return;
    }

    Player p = (Player) ede.getEntity();
    Game g = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(p);
    if (g == null) {
      return;
    }

    if (g.getState() == GameState.STOPPED) {
      return;
    }

    if (g.getState() == GameState.RUNNING) {
      if (g.isSpectator(p)) {
        ede.setCancelled(true);
        return;
      }

      if (g.isProtected(p) && ede.getCause() != DamageCause.VOID) {
        ede.setCancelled(true);
        return;
      }

      if (BedwarsPRO.getInstance().getBooleanConfig("die-on-void", false)
          && ede.getCause() == DamageCause.VOID) {
        ede.setCancelled(true);
        p.setHealth(0);
        return;
      }

      if (ede instanceof EntityDamageByEntityEvent) {
        EntityDamageByEntityEvent edbee = (EntityDamageByEntityEvent) ede;

        if (edbee.getDamager() instanceof Player) {
          Player damager = (Player) edbee.getDamager();
          if (g.isSpectator(damager)) {
            ede.setCancelled(true);
            return;
          }

          g.setPlayerDamager(p, damager);
        } else if (edbee.getDamager().getType().equals(EntityType.ARROW)) {
          Arrow arrow = (Arrow) edbee.getDamager();
          if (arrow.getShooter() instanceof Player) {
            Player shooter = (Player) arrow.getShooter();
            if (g.isSpectator(shooter)) {
              ede.setCancelled(true);
              return;
            }

            g.setPlayerDamager(p, (Player) arrow.getShooter());
          }
        }
      }

      if (!g.getCycle().isEndGameRunning()) {
        return;
      } else if (ede.getCause() == DamageCause.VOID) {
        p.teleport(g.getPlayerTeam(p).getSpawnLocation());
      }
    } else if (g.getState() == GameState.WAITING
        && ede.getCause() == EntityDamageEvent.DamageCause.VOID) {
      p.teleport(g.getLobby());
    }

    ede.setCancelled(true);
  }

  @EventHandler
  public void onDrop(PlayerDropItemEvent die) {
    Player p = die.getPlayer();
    // 禁止丢弃加入物品（下界之星/粘液球等右键快捷物品），防止误丢
    if (JoinItem.isJoinItem(die.getItemDrop().getItemStack())) {
      die.setCancelled(true);
      return;
    }
    Game g = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(p);
    if (g == null) {
      return;
    }

    if (g.getState() != GameState.WAITING) {
      if (g.isSpectator(p)) {
        die.setCancelled(true);
      }

      return;
    }

    die.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onFly(PlayerToggleFlightEvent tfe) {
    Player p = tfe.getPlayer();
    Game g = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(p);
    if (g == null) {
      return;
    }
    if (g.getState() == GameState.STOPPED) {
      return;
    }

    if (p.getGameMode() == GameMode.CREATIVE) {
      tfe.setCancelled(false);
      p.setAllowFlight(true);
      p.setFlying(tfe.isFlying());
      return;
    }

    if (g.getState() == GameState.RUNNING && g.isSpectator(p)) {
      tfe.setCancelled(false);
      return;
    }
    tfe.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onHunger(FoodLevelChangeEvent flce) {
    if (!(flce.getEntity() instanceof Player)) {
      return;
    }

    Player player = (Player) flce.getEntity();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (game == null) {
      return;
    }

    if (game.getState() == GameState.RUNNING) {
      if (game.isSpectator(player) || game.getCycle().isEndGameRunning()) {
        flce.setCancelled(true);
        return;
      }

      flce.setCancelled(false);
      return;
    }

    flce.setCancelled(true);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void onIngameInventoryClick(InventoryClickEvent ice, Player player, Game game) {
    if (!ice.getInventory().getName().equals(BedwarsPRO._l(player, "ingame.shop.name"))) {
      if (game.isSpectator(player)
          || (game.getCycle() instanceof BungeeGameCycle && game.getCycle().isEndGameRunning()
          && BedwarsPRO.getInstance().getBooleanConfig("bungeecord.endgame-in-lobby", true))) {

        ItemStack clickedStack = ice.getCurrentItem();
        if (clickedStack == null) {
          return;
        }

        if (ice.getInventory().getName().equals(BedwarsPRO._l(player, "ingame.spectator"))) {
          ice.setCancelled(true);
          if (!clickedStack.getType().equals(Material.SKULL_ITEM)) {
            return;
          }

          SkullMeta meta = (SkullMeta) clickedStack.getItemMeta();
          Player pl = BedwarsPRO.getInstance().getServer().getPlayer(meta.getOwner());
          if (pl == null) {
            return;
          }

          if (!game.isInGame(pl)) {
            return;
          }

          player.teleport(pl);
          player.closeInventory();
          return;
        }

        Material clickedMat = ice.getCurrentItem().getType();
        if (clickedMat.equals(Material.SLIME_BALL)) {
          game.playerLeave(player, false);
        }

        if (clickedMat.equals(Material.COMPASS)) {
          game.openSpectatorCompass(player);
        }
      }
      return;
    }

    ice.setCancelled(true);
    ItemStack clickedStack = ice.getCurrentItem();

    if (clickedStack == null) {
      return;
    }

    if (game.getPlayerSettings(player).useOldShop()) {
      try {
        if (clickedStack.getType() == Material.SNOW_BALL) {
          game.getPlayerSettings(player).setUseOldShop(false);

          // open new shop
          NewItemShop itemShop = game.openNewItemShop(player);
          itemShop.setCurrentCategory(null);
          itemShop.openCategoryInventory(player);
          return;
        }

        MerchantCategory cat = game.getItemShopCategories().get(clickedStack.getType());
        if (cat == null) {
          return;
        }

        Class clazz = Class.forName("io.jmmym.bedwarspro.com."
            + BedwarsPRO.getInstance().getCurrentVersion().toLowerCase() + ".VillagerItemShop");
        Object villagerItemShop =
            clazz.getDeclaredConstructor(Game.class, Player.class, MerchantCategory.class)
                .newInstance(game, player, cat);

        Method openTrade = clazz.getDeclaredMethod("openTrading", new Class[]{});
        openTrade.invoke(villagerItemShop, new Object[]{});
      } catch (Exception ex) {
        BedwarsPRO.getInstance().getBugsnag().notify(ex);
        ex.printStackTrace();
      }
    } else {
      game.getNewItemShop(player).handleInventoryClick(ice, game, player);
    }
  }

  @EventHandler
  public void onInteractEntity(PlayerInteractEntityEvent iee) {
    Player p = iee.getPlayer();
    Game g = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(p);
    if (g == null) {
      return;
    }

    if (g.getState() == GameState.WAITING) {
      iee.setCancelled(true);
      return;
    }

    if (g.getState() == GameState.RUNNING) {
      this.inGameInteractEntity(iee, g, p);
    }
  }

  @EventHandler
  public void onInventoryClick(InventoryClickEvent ice) {
    Player player = (Player) ice.getWhoClicked();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (game == null) {
      return;
    }

    // ---- 禁止操作绑定护甲 ----
    if (game.getState() == GameState.RUNNING) {
      ItemStack clicked = ice.getCurrentItem();
      ItemStack cursor = ice.getCursor();
      if (isBoundArmor(clicked) || isBoundArmor(cursor)) {
        ice.setCancelled(true);
        return;
      }
    }
    // ---- 结束 ----

    if (game.getState() == GameState.WAITING) {
      this.onLobbyInventoryClick(ice, player, game);
    }

    if (game.getState() == GameState.RUNNING) {
      this.onIngameInventoryClick(ice, player, game);
    }
  }

  /*
   * LOBBY & GAME
   */

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onJoin(PlayerJoinEvent je) {

    final Player player = je.getPlayer();

    // 多服同步：清除快捷存储开关缓存，重新从数据库读取最新状态
    io.jmmym.bedwarspro.quickstash.PunchToDeposit.onPlayerJoin(player.getUniqueId());

    // 屏蔽默认全局消息，只发给不在游戏中的玩家；BungeeCord 模式下不提示（跨服进服提示由 BC 网络统一处理）
    je.setJoinMessage(null);
    if (!BedwarsPRO.getInstance().isBungee()) {
      String msg = ChatColor.YELLOW + player.getName() + " 进入了起床战争大厅";
      for (Player p : Bukkit.getOnlinePlayers()) {
        if (BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(p) == null) {
          p.sendMessage(msg);
        }
      }
    }

    if (BedwarsPRO.getInstance().statisticsEnabled()) {
      BedwarsPRO.getInstance().getPlayerStatisticManager().loadStatistic(player.getUniqueId());
    }

    if (BedwarsPRO.getInstance().isHologramsEnabled()
        && BedwarsPRO.getInstance().getHolographicInteractor() != null && BedwarsPRO.getInstance()
        .getHolographicInteractor().getType().equalsIgnoreCase("HolographicDisplays")) {
      BedwarsPRO.getInstance().getHolographicInteractor().updateHolograms(player, 60L);
    }

    ArrayList<Game> games = BedwarsPRO.getInstance().getGameManager().getGames();
    if (games.size() == 0) {
      return;
    }

    if (!BedwarsPRO.getInstance().isBungee()) {
      Game game = BedwarsPRO.getInstance().getGameManager().getGameByLocation(player.getLocation());

      if (game != null) {
        if (game.getMainLobby() != null) {
          player.teleport(game.getMainLobby());
        } else {
          game.playerJoins(player);
        }
        return;
      }
    }

    if (BedwarsPRO.getInstance().isBungee()) {
      je.setJoinMessage(null);
      final Game firstGame = games.get(0);

      if (firstGame.getState() == GameState.STOPPED && player.hasPermission("bw.setup")) {
        return;
      }

      firstGame.playerJoins(player);

    }
  }

  private void onLobbyInventoryClick(InventoryClickEvent ice, Player player, Game game) {
    Inventory inv = ice.getInventory();
    ItemStack clickedStack = ice.getCurrentItem();

    if (!inv.getTitle().equals(BedwarsPRO._l(player, "lobby.chooseteam"))) {
      ice.setCancelled(true);
      return;
    }

    if (clickedStack == null) {
      ice.setCancelled(true);
      return;
    }

    if (clickedStack.getType() != Material.WOOL) {
      ice.setCancelled(true);
      return;
    }

    ice.setCancelled(true);
    Wool wool = (Wool) clickedStack.getData();
    Team team = game.getTeamByDyeColor(wool.getColor());
    if (team == null) {
      return;
    }

    game.playerJoinTeam(player, team);
    player.closeInventory();
  }

  @EventHandler
  public void onPickup(PlayerPickupItemEvent ppie) {
    Player player = ppie.getPlayer();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (game == null) {
      game = BedwarsPRO.getInstance().getGameManager().getGameByLocation(player.getLocation());
      if (game == null) {
        return;
      }
    }

    if (game.getState() != GameState.WAITING && game.isInGame(player)) {
      return;
    }

    ppie.setCancelled(true);
  }
  /**
   * 检查物品是否含有绑定标记
   */
  /**
   * 检查物品是否含有绑定标记（不可见的 §r）
   */
  private boolean isBoundArmor(ItemStack item) {
    if (item == null || item.getType() == Material.AIR) return false;
    if (!item.hasItemMeta()) return false;
    ItemMeta meta = item.getItemMeta();
    if (!meta.hasLore()) return false;
    List<String> lore = meta.getLore();
    // 检查是否包含不可见标记 §r
    return lore.contains("§r");
  }

  /**
   * 获取护甲等级
   */
  private int getArmorLevel(ItemStack armor) {
    if (armor == null) return 0;
    String name = armor.getType().name();
    if (name.contains("LEATHER")) return 1;
    if (name.contains("GOLD")) return 2;
    if (name.contains("CHAINMAIL")) return 3;
    if (name.contains("IRON")) return 4;
    if (name.contains("DIAMOND")) return 5;
    return 0;
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerDie(PlayerDeathEvent pde) {
    final Player player = pde.getEntity();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (game == null) {
      return;
    }

    if (game.getState() == GameState.RUNNING) {
      // 击杀者获得灵魂（下界之星）
      Player killer = player.getKiller();
      if (killer != null && game.isInGame(killer)) {
        ItemStack soul = new ItemStack(Material.NETHER_STAR, 1);
        ItemMeta meta = soul.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_RED + "灵魂");
        soul.setItemMeta(meta);
        killer.getInventory().addItem(soul);
      }

      // 统计背包中的下界之星数量（不包括箱子/末影箱中的）
      int netherStarCount = 0;
      for (ItemStack invItem : player.getInventory().getContents()) {
        if (invItem != null && invItem.getType() == Material.NETHER_STAR) {
          netherStarCount += invItem.getAmount();
        }
      }
      if (netherStarCount > 0) {
        savedNetherStarCount.put(player.getUniqueId(), netherStarCount);
      }

      // 保留死亡玩家的绑定护甲，并从掉落物中移除下界之星和绑定护甲
      List<ItemStack> savedItems = new ArrayList<>();
      List<ItemStack> drops = pde.getDrops();
      Iterator<ItemStack> it = drops.iterator();
      while (it.hasNext()) {
        ItemStack item = it.next();
        if (item != null && item.getType() == Material.NETHER_STAR) {
          it.remove();
        } else if (isBoundArmor(item)) {
          savedItems.add(item.clone());
          it.remove();
        }
      }
      // 保存绑定护甲到静态 map，重生时归还
      if (!savedItems.isEmpty()) {
        savedNetherStars.put(player.getUniqueId(), savedItems);
      }

      pde.setDroppedExp(0);
      pde.setDeathMessage(null);

      if (!BedwarsPRO.getInstance().getBooleanConfig("player-drops", false)) {
        pde.getDrops().clear();
      }

      // 重生逻辑（保持不变）
      try {
        if (!BedwarsPRO.getInstance().isSpigot()) {
          Class<?> clazz = null;
          try {
            clazz = Class.forName("io.jmmym.bedwarspro.com."
                    + BedwarsPRO.getInstance().getCurrentVersion().toLowerCase()
                    + ".PerformRespawnRunnable");
          } catch (ClassNotFoundException ex) {
            BedwarsPRO.getInstance().getBugsnag().notify(ex);
            clazz = Class
                    .forName("io.jmmym.bedwarspro.com.fallback.PerformRespawnRunnable");
          }

          BukkitRunnable respawnRunnable =
                  (BukkitRunnable) clazz.getDeclaredConstructor(Player.class).newInstance(player);
          respawnRunnable.runTaskLater(BedwarsPRO.getInstance(), 20L);
        } else {
          new BukkitRunnable() {

            @Override
            public void run() {
              player.spigot().respawn();
            }
          }.runTaskLater(BedwarsPRO.getInstance(), 20L);
        }

      } catch (Exception e) {
        BedwarsPRO.getInstance().getBugsnag().notify(e);
        e.printStackTrace();
      }

      pde.setKeepInventory(
              BedwarsPRO.getInstance().getBooleanConfig("keep-inventory-on-death", false));

      Player killerPlayer = player.getKiller();
      if (killerPlayer == null) {
        killerPlayer = game.getPlayerDamager(player);
      }

      game.getCycle().onPlayerDies(player, killerPlayer);
    }
  }

  /*
   * LOBBY
   */

  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent pie) {
    Player player = pie.getPlayer();
    Game g = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (g == null) {
      // 非游戏内交互（如点击牌子加入）
      if (pie.getAction() != Action.RIGHT_CLICK_BLOCK
              && pie.getAction() != Action.RIGHT_CLICK_AIR) {
        return;
      }

      Block clicked = pie.getClickedBlock();

      if (clicked == null) {
        return;
      }

      if (!(clicked.getState() instanceof Sign)) {
        return;
      }

      Game game = BedwarsPRO.getInstance().getGameManager()
              .getGameBySignLocation(clicked.getLocation());
      if (game == null) {
        return;
      }

      if (game.playerJoins(player)) {
        player.sendMessage(
                ChatWriter.pluginMessage(ChatColor.GREEN + BedwarsPRO._l(player, "success.joined")));
      }
      return;
    }

    if (g.getState() == GameState.STOPPED) {
      return;
    }

    Material interactingMaterial = pie.getMaterial();
    Block clickedBlock = pie.getClickedBlock();

    // ========== 游戏运行中（RUNNING） ==========
    if (g.getState() == GameState.RUNNING) {
      if (pie.getAction() == Action.PHYSICAL && clickedBlock != null
              && (clickedBlock.getType() == Material.WHEAT
              || clickedBlock.getType() == Material.SOIL)) {
        pie.setCancelled(true);
        return;
      }

// ---- 宠物生成 ----
      if (interactingMaterial == Material.MONSTER_EGG) {
        if (pie.getAction() != Action.RIGHT_CLICK_AIR && pie.getAction() != Action.RIGHT_CLICK_BLOCK) {
          return;
        }
        pie.setCancelled(true);
        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getType() != Material.MONSTER_EGG) {
          return;
        }
        short damage = hand.getDurability();
        EntityType entityType = null;
        switch (damage) {
          case 10: entityType = EntityType.PIG; break;
          case 11: entityType = EntityType.COW; break;
          case 12: entityType = EntityType.SHEEP; break;
          case 13: entityType = EntityType.CHICKEN; break;
          case 18: entityType = EntityType.WITHER; break;
          case 19: entityType = EntityType.BAT; break;
          case 29: entityType = EntityType.HORSE; break;
          case 49: entityType = EntityType.ZOMBIE; break;
          case 50: entityType = EntityType.CREEPER; break;
          case 51: entityType = EntityType.SKELETON; break;
          case 52: entityType = EntityType.SPIDER; break;
          case 60: entityType = EntityType.SILVERFISH; break;
          case 62: entityType = EntityType.MAGMA_CUBE; break;
          case 81: entityType = EntityType.SLIME; break;
          case 95: entityType = EntityType.WOLF; break;
          case 120: entityType = EntityType.IRON_GOLEM; break;
          case 121: entityType = EntityType.VILLAGER; break;
          default: entityType = EntityType.fromId(damage); break;
        }
        if (entityType == null) {
          return;
        }
        final Game game = g;
        final Player p = player;
        final EntityType finalEntityType = entityType;

        Bukkit.getScheduler().runTaskLater(BedwarsPRO.getInstance(), () -> {
          Location loc = p.getLocation().clone();
          loc.setY(loc.getY() + 1);
          LivingEntity pet = (LivingEntity) p.getWorld().spawnEntity(loc, finalEntityType);
          if (pet == null) {
            return;
          }
          pet.setHealth(pet.getMaxHealth());
          pet.setMetadata("owner", new FixedMetadataValue(BedwarsPRO.getInstance(), p.getUniqueId().toString()));
          Team team = game.getPlayerTeam(p);
          if (team != null) {
            pet.setMetadata("team", new FixedMetadataValue(BedwarsPRO.getInstance(), team.getName()));
          }
          if (finalEntityType == EntityType.WOLF) {
            Wolf wolf = (Wolf) pet;
            Bukkit.getScheduler().runTaskLater(BedwarsPRO.getInstance(), () -> {
              wolf.setTamed(false);
              wolf.setOwner(null);
            }, 1L);
          }
          pet.setCustomName(ChatColor.GREEN + p.getName() + "'s Pet");
          pet.setCustomNameVisible(true);
        }, 0L);

        if (hand.getAmount() > 1) {
          hand.setAmount(hand.getAmount() - 1);
        } else {
          player.setItemInHand(null);
        }
        return;
      }
// ---- 宠物生成结束 ----

      if (pie.getAction() != Action.RIGHT_CLICK_BLOCK
              && pie.getAction() != Action.RIGHT_CLICK_AIR) {
        return;
      }

      // 拉杆
      if (clickedBlock != null && clickedBlock.getType() == Material.LEVER && !g.isSpectator(player)
              && pie.getAction() == Action.RIGHT_CLICK_BLOCK) {
        if (!g.getRegion().isPlacedUnbreakableBlock(clickedBlock)) {
          g.getRegion().addPlacedUnbreakableBlock(clickedBlock, clickedBlock.getState());
        }
        return;
      }

      // 观战者交互
      if (g.isSpectator(player)
              || (g.getCycle() instanceof BungeeGameCycle && g.getCycle().isEndGameRunning()
              && BedwarsPRO.getInstance().getBooleanConfig("bungeecord.endgame-in-lobby", true))) {
        if (interactingMaterial == Material.SLIME_BALL) {
          g.playerLeave(player, false);
          return;
        }

        if (interactingMaterial == Material.COMPASS) {
          g.openSpectatorCompass(player);
          pie.setCancelled(true);
          return;
        }
      }

      // 针对旧版本观战者碰撞处理
      if (clickedBlock != null) {
        try {
          GameMode.valueOf("SPECTATOR");
        } catch (Exception ex) {
          BedwarsPRO.getInstance().getBugsnag().notify(ex);
          for (Player p : g.getFreePlayers()) {
            if (!g.getRegion().isInRegion(p.getLocation())) {
              continue;
            }

            if (pie.getClickedBlock().getLocation().distance(p.getLocation()) < 2) {
              Location oldLocation = p.getLocation();
              if (oldLocation.getY() >= pie.getClickedBlock().getLocation().getY()) {
                oldLocation.setY(oldLocation.getY() + 2);
              } else {
                oldLocation.setY(oldLocation.getY() - 2);
              }

              p.teleport(oldLocation);
            }
          }
        }
      }

      // 末影箱（队伍箱子）
      if (clickedBlock != null && clickedBlock.getType() == Material.ENDER_CHEST
              && !g.isSpectator(player)) {
        pie.setCancelled(true);

        Block chest = pie.getClickedBlock();
        Team chestTeam = g.getTeamOfEnderChest(chest);
        Team playerTeam = g.getPlayerTeam(player);

        if (chestTeam == null) {
          return;
        }

        if (chestTeam.equals(playerTeam)) {
          player.openInventory(chestTeam.getInventory());
        } else {
          player.sendMessage(
                  ChatWriter
                          .pluginMessage(ChatColor.RED + BedwarsPRO._l(player, "ingame.noturteamchest")));
        }

        return;
      }

      return;
    }

    // ========== 等待大厅（WAITING） ==========
    if (g.getState() == GameState.WAITING) {
      if (interactingMaterial == null) {
        pie.setCancelled(true);
        return;
      }

      if (pie.getAction() == Action.PHYSICAL) {
        if (clickedBlock != null && (clickedBlock.getType() == Material.WHEAT
                || clickedBlock.getType() == Material.SOIL)) {
          pie.setCancelled(true);
          return;
        }
      }

      if (pie.getAction() != Action.RIGHT_CLICK_BLOCK
              && pie.getAction() != Action.RIGHT_CLICK_AIR) {
        return;
      }

      // ---- 等待大厅交互 ----
      switch (interactingMaterial) {
        case BED:
          pie.setCancelled(true);
          if (!g.isAutobalanceEnabled()) {
            g.getPlayerStorage(player).openTeamSelection(g);
          }
          break;
        case DIAMOND:
          pie.setCancelled(true);
          if (player.isOp() || player.hasPermission("bw.setup")) {
            g.start(player);
          } else if (player.hasPermission("bw.vip.forcestart")) {
            if (g.isStartable()) {
              g.start(player);
            } else {
              if (!g.hasEnoughPlayers()) {
                player.sendMessage(ChatWriter.pluginMessage(
                        ChatColor.RED + BedwarsPRO._l(player, "lobby.cancelstart.not_enough_players")));
              } else if (!g.hasEnoughTeams()) {
                player.sendMessage(ChatWriter
                        .pluginMessage(
                                ChatColor.RED + BedwarsPRO
                                        ._l(player, "lobby.cancelstart.not_enough_teams")));
              }
            }
          }
          break;
        case EMERALD:
          pie.setCancelled(true);
          if ((player.isOp() || player.hasPermission("bw.setup")
                  || player.hasPermission("bw.vip.reducecountdown"))
                  && g.getGameLobbyCountdown().getCounter() > g.getGameLobbyCountdown()
                  .getLobbytimeWhenFull()) {
            g.getGameLobbyCountdown().setCounter(g.getGameLobbyCountdown().getLobbytimeWhenFull());
          }
          break;
        case SLIME_BALL:
          pie.setCancelled(true);
          g.playerLeave(player, false);
          break;
        case LEATHER_CHESTPLATE:
          pie.setCancelled(true);
          player.updateInventory();
          break;
        default:
          break;
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerRespawn(PlayerRespawnEvent pre) {
    Player p = pre.getPlayer();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(p);

    if (game == null) {
      return;
    }

    if (game.getState() == GameState.RUNNING) {
      game.getCycle().onPlayerRespawn(pre, p);
        p.setMaxHealth(20.0);
        p.setHealth(20.0);

      // ---- 延迟恢复绑定护甲 ----
      // GiveItem.onDeath 会在死亡后 1 tick 给玩家皮革护甲，
      // 所以这里延迟 2 tick 确保在 GiveItem 之后执行
      new BukkitRunnable() {
        @Override
        public void run() {
          if (!p.isOnline()) return;

          PlayerInventory inv = p.getInventory();

          // 从保存的物品中提取绑定护甲
          List<ItemStack> savedArmors = new ArrayList<>();
          if (savedNetherStars.containsKey(p.getUniqueId())) {
            List<ItemStack> saved = savedNetherStars.get(p.getUniqueId());
            Iterator<ItemStack> savedIter = saved.iterator();
            while (savedIter.hasNext()) {
              ItemStack item = savedIter.next();
              if (isBoundArmor(item)) {
                savedArmors.add(item);
                savedIter.remove();
              }
            }
          }

          // 装备绑定护甲（替换皮革护甲）
          for (ItemStack armor : savedArmors) {
            String typeName = armor.getType().name();
            if (typeName.endsWith("_LEGGINGS")) {
              inv.setLeggings(armor);
            } else if (typeName.endsWith("_BOOTS")) {
              inv.setBoots(armor);
            } else if (typeName.endsWith("_CHESTPLATE")) {
              inv.setChestplate(armor);
            } else if (typeName.endsWith("_HELMET")) {
              inv.setHelmet(armor);
            } else {
              inv.addItem(armor);
            }
          }

          // 清空已处理的绑定护甲保存数据
          savedNetherStars.remove(p.getUniqueId());

          // 按计数归还下界之星（只归还死亡时背包中的数量）
          if (savedNetherStarCount.containsKey(p.getUniqueId())) {
            int count = savedNetherStarCount.remove(p.getUniqueId());
            ItemStack soul = new ItemStack(Material.NETHER_STAR, count);
            ItemMeta soulMeta = soul.getItemMeta();
            soulMeta.setDisplayName(ChatColor.DARK_RED + "灵魂");
            soul.setItemMeta(soulMeta);
            inv.addItem(soul);
          }

          // 应用队伍武器附魔
          Team team = game.getPlayerTeam(p);
          if (team != null) {
            int level = team.getWeaponEnchantLevel();
            if (level > 0) {
              for (ItemStack item : inv.getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                  String typeName = item.getType().name();
                  if (typeName.contains("SWORD") || typeName.contains("AXE")) {
                    if (item.containsEnchantment(Enchantment.DAMAGE_ALL)) {
                      item.removeEnchantment(Enchantment.DAMAGE_ALL);
                    }
                    item.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, level);
                  }
                }
              }
            }
          }

          p.updateInventory();
        }
      }.runTaskLater(BedwarsPRO.getInstance(), 2L);

      return;
    }

    if (game.getState() == GameState.WAITING) {
      pre.setRespawnLocation(game.getLobby());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onQuit(PlayerQuitEvent pqe) {
    Player player = pqe.getPlayer();

    // 屏蔽默认全局消息，只发给不在游戏中的玩家
    Game quitGame = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);
    pqe.setQuitMessage(null);
    String msg = ChatColor.YELLOW + player.getName() + " 离开起床战争大厅";
    for (Player p : Bukkit.getOnlinePlayers()) {
      if (!p.equals(player) && BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(p) == null) {
        p.sendMessage(msg);
      }
    }

    // Remove holographs
    if (BedwarsPRO.getInstance().isHologramsEnabled()
        && BedwarsPRO.getInstance().getHolographicInteractor() != null && BedwarsPRO.getInstance()
        .getHolographicInteractor().getType().equalsIgnoreCase("HolographicDisplays")) {
      BedwarsPRO.getInstance().getHolographicInteractor().unloadAllHolograms(player);
    }

    if (BedwarsPRO.getInstance().statisticsEnabled()) {
      BedwarsPRO.getInstance().getPlayerStatisticManager().unloadStatistic(player);
    }

    if (quitGame == null) {
      return;
    }

    quitGame.playerLeave(player, false);
  }

  @EventHandler
  public void onSleep(PlayerBedEnterEvent bee) {

    Player p = bee.getPlayer();

    Game g = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(p);
    if (g == null) {
      return;
    }

    if (g.getState() == GameState.STOPPED) {
      return;
    }

    bee.setCancelled(true);
  }

  @EventHandler
  public void onSwitchWorld(PlayerChangedWorldEvent change) {
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(change.getPlayer());
    if (game != null) {
      if (game.getState() == GameState.RUNNING) {
        if (!game.getCycle().isEndGameRunning()) {
          if (!game.getPlayerSettings(change.getPlayer()).isTeleporting()) {
            game.playerLeave(change.getPlayer(), false);
          } else {
            game.getPlayerSettings(change.getPlayer()).setTeleporting(false);
          }
        }
      } else if (game.getState() == GameState.WAITING) {
        if (!game.getPlayerSettings(change.getPlayer()).isTeleporting()) {
          game.playerLeave(change.getPlayer(), false);
        } else {
          game.getPlayerSettings(change.getPlayer()).setTeleporting(false);
        }
      }
    }

    if (!BedwarsPRO.getInstance().isHologramsEnabled()
        || BedwarsPRO.getInstance().getHolographicInteractor() == null) {
      return;
    }

    BedwarsPRO.getInstance().getHolographicInteractor().updateHolograms(change.getPlayer());
  }

  @EventHandler
  public void openInventory(InventoryOpenEvent ioe) {
    if (!(ioe.getPlayer() instanceof Player)) {
      return;
    }

    Player player = (Player) ioe.getPlayer();
    Game game = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(player);

    if (game == null) {
      return;
    }

    if (game.getState() != GameState.RUNNING) {
      return;
    }

    if (ioe.getInventory().getType() == InventoryType.ENCHANTING
        || ioe.getInventory().getType() == InventoryType.BREWING
        || (ioe.getInventory().getType() == InventoryType.CRAFTING
        && !BedwarsPRO.getInstance().getBooleanConfig("allow-crafting", false))) {
      ioe.setCancelled(true);
      return;
    } else if (ioe.getInventory().getType() == InventoryType.CRAFTING
        && BedwarsPRO.getInstance().getBooleanConfig("allow-crafting", false)) {
      return;
    }

    if (game.isSpectator(player)) {
      if (ioe.getInventory().getName().equals(BedwarsPRO._l(player, "ingame.spectator"))) {
        return;
      }

      ioe.setCancelled(true);
    }

    if (ioe.getInventory().getHolder() == null) {
      return;
    }

    if (game.getRegion().getInventories().contains(ioe.getInventory())) {
      return;
    }

    game.getRegion().addInventory(ioe.getInventory());
  }

}
