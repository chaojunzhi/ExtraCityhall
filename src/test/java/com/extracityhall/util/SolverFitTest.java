package com.extracityhall.util;

import java.util.Arrays;

/**
 * 离线拟合测试：用一组贴近 MineColonies 的示例数据驱动求解器，并和暴力最优对拍，
 * 验证「带容量最大权分配」结果既可行（每名市民至多一个职业、每职业不超空槽）又最优。
 *
 * <p>直接用 {@code javac} + {@code java} 运行即可，不依赖任何 Minecraft / 构建工具：
 * <pre>
 *   javac -d build/fit src/main/java/com/extracityhall/util/JobAssignmentSolver.java \
 *                        src/test/java/com/extracityhall/util/SolverFitTest.java
 *   java  -cp build/fit com.extracityhall.util.SolverFitTest
 * </pre>
 */
public final class SolverFitTest
{
    // 与 MineColonies Skill.values() 对齐的 8 维属性（含本模组的 Knowledge / Mana）
    private static final String[] SKILLS =
        {"Strength", "Stamina", "Dexterity", "Intelligence", "Charisma", "Adaptability", "Knowledge", "Mana"};

    private static final int STRENGTH = 0, STAMINA = 1, DEXTERITY = 2, INTELLIGENCE = 3,
        CHARISMA = 4, ADAPTABILITY = 5, KNOWLEDGE = 6, MANA = 7;

    // 市民 → 各项属性等级（行 = 市民，列 = 属性）
    private static final String[] CITIZENS = {"C0", "C1", "C2", "C3", "C4", "C5"};
    private static final double[][] P = {
        {9, 8, 3, 2, 1, 4, 1, 0},   // 体力型
        {6, 9, 7, 2, 1, 5, 1, 0},   // 伐木型
        {4, 5, 4, 3, 2, 6, 2, 0},   // 农民型
        {1, 2, 2, 9, 4, 3, 9, 3},   // 研究型（高智力+知识）
        {8, 7, 8, 3, 3, 5, 1, 0},   // 护卫型（高力量+敏捷）
        {1, 1, 2, 6, 5, 3, 8, 7},   // 书记型（高知识+法力）
    };

    // 职业 → 主/次技能（主 1.0 / 次 0.5，其余 0）
    private static final String[] JOBS = {"Miner", "Lumberjack", "Farmer", "Guard", "Researcher", "Clerk"};
    private static final int[][] JOB_PRIMARY_SECONDARY = {
        {STRENGTH, STAMINA},     // Miner
        {DEXTERITY, STRENGTH},   // Lumberjack
        {ADAPTABILITY, DEXTERITY}, // Farmer
        {STRENGTH, DEXTERITY},   // Guard
        {INTELLIGENCE, KNOWLEDGE}, // Researcher
        {KNOWLEDGE, MANA},       // Clerk
    };

    // 玩家优先级 G（0~1，默认 0.5；此处特意抬高 Guard 以验证缩放生效）
    private static final double[] G = {0.5, 0.5, 0.5, 0.9, 0.5, 0.5};

    // 各职业空槽 cap
    private static final int[] CAP = {1, 1, 1, 1, 1, 1};

    public static void main(final String[] args)
    {
        // 1) 由主/次技能构造 W 因子矩阵
        final double[][] w = buildW();

        // 2) 得分矩阵 score[i][k] = G[k] * (P[i] · W[k])，已非负
        final double[][] score = JobAssignmentSolver.scoreMatrix(P, w, G);

        // 3) 求解
        final int[] assign = JobAssignmentSolver.solve(score, CAP);

        // 4) 输出
        printHeader("得分矩阵 score[i][k] = G[k] · (P[i]·W[k])");
        printScore(score);
        printHeader("求解结果（市民 → 职业）");
        double total = 0.0;
        for (int i = 0; i < assign.length; i++)
        {
            final String job = assign[i] < 0 ? "(未分配)" : JOBS[assign[i]];
            if (assign[i] >= 0) total += score[i][assign[i]];
            System.out.printf("  %-3s -> %-11s  得分=%.3f%n", CITIZENS[i], job,
                assign[i] >= 0 ? score[i][assign[i]] : 0.0);
        }
        System.out.printf("求解总得分 = %.4f%n", total);

        // 5) 可行性自检
        checkFeasible(assign, CAP);

        // 6) 与暴力最优对拍
        final double brute = bruteForceOptimal(score, CAP);
        System.out.printf("%n暴力最优总得分 = %.4f%n", brute);
        if (Math.abs(brute - total) < 1e-6)
        {
            System.out.println("✅ 求解器结果与暴力最优一致（最优性已验证）。");
        }
        else
        {
            System.out.println("❌ 求解器不是最优！差异 = " + (brute - total));
        }
    }

    private static double[][] buildW()
    {
        final double[][] w = new double[JOBS.length][SKILLS.length];
        for (int k = 0; k < JOBS.length; k++)
        {
            w[k][JOB_PRIMARY_SECONDARY[k][0]] = 1.0;
            w[k][JOB_PRIMARY_SECONDARY[k][1]] = 0.5;
        }
        return w;
    }

    private static void printHeader(final String s)
    {
        System.out.println();
        System.out.println("=== " + s + " ===");
    }

    private static void printScore(final double[][] score)
    {
        System.out.print("       ");
        for (final String j : JOBS) System.out.printf("%-11s", j);
        System.out.println();
        for (int i = 0; i < score.length; i++)
        {
            System.out.printf("%-6s", CITIZENS[i]);
            for (int k = 0; k < score[i].length; k++) System.out.printf("%-11.3f", score[i][k]);
            System.out.println();
        }
    }

    private static void checkFeasible(final int[] assign, final int[] cap)
    {
        final int[] used = new int[cap.length];
        boolean ok = true;
        final boolean[] seen = new boolean[assign.length];
        for (final int k : assign)
        {
            if (k >= 0)
            {
                used[k]++;
                if (used[k] > cap[k]) ok = false;
            }
        }
        // 每名市民至多一个职业：由 assign 结构天然保证（一对一映射）
        if (ok)
        {
            System.out.println("✅ 可行性：每名市民至多一个职业，且无职业超出空槽。");
        }
        else
        {
            System.out.println("❌ 可行性检查失败：存在职业超出空槽数。");
        }
        Arrays.fill(seen, false);
    }

    /** 暴力枚举所有合法指派，求最大总得分（仅用于小规模对拍验证）。 */
    private static double bruteForceOptimal(final double[][] score, final int[] cap)
    {
        final int nCit = score.length;
        final int nJob = score[0].length;
        final int[] used = new int[nJob];
        final double[] best = {0.0};
        dfs(0, 0.0, score, cap, used, nCit, best);
        return best[0];
    }

    private static void dfs(final int i, final double acc, final double[][] score, final int[] cap,
                            final int[] used, final int nCit, final double[] best)
    {
        if (i == nCit)
        {
            if (acc > best[0]) best[0] = acc;
            return;
        }
        // 选择 1：不分配该市民
        dfs(i + 1, acc, score, cap, used, nCit, best);
        // 选择 2：分配到某个尚有空槽的职业
        for (int k = 0; k < cap.length; k++)
        {
            if (used[k] < cap[k] && score[i][k] > 0)
            {
                used[k]++;
                dfs(i + 1, acc + score[i][k], score, cap, used, nCit, best);
                used[k]--;
            }
        }
    }
}
