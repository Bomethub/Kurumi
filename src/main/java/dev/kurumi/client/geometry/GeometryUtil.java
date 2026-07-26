package dev.kurumi.client.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

public final class GeometryUtil {
   private static final double EPSILON = 1.0E-6D;
   private static final double HIT_EPSILON = 0.015D;
   private static final double MIN_EDGE_PRECISION = 1.0E-7D;
   private static final double EDGE_ULP_MULTIPLIER = 32.0D;
   private static final double BASE_EDGE_LOOKAHEAD = 0.035D;
   private static final double MAX_EDGE_LOOKAHEAD_FRACTION = 0.85D;
   private static final double CURRENT_STEP_MULTIPLIER = 1.5D;
   private static final double PRIOR_STEP_MULTIPLIER = 0.25D;
   private static final double ACCELERATION_MULTIPLIER = 0.4D;

   private GeometryUtil() {
   }

   public static List<AxisAlignedBB> collisionBoxes(WorldClient var0, BlockPos var1) {
      IBlockState var2 = var0.getBlockState(var1);
      Block var3 = var2.getBlock();
      ArrayList var4 = new ArrayList();
      AxisAlignedBB var5 = new AxisAlignedBB((double)var1.getX() - 0.01D, (double)var1.getY() - 0.01D, (double)var1.getZ() - 0.01D, (double)var1.getX() + 1.01D, (double)var1.getY() + 1.01D, (double)var1.getZ() + 1.01D);
      var3.addCollisionBoxesToList(var0, var1, var2, var5, var4, (Entity)null);
      return var4;
   }

   public static FacePatch resolveHitFace(WorldClient var0, BlockPos var1, EnumFacing var2, Vec3 var3) {
      List var4 = collisionBoxes(var0, var1);
      AxisAlignedBB var5 = null;
      double var6 = Double.POSITIVE_INFINITY;
      Iterator var8 = var4.iterator();

      AxisAlignedBB var9;
      FacePatch var10;
      double var11;
      while(var8.hasNext()) {
         var9 = (AxisAlignedBB)var8.next();
         var10 = FacePatch.fromBox(var9, var2);
         var11 = Math.abs(component(var3, var10.getNormalAxis()) - var10.getPlane());
         if (var11 <= 0.015D && var10.containsUV(var10.getU(var3), var10.getV(var3), 0.015D) && var11 < var6) {
            var5 = var9;
            var6 = var11;
         }
      }

      if (var5 == null) {
         var8 = var4.iterator();

         while(var8.hasNext()) {
            var9 = (AxisAlignedBB)var8.next();
            var10 = FacePatch.fromBox(var9, var2);
            if (var10.containsUV(var10.getU(var3), var10.getV(var3), 0.015D)) {
               var11 = Math.abs(component(var3, var10.getNormalAxis()) - var10.getPlane());
               if (var11 < var6) {
                  var5 = var9;
                  var6 = var11;
               }
            }
         }
      }

      if (var5 == null) {
         Block var13 = var0.getBlockState(var1).getBlock();
         var9 = var13.getSelectedBoundingBox(var0, var1);
         if (var9 != null) {
            var5 = var9;
         }
      }

      return var5 == null ? null : FacePatch.fromBox(var5, var2);
   }

   public static Vec3 intersectInfiniteLookRay(EntityPlayerSP var0, FacePatch var1) {
      return intersectInfiniteLookRay(var0, var1, 1.0F);
   }

   public static Vec3 intersectInfiniteLookRay(EntityPlayerSP var0, FacePatch var1, float var2) {
      Vec3 var3 = var0.getPositionEyes(var2);
      Vec3 var4 = var0.getLook(var2);
      double var5 = component(var4, var1.getNormalAxis());
      if (Math.abs(var5) <= 1.0E-6D) {
         return null;
      } else {
         double var7 = (var1.getPlane() - component(var3, var1.getNormalAxis())) / var5;
         return var7 < 0.0D ? null : var3.addVector(var4.xCoord * var7, var4.yCoord * var7, var4.zCoord * var7);
      }
   }

