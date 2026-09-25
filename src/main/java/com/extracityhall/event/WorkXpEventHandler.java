package com.extracityhall.event;

import com.extracityhall.ExtraCityHall;
import com.extracityhall.config.ExhConfig;
import com.extracityhall.util.SecretaryXpMultiplier;
import com.extracityhall.util.WorkXpCurve;
import com.extracityhall.util.XpProbe;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuildingWorkerModule;
import com.minecolonies.api.colony.buildings.modules.IAssignsJob;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenSkillHandler;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 服务端工作曲线结算：每 {@code ExhConfig.settleIntervalTicks} tick 扫描一次全殖民地，
 * 对"正在工作"的市民按 {@link WorkXpCurve} 结算一次主技能经验。
 *
 * <p>挂点选择：{@link TickEvent.ServerTickEvent} 是全局单一服务端入口；
 * 不用 {@code LevelTickEvent}（每个维度每 tick 都触发，计数器会跨维度重复累加）。
 *
 * <p><b>不乘书记系数 a</b>：结算值直接交给
 * {@code citizen.getCitizenSkillHandler().addXpToSkill(...)}，由既有的
 * {@link com.extracityhall.util.SecretarySkillHandlerWrapper} 统一乘书记倍率，
 * 保证实际生效 {@code a * b * ln(L/Lmax + 1)} 且 a 只乘一次。
 *
 * <p>性能：计数未达阈值时立即返回，不做任何遍历；每 10 秒一次的
 * O(殖民数 × 市民数) 遍历量级极小。
 */
public final class WorkXpEventHandler
{
    /** 距上次结算经过的 tick 数。静态即可：服务端只有一个 tick 循环。 */
    private static int tickCounter = 0;

    private WorkXpEventHandler()
    {
    }

    /**
     * 服务端 tick：累计到间隔后结算一轮。
     *
     * @param event 服务端 tick 事件
     */
    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        XpProbe.advanceTick();

        if (++tickCounter < ExhConfig.settleIntervalTicks)
        {
            return;
        }
        tickCounter = 0;

        settleAllColonies();
    }

    /** 遍历所有维度的所有殖民地（{@code getAllColonies()} 内部已覆盖全部维度）并逐个市民结算。 */
    private static void settleAllColonies()
    {
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            if (colony == null || colony.getCitizenManager() == null)
            {
                continue;
            }

            for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
            {
                // 单个市民异常不应中断整轮扫描，也不应每 tick 刷屏；仅在异常分支记录。
                try
                {
                    settle(citizen);
                }
                catch (final Exception e)
                {
                    ExtraCityHall.LOGGER.error("工作曲线结算异常，已跳过该市民", e);
                }
            }
        }
    }

    /**
     * 为单个市民结算一次工作经验。
     *
     * @param citizen 市民数据
     */
    private static void settle(final ICitizenData citizen)
    {
        if (citizen == null || citizen.getJob() == null)
        {
            return;
        }

        // 实体不在世界（例如夜里睡觉被反注册）时 AI 不 tick，状态名会停在旧值，直接跳过。
        if (citizen.getEntity().isEmpty())
        {
            return;
        }

        final IJob<?> job = citizen.getJob();
        if (!isWorking(job))
        {
            return;
        }

        // 主技能取自「职业所绑定的工人模块」，而非逐个职业硬编码。
        // 注意：MineColonies 1.20.1 的 IBuildingWorker 接口已废弃——1.1.605 全库无任何实现类，
        // 主/次技能改由 WorkerBuildingModule 一类模块持有，故必须走 job.getWorkModule()。
        final IAssignsJob module = job.getWorkModule();
        if (!(module instanceof IBuildingWorkerModule workerModule))
        {
            return;
        }
        final Skill skill = workerModule.getPrimarySkill();
        if (skill == null)
        {
            return;
        }

        final ICitizenSkillHandler skills = citizen.getCitizenSkillHandler();
        final double lmax = WorkXpCurve.lmaxOf(citizen);
        final int level = skills.getLevel(skill);

        if (!WorkXpCurve.shouldGrant(level, lmax))
        {
            return; // L<=0 死区，或已达 75%*Lmax 截断
        }

        final double rawXp = WorkXpCurve.xpFor(level, lmax);
        // 注意：此处不要把 rawXp 再乘 SecretaryXpMultiplier.getMultiplier()，否则 a 会被平方；
        // 倍率由 SecretarySkillHandlerWrapper 在 addXpToSkill 内部施加，这里仅读取用于探针展示。
        skills.addXpToSkill(skill, rawXp, citizen);

        final IColony colony = citizen.getColony();
        XpProbe.record(
            colony == null ? -1 : colony.getID(),
            citizen.getName(),
            skill,
            level,
            skills.getLevel(skill),
            rawXp,
            colony == null ? 1.0 : SecretaryXpMultiplier.getMultiplier(colony));
    }

    /**
     * 该职业此刻是否在工作中。
     *
     * <p>判据是 AI 状态名而非 {@code renderMetadata}：AI 每 20 tick 会把状态名写进
     * {@code job.nameTag}（{@code AbstractEntityAIBasic.updateVisualState}），只要不是 IDLE
     * 就算在干活——与 MineColonies 自己对 renderMetadata 的判定口径相同，但对渔夫/伐木工/
     * 快递员（覆写后从不写 "working"）以及 crafting 系（只在 CRAFT 状态写）同样有效。
     *
     * <p>空串表示 AI 尚未跑过一轮（刚生成/未初始化），按未工作处理。
     *
     * @param job 市民职业
     * @return 是否在工作中
     */
    private static boolean isWorking(@NotNull final IJob<?> job)
    {
        final String state = job.getNameTagDescription();
        return state != null && !state.isEmpty() && !WorkXpCurve.IDLE_STATE.equals(state);
    }
}
