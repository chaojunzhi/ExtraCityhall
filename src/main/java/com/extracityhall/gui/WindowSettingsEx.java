package com.extracityhall.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.ButtonImage;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingsModuleView;
import com.minecolonies.core.client.gui.townhall.WindowSettings;
import com.minecolonies.core.colony.buildings.moduleviews.TownHallSettingsModuleView;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 原版市政厅「设置」页的扩展版本：按书记是否在岗强制控制「自动分配职业」开关。
 *
 * 原版 {@code WindowSettings#onUpdate} 每帧都会调用 {@code BoolSetting#render}，
 * 后者无条件 {@code triggerButton.setEnabled(isActive(...))}，因此仅在开窗时改一次 enabled 会被覆盖。
 * 这里通过继承并在每帧 super.onUpdate() 之后重设 enabled/提示（渲染发生在 onUpdate 之后，故生效）。
 *
 * 自动分配语义：无在岗工人（书记）时不仅锁定开关，还强制把设置值关回 OFF——
 * 即自动分配默认关闭，直到有工人在岗后才允许开启。
 */
public class WindowSettingsEx extends WindowSettings {
    private static final String PANE_JOB = "job";
    private static final String PANE_TRIGGER = "trigger";

    private final BuildingTownHall.View townHallView;
    private Boolean lastHadSecretary = null;

    public WindowSettingsEx(final BuildingTownHall.View townHall) {
        super(townHall);
        this.townHallView = townHall;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        applyAutoHiringLock();
    }

    private void applyAutoHiringLock() {
        final Pane pane = this.findPaneByID(PANE_JOB);
        if (pane == null) {
            return;
        }
        final ButtonImage trigger = pane.findPaneOfTypeByID(PANE_TRIGGER, ButtonImage.class);
        if (trigger == null) {
            return;
        }

        final boolean hasSecretary = SecretaryView.hasSecretary(this.townHallView);

        // 无在岗工人：不仅锁定开关，还把已开启的自动分配强制关回 OFF（本地翻转 + 发送 TriggerSettingMessage 同步服务端）
        if (!hasSecretary) {
            forceAutoHiringOff();
        }

        trigger.setEnabled(hasSecretary);

        // 提示随状态变化时更新（render 每帧只会改写文本，不再重置 tooltip）
        if (lastHadSecretary == null || lastHadSecretary != hasSecretary) {
            lastHadSecretary = hasSecretary;
            PaneBuilders.singleLineTooltip(
                Component.translatable(hasSecretary
                    ? "gui.extracityhall.settings.autohire.tooltip"
                    : "gui.extracityhall.settings.autohire.locked"),
                trigger);
        }
    }

    /**
     * 把「自动分配职业」设置强制关回 OFF。
     * 复用原版客户端触发链路 {@code ISettingsModuleView#trigger(key)}：
     * 本地翻转 BoolSetting 值并向服务端发送 {@code TriggerSettingMessage}，服务端随之落盘。
     * 本地翻转后 getValue() 变为 false，本方法不会连续重复发送。
     */
    private void forceAutoHiringOff() {
        final List<TownHallSettingsModuleView> views = this.townHallView.getModuleViews(TownHallSettingsModuleView.class);
        if (views.isEmpty()) {
            return;
        }
        final ISettingsModuleView settings = views.get(0);
        final BoolSetting setting = settings.getSetting(BuildingTownHall.AUTO_HIRING_MODE);
        if (setting != null && Boolean.TRUE.equals(setting.getValue())) {
            settings.trigger(BuildingTownHall.AUTO_HIRING_MODE);
        }
    }
}
