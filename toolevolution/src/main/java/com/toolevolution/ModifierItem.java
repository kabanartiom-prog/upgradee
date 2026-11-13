package com.toolevolution;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class ModifierItem extends Item {
    private final Modification modification;

    public ModifierItem(Modification modification, Properties properties) {
        super(properties);
        this.modification = modification;
    }

    public Modification getModification() {
        return modification;
    }

    /** Modifier is carried on the cursor and clicked onto a tool in an inventory slot. */
    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        ItemStack tool = slot.getItem();
        if (tool.isEmpty() || !slot.mayPickup(player)) {
            return false;
        }
        if (!handle(stack, tool, player)) {
            return false;
        }
        slot.setChanged();
        return true;
    }

    /** Tool is carried on the cursor and clicked onto a modifier lying in a slot. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (other.isEmpty() || !slot.mayPickup(player)) {
            return false;
        }
        if (!handle(stack, other, player)) {
            return false;
        }
        slot.setChanged();
        return true;
    }

    private boolean handle(ItemStack modifierStack, ItemStack tool, Player player) {
        ToolKind kind = ToolKind.of(tool);
        if (kind == null || !modification.supports(kind)) {
            return false; // not a suitable tool: let vanilla swap the stacks as usual
        }
        boolean client = player.level().isClientSide;
        ToolData.Result result = ToolData.canInstall(tool, modification);
        if (result != ToolData.Result.OK) {
            if (client) {
                player.displayClientMessage(Component.translatable("message." + ToolEvolution.MOD_ID + "." + result.key)
                        .withStyle(ChatFormatting.RED), true);
            }
            return true;
        }
        ToolData.install(tool, modification);
        modifierStack.shrink(1);
        if (client) {
            player.displayClientMessage(Component.translatable("message." + ToolEvolution.MOD_ID + ".installed",
                    Component.translatable(modification.nameKey())).withStyle(ChatFormatting.GREEN), true);
            player.playSound(SoundEvents.SMITHING_TABLE_USE, 0.6F, 1.2F);
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(modification.descKey()).withStyle(ChatFormatting.GRAY));
        MutableComponent kinds = Component.empty();
        boolean first = true;
        for (ToolKind kind : modification.kinds()) {
            if (!first) {
                kinds.append(", ");
            }
            kinds.append(Component.translatable(kind.translationKey()));
            first = false;
        }
        tooltip.add(Component.translatable("tooltip." + ToolEvolution.MOD_ID + ".applies_to", kinds).withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip." + ToolEvolution.MOD_ID + ".how_to").withStyle(ChatFormatting.DARK_GRAY));
    }
}
