package com.extracityhall.config;

import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * ExtraCityHall 通用配置，存于 {@code config/exhConfig.cfg}。
 *
 * <h2>经验获取计算公式</h2>
 * <pre>
 * 工作曲线（被动经验，与经验球无关）每 settleIntervalTicks tick 结算一次：
 *   ΔXP_work = baseXpB * ln(L / Lmax + 1)         仅当 0 &lt; L &lt; cutoffRatio * Lmax
 *   其中 Lmax = (homeLevel + 1) * 10               （住宅小屋等级决定，非可调）
 *
 * 书记全局倍率（乘到市民获得的所有经验上）：
 *   factor      = 1 + 0.5 * tanh_trunc( (Knowledge + 0.5 * Mana) / 100 )
 *   tanh_trunc(z) = floor(100 * tanh(z)) / 100
 *
 * 总经验（经 SecretarySkillHandlerWrapper 施加，避免倍率被平方）：
 *   ΔXP_total = factor * baseXpB * ln(L / Lmax + 1)   (0 &lt; L &lt; cutoffRatio * Lmax)
 * </pre>
 *
 * <p>本类只放「部署期可调的速率旋钮」（b、结算间隔、截断比例）；
 * 公式形状相关的静态系数（tanh 最大加成 0.5、次属性权重 0.5、缩放分母 100、截断精度 100）
 * 硬编码于 {@code com.extracityhall.util.SecretaryXp}，不对外暴露。
 *
 * <p>改完 cfg 重启游戏生效。后续其它可调项也在此文件追加。
 */
public final class ExhConfig
{
    private static final Logger LOGGER = LogManager.getLogger(ExhConfig.class);
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("exhConfig.cfg");
    private static final Properties PROPS = new Properties();

    /** 工作曲线基准常数 b：翻倍则所有耗时减半（经验速率主旋钮）。 */
    public static double baseXpB = 5.0;
    /** 结算间隔（游戏刻）：每 N tick 发放一次工作经验（20 tick = 1 秒）。 */
    public static int settleIntervalTicks = 200;
    /** 等级截断比例（0~1）：达到 Lmax 的该比例后不再发放工作经验。 */
    public static double cutoffRatio = 0.75;

    // ---- 自动重排（夜晚重排周期） ----
    /** 是否启用「夜晚自动重排」：开启后每 autoAssignPeriodDays 个游戏日、在夜晚对全体市民（含在职）重排职业。 */
    public static boolean autoAssignEnabled = false;
    /** 自动重排周期（游戏日）：每 N 个游戏日执行一次；1 游戏日 = 24000 游戏刻。默认 1（每晚一次）。 */
    public static double autoAssignPeriodDays = 1.0;
    /** 1 个游戏日对应的游戏刻数（Minecraft 固定：24000 tick/日，20 tick = 1 秒）。 */
    public static final long TICKS_PER_DAY = 24000L;

    private ExhConfig()
    {
    }

    /** 启动时调用：不存在则写默认 cfg（含公式注释），否则读入覆盖默认值。 */
    public static void load()
    {
        try
        {
            if (!Files.exists(FILE))
            {
                writeDefaults();
            }
            try (InputStream in = Files.newInputStream(FILE))
            {
                PROPS.load(in);
            }
            baseXpB = parseDouble("baseXpB", baseXpB);
            settleIntervalTicks = Math.max(1, parseInt("settleIntervalTicks", settleIntervalTicks));
            cutoffRatio = parseDouble("cutoffRatio", cutoffRatio);
            autoAssignEnabled = parseBoolean("autoAssignEnabled", autoAssignEnabled);
            autoAssignPeriodDays = parseDouble("autoAssignPeriodDays", autoAssignPeriodDays);
        }
        catch (final IOException e)
        {
            LOGGER.error("无法读取 exhConfig.cfg，使用默认经验参数", e);
        }
    }

