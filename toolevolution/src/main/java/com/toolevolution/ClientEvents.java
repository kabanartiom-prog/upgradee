package com.toolevolution;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ToolEvolution.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Modification> mods = ToolData.get(stack);
        if (mods.isEmpty()) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip." + ToolEvolution.MOD_ID + ".slots", mods.size(), ToolData.MAX_SLOTS)
                .withStyle(ChatFormatting.GOLD));
        for (Modification mod : mods) {
            MutableComponent line = Component.literal("- ")
                    .append(Component.translatable(mod.nameKey()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(mod.descKey()).withStyle(ChatFormatting.GRAY));
            lines.add(line);
        }
        List<Component> tooltip = event.getToolTip();
        tooltip.addAll(Math.min(1, tooltip.size()), lines);
    }
}
