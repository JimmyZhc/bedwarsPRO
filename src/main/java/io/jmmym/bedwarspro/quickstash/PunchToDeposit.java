package io.jmmym.bedwarspro.quickstash;

import io.jmmym.bedwarspro.BedwarsPRO;
import io.jmmym.bedwarspro.database.DatabaseManager;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 快捷存入（QuickStash）模块主类。
 *
 * <p>功能：
 * <ul>
 *   <li>手持物品左键点击箱子/末影箱 → 将手中整组物品存入箱子</li>
 *   <li>潜行 + 左键点击 → 将背包内所有同种物品一次性全部存入</li>
 *   <li>右键点击保持原版行为（打开箱子）</li>
 *   <li>玩家个人开关：/bwpro quickstash gui/on/off/status，默认全部开启</li>
 *   <li>开关状态持久化：优先写入数据库（quickstash-database 启用时），
 *       否则保存在本地 QuickStash/players.yml，并提示"数据库未配置，仅保存在本地"</li>
 * </ul></p>
 */
public class PunchToDeposit {

    /** GUI 标题（用于点击事件识别）。 */
    public static final String GUI_TITLE = "快捷存入设置";

    private static PunchToDeposit instance;

    private final BedwarsPRO plugin;
    private Config config;
    private DepositListener listener;

    /** 玩家开关：uuid -> enabled，默认 true。 */
    private final Map<UUID, Boolean> enabledMap = new HashMap<>();
    /** 本地玩家开关文件。 */
    private File playersFile;
    private YamlConfiguration playersCfg;
    /** 数据库是否可用。 */
    private boolean dbAvailable = false;

    private PunchToDeposit(BedwarsPRO plugin) {
        this.plugin = plugin;
    }

    public static PunchToDeposit getInstance() {
        return instance;
    }

    public static Config getConfig() {
        return instance == null ? null : instance.config;
    }

