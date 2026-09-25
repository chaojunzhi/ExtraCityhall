package com.extracityhall.util;

import com.minecolonies.api.entity.citizen.Skill;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 经验增长事件探针：环形缓冲保留最近 {@link #CAPACITY} 条工作曲线发放记录，供
 * {@code /exh expprobe} 展示，用来在遊戲内确认"到底有没有真的在加经验"。
 *
 * <p>每条事件记录发放时刻的游戏刻、殖民地、市民、技能、发放前后等级、
 * 曲线原始值 {@code rawXp}、当时读取的书记系数 {@code a}，以及二者相乘的实到经验
 * {@code rawXp * a}——注意 {@code rawXp} 是<b>未乘 a</b> 的值，a 由
 * {@link SecretarySkillHandlerWrapper} 在 {@code addXpToSkill} 内部施加，故此处只读取展示，避免重复相乘。
 *
 * <p>线程安全：服务端 tick 与命令执行理论同源，但这里仍用 {@code synchronized} 保护，
 * 代价可忽略。
 */
public final class XpProbe
{
    /** 缓冲容量：保留最近 20 条，与命令输出条数一致。 */
    public static final int CAPACITY = 20;

    private static final Deque<XpEvent> EVENTS = new ArrayDeque<>(CAPACITY);

    /** 累计发放次数，不因缓冲淘汰而归零，用于判断长期是否在工作。 */
    private static long totalGrants = 0;

    /** 内部服务端刻计数，由 tick 处理器每刻推进；作为事件时间戳，避免依赖 Level 取游戏刻。 */
    private static long serverTicks = 0;

    private XpProbe()
    {
    }

    /** 推进服务端刻计数，由 tick 处理器每刻调用一次。 */
    public static synchronized void advanceTick()
    {
        serverTicks++;
    }

    /**
     * 当前服务端刻计数，与事件时间戳同源，用于命令侧计算"多少刻前"。
     *
     * @return 累计服务端刻数
     */
    public static synchronized long currentTick()
    {
        return serverTicks;
    }

    /**
     * 记录一次经验发放，时间戳取内部服务端刻计数。
     *
     * @param colonyId    殖民地 ID
     * @param citizenName 市民名
     * @param skill       结算的技能
     * @param levelBefore 发放前等级
     * @param levelAfter  发放后等级
     * @param rawXp       曲线原始经验（未乘 a）
     * @param multiplier  当时读取的书记系数 a
     */
    public static synchronized void record(final int colonyId,
                                            final String citizenName,
                                            final Skill skill,
                                            final int levelBefore,
                                            final int levelAfter,
                                            final double rawXp,
                                            final double multiplier)
    {
        totalGrants++;
        if (EVENTS.size() >= CAPACITY)
        {
            EVENTS.removeFirst();
        }
        EVENTS.addLast(new XpEvent(serverTicks, colonyId, citizenName, skill, levelBefore, levelAfter, rawXp, multiplier));
    }

    /** 累计发放次数。 */
    public static synchronized long totalGrants()
    {
        return totalGrants;
    }

    /**
     * 最近记录，按时间倒序（最新在前）。
     *
     * @return 不可变视图无关的副本
     */
    public static synchronized List<XpEvent> recent()
    {
        final List<XpEvent> list = new ArrayList<>(EVENTS);
        Collections.reverse(list);
        return list;
    }

    /**
     * 一次经验发放事件。
     *
     * @param tick        发放时刻的游戏刻
     * @param colonyId    殖民地 ID
     * @param citizenName 市民名
     * @param skill       结算的技能
     * @param levelBefore 发放前等级
     * @param levelAfter  发放后等级
     * @param rawXp       曲线原始经验（未乘 a）
     * @param multiplier  当时读取的书记系数 a
     */
    public record XpEvent(long tick,
                          int colonyId,
                          String citizenName,
                          Skill skill,
                          int levelBefore,
                          int levelAfter,
                          double rawXp,
                          double multiplier)
    {
        /** 实际落入技能的经验（曲线值 × 书记系数）。 */
        public double effectiveXp()
        {
            return rawXp * multiplier;
        }
    }
}
