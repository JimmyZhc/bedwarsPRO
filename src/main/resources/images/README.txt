========================================
版本: 1.3.4 (2026-08-19)

更新内容:
- 修复 "添加占位handler失败: InstantiationException" 与
  "注册Corpse通道失败: InstantiationException"(真正根因):
  - 两处占位 handler 都用了 Class.forName("io.netty.channel.ChannelHandlerAdapter")
    .getConstructor().newInstance() —— ChannelHandlerAdapter 是抽象类，
    反射实例化必然抛 InstantiationException，占位 handler 从未注入成功
  - 改为 io.netty.channel.ChannelDuplexHandler（具体类、public 无参构造），
    占位 handler 注入成功 → registerCorpseChannel 走通 → bot 的假 channel
    注册进 Corpse 内嵌 packetevents 的 CHANNELS/USERS
- 连带修复 "第一次尸体也不消失" 与 Tab 多次显示同名:
  - 之前 registerCorpseChannel 失败 → CorpsePool.corpseTick（异步每 2 tick）
    对 bot show 尸体时 getChannel(bot uuid) 返回 null → CorpseNPC.spawn(null)
    抛 NPE，但 show() 已无条件把 bot 塞进 seeingPlayers；
    尸体移除时 CorpsePool.remove 内部对 bot 逐个 hide 又因 channel null 抛 NPE
    中断 forEach → 真人收不到 Destroy → 尸体实体永久残留（包括第一次死亡）
  - 修复后 bot 有真实可写的 EmbeddedChannel：show/hide(bot) 都只写入
    outbound 缓冲不再抛异常，尸体移除链路完整，真人正常收到 Destroy
    （配合 markCorpseShownForAll 清除 bot + forceHideCorpse + 补发
      REMOVE_PLAYER，尸体消失、Tab 条目收敛）
- 修复 /tp、/kill 仍找不到实体:
  - 1.12.2 的 PlayerList 中 UUID 映射字段被混淆为 j（不是 playersByUUID），
    之前反射 playersByUUID 永远找不到、按 UUID 解析路径一直失效；
    现改为按"字段类型是 Map 且现有 key 是 UUID"匹配，1.8-1.12 通用
  - bot 死亡瞬间（PlayerDeathEvent）立即调用 ensureBotsInServerLists 补位，
    不再只依赖每 5 tick 的周期任务：确保 bot 始终在 PlayerList.players
    （/tp /kill 按名字解析走 getPlayer(String) 遍历该列表）
  - ensureBotsInServerLists 改为 public，供死亡/复活流程直接调用

========================================
版本: 1.3.3 (2026-08-19)

更新内容:
- 修复假人秒复活：
  - bot 复活延迟由 20 tick（1 秒）改为跟随计分板 respawn.respawn_delay
    （scoreboard/config.yml，默认 5 秒），死亡后与真人一致的复活节奏，
    不再"秒复活"
- 修复"第二次死亡后尸体躺地上不消失"（真正根因，实测实证）：
  - 反编译确认 Corpse v3.0.0：Corpse 构造器内部直接 takeCareOf（尸体创建即
    进 corpseMap），CorpsePool.corpseTick 是 runTaskTimerAsynchronously 延迟
    20 tick 启动、每 2 tick 异步遍历 Bukkit.getOnlinePlayers()（含 bot 假人），
    对每个尸体 show(player) 且 show() 无条件把玩家塞进尸体 seeingPlayers
  - 第一次死亡时尸体在 corpseTick 启动前（20 tick 内）就被移除，所以正常；
    之后每次死亡，corpseTick 都会把 bot show 进尸体 seeingPlayers。若 bot 的
    假 channel 注册失败（控制台"注册Corpse通道失败"），CorpsePool.remove(id)
    内部对 seeingPlayers 逐个 hide(bot) 会在 despawn(null) 抛 NPE，forEach
    中断 → 排在后面的真人收不到 Destroy → 尸体实体永久残留
  - 修复：markCorpseShownForAll 改为从 seeingPlayers 清除 bot（不光是"不添加"）；
    removeCorpseForBot 与周期清理任务的 remove 调用单独 try-catch，即使 remove
    异常也继续 forceHideCorpse（保证真人实体消失）+ REMOVE_PLAYER（清 Tab）
  - "注册Corpse通道失败"/"添加占位handler失败"日志附带异常类型，方便下次排查
- 新增 /bwpro debug on|off|status（管理员，带 Tab 补齐）：
  - 所有 [Bot] 调试日志（NMS版本/假人创建/加入服务器列表/PlayerInfo/
    Spawn+Metadata+Head/复活/周期保位/移除Corpse尸体/占位handler失败等）
    默认不再输出到控制台，管理员执行 /bwpro debug on 才开启排查，
    /bwpro debug off 关闭，/bwpro debug status 查看当前状态
  - BotManager 发给玩家的聊天消息（/bwbot 相关提示）不受此开关影响
- 尸体移除日志两处统一：
  - bot 尸体移除有两条路径：死亡后延迟 2 tick 主动移除 + 每 5 tick 周期
    兜底任务。之前只有主动移除路径打印"已移除Corpse尸体"，周期任务抢先
    移除时该日志不出现（误以为只有第一次死亡才移除）
  - 现在周期兜底任务移除 bot 尸体时同样打印"已移除Corpse尸体"（受 debug
    开关控制），每次死亡都能在日志中看到移除记录

========================================
版本: 1.3.2 (2026-08-19)

更新内容:
- 修复 Tab 栏清理彻底失效（日志"未找到PlayerInfoData构造器"）：
  - 根因：sendRemovePlayerInfo 用 Class.forName(...EnumGamemode) 与构造器参数
    类型做 == 引用比较。1.8/1.9 的枚举类实际是 WorldSettings$EnumGamemode
    （类名不同必然失败），且插件 classloader 与 NMS 类可能不是同一个 Class
    实例，== 比较同样失败 → 每次清理都静默跳过 → 构造器找不到 → Tab 里
    bot/尸体名字一次比一次多
  - 修复（javap 实测 1.8-1.12 五个版本构造器签名后重写）：按参数类型名尾缀
    分类匹配（外层实例 + GameProfile + int + *EnumGamemode + *IChatBaseComponent），
    从构造器真实枚举类取 NOT_SET、用构造器真实 GameProfile 类型反射建实例，
    彻底兼容 1.8/1.9（WorldSettings$EnumGamemode）与 1.10-1.12（EnumGamemode）
- 修复"遗体不会消失"：
  - 根因：markCorpseShownForAll 把 bot 也塞进 CorpsePool 的 seeingPlayers，
    移除尸体时 CorpsePool.remove 内部对每个 seeingPlayer 逐个 hide(bot)，
    假玩家的 channel 处理异常会中断 forEach，排在后面的真人收不到 Destroy
    包 → 尸体实体永久留在客户端
  - 修复：塞 seeingPlayers 时跳过 bot（假玩家从不真正收包），真人的隐藏
    由 forceHideCorpse 逐人兜底，遗体正常消失
