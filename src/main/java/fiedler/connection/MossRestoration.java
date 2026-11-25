package fiedler.connection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import org.jspecify.annotations.Nullable;

/**
 * Brush (Trails &amp; Tales, 2023) → mossy cobblestone (Classic, 2009) and mossy stone bricks (Beta 1.8, 2011).
 *
 * <p>In vanilla, brushing anything except suspicious sand or gravel only makes dust. Mossy blocks can be crafted,
 * but there was never a way back. Now a few strokes of a brush scrub the moss off, keeping the block's shape
 * (stairs keep their facing, walls their connections), and drop a moss carpet. Nothing is duplicated: crafting a
 * mossy block costs a whole moss block and you get back two thirds of one.
 */
public final class MossRestoration {
    /** Brush strokes needed. BrushItem strokes every 10 ticks, so this is three seconds of brushing. */
    public static final int STROKES_TO_RESTORE = 6;

    public static final Map<Block, Block> CLEAN = Map.of(
            Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE,
            Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.COBBLESTONE_STAIRS,
            Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.COBBLESTONE_SLAB,
            Blocks.MOSSY_COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL,
            Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS,
            Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.STONE_BRICK_STAIRS,
            Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.STONE_BRICK_SLAB,
            Blocks.MOSSY_STONE_BRICK_WALL, Blocks.STONE_BRICK_WALL);

    private static final UUID NO_PLAYER = new UUID(0L, 0L);
    private static final Map<UUID, Progress> PROGRESS = new HashMap<>();

    private record Progress(BlockPos pos, int strokes) {}

    private MossRestoration() {}

    /** Mirrors BrushItem#onUseTick: one stroke on the 5th tick of every 10-tick swing, aimed by the same ray. */
    public static void onBrushTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player) || !(player.level() instanceof ServerLevel level)) return;
        ItemStack brush = event.getItem();
        if (!(brush.getItem() instanceof BrushItem)) return;
        int elapsed = brush.getUseDuration(player) - event.getDuration() + 1;
        if (elapsed % 10 != 5) return;
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player, EntitySelector.CAN_BE_PICKED, player.blockInteractionRange());
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            stroke(level, blockHit.getBlockPos(), blockHit.getDirection(), player, brush);
        }
    }

    /**
     * One brush stroke on {@code pos}.
     *
     * @return true if this stroke finished restoring the block
     */
    public static boolean stroke(ServerLevel level, BlockPos pos, Direction face, @Nullable Player player, ItemStack brush) {
        UUID key = player == null ? NO_PLAYER : player.getUUID();
        BlockState state = level.getBlockState(pos);
        Block clean = CLEAN.get(state.getBlock());
        if (clean == null) {
            PROGRESS.remove(key);
            return false;
        }
        Progress previous = PROGRESS.get(key);
        int strokes = previous != null && previous.pos().equals(pos) ? previous.strokes() + 1 : 1;
        if (strokes < STROKES_TO_RESTORE) {
            PROGRESS.put(key, new Progress(pos.immutable(), strokes));
            return false;
        }
        PROGRESS.remove(key);

        BlockState restored = copyProperties(state, clean.defaultBlockState());
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        level.setBlock(pos, restored, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.MOSS_CARPET_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        Block.popResourceFromFace(level, pos, face, new ItemStack(Items.MOSS_CARPET));
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        if (player != null && !brush.isEmpty()) {
            brush.hurtAndBreak(1, player, player.getUsedItemHand().asEquipmentSlot());
        }
        return true;
    }

    /** Copies every property the two states share (facing, half, shape, slab type, wall sides, waterlogging...). */
    public static BlockState copyProperties(BlockState from, BlockState to) {
        for (Property<?> property : from.getProperties()) {
            if (to.hasProperty(property)) to = copy(from, to, property);
        }
        return to;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }
}
