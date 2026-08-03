package io.jmmym.bedwarspro.scoreboard.addon;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.events.BedwarsGameOverEvent;
import io.jmmym.bedwarspro.game.Game;
import io.jmmym.bedwarspro.game.GameState;
import io.jmmym.bedwarspro.game.Team;
import lombok.Getter;
import io.jmmym.bedwarspro.scoreboard.Main;
import io.jmmym.bedwarspro.scoreboard.arena.Arena;
import io.jmmym.bedwarspro.scoreboard.config.Config;
import io.jmmym.bedwarspro.scoreboard.manager.PlaceholderManager;
import io.jmmym.bedwarspro.scoreboard.storage.PlayerGameStorage;
import io.jmmym.bedwarspro.scoreboard.utils.PlaceholderAPIUtil;
import io.jmmym.bedwarspro.scoreboard.utils.ScoreboardUtil;
import io.jmmym.bedwarspro.scoreboard.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;

public class ScoreBoard {

        private final Arena arena;
        private final Game game;
        @Getter
        private final Map<String, String> timer_placeholder;
        private final PlaceholderManager placeholderManager;
        private final Map<String, String> team_status;
        @Getter
        private final Map<String, String> plan_infos;
        private int title_index = 0;

        private String currentPlanId = null;
        private Field delayField = null;
        private Field spawnerField = null;
        private String currentRandomEventName = null;
        private int currentRandomEventTime = 0;

        public ScoreBoard(Arena arena) {
                this.arena = arena;
                game = arena.getGame();
                placeholderManager = new PlaceholderManager(game);
                team_status = new HashMap<>();
                timer_placeholder = new HashMap<>();
                plan_infos = new HashMap<>();

                if (Config.scoreboard_interval != 20) {
                        arena.addGameTask(new BukkitRunnable() {
                                @Override
                                public void run() {
                                        updateScoreboard();
                                }
                        }.runTaskTimer(Main.getPlugin(), 0L, Config.scoreboard_interval));
                }

                arena.addGameTask(new BukkitRunnable() {
                        @Override
                        public void run() {
                                if (currentRandomEventTime > 0) {
                                        currentRandomEventTime--;
                                        if (currentRandomEventTime <= 0) {
                                                currentRandomEventName = null;
                                        }
                                }
                        }
                }.runTaskTimer(Main.getPlugin(), 0L, 20L));

                arena.addGameTask(new BukkitRunnable() {
                        @Override
                        public void run() {
                                for (BukkitTask task : game.getRunningTasks()) {
                                        task.cancel();
                                }
                                game.getRunningTasks().clear();
                                startTimerCountdown(game);
                        }
                }.runTaskLater(Main.getPlugin(), 20L));
        }

        public PlaceholderManager getPlaceholderManager() {
                return placeholderManager;
        }

        public void setTeamStatusFormat(String team, String status) {
                team_status.put(team, status);
        }

        public void removeTeamStatusFormat(String team) {
                team_status.remove(team);
        }

        public Map<String, String> getTeamStatusFormat() {
                return team_status;
        }

        private String getGameTime(int time) {
                return String.valueOf(time / 60);
        }

        private void startTimerCountdown(Game game) {
                game.setTimeLeft(BedwarsPRO.getInstance().getMaxLength());
                game.addRunningTask(new BukkitRunnable() {
                        public void run() {
                                if (game.getTimeLeft() == 0) {
                                        game.setOver(true);
                                        game.getCycle().checkGameOver();
                                        cancel();
                                        return;
                                }
                                if (game.getState() != GameState.RUNNING || game.getPlayers().isEmpty()) {
                                        arena.onOver(new BedwarsGameOverEvent(game, null));
                                        arena.onEnd();
                                        cancel();
                                        return;
                                }
                                game.setTimeLeft(game.getTimeLeft() - 1);
                                // 修复：添加空值检查，防止 getTimeTask() 返回 null
                                if (arena.getTimeTask() != null) {
                                        arena.getTimeTask().refresh();
                                }
                                if (Config.scoreboard_interval == 20) updateScoreboard();
                        }
                }.runTaskTimer(BedwarsPRO.getInstance(), 0L, 20L));
        }

