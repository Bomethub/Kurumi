package dev.kurumi.client;

import dev.kurumi.client.geometry.EdgeCrossing;
import dev.kurumi.client.geometry.FacePatch;
import dev.kurumi.client.geometry.GeometryUtil;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent.Unload;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;
import org.lwjgl.input.Mouse;

public final class StraightLineController {
   private static final double MIN_PENDING_LOOK_DOT = 0.5D;
   private static final double LOOKAHEAD_FACE_INSET = 1.0E-5D;
   private static final int MAX_PENDING_PLACEMENTS = 3;
   private static final int PLACEMENT_INVALID = 0;
   private static final int PLACEMENT_ATTEMPTED = 1;
   private static final int PLACEMENT_SUCCEEDED = 2;
   private final Minecraft minecraft = Minecraft.getMinecraft();
   private final Map<Class<?>, Boolean> nonActivatingBlockClasses = new HashMap();
   private final List<StraightLineController.PendingPlacement> pendingPlacements = new ArrayList(3);
   private boolean active;
   private boolean internalPlacement;
   private boolean physicalLeftDown;
   private boolean physicalRightDown;
   private FacePatch xFace;
   private BlockPos yBlock;
   private FacePatch zFace;
   private EnumFacing slDirection;
   private BlockPos startBlock;
   private boolean hasPreviousProjection;
   private double previousU;
   private double previousV;
   private double previousStepU;
   private double previousStepV;
   private boolean requiredXPlacementPending;
   private BlockPos requiredXStartBlock;
   private long currentClientTick;
   private long lastPlacementClientTick = Long.MIN_VALUE;
   private StraightLineController.PendingPlacement requiredPendingPlacement;
   private boolean reflectionResolved;
   private Method onPlayerRightClickMethod;
   private Method blockReachDistanceMethod;
   private Method swingItemMethod;

   @SubscribeEvent
   public void onClientTick(ClientTickEvent var1) {
      if (var1.phase == Phase.START) {
         ++this.currentClientTick;
         this.physicalLeftDown = Mouse.isButtonDown(0);
         this.physicalRightDown = Mouse.isButtonDown(1);
         boolean var2 = this.activationConditionsMet();
         if (this.active && !var2) {
            this.stopStraightLine();
         } else {
            if (!this.active && var2) {
               this.startStraightLine();
            }

            if (this.active) {
               this.suppressVanillaInputs();
               RightClickDelayAccessor.clear(this.minecraft);
               if (this.xFace == null) {
                  this.acquireFirstFace();
               }

            }
         }
      } else {
         if (var1.phase == Phase.END && this.active) {
            if (!this.activationConditionsMet()) {
               this.stopStraightLine();
               return;
            }

            this.suppressVanillaInputs();
            RightClickDelayAccessor.clear(this.minecraft);
            this.processCurrentSample(1.0F, true);
         }

      }
   }

   @SubscribeEvent
   public void onPlayerTick(PlayerTickEvent var1) {
      if (var1.phase == Phase.START && var1.player == this.minecraft.thePlayer && this.active) {
         if (!this.activationConditionsMet()) {
            this.stopStraightLine();
         } else {
            this.suppressVanillaInputs();
            RightClickDelayAccessor.clear(this.minecraft);
            this.consumePendingPlacement();
         }
      }
   }

   @SubscribeEvent
   public void onRenderTick(RenderTickEvent var1) {
      if (var1.phase == Phase.END && this.active) {
         if (!this.activationConditionsMet()) {
            this.stopStraightLine();
         } else {
            this.suppressVanillaInputs();
            RightClickDelayAccessor.clear(this.minecraft);
            this.processCurrentSample(var1.renderTickTime, true);
         }
      }
   }

   @SubscribeEvent
   public void onMouse(MouseEvent var1) {
      if (var1.button == 0 || var1.button == 1) {
         if (var1.button == 0) {
            this.physicalLeftDown = var1.buttonstate;
         } else {
            this.physicalRightDown = var1.buttonstate;
         }

         if (this.active && !this.activationConditionsMet()) {
            this.stopStraightLine();
         } else {
            if (!this.active && var1.buttonstate && this.activationConditionsMet()) {
               this.startStraightLine();
               this.processCurrentSample(1.0F, true);
            }

            if (this.active && var1.isCancelable()) {
               var1.setCanceled(true);
            }

         }
      }
   }