- 修复 /tp /kill 找不到实体（保位加固）：
  - ensureBotsInServerLists 去掉 !p.isValid() 门控（死亡中的 bot isValid() 为
    false，之前被跳过，bot 脱离 PlayerList 后 /tp /kill 命中不到）
  - 新增 PlayerList.playersByUUID 注册（按 UUID 解析目标的命令/API 也能命中）
  - 世界实体列表补回不再区分死亡/存活，死亡中的 bot 也尝试保持在世界里

========================================
版本: 1.3.1 (2026-08-19)

更新内容:
- 修复所有地图提示"有队伍没有设置床"导致全部停止（换新版本后突然出现）：
  - 根因：Team 反序列化时用"加载瞬间 head 方块是否为床"来决定是否恢复
    bedfeed（Team(Map) 构造器里 getBlock().getType().equals(BED_BLOCK) 判断）。
    服务器启动早期 / 世界 chunk 未加载时，床方块 getType() 返回 AIR →
    feet 被永久丢弃（deserialize 只执行一次）→ 重启后每张图的每支队伍
    feet 恒为 null → checkGame 永远报 TEAM_NO_WRONG_BED
    （saveGame 保存瞬间床是正常的，因为那时方块已加载、校验能通过，
      所以"明明设置过也保存过"仍然报错）
  - 修复（Team.deserialize）：有 bedhead/bedfeed 就无条件恢复，不再依赖
    加载瞬间的方块类型；床的真实性改由运行时 checkGame 校验
  - 兜底（Game.checkTeams）：feet 为 null 或非床时，按 head 的相邻床块
    自动补算（床固定两格），兼容历史配置缺 bedfeed 的情况
  - 注意：若服务器上某张图实际床方块确实被破坏/替换了，仍会如实报错，
    需进游戏重新摆床再 /bw setbed

========================================
版本: 1.3.0 (2026-08-19)

更新内容:
- 修复 Bot Tab 栏同名条目残留（上一版 1.2.9 补发 REMOVE_PLAYER 实际没发出去）：
  - 根因（javap 反编译 Corpse-3.0.0.jar 实证）：
    - CorpseNPC.despawn 只发 DestroyEntities，从不发 REMOVE_PLAYER；
      尸体 show 时用 UUID.randomUUID() 发 ADD_PLAYER → Tab 条目永久累积，
      bot 每死一次 Tab 就多一个名字，尸体移除后名字仍保留
    - CorpsePool 中实际实例是 LootableCorpse 等子类，profile 字段声明在
      父类 Corpse（protected final）——1.2.9 用 getDeclaredField("profile")
      只查当前类，子类实例上抛 NoSuchFieldException 被静默吞掉 →
      corpseUuid 恒为 null → REMOVE_PLAYER 从未发出，清理形同虚设
    - sendRemovePlayerInfo 用 ps[0] == GameProfile.class 引用比较，不同
      classloader 下构造器匹配失败也会静默跳过
  - 修复（BukkitFakePlayer）：
    - getCorpseProfileUuid：沿父类链逐级 getDeclaredField 查找 profile，
      再反射调 GameProfile.getUUID()，适配 LootableCorpse 等全部子类
    - sendRemovePlayerInfo 重写：GameProfile 判断改为类名匹配（防 classloader
      差异）；跳过所有 bot（不给假玩家发包）；log=true 时输出详细成功/失败日志
    - 新增周期兜底任务 startCorpseTabCleanupTask（每 5 tick 主线程）：
      · bot 名尸体：CorpsePool.remove(id) + 补发 REMOVE_PLAYER（含日志）
      · 真人尸体：对能看到它的在线真人补发 REMOVE_PLAYER（广播一次即收敛）
      · ensureBotsInServerLists：确保每个存活 bot 在 PlayerList.players 与
        WorldServer.entityList 中，缺失即补加 —— 修复 /tp、/kill 找不到实体
  - 真人玩家重生时（scheduleCleanupPlayerCorpseTabs）同样补发 REMOVE_PLAYER

========================================
版本: 1.2.9 (2026-08-19)

更新内容:
- 修复 bot 复活后 Tab 显示 2 次同名：
  - respawnFakePlayer 广播 REMOVE_PLAYER + ADD_PLAYER，强制客户端把 bot 的
    Tab 条目收敛为一条
- 修复尸体移除后 Tab 名字残留（首次尝试，1.3.0 修复其静默失败）：
  - 移除 Corpse 尸体后延迟补发 REMOVE_PLAYER
- 修复复活后的 bot /tp、/kill 提示"无法找到实体"：
  - 1.12 中 dead 实体会被 World 从 entityList 移除，respawnFakePlayer 现在
    反射检查 worldServer.entityList，实体不在则重新 addEntity 回世界

========================================
版本: 1.2.8 (2026-08-19)

更新内容:
- 修复游戏结束菜单「再来一次」点击无效（提示"你不能在一个已开始中的游戏怎么做"）：
  - 根因：runGameOver 先发放菜单（下界之星第9格）再等 gameoverdelay 倒计时结束
    才把玩家踢出游戏。玩家在"拿到菜单 → 被踢出"这段时间里游戏状态仍是 RUNNING，
    「再来一次」复用 JoinGameCommand 时被 RUNNING 拦截（notwhileingame）直接拒绝
  - 修复（JoinGameCommand.execute）：
    - 玩家所在游戏为 RUNNING 且已进入结束流程（GameCycle.isEndGameRunning()）时，
      先调用 game.playerLeave(player, false) 让玩家干净退出旧游戏，
      再继续按记录的选择重新加入（casual item / casual xp / ranked / random / 指定图名）
    - 仍在正常进行中的对局保持原行为：拒绝加入并提示 notwhileingame
    - playerLeave 在结束流程中是安全的：统计"离队判负"、hide/show 等逻辑均以
      isEndGameRunning() 为守卫跳过；onGameEnds 的踢人循环只遍历仍在游戏的玩家，
      已退出的玩家不会被重复处理
  - 修复（ReturnLobbyListener.handleRejoin 兜底分支）：没有 /bw join 记录
    （通过牌子/自动加入进游戏）的玩家在结束流程中点「再来一次」不再提示
    "游戏已开始，无法加入"，改为先退出旧游戏返回大厅

========================================
版本: 1.2.7 (2026-08-19)

