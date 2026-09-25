package com.extracityhall.jobs;

import com.extracityhall.ExtraCityHall;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.core.colony.jobs.views.DefaultJobView;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组新增的职业注册。
 *
 * MineColonies 的职业注册表为 {@code minecolonies:jobs}，第三方 mod 用
 * {@code DeferredRegister.create(new ResourceLocation("minecolonies","jobs"), MODID)}
 * 即可向同一注册表追加条目（参考 {@code ModJobsInitializer}）。
 */
public final class ExtraJobEntries {
    /** MineColonies 职业注册表 key */
    public static final ResourceLocation JOBS_REGISTRY = new ResourceLocation("minecolonies", "jobs");

    /** 书记职业 id: extracityhall:secretary（翻译 key：com.extracityhall.job.secretary） */
    public static final ResourceLocation SECRETARY_ID = new ResourceLocation(ExtraCityHall.MODID, "secretary");

    public static final DeferredRegister<JobEntry> JOBS =
        DeferredRegister.create(JOBS_REGISTRY, ExtraCityHall.MODID);

    public static final RegistryObject<JobEntry> SECRETARY = JOBS.register("secretary",
        () -> new JobEntry.Builder()
            .setJobProducer(JobSecretary::new)
            .setJobViewProducer(() -> DefaultJobView::new)
            .setRegistryName(SECRETARY_ID)
            .createJobEntry());

    private ExtraJobEntries() {
    }

    public static void register(final IEventBus bus) {
        JOBS.register(bus);
    }
}