        private void applyResourceSpeed(String planId) {
                try {
                        if (spawnerField == null) {
                                try {
                                        spawnerField = Game.class.getDeclaredField("resourceSpawner");
                                } catch (NoSuchFieldException e) {
                                        try {
                                                spawnerField = Game.class.getDeclaredField("spawner");
                                        } catch (NoSuchFieldException e2) {
                                                return;
                                        }
                                }
                                spawnerField.setAccessible(true);
                        }
                        Object spawner = spawnerField.get(game);
                        if (spawner == null) {
                                return;
                        }
                        if (delayField == null) {
                                try {
                                        delayField = spawner.getClass().getDeclaredField("delay");
                                } catch (NoSuchFieldException e) {
                                        try {
                                                delayField = spawner.getClass().getDeclaredField("spawnDelay");
                                        } catch (NoSuchFieldException e2) {
                                                try {
                                                        delayField = spawner.getClass().getDeclaredField("interval");
                                                } catch (NoSuchFieldException e3) {
                                                        return;
                                                }
                                        }
                                }
                                if (delayField == null) {
                                        return;
                                }
                                delayField.setAccessible(true);
                        }
                        int newDelay = getDelayForPlan(planId);
                        delayField.setInt(spawner, newDelay);
                } catch (Exception e) {
                }
        }

        private int getDelayForPlan(String planId) {
                int defaultDelay = 40;
                switch (planId) {
                        case "1": return 20;
                        case "2": return 30;
                        case "3": return 25;
                        case "4": return 35;
                        case "5": return 40;
                        case "6": return 20;
                        case "7": return 30;
                        case "8": return 25;
                        case "9": return 35;
                        case "10": return 40;
                        default: return defaultDelay;
                }
        }

