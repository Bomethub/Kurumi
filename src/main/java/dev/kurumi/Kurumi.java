package dev.kurumi;

import dev.kurumi.client.StraightLineController;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(
   modid = "kurumi",
   name = "Kurumi",
   version = "1.0.20",
   acceptedMinecraftVersions = "[1.8.9]",
   clientSideOnly = true
)
public final class Kurumi {
   public static final String MOD_ID = "kurumi";
   public static final String MOD_NAME = "Kurumi";
   public static final String VERSION = "1.0.20";

   @EventHandler
   public void init(FMLInitializationEvent var1) {
      StraightLineController var2 = new StraightLineController();
      MinecraftForge.EVENT_BUS.register(var2);
      FMLCommonHandler.instance().bus().register(var2);
   }
}
