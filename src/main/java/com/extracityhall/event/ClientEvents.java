package com.extracityhall.event;

import com.extracityhall.ExtraCityHall;
import com.extracityhall.gui.EmploymentTabHelper;
import com.extracityhall.gui.WindowSettingsEx;
import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.core.client.gui.AbstractWindowModuleBuilding;
import com.minecolonies.core.client.gui.townhall.WindowSettings;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

/**
 * 客户端专属事件：市政厅窗口的「就业」书签注入。
 *
 * 单独成类的原因：{@code ScreenEvent.Opening} / {@code BOScreen} / 原版 GUI 窗口均属客户端类，
 * 若与服务端监听混在同一个类里注册，服务端会因梳理处理器入参类型而加载这些类，
 * 存在专用服务端报错的风险。此处用 {@code Dist.CLIENT} + {@code Bus.FORGE} 声明，
 * 由 Forge 自行按侧注册。
 *
 * <p>打开窗口的流程：BlockUI 窗口由 BOScreen（MC Screen 子类）包装，经 getWindow() 取实际窗口。
 * 原版 7 个分页窗口（WindowMainPage/InfoPage/CitizenPage/PermissionsPage/StatsPage/
 * AlliancePage/Settings）均继承 AbstractWindowModuleBuilding，且构造时 XML 已加载，
 * 在 Opening 阶段注入按钮安全可用。
 */
@Mod.EventBusSubscriber(modid = ExtraCityHall.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {
    /** 原版 AbstractWindowModuleBuilding.building 字段（反射缓存，避免每次开窗重复查找） */
    private static final Field BUILDING_FIELD = findBuildingField();

    private ClientEvents() {
    }

    private static Field findBuildingField() {
        try {
            final Field field = AbstractWindowModuleBuilding.class.getDeclaredField("building");
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            return null;
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(final ScreenEvent.Opening event) {
        if (event.getScreen() == null || !(event.getScreen() instanceof BOScreen boScreen)) {
            return;
        }
        final BOWindow window = boScreen.getWindow();

        // 设置页：换成扩展窗口 WindowSettingsEx（按书记在岗与否锁定「自动分配职业」开关）
        if (window instanceof WindowSettings) {
            if (window instanceof WindowSettingsEx) {
                // 直接打开的扩展窗口：补注入就业书签（幂等，已注入则跳过）
                final AbstractWindowModuleBuilding<?> exWindow = (AbstractWindowModuleBuilding<?>) window;
                final BuildingTownHall.View exView = extractTownHallView(exWindow);
                if (exView != null && exWindow.findPaneOfTypeByID("employment", Button.class) == null) {
                    EmploymentTabHelper.inject(exWindow, exView);
                }
                return;
            }
            final BuildingTownHall.View settingsView = extractTownHallView((AbstractWindowModuleBuilding<?>) window);
            if (settingsView != null) {
                final WindowSettingsEx extended = new WindowSettingsEx(settingsView);
                // setNewScreen 替换出的屏幕不会再次触发 ScreenEvent.Opening，
                // 必须在此主动注入就业书签，否则设置页会丢失「就业」入口
                EmploymentTabHelper.inject(extended, settingsView);
                event.setNewScreen(new BOScreen(extended));
                return;
            }
        }

        if (!(window instanceof AbstractWindowModuleBuilding<?> moduleWindow)) {
            return;
        }
        final BuildingTownHall.View view = extractTownHallView(moduleWindow);
        if (view == null) {
            return;
        }
        // 幂等：本模组已注入过则跳过；就业页继承 AbstractWindowSkeleton，
        // 不满足 instanceof AbstractWindowModuleBuilding，天然不会走到这里
        if (moduleWindow.findPaneOfTypeByID("employment", Button.class) != null) {
            return;
        }
        EmploymentTabHelper.inject(moduleWindow, view);
    }

    /** 从原版市政厅窗口反射取出 BuildingTownHall.View（AbstractWindowModuleBuilding.building） */
    private static BuildingTownHall.View extractTownHallView(final AbstractWindowModuleBuilding<?> window) {
        if (BUILDING_FIELD == null) {
            return null;
        }
        try {
            final Object value = BUILDING_FIELD.get(window);
            if (value instanceof BuildingTownHall.View view) {
                return view;
            }
        } catch (IllegalAccessException ignored) {
        }
        return null;
    }
}
