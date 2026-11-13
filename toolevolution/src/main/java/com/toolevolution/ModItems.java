package com.toolevolution;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ToolEvolution.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ToolEvolution.MOD_ID);

    public static final Map<Modification, RegistryObject<Item>> MODIFIERS = new EnumMap<>(Modification.class);

    static {
        for (Modification mod : Modification.values()) {
            MODIFIERS.put(mod, ITEMS.register(mod.id(),
                    () -> new ModifierItem(mod, new Item.Properties().rarity(Rarity.UNCOMMON))));
        }
    }

    public static final RegistryObject<CreativeModeTab> TAB = CREATIVE_TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ToolEvolution.MOD_ID))
                    .icon(() -> new ItemStack(MODIFIERS.get(Modification.SPEED_3).get()))
                    .displayItems((params, output) -> {
                        for (Modification mod : Modification.values()) {
                            output.accept(MODIFIERS.get(mod).get());
                        }
                    })
                    .build());

    private ModItems() {
    }
}
