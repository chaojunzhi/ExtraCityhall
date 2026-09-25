package com.extracityhall.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.List;

/**
 * 带容量约束的最大权分配求解器（最小费用最大流）。
 *
 * <p>把"每名市民至多一个职业、每个职业不超空槽"的最优匹配建模为二分最小费用最大流：
 * 源 → 市民(容量1) → 职业/建筑节点 → 汇(容量=空槽)，市民→建筑边的费用取 {@code -score}，
 * 最小费用即最大总适配。约束矩阵全单模，整数解自然成立。
 *
 * <p>本类为纯函数，不依赖任何 Minecraft / MineColonies 类型，便于独立测试。
 */
public final class JobAssignmentSolver
{
    private static final double EPS = 1e-9;

    private JobAssignmentSolver()
    {
    }

    /**
     * 求解分配。
     *
     * @param score     {@code score[i][k]} = 市民 i 对职业/建筑 k 的得分（建议已非负）
     * @param openSlots {@code openSlots[k]} = 职业/建筑 k 的可雇佣空槽数（按 0 截断）
     * @return {@code assignment[i]} = 市民 i 被分配到的建筑索引 k；{@code -1} 表示未分配
     */
    public static int[] solve(final double[][] score, final int[] openSlots)
    {
        final int nCit = score.length;
        if (nCit == 0)
        {
            return new int[0];
        }
        final int nJob = score[0].length;
        final int vCount = nCit + nJob + 2;
        final int s = 0;
        final int t = vCount - 1;

        final List<List<Integer>> adj = new ArrayList<>(vCount);
        for (int i = 0; i < vCount; i++)
        {
            adj.add(new ArrayList<>());
        }
        final List<Integer> to = new ArrayList<>();
        final List<Integer> cap = new ArrayList<>();
        final List<Double> cost = new ArrayList<>();

        final class Graph
        {
            private int ec = 0;

            void addEdge(final int u, final int v, final int c, final double w)
            {
                adj.get(u).add(ec);
                to.add(v);
                cap.add(c);
                cost.add(w);
                ec++;
                adj.get(v).add(ec);
                to.add(u);
                cap.add(0);
                cost.add(-w);
                ec++;
            }
        }
        final Graph g = new Graph();

        for (int i = 0; i < nCit; i++)
        {
            g.addEdge(s, 1 + i, 1, 0.0);
        }
        for (int k = 0; k < nJob; k++)
        {
            g.addEdge(1 + nCit + k, t, Math.max(0, openSlots[k]), 0.0);
        }
        for (int i = 0; i < nCit; i++)
        {
            for (int k = 0; k < nJob; k++)
            {
                final double sv = score[i][k];
                if (sv > EPS)
                {
                    g.addEdge(1 + i, 1 + nCit + k, 1, -sv);
                }
            }
        }

        final double[] dist = new double[vCount];
        final int[] prevV = new int[vCount];
        final int[] prevE = new int[vCount];
        final boolean[] inq = new boolean[vCount];

        // 最小费用最大流：SPFA 求最短路（可处理负权边），无负环，必然终止。
        while (true)
        {
            Arrays.fill(dist, Double.POSITIVE_INFINITY);
            Arrays.fill(inq, false);
            dist[s] = 0.0;
            final ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(s);
            inq[s] = true;
            while (!queue.isEmpty())
            {
                final int u = queue.poll();
                inq[u] = false;
                for (final int e : adj.get(u))
                {
                    if (cap.get(e) > 0 && dist[u] + cost.get(e) < dist[to.get(e)] - EPS)
                    {
                        dist[to.get(e)] = dist[u] + cost.get(e);
                        prevV[to.get(e)] = u;
                        prevE[to.get(e)] = e;
                        if (!inq[to.get(e)])
                        {
                            inq[to.get(e)] = true;
                            queue.add(to.get(e));
                        }
                    }
                }
            }
            if (dist[t] == Double.POSITIVE_INFINITY)
            {
                break;
            }
            int f = Integer.MAX_VALUE;
            int v = t;
            while (v != s)
            {
                final int e = prevE[v];
                f = Math.min(f, cap.get(e));
                v = prevV[v];
            }
            v = t;
            while (v != s)
            {
                final int e = prevE[v];
                cap.set(e, cap.get(e) - f);
                cap.set(e ^ 1, cap.get(e ^ 1) + f);
                v = prevV[v];
            }
        }

        final int[] assignment = new int[nCit];
        Arrays.fill(assignment, -1);
        for (int i = 0; i < nCit; i++)
        {
            for (final int e : adj.get(1 + i))
            {
                final int v = to.get(e);
                if (v >= 1 + nCit && v <= 1 + nCit + nJob - 1 && cap.get(e) == 0)
                {
                    assignment[i] = v - (1 + nCit);
                    break;
                }
            }
        }
        return assignment;
    }

    /**
     * 由属性矩阵 P、职业因子矩阵 W、职业优先级 G 计算得分矩阵。
     *
     * @param p {@code p[i][j]} 市民 i 的属性 j（建议已归一化到 [0,1]）
     * @param w {@code w[k][j]} 职业/建筑 k 对属性 j 的权重（主 1.0 / 次 0.5 / 其余 0）
     * @param g {@code g[k]} 职业/建筑 k 的玩家优先级（0~1）
     * @return {@code score[i][k] = g[k] * (p[i]·w[k])}，并保证非负
     */
    public static double[][] scoreMatrix(final double[][] p, final double[][] w, final double[] g)
    {
        final int nCit = p.length;
        final int nJob = w.length;
        final double[][] score = new double[nCit][nJob];
        for (int i = 0; i < nCit; i++)
        {
            for (int k = 0; k < nJob; k++)
            {
                double fit = 0.0;
                final int dim = Math.min(p[i].length, w[k].length);
                for (int j = 0; j < dim; j++)
                {
                    fit += p[i][j] * w[k][j];
                }
                score[i][k] = Math.max(0.0, g[k] * fit);
            }
        }
        return score;
    }
}
