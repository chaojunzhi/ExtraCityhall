package com.extracityhall.command;

import com.extracityhall.ExtraCityHall;
import com.extracityhall.data.ColonyJobConfig;
import com.extracityhall.util.JobAssigner;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.UUID;

/**
 * 命令 {@code /exh assign}（及 {@code /exh assign unemployed}）：在服务端抽取数据、求解并落地最优分配；
 * 命令 {@code /exh setpriority <jobKey> <0~1>}：设置某职业优先级 G（GUI 之外的可靠入口）。
 *
 * <p>重排逻辑统一交由 {@link JobAssigner}，本模组「最优分配」按钮、就业页与自动重排定时器共用。
 * 由本模组接管 MineColonies 原版自动分配。命令反馈文案走 lang（command.extracityhall.*），
 * 与 GUI 本地化风格保持一致。
 */
@Mod.EventBusSubscriber(modid = ExtraCityHall.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AssignCommand
{
    private AssignCommand()
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
            .then(Commands.literal("assign")
                .executes(ctx -> runAssign(ctx.getSource(), false))
                .then(Commands.literal("unemployed").executes(ctx -> runAssign(ctx.getSource(), true))))
            .then(Commands.literal("setpriority")
                .then(Commands.argument("jobKey", StringArgumentType.string())
                    .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                        .executes(ctx -> runSetPriority(ctx.getSource(),
                            StringArgumentType.getString(ctx, "jobKey"),
                            (float) DoubleArgumentType.getDouble(ctx, "value"))))))
            .then(Commands.literal("assignmode")
                .then(Commands.argument("mode", StringArgumentType.word())
                    .executes(ctx -> runSetAssignMode(ctx.getSource(),
                        StringArgumentType.getString(ctx, "mode"))))));
    }

    /**
     * 执行最优分配。
     *
     * @param source         命令来源（需为玩家）
     * @param onlyUnemployed 仅分配失业市民（不重排在职市民）
     * @return 1 表示执行成功
     */
    public static int runAssign(final CommandSourceStack source, final boolean onlyUnemployed)
    {
        final Player player = source.getPlayer();
        if (!(player instanceof ServerPlayer sp))
        {
            source.sendFailure(Component.translatable("command.extracityhall.assign.need_player"));
            return 0;
        }
        final IColony colony = findColony(sp);
        if (colony == null)
        {
            source.sendFailure(Component.translatable("command.extracityhall.colony.not_found"));
            return 0;
        }

        final ColonyJobConfig cfg = ColonyJobConfig.get(sp.getServer());
        final int mode = cfg.getMode(colony.getID());
        if (mode == ColonyJobConfig.MODE_VANILLA)
        {
            source.sendFailure(Component.translatable("command.extracityhall.assign.vanilla_mode"));
            return 0;
        }
        // 模式为「未就业最优」时只动失业市民；显式的 unemployed 子命令同样只动失业市民
        final boolean useUnemployedOnly = onlyUnemployed || mode == ColonyJobConfig.MODE_UNEMPLOYED;

        final int moved;
        try
        {
            moved = JobAssigner.assign(colony, cfg, useUnemployedOnly);
        }
        catch (final Throwable e)
        {
            ExtraCityHall.LOGGER.error("最优分配执行失败", e);
            source.sendFailure(Component.translatable("command.extracityhall.assign.failed",
                e.getClass().getSimpleName() + ": " + e.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("command.extracityhall.assign.done", moved), false);
        return 1;
    }

    /** 设置某职业优先级 G 并落盘。 */
    public static int runSetPriority(final CommandSourceStack source, final String jobKey, final float value)
    {
        final Player player = source.getPlayer();
        if (!(player instanceof ServerPlayer sp))
        {
            source.sendFailure(Component.translatable("command.extracityhall.setpriority.need_player"));
            return 0;
        }
        final IColony colony = findColony(sp);
        if (colony == null)
        {
            source.sendFailure(Component.translatable("command.extracityhall.colony.not_found"));
            return 0;
        }
        ColonyJobConfig.get(sp.getServer()).setPriority(colony.getID(), jobKey, value);
        source.sendSuccess(() -> Component.translatable("command.extracityhall.setpriority.done",
            jobKey, (int) (value * 100)), false);
        return 1;
    }

    /** 设置该殖民地的分配模式（vanilla / unemployed / all）。 */
    public static int runSetAssignMode(final CommandSourceStack source, final String modeArg)
    {
        final Player player = source.getPlayer();
        if (!(player instanceof ServerPlayer sp))
        {
            source.sendFailure(Component.translatable("command.extracityhall.setmode.need_player"));
            return 0;
        }
        final IColony colony = findColony(sp);
        if (colony == null)
        {
            source.sendFailure(Component.translatable("command.extracityhall.colony.not_found"));
            return 0;
        }
        final String name = modeArg.toLowerCase(Locale.ROOT);
        final int mode;
        switch (name)
        {
            case "vanilla":
                mode = ColonyJobConfig.MODE_VANILLA;
                break;
            case "unemployed":
                mode = ColonyJobConfig.MODE_UNEMPLOYED;
                break;
            case "all":
                mode = ColonyJobConfig.MODE_ALL;
                break;
            default:
                source.sendFailure(Component.translatable("command.extracityhall.setmode.bad"));
                return 0;
        }
        ColonyJobConfig.get(sp.getServer()).setMode(colony.getID(), mode);
        source.sendSuccess(() -> Component.translatable("command.extracityhall.setmode.done", name), false);
        return 1;
    }

    private static IColony findColony(final ServerPlayer sp)
    {
        final UUID owner = sp.getUUID();
        for (final IColony c : IColonyManager.getInstance().getColonies(sp.level()))
        {
            if (c != null && owner.equals(c.getPermissions().getOwner()))
            {
                return c;
            }
        }
        return null;
    }
}
