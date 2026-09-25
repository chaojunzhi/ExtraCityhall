package com.extracityhall.network;

import com.extracityhall.data.ColonyJobConfig;
import com.extracityhall.util.JobAssigner;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.IAssignsJob;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：请求某殖民地的职业优先级 G 快照（用于打开滑块窗口时同步当前值）。
 */
public class RequestJobPriorityMessage
{
    private final int colonyId;

    public RequestJobPriorityMessage(final int colonyId)
    {
        this.colonyId = colonyId;
    }

    public static void encode(final RequestJobPriorityMessage m, final FriendlyByteBuf b)
    {
        b.writeInt(m.colonyId);
    }

    public static RequestJobPriorityMessage decode(final FriendlyByteBuf b)
    {
        return new RequestJobPriorityMessage(b.readInt());
    }

    public static void handle(final RequestJobPriorityMessage m, final Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            final ServerPlayer player = ctx.get().getSender();
            if (player == null)
            {
                return;
            }
            final IColony colony = IColonyManager.getInstance().getColonyByWorld(m.colonyId, player.level());
            if (colony == null)
            {
                return;
            }
            final ColonyJobConfig cfg = ColonyJobConfig.get(player.getServer());
            final List<String> keys = new ArrayList<>();
            final List<Float> vals = new ArrayList<>();
            for (final IBuilding b : colony.getBuildingManager().getBuildings().values())
            {
                if (b == null)
                {
                    continue;
                }
                // 逐个「工作位」取职业键：同一栋楼的多个职业（林务员 / 果园管理员）分别列出
                for (final IAssignsJob module : JobAssigner.safeJobModules(b))
                {
                    final String key = JobAssigner.jobKeyOf(module);
                    if (keys.contains(key))
                    {
                        continue; // 多个工作位同属一个职业时只列一个滑块
                    }
                    keys.add(key);
                    vals.add(cfg.getPriority(m.colonyId, key));
                }
            }
            ExtraCityHallNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new JobPrioritySyncMessage(m.colonyId, keys, vals, cfg.getMode(m.colonyId)));
        });
        ctx.get().setPacketHandled(true);
    }
}
