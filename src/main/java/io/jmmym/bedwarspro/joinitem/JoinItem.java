package io.jmmym.bedwarspro.joinitem;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.PacketType;
import io.jmmym.bedwarspro.BedwarsPRO;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 加入物品（JoinItem）模块主类。
 *
 * <p>功能：
 * <ul>
 *   <li>玩家加入服务器 / 切换世界时，若所在世界符合黑白名单规则，自动在快捷栏指定槽位发放物品</li>
 *   <li>右键该物品执行配置的加入游戏指令（默认 /bw autojoin）</li>
 *   <li>仅在「一端多图」（非 BungeeCord）模式且功能开启时生效</li>
 *   <li>为避免覆盖玩家物品：目标槽位已有物品时放入快捷栏第一个空位，快捷栏已满则不发放</li>
 * </ul></p>
 */
public class JoinItem {

    private static JoinItem instance;

    private final BedwarsPRO plugin;
    private JoinItemConfig config;
    private boolean listenerRegistered = false;
    private PacketAdapter packetListener;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private JoinItem(BedwarsPRO plugin) {
        this.plugin = plugin;
    }

    public static JoinItem getInstance() {
        return instance;
    }

    public static JoinItemConfig getConfig() {
        return instance == null ? null : instance.config;
    }

    /** 插件启用时调用。 */
    public static void init(BedwarsPRO plugin) {
        if (instance == null) {
            instance = new JoinItem(plugin);
        }
        instance.enable();
    }

    /** 插件禁用时调用。 */
    public static void shutdown() {
        if (instance != null) {
            instance.disable();
            instance = null;
        }
    }

    /** 重载加入物品配置（供 /bwpro reload 与 /bwpro joinitem reload 调用）。 */
    public static void reload() {
        if (instance != null) {
            instance.config.load();
            for (Player p : Bukkit.getOnlinePlayers()) {
                instance.apply(p);
            }
        }
    }

