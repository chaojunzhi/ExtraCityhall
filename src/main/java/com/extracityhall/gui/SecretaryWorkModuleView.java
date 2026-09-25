package com.extracityhall.gui;

import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 市政厅「书记」工人模块视图（客户端）。
 *
 * <p>与服务端 {@link com.extracityhall.modules.SecretaryWorkModule} 对应，
 * 通过 {@code deserialize}(FriendlyByteBuf) 消费同步过来的「当前生效经验倍率」载荷，
 * 以与服务端 {@code serializeToView} 的写入保持缓冲对齐（当前 GUI 尚未展示该倍率）。
 */
public class SecretaryWorkModuleView extends WorkerBuildingModuleView {
    @Override
    public void deserialize(final FriendlyByteBuf buf) {
        super.deserialize(buf);
        buf.readDouble(); // 消费同步载荷以保持缓冲对齐
    }
}
