package com.extracityhall.command;

import com.extracityhall.ExtraCityHall;
import com.extracityhall.util.XpProbe;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 调试命令 {@code /exh expprobe}：输出最近 {@link XpProbe#CAPACITY} 条工作经验增长事件。
 *
 * <p>用于确认工作曲线是否真的在发放经验：每条记录展示发放时刻、
 * 市民与技能、发放前后等级、曲线原始值、当时读取的书记系数 {@code a}，以及相乘后的实到经验。
 * 反馈文案走 lang（command.extracityhall.expprobe.*），与 GUI 本地化风格保持一致。
 */
@Mod.EventBusSubscriber(modid = ExtraCityHall.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SecretaryDebugCommand
{
    private SecretaryDebugCommand()
    {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event)
    {
        register(event.getDispatcher());
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("exh")
            .then(Commands.literal("expprobe")
                .executes(ctx -> runExpProbe(ctx.getSource()))));
    }

    /**
     * {@code /exh expprobe}：输出最近的经验增长事件（最新在前）。
     *
     * @param source 命令来源
     * @return 1 表示执行成功
     */
    private static int runExpProbe(@NotNull final CommandSourceStack source)
    {
        final long now = XpProbe.currentTick();
        final List<XpProbe.XpEvent> events = XpProbe.recent();

        final MutableComponent out = Component.literal("")
            .append(Component.translatable("command.extracityhall.expprobe.title").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("\n"))
            .append(Component.translatable("command.extracityhall.expprobe.header",
                XpProbe.totalGrants(), events.size()).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("\n"));

        if (events.isEmpty())
        {
            out.append(Component.translatable("command.extracityhall.expprobe.empty").withStyle(ChatFormatting.RED));
        }
        else
        {
            int index = 1;
            for (final XpProbe.XpEvent event : events)
            {
                final long ago = now - event.tick();
                out.append(Component.literal("#").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(String.valueOf(index++)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("command.extracityhall.expprobe.ago", ago, ago / 20)
                        .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("command.extracityhall.expprobe.line2",
                        event.colonyId(), event.citizenName(), event.skill().name()))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("command.extracityhall.expprobe.line3",
                        event.levelBefore(), event.levelAfter(),
                        event.rawXp(), event.multiplier(), event.effectiveXp())
                        .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("\n"));
            }
        }

        source.sendSuccess(() -> out, false);
        return 1;
    }
}
