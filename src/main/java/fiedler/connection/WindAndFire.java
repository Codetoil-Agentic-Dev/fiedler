package fiedler.connection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Wind charges and breezes (Tricky Trials, 2024) → campfires (Village &amp; Pillage, 2019).
 *
 * <p>Vanilla wind bursts already blow out candles (AbstractCandleBlock#onExplosionHit), but campfires shrug them off.
 * This applies the same rule to campfires and soul campfires. Any explosion that only <em>triggers</em> blocks
 * counts, which is exactly the wind-burst family: wind charges, breeze shots and the mace's Wind Burst.
 */
public final class WindAndFire {
    private WindAndFire() {}

    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (event.getExplosion().getBlockInteraction() != Explosion.BlockInteraction.TRIGGER_BLOCK) return;
        Level level = event.getLevel();
        for (BlockPos pos : event.getAffectedBlocks()) {
            BlockState state = level.getBlockState(pos);
            if (CampfireBlock.isLitCampfire(state)) {
                level.levelEvent(null, LevelEvent.SOUND_EXTINGUISH_FIRE, pos, 0);
                CampfireBlock.douse(null, level, pos, state);
                level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
                level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
            }
        }
    }
}
