package com.extracityhall.util;

import com.extracityhall.config.ExhConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;

/**
 * 工作曲线：独立于经验球的额外经验来源，每 {@code ExhConfig.settleIntervalTicks} tick 结算一次。
 *
 * <p>每次结算的经验：
 * <pre>XP = a * b * ln(L / Lmax + 1)</pre>
 * 其中 {@code b} 即 {@code ExhConfig.baseXpB}；{@code a} 是书记系数，<b>本类刻意不乘</b>——
 * 结算值交给 {@code citizen.getCitizenSkillHandler().addXpToSkill(...)} 后，由既有的
 * {@link SecretarySkillHandlerWrapper} 统一施加书记倍率，从而只乘一次（避免 a 被平方）。
 *
 * <p>数学背景（用于标定，源自 MineColonies {@code ExperienceUtils}）：
 * <ul>
 *   <li>单级成本 {@code cost(l) = 1 + 5l + 0.005 l^3}；</li>
 *   <li>累计需求 {@code X(L) = L + 2.5L(L-1) + 0.00125 L^2(L-1)^2}，主导项约 {@code 0.00125 L^4}；</li>
 *   <li>每级耗时 {@code T(L) = cost(L) / delta(L)}：L≈3 处有浅谷后单调上升，整体按 L^4 增长。</li>
 * </ul>
 * 故 {@code b=5} 的标定结果：1 级小屋（Lmax=20，截断 L=15）且 a=1 时，
 * L=1→15 约 61.5 分钟，首级约 4.1 分钟，末段约 5.5 分钟/级。
 *
 * <p>收敛性：原始 XP 随 L 递增且封顶于 {@code b*ln2}（不自行归零），
 * 真正的收敛由 {@code ExhConfig.cutoffRatio} 硬截断保证——工作最多贡献 {@code 0.75*Lmax} 级，
 * 其余只能靠经验球等设施。
 *
 * <p>可调参数（b / 结算间隔 / 截断比例）存于 {@code config/exhConfig.cfg}（{@link ExhConfig}），
 * 公式形状常数（tanh 加成、次属性权重、缩放分母、截断精度）硬编码于 {@code SecretaryXp}。
 */
public final class WorkXpCurve
{

    /**
     * 空闲状态名。MineColonies 的 AI 每 20 tick 把当前状态名写进
     * {@code IJob.getNameTagDescription()}（{@code AbstractEntityAIBasic.updateVisualState}），
     * 状态名不是 IDLE 即视为「正在工作」——与 MineColonies 自身 renderMetadata 的口径一致。
     *
     * <p>刻意不用 {@code renderMetadata}：该字段的语义是「手上拿什么」，被各职业 AI 大量覆写，
     * 渔夫/伐木工/快递员根本不写 "working"，crafting 系只在真正的 CRAFT 状态写，
     * 拿它当工作判据会整类漏掉工人。
     */
    public static final String IDLE_STATE = "IDLE";

    private WorkXpCurve()
    {
    }

    /**
     * 该市民的属性等级上限，口径与 MineColonies 自身封顶一致：住宅小屋等级决定，
     * 即 {@code (homeLevel + 1) * 10}；无住宅时按 0 级算（上限 10）。
     *
     * @param citizen 市民数据
     * @return Lmax
     */
    public static double lmaxOf(final ICitizenData citizen)
    {
        if (citizen == null)
        {
            return 10.0;
        }
        final IBuilding home = citizen.getHomeBuilding();
        final int homeLevel = home == null ? 0 : home.getBuildingLevel();
        return (homeLevel + 1) * 10.0;
    }

    /**
     * 是否应发放工作经验：排除 L&lt;=0 死区（ln(0/Lmax+1)=0，工作推不动 0 级，
     * 必须先由经验球给到第 1 级）与 L&gt;=75%*Lmax 的截断。
     *
     * @param level 当前等级
     * @param lmax  该市民的等级上限
     * @return 是否发放
     */
    public static boolean shouldGrant(final int level, final double lmax)
    {
        return level > 0 && level < ExhConfig.cutoffRatio * lmax;
    }

    /**
     * 本次结算应发放的经验（不含书记系数 a）。
     *
     * @param level 当前等级
     * @param lmax  该市民的等级上限
     * @return 经验值 {@code b * ln(L/Lmax + 1)}
     */
    public static double xpFor(final int level, final double lmax)
    {
        return ExhConfig.baseXpB * Math.log(level / lmax + 1.0);
    }
}
