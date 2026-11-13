package com.toolevolution;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** All gameplay effects of the modifications. Registered on the Forge event bus (works in singleplayer and on servers). */
@Mod.EventBusSubscriber(modid = ToolEvolution.MOD_ID)
public final class ModEvents {
    private static final float DURABILITY_SAVE_CHANCE = 0.30F;
    private static final int LUMBERJACK_MAX_LOGS = 128;
    private static final int LUMBERJACK_RADIUS = 10;
    private static final float WIDE_HARDNESS_MARGIN = 2.0F;
    private static final int MAGNET_MARK_LIFETIME_TICKS = 5;

    /** Guards against recursion when the mod itself breaks / uses extra blocks (server thread only). */
    private static boolean processing = false;

    private record MagnetMark(UUID player, ResourceKey<Level> dimension, BlockPos pos, long time) {
    }

    private record DurabilityTrack(int slot, Item item, int damage) {
    }

    private static final List<MagnetMark> MAGNET_MARKS = new ArrayList<>();
    private static final Map<UUID, DurabilityTrack> DURABILITY = new HashMap<>();

    private ModEvents() {
    }

    // ------------------------------------------------------------------ speed

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack held = event.getEntity().getMainHandItem();
        List<Modification> mods = ToolData.get(held);
        if (mods.isEmpty() || held.getDestroySpeed(event.getState()) <= 1.0F) {
            return;
        }
        double bonus = 0;
        for (Modification mod : mods) {
            if (mod.group().equals("speed")) {
                bonus += mod.value();
            }
        }
        if (bonus > 0) {
            event.setNewSpeed(event.getNewSpeed() * (1.0F + (float) bonus));
        }
    }

    // ------------------------------------------------------------- durability

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) {
            return;
        }
        Player player = event.player;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !held.isDamageableItem() || !ToolData.has(held, Modification.DURABILITY)) {
            DURABILITY.remove(player.getUUID());
            return;
        }
        int slot = player.getInventory().selected;
        int damage = held.getDamageValue();
        DurabilityTrack last = DURABILITY.get(player.getUUID());
        if (last != null && last.slot() == slot && last.item() == held.getItem() && damage > last.damage()) {
            int spent = damage - last.damage();
            int refund = 0;
            for (int i = 0; i < spent; i++) {
                if (player.getRandom().nextFloat() < DURABILITY_SAVE_CHANCE) {
                    refund++;
                }
            }
            if (refund > 0) {
                held.setDamageValue(Math.max(last.damage(), damage - refund));
                damage = held.getDamageValue();
            }
        }
        DURABILITY.put(player.getUUID(), new DurabilityTrack(slot, held.getItem(), damage));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DURABILITY.remove(event.getEntity().getUUID());
    }

    // ----------------------------------------------- block breaking modifiers

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        List<Modification> mods = ToolData.get(held);
        if (mods.isEmpty()) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        if (mods.contains(Modification.MAGNET)) {
            long now = level.getGameTime();
            MAGNET_MARKS.removeIf(m -> now - m.time() > MAGNET_MARK_LIFETIME_TICKS);
            MAGNET_MARKS.add(new MagnetMark(player.getUUID(), level.dimension(), pos.immutable(), now));
        }

        if (processing || player.isCreative() || player.isShiftKeyDown()) {
            return;
        }

        if (mods.contains(Modification.WIDE_MINE) || mods.contains(Modification.WIDE_DIG)) {
            wideBreak(player, level, held, pos, state);
        } else if (mods.contains(Modification.LUMBERJACK)) {
            fellTree(player, level, held, pos, state);
        }
    }

    private static boolean allowed(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return level.mayInteract(player, pos)
                && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer());
    }

    private static boolean toolAlmostBroken(ItemStack held) {
        return held.isEmpty() || (held.isDamageableItem() && held.getMaxDamage() - held.getDamageValue() <= 1);
    }

    private static Direction lookedFace(ServerPlayer player, ServerLevel level, BlockPos target) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(player.getBlockReach() + 1.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target)) {
            return hit.getDirection();
        }
        return Direction.getNearest(look.x, look.y, look.z).getOpposite();
    }

    private static void wideBreak(ServerPlayer player, ServerLevel level, ItemStack held, BlockPos center, BlockState centerState) {
        if (!held.isCorrectToolForDrops(centerState)) {
            return;
        }
        Direction.Axis axis = lookedFace(player, level, center).getAxis();
        float centerHardness = centerState.getDestroySpeed(level, center);
        processing = true;
        try {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) {
                        continue;
                    }
                    if (toolAlmostBroken(held)) {
                        return;
                    }
                    BlockPos target = switch (axis) {
                        case Y -> center.offset(a, 0, b);
                        case X -> center.offset(0, a, b);
                        default -> center.offset(a, b, 0);
                    };
                    BlockState s = level.getBlockState(target);
                    if (s.isAir() || s.getBlock() instanceof LiquidBlock || s.hasBlockEntity()) {
                        continue;
                    }
                    float hardness = s.getDestroySpeed(level, target);
                    if (hardness < 0 || hardness > centerHardness + WIDE_HARDNESS_MARGIN) {
                        continue;
                    }
                    if (!held.isCorrectToolForDrops(s) || !allowed(player, level, target)) {
                        continue;
                    }
                    player.gameMode.destroyBlock(target);
                }
            }
        } finally {
            processing = false;
        }
    }

    private static boolean isLeafLike(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.WART_BLOCKS);
    }

    private static void fellTree(ServerPlayer player, ServerLevel level, ItemStack held, BlockPos origin, BlockState originState) {
        if (!originState.is(BlockTags.LOGS) || level.getBlockState(origin.below()).is(BlockTags.LOGS)) {
            return; // only triggers on the bottom log of a tree
        }
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> logs = new ArrayList<>();
        boolean hasLeaves = false;
        visited.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && logs.size() < LUMBERJACK_MAX_LOGS) {
            BlockPos cur = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos n = cur.offset(dx, dy, dz);
                        if (n.getY() < origin.getY()
                                || Math.abs(n.getX() - origin.getX()) > LUMBERJACK_RADIUS
                                || Math.abs(n.getZ() - origin.getZ()) > LUMBERJACK_RADIUS) {
                            continue;
                        }
                        BlockState s = level.getBlockState(n);
                        if (isLeafLike(s)) {
                            hasLeaves = true;
                            continue;
                        }
                        if (s.is(BlockTags.LOGS) && visited.add(n)) {
                            logs.add(n);
                            queue.add(n);
                        }
                    }
                }
            }
        }
        if (!hasLeaves || logs.isEmpty()) {
            return; // not a real tree (e.g. a log house): chop normally
        }
        processing = true;
        try {
            for (BlockPos target : logs) {
                if (toolAlmostBroken(held)) {
                    return;
                }
                BlockState s = level.getBlockState(target);
                if (!s.is(BlockTags.LOGS) || !held.isCorrectToolForDrops(s) || !allowed(player, level, target)) {
                    continue;
                }
                player.gameMode.destroyBlock(target);
            }
        } finally {
            processing = false;
        }
    }

    // ----------------------------------------------------------------- magnet

    @SubscribeEvent
    public static void onItemSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || MAGNET_MARKS.isEmpty()) {
            return;
        }
        if (!(event.getEntity() instanceof ItemEntity item) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        Iterator<MagnetMark> it = MAGNET_MARKS.iterator();
        while (it.hasNext()) {
            MagnetMark mark = it.next();
            if (now - mark.time() > MAGNET_MARK_LIFETIME_TICKS) {
                it.remove();
                continue;
            }
            if (!mark.dimension().equals(level.dimension())
                    || item.position().distanceToSqr(Vec3.atCenterOf(mark.pos())) > 4.0) {
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(mark.player());
            if (player == null || !player.isAlive()) {
                continue;
            }
            item.setPos(player.getX(), player.getY() + 0.25, player.getZ());
            item.setDeltaMovement(Vec3.ZERO);
            item.setNoPickUpDelay();
            return;
        }
    }

    // ------------------------------------------------------------------- hoe

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (processing || event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof HoeItem) || player.isShiftKeyDown() || !ToolData.has(held, Modification.WIDE_TILL)) {
            return;
        }
        if (event.getFace() == Direction.DOWN) {
            return;
        }
        BlockPos center = event.getPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (toolAlmostBroken(held)) {
                    return;
                }
                BlockPos target = center.offset(dx, 0, dz);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target).add(0, 0.5, 0), Direction.UP, target, false);
                PlayerInteractEvent.RightClickBlock probe =
                        new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, target, hit);
                boolean cancelled;
                processing = true;
                try {
                    cancelled = MinecraftForge.EVENT_BUS.post(probe);
                } finally {
                    processing = false;
                }
                if (cancelled || probe.getUseItem() == Event.Result.DENY) {
                    continue;
                }
                if (!level.mayInteract(player, target) || !player.mayUseItemAt(target, Direction.UP, held)) {
                    continue;
                }
                held.getItem().useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, held, hit));
            }
        }
    }

    // ----------------------------------------------------------------- sword

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || event.getAmount() <= 0) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || event.getSource().getDirectEntity() != player
                || event.getEntity() == player) {
            return;
        }
        if (!ToolData.has(player.getMainHandItem(), Modification.VAMPIRISM)) {
            return;
        }
        if (player.getHealth() < player.getMaxHealth()) {
            player.heal(Math.min(2.0F, Math.max(0.5F, event.getAmount() * 0.15F)));
        }
    }

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (!ToolData.has(player.getMainHandItem(), Modification.CRITICAL)) {
            return;
        }
        if (event.isVanillaCritical() || event.getResult() == Event.Result.ALLOW) {
            event.setDamageModifier(event.getDamageModifier() + (float) Modification.CRITICAL.value());
        }
    }
}