更新内容:
- 修复 Corpse 尸体导致 bot Tab 名字每死一次增加一个 / 尸体残留：
  - 现象：bot 死几次 Tab 就增加几个相同的 bot 名字；遗体清除后 Tab
    名字仍保留；死亡点留下"站着/躺着的 bot"造成 tp/kill 找不到实体的困惑
  - 根因（javap 反编译 Corpse-3.0.0.jar 实证）：
    - CorpsePool.handleDeath 监听 PlayerDeathEvent，任何玩家（含 bot 假人）
      死亡都会创建同名尸体（Corpse.fromPlayer）
    - 尸体由 corpseTick 每 2 tick 向附近在线玩家 show（发 ADD_PLAYER + 实体
      生成包）→ 真人 Tab 里每死一次多一个 bot 名字
    - bot 复活走反射流程（respawnFakePlayer），不触发 PlayerRespawnEvent，
      因此 CorpsePool.handleRespawn 永远不会清理 bot 的尸体 → Tab 名字永久残留
    - Corpse 是第三方插件无法直接修改，1.2.5 的 CHANNELS/USERS 注册只能保证
      它不崩溃，无法阻止它创建尸体
  - 修复（BukkitFakePlayer.scheduleRemoveCorpseForBot）：
    - bot 死亡时（PlayerListener.onPlayerDie bot 分支）延迟 2 tick 按名字找到
      CorpsePool 中所有同名尸体并 CorpsePool.remove(id)
    - CorpsePool.remove 内部会对所有看到尸体的人发 despawn（REMOVE_PLAYER +
      Destroy）→ 尸体消失、Tab 名字移除
    - bot 永久移除（removeFakePlayer：床拆/游戏结束）时兜底再清理一次
  - 附带改善：死亡点不再残留"bot 尸体"（1.12 无床时尸体可能站立，看起来像
    bot 无视复活时间原地复活），tp/kill 的目标更明确

========================================
版本: 1.2.6 (2026-08-19)

更新内容:
- 修复游戏结束菜单（下界之星第9格）「再来一次」按钮无效：
  - 原逻辑：点击后直接尝试加入玩家当前所在的游戏，游戏已开始则提示
    "游戏已开始，无法加入"，按钮形同虚设
  - 新逻辑：按玩家最近一次 /bw join 的选择重新加入（选的啥就是啥）：
    - 最开始选的 casual item → 点击等价于 /bw join casual item
    - 最开始选的 casual xp  → 点击等价于 /bw join casual xp
    - 最开始选的 ranked    → 点击等价于 /bw join ranked
    - 最开始选的 random    → 点击等价于 /bw join random
    - 指定图名加入的        → 点击等价于 /bw join <图名>
  - 实现（JoinGameCommand + ReturnLobbyListener）：
    - JoinGameCommand.execute 执行 /bw join 时把参数记录到
      ReturnLobbyListener.LAST_JOIN_MODE（uuid → 模式）
    - ReturnLobbyListener.handleRejoin 读取记录并复用 JoinGameCommand
      的完整加入逻辑（排位队列 / casual 选图 / 模式过滤）
    - 没有记录的玩家（非 /bw join 进入）退回原加入逻辑

========================================
版本: 1.2.5 (2026-08-19)

更新内容:
- 修复 Corpse v3.0.0 插件对 bot 假人崩溃（NullPointerException）：
  - 报错：Cannot invoke "io.netty.channel.Channel.alloc()" because "o" is null
  - 根因：CorpsePool.corpseTick 每 2 tick 遍历 Bukkit.getOnlinePlayers()
    （包含 bot 假人），对每个玩家调 Corpse.show(player) →
    packetevents ProtocolManager.getChannel(player.getUniqueId()) 从静态
    CHANNELS map 取 channel。bot 是手动加入的假玩家，从未注册过 channel，
    返回 null → ChannelOperatorModernImpl.pooledByteBuf(null) →
    channel.alloc() 直接 NPE（真实玩家都有对应的 Netty Channel，bot 没有）
  - 修复（BukkitFakePlayer）：
    - bot 创建时（createFakePlayer）把它的 EmbeddedChannel 注册进 Corpse
      内嵌 packetevents：
      1) ProtocolManager.CHANNELS（uuid → channel）—— 解决 pooledByteBuf NPE
      2) ProtocolManager.USERS（channel.pipeline() → User，ClientVersion 取
         V_1_12_2）—— PacketWrapper.prepareForSend 还会调
         user.getClientVersion()，User 缺失同样会 NPE
      3) pipeline 补 packetevents 的 ENCODER_NAME 占位 handler ——
         CorpseNPC.spawn 写包走 writeAndFlushInContext(channel, ENCODER_NAME,
         buf)，pipeline 找不到该名字的 handler 会返回 null 再 NPE
    - bot 移除时（removeFakePlayer）同步从 CHANNELS / USERS 反注册，
      防止 packetevents 静态 map 泄漏（USERS 以 pipeline 为 key 强引用 channel）
  - Corpse 插件未安装时注册逻辑静默跳过，不影响 bot 本体功能；
    Corpse 后续对 bot 写包只会进入 EmbeddedChannel 的 outbound 缓冲，
    不会发给任何真实客户端，随 bot 移除一起被回收

========================================
版本: 1.2.4 (2026-08-19)

更新内容:
- 修复 bot 被打死后"立马复活"且"打不动"（杀不死）：
  - 根因：FastRespawn（快速复活）对 bot 假人同样生效，致命伤害被 cancel 并
    立即回满血+传送，bot 永远杀不死
  - 修复：FastRespawn.onDamage 开头跳过 bot，bot 走自己独立的
    死亡→20tick 复活流程（PlayerListener.onPlayerDie 床完好分支）
- 修复 bot 死亡后复活流程中断（卡死）：
  - 根因：复活任务先调用 setHealth，但 bot 死亡时 isDead() 为 true，
    1.12 CraftPlayer.setHealth 对 dead 玩家设置正血量抛
    IllegalArgumentException（Cannot set health of dead player），
    整个复活流程被 catch 吞掉，respawnFakePlayer 根本没执行
  - 修复：先 respawnFakePlayer（反射解除 NMS dead 标志）再 setHealth/食物/效果
- 修复复活后的 bot"打不动"、/tp /kill bot 提示"无法找到实体"：
  - 根因：1.12 中 dead 的实体会被 World 从 entityList 移除，respawnFakePlayer
    之前只解除 dead 标志并发包，没有把实体重新加入世界——服务端实体不在世界里，
    攻击无效、传送/击杀找不到实体
  - 修复：respawnFakePlayer 现在会反射检查 worldServer.entityList，实体不在则
    重新 addEntity 回世界
- 修复 bot 死亡/复活后 Tab 显示 2 次名字：
  - 根因：床战 hidePlayer/showPlayer（底层会发 PlayerInfo REMOVE/ADD 包）对
    bot 同样执行，时序错乱导致 Tab 里 bot 的 PlayerInfo 条目重复
  - 修复：
    - respawnFakePlayer 现在广播 REMOVE_PLAYER + ADD_PLAYER，强制客户端把该
      bot 的 Tab 条目收敛为一条（先清掉全部重复再 ADD 一次）
    - bot 不再参与床战 hidePlayer/showPlayer/setPlayerVisibility（Game.playerJoins
      5L/15L 任务、Game.setPlayerVisibility、Game.playerLeave 的 showPlayer 循环
      均跳过 bot），bot 的 PlayerInfo 只由 BukkitFakePlayer 管理
      （创建 ADD、复活 REMOVE+ADD、移除 REMOVE）
    - 后加入游戏的真人玩家通过 BukkitFakePlayer.sendVisibilityToPlayer 补发
      bot 的 ADD_PLAYER + NamedEntitySpawn + Metadata + Head，确保能看到
      已在游戏中的 bot（不依赖 hide/show）

