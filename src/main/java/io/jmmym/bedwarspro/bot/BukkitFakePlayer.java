package io.jmmym.bedwarspro.bot;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.mojang.authlib.GameProfile;
import io.jmmym.bedwarspro.BedwarsPRO;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class BukkitFakePlayer {

  /**
   * bot → EmbeddedChannel 缓存。Corpse v3.0.0 内嵌 packetevents 的
   * ProtocolManager.USERS 以 pipeline 为 key，移除注册时需要 channel 才能拿到
   * pipeline，这里保存一份便于 removeFakePlayer 清理。
   */
  private static final java.util.concurrent.ConcurrentHashMap<UUID, Object> CORPSE_CHANNELS =
      new java.util.concurrent.ConcurrentHashMap<>();

  /**
   * Bot 调试日志（info 级），受 /bwpro debug on|off 控制，默认关闭。
   * 管理员排查假人问题时开启，正常运行时控制台保持干净。
   */
  private static void botInfo(String msg) {
    if (BedwarsPRO.getInstance().isBotDebug()) {
      BedwarsPRO.getInstance().getLogger().info(msg);
    }
  }

  /** Bot 调试日志（warning 级），同上受 debug 开关控制。 */
  public static void botWarn(String msg) {
    if (BedwarsPRO.getInstance().isBotDebug()) {
      BedwarsPRO.getInstance().getLogger().warning(msg);
    }
  }

  private BukkitFakePlayer() {
  }

  public static void createFakePlayer(World world, String name, Location spawnLoc,
      FakePlayerCallback callback) {
    new BukkitRunnable() {
      @Override
      public void run() {
        try {
          Object craftPlayer = create(world, name, spawnLoc);
          if (craftPlayer != null) {
            callback.onComplete((Player) craftPlayer);
          } else {
            botWarn("[Bot] create() 返回null: " + name);
            callback.onComplete(null);
          }
        } catch (Exception e) {
          BedwarsPRO.getInstance().getLogger().severe("[Bot] 创建假人失败: " + name);
          e.printStackTrace();
          callback.onComplete(null);
        }
      }
    }.runTask(BedwarsPRO.getInstance());
  }

  private static Object create(World world, String name, Location spawnLoc) throws Exception {
    String version = BedwarsPRO.getInstance().getCurrentVersion();
    String nms = "net.minecraft.server." + version + ".";
    String cb = "org.bukkit.craftbukkit." + version + ".";

    botInfo("[Bot] NMS版本: " + version);

    // 1. MinecraftServer
    Object craftServer = Class.forName(cb + "CraftServer").cast(Bukkit.getServer());
    Object minecraftServer;
    try {
      Field consoleField = craftServer.getClass().getField("console");
      consoleField.setAccessible(true);
      minecraftServer = consoleField.get(craftServer);
    } catch (NoSuchFieldException e) {
      minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);
    }

    // 2. WorldServer
    Object craftWorld = Class.forName(cb + "CraftWorld").cast(world);
    Object worldServer = craftWorld.getClass().getMethod("getHandle").invoke(craftWorld);

    // 3. GameProfile
    GameProfile gameProfile = new GameProfile(UUID.randomUUID(), name);

    // 4. EntityPlayer构造函数
    Class<?> entityPlayerClass = Class.forName(nms + "EntityPlayer");
    Constructor<?> entityPlayerConstructor = null;
    for (Constructor<?> c : entityPlayerClass.getDeclaredConstructors()) {
      if (c.getParameterCount() == 4) {
        entityPlayerConstructor = c;
        break;
      }
    }
    if (entityPlayerConstructor == null) {
      botWarn("[Bot] 未找到EntityPlayer 4参数构造函数");
      return null;
    }

    // 5. PlayerInteractManager
    Class<?> pimClass = Class.forName(nms + "PlayerInteractManager");
    Object interactManager = null;
    for (Constructor<?> c : pimClass.getDeclaredConstructors()) {
      if (c.getParameterCount() == 1) {
        try {
          c.setAccessible(true);
          interactManager = c.newInstance(worldServer);
          break;
        } catch (Exception ignored) {
        }
      }
    }
    if (interactManager == null) {
      botWarn("[Bot] 无法创建PlayerInteractManager");
      return null;
    }

    // 6. 创建EntityPlayer
    entityPlayerConstructor.setAccessible(true);
    Object entityPlayer = entityPlayerConstructor.newInstance(minecraftServer, worldServer,
        gameProfile, interactManager);

    // 7. NetworkManager + EmbeddedChannel
    Class<?> networkManagerClass = Class.forName(nms + "NetworkManager");
    Class<?> enumProtocolDirectionClass = Class.forName(nms + "EnumProtocolDirection");

    Object direction = null;
    for (Object enumConst : enumProtocolDirectionClass.getEnumConstants()) {
      if (enumConst.toString().equals("SERVERBOUND")) {
        direction = enumConst;
        break;
      }
    }

    Constructor<?> nmCtor = null;
    for (Constructor<?> ctor : networkManagerClass.getDeclaredConstructors()) {
      if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0].isEnum()) {
        nmCtor = ctor;
        break;
      }
    }
    if (nmCtor == null) {
      botWarn("[Bot] 未找到NetworkManager构造函数");
      return null;
    }
    nmCtor.setAccessible(true);
    Object networkManager = nmCtor.newInstance(direction);

    // EmbeddedChannel
    Class<?> embeddedChannelClass = Class.forName("io.netty.channel.embedded.EmbeddedChannel");
    Object channel = embeddedChannelClass.getConstructor().newInstance();

    // 给 EmbeddedChannel 添加 encoder/decoder 占位 handler：
    // ProtocolLib 在玩家加入（PlayerJoinEvent）时会对 channel 执行
    // addAfter("encoder", ...) 注入，空 pipeline 没有 encoder 会抛
    // NoSuchElementException: encoder（ProtocolLib 5.1.0 无兜底）。
    // 注意：io.netty.channel.ChannelHandlerAdapter 是抽象类不能 newInstance
    // （InstantiationException），必须用可实例化的具体类作占位。
    try {
      Class<?> channelHandlerClass = Class.forName("io.netty.channel.ChannelHandler");
      Object dummy = Class.forName("io.netty.channel.ChannelDuplexHandler").getConstructor().newInstance();
      Object pipeline = embeddedChannelClass.getMethod("pipeline").invoke(channel);
      Method addLast = pipeline.getClass().getMethod("addLast", String.class, channelHandlerClass);
      addLast.invoke(pipeline, "encoder", dummy);
      addLast.invoke(pipeline, "decoder", dummy);
    } catch (Exception ex) {
      botWarn("[Bot] 添加占位handler失败: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }

    // 7.4 注册到 Corpse v3.0.0 内嵌 packetevents：
    // CorpsePool.corpseTick 每 2 tick 遍历 Bukkit.getOnlinePlayers()（含 bot 假人），
    // 对每个玩家调 Corpse.show(player) → ProtocolManager.getChannel(player.getUniqueId())。
    // bot 从未注册过 channel → 返回 null → ChannelOperatorModernImpl.pooledByteBuf(null)
    // → channel.alloc() NPE（即 "Cannot invoke Channel.alloc() because o is null" 崩溃）。
    // 另外 PacketWrapper.prepareForSend 还会 getUser(channel) 并调用 user.getClientVersion()，
    // 所以 CHANNELS（uuid→channel）和 USERS（pipeline→User）都要注册；
    // 还要给 pipeline 补 packetevents 的 ENCODER_NAME 占位 handler：
    // CorpseNPC.spawn → sendPacket → writeAndFlushInContext(channel, ENCODER_NAME, buf)
    // → pipeline.context(ENCODER_NAME)，找不到返回 null → ctx.writeAndFlush NPE。
    // 注册后 Corpse 就能在 bot 的假 channel 上安全写包（EmbeddedChannel 只进 outbound
    // 缓冲，不会发给任何真实客户端，随 bot 移除一起被回收）。
    registerCorpseChannel(gameProfile.getId(), name, channel);

    // 设置channel到NetworkManager
    for (Field f : networkManagerClass.getDeclaredFields()) {
      if (f.getType().getName().contains("Channel")) {
        f.setAccessible(true);
        f.set(networkManager, channel);
        break;
      }
    }

    // 设置协议为PLAY
    Class<?> enumProtocolClass = Class.forName(nms + "EnumProtocol");
    Object playProtocol = null;
    for (Object enumConst : enumProtocolClass.getEnumConstants()) {
      if (enumConst.toString().equals("PLAY")) {
        playProtocol = enumConst;
        break;
      }
    }
    if (playProtocol != null) {
      for (Field f : networkManagerClass.getDeclaredFields()) {
        if (f.getType() == enumProtocolClass) {
          f.setAccessible(true);
          f.set(networkManager, playProtocol);
          botInfo("[Bot] 协议已设置为PLAY");
          break;
        }
      }
    }

    // 8. PlayerConnection
    Class<?> playerConnectionClass = Class.forName(nms + "PlayerConnection");
    Constructor<?> pcCtor = playerConnectionClass.getDeclaredConstructor(
        Class.forName(nms + "MinecraftServer"),
        networkManagerClass,
        entityPlayerClass);
    pcCtor.setAccessible(true);
    Object playerConnection = pcCtor.newInstance(minecraftServer, networkManager, entityPlayer);
    entityPlayerClass.getField("playerConnection").set(entityPlayer, playerConnection);

    botInfo("[Bot] NMS对象创建完成: " + name);

    // 9. 设置位置
    Location loc = (spawnLoc != null) ? spawnLoc : world.getSpawnLocation();
    try {
      Method setLocation = entityPlayerClass.getMethod("setLocation",
          double.class, double.class, double.class, float.class, float.class);
      setLocation.invoke(entityPlayer, loc.getX(), loc.getY(), loc.getZ(), 0f, 0f);
    } catch (NoSuchMethodException e) {
      try {
        Method setLocation = entityPlayerClass.getMethod("a",
            double.class, double.class, double.class, float.class, float.class);
        setLocation.invoke(entityPlayer, loc.getX(), loc.getY(), loc.getZ(), 0f, 0f);
      } catch (Exception ignored) {
      }
    }

    // 10. 手动加入服务器（不走 PlayerList.a / placeNewPlayer）：
    //     真实加入会触发 PlayerJoinEvent，ProtocolLib/GrimAC(PacketEvents)/ViaVersion
    //     会对 bot 的假 channel 注入失败（NoSuchElementException: encoder），
    //     甚至踢掉真实玩家（PacketEvents failed to inject into a channel）。
    //     手动加入不触发任何事件，bot 仍可被床战系统当作正常玩家操作。
    boolean placed = false;
    try {
      Class<?> craftServerClazz = Class.forName(cb + "CraftServer");
      Object craftServerObj = craftServerClazz.cast(Bukkit.getServer());
      Method getHandleMethod = craftServerClazz.getMethod("getHandle");
      Object playerList = getHandleMethod.invoke(craftServerObj);

      // playerList.players.add(entityPlayer)
      Field playersField = null;
      Class<?> searchClass = playerList.getClass();
      while (searchClass != null && playersField == null) {
        try {
          playersField = searchClass.getDeclaredField("players");
        } catch (NoSuchFieldException e) {
          searchClass = searchClass.getSuperclass();
        }
      }
      if (playersField != null) {
        playersField.setAccessible(true);
        Object listObj = playersField.get(playerList);
        if (listObj instanceof java.util.List) {
          ((java.util.List) listObj).add(entityPlayer);
          placed = true;
          botInfo("[Bot] 已加入服务器玩家列表: " + name);
        }
      } else {
        botWarn("[Bot] 未找到玩家列表字段 players");
      }
    } catch (Exception e) {
      botWarn("[Bot] 手动加入玩家列表异常: " + e.getMessage());
    }

    // 11. 获取Bukkit包装
    Object craftPlayer = entityPlayerClass.getMethod("getBukkitEntity").invoke(entityPlayer);

    // 12. 手动加入不会走 placeNewPlayer 的 addEntity，必须手动把实体加入世界，
    //     这样其他玩家才能在游戏里看到 bot 实体
    for (Method m : worldServer.getClass().getMethods()) {
      if (m.getName().equals("addEntity") && m.getParameterCount() >= 1 && m.getParameterCount() <= 2) {
        try {
          if (m.getParameterCount() == 2) {
            m.invoke(worldServer, entityPlayer,
                org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
          } else {
            m.invoke(worldServer, entityPlayer);
          }
          botInfo("[Bot] addEntity成功");
          break;
        } catch (Exception e) {
          botWarn("[Bot] addEntity失败: " + e.getMessage());
        }
      }
    }

    // 13. 始终发送可见性包给真实玩家
    sendVisibilityPackets(entityPlayer, entityPlayerClass, nms, playerConnection, playerConnectionClass, craftPlayer);

    // 13. KeepAlive保活
    startKeepAlive(entityPlayer, entityPlayerClass, playerConnection, playerConnectionClass);

    botInfo("[Bot] 假人创建成功: " + name);
    return craftPlayer;
  }

  private static void sendVisibilityPackets(Object entityPlayer, Class<?> entityPlayerClass,
      String nms, Object playerConnection, Class<?> pcClass, Object craftPlayer) {
    try {
      String version = BedwarsPRO.getInstance().getCurrentVersion();
      String cb = "org.bukkit.craftbukkit." + version + ".";

      Method sendPacketMethod = null;
      for (Method m : pcClass.getDeclaredMethods()) {
        if (m.getName().equals("sendPacket") && m.getParameterCount() == 1) {
          sendPacketMethod = m;
          break;
        }
      }
      if (sendPacketMethod == null) {
        return;
      }
      sendPacketMethod.setAccessible(true);
      final Method sendPacket = sendPacketMethod;

      // PlayerInfo ADD_PLAYER
      Class<?> packetInfoClass = Class.forName(nms + "PacketPlayOutPlayerInfo");
      Class<?> enumActionClass = Class.forName(nms + "PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
      Object addAction = null;
      for (Object enumConst : enumActionClass.getEnumConstants()) {
        if (enumConst.toString().equals("ADD_PLAYER")) {
          addAction = enumConst;
          break;
        }
      }

      Object playerInfoPacket = null;
      if (addAction != null) {
        for (Constructor<?> ctor : packetInfoClass.getDeclaredConstructors()) {
          Class<?>[] params = ctor.getParameterTypes();
          if (params.length == 2 && params[0].isEnum()) {
            try {
              ctor.setAccessible(true);
              playerInfoPacket = ctor.newInstance(addAction, java.util.Collections.singletonList(entityPlayer));
              break;
            } catch (Exception ignored) {
            }
          }
        }
      }

      // NamedEntitySpawn
      Class<?> packetSpawnClass = Class.forName(nms + "PacketPlayOutNamedEntitySpawn");
      Class<?> entityHumanClass = Class.forName(nms + "EntityHuman");
      Object spawnPacket = null;
      for (Constructor<?> ctor : packetSpawnClass.getDeclaredConstructors()) {
        Class<?>[] params = ctor.getParameterTypes();
        if (params.length == 1 && entityHumanClass.isAssignableFrom(params[0])) {
          try {
            ctor.setAccessible(true);
            spawnPacket = ctor.newInstance(entityPlayer);
            break;
          } catch (Exception ignored) {
          }
        }
      }

      // EntityMetadata
      Class<?> packetMetaClass = Class.forName(nms + "PacketPlayOutEntityMetadata");
      Class<?> entityClass = Class.forName(nms + "Entity");
      Object metaPacket = null;
      for (Constructor<?> ctor : packetMetaClass.getDeclaredConstructors()) {
        Class<?>[] params = ctor.getParameterTypes();
        if (params.length == 3 && params[0] == int.class
            && entityClass.isAssignableFrom(params[1]) && params[2] == boolean.class) {
          try {
            ctor.setAccessible(true);
            metaPacket = ctor.newInstance(
                entityPlayerClass.getMethod("getId").invoke(entityPlayer),
                entityPlayer, true);
            break;
          } catch (Exception ignored) {
          }
        }
      }

      // EntityHeadRotation
      Class<?> packetHeadClass = Class.forName(nms + "PacketPlayOutEntityHeadRotation");
      Object headPacket = null;
      for (Constructor<?> ctor : packetHeadClass.getDeclaredConstructors()) {
        Class<?>[] params = ctor.getParameterTypes();
        if (params.length == 2 && entityHumanClass.isAssignableFrom(params[0])) {
          try {
            ctor.setAccessible(true);
            headPacket = ctor.newInstance(entityPlayer,
                entityPlayerClass.getMethod("getYaw").invoke(entityPlayer));
            break;
          } catch (Exception ignored) {
          }
        }
      }

      Class<?> craftPlayerClass = Class.forName(cb + "entity.CraftPlayer");

      if (playerInfoPacket != null) {
        for (Player p : Bukkit.getOnlinePlayers()) {
          if (p.equals(craftPlayer)) continue;
          try {
            Object handle = craftPlayerClass.getMethod("getHandle").invoke(p);
            Object pc = handle.getClass().getField("playerConnection").get(handle);
            sendPacket.invoke(pc, playerInfoPacket);
          } catch (Exception ignored) {
          }
        }
        botInfo("[Bot] PlayerInfo已发送");
      }

      final Object fSpawn = spawnPacket;
      final Object fMeta = metaPacket;
      final Object fHead = headPacket;
      new BukkitRunnable() {
        @Override
        public void run() {
          for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(craftPlayer)) continue;
            try {
              Object handle = craftPlayerClass.getMethod("getHandle").invoke(p);
              Object pc = handle.getClass().getField("playerConnection").get(handle);
              if (fSpawn != null) sendPacket.invoke(pc, fSpawn);
              if (fMeta != null) sendPacket.invoke(pc, fMeta);
              if (fHead != null) sendPacket.invoke(pc, fHead);
            } catch (Exception ignored) {
            }
          }
          botInfo("[Bot] Spawn+Metadata+Head已发送");
        }
      }.runTaskLater(BedwarsPRO.getInstance(), 1L);

    } catch (Exception e) {
      botWarn("[Bot] 发包异常: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * 向指定玩家补发某个 bot 的 PlayerInfo(ADD_PLAYER) + NamedEntitySpawn + Metadata + Head。
   * 用于：后加入游戏的真人玩家，确保能看到已在游戏中的 bot。
   * bot 不参与床战 hidePlayer/showPlayer 机制（hide/show 会重发 PlayerInfo 包，
   * 时序错乱会导致 Tab 重复显示 bot 名字），bot 的可见性统一由本类在
   * 创建（createFakePlayer）/ 加入（本方法）/ 复活（respawnFakePlayer）时管理。
   */
  public static void sendVisibilityToPlayer(Player bot, Player viewer) {
    if (bot == null || viewer == null || bot.equals(viewer)) {
      return;
    }
    try {
      String version = BedwarsPRO.getInstance().getCurrentVersion();
      String nms = "net.minecraft.server." + version + ".";
      String cb = "org.bukkit.craftbukkit." + version + ".";

      Class<?> craftPlayerClass = Class.forName(cb + "entity.CraftPlayer");
      Object craftPlayer = craftPlayerClass.cast(bot);
      Object entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);

      // PlayerInfo ADD_PLAYER
      Class<?> packetInfoClass = Class.forName(nms + "PacketPlayOutPlayerInfo");
      Class<?> enumActionClass = Class.forName(nms + "PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
      Object addAction = null;
      for (Object enumConst : enumActionClass.getEnumConstants()) {
        if (enumConst.toString().equals("ADD_PLAYER")) {
          addAction = enumConst;
          break;
        }
      }
      Object addPacket = null;
      if (addAction != null) {
        for (Constructor<?> ctor : packetInfoClass.getDeclaredConstructors()) {
          if (ctor.getParameterCount() == 2 && ctor.getParameterTypes()[0].isEnum()) {
            try {
              ctor.setAccessible(true);
              addPacket = ctor.newInstance(addAction,
                  java.util.Collections.singletonList(entityPlayer));
              break;
            } catch (Exception ignored) {
            }
          }
        }
      }

      // NamedEntitySpawn
      Class<?> packetSpawnClass = Class.forName(nms + "PacketPlayOutNamedEntitySpawn");
      Class<?> entityHumanClass = Class.forName(nms + "EntityHuman");
      Object spawnPacket = null;
      for (Constructor<?> ctor : packetSpawnClass.getDeclaredConstructors()) {
        if (ctor.getParameterCount() == 1
            && entityHumanClass.isAssignableFrom(ctor.getParameterTypes()[0])) {
          try {
            ctor.setAccessible(true);
            spawnPacket = ctor.newInstance(entityPlayer);
            break;
          } catch (Exception ignored) {
          }
        }
      }

      // EntityMetadata
      Class<?> packetMetaClass = Class.forName(nms + "PacketPlayOutEntityMetadata");
      Class<?> entityClass = Class.forName(nms + "Entity");
      Object metaPacket = null;
      for (Constructor<?> ctor : packetMetaClass.getDeclaredConstructors()) {
        Class<?>[] params = ctor.getParameterTypes();
        if (params.length == 3 && params[0] == int.class
            && entityClass.isAssignableFrom(params[1]) && params[2] == boolean.class) {
          try {
            ctor.setAccessible(true);
            metaPacket = ctor.newInstance(
                entityPlayer.getClass().getMethod("getId").invoke(entityPlayer),
                entityPlayer, true);
            break;
          } catch (Exception ignored) {
          }
        }
      }

      // EntityHeadRotation
      Class<?> packetHeadClass = Class.forName(nms + "PacketPlayOutEntityHeadRotation");
      Object headPacket = null;
      for (Constructor<?> ctor : packetHeadClass.getDeclaredConstructors()) {
        if (ctor.getParameterCount() == 2
            && entityHumanClass.isAssignableFrom(ctor.getParameterTypes()[0])) {
          try {
            ctor.setAccessible(true);
            headPacket = ctor.newInstance(entityPlayer,
                entityPlayer.getClass().getMethod("getYaw").invoke(entityPlayer));
            break;
          } catch (Exception ignored) {
          }
        }
      }

      Method sendPacket = null;
      for (Method m : Class.forName(nms + "PlayerConnection").getMethods()) {
        if (m.getName().equals("sendPacket") && m.getParameterCount() == 1) {
          sendPacket = m;
          break;
        }
      }
      if (sendPacket == null) {
        return;
      }

      Object handle = craftPlayerClass.getMethod("getHandle").invoke(viewer);
      Object pc = handle.getClass().getField("playerConnection").get(handle);
      if (addPacket != null) {
        sendPacket.invoke(pc, addPacket);
      }
      if (spawnPacket != null) {
        sendPacket.invoke(pc, spawnPacket);
      }
      if (metaPacket != null) {
        sendPacket.invoke(pc, metaPacket);
      }
      if (headPacket != null) {
        sendPacket.invoke(pc, headPacket);
      }
    } catch (Exception ignored) {
    }
  }

  private static void startKeepAlive(Object entityPlayer, Class<?> entityPlayerClass,
      Object playerConnection, Class<?> pcClass) {
    new BukkitRunnable() {
      @Override
      public void run() {
        try {
          // 重置PlayerConnection所有boolean字段防止超时踢出
          for (Field f : pcClass.getDeclaredFields()) {
            if (f.getType() == boolean.class) {
              f.setAccessible(true);
              f.set(playerConnection, false);
            }
          }
        } catch (Exception ignored) {
        }
      }
    }.runTaskTimer(BedwarsPRO.getInstance(), 100L, 100L);
  }

  public static void removeFakePlayer(Player player) {
    if (player == null) {
      return;
    }
    // 注意：不检查 player.isOnline()——bot 是手动加入的假玩家，
    // isOnline() 语义不可靠（死亡/已从列表移除等状态下可能返回 false），
    // 若提前 return 会导致玩家列表残留、Tab 里永久显示 bot 名字。
    // 移除 Corpse v3.0.0 内嵌 packetevents 的 CHANNELS/USERS 注册，防止静态 map 泄漏
    unregisterCorpseChannel(player.getUniqueId());
    // 移除 Corpse 为 bot 创建的同名尸体（游戏结束/床拆直接移除 bot 时兜底清理）
    scheduleRemoveCorpseForBot(player.getName());
    try {
      String version = BedwarsPRO.getInstance().getCurrentVersion();
      final Class<?> craftPlayerClass = Class.forName(
          "org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
      Object craftPlayer = craftPlayerClass.cast(player);
      Object entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);
      Object worldServer = player.getWorld().getClass().getMethod("getHandle").invoke(player.getWorld());

      // 1. 从服务器玩家列表移除（手动加入的假人不会自动断开）
      Class<?> craftServerClazz = Class.forName(
          "org.bukkit.craftbukkit." + version + ".CraftServer");
      Object craftServerObj = craftServerClazz.cast(Bukkit.getServer());
      Object playerList = craftServerClazz.getMethod("getHandle").invoke(craftServerObj);
      Field playersField = null;
      Class<?> searchClass = playerList.getClass();
      while (searchClass != null && playersField == null) {
        try {
          playersField = searchClass.getDeclaredField("players");
        } catch (NoSuchFieldException e) {
          searchClass = searchClass.getSuperclass();
        }
      }
      if (playersField != null) {
        playersField.setAccessible(true);
        Object listObj = playersField.get(playerList);
        if (listObj instanceof java.util.List) {
          ((java.util.List) listObj).remove(entityPlayer);
        }
      }

      // 2. 从世界移除实体
      boolean removed = false;
      for (Method m : worldServer.getClass().getMethods()) {
        if (m.getName().equals("removeEntity") && m.getParameterCount() == 1) {
          try {
            m.invoke(worldServer, entityPlayer);
            removed = true;
            break;
          } catch (Exception ignored) {
          }
        }
      }
      if (!removed) {
        // 兜底：标记实体死亡
        try {
          entityPlayer.getClass().getMethod("die").invoke(entityPlayer);
        } catch (Exception ignored) {
        }
      }

      // 3. 发送 REMOVE_PLAYER 包，清理其他玩家 Tab 里残留的 bot 条目
      try {
        Class<?> packetInfoClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutPlayerInfo");
        Class<?> enumActionClass = Class.forName(
            "net.minecraft.server." + version + ".PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
        Object removeAction = null;
        for (Object enumConst : enumActionClass.getEnumConstants()) {
          if (enumConst.toString().equals("REMOVE_PLAYER")) {
            removeAction = enumConst;
            break;
          }
        }
        if (removeAction != null) {
          Object removePacket = null;
          for (Constructor<?> ctor : packetInfoClass.getDeclaredConstructors()) {
            if (ctor.getParameterCount() == 2 && ctor.getParameterTypes()[0].isEnum()) {
              try {
                ctor.setAccessible(true);
                removePacket = ctor.newInstance(removeAction, java.util.Collections.singletonList(entityPlayer));
                break;
              } catch (Exception ignored) {
              }
            }
          }
          if (removePacket != null) {
            Method sendMethod = null;
            for (Method m : Class.forName("net.minecraft.server." + version + ".PlayerConnection").getMethods()) {
              if (m.getName().equals("sendPacket") && m.getParameterCount() == 1) {
                sendMethod = m;
                break;
              }
            }
            if (sendMethod != null) {
              Class<?> craftPlayerClazz = Class.forName(
                  "org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
              for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(player)) {
                  continue;
                }
                try {
                  Object h = craftPlayerClazz.getMethod("getHandle").invoke(online);
                  Object pc = h.getClass().getField("playerConnection").get(h);
                  sendMethod.invoke(pc, removePacket);
                } catch (Exception ignored) {
                }
              }
            }
          }
        }
      } catch (Exception ignored) {
      }
    } catch (Exception e) {
      botWarn("[Bot] 移除假人失败: " + player.getName());
    }
  }

  /**
   * 把 bot 的 EmbeddedChannel 注册进 Corpse v3.0.0 内嵌 packetevents：
   * - ProtocolManager.CHANNELS（uuid → channel）
   * - ProtocolManager.USERS（channel.pipeline() → User）
   * - pipeline 上补 packetevents 的 ENCODER_NAME 占位 handler
   * Corpse 插件未安装时 Class.forName 失败会静默跳过，不影响 bot 本体功能。
   */
  private static void registerCorpseChannel(UUID uuid, String name, Object channel) {
    try {
      String pkg = "com.github.unldenis.corpse.libs.packetevents.packetevents.";
      Class<?> pmClass = Class.forName(pkg + "manager.protocol.ProtocolManager");

      // CHANNELS: uuid -> channel
      Object channels = pmClass.getField("CHANNELS").get(null);
      if (channels instanceof java.util.Map) {
        ((java.util.Map<UUID, Object>) channels).put(uuid, channel);
      }

      // ENCODER_NAME 占位 handler：防 CorpseNPC 写包时
      // writeAndFlushInContext 的 pipeline.context(ENCODER_NAME) 返回 null
      String encoderName = (String) Class.forName(pkg + "PacketEvents")
          .getField("ENCODER_NAME").get(null);
      if (encoderName != null && !encoderName.isEmpty()) {
        Object pipeline = channel.getClass().getMethod("pipeline").invoke(channel);
        java.util.List<?> names = (java.util.List<?>) pipeline.getClass()
            .getMethod("names").invoke(pipeline);
        if (!names.contains(encoderName)) {
          // ChannelHandlerAdapter 是抽象类不能实例化，用 ChannelDuplexHandler
          // （具体类、public 无参构造）作占位 handler，避免 InstantiationException
          Object dummy = Class.forName("io.netty.channel.ChannelDuplexHandler")
              .getConstructor().newInstance();
          Method addLast = pipeline.getClass().getMethod("addLast",
              String.class, Class.forName("io.netty.channel.ChannelHandler"));
          addLast.invoke(pipeline, encoderName, dummy);
        }
      }

      // USERS: pipeline -> User（prepareForSend 会调 user.getClientVersion()）
      Object pipeline = channel.getClass().getMethod("pipeline").invoke(channel);
      Class<?> connectionStateClass = Class.forName(pkg + "protocol.ConnectionState");
      Object playState = null;
      for (Object c : connectionStateClass.getEnumConstants()) {
        if (c.toString().equals("PLAY")) {
          playState = c;
          break;
        }
      }
      Class<?> clientVersionClass = Class.forName(pkg + "protocol.player.ClientVersion");
      Object clientVersion = null;
      for (Object v : clientVersionClass.getEnumConstants()) {
        if (v.toString().equals("V_1_12_2")) {
          clientVersion = v;
          break;
        }
      }
      Object profile = Class.forName(pkg + "protocol.player.UserProfile")
          .getConstructor(UUID.class, String.class).newInstance(uuid, name);
      Object user = Class.forName(pkg + "protocol.player.User")
          .getConstructor(Object.class, connectionStateClass, clientVersionClass,
              Class.forName(pkg + "protocol.player.UserProfile"))
          .newInstance(channel, playState, clientVersion, profile);
      Object users = pmClass.getField("USERS").get(null);
      if (users instanceof java.util.Map) {
        ((java.util.Map<Object, Object>) users).put(pipeline, user);
      }

      CORPSE_CHANNELS.put(uuid, channel);
      botInfo("[Bot] 已注册Corpse通道: " + name);
    } catch (Throwable t) {
      botWarn("[Bot] 注册Corpse通道失败(不影响bot本体): " + t.getClass().getSimpleName() + ": " + t.getMessage());
    }
  }

  /** 从 Corpse 内嵌 packetevents 移除 bot 的 CHANNELS/USERS 注册，防止静态 map 泄漏 */
  private static void unregisterCorpseChannel(UUID uuid) {
    try {
      Object channel = CORPSE_CHANNELS.remove(uuid);
      String pkg = "com.github.unldenis.corpse.libs.packetevents.packetevents.";
      Class<?> pmClass = Class.forName(pkg + "manager.protocol.ProtocolManager");
      Object channels = pmClass.getField("CHANNELS").get(null);
      if (channels instanceof java.util.Map) {
        ((java.util.Map<UUID, Object>) channels).remove(uuid);
      }
      if (channel != null) {
        Object pipeline = channel.getClass().getMethod("pipeline").invoke(channel);
        Object users = pmClass.getField("USERS").get(null);
        if (users instanceof java.util.Map) {
          ((java.util.Map<Object, Object>) users).remove(pipeline);
        }
      }
    } catch (Throwable ignored) {
    }
  }

  /**
   * 延迟 2 tick 移除 Corpse v3.0.0 为 bot 创建的同名尸体。
   * CorpsePool.handleDeath 会在 PlayerDeathEvent（含 bot 假人）时为死亡玩家创建
   * 尸体，尸体以 bot 名字向所有附近玩家发送 ADD_PLAYER → 每死一次 Tab 就多一个
   * bot 名字；且 bot 复活走的是反射流程、不触发 PlayerRespawnEvent，Corpse 不会
   * 自动清理尸体，Tab 名字永久残留（尸体被"清除"只是 showTags 同名替换，不彻底）。
   * 这里主动按名字移除：CorpsePool.remove(id) 内部会对所有看到尸体的人发 despawn
   * （REMOVE_PLAYER + Destroy），Tab 名字随之消失。
   */
  public static void scheduleRemoveCorpseForBot(String botName) {
    Bukkit.getScheduler().runTaskLater(BedwarsPRO.getInstance(), () -> {
      removeCorpseForBot(botName);
    }, 2L);
  }

  /**
   * 兼容加载 Corpse 插件的 CorpsePool 类（按 Corpse 版本自动匹配）：
   * - v3.0.0（内嵌 packetevents，测试专用）→ com.github.unldenis.corpse.pool.CorpsePool
   * - v1.0.10（依赖 ProtocolLib，TEST/一端多图）→ com.github.unldenis.corpse.manager.CorpsePool
   * 两个版本的 CorpsePool 均有 getInstance()/getCorpses()/remove(int) 同签名 API
   * （javap 已分别验证）。Corpse 未安装或版本未知时返回 null，调用方静默跳过。
   */
  private static Class<?> findCorpsePoolClass() {
    String[] candidates = {
        "com.github.unldenis.corpse.pool.CorpsePool",
        "com.github.unldenis.corpse.manager.CorpsePool"
    };
    for (String cn : candidates) {
      try {
        return Class.forName(cn);
      } catch (Throwable ignored) {
      }
    }
    return null;
  }

  public static void removeCorpseForBot(String botName) {
    try {
      Class<?> poolClass = findCorpsePoolClass();
      if (poolClass == null) {
        return;
      }
      Object pool = poolClass.getMethod("getInstance").invoke(null);
      // corpseMap 中可能残留多个同名尸体（showTags=false 时同名不替换），全部移除
      Object corpses = poolClass.getMethod("getCorpses").invoke(pool);
      if (corpses instanceof java.util.Collection) {
        for (Object corpse : new java.util.ArrayList<>((java.util.Collection<?>) corpses)) {
          String cname = (String) corpse.getClass().getMethod("getName").invoke(corpse);
          if (botName.equals(cname)) {
            int id = (Integer) corpse.getClass().getMethod("getId").invoke(corpse);
            // 记录尸体 profile 的 UUID：Corpse 每次死亡都用 UUID.randomUUID() 生成
            // 全新 UUID 的 UserProfile，Tab 里尸体条目（ADD_PLAYER 添加）按这个 UUID
            // 存放；remove 之后尸体对象虽仍可引用，但先取出来更保险
            java.util.UUID corpseUuid = getCorpseProfileUuid(corpse);
            // 先标记已显示再 remove：封死异步 corpseTick（每 2 tick）在 remove 前后
            // 对尸体 show 的竞态（否则尸体已出 corpseMap 却永久可见，用户反馈
            // "遗体不消失"）。remove 后再对所有人补一次 hide 兜底。
            markCorpseShownForAll(corpse);
            try {
              poolClass.getMethod("remove", int.class).invoke(pool, id);
            } catch (Throwable t) {
              // remove 内部会对 seeingPlayers 逐个 hide；即使 markCorpseShownForAll
              // 已清掉 bot，异步 corpseTick 仍可能恰在 remove 那一拍把 bot show 进
              // seeingPlayers，导致 hide(bot) 抛 NPE 中断 forEach。这里吞掉异常
              // 继续兜底，保证真人的实体（forceHideCorpse）与 Tab 条目照常清理。
              botWarn("[Bot] Corpse移除异常(已兜底隐藏): " + t);
            }
            forceHideCorpse(corpse);
            if (corpseUuid != null) {
              // Corpse 的 hide/despawn 只发 DestroyEntities（实体消失），Tab 里的
              // 尸体条目（show 时 ADD_PLAYER 添加）只能靠 REMOVE_PLAYER 移除，
              // 不补发就会永久残留（死几次 Tab 就多几个同名条目）。
              // 延迟 3 tick 补发：覆盖 corpseTick（异步、每 2 tick）在 remove 前后
              // 对尸体 show 的竞态窗口，保证清理包在最后一次 ADD_PLAYER 之后到达。
              final java.util.UUID fUuid = corpseUuid;
              final Object fCorpse = corpse;
              Bukkit.getScheduler().runTaskLater(BedwarsPRO.getInstance(),
                  () -> {
                    sendRemovePlayerInfo(fUuid, botName, true);
                    // 再次兜底 hide：覆盖 corpseTick 正好在 remove 后那一拍 show 的情况
                    forceHideCorpse(fCorpse);
                  }, 3L);
            }
            botInfo("[Bot] 已移除Corpse尸体: " + botName);
          }
        }
      }
    } catch (Throwable ignored) {
    }
  }

  /**
   * 对真实玩家重生的尸体 Tab 残留做同样清理：Corpse 的 despawn 只发 Destroy，
   * 玩家死亡时尸体 show 发过 ADD_PLAYER（随机 UUID），重生/尸体销毁时不会发
   * REMOVE_PLAYER，Tab 里会永久残留一条同名条目（真人玩家名重复的来源）。
   * 延迟 3 tick 执行以覆盖 corpseTick 的 show 竞态。
   */
  public static void scheduleCleanupPlayerCorpseTabs(Player player) {
    if (player == null) {
      return;
    }
    Bukkit.getScheduler().runTaskLater(BedwarsPRO.getInstance(), () -> {
      try {
        Class<?> poolClass = findCorpsePoolClass();
        if (poolClass == null) {
          return;
        }
        Object pool = poolClass.getMethod("getInstance").invoke(null);
        Object corpses = poolClass.getMethod("getCorpses").invoke(pool);
        if (corpses instanceof java.util.Collection) {
          for (Object corpse : new java.util.ArrayList<>((java.util.Collection<?>) corpses)) {
            try {
              Boolean shown = (Boolean) corpse.getClass()
                  .getMethod("isShownFor", Player.class).invoke(corpse, player);
              if (shown == null || !shown) {
                continue;
              }
              java.util.UUID uuid = getCorpseProfileUuid(corpse);
              if (uuid == null) {
                continue;
              }
              String cname = (String) corpse.getClass().getMethod("getName").invoke(corpse);
              sendRemovePlayerInfo(uuid, cname, false);
            } catch (Throwable ignored) {
            }
          }
        }
      } catch (Throwable ignored) {
      }
    }, 3L);
  }

  /**
   * 向所有在线真实玩家发送 ProtocolLib REMOVE_PLAYER 包（1.8-1.12 通用），把指定 UUID
   * 的 Tab 条目移除。用于清理 Corpse 尸体 show 时添加的、但 Corpse 自己永远不会
   * 用 REMOVE_PLAYER 移除的 Tab 残留条目（Corpse 的 hide/despawn 只发 Destroy）。
   *
   * <p>必须用 ProtocolLib 版本而非 NMS raw 反射版：Corpse v1.0.10 走 ProtocolLib
   * {@code ProtocolManager.sendServerPacket} 发 ADD_PLAYER，客户端 Tab 条目由
   * ProtocolLib 的 server 管线管理；若发 raw NMS 包（绕过 ProtocolLib），服务器
   * 端 Tab 缓存不会被移除 → Tab 条目永久残留，且每次 corpseTick 再次 ADD_PLAYER
   * 又会累积一个新条目，形成"死几次就多几个同名"。
   *
   * @param uuid 尸体 profile 的随机 UUID（ADD_PLAYER 时生成，REMOVE_PLAYER 必须用同一 UUID）
   * @param name 尸体名字（仅日志用，Tab 条目按 UUID 区分）
   * @param log  是否打印发送结果
   */
  public static void sendRemovePlayerInfo(java.util.UUID uuid, String name, boolean log) {
    if (uuid == null) {
      return;
    }
    try {
      com.comphenix.protocol.ProtocolManager pm = ProtocolLibrary.getProtocolManager();
      if (pm == null) {
        if (log) {
          botWarn("[Bot] Tab清理: ProtocolLib 未初始化，跳过 REMOVE_PLAYER");
        }
        return;
      }
      // ProtocolLib 5.1.0 的 getPlayerInfoActions().write() 不支持新包，必须用反射构建 NMS packet
      String version = BedwarsPRO.getInstance().getCurrentVersion();
      String nms = "net.minecraft.server." + version + ".";
      Class<?> packetClass = Class.forName(nms + "PacketPlayOutPlayerInfo");
      Class<?> actionClass = Class.forName(nms + "PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
      Object removeAction = null;
      for (Object c : actionClass.getEnumConstants()) {
        if (c.toString().equals("REMOVE_PLAYER")) {
          removeAction = c;
          break;
        }
      }
      if (removeAction == null) {
        if (log) {
          botWarn("[Bot] Tab清理: 未找到REMOVE_PLAYER枚举");
        }
        return;
      }
      // PacketPlayOutPlayerInfo 字段 a=action(枚举), b=data(List<PlayerInfoData>)
      Object packet = packetClass.getConstructor().newInstance();
      java.lang.reflect.Field actionField = null;
      java.lang.reflect.Field dataField = null;
      for (java.lang.reflect.Field f : packetClass.getDeclaredFields()) {
        if (f.getType() == actionClass) {
          actionField = f;
        } else if (f.getType().getName().contains("List") || f.getType().getName().contains("playerInfoData")) {
          dataField = f;
        }
      }
      if (actionField != null) {
        actionField.setAccessible(true);
        actionField.set(packet, removeAction);
      }
      if (dataField != null) {
        dataField.setAccessible(true);
        Class<?> dataClass = Class.forName(nms + "PacketPlayOutPlayerInfo$PlayerInfoData");
        Object profile = Class.forName("com.mojang.authlib.GameProfile").getConstructor(java.util.UUID.class, String.class).newInstance(uuid, name);
        List<Object> dataList = new java.util.ArrayList<>();
        for (java.lang.reflect.Constructor<?> ctor : dataClass.getDeclaredConstructors()) {
          Class<?>[] ps = ctor.getParameterTypes();
          if (ps.length != 4 && ps.length != 5) continue;
          boolean hasProfile = false, hasInt = false, hasGamemode = false, hasComponent = false;
          for (Class<?> pt : ps) {
            String pn = pt.getName();
            if (pn.equals("com.mojang.authlib.GameProfile")) hasProfile = true;
            else if (pt == int.class) hasInt = true;
            else if (pn.endsWith("EnumGamemode")) hasGamemode = true;
            else if (pn.endsWith("IChatBaseComponent")) hasComponent = true;
          }
          if (hasProfile && hasInt && hasGamemode && hasComponent) {
            Object notSet = null;
            Class<?> gamemodeClass = null;
            for (Class<?> pt : ps) { if (pt.getName().endsWith("EnumGamemode")) gamemodeClass = pt; }
            if (gamemodeClass != null) {
              for (Object c : gamemodeClass.getEnumConstants()) { if (c.toString().equals("NOT_SET")) notSet = c; }
            }
            if (notSet != null) {
              Object data = ctor.newInstance(packet, profile, 0, notSet,
                  Class.forName(nms + "ChatComponentText").getConstructor(String.class).newInstance(""));
              dataList.add(data);
              break;
            }
          }
        }
        dataField.set(packet, dataList);
      }
      int sent = 0;
      BotManager bm = BedwarsPRO.getInstance().getBotManager();
      // 用反射调用 sendServerPacket(Player, Object, boolean) 避免类型不匹配
      java.lang.reflect.Method sendMethod = null;
      for (java.lang.reflect.Method m : pm.getClass().getMethods()) {
        if (m.getName().equals("sendServerPacket") && m.getParameterCount() == 3
            && m.getParameterTypes()[1] == com.comphenix.protocol.events.PacketContainer.class) {
          sendMethod = m;
          break;
        }
      }
      if (sendMethod == null) {
        botWarn("[Bot] Tab清理: 找不到sendServerPacket方法");
        return;
      }
      for (Player p : Bukkit.getOnlinePlayers()) {
        if (bm != null && bm.isBot(p)) continue;
        try {
          sendMethod.invoke(pm, p, packet, false);
          sent++;
        } catch (Throwable ignored) {}
      }
      if (log) {
        botInfo("[Bot] Tab清理: 已向" + sent + "名玩家发送REMOVE_PLAYER(" + uuid + " / " + name + ")");
      }
    } catch (Throwable t) {
      botWarn("[Bot] Tab清理REMOVE_PLAYER失败: " + t.getMessage());
    }
  }

  /**
   * @deprecated 原 NMS raw 反射版；ProtocolLib 管线下 REMOVE_PLAYER 必须经
   *             ProtocolLibrary 发送否则服务端 Tab 缓存不被更新，保留仅供回退。
   */
  @Deprecated
  public static void sendRemovePlayerInfoNms(java.util.UUID uuid, String name, boolean log) {
    if (uuid == null) {
      return;
    }
    try {
      String version = BedwarsPRO.getInstance().getCurrentVersion();
      String nms = "net.minecraft.server." + version + ".";

      Class<?> packetClass = Class.forName(nms + "PacketPlayOutPlayerInfo");
      Class<?> actionClass = Class.forName(nms + "PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
      Object removeAction = null;
      for (Object c : actionClass.getEnumConstants()) {
        if (c.toString().equals("REMOVE_PLAYER")) {
          removeAction = c;
          break;
        }
      }
      if (removeAction == null) {
        if (log) {
          botWarn("[Bot] Tab清理: 未找到REMOVE_PLAYER枚举");
        }
        return;
      }

      // PlayerInfoData 是 PacketPlayOutPlayerInfo 的非静态内部类，字节码构造器形如
      // (外层PacketPlayOutPlayerInfo, GameProfile, int ping, *EnumGamemode*, IChatBaseComponent)。
      // 各版本差异：1.8/1.9 的 gamemode 枚举是 WorldSettings$EnumGamemode（简单名/尾缀同为
      // "EnumGamemode"），1.10+ 才是独立的 EnumGamemode；直接用"类型名尾缀"匹配可同时兼容。
      Class<?> dataClass = Class.forName(nms + "PacketPlayOutPlayerInfo$PlayerInfoData");

      // 1.12 PacketPlayOutPlayerInfo 字段：a(枚举 action) + b(List<PlayerInfoData>)。
      // 先创建 packet 实例：PlayerInfoData 是非静态内部类，其字节码构造器第一个参数
      // 是外层 PacketPlayOutPlayerInfo 实例（合成 this$0），创建 data 时需要传入。
      Object packet = packetClass.getConstructor().newInstance();

      // 匹配 PlayerInfoData 构造器：按参数"类型名"分类，不依赖 Class == 身份比较。
      // 之前用 == 比较 EnumGamemode/IChatBaseComponent（Class.forName 结果 vs 构造器
      // 参数类型），在插件 classloader 与 NMS 引用类不是同一 Class 实例时会匹配失败，
      // 直接导致"未找到PlayerInfoData构造器"、REMOVE_PLAYER 从未发送、Tab 条目永久残留。
      // 期望参数集合：外层实例(可选) + com.mojang.authlib.GameProfile + int
      // + *EnumGamemode* + *IChatBaseComponent，各自只能出现一次。
      Object data = null;
      for (java.lang.reflect.Constructor<?> ctor : dataClass.getDeclaredConstructors()) {
        Class<?>[] ps = ctor.getParameterTypes();
        if (ps.length != 4 && ps.length != 5) {
          continue;
        }
        Class<?> gameProfileType = null;
        Class<?> gamemodeType = null;
        boolean hasInt = false;
        boolean hasBaseComponent = false;
        boolean setOk = true;
        for (Class<?> pt : ps) {
          String pn = pt.getName();
          if (pn.equals("com.mojang.authlib.GameProfile")) {
            if (gameProfileType != null) {
              setOk = false;
              break;
            }
            gameProfileType = pt;
          } else if (pt == int.class) {
            if (hasInt) {
              setOk = false;
              break;
            }
            hasInt = true;
          } else if (pn.endsWith("EnumGamemode")) {
            if (gamemodeType != null) {
              setOk = false;
              break;
            }
            gamemodeType = pt;
          } else if (pn.endsWith("IChatBaseComponent")) {
            if (hasBaseComponent) {
              setOk = false;
              break;
            }
            hasBaseComponent = true;
          } else if (packetClass.isAssignableFrom(pt)) {
            // 外层实例参数：非静态内部类构造器的合成 this$0
          } else {
            setOk = false;
            break;
          }
        }
        if (!setOk || gameProfileType == null || gamemodeType == null
            || !hasInt || !hasBaseComponent) {
          continue;
        }
        // 从构造器真实的枚举类取 NOT_SET（兼容 WorldSettings$EnumGamemode / EnumGamemode）
        Object notSet = null;
        for (Object c : gamemodeType.getEnumConstants()) {
          if (c.toString().equals("NOT_SET")) {
            notSet = c;
            break;
          }
        }
        if (notSet == null) {
          continue;
        }
        // 用构造器真实的 GameProfile 类型构建实例，避免不同 classloader 导致类型不兼容
        Object profile = gameProfileType.getConstructor(java.util.UUID.class, String.class)
            .newInstance(uuid, name);
        Object[] args = new Object[ps.length];
        for (int i = 0; i < ps.length; i++) {
          if (ps[i] == int.class) {
            args[i] = 0;
          } else if (ps[i] == gameProfileType) {
            args[i] = profile;
          } else if (ps[i] == gamemodeType) {
            args[i] = notSet;
          } else if (ps[i].getName().endsWith("IChatBaseComponent")) {
            args[i] = null;
          } else if (packetClass.isAssignableFrom(ps[i])) {
            args[i] = packet;
          } else {
            args[i] = null;
          }
        }
        try {
          ctor.setAccessible(true);
          data = ctor.newInstance(args);
          break;
        } catch (Exception ignored) {
        }
      }
      if (data == null) {
        if (log) {
          botWarn("[Bot] Tab清理: 未找到PlayerInfoData构造器");
        }
        return;
      }

      for (java.lang.reflect.Field f : packetClass.getDeclaredFields()) {
        if (f.getType().isEnum()) {
          f.setAccessible(true);
          f.set(packet, removeAction);
        } else if (java.util.List.class.isAssignableFrom(f.getType())) {
          f.setAccessible(true);
          Object listObj = f.get(packet);
          if (listObj instanceof java.util.List) {
            ((java.util.List) listObj).add(data);
          }
        }
      }

      Method sendPacket = null;
      for (Method m : Class.forName(nms + "PlayerConnection").getMethods()) {
        if (m.getName().equals("sendPacket") && m.getParameterCount() == 1) {
          sendPacket = m;
          break;
        }
      }
      if (sendPacket == null) {
        if (log) {
          botWarn("[Bot] Tab清理: 未找到sendPacket方法");
        }
        return;
      }
      Class<?> craftPlayerClass = Class.forName(
          "org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
      BotManager bm = BedwarsPRO.getInstance().getBotManager();
      int sent = 0;
      for (Player online : Bukkit.getOnlinePlayers()) {
        if (bm != null && bm.isBot(online)) {
          continue;
        }
        try {
          Object h = craftPlayerClass.getMethod("getHandle").invoke(online);
          Object pc = h.getClass().getField("playerConnection").get(h);
          sendPacket.invoke(pc, packet);
          sent++;
        } catch (Exception ignored) {
        }
      }
      if (log) {
        botInfo("[Bot] Tab清理: 已向" + sent + "名玩家发送REMOVE_PLAYER(" + uuid + " / " + name + ")");
      }
    } catch (Throwable t) {
      botWarn("[Bot] Tab清理REMOVE_PLAYER失败: " + t.getMessage());
    }
  }

  /**
   * 读取 Corpse 尸体对象的 profile（packetevents UserProfile）字段并返回其 UUID。
   * Corpse 每次创建尸体都用 UUID.randomUUID() 生成全新 UserProfile（Corpse 构造器
   * 字节码实证），Tab 里尸体条目（show 时 ADD_PLAYER）按这个 UUID 存放；
   * REMOVE_PLAYER 必须用同一个 UUID 才能把该条目移除。
   * profile 字段声明在父类 Corpse（protected final），而 corpseMap 里的实例可能是
   * LootableCorpse 等子类，getDeclaredField 只查当前类会找不到（返回 null 导致
   * 1.2.9 的清理包根本没发），必须沿父类链查找。
   */
  private static java.util.UUID getCorpseProfileUuid(Object corpse) {
    try {
      java.lang.reflect.Field profileField = null;
      Class<?> clazz = corpse.getClass();
      while (clazz != null && profileField == null) {
        try {
          profileField = clazz.getDeclaredField("profile");
        } catch (NoSuchFieldException e) {
          clazz = clazz.getSuperclass();
        }
      }
      if (profileField == null) {
        return null;
      }
      profileField.setAccessible(true);
      Object profile = profileField.get(corpse);
      if (profile == null) {
        return null;
      }
      return (java.util.UUID) profile.getClass().getMethod("getUUID").invoke(profile);
    } catch (Throwable ignored) {
      return null;
    }
  }

  /**
   * 把尸体标记为对所有在线玩家"已显示"（反射往 seeingPlayers 集合塞人）。
   * CorpsePool.corpseTick（异步、每 2 tick）只在 !isShownFor(玩家) 且距离近时
   * 才 show（ADD_PLAYER + spawn）。我们 remove 尸体后如果 corpseTick 恰好正拿着
   * 这个尸体引用，会把已移除的尸体重新 show 出来——尸体已不在 corpseMap，之后
   * 再没人能隐藏它，就成了永久可见的"僵尸尸体"（用户反馈"遗体不消失"）。
   * 先标记已显示，corpseTick 的 isShownFor 返回 true，就不会再 show。
   */
  private static void markCorpseShownForAll(Object corpse) {
    try {
      java.lang.reflect.Field seeingField = null;
      Class<?> clazz = corpse.getClass();
      while (clazz != null && seeingField == null) {
        try {
          seeingField = clazz.getDeclaredField("seeingPlayers");
        } catch (NoSuchFieldException e) {
          clazz = clazz.getSuperclass();
        }
      }
      if (seeingField == null) {
        return;
      }
      seeingField.setAccessible(true);
      Object set = seeingField.get(corpse);
      if (set instanceof java.util.Collection) {
        @SuppressWarnings("unchecked")
        java.util.Collection<Player> seeing = (java.util.Collection<Player>) set;
        BotManager bm = BedwarsPRO.getInstance().getBotManager();
        for (Player online : Bukkit.getOnlinePlayers()) {
          if (bm != null && bm.isBot(online)) {
            // 必须把 bot 从 seeingPlayers 里清掉，不只是不添加：
            // corpseTick（每 2 tick、延迟 20 tick 后才启动）对尸体 show 时
            // show() 会无条件 seeingPlayers.add(bot)。bot 的假 channel 注册
            // 失败时，CorpsePool.remove() 内部对 seeingPlayers 逐个 hide(bot)
            // 会在 despawn(null) 抛 NPE，forEach 中断 → 排在后面的真人收不到
            // Destroy → 尸体实体永久残留（"第二次死亡后尸体躺地上不消失"，
            // 第一次死亡时 corpseTick 尚未启动所以移除正常）。
            // 清掉 bot 后 remove 内部只对真人 hide（真人 channel 正常）。
            seeing.remove(online);
            continue;
          }
          seeing.add(online);
        }
      }
    } catch (Throwable ignored) {
    }
  }

  /**
   * 直接对每个在线真人玩家调 Corpse.hide(Player)（内部走 CorpseNPC.despawn →
   * DestroyEntities），兜底覆盖 remove 内部 hide 与异步 corpseTick show 之间的
   * 竞态窗口（corpseTick 已通过 isShownFor 检查、正要 show 的那一拍）。
   */
  private static void forceHideCorpse(Object corpse) {
    try {
      BotManager bm = BedwarsPRO.getInstance().getBotManager();
      Method hide = corpse.getClass().getMethod("hide", Player.class);
      for (Player viewer : Bukkit.getOnlinePlayers()) {
        if (bm != null && bm.isBot(viewer)) {
          continue;
        }
        try {
          hide.invoke(corpse, viewer);
        } catch (Throwable ignored) {
        }
      }
    } catch (Throwable ignored) {
    }
  }

  /**
   * 周期性兜底清理任务（每 5 tick，主线程）。
   *
   * 1. Corpse 尸体在 Tab 里的残留：Corpse 的 hide/despawn/remove 只发
   *    DestroyEntities（实体消失），show 时发的 ADD_PLAYER（每次死亡随机 UUID）
   *    永远不会被 REMOVE_PLAYER 移除 → Tab 里同名条目永久累积（"机器人死几次
   *    Tab 就多几个相同名字"）。
   *    这里兜底处理：
   *    a) 所有 bot 名尸体：直接 CorpsePool.remove(id)（尸体对 bot 无意义，bot
   *       复活会重新 spawn）并补发 REMOVE_PLAYER；
   *    b) 所有真实玩家尸体：对每个能看到它的在线真人补发 REMOVE_PLAYER。
   *       Corpse.corpseTick 只在 !isShownFor(玩家) 时才 show（ADD_PLAYER），所以
   *       清理后条目不会立刻回来，除非玩家远离再靠近触发重新 show——本任务会
   *       再次清理，保持 Tab 收敛。
   * 2. bot 保位：确保 bot 在服务器玩家列表（PlayerList.players）与世界实体列表
   *    （WorldServer.entityList）中，修复 /tp、/kill 等命令"找不到实体"的问题
   *    （bot 手动加入不走 placeNewPlayer，若被 World 清理则命令找不到目标）。
   */
  public static void startCorpseTabCleanupTask() {
    new BukkitRunnable() {
      @Override
      public void run() {
        try {
          Class<?> poolClass = findCorpsePoolClass();
          if (poolClass == null) {
            ensureBotsInServerLists();
            return;
          }
          Object pool = poolClass.getMethod("getInstance").invoke(null);
          Object corpses = poolClass.getMethod("getCorpses").invoke(pool);
          if (corpses instanceof java.util.Collection) {
            BotManager bm = BedwarsPRO.getInstance().getBotManager();
            java.util.Set<String> botNames = new java.util.HashSet<>();
            if (bm != null) {
              for (BotPlayer bp : bm.getAllBots()) {
                Player bpPlayer = bp.getBukkitPlayer();
                if (bpPlayer != null) {
                  botNames.add(bpPlayer.getName());
                }
              }
            }
            for (Object corpse : new java.util.ArrayList<>((java.util.Collection<?>) corpses)) {
              try {
                String cname = (String) corpse.getClass().getMethod("getName").invoke(corpse);
                if (cname == null) {
                  continue;
                }
                java.util.UUID cUuid = getCorpseProfileUuid(corpse);
                if (botNames.contains(cname)) {
                  int id = (Integer) corpse.getClass().getMethod("getId").invoke(corpse);
                  // 与 removeCorpseForBot 同样的竞态处理：先标记已显示再 remove，
                  // remove 后补一次 hide，防止尸体被异步 corpseTick 重新 show 成
                  // 永久可见的"僵尸尸体"
                  markCorpseShownForAll(corpse);
                  try {
                    poolClass.getMethod("remove", int.class).invoke(pool, id);
                  } catch (Throwable t) {
                    // 见 removeCorpseForBot：异步 corpseTick 可能把 bot show 进
                    // seeingPlayers 导致 hide(bot) NPE 中断 remove，吞掉继续兜底
                    botWarn("[Bot] 周期清理Corpse移除异常(已兜底隐藏): " + t);
                  }
                  forceHideCorpse(corpse);
                  botInfo("[Bot] 已移除Corpse尸体: " + cname);
                  // UUID 读取失败也要 remove 尸体（尸体消失不依赖 UUID），
                  // 只是 Tab 条目（REMOVE_PLAYER）无法清理
                  if (cUuid != null) {
                    sendRemovePlayerInfo(cUuid, cname, true);
                  }
                } else if (cUuid != null) {
                  // 真实玩家尸体：给所有能看到它的在线真人补发 REMOVE_PLAYER（广播一次即可）
                  for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (bm != null && bm.isBot(viewer)) {
                      continue;
                    }
                    try {
                      Object shown = corpse.getClass()
                          .getMethod("isShownFor", Player.class).invoke(corpse, viewer);
                      if (shown instanceof Boolean && (Boolean) shown) {
                        sendRemovePlayerInfo(cUuid, cname, false);
                        break;
                      }
                    } catch (Throwable ignored) {
                    }
                  }
                }
              } catch (Throwable ignored) {
              }
            }
          }
          ensureBotsInServerLists();
        } catch (Throwable ignored) {
        }
      }
    }.runTaskTimer(BedwarsPRO.getInstance(), 5L, 5L);
  }

  /** 确保所有存活的 bot 在 PlayerList.players 与 WorldServer.entityList 中（tp/kill 修复）。 */
  public static void ensureBotsInServerLists() {
    try {
      BotManager bm = BedwarsPRO.getInstance().getBotManager();
      if (bm == null) {
        return;
      }
      String version = BedwarsPRO.getInstance().getCurrentVersion();
      Class<?> craftPlayerClass = Class.forName(
          "org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
      Class<?> craftServerClass = Class.forName(
          "org.bukkit.craftbukkit." + version + ".CraftServer");
      Object playerList = craftServerClass.getMethod("getHandle")
          .invoke(craftServerClass.cast(Bukkit.getServer()));
      Field playersField = null;
      Class<?> sc = playerList.getClass();
      while (sc != null && playersField == null) {
        try {
          playersField = sc.getDeclaredField("players");
        } catch (NoSuchFieldException e) {
          sc = sc.getSuperclass();
        }
      }
      for (BotPlayer bp : bm.getAllBots()) {
        Player p = bp.getBukkitPlayer();
        // 不做 !isValid() 门控：死亡中的 bot isValid()==false（isAlive()==false），
        // 若跳过会导致其脱离玩家列表/世界实体列表，/tp、/kill 等命令"找不到实体"
        if (p == null) {
          continue;
        }
        try {
          Object ep = craftPlayerClass.getMethod("getHandle").invoke(craftPlayerClass.cast(p));
          // 1) PlayerList.players：/tp、/kill 等按名字解析目标的命令走这里
          if (playersField != null) {
            playersField.setAccessible(true);
            Object listObj = playersField.get(playerList);
            if (listObj instanceof java.util.List && !((java.util.List) listObj).contains(ep)) {
              ((java.util.List) listObj).add(ep);
              botInfo("[Bot] 周期保位: " + p.getName() + " 已重新加入服务器玩家列表");
            }
          }
          // 2) PlayerList 的 UUID 映射：1.12.2 中被混淆为字段 j（不是
          //    playersByUUID！），1.8-1.11 是 playersByUUID。按"字段类型是
          //    Map 且现有 key 是 UUID"来匹配，跨版本通用
          try {
            Field uuidMapField = null;
            Class<?> uc = playerList.getClass();
            while (uc != null && uuidMapField == null) {
              for (Field f : uc.getDeclaredFields()) {
                if (java.util.Map.class.isAssignableFrom(f.getType())) {
                  try {
                    f.setAccessible(true);
                    Object m = f.get(playerList);
                    if (m instanceof java.util.Map) {
                      boolean hasUuidKey = false;
                      for (Object key : ((java.util.Map<?, ?>) m).keySet()) {
                        if (key instanceof java.util.UUID) {
                          hasUuidKey = true;
                          break;
                        }
                      }
                      if (hasUuidKey) {
                        uuidMapField = f;
                        break;
                      }
                    }
                  } catch (Throwable ignored) {
                  }
                }
              }
              uc = uc.getSuperclass();
            }
            if (uuidMapField != null) {
              uuidMapField.setAccessible(true);
              Object mapObj = uuidMapField.get(playerList);
              if (mapObj instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) mapObj;
                Object uuid = ep.getClass().getMethod("getUniqueID").invoke(ep);
                if (uuid != null && !map.containsKey(uuid)) {
                  map.put(uuid, ep);
                  botInfo("[Bot] 周期保位: " + p.getName() + " 已加入 UUID 映射(" + uuidMapField.getName() + ")");
                }
              }
            }
          } catch (Throwable ignored) {
          }
          // 3) 世界实体列表：死亡中的实体每 tick 可能被 World 移除，周期补回保证
          //    实体可被选中/交互；若 World.addEntity 拒绝 dead 实体则静默跳过，
          //    PlayerList 成员关系已足够让 /tp /kill 按名字找到目标
          Object ws = p.getWorld().getClass().getMethod("getHandle").invoke(p.getWorld());
          Field elField = null;
          Class<?> wc = ws.getClass();
          while (wc != null && elField == null) {
            try {
              elField = wc.getDeclaredField("entityList");
            } catch (NoSuchFieldException e) {
              wc = wc.getSuperclass();
            }
          }
          if (elField != null) {
            elField.setAccessible(true);
            Object listObj = elField.get(ws);
            if (listObj instanceof java.util.List && !((java.util.List) listObj).contains(ep)) {
              boolean added = false;
              for (Method m : ws.getClass().getMethods()) {
                if (m.getName().equals("addEntity") && m.getParameterCount() >= 1
                    && m.getParameterCount() <= 2) {
                  try {
                    if (m.getParameterCount() == 2) {
                      m.invoke(ws, ep,
                          org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
                    } else {
                      m.invoke(ws, ep);
                    }
                    added = true;
                    break;
                  } catch (Exception ignored) {
                  }
                }
              }
              if (added) {
                botInfo("[Bot] 周期保位: " + p.getName() + " 实体已重新加入世界");
              }
            }
          }
        } catch (Throwable ignored) {
        }
      }
    } catch (Throwable ignored) {
    }
  }

  /**
   * 让 bot 假人在死亡后完整复活。
   * FastRespawn 对假玩家不完整：服务端 Entity.dead 标志未解除（isAlive() 仍为 false、
   * AI 不再驱动），客户端视角 bot 实体已死亡消失且不会重新出现。
   * 这里补全：1) 反射解除服务端 dead；2) 确保实体重新在世界实体列表中
   * （若死亡过程中被 World 移除则重新 addEntity，否则服务端无法交互：
   * 攻击无效 / tp、kill 找不到实体）；3) 向其他玩家重发
   * REMOVE + ADD + Destroy + Spawn + Metadata + Head，让客户端 Tab 收敛为
   * 一条且实体重新显示。
   */
  public static void respawnFakePlayer(Player player) {
    if (player == null) {
      return;
    }
    try {
      String version = BedwarsPRO.getInstance().getCurrentVersion();
      final Class<?> craftPlayerClass = Class.forName(
          "org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
      Object craftPlayer = craftPlayerClass.cast(player);
      Object entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);

      // 1. 解除服务端死亡状态（Entity.dead = false），恢复 isAlive()/AI 驱动
      Class<?> clazz = entityPlayer.getClass();
      while (clazz != null) {
        try {
          Field deadField = clazz.getDeclaredField("dead");
          if (deadField.getType() == boolean.class) {
            deadField.setAccessible(true);
            deadField.setBoolean(entityPlayer, false);
            break;
          }
        } catch (NoSuchFieldException ignored) {
        }
        clazz = clazz.getSuperclass();
      }

      // 1.5 确保实体在世界实体列表中。1.12 中 dead 的实体会被 World 从
      // entityList 移除，仅解除 dead 标志并不会把它加回去——实体不在世界里
      // 就无法被攻击/传送/击杀（"打不动"、"tp/kill 找不到实体"）。
      try {
        Object worldServer = player.getWorld().getClass().getMethod("getHandle").invoke(player.getWorld());
        Field entityListField = null;
        Class<?> wc = worldServer.getClass();
        while (wc != null && entityListField == null) {
          try {
            entityListField = wc.getDeclaredField("entityList");
          } catch (NoSuchFieldException e) {
            wc = wc.getSuperclass();
          }
        }
        boolean inWorld = true;
        if (entityListField != null) {
          entityListField.setAccessible(true);
          Object listObj = entityListField.get(worldServer);
          if (listObj instanceof java.util.List) {
            inWorld = ((java.util.List) listObj).contains(entityPlayer);
          }
        }
        if (!inWorld) {
          boolean added = false;
          for (Method m : worldServer.getClass().getMethods()) {
            if (m.getName().equals("addEntity") && m.getParameterCount() >= 1
                && m.getParameterCount() <= 2) {
              try {
                if (m.getParameterCount() == 2) {
                  m.invoke(worldServer, entityPlayer,
                      org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
                } else {
                  m.invoke(worldServer, entityPlayer);
                }
                added = true;
                break;
              } catch (Exception ignored) {
              }
            }
          }
          if (added) {
            botInfo("[Bot] " + player.getName() + " 实体已重新加入世界");
          }
        }
      } catch (Exception ignored) {
      }

      int entityId = (Integer) entityPlayer.getClass().getMethod("getId").invoke(entityPlayer);

      // PlayerInfo REMOVE_PLAYER / ADD_PLAYER：强制客户端把该 bot 的 Tab 条目
      // 收敛为一条（此前 hide/show 时序若导致重复 ADD，REMOVE 会清掉全部再 ADD 一次）
      Class<?> packetInfoClass = Class.forName(
          "net.minecraft.server." + version + ".PacketPlayOutPlayerInfo");
      Class<?> enumActionClass = Class.forName(
          "net.minecraft.server." + version + ".PacketPlayOutPlayerInfo$EnumPlayerInfoAction");
      Object removeAction = null;
      Object addAction = null;
      for (Object enumConst : enumActionClass.getEnumConstants()) {
        if (enumConst.toString().equals("REMOVE_PLAYER")) {
          removeAction = enumConst;
        }
        if (enumConst.toString().equals("ADD_PLAYER")) {
          addAction = enumConst;
        }
      }
      Object removePacket = null;
      Object addPacket = null;
      for (Constructor<?> ctor : packetInfoClass.getDeclaredConstructors()) {
        if (ctor.getParameterCount() == 2 && ctor.getParameterTypes()[0].isEnum()) {
          try {
            ctor.setAccessible(true);
            if (removeAction != null && removePacket == null) {
              removePacket = ctor.newInstance(removeAction,
                  java.util.Collections.singletonList(entityPlayer));
            }
            if (addAction != null && addPacket == null) {
              addPacket = ctor.newInstance(addAction,
                  java.util.Collections.singletonList(entityPlayer));
            }
          } catch (Exception ignored) {
          }
        }
      }

      // PacketPlayOutEntityDestroy(int[])
      Class<?> packetDestroyClass =
          Class.forName("net.minecraft.server." + version + ".PacketPlayOutEntityDestroy");
      Object destroyPacket = null;
      for (Constructor<?> ctor : packetDestroyClass.getDeclaredConstructors()) {
        if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0] == int[].class) {
          try {
            ctor.setAccessible(true);
            destroyPacket = ctor.newInstance(new int[] {entityId});
            break;
          } catch (Exception ignored) {
          }
        }
      }

      // PacketPlayOutNamedEntitySpawn(EntityHuman)
      Class<?> packetSpawnClass =
          Class.forName("net.minecraft.server." + version + ".PacketPlayOutNamedEntitySpawn");
      Class<?> entityHumanClass = Class.forName("net.minecraft.server." + version + ".EntityHuman");
      Object spawnPacket = null;
      for (Constructor<?> ctor : packetSpawnClass.getDeclaredConstructors()) {
        if (ctor.getParameterCount() == 1
            && entityHumanClass.isAssignableFrom(ctor.getParameterTypes()[0])) {
          try {
            ctor.setAccessible(true);
            spawnPacket = ctor.newInstance(entityPlayer);
            break;
          } catch (Exception ignored) {
          }
        }
      }

      // PacketPlayOutEntityMetadata(int, Entity, boolean)
      Class<?> packetMetaClass =
          Class.forName("net.minecraft.server." + version + ".PacketPlayOutEntityMetadata");
      Class<?> entityClass = Class.forName("net.minecraft.server." + version + ".Entity");
      Object metaPacket = null;
      for (Constructor<?> ctor : packetMetaClass.getDeclaredConstructors()) {
        Class<?>[] params = ctor.getParameterTypes();
        if (params.length == 3 && params[0] == int.class
            && entityClass.isAssignableFrom(params[1]) && params[2] == boolean.class) {
          try {
            ctor.setAccessible(true);
            metaPacket = ctor.newInstance(entityId, entityPlayer, true);
            break;
          } catch (Exception ignored) {
          }
        }
      }

      // PacketPlayOutEntityHeadRotation(EntityHuman, byte)
      Class<?> packetHeadClass =
          Class.forName("net.minecraft.server." + version + ".PacketPlayOutEntityHeadRotation");
      Object headPacket = null;
      for (Constructor<?> ctor : packetHeadClass.getDeclaredConstructors()) {
        if (ctor.getParameterCount() == 2
            && entityHumanClass.isAssignableFrom(ctor.getParameterTypes()[0])) {
          try {
            ctor.setAccessible(true);
            headPacket = ctor.newInstance(entityPlayer,
                entityPlayer.getClass().getMethod("getYaw").invoke(entityPlayer));
            break;
          } catch (Exception ignored) {
          }
        }
      }

      Method sendMethodRef = null;
      for (Method m : Class.forName("net.minecraft.server." + version + ".PlayerConnection")
          .getMethods()) {
        if (m.getName().equals("sendPacket") && m.getParameterCount() == 1) {
          sendMethodRef = m;
          break;
        }
      }
      final Method sendMethod = sendMethodRef;
      if (sendMethod == null) {
        return;
      }

      final Object fRemove = removePacket;
      final Object fAdd = addPacket;
      final Object fDestroy = destroyPacket;
      final Object fSpawn = spawnPacket;
      final Object fMeta = metaPacket;
      final Object fHead = headPacket;

      // 先 REMOVE + Destroy：清掉客户端 Tab 里可能残留/重复的条目并移除旧实体
      for (Player online : Bukkit.getOnlinePlayers()) {
        if (online.equals(player)) {
          continue;
        }
        try {
          Object h = craftPlayerClass.getMethod("getHandle").invoke(online);
          Object pc = h.getClass().getField("playerConnection").get(h);
          if (fRemove != null) {
            sendMethod.invoke(pc, fRemove);
          }
          if (fDestroy != null) {
            sendMethod.invoke(pc, fDestroy);
          }
        } catch (Exception ignored) {
        }
      }

      // 延迟 1 tick 再 ADD + Spawn + Metadata + Head，避免同 tick 顺序错乱
      new BukkitRunnable() {
        @Override
        public void run() {
          for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) {
              continue;
            }
            try {
              Object h = craftPlayerClass.getMethod("getHandle").invoke(online);
              Object pc = h.getClass().getField("playerConnection").get(h);
              if (fAdd != null) {
                sendMethod.invoke(pc, fAdd);
              }
              if (fSpawn != null) {
                sendMethod.invoke(pc, fSpawn);
              }
              if (fMeta != null) {
                sendMethod.invoke(pc, fMeta);
              }
              if (fHead != null) {
                sendMethod.invoke(pc, fHead);
              }
            } catch (Exception ignored) {
            }
          }
        }
      }.runTaskLater(BedwarsPRO.getInstance(), 1L);

      botInfo("[Bot] " + player.getName() + " 已复活");
    } catch (Exception e) {
      botWarn("[Bot] 复活假人失败: " + player.getName() + " " + e.getMessage());
    }
  }

  public interface FakePlayerCallback {
    void onComplete(Player player);
  }
}