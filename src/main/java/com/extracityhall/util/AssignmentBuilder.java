package com.extracityhall.util;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.IBuildingWorkerModule;
import com.minecolonies.api.colony.buildings.modules.IAssignsJob;
import com.minecolonies.api.entity.citizen.Skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 抽取最优分配所需的矩阵，并交给 {@link JobAssignmentSolver} 求解。
 *
 * <p>矩阵约定（与方案一致）：
 * <ul>
 *   <li>P[i][j]：市民 i 的属性 j（MineColonies 各 {@link Skill}，含 Knowledge/Mana），按列归一化到 [0,1]；</li>
 *   <li>W[k][j]：职业/建筑 k 对属性 j 的权重，主技能 1.0、次技能 0.5、其余 0；</li>
 *   <li>G[k]：玩家对职业 k 的优先级（0~1，缺省 0.5）；</li>
 *   <li>cap[k]：职业/建筑 k 的可雇佣空槽数 = 最大工人数 − 当前工人数；</li>
 *   <li>score[i][k] = G[k] · (P[i] · W[k])，并保证非负。</li>
 * </ul>
 *
 * <p>注意：本类集中使用了若干 MineColonies 公共 API，方法名已对照 1.20.1 常用签名，
 * 若你的 MineColonies 版本有出入（如 {@code getBuildings()} 返回类型、{@code getMaxWorkersInBuilding()}、
 * {@code getBuildingType()}），以编译报错处为准微调即可。
 */
public final class AssignmentBuilder
{
    private AssignmentBuilder()
    {
    }

    /**
     * 一次求解所需的全部输入与结果。
     *
     * <p>{@code modules} 是「工作位」而非建筑：一栋楼可有多个职业模块
     * （如林务员小屋 = 林务员 + 果园管理员），每个模块都是一个独立分配槽位。
     */
    public static final class Plan
    {
        public final List<ICitizenData> citizens;
        public final List<IAssignsJob> modules;
        public final double[][] score;
        public final int[] cap;

        public Plan(final List<ICitizenData> citizens, final List<IAssignsJob> modules, final double[][] score, final int[] cap)
        {
            this.citizens = citizens;
            this.modules = modules;
            this.score = score;
            this.cap = cap;
        }
    }

    /**
     * 构建分配方案。
     *
     * @param colony          目标殖民地
     * @param priorities      职业 key → 优先级（缺省 0.5 由调用方补齐）
     * @param onlyUnemployed  仅把失业市民分配到空槽（不触动在职市民）
     */
    public static Plan build(final IColony colony, final Map<String, Float> priorities, final boolean onlyUnemployed)
    {
        final Skill[] skills = Skill.values();
        final int d = skills.length;

        final List<ICitizenData> citizens = new ArrayList<>();
        for (final ICitizenData c : colony.getCitizenManager().getCitizens())
        {
            if (c == null)
            {
                continue;
            }
            if (onlyUnemployed && c.getJob() != null)
            {
                continue;
            }
            citizens.add(c);
        }

        // P 矩阵（市民 × 属性），随后按列归一化到 [0,1]
        final double[][] p = new double[citizens.size()][d];
        for (int i = 0; i < citizens.size(); i++)
        {
            final ICitizenData c = citizens.get(i);
            for (int j = 0; j < d; j++)
            {
                p[i][j] = c.getCitizenSkillHandler().getLevel(skills[j]);
            }
        }
        normalizeColumns(p);

        // 抽取「工作位」：一栋楼可能有多个职业模块（林务员小屋 = 林务员 + 果园管理员），
        // 每个模块都是一个独立分配槽位，各带自己的主/次技能、容量与优先级键。
        final List<IAssignsJob> modules = new ArrayList<>();
        final List<double[]> wList = new ArrayList<>();
        final List<Float> gList = new ArrayList<>();
        final List<Integer> capList = new ArrayList<>();
        for (final IBuilding b : colony.getBuildingManager().getBuildings().values())
        {
            if (b == null)
            {
                continue;
            }
            for (final IAssignsJob m : JobAssigner.safeJobModules(b))
            {
                try
                {
                    final IBuildingWorkerModule wm = (IBuildingWorkerModule) m;
                    final Skill primary = wm.getPrimarySkill();
                    final Skill secondary = wm.getSecondarySkill();
                    final double[] wk = new double[d];
                    for (int j = 0; j < d; j++)
                    {
                        final Skill s = skills[j];
                        wk[j] = (s == primary) ? 1.0 : (s == secondary) ? 0.5 : 0.0;
                    }
                    final String jobKey = JobAssigner.jobKeyOf(m);
                    final Float pr = priorities.get(jobKey);
                    final int maxWorkers = m.getModuleMax();
                    // 全量重排（onlyUnemployed=false）时把每个工作位视为全空槽（旧员工会被重新决策）；
                    // 否则会出现「已满员的槽位空槽=0，谁都进不去」而无法真正重排。
                    final int slots = onlyUnemployed
                        ? Math.max(0, maxWorkers - countWorkers(colony, m))
                        : Math.max(0, maxWorkers);

                    modules.add(m);
                    wList.add(wk);
                    gList.add(pr == null ? 0.5f : pr);
                    capList.add(slots);
                }
                catch (final Throwable ex)
                {
                    // 该工作位不可用（模块未就绪等），跳过即可，不影响其它槽位
                }
            }
        }

        final int nB = modules.size();
        final double[][] w = wList.toArray(new double[0][]);
        final double[] g = new double[nB];
        final int[] cap = new int[nB];
        for (int k = 0; k < nB; k++)
        {
            g[k] = gList.get(k);
            cap[k] = capList.get(k);
        }

        final double[][] score = JobAssignmentSolver.scoreMatrix(p, w, g);
        return new Plan(citizens, modules, score, cap);
    }

    /** 统计当前在该建筑工人模块任职的市民数（用于算空槽）。 */
    private static int countWorkers(final IColony colony, final IAssignsJob module)
    {
        int n = 0;
        for (final ICitizenData c : colony.getCitizenManager().getCitizens())
        {
            if (c == null || c.getJob() == null)
            {
                continue;
            }
            final IAssignsJob wm = JobAssigner.safeWorkModule(c.getJob());
            if (wm == module)
            {
                n++;
            }
        }
        return n;
    }

    /** 把矩阵每一列归一化到 [0,1]；某列全相等则置 0（无区分度）。 */
    private static void normalizeColumns(final double[][] p)
    {
        if (p.length == 0)
        {
            return;
        }
        final int rows = p.length;
        final int cols = p[0].length;
        for (int j = 0; j < cols; j++)
        {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < rows; i++)
            {
                final double v = p[i][j];
                if (v < min)
                {
                    min = v;
                }
                if (v > max)
                {
                    max = v;
                }
            }
            if (max - min < 1e-9)
            {
                for (int i = 0; i < rows; i++)
                {
                    p[i][j] = 0.0;
                }
            }
            else
            {
                final double inv = 1.0 / (max - min);
                for (int i = 0; i < rows; i++)
                {
                    p[i][j] = (p[i][j] - min) * inv;
                }
            }
        }
    }
}
