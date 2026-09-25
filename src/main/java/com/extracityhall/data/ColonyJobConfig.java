package com.extracityhall.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * 殖民地级「职业优先级 G」持久化（随存档保存）。
 *
 * <p>G 为每职业 0~1 的权重（默认 0.5），按 {@code (colonyId -> jobKey -> priority)} 三层存放。
 * 使用 Forge 1.20 的 {@link SavedData} 挂到主世界维度，随世界一起读写。
 */
public class ColonyJobConfig extends SavedData
{
    /** 分配模式：原版 —— 本模组不接管，周期重排跳过该殖民地。 */
    public static final int MODE_VANILLA = 0;
    /** 分配模式：未就业最优 —— 只给失业市民分配，不触动在职者。 */
    public static final int MODE_UNEMPLOYED = 1;
    /** 分配模式：全体最优 —— 全量重排（含在职市民），默认值。 */
    public static final int MODE_ALL = 2;

    private final Map<Integer, Map<String, Float>> priorities = new HashMap<>();
    /** 每殖民地的分配模式（colonyId -> mode）。 */
    private final Map<Integer, Integer> modes = new HashMap<>();

    /** 取指定殖民地的存档实例（挂到主世界）。 */
    public static ColonyJobConfig get(final ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(ColonyJobConfig::load, ColonyJobConfig::new, "extracityhall_jobconfig");
    }

    public static ColonyJobConfig get(final MinecraftServer server)
    {
        return get(server.overworld());
    }

    /** 单职业优先级，缺省 0.5。 */
    public float getPriority(final int colonyId, final String jobKey)
    {
        final Map<String, Float> m = priorities.get(colonyId);
        if (m == null)
        {
            return 0.5f;
        }
        final Float v = m.get(jobKey);
        return v == null ? 0.5f : v;
    }

    /** 设置单职业优先级并落盘（按 0~1 截断）。 */
    public void setPriority(final int colonyId, final String jobKey, final float value)
    {
        final Map<String, Float> m = priorities.computeIfAbsent(colonyId, k -> new HashMap<>());
        m.put(jobKey, Math.max(0.0f, Math.min(1.0f, value)));
        setDirty();
    }

    /** 该殖民地的分配模式，缺省为「全体最优」。 */
    public int getMode(final int colonyId)
    {
        final Integer v = modes.get(colonyId);
        if (v == null)
        {
            return MODE_ALL;
        }
        return (v >= MODE_VANILLA && v <= MODE_ALL) ? v : MODE_ALL;
    }

    /** 设置该殖民地的分配模式并落盘（越界值回退「全体最优」）。 */
    public void setMode(final int colonyId, final int mode)
    {
        modes.put(colonyId, (mode >= MODE_VANILLA && mode <= MODE_ALL) ? mode : MODE_ALL);
        setDirty();
    }

    @Override
    public CompoundTag save(final CompoundTag tag)
    {
        for (final Map.Entry<Integer, Map<String, Float>> e : priorities.entrySet())
        {
            final CompoundTag sub = new CompoundTag();
            for (final Map.Entry<String, Float> p : e.getValue().entrySet())
            {
                sub.putFloat(p.getKey(), p.getValue());
            }
            tag.put("colony_" + e.getKey(), sub);
        }
        for (final Map.Entry<Integer, Integer> e : modes.entrySet())
        {
            tag.putInt("mode_" + e.getKey(), e.getValue());
        }
        return tag;
    }

    public static ColonyJobConfig load(final CompoundTag tag)
    {
        final ColonyJobConfig data = new ColonyJobConfig();
        for (final String key : tag.getAllKeys())
        {
            if (key.startsWith("colony_"))
            {
                final int id = Integer.parseInt(key.substring("colony_".length()));
                final CompoundTag sub = tag.getCompound(key);
                final Map<String, Float> m = new HashMap<>();
                for (final String jk : sub.getAllKeys())
                {
                    m.put(jk, sub.getFloat(jk));
                }
                data.priorities.put(id, m);
            }
            else if (key.startsWith("mode_"))
            {
                try
                {
                    final int id = Integer.parseInt(key.substring("mode_".length()));
                    data.modes.put(id, tag.getInt(key));
                }
                catch (final NumberFormatException ignored)
                {
                    // 忽略损坏的键，该殖民地回退默认「全体最优」
                }
            }
        }
        return data;
    }
}
