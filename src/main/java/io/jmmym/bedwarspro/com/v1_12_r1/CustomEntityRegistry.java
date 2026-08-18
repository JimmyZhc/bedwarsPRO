package io.jmmym.bedwarspro.com.v1_12_r1;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityTypes;
import net.minecraft.server.v1_12_R1.MinecraftKey;
import net.minecraft.server.v1_12_R1.RegistryMaterials;

@SuppressWarnings({"rawtypes", "unchecked"})
public class CustomEntityRegistry {

  private static boolean registered = false;

  public static void addCustomEntity(int entityId, String entityName,
      Class<? extends Entity> entityClass) {
    if (registered) {
      return;
    }
    registered = true;

    MinecraftKey minecraftKey = new MinecraftKey(entityName);
    RegistryMaterials registry = EntityTypes.b;

    try {
      for (Method m : registry.getClass().getMethods()) {
        Class<?>[] params = m.getParameterTypes();
        if (params.length == 3
            && params[0] == int.class
            && params[1] == MinecraftKey.class
            && !params[2].isPrimitive()) {
          m.invoke(registry, entityId, minecraftKey, entityClass);
          return;
        }
      }

      Method aMethod = registry.getClass().getMethod("a", int.class, MinecraftKey.class, Object.class);
      aMethod.invoke(registry, entityId, minecraftKey, entityClass);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
