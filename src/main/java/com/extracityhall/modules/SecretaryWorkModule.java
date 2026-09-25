package com.extracityhall.modules;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Function;

/**
 * 市政厅「书记」工人模块（服务端）。
 *
 * <p>在 {@link WorkerBuildingModule} 基础上额外持有「当前生效经验倍率」{@code secretaryXpFactor}，
 * 该值由 {@code EntityAISecretary} 在书记 AI 在岗持书时写入、离岗时清零，并通过 MineColonies
 * 标准的模块 {@code serializeToView}/{@code deserialize}(FriendlyByteBuf) 同步通道
 * 带到客户端 {@link com.extracityhall.gui.SecretaryWorkModuleView}，使 GUI 显示的经验加成
 * 与实际生效倍率一致，避免「显示有加成但实未生效」。
 *
 * <p>该倍率为瞬时运行态，无需落盘（书记 AI 会周期性重算写入），故只在视图同步通道携带。
 */
public class SecretaryWorkModule extends WorkerBuildingModule {
    /** 当前生效倍率，缺省 1.0（无加成）；仅在书记 AI 活跃时大于 1.0 */
    private double secretaryXpFactor = 1.0;

    public SecretaryWorkModule(final JobEntry jobEntry, final Skill primary, final Skill secondary,
                                final boolean canWorkAtNight, final Function<IBuilding, Integer> maxWorkers) {
        super(jobEntry, primary, secondary, canWorkAtNight, maxWorkers);
    }

    public void setSecretaryXpFactor(final double factor) {
        this.secretaryXpFactor = factor <= 1.0 ? 1.0 : factor;
    }

    @Override
    public void serializeToView(final FriendlyByteBuf buf) {
        super.serializeToView(buf);
        buf.writeDouble(secretaryXpFactor);
    }
}
