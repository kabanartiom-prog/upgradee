#!/usr/bin/env python3
"""Generates textures, item models, lang files and recipes for Tool Evolution (pure Python, no deps)."""
import json, os, struct, zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
MOD = "toolevolution"
ASSETS = os.path.join(ROOT, "assets", MOD)
DATA = os.path.join(ROOT, "data", MOD)
for d in ("textures/item", "models/item", "lang"):
    os.makedirs(os.path.join(ASSETS, d), exist_ok=True)
os.makedirs(os.path.join(DATA, "recipes"), exist_ok=True)

# id: (en name, ru name, en desc, ru desc, fill rgb, glyph rgb, glyph, dots)
GLYPHS = {
    "bolt": ["....##..", "...##...", "..##....", ".######.", "...##...", "..##....", ".##.....", "........"],
    "magnet": ["##....##", "##....##", "##....##", "##....##", "###..###", ".######.", "..####..", "........"],
    "shield": [".######.", "########", "########", "########", ".######.", "..####..", "...##...", "........"],
    "grid": ["##.##.##", "##.##.##", "........", "##.##.##", "##.##.##", "........", "##.##.##", "##.##.##"],
    "tree": ["...##...", "..####..", ".######.", "########", "..####..", "...##...", "...##...", "...##..."],
    "drop": ["...##...", "...##...", "..####..", ".######.", ".######.", ".######.", "..####..", "........"],
    "star": ["...##...", "...##...", "########", ".######.", "..####..", ".##..##.", "##....##", "........"],
}

MODS = {
    "speed_1": ("Speed I", "Скорость I", "Mining speed +15%", "Скорость копания +15%", (240, 200, 60), (70, 45, 0), "bolt", 1),
    "speed_2": ("Speed II", "Скорость II", "Mining speed +30%", "Скорость копания +30%", (240, 140, 40), (70, 30, 0), "bolt", 2),
    "speed_3": ("Speed III", "Скорость III", "Mining speed +45%", "Скорость копания +45%", (225, 70, 50), (255, 240, 200), "bolt", 3),
    "axe_speed": ("Chopping Speed", "Скорость рубки", "Chopping speed +30%", "Скорость рубки +30%", (90, 180, 90), (20, 60, 20), "bolt", 0),
    "shovel_speed": ("Digging Speed", "Скорость копания (лопата)", "Digging speed +30%", "Скорость копания +30%", (175, 125, 75), (50, 30, 10), "bolt", 0),
    "hoe_speed": ("Swiftness", "Ускорение", "Faster work on suitable blocks (+30%)", "Ускоряет работу с подходящими блоками (+30%)", (60, 190, 170), (10, 60, 55), "bolt", 0),
    "magnet": ("Magnet", "Магнит", "Pulls dropped items to you", "Притягивает выпавшие предметы к вам", (210, 60, 60), (255, 255, 255), "magnet", 0),
    "durability": ("Durability", "Прочность", "Uses 30% less durability", "Расход прочности меньше на 30%", (110, 130, 175), (240, 245, 255), "shield", 0),
    "wide_mine": ("Wide Mining", "Широкая копка (кирка)", "Mines a 3x3 area (sneak to disable)", "Копает область 3×3 (присядьте, чтобы отключить)", (90, 170, 225), (10, 40, 70), "grid", 0),
    "wide_dig": ("Wide Digging", "Широкая копка (лопата)", "Digs a 3x3 area (sneak to disable)", "Копает область 3×3 (присядьте, чтобы отключить)", (185, 140, 90), (60, 35, 10), "grid", 0),
    "wide_till": ("Wide Tilling", "Большая обработка", "Tills a 3x3 area (sneak to disable)", "Обрабатывает область 3×3 (присядьте, чтобы отключить)", (150, 200, 80), (40, 60, 10), "grid", 0),
    "lumberjack": ("Lumberjack", "Лесоруб", "Fells the whole tree from the bottom log (sneak to disable)", "Срубает всё дерево с нижнего бревна (присядьте, чтобы отключить)", (60, 140, 60), (240, 230, 200), "tree", 0),
    "vampirism": ("Vampirism", "Вампиризм", "Restores a little health on hit", "Восстанавливает немного здоровья при ударе", (150, 30, 75), (255, 200, 210), "drop", 0),
    "critical": ("Critical Strike", "Критический удар", "Critical hits deal +35% extra damage", "Критические удары наносят на 35% больше урона", (245, 235, 120), (150, 20, 20), "star", 0),
}

def clamp(v): return max(0, min(255, int(v)))
def shade(c, f): return tuple(clamp(x * f) for x in c)
def lighten(c, a): return tuple(clamp(x + a) for x in c)

def make_icon(fill, glyph_col, glyph, dots):
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    border, hi, lo = shade(fill, 0.40), lighten(fill, 45), shade(fill, 0.78)
    for y in range(1, 15):
        for x in range(1, 15):
            if (x in (1, 14)) and (y in (1, 14)):
                continue  # rounded corners
            edge = x in (1, 14) or y in (1, 14)
            if edge:
                c = border
            elif y == 2 or x == 2:
                c = hi
            elif y == 13 or x == 13:
                c = lo
            else:
                c = fill
            px[y][x] = c + (255,)
    for gy, row in enumerate(GLYPHS[glyph]):
        for gx, ch in enumerate(row):
            if ch == "#":
                px[3 + gy][4 + gx] = glyph_col + (255,)
    for i in range(dots):
        x0 = 8 - (dots * 3 - 1) // 2 + i * 3 + (1 if dots == 2 else 0)
        for dx in range(2):
            px[12][x0 + dx] = glyph_col + (255,)
    return px