    private static void writeDefaults() throws IOException
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("# ============================================================\n");
        sb.append("# ExtraCityHall 配置参数（修改后重启游戏生效）\n");
        sb.append("# ============================================================\n");
        sb.append("#\n");
        sb.append("# ---- 经验获取 ----\n");
        sb.append("# 工作曲线（被动经验，与经验球无关）每 settleIntervalTicks tick 结算一次：\n");
        sb.append("#   ΔXP_work = baseXpB * ln(L / Lmax + 1)        仅当 0 < L < cutoffRatio * Lmax\n");
        sb.append("#   其中 Lmax = (homeLevel + 1) * 10             （住宅等级决定，非可调）\n");
        sb.append("#\n");
        sb.append("# 书记全局倍率（乘到市民获得的所有经验上）：\n");
        sb.append("#   factor      = 1 + 0.5 * tanh_trunc( (Knowledge + 0.5 * Mana) / 100 )\n");
        sb.append("#   tanh_trunc(z) = floor(100 * tanh(z)) / 100\n");
        sb.append("#\n");
        sb.append("# 总经验（经 SecretarySkillHandlerWrapper 施加，避免倍率被平方）：\n");
        sb.append("#   ΔXP_total = factor * baseXpB * ln(L / Lmax + 1)   (0 < L < cutoffRatio * Lmax)\n");
        sb.append("#\n");
        sb.append("# --- 本文件可调（动态参数） ---\n");
        sb.append("# baseXpB: 工作曲线基准常数 b；翻倍则所有耗时减半（速率主旋钮）\n");
        sb.append("baseXpB=").append(baseXpB).append("\n");
        sb.append("# settleIntervalTicks: 结算间隔（游戏刻），每 N tick 发放一次（20 tick = 1 秒）\n");
        sb.append("settleIntervalTicks=").append(settleIntervalTicks).append("\n");
        sb.append("# cutoffRatio: 等级截断比例（0~1），达到 Lmax 的该比例后不再发放工作经验\n");
        sb.append("cutoffRatio=").append(cutoffRatio).append("\n");
        sb.append("#\n");
        sb.append("# ---- 自动重排（夜晚重排周期） ----\n");
        sb.append("# 开启 autoAssignEnabled 后，每 autoAssignPeriodDays 个游戏日、在「夜晚」对全体市民（含已在职者）重排职业。\n");
        sb.append("#   触发条件：当前处于夜晚（游戏日内时间 ∈ [13000, 23000)）且距上次重排已 ≥ periodDays × 24000 tick\n");
        sb.append("#   每个游戏日 = 24000 tick（20 tick = 1 秒，故 1 游戏日 ≈ 现实 20 分钟）\n");
        sb.append("#   求解仍为线性规划最优（含在职市民的整体最优匹配），不进入 tick 循环，仅夜晚触发一次\n");
        sb.append("#\n");
        sb.append("# autoAssignEnabled: 是否启用自动重排（默认 false，关闭以免误改玩家手动安排）\n");
        sb.append("autoAssignEnabled=").append(autoAssignEnabled).append("\n");
        sb.append("# autoAssignPeriodDays: 重排周期（游戏日，可为小数，如 0.5=半天、2=两天）；最小有效 1 tick\n");
        sb.append("autoAssignPeriodDays=").append(autoAssignPeriodDays).append("\n");
        sb.append("#\n");
        sb.append("# --- 公式固有常数（硬编码于 SecretaryXp，不可调） ---\n");
        sb.append("# 0.5  tanh 最大加成系数（最大 +50%）\n");
        sb.append("# 0.5  次属性（智力/Mana）权重\n");
        sb.append("# 100  属性缩放分母\n");
        sb.append("# 100  截断精度（两位小数）\n");
        Files.createDirectories(FILE.getParent());
        Files.writeString(FILE, sb.toString());
    }

    private static int parseInt(final String key, final int def)
    {
        final String v = PROPS.getProperty(key);
        if (v == null)
        {
            return def;
        }
        try
        {
            return Integer.parseInt(v.trim());
        }
        catch (final NumberFormatException e)
        {
            return def;
        }
    }

    private static double parseDouble(final String key, final double def)
    {
        final String v = PROPS.getProperty(key);
        if (v == null)
        {
            return def;
        }
        try
        {
            return Double.parseDouble(v.trim());
        }
        catch (final NumberFormatException e)
        {
            return def;
        }
    }

    private static boolean parseBoolean(final String key, final boolean def)
    {
        final String v = PROPS.getProperty(key);
        if (v == null)
        {
            return def;
        }
        return Boolean.parseBoolean(v.trim());
    }
}
