package com.extracityhall.gui;

import com.extracityhall.data.ClientJobPriorities;
import com.extracityhall.network.ExtraCityHallNetwork;
import com.extracityhall.network.RequestJobPriorityMessage;
import com.extracityhall.network.SetJobPriorityMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 「职业优先级」滑块窗口（Vanilla {@link Screen}）。
 *
 * <p>打开时向服务端请求该殖民地的优先级快照，服务端回传后渲染每个职业一个 0~1 滑块；
 * 拖动即时通过 {@link SetJobPriorityMessage} 写入服务端并落盘。关闭时返回原窗口。
 *
 * <p>说明：此窗口覆盖在 BlockUI 的就业窗口之上，返回依赖 {@code lastScreen} 还原；
 * 不同 MineColonies 版本下返回体验可能略有差异，若不便可用 {@code /exh setpriority} 命令替代。
 */
public class JobPriorityScreen extends Screen
{
    private final Screen lastScreen;
    private final int colonyId;
    private final List<JobSlider> sliders = new ArrayList<>();

    public JobPriorityScreen(final Screen lastScreen, final int colonyId)
    {
        super(Component.literal("职业优先级"));
        this.lastScreen = lastScreen;
        this.colonyId = colonyId;
    }

    @Override
    protected void init()
    {
        super.init();
        ClientJobPriorities.setOnUpdate(this::rebuild);
        addStaticWidgets();
        buildSliders();
        // 请求最新优先级；服务端回传后 onUpdate 触发 rebuild 重绘
        ExtraCityHallNetwork.CHANNEL.sendToServer(new RequestJobPriorityMessage(colonyId));
    }

    private void addStaticWidgets()
    {
        this.addRenderableWidget(Button.builder(Component.literal("完成"), b -> this.onClose())
            .bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    private void buildSliders()
    {
        sliders.clear();
        if (ClientJobPriorities.colonyId() != colonyId)
        {
            return; // 尚未同步
        }
        int y = 40;
        for (final String key : ClientJobPriorities.keys())
        {
            final float val = ClientJobPriorities.get(key);
            final JobSlider s = new JobSlider(this.width / 2 - 100, y, 200, 20, key, val, colonyId);
            this.addRenderableWidget(s);
            sliders.add(s);
            y += 26;
        }
    }

    /** 由服务端同步回调在客户端线程触发。 */
    public void rebuild()
    {
        this.clearWidgets();
        addStaticWidgets();
        buildSliders();
    }

    @Override
    public void onClose()
    {
        ClientJobPriorities.setOnUpdate(null);
        Minecraft.getInstance().setScreen(lastScreen);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partial)
    {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partial);
    }

    private static class JobSlider extends AbstractSliderButton
    {
        private final int colonyId;
        private final String jobKey;

        JobSlider(final int x, final int y, final int w, final int h, final String key, final float val, final int colonyId)
        {
            super(x, y, w, h, Component.literal(key), val);
            this.colonyId = colonyId;
            this.jobKey = key;
            setMessage(Component.literal(key + "  " + (int) (val * 100) + "%"));
        }

        @Override
        protected void updateMessage()
        {
            setMessage(Component.literal(jobKey + "  " + (int) (value * 100) + "%"));
        }

        @Override
        protected void applyValue()
        {
            ExtraCityHallNetwork.CHANNEL.sendToServer(new SetJobPriorityMessage(colonyId, jobKey, (float) value));
        }
    }
}
