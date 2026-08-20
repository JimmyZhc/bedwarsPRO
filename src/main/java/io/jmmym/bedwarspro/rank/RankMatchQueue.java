package io.jmmym.bedwarspro.rank;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameCheckCode;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.Team;
import io.jmmym.bedwarspro.game.TeamColor;
import io.jmmym.bedwarspro.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * 排位匹配队列（等待大厅模式）。
 *
 * <p>排位玩家加入后直接进入一张空闲的 WAITING 地图（等待大厅），与休闲玩家隔离
 * （地图被标记为排位对局）。玩家满员后由床战插件自带的大厅倒计时自动开局；
 * 倒计时开始后，队列会按 ELO 从高到低蛇形重排红/绿/蓝/黄 4 队（策划案 3.4.5）。
 * 中途离开（/bw leave、退出对局、下线）会自动移出房间并清理队列。</p>
 */
public class RankMatchQueue {

  /** 排位分队顺序（按蛇形公式动态分配，适配任意地图人数，不写死 16 人）。 */
  private static final TeamColor[] TEAM_ORDER = {TeamColor.RED, TeamColor.GREEN, TeamColor.BLUE, TeamColor.YELLOW};

  private final LinkedHashMap<UUID, Player> queue = new LinkedHashMap<>();
  private final Map<UUID, Long> queuedAt = new HashMap<>();
  private final Map<UUID, Boolean> warned = new HashMap<>();
  private BukkitTask ticker = null;

  /** 启动队列定时调度（每 10 秒：清理残留 / 人数不足提示 / 蛇形重排）。 */
  public void startTicker() {
    this.stopTicker();
    this.ticker = Bukkit.getScheduler().runTaskTimer(BedwarsPRO.getInstance(), new Runnable() {
      @Override
      public void run() {
        RankMatchQueue.this.tick();
      }
    }, 200L, 200L);
  }

  public void stopTicker() {
    if (this.ticker != null) {
      this.ticker.cancel();
      this.ticker = null;
    }
  }

  public boolean isQueued(UUID uuid) {
    return this.queue.containsKey(uuid);
  }

  public int size() {
    return this.queue.size();
  }

  public List<Player> getPlayers() {
    return new ArrayList<>(this.queue.values());
  }

  /** 玩家加入排位匹配：直接进入排位等待大厅。已在队列或已在游戏中的玩家无法加入。 */
  public boolean addPlayer(Player p) {
    if (p == null) {
      return false;
    }
    UUID uuid = p.getUniqueId();
    if (this.queue.containsKey(uuid)) {
      RankMessages.msg(p, "queue.already");
      return false;
    }
    if (BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(p) != null) {
      RankMessages.msg(p, "queue.in-game");
      return false;
    }

    // 优先加入已存在的排位等待大厅，否则创建新房间（只在配置的排位图中随机）
    Game game = this.findRankedLobby();
    if (game == null) {
      game = this.findIdleGame();
      if (game == null) {
        RankMessages.msg(p, "queue.no-map");
        return false;
      }
      // 满员人数 = 该地图的最大游戏人数（不写死 16 人）；开启大厅倒计时也按此人数
      int target = this.matchTarget(game);
      game.setMinPlayers(target);
    }
    int total = this.matchTarget(game);

    if (!game.playerJoins(p)) {
      RankMessages.msg(p, "queue.no-map");
      return false;
    }
    // 不立即分配队伍：玩家留在 freePlayers（等待大厅不显示队伍），
    // 满员后由蛇形分配（rebalanceSnakeIfNeeded）统一按 ELO 分入红/绿/蓝/黄 4 队

    this.queue.put(uuid, p);
    this.queuedAt.put(uuid, System.currentTimeMillis());
    this.warned.put(uuid, Boolean.FALSE);

    RankMessages.msg(p, "queue.joined",
        "x", game.getPlayers().size(), "y", total);
    return true;
  }

  /** BC 一端一图模式：玩家进服已自动进入唯一地图等待大厅，仅登记进队列（不重复进房）。 */
  public void addJoinedPlayer(Player p, Game g) {
    if (p == null || g == null) {
      return;
    }
    UUID uuid = p.getUniqueId();
    if (this.queue.containsKey(uuid)) {
      return;
    }
    int total = g.getMaxPlayers() > 0 ? g.getMaxPlayers()
        : RankManager.getInstance().getMatchPlayers();
    this.queue.put(uuid, p);
    this.queuedAt.put(uuid, System.currentTimeMillis());
    this.warned.put(uuid, Boolean.FALSE);
    RankMessages.msg(p, "queue.joined",
        "x", g.getPlayers().size(), "y", total);
  }