def write_png(path, px):
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)

def dump(path, obj):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")

en = {"itemGroup.%s" % MOD: "Tool Evolution"}
ru = {"itemGroup.%s" % MOD: "Tool Evolution"}

for mid, (en_n, ru_n, en_d, ru_d, fill, gcol, glyph, dots) in MODS.items():
    write_png(os.path.join(ASSETS, "textures/item", mid + ".png"), make_icon(fill, gcol, glyph, dots))
    dump(os.path.join(ASSETS, "models/item", mid + ".json"),
         {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (MOD, mid)}})
    en["item.%s.%s" % (MOD, mid)] = "Modifier: " + en_n
    ru["item.%s.%s" % (MOD, mid)] = "Модификатор: " + ru_n
    en["modification.%s.%s" % (MOD, mid)] = en_n
    ru["modification.%s.%s" % (MOD, mid)] = ru_n
    en["modification.%s.%s.desc" % (MOD, mid)] = en_d
    ru["modification.%s.%s.desc" % (MOD, mid)] = ru_d

def common(d, tr):
    p = "tooltip.%s." % MOD
    m = "message.%s." % MOD
    d.update({
        p + "slots": tr["slots"], p + "applies_to": tr["applies"], p + "how_to": tr["how"],
        p + "kind.pickaxe": tr["pickaxe"], p + "kind.axe": tr["axe"], p + "kind.shovel": tr["shovel"],
        p + "kind.hoe": tr["hoe"], p + "kind.sword": tr["sword"],
        m + "installed": tr["installed"], m + "full": tr["full"], m + "duplicate": tr["dup"],
        m + "conflict": tr["conflict"], m + "wrong_tool": tr["wrong"],
    })

common(en, dict(slots="Modifications: %s/%s", applies="Applies to: %s", how="Pick it up with the cursor and click a tool in your inventory",
                pickaxe="Pickaxe", axe="Axe", shovel="Shovel", hoe="Hoe", sword="Sword",
                installed="Installed: %s", full="All 3 modification slots are used", dup="This modification is already installed",
                conflict="A modification of this type is already installed", wrong="This tool is not suitable"))
common(ru, dict(slots="Модификации: %s/%s", applies="Подходит для: %s", how="Возьмите курсором и кликните по инструменту в инвентаре",
                pickaxe="Кирка", axe="Топор", shovel="Лопата", hoe="Мотыга", sword="Меч",
                installed="Установлено: %s", full="Все 3 слота модификаций заняты", dup="Эта модификация уже установлена",
                conflict="Модификация этого типа уже установлена", wrong="Инструмент не подходит"))
dump(os.path.join(ASSETS, "lang", "en_us.json"), en)
dump(os.path.join(ASSETS, "lang", "ru_ru.json"), ru)

V = lambda i: {"item": "minecraft:" + i}
T = lambda t: {"tag": "minecraft:" + t}
M = lambda i: {"item": "%s:%s" % (MOD, i)}
RECIPES = {
    "speed_1": ([" R ", "RIR", " R "], {"R": V("redstone"), "I": V("iron_ingot")}),
    "speed_2": (["GSG", "RRR"], {"G": V("gold_ingot"), "S": M("speed_1"), "R": V("redstone")}),
    "speed_3": (["DSD", "RRR"], {"D": V("diamond"), "S": M("speed_2"), "R": V("redstone")}),
    "axe_speed": ([" R ", "RIR", " L "], {"R": V("redstone"), "I": V("iron_ingot"), "L": T("logs")}),
    "shovel_speed": ([" R ", "RIR", " C "], {"R": V("redstone"), "I": V("iron_ingot"), "C": V("clay_ball")}),
    "hoe_speed": ([" R ", "RIR", " W "], {"R": V("redstone"), "I": V("iron_ingot"), "W": V("wheat")}),
    "magnet": (["I I", "IEI", " R "], {"I": V("iron_ingot"), "E": V("ender_pearl"), "R": V("redstone")}),
    "durability": ([" O ", "IDI", " O "], {"O": V("obsidian"), "I": V("iron_ingot"), "D": V("diamond")}),
    "wide_mine": (["IGI", "GDG", "IGI"], {"I": V("iron_ingot"), "G": V("gold_ingot"), "D": V("diamond")}),
    "wide_dig": (["IGI", "GCG", "IGI"], {"I": V("iron_ingot"), "G": V("gold_ingot"), "C": V("clay_ball")}),
    "wide_till": (["IGI", "GHG", "IGI"], {"I": V("iron_ingot"), "G": V("gold_ingot"), "H": V("hay_block")}),
    "lumberjack": (["ILI", "LEL", "ILI"], {"I": V("iron_ingot"), "L": T("logs"), "E": V("emerald")}),
    "vampirism": (["SRS", "RGR", "SRS"], {"S": V("spider_eye"), "R": V("redstone"), "G": V("ghast_tear")}),
    "critical": (["BIB", "IDI", "BIB"], {"B": V("blaze_powder"), "I": V("iron_ingot"), "D": V("diamond")}),
}
for rid, (pattern, key) in RECIPES.items():
    dump(os.path.join(DATA, "recipes", rid + ".json"),
         {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key,
          "result": {"item": "%s:%s" % (MOD, rid), "count": 1}})
print("Generated", len(MODS), "modifiers")
