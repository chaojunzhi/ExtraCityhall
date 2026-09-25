package com.extracityhall.network;

import com.extracityhall.data.ColonyJobConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：设置某殖民地的「分配模式」。
 *
 * <p>取值见 {@link ColonyJobConfig}：{@code MODE_VANILLA} / {@code MODE_UNEMPLOYED} / {@code MODE_ALL}。
 */
public class SetAssignModeMessage
{
    private final int colonyId;
    private final int mode;

    public SetAssignModeMessage(final int colonyId, final int mode)
    {
        this.colonyId = colonyId;
        this.mode = mode;
    }

    public static void encode(final SetAssignModeMessage m, final FriendlyByteBuf b)
    {
        b.writeInt(m.colonyId);
        b.writeInt(m.mode);
    }

    public static SetAssignModeMessage decode(final FriendlyByteBuf b)
    {
        return new SetAssignModeMessage(b.readInt(), b.readInt());
    }

    public static void handle(final SetAssignModeMessage m, final Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            final ServerPlayer player = ctx.get().getSender();
            if (player != null)
            {
                ColonyJobConfig.get(player.getServer()).setMode(m.colonyId, m.mode);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
