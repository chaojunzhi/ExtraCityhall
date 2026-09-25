package com.extracityhall.util;

import com.minecolonies.api.colony.IColony;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 书记经验倍率注册表（全殖民地共享，服务端运行期状态）。
 *
 * <p>书记 AI 在岗时按知识/智力算出的 tanh 倍率写入「殖民地 → 倍率」映射；
 * 每个市民自然干活调用 {@code addXpToSkill} 时，由 {@link SecretarySkillHandlerWrapper}
 * 读取本殖民地倍率乘算后委托真实处理器。
 *
 * <p>为避免书记离岗/阵亡后倍率残留，采用「过期」机制：每次写入附带一个过期刻，
 * 超过该刻后 {@link #getMultiplier} 自动回落到 1.0（无加成），无需显式 reset 钩子。
 */
public final class SecretaryXpMultiplier {
    /** 倍率生效宽限（游戏刻），AI 每周期刷新，离线即过期 */
    private static final long GRACE_TICKS = 3L * 20L;

    private static final Map<IColony, Entry> COLONY_MULTIPLIER = new ConcurrentHashMap<>();

    private SecretaryXpMultiplier() {
    }

    /** 设置某殖民地的书记经验倍率（应 >= 1.0；<=1.0 视为无加成，仍写入以便过期清理） */
    public static void setMultiplier(final IColony colony, final double factor) {
        if (colony == null) {
            return;
        }
        final double f = factor <= 1.0 ? 1.0 : factor;
        COLONY_MULTIPLIER.put(colony, new Entry(f, now(colony) + GRACE_TICKS));
    }

    /** 读取某殖民地的倍率，缺省 1.0（无加成）；已过期则清理并返回 1.0 */
    public static double getMultiplier(final IColony colony) {
        if (colony == null) {
            return 1.0;
        }
        final Entry e = COLONY_MULTIPLIER.get(colony);
        if (e == null) {
            return 1.0;
        }
        if (now(colony) > e.expiry) {
            COLONY_MULTIPLIER.remove(colony);
            return 1.0;
        }
        return e.factor;
    }

    /** 立即清除某殖民地的倍率（书记离岗/丢书时调用，等价于置回 1.0） */
    public static void reset(final IColony colony) {
        if (colony != null) {
            COLONY_MULTIPLIER.remove(colony);
        }
    }

    private static long now(final IColony colony) {
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return Long.MAX_VALUE;
        }
        final ResourceKey<Level> dim = colony.getDimension();
        final ServerLevel level = dim == null ? null : server.getLevel(dim);
        return level == null ? Long.MAX_VALUE : level.getGameTime();
    }

    /** 倍率 + 过期刻 */
    private static final class Entry {
        final double factor;
        final long expiry;

        Entry(final double factor, final long expiry) {
            this.factor = factor;
            this.expiry = expiry;
        }
    }
}