  /** 玩家主动离开排位队列（从等待大厅移出）。返回是否曾在队列中。 */
  public boolean removePlayer(Player p) {
    if (p != null) {
      return this.removePlayer(p.getUniqueId());
    }
    return false;
  }

  public boolean removePlayer(UUID uuid) {
    if (this.queue.remove(uuid) == null) {
      return false;
    }
    this.queuedAt.remove(uuid);
    this.warned.remove(uuid);
    Player p = Bukkit.getPlayer(uuid);
    if (p != null && p.isOnline()) {
      Game g = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(p);
      if (g != null && g.getState() != GameState.RUNNING
          && RankManager.getInstance().isRankedGame(g.getName())) {
        try {
          g.playerLeave(p, false);
        } catch (Exception ignored) {
        }
      }
    }
    return true;
  }

  // ===== 内部 =====

  private void tick() {
    // 满员人数 = 当前排位房地图的最大游戏人数（不写死 16 人），
    // 拿不到房间时回退到配置的 match.players
    int total = RankManager.getInstance().getMatchPlayers();
    Game ranked = null;
    for (Player qp : this.queue.values()) {
      Game g = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(qp);
      if (g != null) {
        ranked = g;
        break;
      }
    }
    if (ranked != null && ranked.getMaxPlayers() > 0) {
      total = ranked.getMaxPlayers();
    }
    long now = System.currentTimeMillis();
    int lowWaitTimeout = RankManager.getInstance().getLowWaitTimeout();

    // 清理已不在排位等待大厅的残留玩家（已 /bw leave、退出对局、下线等）
    this.pruneQueue();
    // 清理无人等待的空排位房标记：排位玩家全部离开后释放该图，
    // 让休闲快速加入可复用，也让下次排位可重新随机选房
    this.cleanupEmptyRankedLobbies();

    if (this.queue.isEmpty()) {
      return;
    }

    // 人数不足提示（等待超过 low-wait-timeout 且未满员）
    if (this.queue.size() < total) {
      for (Map.Entry<UUID, Long> entry : this.queuedAt.entrySet()) {
        if (now - entry.getValue() >= lowWaitTimeout * 1000L && !this.warned.get(entry.getKey())) {
          this.warned.put(entry.getKey(), Boolean.TRUE);
          Player p = this.queue.get(entry.getKey());
          if (p != null && p.isOnline()) {
            RankMessages.msg(p, "queue.low");
          }
        }
      }
    }

    // 满员且大厅倒计时中：按 ELO 蛇形重排队伍（每个房间仅一次）
    if (this.queue.size() >= total) {
      this.rebalanceSnakeIfNeeded();
    }
  }

  /** 清理队列中已不在排位等待大厅的玩家。 */
  private void pruneQueue() {
    Iterator<UUID> it = this.queue.keySet().iterator();
    while (it.hasNext()) {
      UUID uuid = it.next();
      Player p = this.queue.get(uuid);
      if (p == null || !p.isOnline()) {
        it.remove();
        this.queuedAt.remove(uuid);
        this.warned.remove(uuid);
        continue;
      }
      Game g = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(p);
      if (g == null || !RankManager.getInstance().isRankedGame(g.getName())) {
        it.remove();
        this.queuedAt.remove(uuid);
        this.warned.remove(uuid);
      }
    }
  }

  /** 满员排位房间：按 ELO 蛇形重排红/绿/蓝/黄 4 队并触发大厅倒计时。 */
  private void rebalanceSnakeIfNeeded() {
    Game game = null;
    for (Player p : this.queue.values()) {
      Game g = BedwarsPRO.getInstance().getGameManager().getGameOfPlayer(p);
      if (g != null) {
        game = g;
        break;
      }
    }
    if (game == null || game.getState() != GameState.WAITING) {
      return;
    }
    if (game.getPlayers().size() < this.matchTarget(game)) {
      return;
    }
    // 倒计时已开始（或对局已进入倒计时阶段）：不重复分配
    if (game.getLobbyCountdown() != null) {
      return;
    }

    // 蛇形分配：先把现有队伍玩家移出，再按 ELO 蛇形重新加入（玩家在 freePlayers 时直接加入）。
    // 逐个 playerJoinTeam 期间游戏会在满员后自动启动大厅倒计时（Game#playerJoinTeam 的 isStartable 检查）。
    List<Player> all = new ArrayList<>(game.getPlayers());
    Map<UUID, TeamColor> assignment = this.snakeAssign(all);
    for (Player p : all) {
      Team cur = game.getPlayerTeam(p);
      if (cur != null) {
        try {
          cur.removePlayer(p);
        } catch (Exception ignored) {
        }
      }
    }
    for (Player p : all) {
      Team team = this.getTeamByColor(game, assignment.get(p.getUniqueId()));
      if (team != null) {
        try {
          game.playerJoinTeam(p, team);
        } catch (Exception ignored) {
        }
      }
    }
  }