    /** 插件启用时调用。 */
    public static void init(BedwarsPRO plugin) {
        if (instance == null) {
            instance = new PunchToDeposit(plugin);
        }
        instance.enable();
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    /** 插件禁用时调用，保存玩家开关状态。 */
    public static void shutdown() {
        if (instance != null) {
            instance.disable();
            instance = null;
        }
    }

    /** 重载快捷存储配置文件（供 /bwpro reload 与 /bwpro quickstash reload 调用）。 */
    public static void reload() {
        if (instance != null) {
            instance.config.load();
        }
    }

    // ==================== 生命周期 ====================

    private void enable() {
        config = new Config(plugin);
        config.load();
        listener = new DepositListener(this);
        initDatabase();
        loadLocalPlayers();
        if (dbAvailable) {
            plugin.getLogger().info("[QuickStash] 快捷存储数据库连接成功，玩家快捷存入开关将同步存储。");
        } else {
            plugin.getLogger().info("[QuickStash] 数据库未配置或连接失败，玩家开关状态仅保存在本地！");
        }
    }

    private void disable() {
        saveLocalPlayers();
    }

    // ==================== 数据库 ====================

    private void initDatabase() {
        dbAvailable = false;
        if (!plugin.getConfig().getBoolean("quickstash-database.enabled", false)) {
            return;
        }
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) {
            return;
        }
        String table = db.getTablePrefix() + "quickstash_players";
        try (Connection conn = db.getConnection()) {
            if (conn == null) {
                return;
            }
            try (PreparedStatement st = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS `" + table + "` "
                            + "(`uuid` varchar(36) NOT NULL, "
                            + "`enabled` tinyint(1) NOT NULL DEFAULT '1', "
                            + "`updatedAt` bigint NOT NULL DEFAULT '0', "
                            + "PRIMARY KEY (`uuid`)) "
                            + "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")) {
                st.executeUpdate();
            }
            dbAvailable = true;
        } catch (Exception e) {
            plugin.getLogger().warning("[QuickStash] 数据库表初始化失败: " + e.getMessage());
            dbAvailable = false;
        }
    }

    private Boolean loadFromDb(UUID uuid) {
        if (!dbAvailable) {
            return null;
        }
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) {
            return null;
        }
        String table = db.getTablePrefix() + "quickstash_players";
        try (Connection conn = db.getConnection()) {
            if (conn == null) {
                return null;
            }
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT `enabled` FROM `" + table + "` WHERE `uuid` = ?")) {
                st.setString(1, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean("enabled");
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[QuickStash] 从数据库读取开关失败 " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    private void saveToDb(UUID uuid, boolean enabled) {
        if (!dbAvailable) {
            return;
        }
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) {
            return;
        }
        String table = db.getTablePrefix() + "quickstash_players";
        try (Connection conn = db.getConnection()) {
            if (conn == null) {
                return;
            }
            try (PreparedStatement st = conn.prepareStatement(
                    "INSERT INTO `" + table + "` (`uuid`, `enabled`, `updatedAt`) "
                            + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE "
                            + "`enabled` = VALUES(`enabled`), `updatedAt` = VALUES(`updatedAt`)")) {
                st.setString(1, uuid.toString());
                st.setBoolean(2, enabled);
                st.setLong(3, System.currentTimeMillis());
                st.executeUpdate();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[QuickStash] 写入数据库失败 " + uuid + ": " + e.getMessage());
        }
    }

    // ==================== 玩家开关状态 ====================

    /** 获取玩家快捷存入开关（默认开启）。 */
    public boolean isEnabledFor(Player player) {
        return isEnabledFor(player.getUniqueId());
    }

    public boolean isEnabledFor(UUID uuid) {
        Boolean cached = enabledMap.get(uuid);
        if (cached != null) {
            return cached;
        }
        // 数据库优先（多服同步）
        if (dbAvailable) {
            Boolean dbVal = loadFromDb(uuid);
            if (dbVal != null) {
                enabledMap.put(uuid, dbVal);
                return dbVal;
            }
        }
        // 本地文件
        if (playersCfg != null && playersCfg.contains(uuid.toString())) {
            boolean v = playersCfg.getBoolean(uuid.toString(), true);
            enabledMap.put(uuid, v);
            return v;
        }
        enabledMap.put(uuid, true);
        return true;
    }

    /** 设置玩家快捷存入开关并持久化。 */
    public void setEnabledFor(Player player, boolean enabled) {
        enabledMap.put(player.getUniqueId(), enabled);
        saveToDb(player.getUniqueId(), enabled);
        saveLocalPlayer(player.getUniqueId(), enabled);
    }

    private void loadLocalPlayers() {
        playersFile = new File(plugin.getDataFolder(), "QuickStash/players.yml");
        if (!playersFile.exists()) {
            playersCfg = new YamlConfiguration();
            return;
        }
        // 只加载文件作为"数据库读不到时的本地兜底"，
        // 不再预载进 enabledMap 缓存，否则本地旧值会抢先命中缓存、导致跨服数据库永远读不到
        playersCfg = YamlConfiguration.loadConfiguration(playersFile);
    }

    /** 玩家加入时清除内存缓存，强制从数据库重新读取最新开关状态（多服同步）。 */
    public static void onPlayerJoin(UUID uuid) {
        if (instance != null) {
            instance.enabledMap.remove(uuid);
        }
    }

    private void saveLocalPlayer(UUID uuid, boolean enabled) {
        ensurePlayersCfg();
        playersCfg.set(uuid.toString(), enabled);
        try {
            playersCfg.save(playersFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[QuickStash] 保存本地玩家开关失败: " + e.getMessage());
        }
    }

    private void saveLocalPlayers() {
        if (playersCfg == null || playersFile == null) {
            return;
        }
        try {
            playersCfg.save(playersFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[QuickStash] 保存本地玩家开关失败: " + e.getMessage());
        }
    }

    private void ensurePlayersCfg() {
        if (playersCfg == null) {
            playersCfg = new YamlConfiguration();
        }
        if (playersFile == null) {
            playersFile = new File(plugin.getDataFolder(), "QuickStash/players.yml");
        }
        File dir = playersFile.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
    }

    // ==================== 命令处理（/bwpro quickstash ...）====================

    /**
     * 处理 /bwpro quickstash 子命令，由 TaskCommand 委托调用。
     */
    public static boolean handleCommand(CommandSender sender, String[] args) {
        if (instance == null) {
            sender.sendMessage(ChatColor.RED + "快捷存入模块未初始化！");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "仅玩家可执行此命令");
            return true;
        }
        Player player = (Player) sender;
        Config config = instance.config;
        if (!player.hasPermission("bwpro.quickstash.use")) {
            config.msg(player, "cmd-no-permission");
            return true;
        }
        if (args.length < 2) {
            sendHelp(player);
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "gui":
                instance.openGui(player);
                break;
            case "on":
                instance.setEnabledFor(player, true);
                config.msg(player, "cmd-on");
                instance.hintLocalOnly(player);
                break;
            case "off":
                instance.setEnabledFor(player, false);
                config.msg(player, "cmd-off");
                instance.hintLocalOnly(player);
                break;
            case "status": {
                boolean cur = instance.isEnabledFor(player);
                config.msg(player, cur ? "cmd-status-on" : "cmd-status-off");
                break;
            }
            case "reload":
                if (!player.hasPermission("bwpro.quickstash.admin") && !player.isOp()) {
                    config.msg(player, "cmd-no-permission");
                    return true;
                }
                reload();
                config.msg(player, "cmd-reload");
                break;
            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    private static void sendHelp(Player player) {
        Config config = instance.config;
        config.msg(player, "cmd-help");
        config.msg(player, "cmd-help-detail");
    }

    /** 数据库未配置时提示"仅保存在本地"。 */
    private void hintLocalOnly(Player player) {
        if (!dbAvailable) {
            config.msg(player, "db-local-only-hint");
        }
    }

    // ==================== GUI ====================

    /** 打开快捷存入设置界面。 */
    public void openGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, GUI_TITLE);
        updateGui(inv, player);
        player.openInventory(inv);
    }

    private void updateGui(Inventory inv, Player player) {
        boolean enabled = isEnabledFor(player);

        // 槽0: 开关按钮
        ItemStack btn = new ItemStack(Material.WOOL, 1, enabled ? (short) 5 : (short) 14);
        ItemMeta btnMeta = btn.getItemMeta();
        btnMeta.setDisplayName(enabled
                ? ChatColor.GREEN + "快捷存入: " + ChatColor.WHITE + "开启"
                : ChatColor.RED + "快捷存入: " + ChatColor.WHITE + "关闭");
        btnMeta.setLore(java.util.Arrays.asList(
                ChatColor.GRAY + "点击" + (enabled ? "关闭" : "开启") + "快捷存入功能"));
        btn.setItemMeta(btnMeta);
        inv.setItem(0, btn);

        // 槽1: 功能说明
        ItemStack info = new ItemStack(Material.CHEST);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.YELLOW + "快捷存入说明");
        infoMeta.setLore(java.util.Arrays.asList(
                ChatColor.GRAY + "手持物品 左键箱子: 存入手中整组",
                ChatColor.GRAY + "潜行 + 左键箱子: 存入背包全部同类物品",
                ChatColor.GRAY + "右键箱子: 正常打开",
                ChatColor.GRAY + "剑/工具/弓等物品无法存入"));
        info.setItemMeta(infoMeta);
        inv.setItem(1, info);

        // 槽2-8: 灰色玻璃填充
        for (int i = 2; i < 9; i++) {
            ItemStack sep = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 7);
            ItemMeta meta = sep.getItemMeta();
            meta.setDisplayName(" ");
            sep.setItemMeta(meta);
            inv.setItem(i, sep);
        }
    }

    /** 处理 GUI 点击（由 DepositListener 委托）。 */
    public void handleGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (event.getView().getTitle() == null
                || !event.getView().getTitle().equals(GUI_TITLE)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() != 0 || event.getCurrentItem() == null) {
            return;
        }
        boolean next = !isEnabledFor(player);
        setEnabledFor(player, next);
        config.msg(player, next ? "gui-on" : "gui-off");
        hintLocalOnly(player);
        updateGui(event.getView().getTopInventory(), player);
    }
}
