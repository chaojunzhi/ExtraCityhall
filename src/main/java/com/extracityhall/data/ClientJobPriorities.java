package com.extracityhall.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户端缓存：由 {@code JobPrioritySyncMessage} 填充，供职业优先级滑块窗口读取。
 *
 * <p>仅客户端使用；权威数据在服务器端的 {@link ColonyJobConfig}。
 */
public final class ClientJobPriorities
{
    private static int colonyId = -1;
    private static final Map<String, Float> values = new HashMap<>();
    private static Runnable onUpdate = null;
    /** 该殖民地的分配模式（原版 / 未就业最优 / 全体最优），默认全体最优。 */
    private static int mode = ColonyJobConfig.MODE_ALL;

    private ClientJobPriorities()
    {
    }

    public static void update(final int id, final List<String> keys, final List<Float> vals, final int modeIn)
    {
        colonyId = id;
        values.clear();
        for (int i = 0; i < keys.size(); i++)
        {
            values.put(keys.get(i), vals.get(i));
        }
        mode = modeIn;
        if (onUpdate != null)
        {
            onUpdate.run();
        }
    }

    /** 由滑块窗口注册：数据同步到达时触发界面重绘。 */
    public static void setOnUpdate(final Runnable r)
    {
        onUpdate = r;
    }

    public static int colonyId()
    {
        return colonyId;
    }

    /** 当前缓存的分配模式（未同步时为默认的「全体最优」）。 */
    public static int mode()
    {
        return mode;
    }

    public static float get(final String key)
    {
        return values.getOrDefault(key, 0.5f);
    }

    public static Set<String> keys()
    {
        return new HashSet<>(values.keySet());
    }
}