   @SubscribeEvent
   public void onPlayerInteract(PlayerInteractEvent var1) {
      if (this.shouldCancelPlayerAction(var1.entityPlayer == null ? false : var1.entityPlayer.worldObj.isRemote)) {
         var1.setCanceled(true);
      }

   }

   @SubscribeEvent
   public void onAttackEntity(AttackEntityEvent var1) {
      if (this.shouldCancelPlayerAction(var1.entityPlayer == null ? false : var1.entityPlayer.worldObj.isRemote)) {
         var1.setCanceled(true);
      }

   }

   @SubscribeEvent
   public void onEntityInteract(EntityInteractEvent var1) {
      if (this.shouldCancelPlayerAction(var1.entityPlayer == null ? false : var1.entityPlayer.worldObj.isRemote)) {
         var1.setCanceled(true);
      }

   }

   @SubscribeEvent
   public void onWorldUnload(Unload var1) {
      if (this.minecraft.theWorld == var1.world) {
         this.stopStraightLine();
      }

   }

   @SubscribeEvent
   public void onJoinWorld(EntityJoinWorldEvent var1) {
      if (var1.entity == this.minecraft.thePlayer) {
         this.stopStraightLine();
      }

   }

   private void processCurrentSample(float var1, boolean var2) {
      this.processCurrentSample(var1, var2, this.minecraft.objectMouseOver);
   }

   private void processCurrentSample(float var1, boolean var2, MovingObjectPosition var3) {
      if (this.active && this.minecraft.thePlayer != null && this.minecraft.theWorld != null) {
         if (this.xFace == null) {
            this.acquireFirstFace(var3);
            if (this.xFace == null) {
               return;
            }
         }

         if (this.slDirection == null || this.startBlock == null) {
            if (!this.requiredXPlacementPending) {
               this.detectAndResolveFirstEdge(var1, var3);
            }

            if (this.requiredXPlacementPending) {
               if (var2) {
                  this.captureRequiredXPlacement(var3);
                  this.captureVirtualLookahead(var1);
               }

               return;
            }
         }

         if (var2 && this.slDirection != null && this.startBlock != null) {
            this.captureNextRayBlockIfPossible(var3);
            this.captureVirtualLookahead(var1);
         }

      }
   }

   private boolean shouldCancelPlayerAction(boolean var1) {
      return var1 && this.active && !this.internalPlacement;
   }