========================================
版本: 1.2.3 (2026-08-19)

更新内容:
- /bw join casual 必须指定商店模式：
  - 不带参数直接拒绝加入并提示「请指定商店模式：/bw join casual <xp|item>」
  - /bw join casual xp 只加入经验模式的休闲图，/bw join casual item 只加入物品模式的休闲图
- 修复游戏结束后 Tab 重复显示玩家名 / 不定时多次显示 BOT 名字：
  - 根因：游戏结束时先重置计分板、踢出真人玩家，最后才在 finally 中清理 bot；
    真人被传送回大厅的过程中 setPlayerVisibility / hidePlayer 会对已残留的
    bot 条目重发 PlayerInfo 包，导致 Tab 里不定时重复显示 bot 名字
  - SingleGameCycle.onGameEnds 改为「先完整清理本对局所有 bot（停止 AI、移除
    床战玩家列表、移除服务器玩家列表、移除世界实体、广播 REMOVE_PLAYER），
    再踢出真人玩家」——真人传送回大厅时 bot 已完全不在玩家列表，不会再刷新进 Tab
  - Game.playerJoins 的两个延迟可见性任务（5L / 15L）加入「玩家仍在游戏中」守卫：
    玩家在任务执行前已离开本游戏（被踢出 / 游戏结束清理）则直接跳过，
    不再执行 hidePlayer / showPlayer / setPlayerVisibility 等会触发
    PlayerInfo 包的操作，避免对已移除的玩家（含 bot）重发 ADD_PLAYER
- /bwbot add 仅允许在等待中的对局添加 bot：
  - 游戏已开始（非 WAITING）时拒绝添加并提示「游戏已开始，只能在等待中加入Bot！」
  - 防止在已开始的对局中加 bot 时，bot 被随机塞进其他等待图导致进错图

========================================
版本: 1.2.2 (2026-08-19)

更新内容:
- 修复 /bwpro mapgui、rankreload、check、update 命令缺少 Tab 补齐
- 修复加入物品模块 Bug（对齐桌面备份版逻辑）：
  - onInteract 改为 inGame(player) + config.isWorldEnabled(worldAtEvent) 双重校验，与桌面版一致
  - apply() 增加 isInGameWorld(player) 前置拦截
  - JoinItemListener 添加 ignoreCancelled 注释说明
- 修复 locale 缺失 xp_mode 条目（bwsba-language.yml / bwsba/zh_CN/language.yml），/bwdba GUI 切换经验/物品模式现在正常显示名称
- 修复 1.9+（如 1.12）右键空气无效 Bug：
  - 原实现只监听 BLOCK_PLACE face=255（1.8 专用包），1.9+ 右键空气发的是 USE_ITEM 包，
    导致对着空气右键下界之星/粘液球无反应；现同时监听 BLOCK_PLACE + USE_ITEM 两个包
  - 1.8 仍走 face=255，1.9+ 走 USE_ITEM，对着方块右键由 PlayerInteractEvent 兜底，不重复触发
- 新增加入物品位置锁定：
  - 禁止通过背包点击/拖动移动加入物品（InventoryClick/InventoryDrag 拦截）
  - 配合原有的 PlayerDropItemEvent 禁止丢弃，快捷物品固定在发放槽位
- 还原桌面备份版商店逻辑（修复经验/物品模式完全无效的 Bug）：
  - 修复 Game.start 崩溃 ClassCastException（Integer cannot be cast to List）：
    原实现删除了桌面版对"数字 price"（经验商店 xp_shop.yml 的 price 直接写经验值）的解析支持，
    导致加载经验商店时把 Integer 当 List 强转而崩溃，商店初始化失败、经验模式不可用；
    已恢复桌面版双格式解析（List=物品商店 / Number=经验商店）
  - 物品商店文件名改回 item_shop.yml（与桌面版一致），旧 shop.yml 自动迁移，
    同步更新远程配置清单（REMOTE_CFG_PATHS / ConfigSync）及 SupportData 引用
- 还原桌面备份版剑购买逻辑（NewItemShop）：
  - 物品模式买剑改为替换到等级最低的剑位置，旧剑放入背包（不删除）
  - 移除排位赛版"同位置升级/同级拒绝"的改动
- 还原桌面备份版 TNT 羊逻辑（TNTSheep / TNTSheepListener）：
  - run() 恢复为 boolean：非羊蛋（如宠物系统的宠物蛋）返回 false 不消耗物品、
    不取消事件，交还宠物系统处理；只有真正消耗 TNT 羊蛋才取消右键事件
- 还原桌面备份版统计数据损坏防御（PlayerStatisticManager）：
  - getConfigurationSection 返回 null（路径被写为标量/异常数据）时移除该条数据并重建，
    避免 NPE 崩溃
- 修复 GiveItem 缺失的团队武器附魔（applyTeamWeaponEnchantment）：
  - 重生/首次发放的剑/斧直接带上队伍锋利附魔（桌面版有，排位赛版被删）
- 还原桌面备份版 /bw 命令 Tab 补齐（BedwarsPROCommandTabCompleter）：
  - /bw join 补齐 ranked / casual 选项
  - /bw join casual 补齐 xp / item 选项（指定经验模式/物品模式的休闲图）
- 修复 ProtocolLib WrappedDataWatcher 崩溃（兼容 5.1.0 / 5.4.0）：
  - 移除所有 new WrappedDataWatcher() 构造（LegacyDataWatcher.newHandle 在部分
    ProtocolLib 版本反射失败抛 NPE constructor，影响 ENTITY_METADATA 包监听）
  - ShopListener.onPacketSending / Shop.hideEntityTag / HolographicAPI.setTitle
    改用 WrappedWatchableObject（读取/修改包中已有对象，新增时 try-catch 兜底），
    隐藏村民/NPC/全息文字标签功能不变，且不再抛异常
  - 彻底移除全工程 WrappedDataWatcher 相关代码（含无用的 Registry.get 初始化），
    编译依赖 ProtocolLib 5.1.0（provided，运行时兼容 5.1.0 / 5.4.0）
  - 其余 ProtocolLib API（getIntegers/getStrings/getDoubles/getBooleans/
    getChatComponents/getEntityUseActions/getWatchableCollectionModifier 等）
    均为 5.1.0 稳定 API，无需改动
- 兼容 GrimAC(PacketEvents) 等反作弊插件共存（防止数据包冲突导致闪退）：
  - 全部 7 个 ProtocolLib 包监听（ShopListener/EventListener/EditGame/
    NoBreakBed/Spectator/JoinItem）的 onPacketReceiving/onPacketSending
    均加 try-catch 兜底，异常不再冒泡到 ProtocolLib/其他监听器
  - 唯一"修改数据包内容"的监听（ShopListener 隐藏商店 NPC 名字标签）
    新增 config 开关 hide-npc-name-tags（config.yml 其他设置区），
    与 GrimAC 冲突时可设为 false 彻底关闭该包修改
