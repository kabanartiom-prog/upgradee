package com.toolevolution;

import javax.annotation.Nullable;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;

public enum ToolKind {
    PICKAXE("pickaxe"),
    AXE("axe"),
    SHOVEL("shovel"),
    HOE("hoe"),
    SWORD("sword");

    private final String id;

    ToolKind(String id) {
        this.id = id;
    }

    public String translationKey() {
        return "tooltip." + ToolEvolution.MOD_ID + ".kind." + id;
    }

    @Nullable
    public static ToolKind of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        if (item instanceof PickaxeItem) return PICKAXE;
        if (item instanceof AxeItem) return AXE;
        if (item instanceof ShovelItem) return SHOVEL;
        if (item instanceof HoeItem) return HOE;
        if (item instanceof SwordItem) return SWORD;
        return null;
    }
}
