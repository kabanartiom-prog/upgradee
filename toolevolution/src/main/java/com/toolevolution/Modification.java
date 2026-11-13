package com.toolevolution;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

public enum Modification {
    SPEED_1("speed_1", "speed", 0.15, ToolKind.PICKAXE),
    SPEED_2("speed_2", "speed", 0.30, ToolKind.PICKAXE),
    SPEED_3("speed_3", "speed", 0.45, ToolKind.PICKAXE),
    AXE_SPEED("axe_speed", "speed", 0.30, ToolKind.AXE),
    SHOVEL_SPEED("shovel_speed", "speed", 0.30, ToolKind.SHOVEL),
    HOE_SPEED("hoe_speed", "speed", 0.30, ToolKind.HOE),
    MAGNET("magnet", "magnet", 0, ToolKind.PICKAXE, ToolKind.SHOVEL),
    DURABILITY("durability", "durability", 0.30, ToolKind.PICKAXE, ToolKind.AXE, ToolKind.HOE, ToolKind.SWORD),
    WIDE_MINE("wide_mine", "wide", 0, ToolKind.PICKAXE),
    WIDE_DIG("wide_dig", "wide", 0, ToolKind.SHOVEL),
    WIDE_TILL("wide_till", "wide", 0, ToolKind.HOE),
    LUMBERJACK("lumberjack", "lumberjack", 0, ToolKind.AXE),
    VAMPIRISM("vampirism", "vampirism", 0, ToolKind.SWORD),
    CRITICAL("critical", "critical", 0.35, ToolKind.SWORD);

    private static final Map<String, Modification> BY_ID = new HashMap<>();

    static {
        for (Modification m : values()) {
            BY_ID.put(m.id, m);
        }
    }

    private final String id;
    private final String group;
    private final double value;
    private final Set<ToolKind> kinds;

    Modification(String id, String group, double value, ToolKind... kinds) {
        this.id = id;
        this.group = group;
        this.value = value;
        this.kinds = EnumSet.copyOf(Arrays.asList(kinds));
    }

    public String id() {
        return id;
    }

    /** Modifications in the same group cannot be installed together on one tool. */
    public String group() {
        return group;
    }

    public double value() {
        return value;
    }

    public Set<ToolKind> kinds() {
        return kinds;
    }

    public boolean supports(ToolKind kind) {
        return kinds.contains(kind);
    }

    public String nameKey() {
        return "modification." + ToolEvolution.MOD_ID + "." + id;
    }

    public String descKey() {
        return "modification." + ToolEvolution.MOD_ID + "." + id + ".desc";
    }

    @Nullable
    public static Modification byId(String id) {
        return BY_ID.get(id);
    }
}