- 修复与 GrimAC(PacketEvents) 的数据包格式冲突（IndexOutOfBounds / DataWatcherObject.b()==null）：
  - 1.9+ 的 ENTITY_METADATA 包必须是 (index + type + value) 新格式，
    之前无 serializer 的 new WrappedWatchableObject(3, false) 会生成
    DataWatcherObject.b()==null 的损坏项，GrimAC 解析时抛 NPE 后越界
  - Shop.hideEntityTag / HolographicAPI.setTitle / ShopListener 新增分支
    在 1.9+ 统一改用带显式 serializer 的
    new WrappedWatchableObject(new WrappedDataWatcher.WrappedDataWatcherObject(
    index, Registry.get(Xxx.class)), value)（1.8 分支保留旧格式）
  - 读取已有对象用 setValue 修改（只改值、不动 serializer）保持安全
- 修复 bot 假人加入导致 ProtocolLib/GrimAC 报错与真实玩家被踢：
  - 原实现用 placeNewPlayer 走真实玩家加入流程，触发 PlayerJoinEvent 后
    ProtocolLib 5.1.0 对 bot 的假 channel 注入失败（NoSuchElementException: encoder），
    GrimAC(PacketEvents) 注入失败还会把真实玩家全部踢下线
  - bot 改为手动加入服务器（playerList.players.add + worldServer.addEntity），
    完全不触发 PlayerJoinEvent，对 ProtocolLib/GrimAC/ViaVersion 完全透明
- 修复 bot 假人生命周期残留（Tab 重复 / 数据混乱）：
  - BotPlayer.leaveGame() 现在会调用 game.playerLeave 从床战玩家列表/队伍/GameManager
    完全移除（此前 bot 永远残留在游戏中）
  - Game.playerLeave 对 bot 跳过统计写入与 bw stats 命令执行（假连接执行命令会崩），
    同时仍 unload 统计防止内存泄漏
  - removeFakePlayer 移除 bot 时发送 REMOVE_PLAYER 包，清理所有玩家 Tab 里
    残留的 bot 条目（此前 bot 的 Tab 条目永久残留，导致 Tab 显示混乱）
  - 游戏结束时 bot 走完整 unregisterBot（床战 + 服务器玩家列表 + 世界实体 + Tab 条目）；
    bot 加入游戏失败 / 无可用游戏时立即清理，不再残留
- 修复 bot 进错图：
  - /bwbot add <游戏> 指定游戏后，bot 现在优先加入指定对局（该对局在等待中且可加入时）
  - 指定对局不可用（不在等待 / 已满 / 异常）时，从所有等待中的对局里随机选一张
    （此前忽略指定游戏、永远找第一张等待图，导致 bot 进错图）
- 修复 /bw join casual 不优先聚人：
  - /bw join casual（含 casual item / casual xp）现在优先从「已有人在等待」的对局中
    随机选一张（聚人开局），避免各自开新图
  - 若所有等待对局都无人，再从全部等待中的对局里随机选一张
  - 带 item/xp 时仍只从对应商店模式的等待对局里选，不串模式
- 修复击杀 bot 假人无奖励、无提示：
  - 此前 bot 死亡在 PlayerListener.onPlayerDie 的 bot 分支直接 return，
    跳过了击杀者获得灵魂（下界之星）、击杀标题（X杀/经验+X）、击杀消息广播
  - 现在杀 bot 与杀真人一致：物品模式掉落灵魂、经验模式转移经验、
    击杀标题与击杀消息正常显示
  - GameCycle.onPlayerDies 对 bot 受害者/击杀者跳过统计数据写入（不污染统计文件），
    bot 击杀者也不执行 rewards.player-kill 奖励命令（假连接执行命令会崩）
- 修复 bot 残留（游戏结束后 Tab 仍显示 bot、显示多个名字）：
  - 根因 1：bot 加入游戏后从未调用 joinGame，BotPlayer.currentGame 永远为 null，
    导致 getBotsInGame/getBotCountInGame 永远为空——maxBots 上限失效（反复加 bot 不计数，
    Tab 里 bot 越来越多）、游戏结束清理找不到 bot、/bwbot list/remove 全部失效
  - 根因 2：BotManager.onGameEnd 只调 bot.leaveGame()（仅从床战移除），
    不调 unregisterBot/removeFakePlayer，bot 的服务器玩家列表条目残留导致 Tab 显示
  - 根因 3：removeFakePlayer 开头检查 !player.isOnline() 直接 return，
    bot 假玩家在死亡/移除状态下 isOnline() 不可靠，导致整段清理被跳过
  - 修复：addBotToGame 加入成功后调用 bot.joinGame(game) + game.addBot(bot)
    （任务注册/计数/清理恢复完整）；onGameEnd 改为对每个 bot 完整 unregisterBot；
    removeFakePlayer 移除 isOnline() 前置检查（player 非 null 即可清理）；
    SingleGameCycle.onGameEnds 的 finally 中兜底调用 onGameEnd，
    即使 kickPlayer 循环异常中断也会清理干净
- 修复 bot 假人死亡后不复活、Tab 出现重复名字：
  - 根因：FastRespawn 对假玩家只 setHealth+teleport，服务端 Entity.dead 标志未解除
    （isAlive() 仍为 false、AI 停止驱动），客户端视角 bot 实体死亡消失后不会重新出现，
    bot 卡在死亡状态；残留的假人 + 新加入的 bot 造成 Tab 出现多个名字
  - 修复：BotPlayer 死亡且床完好时，延迟 20 tick 完整复活——
    恢复血量/食物/清效果、反射解除 NMS Entity.dead 标志、
    向其他玩家重发 Destroy + NamedEntitySpawn + Metadata + Head 让客户端重新显示 bot、
    传送到队伍出生点、重置 AI 状态（BotPlayer.respawn()）
  - 无论 fast_respawn 开关如何，bot 死亡后都能正常复活
- 修复排位匹配人数写死 16 人：
  - 排位匹配满员人数、大厅倒计时、满员判断改为读取「该地图的最大游戏人数」
    （Game.getMaxPlayers() = 各队人数上限之和），不再固定用配置的 match.players；
    配置拿不到时回退 match.players
  - 蛇形分队从固定 16 人数组改为动态公式（偶数轮正向 0..n-1、奇数轮反向 n-1..0），
    适配任意地图人数（8/12/16/20 人等）均匀分入红绿蓝黄 4 队

========================================
版本: 1.2.1 (2026-08-13)

更新内容:
- /bw join casual 支持指定商店模式：
  - /bw join casual xp 只从经验模式休闲等待图中随机选图
  - /bw join casual item 只从物品模式休闲等待图中随机选图
  - 不带模式参数的 /bw join casual 行为不变（全部休闲图随机）
- /bw join Tab 补齐完善：
  - 第二个参数补齐 ranked / casual（含地图名）
  - /bw join casual 后补齐 xp / item

========================================
版本: 1.2.1 (2026-08-12)

