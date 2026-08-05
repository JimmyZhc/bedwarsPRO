package io.jmmym.bedwarspro.bot;

import com.mojang.authlib.GameProfile;
import io.jmmym.bedwarspro.BedwarsPRO;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class BukkitFakePlayer {

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
            BedwarsPRO.getInstance().getLogger().warning("[Bot] create() 返回null: " + name);
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

    BedwarsPRO.getInstance().getLogger().info("[Bot] NMS版本: " + version);

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
      BedwarsPRO.getInstance().getLogger().warning("[Bot] 未找到EntityPlayer 4参数构造函数");
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
      BedwarsPRO.getInstance().getLogger().warning("[Bot] 无法创建PlayerInteractManager");
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
      BedwarsPRO.getInstance().getLogger().warning("[Bot] 未找到NetworkManager构造函数");
      return null;
    }
    nmCtor.setAccessible(true);
    Object networkManager = nmCtor.newInstance(direction);

    // EmbeddedChannel
    Class<?> embeddedChannelClass = Class.forName("io.netty.channel.embedded.EmbeddedChannel");
    Object channel = embeddedChannelClass.getConstructor().newInstance();

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
          BedwarsPRO.getInstance().getLogger().info("[Bot] 协议已设置为PLAY");
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

    BedwarsPRO.getInstance().getLogger().info("[Bot] NMS对象创建完成: " + name);

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

    // 10. 尝试placeNewPlayer
    boolean placed = false;
    try {
      Class<?> craftServerClazz = Class.forName(cb + "CraftServer");
      Object craftServerObj = craftServerClazz.cast(Bukkit.getServer());
      Method getHandleMethod = craftServerClazz.getMethod("getHandle");
      Object playerList = getHandleMethod.invoke(craftServerObj);
      BedwarsPRO.getInstance().getLogger().info("[Bot] PlayerList类型: " + playerList.getClass().getName());

      Method placeNewPlayer = null;
      Class<?> searchClass = playerList.getClass();
      while (searchClass != null && placeNewPlayer == null) {
        for (Method m : searchClass.getDeclaredMethods()) {
          if (m.getParameterCount() == 2) {
            Class<?>[] params = m.getParameterTypes();
            if (params[0] == networkManagerClass && entityPlayerClass.isAssignableFrom(params[1])) {
              placeNewPlayer = m;
              break;
            }
          }
        }
        searchClass = searchClass.getSuperclass();
      }

      if (placeNewPlayer != null) {
        placeNewPlayer.setAccessible(true);
        placeNewPlayer.invoke(playerList, networkManager, entityPlayer);
        BedwarsPRO.getInstance().getLogger().info("[Bot] placeNewPlayer成功: " + name);
        placed = true;
      } else {
        BedwarsPRO.getInstance().getLogger().warning("[Bot] 未找到placeNewPlayer，打印2参数方法:");
        searchClass = playerList.getClass();
        while (searchClass != null) {
          for (Method m : searchClass.getDeclaredMethods()) {
            if (m.getParameterCount() == 2) {
              BedwarsPRO.getInstance().getLogger().warning("[Bot]   " + searchClass.getSimpleName() + "." + m.getName() + java.util.Arrays.toString(m.getParameterTypes()));
            }
          }
          searchClass = searchClass.getSuperclass();
        }
      }
    } catch (Exception e) {
      BedwarsPRO.getInstance().getLogger().warning("[Bot] placeNewPlayer异常: " + e.getMessage());
    }

    // 11. 获取Bukkit包装
    Object craftPlayer = entityPlayerClass.getMethod("getBukkitEntity").invoke(entityPlayer);

    // 12. placeNewPlayer失败时手动添加到世界
    if (!placed) {
      for (Method m : worldServer.getClass().getMethods()) {
        if (m.getName().equals("addEntity") && m.getParameterCount() >= 1 && m.getParameterCount() <= 2) {
          try {
            if (m.getParameterCount() == 2) {
              m.invoke(worldServer, entityPlayer,
                  org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
            } else {
              m.invoke(worldServer, entityPlayer);
            }
            BedwarsPRO.getInstance().getLogger().info("[Bot] addEntity成功");
            break;
          } catch (Exception e) {
            BedwarsPRO.getInstance().getLogger().warning("[Bot] addEntity失败: " + e.getMessage());
          }
        }
      }
    }

    // 13. 始终发送可见性包给真实玩家
    sendVisibilityPackets(entityPlayer, entityPlayerClass, nms, playerConnection, playerConnectionClass, craftPlayer);

    // 13. KeepAlive保活
    startKeepAlive(entityPlayer, entityPlayerClass, playerConnection, playerConnectionClass);

    BedwarsPRO.getInstance().getLogger().info("[Bot] 假人创建成功: " + name);
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
        BedwarsPRO.getInstance().getLogger().info("[Bot] PlayerInfo已发送");
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
          BedwarsPRO.getInstance().getLogger().info("[Bot] Spawn+Metadata+Head已发送");
        }
      }.runTaskLater(BedwarsPRO.getInstance(), 1L);

    } catch (Exception e) {
      BedwarsPRO.getInstance().getLogger().warning("[Bot] 发包异常: " + e.getMessage());
      e.printStackTrace();
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
    if (player == null || !player.isOnline()) {
      return;
    }
    try {
      String version = BedwarsPRO.getInstance().getCurrentVersion();
      String nms = "net.minecraft.server." + version + ".";
      Class<?> craftPlayerClass = Class.forName(
          "org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
      Object craftPlayer = craftPlayerClass.cast(player);
      Object entityPlayer = craftPlayerClass.getMethod("getHandle").invoke(craftPlayer);
      entityPlayer.getClass().getMethod("die").invoke(entityPlayer);
    } catch (Exception e) {
      BedwarsPRO.getInstance().getLogger().warning("[Bot] 移除假人失败: " + player.getName());
    }
  }

  public interface FakePlayerCallback {
    void onComplete(Player player);
  }
}
