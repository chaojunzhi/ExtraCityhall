package com.extracityhall;

import com.extracityhall.config.ExhConfig;
import com.extracityhall.event.GreenBookEvents;
import com.extracityhall.network.ExtraCityHallNetwork;
import com.extracityhall.event.SecretaryXpEventHandler;
import com.extracityhall.event.WorkXpEventHandler;
import com.extracityhall.jobs.ExtraJobEntries;
import com.extracityhall.modules.SecretaryModules;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.sounds.ModSoundEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;

import java.util.Map;

/**
 * ExtraCityHall (扩展市政厅) - MineColonies 1.20.1 附属模组。
 *
 * 功能:
 * - 手持普通书右键市政厅方块, 消耗书并转化为「绿皮书」物品;
 * - 绿皮书右键殖民地任意建筑绑定殖民地(NBT 记录 colony id + dimension);
 * - 空中右键打开市政厅管理 GUI(任意入口打开的原版市政厅窗口均自动附加「就业」书签);
 * - 就业页第一页为殖民地概览/建筑详情, 第二页为市政厅管理页(可雇佣「书记」);
 * - 书记在岗时按 tanh 公式给全殖民地追加经验加成;
 * - 书记不在岗时市政厅设置页的「自动分配职业」开关锁定为关闭（默认关闭直到有工人在岗）。
 */
@Mod(ExtraCityHall.MODID)
public class ExtraCityHall {
    public static final String MODID = "extracityhall";

    public static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger(ExtraCityHall.class);

    public ExtraCityHall() {
        ExhConfig.load();
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(modBus);
        ExtraJobEntries.register(modBus);
        modBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.register(GreenBookEvents.class);
        MinecraftForge.EVENT_BUS.register(SecretaryXpEventHandler.class);
        MinecraftForge.EVENT_BUS.register(WorkXpEventHandler.class);
    }

    /**
     * 注册表就绪后：把书记/最低存量模块挂到市政厅建筑条目，并登记到 MineColonies 职业清单。
     * 必须在任何殖民地建筑被实例化之前完成（注册表事件早于 common setup，存档加载晚于 common setup）。
     */
    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ExtraCityHallNetwork.register();
            SecretaryModules.attachToTownHall();
            if (ModJobs.jobs != null && !ModJobs.jobs.contains(ExtraJobEntries.SECRETARY_ID)) {
                ModJobs.jobs.add(ExtraJobEntries.SECRETARY_ID);
            }
            // 撞人/随机音效会经 SoundUtils 查 CITIZEN_SOUND_EVENTS.get(jobPath)，
            // 而该表由 ModSoundEvents 静态块在类加载时按当时 ModJobs.getJobs() 填充，
            // 本模组的 secretary 是 DeferredRegister 在 registry 阶段才注册的，那时表已填充完，
            // 故缺 "secretary" 键 → .get() 返回 null → NPE。
            // 这里把 "secretary" 占位指向已存在的 "unemployed" 整张映射（事件/声线索引结构一致），
            // 既消除崩溃又不会静音（播放失业市民音效）。待补齐书记专属音效后可替换为真实条目。
            registerSecretarySoundPlaceholder();
        });
    }

    /**
     * 把书记职业的音效占位进 {@code ModSoundEvents.CITIZEN_SOUND_EVENTS}。
     *
     * <p>该字段是 MineColonies 的 {@code public static} 可变 Map（按 job 路径建索引），
     * 静态块在类加载时按当时 {@code ModJobs.getJobs()} 填充；本模组 {@code secretary} 是
     * DeferredRegister 在 registry 阶段才注册的，故表内缺此键 → 玩家撞到书记市民时
     * {@code SoundUtils.playSoundAtCitizenWith} 链式 {@code .get("secretary")} 返回 null → NPE。
     *
     * <p>这里直接把 {@code "secretary"} 指向已存在的 {@code "unemployed"} 整张映射
     * （事件/声线索引结构一致），既消除崩溃又不会静音（播放失业市民音效）。
     * 待补齐书记专属音效后可替换为真实条目。字段为公开可见，无需反射。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerSecretarySoundPlaceholder() {
        // 引用该字段即触发 ModSoundEvents 类加载（确保 "unemployed" 等键已填充）
        final Map events = ModSoundEvents.CITIZEN_SOUND_EVENTS;
        final Object placeholder = events.get("unemployed");
        if (placeholder != null && !events.containsKey("secretary")) {
            events.put("secretary", placeholder);
        }
    }
}