   public static EdgeCrossing detectFirstEdgeApproach(FacePatch var0, double var1, double var3, double var5, double var7, double var9, double var11) {
      double var13 = edgePrecision(var0);
      double var15 = var5 - var1;
      double var17 = var7 - var3;
      boolean var19 = var0.containsUV(var5, var7, var13);
      if (!var19) {
         return detectSegmentExit(var0, var1, var3, var5, var7, var13);
      } else {
         ArrayList var20 = new ArrayList(4);
         addApproachCandidate(var20, var0, true, false, var5, var7, var15, var17, var9, var11, var13);
         addApproachCandidate(var20, var0, true, true, var5, var7, var15, var17, var9, var11, var13);
         addApproachCandidate(var20, var0, false, false, var5, var7, var15, var17, var9, var11, var13);
         addApproachCandidate(var20, var0, false, true, var5, var7, var15, var17, var9, var11, var13);
         if (var20.isEmpty()) {
            return null;
         } else {
            Collections.sort(var20, new Comparator<GeometryUtil.Candidate>() {
               public int compare(GeometryUtil.Candidate var1, GeometryUtil.Candidate var2) {
                  int var3 = Double.compare(var1.t, var2.t);
                  return var3 != 0 ? var3 : Integer.compare(var1.order, var2.order);
               }
            });
            GeometryUtil.Candidate var21 = (GeometryUtil.Candidate)var20.get(0);
            double var24 = var0.clampU(var5, var13);
            double var26 = var0.clampV(var7, var13);
            return new EdgeCrossing(edgeSide(var0, var21.uBoundary, var21.highBoundary), var0.pointFromUV(var24, var26));
         }
      }
   }

   private static EdgeCrossing detectSegmentExit(FacePatch var0, double var1, double var3, double var5, double var7, double var9) {
      if (!var0.containsUV(var1, var3, var9)) {
         return null;
      } else {
         double var11 = var5 - var1;
         double var13 = var7 - var3;
         ArrayList var15 = new ArrayList(4);
         addExitCandidate(var15, var0, true, false, var1, var3, var11, var13, var9);
         addExitCandidate(var15, var0, true, true, var1, var3, var11, var13, var9);
         addExitCandidate(var15, var0, false, false, var1, var3, var11, var13, var9);
         addExitCandidate(var15, var0, false, true, var1, var3, var11, var13, var9);
         if (var15.isEmpty()) {
            return null;
         } else {
            Collections.sort(var15, new Comparator<GeometryUtil.Candidate>() {
               public int compare(GeometryUtil.Candidate var1, GeometryUtil.Candidate var2) {
                  int var3 = Double.compare(var1.t, var2.t);
                  return var3 != 0 ? var3 : Integer.compare(var1.order, var2.order);
               }
            });
            GeometryUtil.Candidate var16 = (GeometryUtil.Candidate)var15.get(0);
            double var17 = var1 + var11 * var16.t;
            double var19 = var3 + var13 * var16.t;
            return new EdgeCrossing(edgeSide(var0, var16.uBoundary, var16.highBoundary), var0.pointFromUV(Math.max(var0.getMinU(), Math.min(var0.getMaxU(), var17)), Math.max(var0.getMinV(), Math.min(var0.getMaxV(), var19))));
         }
      }
   }

   private static void addExitCandidate(List<GeometryUtil.Candidate> var0, FacePatch var1, boolean var2, boolean var3, double var4, double var6, double var8, double var10, double var12) {
      double var14 = var2 ? var8 : var10;
      if ((!var3 || !(var14 <= var12)) && (var3 || !(var14 >= -var12))) {
         double var16 = var2 ? var4 : var6;
         double var18 = var3 ? (var2 ? var1.getMaxU() : var1.getMaxV()) : (var2 ? var1.getMinU() : var1.getMinV());
         double var20 = (var18 - var16) / var14;
         if (!(var20 < -var12) && !(var20 > 1.0D + var12)) {
            var20 = Math.max(0.0D, Math.min(1.0D, var20));
            double var22 = (var2 ? var6 : var4) + (var2 ? var10 : var8) * var20;
            double var24 = var2 ? var1.getMinV() : var1.getMinU();
            double var26 = var2 ? var1.getMaxV() : var1.getMaxU();
            if (!(var22 < var24 - var12) && !(var22 > var26 + var12)) {
               int var28 = var2 ? (var3 ? 1 : 0) : (var3 ? 3 : 2);
               var0.add(new GeometryUtil.Candidate(var20, var2, var3, var28));
            }
         }
      }
   }