    /** /bwpro joinitem ... 模块命令。 */
    public static boolean handleCommand(CommandSender sender, String[] args) {
        if (instance == null) {
            sender.sendMessage(ChatColor.RED + "加入物品模块未初始化！");
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("reload")) {
            sender.sendMessage(ChatColor.RED + "用法: /bwpro joinitem reload");
            return true;
        }
        if (!sender.hasPermission("bwpro.joinitem.admin") && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令！");
            return true;
        }
        try {
            reload();
            sender.sendMessage(ChatColor.GREEN + "加入物品配置已重载。");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "重载失败: " + e.getMessage());
        }
        return true;
    }

    /** 判断物品是否为配置中的加入物品（材质 + 显示名匹配任意条目）。 */
    public static boolean isJoinItem(ItemStack item) {
        if (instance == null || item == null) {
            return false;
        }
        for (JoinItemConfig.ItemEntry entry : instance.config.getItems()) {
            if (instance.isOurItem(item, entry)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 生命周期 ====================

    private void enable() {
        config = new JoinItemConfig(plugin);
        config.load();
        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(new JoinItemListener(), plugin);
            listenerRegistered = true;
        }
        // 1.8 中右键空气对手持非食物/水桶类物品（如下界之星/粘液球）不会触发 PlayerInteractEvent，
        // 但客户端仍会发送 PacketPlayInBlockPlace（face=255），用 ProtocolLib 监听补上右键空气场景
        registerPacketListener();
        for (Player p : Bukkit.getOnlinePlayers()) {
            apply(p);
        }
        if (isActive()) {
            plugin.getLogger().info("[JoinItem] 加入物品已启用（一端多图模式）。");
        } else {
            plugin.getLogger().info("[JoinItem] 加入物品未启用（需在配置中开启，且处于一端多图/非 BungeeCord 模式）。");
        }
    }

    private void disable() {
        cooldowns.clear();
        unregisterPacketListener();
    }

    /** 注册 ProtocolLib 包监听：拦截 1.8 右键空气（BLOCK_PLACE face=255）。 */
    private void registerPacketListener() {
        if (packetListener != null
                || !Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            return;
        }
        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            packetListener = new PacketAdapter(plugin, ListenerPriority.NORMAL,
                    PacketType.Play.Client.BLOCK_PLACE) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    if (event.isCancelled()) {
                        return;
                    }
                    try {
                        // 1.8 PacketPlayInBlockPlace 的 NMS 字段：BlockPosition / int b(方向) / ItemStack / float*3，
                        // 没有 byte 字段，方向存在 int b 中（getIntegers().read(0)），255 = 右键空气（使用物品）。
                        Integer face = event.getPacket().getIntegers().read(0);
                        if (face != null && face == 255) {
                            final Player player = event.getPlayer();
                            // 包监听运行在 netty 线程，命令执行/插件消息必须切回主线程
                            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                                @Override
                                public void run() {
                                    JoinItem.this.onInteract(player);
                                }
                            });
                        }
                    } catch (Exception ignored) {
                    }
                }
            };
            pm.addPacketListener(packetListener);
        } catch (Exception ignored) {
            // ProtocolLib 不可用或版本不兼容时不注册，右键空气场景退化为不可用
        }
    }

    private void unregisterPacketListener() {
        if (packetListener != null) {
            try {
                ProtocolLibrary.getProtocolManager().removePacketListener(packetListener);
            } catch (Exception ignored) {
            }
            packetListener = null;
        }
    }

    // ==================== 判断 ====================

    /** 功能是否真正生效：配置开启且处于一端多图（非 BungeeCord）模式。 */
    public boolean isActive() {
        return config != null && config.isEnabled() && !plugin.isBungee();
    }

    private boolean inGame(Player player) {
        return plugin.getGameManager() != null
                && plugin.getGameManager().getGameOfPlayer(player) != null;
    }

    // ==================== 应用 ====================

    /**
     * 对玩家应用快捷物品：符合条件则确保每个配置物品的槽位有物品；游戏中不做处理。
     */
    public void apply(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!isActive() || inGame(player)) {
            return;
        }
        if (!config.isWorldEnabled(player.getWorld().getName())) {
            return;
        }
        for (JoinItemConfig.ItemEntry entry : config.getItems()) {
            giveItem(player, entry);
        }
    }

    /** 在玩家快捷栏发放单个快捷物品（目标槽位已有物品时放入第一个空位，已满则放弃）。 */
    private void giveItem(Player player, JoinItemConfig.ItemEntry entry) {
        ItemStack item = buildItem(entry);
        if (item == null) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        int slot = entry.slot;
        ItemStack current = inv.getItem(slot);
        if (isOurItem(current, entry)) {
            return; // 已持有该物品
        }
        int target = -1;
        if (current == null || current.getType() == Material.AIR) {
            target = slot;
        } else {
            // 快捷栏 0-8 找第一个空位
            for (int i = 0; i < 9; i++) {
                ItemStack it = inv.getItem(i);
                if (it == null || it.getType() == Material.AIR) {
                    target = i;
                    break;
                }
            }
        }
        if (target >= 0) {
            inv.setItem(target, item.clone());
        }
    }

    /** 判断物品是否为指定配置的快捷物品（材质 + 显示名匹配）。 */
    public boolean isOurItem(ItemStack item, JoinItemConfig.ItemEntry entry) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (item.getType() != getMaterial(entry)) {
            return false;
        }
        String name = entry.name;
        if (name == null || name.isEmpty()) {
            return true; // 未配置名称，仅按材质匹配
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        return ChatColor.stripColor(meta.getDisplayName())
                .equals(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name)));
    }

    private Material getMaterial(JoinItemConfig.ItemEntry entry) {
        Material mat = Material.matchMaterial(entry.material);
        return mat == null ? Material.NETHER_STAR : mat;
    }

    private ItemStack buildItem(JoinItemConfig.ItemEntry entry) {
        Material mat = getMaterial(entry);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String name = entry.name;
        if (name != null && !name.isEmpty()) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }
        if (!entry.lore.isEmpty()) {
            java.util.List<String> lore = new java.util.ArrayList<>();
            for (String line : entry.lore) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 右键触发 ====================

    /** 玩家退出时清理冷却记录。 */
    public void onQuit(Player player) {
        if (player != null) {
            cooldowns.remove(player.getUniqueId());
        }
    }

    /** 右键处理（由监听器调用）：匹配物品后校验冷却并执行对应指令。 */
    public void onInteract(Player player) {
        if (!isActive() || player == null || !player.isOnline()) {
            return;
        }
        if (inGame(player)) {
            return;
        }
        if (!config.isWorldEnabled(player.getWorld().getName())) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInHand();
        JoinItemConfig.ItemEntry matched = null;
        for (JoinItemConfig.ItemEntry entry : config.getItems()) {
            if (isOurItem(hand, entry)) {
                matched = entry;
                break;
            }
        }
        if (matched == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long cd = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (cd > now) {
            return; // 冷却中
        }
        cooldowns.put(player.getUniqueId(), now + matched.cooldown * 50L);
        String command = matched.command;
        if (command == null || command.isEmpty()) {
            return;
        }
        String firstToken = command.split(" ")[0];
        boolean localCommand = hasLocalCommand(firstToken);
        if (localCommand) {
            player.performCommand(command);
        } else if (plugin.getBungeeHub() != null && !plugin.getBungeeHub().isEmpty()) {
            // 本地子服没有该命令（如 BungeeCord 代理端的 /hub）：
            // 通过 BC 插件消息直接把玩家送到 hubserver 服务器，效果等同代理端的 /hub
            connectToHub(player);
        } else {
            player.performCommand(command); // 未配置 hubserver：交给原版提示 Unknown command
        }
    }

    /**
     * 判断指定命令是否在本子服注册。
     * <p>1.8.8 的 Bukkit Server 接口没有 getCommandMap() 方法，这里用反射调用
     * CraftServer#getCommandMap()（各版本 CraftBukkit/Paper 均内置该方法），跨版本兼容。</p>
     */
    private boolean hasLocalCommand(String firstToken) {
        try {
            java.lang.reflect.Method m = Bukkit.getServer().getClass().getMethod("getCommandMap");
            Object commandMap = m.invoke(Bukkit.getServer());
            java.lang.reflect.Method getCommand = commandMap.getClass().getMethod("getCommand", String.class);
            return getCommand.invoke(commandMap, firstToken) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通过 BungeeCord 插件消息将玩家连接到主配置 bungeecord.hubserver 指定的服务器。
     * 等价于玩家在代理端手动输入 /hub。
     */
    private void connectToHub(Player player) {
        String hub = plugin.getBungeeHub();
        if (hub == null || hub.isEmpty()) {
            player.sendMessage(ChatColor.RED + "未配置 bungeecord.hubserver，无法返回大厅！");
            return;
        }
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        try {
            out.writeUTF("Connect");
            out.writeUTF(hub);
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } catch (Exception ignored) {
        }
    }
}
