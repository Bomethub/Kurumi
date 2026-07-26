package dev.kurumi.client;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class RightClickDelayAccessor {
   private static final Logger LOGGER = LogManager.getLogger("Kurumi");
   private static final int VANILLA_DELAY = 4;
   private static final Field RIGHT_CLICK_DELAY_FIELD = findField();
   private static boolean warned;

   private RightClickDelayAccessor() {
   }

   static void clear(Minecraft var0) {
      set(var0, 0);
   }

   static void restore(Minecraft var0) {
      set(var0, 4);
   }

   private static Field findField() {
      try {
         Field var0 = ReflectionHelper.findField(Minecraft.class, new String[]{"rightClickDelayTimer", "field_71467_ac"});
         var0.setAccessible(true);
         return var0;
      } catch (RuntimeException var1) {
         LOGGER.error("Unable to find Minecraft.rightClickDelayTimer", var1);
         return null;
      }
   }

   private static void set(Minecraft var0, int var1) {
      if (RIGHT_CLICK_DELAY_FIELD != null && var0 != null) {
         try {
            RIGHT_CLICK_DELAY_FIELD.setInt(var0, var1);
         } catch (IllegalAccessException var3) {
            if (!warned) {
               warned = true;
               LOGGER.error("Unable to update Minecraft.rightClickDelayTimer", var3);
            }
         }

      } else {
         warnOnce();
      }
   }

   private static void warnOnce() {
      if (!warned) {
         warned = true;
         LOGGER.warn("FastPlace timer access is unavailable; StraightLine will still use the vanilla placement method.");
      }

   }
}
