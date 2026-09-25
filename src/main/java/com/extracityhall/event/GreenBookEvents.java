package com.extracityhall.event;

import com.extracityhall.ModItems;
import com.extracityhall.item.ItemGreenBook;
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.blocks.huts.BlockHutTownHall;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 服务端事件处理：书籍转化。
 *
 * 手持普通书 (minecraft:book) 对市政厅方块右键时，服务端消耗书并给予
 * {@code extracityhall:green_book}，同时取消原交互（防止打开市政厅 GUI）。
 *
 * <p>注意：本类必须保持"仅通用端"——不得引用任何客户端类（含 ScreenEvent / BOScreen /
 * minecolonies client gui）。窗口侧的「就业」书签注入在 {@link ClientEvents} 中完成。
 */
public final class GreenBookEvents {
    private GreenBookEvents() {
    }

    @SubscribeEvent
    public static void onRightClickTownHall(final PlayerInteractEvent.RightClickBlock event) {
        // 仅在逻辑服务端处理: 消耗书、给予绿皮书、取消交互
        if (event.getLevel().isClientSide || event.isCanceled()) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        final Player player = event.getEntity();
        if (player == null) {
            return;
        }

        // 主手必须是普通书
        final ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (stack.getItem() != Items.BOOK) {
            return;
        }

        // 目标方块必须是市政厅方块
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof BlockHutTownHall)) {
            return;
        }

        // 通过 MineColonies API 判定该位置属于某个殖民地
        final IColony colony = MinecoloniesAPIProxy.getInstance()
            .getColonyManager()
            .getColonyByPosFromWorld(event.getLevel(), event.getPos());
        if (colony == null) {
            return;
        }

        // 取消原交互, 防止打开市政厅 GUI
        event.setCanceled(true);

        // 消耗主手书(创造模式不消耗)
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        // 给予绿皮书并预绑定当前殖民地
        final ItemStack greenBook = new ItemStack(ModItems.GREEN_BOOK.get());
        greenBook.getOrCreateTag().putInt(ItemGreenBook.TAG_COLONY_ID, colony.getID());
        greenBook.getOrCreateTag().putString(
            ItemGreenBook.TAG_DIMENSION,
            colony.getDimension().location().toString());
        if (!player.getInventory().add(greenBook)) {
            player.drop(greenBook, false);
        }

        // 播放翻书音效
        event.getLevel().playSound(
            null,
            player.blockPosition(),
            SoundEvents.BOOK_PAGE_TURN,
            SoundSource.PLAYERS,
            1.0F,
            1.0F);
    }
}