        public void updateScoreboard() {
                String eventName = "无事件";
                String eventCountdown = "0";
                String matchedPlan = null;

                for (String plan : Config.planinfo) {
                        int start = Main.getInstance().getConfig().getInt("planinfo." + plan + ".start_time", -1);
                        int end = Main.getInstance().getConfig().getInt("planinfo." + plan + ".end_time", -1);
                        if (game.getTimeLeft() < start && game.getTimeLeft() >= end) {
                                String name = Main.getInstance().getConfig().getString("planinfo." + plan + ".plans.plan_1");
                                if (name != null && !name.isEmpty()) {
                                        eventName = name;
                                        matchedPlan = plan;
                                        int remaining = Math.max(0, game.getTimeLeft() - end);
                                        eventCountdown = String.valueOf(remaining);
                                        break;
                                }
                        }
                }

                if (matchedPlan != null && !matchedPlan.equals(currentPlanId)) {
                        applyResourceSpeed(matchedPlan);
                        currentPlanId = matchedPlan;
                } else if (matchedPlan == null && currentPlanId != null) {
                        applyResourceSpeed("default");
                        currentPlanId = null;
                }

                int alive_teams = 0;
                int remain_teams = 0;
                Map<String, Team> teams = game.getTeams();

                for (Team team : teams.values()) {
                        if (!team.isDead(game)) {
                                alive_teams++;
                        }
                        if (!team.getPlayers().isEmpty()) {
                                remain_teams++;
                        }
                }

                int wither = game.getTimeLeft() - Config.witherbow_gametime;
                String bowtime = Config.witherbow_enabled && game.getTimeLeft() <= Config.witherbow_gametime
                                ? Config.witherbow_already_starte
                                : String.format("%d:%02d", Math.max(0, wither) / 60, Math.max(0, wither) % 60);

                String formattedTime = Utils.getFormattedTimeLeft(game.getTimeLeft());

                String score_title = "§e" + game.getName() + " §f- §f" + formattedTime;

                String eventNameDisplay = eventName != null && !eventName.equals("无事件") ? "§e" + eventName : "";

                List<String> scoreboard_lines = Arrays.asList(
                                "",
                                "§e事件倒计时:§a" + eventCountdown + "秒",
                                eventNameDisplay,
                                "",
                                "{team_status}",
                                "",
                                "§f杀:{kills} §f/ §f死:{dies} §f/ 床:{beds}",
                                "       §c✿§b栖云居§c✿"
                );

                int alive_players = (int) game.getPlayers().stream()
                                .filter(p -> !game.isSpectator(p))
                                .count();

                for (Player player : game.getPlayers()) {
                        List<String> lines = new ArrayList<>();

                        Team player_team = game.getPlayerTeam(player);
                        PlayerGameStorage playerGameStorage = arena.getPlayerGameStorage();
                        String playerName = player.getName();
                        String player_total_kills = playerGameStorage.getPlayerTotalKills().getOrDefault(playerName, 0) + "";
                        String player_kills = playerGameStorage.getPlayerKills().getOrDefault(playerName, 0) + "";
                        String player_final_kills = playerGameStorage.getPlayerFinalKills().getOrDefault(playerName, 0) + "";
                        String player_dies = playerGameStorage.getPlayerDies().getOrDefault(playerName, 0) + "";
                        String player_beds = playerGameStorage.getPlayerBeds().getOrDefault(playerName, 0) + "";
                        String player_team_color = "§f";
                        String player_team_players = "";
                        String player_team_name = "";
                        String player_team_bed_status = "";

                        if (player_team != null) {
                                player_team_color = player_team.getChatColor() + "";
                                player_team_players = player_team.getPlayers().size() + "";
                                player_team_name = player_team.getName();
                                player_team_bed_status = getTeamBedStatus(game, player_team);
                        }

                        for (String ls : scoreboard_lines) {
                                if (ls.contains("{team_status}")) {
                                        for (Team t : teams.values()) {
                                                String you = (player_team == t) ? Config.scoreboard_you : "";
                                                if (team_status.containsKey(t.getName())) {
                                                        lines.add(team_status.get(t.getName()).replace("{you}", you));
                                                } else {
                                                        String status = t.isDead(game)
                                                                        ? "§c✘ §7" + t.getName()
                                                                        : "§a✔ " + t.getChatColor() + t.getName();
                                                        lines.add(status.replace("{you}", you));
                                                }
                                        }
                                } else {
                                        String date = new SimpleDateFormat(Config.date_format).format(new Date());
                                        String add_line = ls;

                                        if (matchedPlan != null) {
                                                add_line = add_line.replace("{plan_timer_sec_" + matchedPlan + "}", eventCountdown);
                                        }
                                        add_line = add_line.replace("{randomevent}", currentRandomEventName != null ? currentRandomEventName : "无事件");
                                        add_line = add_line.replace("{randomevent_time}", String.valueOf(currentRandomEventTime));

                                        add_line = add_line.replace("{death_mode}", arena.getDeathMode() != null && arena.getDeathMode().getDeathmodeTime() != null ? arena.getDeathMode().getDeathmodeTime() : "")
                                                        .replace("{remain_teams}", remain_teams + "")
                                                        .replace("{alive_teams}", alive_teams + "")
                                                        .replace("{alive_players}", alive_players + "")
                                                        .replace("{teams}", teams.size() + "")
                                                        .replace("{color}", player_team_color)
                                                        .replace("{team_peoples}", player_team_players)
                                                        .replace("{player_name}", playerName)
                                                        .replace("{team}", player_team_name)
                                                        .replace("{beds}", player_beds)
                                                        .replace("{dies}", player_dies)
                                                        .replace("{totalkills}", player_total_kills)
                                                        .replace("{finalkills}", player_final_kills)
                                                        .replace("{kills}", player_kills)
                                                        .replace("{time}", getGameTime(game.getTimeLeft()))
                                                        .replace("{formattime}", formattedTime)
                                                        .replace("{game}", game.getName())
                                                        .replace("{date}", date)
                                                        .replace("{online}", game.getPlayers().size() + "")
                                                        .replace("{bowtime}", bowtime)
                                                        .replace("{team_bed_status}", player_team_bed_status)
                                                        .replace("{no_break_bed}", arena.getNoBreakBed() != null && arena.getNoBreakBed().getTime() != null ? arena.getNoBreakBed().getTime() : "");

                                        // 修复：添加空值检查，防止 getHealthLevel() 返回 null
                                        if (arena.getHealthLevel() != null && arena.getHealthLevel().getLevelTime() != null) {
                                                for (String key : arena.getHealthLevel().getLevelTime().keySet()) {
                                                        add_line = add_line.replace("{sethealthtime_" + key + "}", arena.getHealthLevel().getLevelTime().get(key));
                                                }
                                        }

                                        // 修复：添加空值检查，防止 getResourceUpgrade() 返回 null
                                        if (arena.getResourceUpgrade() != null && arena.getResourceUpgrade().getUpgTime() != null) {
                                                for (String key : arena.getResourceUpgrade().getUpgTime().keySet()) {
                                                        add_line = add_line.replace("{resource_upgrade_" + key + "}", arena.getResourceUpgrade().getUpgTime().get(key));
                                                }
                                        }

                                        for (String key : placeholderManager.getGamePlaceholder().keySet()) {
                                                add_line = add_line.replace(key, placeholderManager.getGamePlaceholder().get(key).onGamePlaceholderRequest(game));
                                        }

                                        for (Team t : teams.values()) {
                                                String team_name = t.getName();
                                                if (add_line.contains("{team_" + team_name + "_status}")) {
                                                        String stf = getTeamStatusFormat(game, t);
                                                        String you_indicator = (player_team == null) ? "" : (player_team == t) ? Config.scoreboard_you : "";
                                                        stf = stf.replace("{you}", you_indicator);
                                                        add_line = add_line.replace("{team_" + team_name + "_status}", stf);
                                                }
                                                if (add_line.contains("{team_" + team_name + "_bed_status}")) {
                                                        add_line = add_line.replace("{team_" + team_name + "_bed_status}", getTeamBedStatus(game, t));
                                                }
                                                if (add_line.contains("{team_" + team_name + "_peoples}")) {
                                                        add_line = add_line.replace("{team_" + team_name + "_peoples}", t.getPlayers().size() + "");
                                                }
                                        }

                                        if (player_team == null || !placeholderManager.getTeamPlaceholders().containsKey(player_team.getName())) {
                                                for (String teamname : placeholderManager.getTeamPlaceholders().keySet()) {
                                                        for (String placeholder : placeholderManager.getTeamPlaceholders().get(teamname).keySet()) {
                                                                add_line = add_line.replace(placeholder, "");
                                                        }
                                                }
                                        } else {
                                                for (String identifier : placeholderManager.getTeamPlaceholder(player_team.getName()).keySet()) {
                                                        add_line = add_line.replace(identifier, placeholderManager.getTeamPlaceholder(player_team.getName()).get(identifier).onTeamPlaceholderRequest(player_team));
                                                }
                                        }

                                        if (placeholderManager.getPlayerPlaceholders().containsKey(playerName)) {
                                                for (String identifier : placeholderManager.getPlayerPlaceholder(playerName).keySet()) {
                                                        add_line = add_line.replace(identifier, placeholderManager.getPlayerPlaceholder(playerName).get(identifier).onPlayerPlaceholderRequest(game, player));
                                                }
                                        } else {
                                                for (String playername : placeholderManager.getPlayerPlaceholders().keySet()) {
                                                        for (String placeholder : placeholderManager.getPlayerPlaceholders().get(playername).keySet()) {
                                                                add_line = add_line.replace(placeholder, "");
                                                        }
                                                }
                                        }

                                        for (String placeholder : timer_placeholder.keySet()) {
                                                add_line = add_line.replace(placeholder, timer_placeholder.get(placeholder));
                                        }

                                        add_line = PlaceholderAPIUtil.setPlaceholders(player, add_line);
                                        lines.add(add_line);
                                }
                        }

                        String title = PlaceholderAPIUtil.setPlaceholders(player, score_title);
                        ScoreboardUtil.setGameScoreboard(player, title, lines, game);
                }
        }

