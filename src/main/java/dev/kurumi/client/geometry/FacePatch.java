package dev.kurumi.client.geometry;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

public final class FacePatch {
   private final AxisAlignedBB owner;
   private final EnumFacing side;
   private final int normalAxis;
   private final int uAxis;
   private final int vAxis;
   private final double plane;
   private final double minU;
   private final double maxU;
   private final double minV;
   private final double maxV;

   private FacePatch(AxisAlignedBB var1, EnumFacing var2, int var3, int var4, int var5, double var6, double var8, double var10, double var12, double var14) {
      this.owner = var1;
      this.side = var2;
      this.normalAxis = var3;
      this.uAxis = var4;
      this.vAxis = var5;
      this.plane = var6;
      this.minU = var8;
      this.maxU = var10;
      this.minV = var12;
      this.maxV = var14;
   }

   public static FacePatch fromBox(AxisAlignedBB var0, EnumFacing var1) {
      switch(var1) {
      case WEST:
         return new FacePatch(var0, var1, 0, 1, 2, var0.minX, var0.minY, var0.maxY, var0.minZ, var0.maxZ);
      case EAST:
         return new FacePatch(var0, var1, 0, 1, 2, var0.maxX, var0.minY, var0.maxY, var0.minZ, var0.maxZ);
      case DOWN:
         return new FacePatch(var0, var1, 1, 0, 2, var0.minY, var0.minX, var0.maxX, var0.minZ, var0.maxZ);
      case UP:
         return new FacePatch(var0, var1, 1, 0, 2, var0.maxY, var0.minX, var0.maxX, var0.minZ, var0.maxZ);
      case NORTH:
         return new FacePatch(var0, var1, 2, 0, 1, var0.minZ, var0.minX, var0.maxX, var0.minY, var0.maxY);
      case SOUTH:
         return new FacePatch(var0, var1, 2, 0, 1, var0.maxZ, var0.minX, var0.maxX, var0.minY, var0.maxY);
      default:
         throw new IllegalArgumentException("Unknown face: " + var1);
      }
   }

   public FacePatch adjacentFace(EnumFacing var1) {
      return fromBox(this.owner, var1);
   }

   public AxisAlignedBB getOwner() {
      return this.owner;
   }

   public EnumFacing getSide() {
      return this.side;
   }

   public int getNormalAxis() {
      return this.normalAxis;
   }

   public int getUAxis() {
      return this.uAxis;
   }

   public int getVAxis() {
      return this.vAxis;
   }

   public double getPlane() {
      return this.plane;
   }

   public double getMinU() {
      return this.minU;
   }

   public double getMaxU() {
      return this.maxU;
   }

   public double getMinV() {
      return this.minV;
   }

   public double getMaxV() {
      return this.maxV;
   }

   public double getU(Vec3 var1) {
      return component(var1, this.uAxis);
   }

   public double getV(Vec3 var1) {
      return component(var1, this.vAxis);
   }

   public Vec3 pointFromUV(double var1, double var3) {
      double[] var5 = new double[3];
      var5[this.normalAxis] = this.plane;
      var5[this.uAxis] = var1;
      var5[this.vAxis] = var3;
      return new Vec3(var5[0], var5[1], var5[2]);
   }

   public boolean containsUV(double var1, double var3, double var5) {
      return var1 >= this.minU - var5 && var1 <= this.maxU + var5 && var3 >= this.minV - var5 && var3 <= this.maxV + var5;
   }

   public double clampU(double var1, double var3) {
      return clamp(var1, this.minU + var3, this.maxU - var3);
   }

   public double clampV(double var1, double var3) {
      return clamp(var1, this.minV + var3, this.maxV - var3);
   }

   public double minForAxis(int var1) {
      switch(var1) {
      case 0:
         return this.owner.minX;
      case 1:
         return this.owner.minY;
      case 2:
         return this.owner.minZ;
      default:
         throw new IllegalArgumentException("axis");
      }
   }

   public double maxForAxis(int var1) {
      switch(var1) {
      case 0:
         return this.owner.maxX;
      case 1:
         return this.owner.maxY;
      case 2:
         return this.owner.maxZ;
      default:
         throw new IllegalArgumentException("axis");
      }
   }

   private static double component(Vec3 var0, int var1) {
      switch(var1) {
      case 0:
         return var0.xCoord;
      case 1:
         return var0.yCoord;
      case 2:
         return var0.zCoord;
      default:
         throw new IllegalArgumentException("axis");
      }
   }

   private static double clamp(double var0, double var2, double var4) {
      return var2 > var4 ? (var2 + var4) * 0.5D : Math.max(var2, Math.min(var4, var0));
   }
}
