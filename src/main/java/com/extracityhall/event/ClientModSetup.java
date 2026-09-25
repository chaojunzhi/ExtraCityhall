package com.extracityhall.event;

import com.extracityhall.ExtraCityHall;
import com.extracityhall.jobs.ExtraJobEntries;
import com.minecolonies.api.client.render.modeltype.SimpleModelType;
import com.minecolonies.api.client.render.modeltype.registry.IModelTypeRegistry;
import com.minecolonies.core.client.model.MaleCitizenModel;
import com.minecolonies.core.event.ClientRegistryHandler;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端专属初始化：把书记职业的自定义模型类型注册进 MineColonies 的 {@link IModelTypeRegistry}。
 *
 * <p>复用 MineColonies 既有市民几何（{@code MALE_CITIZEN} / {@code FEMALE_CITIZEN} 两层），不新增任何模型代码；
 * 仅把 basename 为 secretary 的模型类型登记进去，由 {@code JobSecretary#getModel()} 返回的同一
 * ResourceLocation（extracityhall:secretary）索引。书记上岗后渲染器（RenderBipedCitizen）即按 secretary
 * 取基底模型与贴图。
 *
 * <p>女书记直接复用男款市民几何：官方 {@code FemaleCitizenModel} 没有帽子部件，且自带裙子/胸饰，与
 * 「男女同款中山装、长裤」的目标不符；贴图侧已把胡子与裙装区域擦成透明，故男女都套同一套中山装。
 *
 * <p>注册时机选 {@link EntityRenderersEvent.AddLayers}：该事件在客户端初始化阶段、首帧渲染前触发，
 * 其 {@link EntityRendererProvider.Context} 可直接 {@code bakeLayer} 复用市民几何层，
 * 规避各版本 {@code Minecraft.getEntityModelSet()} 的 API 差异。
 *
 * <p>本类以 {@code Dist.CLIENT} + {@code Bus.MOD} 声明，服务端不会加载其中引用的 MineColonies 客户端类
 * （ClientRegistryHandler / *CitizenModel 等），避免专用服务端类加载崩溃。
 */
@Mod.EventBusSubscriber(modid = ExtraCityHall.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModSetup {
    /** 幂等保护：模型类型只注册一次 */
    private static boolean registered = false;

    private ClientModSetup() {
    }

    @SubscribeEvent
    public static void onAddLayers(final EntityRenderersEvent.AddLayers event) {
        if (registered) {
            return;
        }
        // 依赖 MineColonies 已在自己的客户端 setup 中注册市民层定义，此处可直接烘培复用。
        // 男女各烘一棵（复用同一层定义也没问题，但分别烘更稳妥）。
        final EntityRendererProvider.Context ctx = event.getContext();
        final ModelPart male = ctx.bakeLayer(ClientRegistryHandler.MALE_CITIZEN);
        final ModelPart female = ctx.bakeLayer(ClientRegistryHandler.MALE_CITIZEN);
        IModelTypeRegistry.getInstance().register(new SimpleModelType(
            ExtraJobEntries.SECRETARY_ID,
            1,
            new MaleCitizenModel(male),
            new MaleCitizenModel(female)));
        registered = true;
    }
}
