package com.github.fndream.collectEmerald.mixin;

import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Unique
    private static int cd = 0;
    @Unique
    private static int lastCDSubTick = 0;
    @Unique
    private static int runTick = 0;

    private boolean isCovetedItem;

    @Inject(method = "setCovetedItem", at = @At(value = "HEAD"))
    private void setCovetedItem(CallbackInfo ci) {
        this.isCovetedItem = true;
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tick()V"))
    private void tick(CallbackInfo ci) {
        ItemEntity thit = (ItemEntity) (Object) this;
        if (!(thit.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (isCovetedItem) {
            if (thit.getItemAge() < -5960) {
                if (thit.getItemAge() == -5999 && thit.getCommandTags().contains("collect")) {
                    thit.tryMerge();
                    collect(thit, world);
                }
                return;
            }
        } else {
            if (thit.getItemAge() < 40) {
                if (thit.getItemAge() == 1 && thit.getCommandTags().contains("collect")) {
                    thit.tryMerge();
                    collect(thit, world);
                }
                return;
            }
        }
        if (thit.getCommandTags().contains("collectf")) {
            return;
        }
        int ticks = world.getServer().getTicks();
        if (lastCDSubTick == ticks) {
            return;
        }
        if (cd > 0) {
            cd--;
            lastCDSubTick = ticks;
            if (cd == 0) {
                runTick = world.getServer().getTicks() + 1;
            }
            return;
        }
        if (runTick != ticks) {
            cd = 9;
            return;
        }
        collect(thit, world);
    }

    @Unique
    private static void collect(ItemEntity thit, ServerWorld world) {
        List<ItemFrameEntity> noChestitemFrameList = new ArrayList<>();
        List<ItemFrameEntity> finalNoChestitemFrameList = noChestitemFrameList;
        List<ItemFrameEntity> itemFrameList = world.getEntitiesByClass(ItemFrameEntity.class, thit.getBoundingBox().expand(16), (itemFrame) -> {
                    ItemStack heldItemStack = itemFrame.getHeldItemStack();
                    if (!(heldItemStack.getItem() == Items.EMERALD)) {
                        return false;
                    }
                    BlockPos attachedPos = itemFrame.getBlockPos().offset(itemFrame.getHorizontalFacing().getOpposite());
                    BlockEntity chestBlockEntity = world.getBlockEntity(attachedPos);
                    boolean bl = chestBlockEntity instanceof ChestBlockEntity;
                    boolean bl2 = chestBlockEntity instanceof BarrelBlockEntity;
                    boolean bl3 = chestBlockEntity instanceof ShulkerBoxBlockEntity;
                    if (!bl && !bl2 && !bl3) {
                        if (!thit.getCommandTags().contains("collect")) {
                            finalNoChestitemFrameList.add(itemFrame);
                        }
                        return false;
                    }
                    Inventory inventory;
                    if (bl) {
                        inventory = ChestBlock.getInventory((ChestBlock) chestBlockEntity.getCachedState().getBlock(), chestBlockEntity.getCachedState(), chestBlockEntity.getWorld(), chestBlockEntity.getPos(), true);
                    } else {
                        inventory = ((Inventory) chestBlockEntity);
                    }
                    if (inventory == null || inventory.isEmpty()) {
                        return false;
                    }
                    for (int i = 0; i < inventory.size(); i++) {
                        ItemStack stack = inventory.getStack(i);
                        if (stack.isEmpty()) {
                            continue;
                        }
                        if (thit.getStack().getItem() == stack.getItem()) {
                            return true;
                        }
                    }
                    return false;
                }
        );
        if (itemFrameList.isEmpty() && noChestitemFrameList.isEmpty()) {
            return;
        }
        if (itemFrameList.size() > 1) {
            Collections.shuffle(itemFrameList);
        }
        if (noChestitemFrameList.size() > 1) {
            Collections.shuffle(noChestitemFrameList);
        }

        boolean hasSplit = false;
        boolean isFilter = false;
        ItemStack originalStack = thit.getStack();
        int totalCount = originalStack.getCount();
        int frameCount = noChestitemFrameList.size();
        if (frameCount == 0) {
            frameCount = itemFrameList.size();
            noChestitemFrameList = itemFrameList;
            isFilter = true;
        }
        int per = totalCount / frameCount;
        int remainder = totalCount % frameCount;

        for (int i = 0; i < frameCount; i++) {
            int amount = per + (i < remainder ? 1 : 0);
            if (amount <= 0) continue;

            ItemStack splitStack = originalStack.copy();
            splitStack.setCount(amount);
            ItemFrameEntity targetFrame = noChestitemFrameList.get(i);
            BlockPos pos = targetFrame.getBlockPos();
            ItemEntity newItem = new ItemEntity(
                    world,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    splitStack
            );

            newItem.setVelocity(Vec3d.ZERO);
            newItem.setPickupDelay(10);
            newItem.addCommandTag(isFilter ? "collectf" : "collect");
            world.spawnEntity(newItem);
            world.playSound(
                    null,
                    newItem.getX(),
                    newItem.getY(),
                    newItem.getZ(),
                    SoundEvents.ENTITY_ITEM_PICKUP,
                    SoundCategory.PLAYERS,
                    0.2F,
                    ((world.random.nextFloat() - world.random.nextFloat()) * 0.7F + 1.0F) * 2.0F
            );
            hasSplit = true;
        }
        if (hasSplit) {
            thit.discard();
        }
    }
}
