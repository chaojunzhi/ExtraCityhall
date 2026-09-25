package com.extracityhall.util;

import com.extracityhall.data.ColonyJobConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.IBuildingWorkerModule;
import com.minecolonies.api.colony.buildings.modules.IAssignsJob;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.jobs.registry.JobEntry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 最优职业分配落地助手：把「抽取优先级 → 求解 → 调 MineColonies 指派 API」封装为可复用方法，
 * 供 {@code /exh assign} 命令与自动重排定时器共同调用。
 *
 * <p>{@code onlyUnemployed = false} 时对全体市民（含已在职者）做全局最优重排；
 * {@code true} 时只把失业市民塞进空槽，不触动在职者。
 */
public final class JobAssigner
{
    private static final Logger LOGGER = LogManager.getLogger(JobAssigner.class);

    private JobAssigner()
    {
    }

    /**
     * 建筑类型的稳定键（注册名，如 {@code minecolonies:builder}）。
     *
     * <p>注意：{@code IBuilding.getBuildingType()} 返回 {@code BuildingEntry}，其 {@code toString()} 未被重写，
     * 直接用会得到 {@code BuildingEntry@hash} 这类不稳定乱码（跨存档失效），故必须取 {@code getRegistryName()}。
     */
    public static String buildingKey(final IBuilding b)
    {
        if (b == null || b.getBuildingType() == null || b.getBuildingType().getRegistryName() == null)
        {
            return "unknown";
        }
        return b.getBuildingType().getRegistryName().toString();
    }

    /**
     * 安全取建筑内**全部**工人模块（工作位）。一栋楼可能有多个职业，
     * 如林务员小屋同时含「林务员」与「果园管理员」两个 {@link IAssignsJob} 模块。
     *
     * <p>这是区分「工人建筑」与「住宅 / 装饰类建筑」的安全方式：
     * {@code getFirstModuleOccurance} 在**找不到模块时会直接抛** {@code IllegalStateException}
     * （酒馆等住宅类建筑没有 IAssignsJob，一碰就炸）；
     * 而 {@code getModulesByType} 返回列表，找不到就是空列表，**不会抛异常**。
     *
     * @return 该建筑内所有带主/次技能的工人模块；住宅类建筑返回空列表
     */
    public static List<IAssignsJob> safeJobModules(final IBuilding b)
    {
        final List<IAssignsJob> result = new ArrayList<>();
        if (b == null)
        {
            return result;
        }
        try
        {
            final List<IAssignsJob> modules = b.getModulesByType(IAssignsJob.class);
            if (modules == null)
            {
                return result;
            }
            for (final IAssignsJob m : modules)
            {
                if (m instanceof IBuildingWorkerModule)
                {
                    result.add(m); // 只保留有主/次技能的工人模块
                }
            }
        }
        catch (final Throwable e)
        {
            LOGGER.debug("跳过建筑 {}：读取 IAssignsJob 模块失败（{}）", buildingKey(b), e.toString());
        }
        return result;
    }

    /**
     * 工作位（模块）的职业键：取 {@code JobEntry} 的注册名，如 {@code minecolonies:forester}。
     *
     * <p>必须按**模块**取键而非按建筑取键，否则同一栋楼的多个职业
     * （林务员 / 果园管理员）会共用同一个键，无法分别设置优先级与区分容量。
     */
    public static String jobKeyOf(final IAssignsJob module)
    {
        if (module == null)
        {
            return "unknown";
        }
        try
        {
            final JobEntry entry = module.getJobEntry();
            if (entry != null && entry.getKey() != null)
            {
                return entry.getKey().toString();
            }
        }
        catch (final Throwable e)
        {
            LOGGER.debug("读取职业键失败：{}", e.toString());
        }
        return "unknown";
    }

    /** 安全读取职业的工作模块；个别建筑模块为 null 时会抛异常，兜底返回 null。 */
    public static IAssignsJob safeWorkModule(final IJob<?> job)
    {
        if (job == null)
        {
            return null;
        }
        try
        {
            return job.getWorkModule();
        }
        catch (final Throwable e)
        {
            LOGGER.debug("读取职业工作模块失败：{}", e.toString());
            return null;
        }
    }

    /** 为殖民地构造职业优先级映射：按每个工作位（模块）的 JobEntry 取键，缺失项回退默认 0.5。 */
    public static Map<String, Float> buildPriorities(final IColony colony, final ColonyJobConfig cfg)
    {
        final Map<String, Float> prio = new HashMap<>();
        for (final IBuilding b : colony.getBuildingManager().getBuildings().values())
        {
            if (b == null)
            {
                continue;
            }
            for (final IAssignsJob m : safeJobModules(b))
            {
                final String key = jobKeyOf(m);
                prio.put(key, cfg.getPriority(colony.getID(), key));
            }
        }
        return prio;
    }

    /**
     * 对殖民地执行最优分配并落地。
     *
     * @param colony          目标殖民地
     * @param cfg             殖民地级 G 优先级持久化
     * @param onlyUnemployed  仅分配失业市民（不重排在职市民）
     * @return 实际调动的市民数
     */
    public static int assign(final IColony colony, final ColonyJobConfig cfg, final boolean onlyUnemployed)
    {
        final Map<String, Float> prio = buildPriorities(colony, cfg);
        final AssignmentBuilder.Plan plan = AssignmentBuilder.build(colony, prio, onlyUnemployed);
        final int[] assign = JobAssignmentSolver.solve(plan.score, plan.cap);

        int moved = 0;
        for (int i = 0; i < assign.length; i++)
        {
            final int k = assign[i];
            final IAssignsJob targetModule = k >= 0 ? plan.modules.get(k) : null;
            if (applyAssign(plan.citizens.get(i), targetModule))
            {
                moved++;
            }
        }
        return moved;
    }

    /**
     * 把一个市民调整到目标工作位（模块）；{@code targetModule == null} 表示该市民本次无岗位。
     *
     * <p>关键：当前工作位与目标相同时跳过；不同时**先解除旧职（腾出空槽）再指派**，
     * 以保证全量重排时不会把某工作位挤爆容量。任何异常都被吞掉并记录，绝不冒泡成命令崩溃。
     */
    private static boolean applyAssign(final ICitizenData citizen, final IAssignsJob targetModule)
    {
        try
        {
            final IJob<?> job = citizen.getJob();
            final IAssignsJob current = safeWorkModule(job);

            if (current == targetModule)
            {
                return false; // 无变化
            }
            if (current != null)
            {
                current.removeCitizen(citizen); // 腾出旧空槽（或使其失业）
            }
            if (targetModule != null)
            {
                return targetModule.assignCitizen(citizen);
            }
            return current != null; // 仅解除了旧职，也算一次调整
        }
        catch (final Throwable e)
        {
            LOGGER.error("最优分配：市民 {} 指派失败（目标 {}）",
                citizen == null ? "?" : citizen.getName(), targetModule == null ? "无业" : jobKeyOf(targetModule), e);
            return false;
        }
    }
}