更新内容:
- 修复排位匹配队列提示残留 {mode}：
  - 旧版 rank/messages.yml 中 queue.joined 文案仍含 {mode} 占位符，而该占位符已随全服模式移除，
    会导致消息原样显示「你已加入 {mode} 匹配队列」
  - 现在加载语言文件时自动检测并恢复为新版文案，同时写回配置文件（一次生效，无需手动删文件）
- 修复地图管理界面（/bwpro mapgui）休闲图图标显示「unknown map」：
  - 休闲图原用 Material.MAP 物品，1.8.8/1.12.2 客户端对无地图数据的 MAP 会显示 "unknown map"
  - 改为 PAPER（纸）图标，避免该显示问题
- /bwpro mapgui 界面标题由「排位图管理」改为「地图管理」

========================================
版本: 1.2.0 (2026-08-12)

更新内容:
- 移除「全服模式」概念（不再有全服统一的排位/休闲切换）：
  - 删除 /bwpro mode 命令与全服模式管理界面
  - 模式由玩家自己选择：/bw join ranked 进排位匹配队列，
    /bw join casual（或 /bw join random）随机进休闲图
  - 删除 /bwpro autojoin 系列命令（含 autojoin leave），退出队列统一用 /bw leave
- 排位图管理改为配置文件 + GUI：
  - /bwpro mapgui 打开排位图管理界面，左键点击任意等待中的图切换排位/休闲，
    自动保存到 rank.yml 的 ranked-games；/bwpro rankreload 重读配置
  - 排位匹配队列只会在排位图中随机开局；休闲随机会跳过排位图
  - 排位图中进行的对局按排位结算，与全服模式无关

========================================
版本: 1.1.9 (2026-08-12)

更新内容:
- 网站后台批量更新优化：
  - 「批量更新」合并进「检测更新」页面，进入检测更新页即可看到全部在线服务器、
    复选框/全选、推送按钮与推送中的更新列表，不再单独切换页面
  - 10 秒冷静期改为实时倒计时：剩余秒数每秒刷新，倒计时归零自动切换为
    「已结束（冷静期已过，等待服务器自动更新）」，同时「反悔取消」按钮
    自动变成「紧急停止」按钮
  - 每次推送都重置冷静期（覆盖同台服务器的历史推送记录时不再沿用旧时间），
    保证二次推送同样有完整的 10 秒反悔窗口
- 推送更新通知完善：
  - 服务器心跳（最长 30 秒）首次感知到后台推送时，即使 10 秒冷静期已过，
    也会向在线管理员发送「即将自动下载新插件并重启」的聊天通知（此前只打控制台日志）
- 启动版本检测明确化：
  - 每次启动都输出明确的检测结果：已是最新版本（当前版本号）/
    无法连接授权服务器（可稍后 /bwpro check 手动检测）/ 检测到新版本并提示更新
- 更新完成状态校验（防止「已更新完成」误标）：
  - 服务器上报完成时携带本机插件 jar 的 MD5，网站后台校验与目标新版本一致才
    标记为「已是最新版本」；多台服务器共用同一实例标识（auth-server-id）时，
    一台更新完成不再导致另一台旧版本被误标为已更新完成
  - 旧版本服务器在心跳中发现后台已标记该实例「已是最新版本」而本机仍非目标
    版本时，会自动补一次更新并重启（覆盖共用实例标识漏更 / 上次下载替换失败）
  - 状态文案统一为「已是最新版本」（网站后台与 /bwpro check 一致）

========================================
版本: 1.1.8 (2026-08-11)

更新内容:
- 快速加入智能选图（bc=false 一端多图）：
  - /bw autojoin、/bw autojoin item、/bw autojoin xp 优先加入「已有人在等待」的
    对局（从有人的等待对局中随机选一张），避免各自开新图
  - 若所有等待对局都无人，则从等待中的对局里随机选一张
  - 休闲模式下：item 只从物品模式等待对局里选，xp 只从经验模式等待对局里选，互不串图
- 排位模式修正：
  - /bw autojoin、/bw autojoin item、/bw autojoin xp 全部进入排位匹配队列
    （不再因带 item/xp 而误入休闲快速加入；item/xp 只是商店模式偏好，
    排位对局实际商店模式由该对局配置决定）
  - /bw join random 在排位模式下同样进入排位匹配队列（等效 /bw autojoin）；
    指定图名的 /bw join <图名> 仍可直接进入（排位模式下该局按排位结算）
  - 排位选房：首位排位玩家从空闲等待图中随机选一张（不再固定第一张），
    已有排位等待大厅有人时后续玩家集中进该房等满员；排位房玩家全部离开后
    自动释放（恢复默认开局人数），供休闲快速加入复用
  - 切换全服模式时，清理所有「未在对局中」的排位房标记（正在 RUNNING 的排位
    对局保留标记直到结算结束）——切到休闲模式后全图恢复为休闲图，/bw join random
    全图随机，无需再过滤排位房
  - 排位等待大厅右键「返回大厅」粘液球，现在会同时退出排位匹配队列（等效 /bw leave），
    不再残留队列被下一次匹配带上
- 排位数据跨服同步：
  - 本服本地无某玩家排位数据时，自动从共享数据库读取（A 服结算入库后，
    玩家转到 B 服可读到同一份 ELO / 段位 / 战绩），并落盘到本服本地
- 排位对局被强制停止（/bw stop 等，非正常结算）时立即释放该地图的排位标记，
  避免地图被休闲快速加入永久过滤
- 授权验证放行：启动时若因网络问题（UnknownHostException 等）连接不上授权服务器，
  不再阻止插件启动，改为临时放行，并在每 30 秒心跳中持续重连授权服务器；
  恢复连接后发现本实例未授权 / 被后台禁用时仍会自动停服
- 网站后台新增「批量更新」页面：显示当前所有在线服务器，每台服务器带复选框
  （含全选），勾选后一键向选中的服务器推送最新插件并自动重启（服务器无需二次确认）
- 推送更新后有 10 秒冷静期：期间可在网站端反悔取消（防止选错服务器）；
  冷静期过后服务器自动确认、下载新插件、校验 MD5 后替换并重启

========================================
版本: 1.1.7 (2026-08-11)