   private static void addApproachCandidate(List<GeometryUtil.Candidate> var0, FacePatch var1, boolean var2, boolean var3, double var4, double var6, double var8, double var10, double var12, double var14, double var16) {
      double var18 = var2 ? var4 : var6;
      double var20 = var2 ? var8 : var10;
      double var22 = var3 ? (var2 ? var1.getMaxU() : var1.getMaxV()) : (var2 ? var1.getMinU() : var1.getMinV());
      if (var3) {
         if (var20 <= var16) {
            return;
         }
      } else if (var20 >= -var16) {
         return;
      }

      double var24 = var3 ? var22 - var18 : var18 - var22;
      if (!(var24 < -var16)) {
         var24 = Math.max(0.0D, var24);
         double var26 = var2 ? var12 : var14;
         double var28 = var3 ? var20 : -var20;
         double var30 = var3 ? Math.max(0.0D, var26) : Math.max(0.0D, -var26);
         double var32 = Math.max(0.0D, var28 - var30);
         double var34 = var28 * 1.5D + var30 * 0.25D + var32 * 0.4D;
         double var36 = var2 ? var1.getMaxU() - var1.getMinU() : var1.getMaxV() - var1.getMinV();
         double var38 = Math.max(0.035D, var36 * 0.85D);
         double var40 = Math.min(var38, Math.max(0.035D, 0.035D + var34));
         var40 = Math.max(var40, var16);
         if (!(var24 > var40)) {
            double var42 = var28 <= var16 ? 0.0D : Math.max(0.0D, var24 / var28);
            double var44 = var2 ? var6 : var4;
            double var46 = var2 ? var10 : var8;
            double var48 = var44 + var46 * var42;
            double var50 = var2 ? var1.getMinV() : var1.getMinU();
            double var52 = var2 ? var1.getMaxV() : var1.getMaxU();
            if (!(var48 < var50 - var16) && !(var48 > var52 + var16)) {
               int var54;
               if (var2) {
                  var54 = var3 ? 1 : 0;
               } else {
                  var54 = var3 ? 3 : 2;
               }

               var0.add(new GeometryUtil.Candidate(var42, var2, var3, var54));
            }
         }
      }
   }

   public static boolean isPointOnFace(FacePatch var0, Vec3 var1) {
      double var2 = Math.max(1.0E-6D, edgePrecision(var0) * 8.0D);
      return Math.abs(component(var1, var0.getNormalAxis()) - var0.getPlane()) <= var2 && var0.containsUV(var0.getU(var1), var0.getV(var1), var2);
   }

   public static double edgePrecision(FacePatch var0) {
      double var1 = 0.0D;
      var1 = Math.max(var1, Math.ulp(Math.abs(var0.getPlane())));
      var1 = Math.max(var1, Math.ulp(Math.abs(var0.getMinU())));
      var1 = Math.max(var1, Math.ulp(Math.abs(var0.getMaxU())));
      var1 = Math.max(var1, Math.ulp(Math.abs(var0.getMinV())));
      var1 = Math.max(var1, Math.ulp(Math.abs(var0.getMaxV())));
      return Math.max(1.0E-7D, var1 * 32.0D);
   }

   private static EnumFacing edgeSide(FacePatch var0, boolean var1, boolean var2) {
      int var3 = var1 ? var0.getUAxis() : var0.getVAxis();
      if (var3 == 0) {
         return var2 ? EnumFacing.EAST : EnumFacing.WEST;
      } else if (var3 == 1) {
         return var2 ? EnumFacing.UP : EnumFacing.DOWN;
      } else {
         return var2 ? EnumFacing.SOUTH : EnumFacing.NORTH;
      }
   }