  /** 寻找已存在且未满员的排位等待大厅（仅复用「有人」的排位房，实现后续玩家集中匹配）。 */
  private Game findRankedLobby() {
    for (Game g : BedwarsPRO.getInstance().getGameManager().getGames()) {
      if (g.getState() != GameState.WAITING) {
        continue;
      }
      if (!RankManager.getInstance().isRankedGame(g.getName())) {
        continue;
      }
      if (g.getPlayers().isEmpty()) {
        // 空的排位房不复用：让首位玩家走随机选房（cleanup 会释放其标记）
        continue;
      }
      if (g.getPlayers().size() >= this.matchTarget(g)) {
        continue;
      }
      if (g.checkGame() != GameCheckCode.OK) {
        continue;
      }
      return g;
    }
    return null;
  }

  /** 从所有「配置为排位图」且空闲（无人）且配置合法的 WAITING 地图中随机选一张作为新的排位房。 */
  private Game findIdleGame() {
    ArrayList<Game> idle = new ArrayList<>();
    for (Game g : BedwarsPRO.getInstance().getGameManager().getGames()) {
      if (g.getState() != GameState.WAITING) {
        continue;
      }
      if (!RankManager.getInstance().isRankedGame(g.getName())) {
        continue;
      }
      if (!g.getPlayers().isEmpty()) {
        continue;
      }
      if (g.checkGame() == GameCheckCode.OK) {
        idle.add(g);
      }
    }
    if (idle.isEmpty()) {
      return null;
    }
    return idle.get(Utils.randInt(0, idle.size() - 1));
  }

  /** 空排位房的收尾：排位开局时 minPlayers 被改为 match.players，玩家全部离开后恢复为其
   * 自身配置的默认开局人数，避免休闲玩家（若管理员将该图切回休闲）进去后人数不足无法开局。
   * 排位图本身由配置决定，不随房间空置而取消。 */
  private void cleanupEmptyRankedLobbies() {
    for (Game g : BedwarsPRO.getInstance().getGameManager().getGames()) {
      if (g.getState() != GameState.WAITING) {
        continue;
      }
      if (!RankManager.getInstance().isRankedGame(g.getName())) {
        continue;
      }
      if (!g.getPlayers().isEmpty()) {
        continue;
      }
      try {
        if (g.getConfig() != null) {
          g.setMinPlayers(g.getConfig().getInt("minplayers"));
        }
      } catch (Exception ignored) {
      }
    }
  }

  /** 按 ELO 从高到低排序后蛇形分配红/绿/蓝/黄 4 队。 */
  private Map<UUID, TeamColor> snakeAssign(List<Player> players) {
    List<Player> sorted = new ArrayList<>(players);
    sorted.sort((a, b) -> {
      RankPlayer ra = RankManager.getInstance().getPlayer(a.getUniqueId(), a.getName());
      RankPlayer rb = RankManager.getInstance().getPlayer(b.getUniqueId(), b.getName());
      return Integer.compare(rb.getElo(), ra.getElo());
    });

    Map<UUID, TeamColor> map = new HashMap<>();
    int n = RankMatchQueue.TEAM_ORDER.length;
    for (int i = 0; i < sorted.size(); i++) {
      // 蛇形公式（等价于固定 16 人的 0,1,2,3,3,2,1,0 循环，但适配任意人数）：
      // 偶数轮正向 0..n-1，奇数轮反向 n-1..0
      int block = i / n;
      int pos = i % n;
      int idx = (block % 2 == 0) ? pos : (n - 1 - pos);
      map.put(sorted.get(i).getUniqueId(), RankMatchQueue.TEAM_ORDER[idx]);
    }
    return map;
  }

  /** 该排位房的满员人数：优先地图最大游戏人数（各队上限之和，不写死 16 人），
   * 拿不到时回退到配置的 match.players。 */
  private int matchTarget(Game game) {
    if (game != null && game.getMaxPlayers() > 0) {
      return game.getMaxPlayers();
    }
    return RankManager.getInstance().getMatchPlayers();
  }

  private Team getTeamByColor(Game game, TeamColor color) {
    if (color == null) {
      return null;
    }
    for (Team t : game.getTeams().values()) {
      if (t.getColor() == color) {
        return t;
      }
    }
    return null;
  }
}