更新内容:
- 新增经验起床（XP Bedwars）模式，按对局选择物品商店 / 经验商店：
  - 模式开关：bwsba 编辑游戏 GUI 新增「经验模式开关」物品（经验瓶），点击即切换该对局
    为经验模式或物品模式，设置保存到 game.yml 的 xp-mode，并优先于全局配置
  - 全局配置兜底：config.yml 的 xp-bedwars.enabled（总开关）与 xp-bedwars.games
    （启用经验模式的对局名列表，如 [bedwars1, bedwars2]）仅对未单独设置的对局生效
  - 资源自动换算经验：经验模式下玩家捡到的铁锭 / 金锭 / 钻石自动转成经验
    （换算比例见 xp-bedwars.resource-xp，可自行配置每种资源每单位经验值）
  - 经验商店交易：商店价格按资源换算成总经验扣除，经验不足时提示「经验不足！」，
    不支持的物品在经验模式下标记「该商品在经验模式不可用」
  - 经验条体现：经验模式下经验值用原版经验条 + 等级数字显示（setLevel 同步），
    非经验模式对局 / 大厅的原版等级与经验条不受影响
  - 击杀经验转移：经验模式下击杀敌方玩家获得其当前全部经验（受害者清零），
    主标题不再显示「X杀」，改为白色「经验」+ 绿色「+获得经验值」
  - 拾取资源提示：经验换算后通过经验条上方的 ActionBar 显示
    绿色「你获得了」+ 黄色经验值 + 绿色「点经验」
  - 无特殊事件：经验模式地图不触发随机特殊事件，计分板上特殊事件行自动隐藏
  - 经验商店护甲：买的啥就是啥——单件购买、不自动穿戴、不附赠靴子；剑不自动替换，
    直接放入背包；经验模式护甲（含头盔/胸甲）可以自由取下（物品模式绑定护甲仍不可取下）
  - 剑购买规则：物品/经验商店都拒绝重复购买与低等级剑（全背包检索已有剑）；
    物品模式替换时新剑放到等级最低的旧剑位置，旧剑放入背包而非删除
  - 经验模式击杀不给下界之星（灵魂），击杀奖励即经验转移
  - 快速加入按模式分流（bc=false 一端多图）：/bw autojoin item 只加入物品模式对局，
    /bw autojoin xp 只加入经验模式对局（均带 Tab 补齐）；退出统一用 /bw leave
    （退出对局或退出排位匹配队列）
  - 商店配置按模式拆分：
    - shop/item_shop.yml —— 物品商店配置（原 shop.yml 已自动改名迁移），price 为资源物品列表
    - shop/xp_shop.yml   —— 经验商店配置（新增），结构同物品商店，price 直接写所需经验值（整数）
    - 经验模式下商店读取 xp_shop.yml，物品模式下读取 item_shop.yml，两套配置互不影响
  - 经验上限：xp-bedwars.max-xp 限制单局最高经验（0 = 无上限）
  - 死亡惩罚：xp-bedwars.death-cost 可设置死亡扣除经验比例（0-1，0 = 不扣）
  - 下界之星（击杀奖励「灵魂」）不参与经验换算，保持原有灵魂用途
  - 经验仅保存在单局内存中，对局结束自动清零，不跨对局保留

========================================
版本: 1.1.6 (2026-08-11)

更新内容:
- 更新提示信息仅管理员与控制台可见：
  - 检测到新版本 / 网站已同意 / 拒绝与取消 / 下载进度百分比 / 更新完成重启通知
    等所有更新提示只发送给在线管理员（bwpro.task.admin / OP），日志仅输出控制台
  - 普通玩家不再收到任何插件更新相关消息
  - /bwpro check、/bwpro update 命令已加权限校验，普通玩家执行提示无权限

========================================
版本: 1.1.5 (2026-08-10)

更新内容:
- 新增排位赛（Ranked Bedwars）系统：
  - 全服统一模式控制：/bwpro mode ranked|casual|status|reload（管理员，带 Tab 补齐）
  - 智能匹配：/bw autojoin 按模式分流（排位模式→排位匹配队列 / 休闲模式→休闲大厅），
    /bw autojoin leave 退出队列；排位对局地图自动标记，与休闲玩家房间完全隔离
  - 4 队 16 人对局，蛇形分配（按 ELO 降序分入红绿蓝黄四队）；两段式匹配（ELO 阈值可配置）
  - ELO 机制：K 因子分级（新玩家 40 / 低段位 25 / 常规 20 / 高段位 16），
    按名次加减分（第 1 名加分、第 2 名 ±0、第 3 名扣半、第 4 名正常扣分），MVP 额外加分
  - 15 大段位体系（煤炭 → 下界之星），定级赛机制（新玩家前 5 场）
  - 结算时评选 MVP（击杀 / 破床 / 生存时间 / 资源贡献）
  - 数据存储：本地 rank/players.yml 为主，连接数据库时自动同步至 rank_players 表
  - 战绩查询：/bw stats 新增排位段位、ELO、胜率、名次统计、连胜连败等展示
  - 语言文件可自由配置（rank/messages.yml、rank/rank.yml）
  - 新增 PlaceholderAPI 占位符（identifier: bwpro，需安装 PlaceholderAPI）：
    · 玩家排位数据：%bwpro_rank_tier%（段位中文名）/ %bwpro_rank_tier_en%（段位英文名）/
      %bwpro_rank_elo%（当前 ELO）/ %bwpro_rank_tier_elo%（段位名(ELO)）/
      %bwpro_rank_progress%（段位进度 0-100）/ %bwpro_rank_highest_elo%（历史最高 ELO）/
      %bwpro_rank_games%（总场次）/ %bwpro_rank_wins%（胜场）/
      %bwpro_rank_second% / %bwpro_rank_third% / %bwpro_rank_fourth%（各名次次数）/
      %bwpro_rank_winrate%（胜率）/ %bwpro_rank_kills%（击杀）/ %bwpro_rank_beds%（破床）/
      %bwpro_rank_avg_kills%（场均击杀）/ %bwpro_rank_avg_beds%（场均破床）/
      %bwpro_rank_win_streak%（连胜）/ %bwpro_rank_lose_streak%（连败）/
      %bwpro_rank_placement%（定级赛状态）
    · 服务器级：%bwpro_mode%（当前模式：排位/休闲）/ %bwpro_queue%（排位匹配队列人数）
  - 游戏内计分板内置占位符（bw / bwsba 模块，{花括号} 格式，用于计分板各行与 Actionbar）：
    · 玩家统计：{kills} 击杀 / {dies} 死亡 / {beds} 破床 / {totalkills} 总击杀 /
      {finalkills} 最终击杀 / {player_name} 玩家名 / {team} 队伍 / {color} 队伍颜色 /
      {team_peoples} 队伍人数 / {range} 距队伍出生点距离（仅 Actionbar）
    · 对局信息：{time} 游戏分钟 / {formattime} 格式化时间 / {game} 地图名 / {date} 日期 /
      {online} 在线人数 / {alive_teams} 存活队伍 / {remain_teams} 剩余队伍 /
      {alive_players} 存活玩家 / {teams} 队伍总数 / {bowtime} 凋灵弓倒计时 /
      {death_mode} 死亡模式 / {randomevent} 随机事件名 / {randomevent_time} 随机事件倒计时 /
      {team_bed_status} 本队床状态 / {no_break_bed} 无拆床倒计时 /
      {plan_timer_sec_<N>} 计划倒计时 / {sethealthtime_<N>} 生命等级倒计时 /
      {resource_upgrade_<N>} 资源升级倒计时
    · 队伍格式（team_status_format / playertag）：{you} 你 / {bed_status} 床状态 /
      {players} 队伍人数 / {team_initials} 队伍首字母 / {color_initials} 颜色首字母 /
      {color_name} 颜色名 / {team_<队伍名>_status} / {team_<队伍名>_bed_status} /
      {team_<队伍名>_peoples}
  - 修复排位对局中途切换模式不丢失结算（按地图标记判定）

