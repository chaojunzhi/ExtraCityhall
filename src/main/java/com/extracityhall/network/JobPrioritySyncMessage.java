package com.extracityhall.network;

import com.extracityhall.data.ClientJobPriorities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：回传某殖民地的职业优先级 G 快照。
 */
public class JobPrioritySyncMessage
{
    private final int colonyId;
    private final List<String> jobKeys;
    private final List<Float> values;
    private final int mode;

    public JobPrioritySyncMessage(final int colonyId, final List<String> jobKeys, final List<Float> values, final int mode)
    {
        this.colonyId = colonyId;
        this.jobKeys = jobKeys;
        this.values = values;
        this.mode = mode;
    }

    public static void encode(final JobPrioritySyncMessage m, final FriendlyByteBuf b)
    {
        b.writeInt(m.colonyId);
        b.writeInt(m.jobKeys.size());
        for (int i = 0; i < m.jobKeys.size(); i++)
        {
            b.writeUtf(m.jobKeys.get(i));
            b.writeFloat(m.values.get(i));
        }
        b.writeInt(m.mode);
    }

    public static JobPrioritySyncMessage decode(final FriendlyByteBuf b)
    {
        final int colonyId = b.readInt();
        final int n = b.readInt();
        final List<String> keys = new ArrayList<>();
        final List<Float> vals = new ArrayList<>();
        for (int i = 0; i < n; i++)
        {
            keys.add(b.readUtf());
            vals.add(b.readFloat());
        }
        final int mode = b.readInt();
        return new JobPrioritySyncMessage(colonyId, keys, vals, mode);
    }

    public static void handle(final JobPrioritySyncMessage m, final Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> ClientJobPriorities.update(m.colonyId, m.jobKeys, m.values, m.mode));
        ctx.get().setPacketHandled(true);
    }
}