        private String getTeamBedStatus(Game game, Team team) {
                if (team.isDead(game)) {
                        return Config.scoreboard_team_bed_status_bed_destroyed;
                } else if (!team.isDead(game) && team.getPlayers().isEmpty()) {
                        return Config.scoreboard_team_bed_status_bed_alive_empty;
                } else {
                        return Config.scoreboard_team_bed_status_bed_alive;
                }
        }

        private String getTeamStatusFormat(Game game, Team team) {
                String alive = Config.scoreboard_team_status_format_bed_alive;
                String destroyed = Config.scoreboard_team_status_format_bed_destroyed;
                String alive_empty = Config.scoreboard_team_status_format_bed_alive_empty;
                String status;
                if (team.isDead(game)) {
                        status = destroyed;
                } else if (!team.isDead(game) && team.getPlayers().isEmpty()) {
                        status = alive_empty;
                } else {
                        status = alive;
                }
                if (team.isDead(game) && team.getPlayers().isEmpty()) {
                        status = Config.scoreboard_team_status_format_team_dead;
                }
                return status.replace("{bed_status}", getTeamBedStatus(game, team))
                                .replace("{color}", team.getChatColor() + "")
                                .replace("{color_initials}", team.getChatColor().name().substring(0, 1))
                                .replace("{color_name}", upperInitials(team.getChatColor().name()))
                                .replace("{players}", team.getPlayers().size() + "")
                                .replace("{team_initials}", team.getName().substring(0, 1))
                                .replace("{team}", team.getName());
        }

        private String upperInitials(String str) {
                return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
        }

        public void setCurrentRandomEvent(String name, int time) {
                this.currentRandomEventName = name;
                this.currentRandomEventTime = time;
        }

        public String getCurrentRandomEventName() {
                return currentRandomEventName;
        }

        public int getCurrentRandomEventTime() {
                return currentRandomEventTime;
        }

        private String centerText(String text) {
                int maxLength = 32;
                String stripped = text.replaceAll("§[0-9a-fk-or]", "");
                int textLength = 0;
                for (char c : stripped.toCharArray()) {
                        textLength += (c >= '\u4e00' && c <= '\u9fff') ? 2 : 1;
                }
                int spaces = (maxLength - textLength) / 2;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < spaces; i++) {
                        sb.append(" ");
                }
                return sb.toString() + text;
        }
}