package dev.kurumi.client.geometry;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

public final class EdgeCrossing {
   private final EnumFacing adjacentSide;
   private final Vec3 point;

   public EdgeCrossing(EnumFacing var1, Vec3 var2) {
      this.adjacentSide = var1;
      this.point = var2;
   }

   public EnumFacing getAdjacentSide() {
      return this.adjacentSide;
   }

   public Vec3 getPoint() {
      return this.point;
   }
}
