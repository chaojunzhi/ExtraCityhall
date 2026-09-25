package com.extracityhall.network;

import com.extracityhall.ExtraCityHall;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 本模组的网络通道（沿用 Forge SimpleChannel）。
 *
 * <p>用于客户端滑块 → 服务端 G 设置、以及服务端 → 客户端 G 同步（GUI 需要，原版自动分配接管不需要）。
 */
public final class ExtraCityHallNetwork
{
    private static final String PROTOCOL = "1.0";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(ExtraCityHall.MODID, "main"),
        () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int id = 0;

    private ExtraCityHallNetwork()
    {
    }

    public static void register()
    {
        CHANNEL.registerMessage(id++, SetJobPriorityMessage.class, SetJobPriorityMessage::encode, SetJobPriorityMessage::decode, SetJobPriorityMessage::handle);
        CHANNEL.registerMessage(id++, RequestJobPriorityMessage.class, RequestJobPriorityMessage::encode, RequestJobPriorityMessage::decode, RequestJobPriorityMessage::handle);
        CHANNEL.registerMessage(id++, JobPrioritySyncMessage.class, JobPrioritySyncMessage::encode, JobPrioritySyncMessage::decode, JobPrioritySyncMessage::handle);
        CHANNEL.registerMessage(id++, SetAssignModeMessage.class, SetAssignModeMessage::encode, SetAssignModeMessage::decode, SetAssignModeMessage::handle);
    }
}
