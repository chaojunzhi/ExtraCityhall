package com.extracityhall;

import com.extracityhall.item.ItemGreenBook;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private ModItems() {
    }

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, ExtraCityHall.MODID);

    public static final RegistryObject<ItemGreenBook> GREEN_BOOK =
        ITEMS.register("green_book",
            () -> new ItemGreenBook(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
