package com.toolevolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Reads and writes installed modifications in the ItemStack NBT (persists everywhere the stack goes). */
public final class ToolData {
    public static final String TAG_KEY = "ToolEvolutionMods";
    public static final int MAX_SLOTS = 3;

    public enum Result {
        OK("installed"),
        WRONG_TOOL("wrong_tool"),
        FULL("full"),
        DUPLICATE("duplicate"),
        CONFLICT("conflict");

        public final String key;

        Result(String key) {
            this.key = key;
        }
    }

    private ToolData() {
    }

    public static List<Modification> get(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) {
            return Collections.emptyList();
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_KEY, Tag.TAG_LIST)) {
            return Collections.emptyList();
        }
        ToolKind kind = ToolKind.of(stack);
        if (kind == null) {
            return Collections.emptyList();
        }
        ListTag list = tag.getList(TAG_KEY, Tag.TAG_STRING);
        List<Modification> result = new ArrayList<>(MAX_SLOTS);
        for (int i = 0; i < list.size() && result.size() < MAX_SLOTS; i++) {
            Modification m = Modification.byId(list.getString(i));
            if (m != null && m.supports(kind) && !result.contains(m)) {
                result.add(m);
            }
        }
        return result;
    }

    public static boolean has(ItemStack stack, Modification mod) {
        return get(stack).contains(mod);
    }

    public static Result canInstall(ItemStack tool, Modification mod) {
        ToolKind kind = ToolKind.of(tool);
        if (kind == null || !mod.supports(kind)) {
            return Result.WRONG_TOOL;
        }
        List<Modification> current = get(tool);
        if (current.contains(mod)) {
            return Result.DUPLICATE;
        }
        if (current.size() >= MAX_SLOTS) {
            return Result.FULL;
        }
        for (Modification m : current) {
            if (m.group().equals(mod.group())) {
                return Result.CONFLICT;
            }
        }
        return Result.OK;
    }

    public static void install(ItemStack tool, Modification mod) {
        CompoundTag tag = tool.getOrCreateTag();
        ListTag list = tag.contains(TAG_KEY, Tag.TAG_LIST) ? tag.getList(TAG_KEY, Tag.TAG_STRING) : new ListTag();
        list.add(StringTag.valueOf(mod.id()));
        tag.put(TAG_KEY, list);
    }
}