========================================
版本: 1.1.4 (2026-08-08)

更新内容:
- 修复：插件无法加载的 NoClassDefFoundError（wrong name）
  - 原因：ProGuard 混淆生成了仅大小写不同的类名（如 L 与 l），Windows 大小写不敏感
    文件系统上类文件互相覆盖，JVM 加载时报 (wrong name:)
  - 修复：混淆加 -dontusemixedcaseclassnames，不再生成大小写歧义的类名
- 修复：log4j 解析 XML 失败告警（路径含中文时 MalformedURIException）
  - 原因：jar 内携带 log4j 依赖的 log4j.xml，XML 配置把中文路径当 URL 解析报错
  - 修复：打包排除 log4j.xml，改用内置 log4j.properties（Properties 纯文本格式，无此问题）

========================================
版本: 1.1.3 (2026-08-08)

更新内容:
- 安全修正：移除 config.yml 中的 auth-verify 开关（防止插件泄露后被改 false 绕过授权）
- 授权验证开关移至网站后台：登录后台 → 授权验证设置，可切换「正常验证 / 跳过验证」
  - 正常验证（默认）：插件必须把授权码加入后台白名单才能启动
  - 跳过验证：仅供插件作者调试用，签名合法的插件请求直接放行；关闭后未授权插件立即失效
  - 插件端不提供任何绕过入口，授权与否完全由后台控制

========================================
版本: 1.1.2 (2026-08-08)

更新内容:
- 授权系统新增在线状态统计：
  - 插件首次启动自动生成服务器实例唯一标识（auth-server-id，存于 config.yml），
    授权请求与每 30 秒一次的心跳都携带该标识上报授权服务器
  - 授权后台授权码列表新增“使用状态”列：正在运行的授权码显示绿色
    “该授权码正在被使用”（同一授权码部署多台服务器时显示台数），未使用显示灰色“未使用”
  - 后台标题新增“在线数量”（正在使用的授权码个数 + 服务器实例台数），每 5 秒自动刷新
  - 服务器超过 120 秒未上报心跳即视为离线（HEARTBEAT_TTL，config.php 可调）
- 协议升级：签名改为 HMAC(md5|ts|sid)，服务器兼容旧版无 sid 签名，旧插件不受影响

========================================
版本: 1.1.1 (2026-08-08)

更新内容:
- 新增授权验证开关：config.yml 中 auth-verify 设为 false 可跳过联网授权验证直接启动（仅调试用，默认 true）
- 修复 ProGuard 混淆产物缺 StackMapTable 导致服务器加载报 VerifyError：恢复 preverify，
  通过 includeDependency + rt.jar 补齐 libraryjars，产物已通过 JVM 严格校验

========================================
版本: 1.1.0 (2026-08-08)

更新内容:
- 版本号统一升级至 1.1.0（pom.xml 与 plugin.yml 同步）
- 授权日志文案调整：启动日志"本插件 MD5: xxx"改为"本插件授权码: xxx"，并移除"服务器返回"明文信息
- 授权后台升级：
  - 授权 MD5 全面改称为"授权码"
  - 新增访问日志列表（时间 + IP + 事件：请求访问 / 密码错误禁止访问 / 登录成功 / 白名单拦截）
  - 新增风险操作列表（新增/删除授权记录，删除后可在后台一键恢复）
  - 新增访问控制：通用模式（允许所有 IP）/ 白名单模式（仅允许特定 IP）
- 打包接入 ProGuard 混淆，产物 JAR 内业务类/授权逻辑不可直接阅读
- 修复后台"POST 密码错误时多记一条访问日志"的冗余问题

========================================
版本: 1.0.3 (2026-08-07)

更新内容:
- 修复加入物品右键 bug：1.8 中右键空气对手持下界之星/粘液球不触发 PlayerInteractEvent，
  现通过 ProtocolLib 监听 BLOCK_PLACE(face=255) 补上，对着空气右键也可正常执行指令
- clearstats/clearrecord 二次确认改为确认码机制：首次输入生成 6 位确认码（60 秒内有效），
  必须再次输入并携带确认码才会真正执行清除，杜绝一条命令直接清除
- 任务系统与快捷存储数据库开关拆分：任务进度同步用 task-database.enabled，
  快捷存储玩家开关跨服同步用独立的 quickstash-database.enabled
- 修复快捷存储跨服不同步：本地 players.yml 不再预载进内存缓存（否则永远读不到数据库），
  并在玩家进服时清除缓存强制从数据库读取最新开关状态
- 修复每日任务跨服领取时间读不到：接取任务时除索引外额外持久化任务名，
  跨服后按任务名匹配当日任务，本服没随机到该任务时从任务池兜底并补入当日列表
  （此前仅存索引、按对象引用匹配，各服随机任务列表不同导致永远匹配不上）

========================================
版本: 1.0.2 (2026-08-07)

更新内容:
- 新增清除玩家统计数据指令（需二次确认，支持本地/数据库存储）
- 新增清除地图最快通关记录指令（需二次确认）
- 修复游戏结束返回大厅后没有加入物品的问题（现在会补发快捷物品）
- 修复加入物品右键报错（getCommandMap 改为反射兼容 1.8.8）
- 加入物品（下界之星/粘液球）禁止丢弃，防止误丢
- 加入物品在本地没有 hub 命令时，通过 BungeeCord 插件消息跳回 bungeecord.hubserver 指定的大厅
  （无需开启 bungeecord.enabled，计分板/加入物品仍在仅一端多图模式下生效）

新增指令:
- /bwpro clearstats <玩家> [confirm]     — 清除玩家起床战争统计数据（击杀/死亡/胜场/败场/摧毁床/积分）
- /bwpro clearrecord <地图> [confirm]    — 清除地图最快通关记录（先输入不带 confirm 的查看警告，再带 confirm 确认执行）
  权限: bwpro.task.admin / OP

========================================
版本: 1.0.1 (2026-08-07)

更新内容:
- 所有语言文件统一整理进 locale/ 文件夹（bwia、bwsba 分模块存放）
- 新增世界计分板功能（默认关闭；白名单/黑名单模式；仅在一端多图模式生效）
  配置文件: plugins/BedwarsPRO/Scoreboard/config.yml
- 新增加入物品功能（默认关闭；可配置多个物品，物品栏第一格下界之星=任务系统，最后一格粘液球=回大厅）
  配置文件: plugins/BedwarsPRO/Scoreboard/join-item.yml
- 新增分步重载指令与 Tab 自动补齐

新增指令:
- /bwpro scoreboard reload  — 重载世界计分板配置（权限: bwpro.task.reload / bwpro.task.admin）
- /bwpro joinitem reload    — 重载加入物品配置（权限: bwpro.joinitem.admin / OP）
- /bwpro reload             — 全量重载所有配置（权限: bwpro.task.reload / bwpro.task.admin）