   public static boolean eyeFacesOutward(Vec3 var0, FacePatch var1) {
      double var2;
      switch(var1.getSide()) {
      case WEST:
         var2 = var1.getPlane() - var0.xCoord;
         break;
      case EAST:
         var2 = var0.xCoord - var1.getPlane();
         break;
      case DOWN:
         var2 = var1.getPlane() - var0.yCoord;
         break;
      case UP:
         var2 = var0.yCoord - var1.getPlane();
         break;
      case NORTH:
         var2 = var1.getPlane() - var0.zCoord;
         break;
      case SOUTH:
         var2 = var0.zCoord - var1.getPlane();
         break;
      default:
         return false;
      }

      return var2 > 1.0E-6D;
   }

   public static boolean isFullyCoveredBySameBlock(FacePatch var0, List<AxisAlignedBB> var1) {
      return isFullyCovered(var0, outwardBoxes(var0, var1));
   }

   public static boolean isFullyCoveredByAdjacentBlock(WorldClient var0, BlockPos var1, FacePatch var2) {
      BlockPos var3 = var1.offset(var2.getSide());
      List var4 = collisionBoxes(var0, var3);
      return isFullyCovered(var2, outwardBoxes(var2, var4));
   }

   private static List<AxisAlignedBB> outwardBoxes(FacePatch var0, List<AxisAlignedBB> var1) {
      ArrayList var2 = new ArrayList();
      Iterator var3 = var1.iterator();

      while(var3.hasNext()) {
         AxisAlignedBB var4 = (AxisAlignedBB)var3.next();
         if (occupiesOutwardSide(var0, var4)) {
            var2.add(var4);
         }
      }

      return var2;
   }

   private static boolean occupiesOutwardSide(FacePatch var0, AxisAlignedBB var1) {
      double var2 = var0.getPlane();
      switch(var0.getSide()) {
      case WEST:
         return var1.maxX >= var2 - 1.0E-6D && var1.minX < var2 - 1.0E-6D;
      case EAST:
         return var1.minX <= var2 + 1.0E-6D && var1.maxX > var2 + 1.0E-6D;
      case DOWN:
         return var1.maxY >= var2 - 1.0E-6D && var1.minY < var2 - 1.0E-6D;
      case UP:
         return var1.minY <= var2 + 1.0E-6D && var1.maxY > var2 + 1.0E-6D;
      case NORTH:
         return var1.maxZ >= var2 - 1.0E-6D && var1.minZ < var2 - 1.0E-6D;
      case SOUTH:
         return var1.minZ <= var2 + 1.0E-6D && var1.maxZ > var2 + 1.0E-6D;
      default:
         return false;
      }
   }

   private static boolean isFullyCovered(FacePatch var0, List<AxisAlignedBB> var1) {
      ArrayList var2 = new ArrayList();
      ArrayList var3 = new ArrayList();
      ArrayList var4 = new ArrayList();
      var3.add(var0.getMinU());
      var3.add(var0.getMaxU());
      var4.add(var0.getMinV());
      var4.add(var0.getMaxV());
      Iterator var5 = var1.iterator();

      double var13;
      while(var5.hasNext()) {
         AxisAlignedBB var6 = (AxisAlignedBB)var5.next();
         double var7 = Math.max(var0.getMinU(), minComponent(var6, var0.getUAxis()));
         double var9 = Math.min(var0.getMaxU(), maxComponent(var6, var0.getUAxis()));
         double var11 = Math.max(var0.getMinV(), minComponent(var6, var0.getVAxis()));
         var13 = Math.min(var0.getMaxV(), maxComponent(var6, var0.getVAxis()));
         if (var9 - var7 > 1.0E-6D && var13 - var11 > 1.0E-6D) {
            var2.add(new GeometryUtil.Rect(var7, var9, var11, var13));
            var3.add(var7);
            var3.add(var9);
            var4.add(var11);
            var4.add(var13);
         }
      }

      if (var2.isEmpty()) {
         return false;
      } else {
         sortAndDedupe(var3);
         sortAndDedupe(var4);

         for(int var19 = 0; var19 < var3.size() - 1; ++var19) {
            double var20 = (Double)var3.get(var19);
            double var8 = (Double)var3.get(var19 + 1);
            if (!(var8 - var20 <= 1.0E-6D)) {
               double var10 = (var20 + var8) * 0.5D;

               for(int var12 = 0; var12 < var4.size() - 1; ++var12) {
                  var13 = (Double)var4.get(var12);
                  double var15 = (Double)var4.get(var12 + 1);
                  if (!(var15 - var13 <= 1.0E-6D)) {
                     double var17 = (var13 + var15) * 0.5D;
                     if (!coveredByAny(var2, var10, var17)) {
                        return false;
                     }
                  }
               }
            }
         }

         return true;
      }
   }

