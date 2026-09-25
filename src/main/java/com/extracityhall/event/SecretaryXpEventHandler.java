package com.extracityhall.event;

import com.extracityhall.ExtraCityHall;
import com.extracityhall.util.SecretarySkillHandlerWrapper;
import com.minecolonies.api.colony.ICitizen;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.citizens.event.CitizenAddedEvent;
import com.minecolonies.core.colony.CitizenData;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenSkillHandler;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

/**
 * 服务端事件处理：把殖民地内每个市民的 {@code CitizenSkillHandler} 反射替换为
 * {@link SecretarySkillHandlerWrapper}（透明倍率委托）。
 *
 * <p>仅服务端安装：客户端 {@code CitizenData} 实现不同，反射字段不存在会失败，故用
 * {@code level.isClientSide()} 与服务端类型 {@link CitizenData} 双重守卫。
 *
 * <p>安装时机：市民加入殖民地（含读档加载触发的 {@link CitizenAddedEvent}）时包装一次；
 * 另在 {@link LevelEvent.Load}（存档加载完成）补包所有已存在市民，防止漏网。
 * 替换前以 {@code instanceof SecretarySkillHandlerWrapper} 判定，已包装则跳过，避免重复乘算。
 */
public final class SecretaryXpEventHandler {
    /** CitizenData 持有技能处理器的 final 字段（具体类型 CitizenSkillHandler） */
    private static final Field SKILL_FIELD;

    static {
        Field f = null;
        try {
            f = CitizenData.class.getDeclaredField("citizenSkillHandler");
            f.setAccessible(true);
            // 实例 final 字段在 Java 17 上 setAccessible 后即可 set；不触碰 JDK 内部 modifiers，
            // 避免模块强封装限制（static final 才需要去 final 修饰符，此处无需）。
        } catch (final ReflectiveOperationException e) {
            ExtraCityHall.LOGGER.error("无法解析 CitizenData.citizenSkillHandler 字段，书记倍率将失效", e);
        }
        SKILL_FIELD = f;
    }

    private SecretaryXpEventHandler() {
    }

    /** 市民加入/加载时包装其技能处理器 */
    @SubscribeEvent
    public static void onCitizenAdded(final CitizenAddedEvent event) {
        final ICitizen c = event.getCitizen();
        if (c instanceof ICitizenData citizen) {
            wrapIfNeeded(citizen);
        }
    }

    /** 世界（存档）加载完成后补包所有已存在市民 */
    @SubscribeEvent
    public static void onLevelLoad(final LevelEvent.Load event) {
        final net.minecraft.world.level.LevelAccessor accessor = event.getLevel();
        if (!(accessor instanceof Level level) || level.isClientSide()) {
            return;
        }
        for (final IColony colony : IColonyManager.getInstance().getColonies(level)) {
            if (colony == null || colony.getCitizenManager() == null) {
                continue;
            }
            for (final ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
                wrapIfNeeded(citizen);
            }
        }
    }

    private static void wrapIfNeeded(@NotNull final ICitizenData citizen) {
        if (SKILL_FIELD == null || !(citizen instanceof CitizenData)) {
            return;
        }
        final CitizenData data = (CitizenData) citizen;
        try {
            final Object current = SKILL_FIELD.get(data);
            if (current instanceof SecretarySkillHandlerWrapper) {
                return; // 已包装，防重复
            }
            if (!(current instanceof CitizenSkillHandler)) {
                return; // 非预期实现，跳过
            }
            SKILL_FIELD.set(data, new SecretarySkillHandlerWrapper((CitizenSkillHandler) current));
        } catch (final ReflectiveOperationException e) {
            ExtraCityHall.LOGGER.error("为市民 {} 安装书记倍率包装器失败", citizen.getName(), e);
        }
    }
}
