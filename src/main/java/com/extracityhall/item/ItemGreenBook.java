package com.extracityhall.item;

import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 绿皮书 (GreenBook)。
 *
 * 交互逻辑(参考 core/items/ItemClipboard):
 * - 右键殖民地任意方块: 将 colonyId + dimension 写入物品 NBT(双端写入,
 *   服务端用于持久, 客户端用于后续即时开窗)
 * - 空中右键: 客户端读取 NBT 并通过 IColonyManager#getColonyView 获取
 *   IColonyView, 再调用 {@code IColonyView#getTownHall().openGui(false)}
 *   打开原版市政厅管理 GUI; 原版窗口会被 {@code GreenBookEvents} 统一拦截,
 *   自动注入「就业」书签, 无需任何子类窗口。
 *
 * 另见 {@link com.extracityhall.event.GreenBookEvents}: 手持普通书
 * (minecraft:book) 右键市政厅方块时, 消耗书并转化为本物品。
 */
public class ItemGreenBook extends Item {
    public static final String TAG_COLONY_ID = "colony_id";
    public static final String TAG_DIMENSION = "dimension";

    public ItemGreenBook(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        IColony colony = MinecoloniesAPIProxy.getInstance()
            .getColonyManager()
            .getColonyByPosFromWorld(level, pos);
        if (colony != null) {
            CompoundTag tag = context.getItemInHand().getOrCreateTag();
            tag.putInt(TAG_COLONY_ID, colony.getID());
            tag.putString(TAG_DIMENSION, colony.getDimension().location().toString());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            return InteractionResultHolder.pass(stack);
        }
        final CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_COLONY_ID, Tag.TAG_ANY_NUMERIC) || !tag.contains(TAG_DIMENSION)) {
            player.displayClientMessage(Component.translatable("item.extracityhall.green_book.not_bound"), false);
            return InteractionResultHolder.pass(stack);
        }
        // 维度串可能缺失/损坏（旧版本物品、被第三方改过的 NBT），直接 new ResourceLocation 会抛异常中断客户端
        final ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        if (dimensionId == null) {
            player.displayClientMessage(Component.translatable("item.extracityhall.green_book.not_bound"), false);
            return InteractionResultHolder.pass(stack);
        }
        final ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        IColonyView view = MinecoloniesAPIProxy.getInstance()
            .getColonyManager()
            .getColonyView(tag.getInt(TAG_COLONY_ID), dimension);
        if (view != null && view.hasTownHall()) {
            // 原版入口：构造 WindowMainPage，ScreenEvent.Opening 拦截后自动注入就业书签
            view.getTownHall().openGui(false);
            return InteractionResultHolder.success(stack);
        }
        player.displayClientMessage(Component.translatable("item.extracityhall.green_book.not_bound"), false);
        return InteractionResultHolder.pass(stack);
    }
}