   private boolean activationConditionsMet() {
      if (this.minecraft.thePlayer != null && this.minecraft.theWorld != null && this.minecraft.playerController != null) {
         if (this.minecraft.inGameHasFocus && this.minecraft.currentScreen == null) {
            return (this.physicalLeftDown || Mouse.isButtonDown(0)) && (this.physicalRightDown || Mouse.isButtonDown(1)) ? isHeldFullBlock(this.minecraft.thePlayer.getHeldItem()) : false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean isHeldFullBlock(ItemStack var0) {
      if (var0 != null && var0.stackSize > 0 && var0.getItem() instanceof ItemBlock) {
         Block var1 = ((ItemBlock)var0.getItem()).getBlock();
         return var1 != null && var1.isFullCube();
      } else {
         return false;
      }
   }

   private void startStraightLine() {
      this.clearHistory();
      this.active = true;
      RightClickDelayAccessor.restore(this.minecraft);
      this.suppressVanillaInputs();
      this.acquireFirstFace();
   }

   private void stopStraightLine() {
      boolean var1 = this.active;
      this.active = false;
      this.internalPlacement = false;
      this.clearHistory();
      RightClickDelayAccessor.restore(this.minecraft);
      if (var1) {
         this.restorePhysicalMouseBindings();
      }

   }

   private void clearHistory() {
      this.xFace = null;
      this.yBlock = null;
      this.zFace = null;
      this.slDirection = null;
      this.startBlock = null;
      this.hasPreviousProjection = false;
      this.previousU = 0.0D;
      this.previousV = 0.0D;
      this.previousStepU = 0.0D;
      this.previousStepV = 0.0D;
      this.requiredXPlacementPending = false;
      this.requiredXStartBlock = null;
      this.lastPlacementClientTick = Long.MIN_VALUE;
      this.requiredPendingPlacement = null;
      this.pendingPlacements.clear();
   }

   private void suppressVanillaInputs() {
      if (this.minecraft.gameSettings != null) {
         KeyBinding.setKeyBindState(this.minecraft.gameSettings.keyBindAttack.getKeyCode(), false);
         KeyBinding.setKeyBindState(this.minecraft.gameSettings.keyBindUseItem.getKeyCode(), false);
         if (this.minecraft.playerController != null) {
            this.minecraft.playerController.resetBlockRemoving();
         }

      }
   }

   private void restorePhysicalMouseBindings() {
      if (this.minecraft.gameSettings != null && this.minecraft.currentScreen == null) {
         KeyBinding.setKeyBindState(this.minecraft.gameSettings.keyBindAttack.getKeyCode(), Mouse.isButtonDown(0));
         KeyBinding.setKeyBindState(this.minecraft.gameSettings.keyBindUseItem.getKeyCode(), Mouse.isButtonDown(1));
      }
   }

   private void acquireFirstFace() {
      this.acquireFirstFace(this.minecraft.objectMouseOver);
   }

   private void acquireFirstFace(MovingObjectPosition var1) {
      if (isUsableBlockHit(var1)) {
         BlockPos var2 = var1.getBlockPos();
         IBlockState var3 = this.minecraft.theWorld.getBlockState(var2);
         Block var4 = var3.getBlock();
         if (!var4.isReplaceable(this.minecraft.theWorld, var2)) {
            List var5 = GeometryUtil.collisionBoxes(this.minecraft.theWorld, var2);
            if (!var5.isEmpty()) {
               FacePatch var6 = GeometryUtil.resolveHitFace(this.minecraft.theWorld, var2, var1.sideHit, var1.hitVec);
               if (var6 != null) {
                  this.xFace = var6;
                  this.yBlock = var2;
                  this.previousU = var6.getU(var1.hitVec);
                  this.previousV = var6.getV(var1.hitVec);
                  this.previousStepU = 0.0D;
                  this.previousStepV = 0.0D;
                  this.hasPreviousProjection = true;
               }
            }
         }
      }
   }

   private void detectAndResolveFirstEdge(float var1, MovingObjectPosition var2) {
      Vec3 var4 = sameExactFaceHit(var2, this.yBlock, this.xFace) ? var2.hitVec : GeometryUtil.intersectInfiniteLookRay(this.minecraft.thePlayer, this.xFace, var1);
      if (var4 == null) {
         this.hasPreviousProjection = false;
         this.previousStepU = 0.0D;
         this.previousStepV = 0.0D;
      } else {
         double var5 = this.xFace.getU(var4);
         double var7 = this.xFace.getV(var4);
         if (!this.hasPreviousProjection) {
            this.previousU = var5;
            this.previousV = var7;
            this.previousStepU = 0.0D;
            this.previousStepV = 0.0D;
            this.hasPreviousProjection = true;
         } else {
            double var9 = var5 - this.previousU;
            double var11 = var7 - this.previousV;
            EdgeCrossing var13 = GeometryUtil.detectFirstEdgeApproach(this.xFace, this.previousU, this.previousV, var5, var7, this.previousStepU, this.previousStepV);
            this.previousU = var5;
            this.previousV = var7;
            this.previousStepU = var9;
            this.previousStepV = var11;
            if (var13 != null) {
               EnumFacing var14 = var13.getAdjacentSide();
               this.zFace = this.xFace.adjacentFace(var14);
               Vec3 var15 = this.minecraft.thePlayer.getPositionEyes(var1);
               List var16 = GeometryUtil.collisionBoxes(this.minecraft.theWorld, this.yBlock);
               boolean var3 = GeometryUtil.eyeFacesOutward(var15, this.zFace) && !GeometryUtil.isFullyCoveredBySameBlock(this.zFace, var16);
               if (!var3) {
                  this.slDirection = this.xFace.getSide();
                  this.startBlock = this.yBlock;
               } else {
                  this.slDirection = var14;
                  boolean var18 = GeometryUtil.isFullyCoveredByAdjacentBlock(this.minecraft.theWorld, this.yBlock, this.zFace);
                  if (!var18) {
                     this.startBlock = this.yBlock;
                  } else {
                     this.requiredXPlacementPending = true;
                     this.requiredXStartBlock = this.vanillaCandidatePosition(this.yBlock, this.xFace.getSide());
                     this.pendingPlacements.clear();
                  }
               }
            }
         }
      }
   }

   private void captureRequiredXPlacement(MovingObjectPosition var1) {
      if (sameExactFaceHit(var1, this.yBlock, this.xFace)) {
         this.capturePlacement(var1, this.requiredXStartBlock, true);
      }
   }

   private void captureNextRayBlockIfPossible(MovingObjectPosition var1) {
      if (this.slDirection != null && this.startBlock != null && isUsableBlockHit(var1)) {
         BlockPos var2 = var1.getBlockPos();
         if (!GeometryUtil.collisionBoxes(this.minecraft.theWorld, var2).isEmpty()) {
            BlockPos var3 = this.vanillaCandidatePosition(var2, var1.sideHit);
            if (GeometryUtil.isOnPositiveAxisRay(this.startBlock, this.slDirection, var3)) {
               this.capturePlacement(var1, var3, false);
            }
         }
      }
   }

   private BlockPos vanillaCandidatePosition(BlockPos var1, EnumFacing var2) {
      Block var3 = this.minecraft.theWorld.getBlockState(var1).getBlock();
      return var3.isReplaceable(this.minecraft.theWorld, var1) ? var1 : var1.offset(var2);
   }

   private void capturePlacement(MovingObjectPosition var1, BlockPos var2, boolean var3) {
      StraightLineController.PendingPlacement var4 = this.createPlacement(var1, var2, var3);
      if (var4 != null) {
         if (var3) {
            this.requiredPendingPlacement = var4;
         } else if (!this.requiredXPlacementPending) {
            this.enqueueNormalPlacement(var4);
         }
      }
   }

   private StraightLineController.PendingPlacement createPlacement(MovingObjectPosition var1, BlockPos var2, boolean var3) {
      if (var2 != null && var2.getY() >= 0 && var2.getY() < 256 && isUsableBlockHit(var1)) {
         BlockPos var5 = var1.getBlockPos();
         EnumFacing var4;
         return !var2.equals(this.vanillaCandidatePosition(var5, var4 = var1.sideHit)) ? null : new StraightLineController.PendingPlacement(var5, var4, var1.hitVec, var2, var3, this.currentClientTick, false);
      } else {
         return null;
      }
   }

   private void enqueueNormalPlacement(StraightLineController.PendingPlacement var1) {
      for(int var2 = 0; var2 < this.pendingPlacements.size(); ++var2) {
         StraightLineController.PendingPlacement var3 = (StraightLineController.PendingPlacement)this.pendingPlacements.get(var2);
         if (var3.expectedCandidate.equals(var1.expectedCandidate)) {
            if (!var3.virtualLookahead && var1.virtualLookahead && this.currentClientTick - var3.capturedClientTick <= 1L) {
               return;
            }

            this.pendingPlacements.set(var2, var1);
            this.sortPendingPlacements();
            return;
         }
      }

      this.pendingPlacements.add(var1);
      this.sortPendingPlacements();
      if (this.pendingPlacements.size() > 3) {
         this.pendingPlacements.remove(this.pendingPlacements.size() - 1);
      }

   }

   private void captureVirtualLookahead(float var1) {
      if (this.slDirection != null) {
         StraightLineController.PendingPlacement var2 = this.lookaheadAnchor();
         if (var2 != null) {
            Block var3 = this.minecraft.theWorld.getBlockState(var2.supportPos).getBlock();
            if (!var3.isReplaceable(this.minecraft.theWorld, var2.supportPos)) {
               Block var4 = this.minecraft.theWorld.getBlockState(var2.expectedCandidate).getBlock();
               if (var4.isReplaceable(this.minecraft.theWorld, var2.expectedCandidate)) {
                  BlockPos var5 = var2.expectedCandidate.offset(this.slDirection);
                  if (var5.getY() >= 0 && var5.getY() < 256) {
                     Block var6 = this.minecraft.theWorld.getBlockState(var5).getBlock();
                     if (var6.isReplaceable(this.minecraft.theWorld, var5)) {
                        AxisAlignedBB var7 = new AxisAlignedBB((double)var2.expectedCandidate.getX(), (double)var2.expectedCandidate.getY(), (double)var2.expectedCandidate.getZ(), (double)var2.expectedCandidate.getX() + 1.0D, (double)var2.expectedCandidate.getY() + 1.0D, (double)var2.expectedCandidate.getZ() + 1.0D);
                        FacePatch var8 = FacePatch.fromBox(var7, this.slDirection);
                        Vec3 var9 = this.minecraft.thePlayer.getPositionEyes(var1);
                        if (GeometryUtil.eyeFacesOutward(var9, var8)) {
                           Vec3 var10 = GeometryUtil.intersectInfiniteLookRay(this.minecraft.thePlayer, var8, var1);
                           if (var10 != null && GeometryUtil.isPointOnFace(var8, var10)) {
                              var10 = var8.pointFromUV(var8.clampU(var8.getU(var10), 1.0E-5D), var8.clampV(var8.getV(var10), 1.0E-5D));
                              if (this.isWithinBlockReach(var10, var1)) {
                                 this.enqueueNormalPlacement(new StraightLineController.PendingPlacement(var2.expectedCandidate, this.slDirection, var10, var5, false, this.currentClientTick, true));
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private StraightLineController.PendingPlacement lookaheadAnchor() {
      if (this.requiredXPlacementPending && this.requiredPendingPlacement != null) {
         return this.requiredPendingPlacement;
      } else {
         Iterator var1 = this.pendingPlacements.iterator();

         StraightLineController.PendingPlacement var2;
         Block var3;
         Block var4;
         do {
            if (!var1.hasNext()) {
               return null;
            }

            var2 = (StraightLineController.PendingPlacement)var1.next();
            var3 = this.minecraft.theWorld.getBlockState(var2.supportPos).getBlock();
            var4 = this.minecraft.theWorld.getBlockState(var2.expectedCandidate).getBlock();
         } while(var3.isReplaceable(this.minecraft.theWorld, var2.supportPos) || !var4.isReplaceable(this.minecraft.theWorld, var2.expectedCandidate));

         return var2;
      }
   }

   private void sortPendingPlacements() {
      for(int var1 = 1; var1 < this.pendingPlacements.size(); ++var1) {
         StraightLineController.PendingPlacement var2 = (StraightLineController.PendingPlacement)this.pendingPlacements.get(var1);

         int var3;
         for(var3 = var1 - 1; var3 >= 0 && this.comparePendingPlacements(var2, (StraightLineController.PendingPlacement)this.pendingPlacements.get(var3)) < 0; --var3) {
            this.pendingPlacements.set(var3 + 1, (StraightLineController.PendingPlacement)this.pendingPlacements.get(var3));
         }

         this.pendingPlacements.set(var3 + 1, var2);
      }

   }

   private int comparePendingPlacements(StraightLineController.PendingPlacement var1, StraightLineController.PendingPlacement var2) {
      int var3 = this.rayDistance(var1.expectedCandidate);
      int var4 = this.rayDistance(var2.expectedCandidate);
      if (var3 != var4) {
         return var3 < var4 ? -1 : 1;
      } else if (var1.capturedClientTick != var2.capturedClientTick) {
         return var1.capturedClientTick > var2.capturedClientTick ? -1 : 1;
      } else {
         return 0;
      }
   }

   private int rayDistance(BlockPos var1) {
      if (this.startBlock != null && this.slDirection != null && var1 != null) {
         switch(this.slDirection) {
         case WEST:
            return this.startBlock.getX() - var1.getX();
         case EAST:
            return var1.getX() - this.startBlock.getX();
         case DOWN:
            return this.startBlock.getY() - var1.getY();
         case UP:
            return var1.getY() - this.startBlock.getY();
         case NORTH:
            return this.startBlock.getZ() - var1.getZ();
         case SOUTH:
            return var1.getZ() - this.startBlock.getZ();
         default:
            return Integer.MAX_VALUE;
         }
      } else {
         return Integer.MAX_VALUE;
      }
   }

   private void consumePendingPlacement() {
      if (this.lastPlacementClientTick != this.currentClientTick) {
         StraightLineController.PendingPlacement var1;
         if (this.requiredXPlacementPending) {
            this.captureRequiredXPlacement(this.minecraft.objectMouseOver);
            var1 = this.requiredPendingPlacement;
            this.requiredPendingPlacement = null;
            if (var1 != null) {
               this.tryPlacement(var1);
            }

         } else if (this.slDirection != null && this.startBlock != null) {
            this.captureNextRayBlockIfPossible(this.minecraft.objectMouseOver);

            int var2;
            do {
               if (this.pendingPlacements.isEmpty()) {
                  var1 = this.currentNormalPlacement(this.minecraft.objectMouseOver);
                  if (var1 != null) {
                     this.tryPlacement(var1);
                  }

                  return;
               }

               var1 = (StraightLineController.PendingPlacement)this.pendingPlacements.remove(0);
               var2 = this.tryPlacement(var1);
            } while(var2 == 0);

         }
      }
   }

   private StraightLineController.PendingPlacement currentNormalPlacement(MovingObjectPosition var1) {
      if (this.slDirection != null && this.startBlock != null && isUsableBlockHit(var1)) {
         BlockPos var2 = var1.getBlockPos();
         if (GeometryUtil.collisionBoxes(this.minecraft.theWorld, var2).isEmpty()) {
            return null;
         } else {
            BlockPos var3 = this.vanillaCandidatePosition(var2, var1.sideHit);
            return !GeometryUtil.isOnPositiveAxisRay(this.startBlock, this.slDirection, var3) ? null : this.createPlacement(var1, var3, false);
         }
      } else {
         return null;
      }
   }

   private int tryPlacement(StraightLineController.PendingPlacement var1) {
      if (var1 != null && this.lastPlacementClientTick != this.currentClientTick) {
         long var2 = this.currentClientTick - var1.capturedClientTick;
         if (var2 >= 0L && var2 <= 1L) {
            ItemStack var4 = this.minecraft.thePlayer.getHeldItem();
            if (isHeldFullBlock(var4) && var4.getItem() instanceof ItemBlock) {
               Block var5 = this.minecraft.theWorld.getBlockState(var1.supportPos).getBlock();
               if (var5.isReplaceable(this.minecraft.theWorld, var1.supportPos)) {
                  return 0;
               } else {
                  Block var6 = this.minecraft.theWorld.getBlockState(var1.expectedCandidate).getBlock();
                  if (!var6.isReplaceable(this.minecraft.theWorld, var1.expectedCandidate)) {
                     return 0;
                  } else {
                     AxisAlignedBB var7 = new AxisAlignedBB((double)var1.expectedCandidate.getX(), (double)var1.expectedCandidate.getY(), (double)var1.expectedCandidate.getZ(), (double)var1.expectedCandidate.getX() + 1.0D, (double)var1.expectedCandidate.getY() + 1.0D, (double)var1.expectedCandidate.getZ() + 1.0D);
                     AxisAlignedBB var8 = this.minecraft.thePlayer.getEntityBoundingBox();
                     if (var8 != null && var8.intersectsWith(var7)) {
                        return 0;
                     } else {
                        MovingObjectPosition var9 = this.minecraft.objectMouseOver;
                        Vec3 var10 = var1.hitVec;
                        if (var1.virtualLookahead && !this.isWithinBlockReach(var10, 1.0F)) {
                           return 0;
                        } else {
                           if (isUsableBlockHit(var9) && var1.supportPos.equals(var9.getBlockPos()) && var1.face == var9.sideHit && var1.expectedCandidate.equals(this.vanillaCandidatePosition(var9.getBlockPos(), var9.sideHit))) {
                              var10 = var9.hitVec;
                           } else if (!this.isPendingDirectionStillValid(var1)) {
                              return 0;
                           }

                           ItemBlock var11 = (ItemBlock)var4.getItem();
                           if (!this.canUseRightClickWithoutActivating(var5)) {
                              return 0;
                           } else if (!var11.canPlaceBlockOnSide(this.minecraft.theWorld, var1.supportPos, var1.face, this.minecraft.thePlayer, var4)) {
                              return 0;
                           } else {
                              RightClickDelayAccessor.clear(this.minecraft);
                              this.lastPlacementClientTick = this.currentClientTick;
                              boolean var12 = this.invokeVanillaRightClick(var4, var1.supportPos, var1.face, var10);
                              if (!var12) {
                                 return 1;
                              } else {
                                 this.invokeSwingItem();
                                 this.refreshDirectLookahead(var1.expectedCandidate);
                                 if (var1.requiredStart) {
                                    this.startBlock = var1.expectedCandidate;
                                    this.requiredXPlacementPending = false;
                                    this.requiredXStartBlock = null;
                                    this.requiredPendingPlacement = null;
                                    this.retainDirectLookahead(var1.expectedCandidate);
                                 }

                                 return 2;
                              }
                           }
                        }
                     }
                  }
               }
            } else {
               return 0;
            }
         } else {
            return 0;
         }
      } else {
         return 0;
      }
   }

   private void refreshDirectLookahead(BlockPos var1) {
      for(int var2 = 0; var2 < this.pendingPlacements.size(); ++var2) {
         StraightLineController.PendingPlacement var3 = (StraightLineController.PendingPlacement)this.pendingPlacements.get(var2);
         if (var3.virtualLookahead && var3.supportPos.equals(var1)) {
            this.pendingPlacements.set(var2, new StraightLineController.PendingPlacement(var3.supportPos, var3.face, var3.hitVec, var3.expectedCandidate, var3.requiredStart, this.currentClientTick, true));
         }
      }

      this.sortPendingPlacements();
   }

   private void retainDirectLookahead(BlockPos var1) {
      for(int var2 = this.pendingPlacements.size() - 1; var2 >= 0; --var2) {
         StraightLineController.PendingPlacement var3 = (StraightLineController.PendingPlacement)this.pendingPlacements.get(var2);
         if (!var3.virtualLookahead || !var3.supportPos.equals(var1)) {
            this.pendingPlacements.remove(var2);
         }
      }

      this.refreshDirectLookahead(var1);
   }

   private boolean isWithinBlockReach(Vec3 var1, float var2) {
      if (var1 == null) {
         return false;
      } else {
         Vec3 var3 = this.minecraft.thePlayer.getPositionEyes(var2);
         double var4 = var1.xCoord - var3.xCoord;
         double var6 = var1.yCoord - var3.yCoord;
         double var8 = var1.zCoord - var3.zCoord;
         double var10 = this.currentBlockReachDistance() + 1.0E-4D;
         return var4 * var4 + var6 * var6 + var8 * var8 <= var10 * var10;
      }
   }

   private double currentBlockReachDistance() {
      this.resolveReflectionMethods();
      if (this.blockReachDistanceMethod != null) {
         try {
            Object var1 = this.blockReachDistanceMethod.invoke(this.minecraft.playerController);
            if (var1 instanceof Number) {
               return ((Number)var1).doubleValue();
            }
         } catch (ReflectiveOperationException var2) {
         }
      }

      return 4.5D;
   }

   private boolean isPendingDirectionStillValid(StraightLineController.PendingPlacement var1) {
      Vec3 var10 = this.minecraft.thePlayer.getPositionEyes(1.0F);
      Vec3 var11 = this.minecraft.thePlayer.getLook(1.0F);
      double var12 = (double)var1.supportPos.getX() + 0.5D + (double)var1.face.getFrontOffsetX() * 0.5D;
      double var14 = var12 - var10.xCoord;
      double var4;
      double var8;
      double var16 = Math.sqrt(var14 * var14 + (var8 = (double)var1.supportPos.getY() + 0.5D + (double)var1.face.getFrontOffsetY() * 0.5D - var10.yCoord) * var8 + (var4 = (double)var1.supportPos.getZ() + 0.5D + (double)var1.face.getFrontOffsetZ() * 0.5D - var10.zCoord) * var4);
      if (var16 <= 1.0E-9D) {
         return true;
      } else {
         double var18 = var11.xCoord * (var14 / var16) + var11.yCoord * (var8 / var16) + var11.zCoord * (var4 / var16);
         return var18 >= 0.5D;
      }
   }

   private boolean invokeVanillaRightClick(ItemStack var1, BlockPos var2, EnumFacing var3, Vec3 var4) {
      this.resolveReflectionMethods();
      if (this.onPlayerRightClickMethod == null) {
         return false;
      } else {
         this.internalPlacement = true;

         boolean var7;
         try {
            boolean var6;
            try {
               Object var5 = this.onPlayerRightClickMethod.invoke(this.minecraft.playerController, this.minecraft.thePlayer, this.minecraft.theWorld, var1, var2, var3, var4);
               var6 = Boolean.TRUE.equals(var5);
               var7 = var6;
               return var7;
            } catch (ReflectiveOperationException var11) {
               var6 = false;
               var7 = var6;
            }
         } finally {
            this.internalPlacement = false;
         }

         return var7;
      }
   }

   private void invokeSwingItem() {
      this.resolveReflectionMethods();
      if (this.swingItemMethod != null && this.minecraft.thePlayer != null) {
         try {
            this.swingItemMethod.invoke(this.minecraft.thePlayer);
         } catch (ReflectiveOperationException var2) {
         }

      }
   }

   private void resolveReflectionMethods() {
      if (!this.reflectionResolved) {
         this.reflectionResolved = true;
         Method[] var2;
         int var3;
         int var4;
         Method var5;
         String var6;
         if (this.minecraft.playerController != null) {
            Method[] var1 = this.minecraft.playerController.getClass().getMethods();
            var2 = var1;
            var3 = var1.length;

            for(var4 = 0; var4 < var3; ++var4) {
               var5 = var2[var4];
               var6 = var5.getName();
               if (("onPlayerRightClick".equals(var6) || "func_178890_a".equals(var6)) && var5.getParameterTypes().length == 6) {
                  var5.setAccessible(true);
                  this.onPlayerRightClickMethod = var5;
               }

               if (("getBlockReachDistance".equals(var6) || "func_78757_d".equals(var6)) && var5.getParameterTypes().length == 0) {
                  var5.setAccessible(true);
                  this.blockReachDistanceMethod = var5;
               }
            }
         }

         if (this.minecraft.thePlayer != null) {
            for(Class var7 = this.minecraft.thePlayer.getClass(); var7 != null && this.swingItemMethod == null; var7 = var7.getSuperclass()) {
               var2 = var7.getDeclaredMethods();
               var3 = var2.length;

               for(var4 = 0; var4 < var3; ++var4) {
                  var5 = var2[var4];
                  var6 = var5.getName();
                  if (("swingItem".equals(var6) || "func_71038_i".equals(var6)) && var5.getParameterTypes().length == 0) {
                     var5.setAccessible(true);
                     this.swingItemMethod = var5;
                     break;
                  }
               }
            }
         }

      }
   }

   private boolean canUseRightClickWithoutActivating(Block var1) {
      if (this.minecraft.thePlayer.isSneaking()) {
         return true;
      } else {
         Class var2 = var1.getClass();
         Boolean var3 = (Boolean)this.nonActivatingBlockClasses.get(var2);
         if (var3 != null) {
            return var3;
         } else {
            for(Class var4 = var2; var4 != null && Block.class.isAssignableFrom(var4); var4 = var4.getSuperclass()) {
               Method[] var6 = var4.getDeclaredMethods();
               int var7 = var6.length;

               for(int var8 = 0; var8 < var7; ++var8) {
                  Method var9 = var6[var8];
                  String var10 = var9.getName();
                  if ("onBlockActivated".equals(var10) || "func_180639_a".equals(var10)) {
                     boolean var11 = var4 == Block.class;
                     this.nonActivatingBlockClasses.put(var2, var11);
                     return var11;
                  }
               }
            }

            this.nonActivatingBlockClasses.put(var2, Boolean.FALSE);
            return false;
         }
      }
   }

   private static boolean isUsableBlockHit(MovingObjectPosition var0) {
      return var0 != null && var0.typeOfHit == MovingObjectType.BLOCK && var0.getBlockPos() != null && var0.sideHit != null && var0.hitVec != null;
   }

   private static boolean sameExactFaceHit(MovingObjectPosition var0, BlockPos var1, FacePatch var2) {
      return isUsableBlockHit(var0) && var1.equals(var0.getBlockPos()) && var2.getSide() == var0.sideHit && GeometryUtil.isPointOnFace(var2, var0.hitVec);
   }

   private static final class PendingPlacement {
      private final BlockPos supportPos;
      private final EnumFacing face;
      private final Vec3 hitVec;
      private final BlockPos expectedCandidate;
      private final boolean requiredStart;
      private final long capturedClientTick;
      private final boolean virtualLookahead;

      private PendingPlacement(BlockPos var1, EnumFacing var2, Vec3 var3, BlockPos var4, boolean var5, long var6, boolean var8) {
         this.supportPos = var1;
         this.face = var2;
         this.hitVec = var3;
         this.expectedCandidate = var4;
         this.requiredStart = var5;
         this.capturedClientTick = var6;
         this.virtualLookahead = var8;
      }
   }
}
