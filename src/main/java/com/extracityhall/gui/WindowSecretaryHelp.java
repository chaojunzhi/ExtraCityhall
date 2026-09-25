package com.extracityhall.gui;

import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;

/**
 * 书记「帮助」GUI：由第 2 页右下角红蜡「?」按钮打开。
 * 采用与原版 windowinfo 一致的单页纸布局，仅显示「帮助」标题，无正文文案。
 */
public class WindowSecretaryHelp extends AbstractWindowSkeleton {
    /** 原版 windowinfo.xml 的退出按钮 id（第一页时可见的左翻页箭头） */
    private static final String BUTTON_EXIT = "exit";

    private final BuildingTownHall.View townHallView;

    public WindowSecretaryHelp(final BuildingTownHall.View townHallView) {
        super("extracityhall:gui/windowsecretaryhelp.xml");
        this.townHallView = townHallView;
        registerButton(BUTTON_EXIT, this::close);
    }

    @Override
    public void onOpened() {
        super.onOpened();

        // 交由骨架类统一处理翻页按钮显隐（单页时自动隐藏箭头与页码）
        setPage(false, 0);
    }
}
