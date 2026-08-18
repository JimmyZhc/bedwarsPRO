package io.jmmym.bedwarspro.rank;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.events.BedwarsGameOverEvent;
import io.jmmym.bedwarspro.events.BedwarsPlayerKilledEvent;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.Team;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 排位赛监听器。
 *
 * <p>职责：记录每场排位对局的队伍淘汰顺序（用于确定第 2/3/4 名）与玩家击杀数（MVP 评选），
 * 在 {@link BedwarsGameOverEvent} 时对全部参赛玩家结算 ELO / 段位 / 战绩，并处理玩家
 * 退出匹配队列。</p>
 */
public class RankListener implements Listener {

  /** 每场游戏：队伍淘汰顺序（最早淘汰在前，第 1 个 = 第 4 名）。 */
  private final Map<Game, List<Team>> eliminationOrder = new HashMap<>();
  /** 每场游戏：玩家击杀数（独立于统计系统，保证 MVP 评选可用）。 */
  private final Map<Game, Map<UUID, Integer>> gameKills = new HashMap<>();

  public RankListener() {
    Bukkit.getPluginManager().registerEvents(this, BedwarsPRO.getInstance());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerKilled(BedwarsPlayerKilledEvent e) {
    if (!RankedGame(e.getGame())) {
      return;
    }
    Game game = e.getGame();

    // 记录击杀（MVP 评选）
    Player killer = e.getKiller();
    if (killer != null) {
      Map<UUID, Integer> kills = this.gameKills.get(game);
      if (kills == null) {
        kills = new HashMap<>();
        this.gameKills.put(game, kills);
      }
      kills.put(killer.getUniqueId(), kills.getOrDefault(killer.getUniqueId(), 0) + 1);
    }

    // 记录队伍淘汰顺序
    Player victim = e.getPlayer();
    Team deathTeam = game.getPlayerTeam(victim);
    if (deathTeam == null || !deathTeam.isDead(game)) {
      return;
    }
    List<Team> order = this.eliminationOrder.get(game);
    if (order == null) {
      order = new ArrayList<>();
      this.eliminationOrder.put(game, order);
    }
    if (!order.contains(deathTeam)) {
      order.add(deathTeam);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onGameOver(BedwarsGameOverEvent e) {
    if (!RankedGame(e.getGame())) {
      return;
    }
    this.settleGame(e.getGame(), e.getWinner());
    // 是否排位对局由该图配置决定（rank.yml ranked-games），结算后无需取消标记
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent e) {
    if (RankManager.getInstance() != null) {
      RankManager.getInstance().getRankedQueue().removePlayer(e.getPlayer());
    }
  }

  // ===== 地图管理界面（/bwpro mapgui） =====
  /**
   * 地图管理界面：左键点击某张图切换其排位/休闲状态并写回配置；翻页按钮切换页。
   */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onRankedGameGuiClick(InventoryClickEvent event) {
    if (!RankedGameGUI.isGameGUI(event.getInventory())) {
      return;
    }
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player)) {
      return;
    }
    Player player = (Player) event.getWhoClicked();
    if (!player.hasPermission("bwpro.task.admin") && !player.isOp()) {
      RankMessages.msg(player, "cmd.no-permission");
      return;
    }
    RankedGameGUI gui = (RankedGameGUI) event.getInventory().getHolder();
    int slot = event.getRawSlot();
    if (slot == RankedGameGUI.PAGE_PREV) {
      RankedGameGUI.open(player, gui.getPage() - 1);
      return;
    }
    if (slot == RankedGameGUI.PAGE_NEXT) {
      RankedGameGUI.open(player, gui.getPage() + 1);
      return;
    }
    Game game = gui.getGameAt(slot);
    if (game == null) {
      return;
    }
    RankManager rm = RankManager.getInstance();
    if (rm == null) {
      return;
    }
    boolean nowRanked = !rm.isRankedGame(game.getName());
    rm.setRankedGame(game.getName(), nowRanked);
    player.sendMessage(ChatColor.GREEN + "已将 " + ChatColor.YELLOW + game.getName()
        + ChatColor.GREEN + " 设为" + (nowRanked ? "排位图" : "休闲图") + "！");
    // 刷新界面，展示最新状态
    RankedGameGUI.open(player, gui.getPage());
  }

  /** 该对局是否为排位对局（由该图是否配置为排位图决定）。 */
  private boolean RankedGame(Game game) {
    return RankManager.getInstance() != null
        && game != null
        && RankManager.getInstance().isRankedGame(game.getName());
  }

  // ===== 段位进度界面（奶桶） =====

  /** 段位进度界面为纯展示，禁止取走/放入物品。 */
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onTierGuiClick(InventoryClickEvent event) {
    if (!RankTierGUI.isTierGUI(event.getInventory())) {
      return;
    }
    event.setCancelled(true);
  }

  // ===== 结算 =====

  private void settleGame(Game game, Team winner) {
    // 1. 确定每支队伍的名次
    List<Team> order = this.eliminationOrder.remove(game);
    if (order == null) {
      order = new ArrayList<>();
    }
    // 补充仍未淘汰且不在淘汰列表中的队伍（不含获胜队）
    for (Team t : game.getTeams().values()) {
      if (t.getPlayers().isEmpty() || t.equals(winner) || order.contains(t)) {
        continue;
      }
      order.add(t);
    }

    Map<Team, Integer> placements = new HashMap<>();
    if (winner != null) {
      placements.put(winner, 1);
    }
    int rank = 4;
    for (Team t : order) {
      placements.put(t, rank);
      rank--;
      if (rank < 2) {
        rank = 2; // 无获胜队（时间耗尽）时无人得第 1 名
      }
    }

    // 2. MVP 评选（击杀数最高）
    Map<UUID, Integer> kills = this.gameKills.remove(game);
    UUID mvpUuid = null;
    int mvpKills = 0;
    if (kills != null) {
      for (Map.Entry<UUID, Integer> entry : kills.entrySet()) {
        if (entry.getValue() > mvpKills) {
          mvpKills = entry.getValue();
          mvpUuid = entry.getKey();
        }
      }
    }

    // 3. 逐名结算
    for (Player p : game.getTeamPlayers()) {
      Team team = game.getPlayerTeam(p);
      Integer placement = team == null ? null : placements.get(team);
      if (placement == null) {
        continue;
      }

      RankPlayer rp = RankManager.getInstance().getPlayer(p.getUniqueId(), p.getName());
      int change = EloCalculator.calculateChange(rp, placement);
      rp.applyPlacement(placement, change);

      boolean isMvp = mvpUuid != null && mvpUuid.equals(p.getUniqueId());
      if (isMvp) {
        int bonus = EloCalculator.calculateMvpBonus(rp);
        rp.applyEloChange(bonus);
        if (p.isOnline()) {
          RankMessages.msg(p, "settle.mvp-self", "bonus", bonus);
        }
      }

      int playerKills = kills == null ? 0 : kills.getOrDefault(p.getUniqueId(), 0);
      if (playerKills > 0) {
        rp.setKills(rp.getKills() + playerKills);
      }
      RankManager.getInstance().getStorage().save(rp);

      if (p.isOnline()) {
        RankMessages.msg(p, "settle.line", "placement", placement, "change",
            formatChange(change), "tier", rp.getTier().getCnName(), "elo", rp.getElo());
      }
    }

    // 4. 全服广播结算摘要（在场玩家）
    for (Player p : game.getPlayers()) {
      if (!p.isOnline()) {
        continue;
      }
      RankMessages.msg(p, "settle.header");
      for (int i = 1; i <= 4; i++) {
        for (Team t : game.getTeams().values()) {
          Integer pl = placements.get(t);
          if (pl != null && pl == i) {
            RankMessages.msg(p, "settle.team-line", "placement", i,
                "team", t.getChatColor() + t.getDisplayName());
          }
        }
      }
      if (mvpUuid != null) {
        Player mvp = Bukkit.getPlayer(mvpUuid);
        if (mvp != null) {
          RankMessages.msg(p, "settle.mvp", "player", mvp.getName(), "kills", mvpKills);
        }
      }
      RankMessages.msg(p, "settle.footer");
    }
  }

  private String formatChange(int change) {
    return change >= 0 ? "+" + change : String.valueOf(change);
  }
}
