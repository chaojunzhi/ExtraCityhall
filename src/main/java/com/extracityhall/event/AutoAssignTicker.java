package com.extracityhall.event;

import com.extracityhall.ExtraCityHall;
import com.extracityhall.config.ExhConfig;
import com.extracityhall.data.ColonyJobConfig;
import com.extracityhall.util.JobAssigner;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * 夜晚自动重排定时器。
 *
 * <p>开启 {@code ExhConfig.autoAssignEnabled} 后，每 {@code autoAssignPeriodDays} 个游戏日、
 * 在「夜晚」对当前维度所有殖民地的全体市民（含已在职者）按线性规划最优解重排职业。
 *
 * <p>触发判定（每游戏刻廉价检查，命中才求解一次）：
 * <ul>
 *   <li>仅在服务端、且当前为夜晚（游戏日内时间 ∈ [13000, 23000)）；</li>
 *   <li>距上次重排已 ≥ periodDays × 24000 tick（周期）；</li>
 *   <li>本游戏日尚未触发过（防同一夜晚内重复触发）。</li>
 * </ul>
 * 其余时间零开销，不在 tick 循环里做求解。
 */
@Mod.EventBusSubscriber(modid = ExtraCityHall.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AutoAssignTicker
{
    private static final Logger LOGGER = LogManager.getLogger(AutoAssignTicker.class);

    /** 夜晚时间窗（Minecraft 日内 tick：13000≈日落到 23000≈黎明前）。 */
    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;

    /** 每个维度：上次重排的游戏时间、上次触发所在的游戏日。 */
    private static final Map<ResourceLocation, Long> LAST_RUN = new HashMap<>();
    private static final Map<ResourceLocation, Long> LAST_DAY = new HashMap<>();

    private AutoAssignTicker()
    {
    }

    @SubscribeEvent
    public static void onLevelTick(final TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        if (event.level == null || event.level.isClientSide())
        {
            return;
        }
        if (!ExhConfig.autoAssignEnabled)
        {
            return;
        }

        final long gameTime = event.level.getGameTime();
        final long dayTime = (event.level.getDayTime() % ExhConfig.TICKS_PER_DAY + ExhConfig.TICKS_PER_DAY) % ExhConfig.TICKS_PER_DAY;
        if (dayTime < NIGHT_START || dayTime >= NIGHT_END)
        {
            return; // 仅夜晚触发
        }

        final ResourceLocation dim = event.level.dimension().location();
        final long currentDay = gameTime / ExhConfig.TICKS_PER_DAY;

        final Long lastRun = LAST_RUN.get(dim);
        if (lastRun == null)
        {
            // 首次启用：以当前为起点，满足周期后再触发，避免立刻重排
            LAST_RUN.put(dim, gameTime);
            LAST_DAY.put(dim, currentDay);
            return;
        }

        final long periodTicks = Math.max(1L, (long) (ExhConfig.autoAssignPeriodDays * ExhConfig.TICKS_PER_DAY));
        final Long lastDay = LAST_DAY.get(dim);
        if (gameTime - lastRun < periodTicks)
        {
            return; // 周期未到
        }
        if (lastDay != null && lastDay == currentDay)
        {
            return; // 本游戏日已触发（防同夜重复）
        }

        // 触发：对当前维度全部殖民地做「含在职」的全局最优重排
        final ServerLevel serverLevel = (ServerLevel) event.level;
        final ColonyJobConfig cfg = ColonyJobConfig.get(serverLevel);
        int totalMoved = 0;
        int colonies = 0;
        for (final IColony c : IColonyManager.getInstance().getColonies(serverLevel))
        {
            if (c == null)
            {
                continue;
            }
            try
            {
                final int mode = cfg.getMode(c.getID());
                if (mode == ColonyJobConfig.MODE_VANILLA)
                {
                    continue; // 该殖民地选「原版」：本模组不接管，交给 MineColonies 自己的自动雇佣
                }
                colonies++;
                totalMoved += JobAssigner.assign(c, cfg, mode == ColonyJobConfig.MODE_UNEMPLOYED);
            }
            catch (final Throwable e)
            {
                LOGGER.error("自动重排：殖民地 {} 分配失败", c.getID(), e);
            }
        }
        LAST_RUN.put(dim, gameTime);
        LAST_DAY.put(dim, currentDay);

        if (colonies > 0)
        {
            LOGGER.info("ExtraCityHall 自动重排完成：{} 个殖民地，本批调动 {} 名市民（周期 {} 游戏日）",
                colonies, totalMoved, ExhConfig.autoAssignPeriodDays);
        }
    }
}