   private static boolean coveredByAny(List<GeometryUtil.Rect> var0, double var1, double var3) {
      Iterator var5 = var0.iterator();

      GeometryUtil.Rect var6;
      do {
         if (!var5.hasNext()) {
            return false;
         }

         var6 = (GeometryUtil.Rect)var5.next();
      } while(!(var1 >= var6.minU - 1.0E-6D) || !(var1 <= var6.maxU + 1.0E-6D) || !(var3 >= var6.minV - 1.0E-6D) || !(var3 <= var6.maxV + 1.0E-6D));

      return true;
   }

   private static void sortAndDedupe(List<Double> var0) {
      Collections.sort(var0);

      for(int var1 = var0.size() - 1; var1 > 0; --var1) {
         if (Math.abs((Double)var0.get(var1) - (Double)var0.get(var1 - 1)) <= 1.0E-6D) {
            var0.remove(var1);
         }
      }

   }

   public static boolean isOnPositiveAxisRay(BlockPos var0, EnumFacing var1, BlockPos var2) {
      int var3 = var2.getX() - var0.getX();
      int var4 = var2.getY() - var0.getY();
      int var5 = var2.getZ() - var0.getZ();
      switch(var1) {
      case WEST:
         return var3 < 0 && var4 == 0 && var5 == 0;
      case EAST:
         return var3 > 0 && var4 == 0 && var5 == 0;
      case DOWN:
         return var4 < 0 && var3 == 0 && var5 == 0;
      case UP:
         return var4 > 0 && var3 == 0 && var5 == 0;
      case NORTH:
         return var5 < 0 && var3 == 0 && var4 == 0;
      case SOUTH:
         return var5 > 0 && var3 == 0 && var4 == 0;
      default:
         return false;
      }
   }

   private static double component(Vec3 var0, int var1) {
      if (var1 == 0) {
         return var0.xCoord;
      } else {
         return var1 == 1 ? var0.yCoord : var0.zCoord;
      }
   }

   private static double minComponent(AxisAlignedBB var0, int var1) {
      if (var1 == 0) {
         return var0.minX;
      } else {
         return var1 == 1 ? var0.minY : var0.minZ;
      }
   }

   private static double maxComponent(AxisAlignedBB var0, int var1) {
      if (var1 == 0) {
         return var0.maxX;
      } else {
         return var1 == 1 ? var0.maxY : var0.maxZ;
      }
   }

   private static final class Candidate {
      private final double t;
      private final boolean uBoundary;
      private final boolean highBoundary;
      private final int order;

      private Candidate(double var1, boolean var3, boolean var4, int var5) {
         this.t = var1;
         this.uBoundary = var3;
         this.highBoundary = var4;
         this.order = var5;
      }
   }

   private static final class Rect {
      private final double minU;
      private final double maxU;
      private final double minV;
      private final double maxV;

      private Rect(double var1, double var3, double var5, double var7) {
         this.minU = var1;
         this.maxU = var3;
         this.minV = var5;
         this.maxV = var7;
      }
   }
}
