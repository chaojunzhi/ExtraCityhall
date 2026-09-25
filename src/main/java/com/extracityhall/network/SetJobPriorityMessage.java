package com.extracityhall.network;

import com.extracityhall.data.ColonyJobConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：设置某职业在某殖民地的优先级 G（0~1）。
 */
public class SetJobPriorityMessage
{
    private final int colonyId;
    private final String jobKey;
    private final float value;

    public SetJobPriorityMessage(final int colonyId, final String jobKey, final float value)
    {
        this.colonyId = colonyId;
        this.jobKey = jobKey;
        this.value = value;
    }

    public static void encode(final SetJobPriorityMessage m, final FriendlyByteBuf b)
    {
        b.writeInt(m.colonyId);
        b.writeUtf(m.jobKey);
        b.writeFloat(m.value);
    }

    public static SetJobPriorityMessage decode(final FriendlyByteBuf b)
    {
        return new SetJobPriorityMessage(b.readInt(), b.readUtf(), b.readFloat());
    }

    public static void handle(final SetJobPriorityMessage m, final Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            final ServerPlayer player = ctx.get().getSender();
            if (player != null)
            {
                ColonyJobConfig.get(player.getServer()).setPriority(m.colonyId, m.jobKey, m.value);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
