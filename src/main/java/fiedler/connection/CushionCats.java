package fiedler.connection;

import java.util.List;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.decoration.Cushion;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * Cats (Village &amp; Pillage, 2019) → cushions (Wilderness Bound, 2026).
 *
 * <p>Tame cats already sit on beds, chests and furnaces. Cushions arrived as entities, so the
 * {@code #cats_can_sit_on} block tag can never reach them. Now an idle tame cat sometimes walks to a free cushion
 * nearby and sits on it. Right-clicking a cushion a cat has claimed shoos the cat off (it hisses and won't come back
 * for a minute); right-click again to sit down yourself.
 */
public final class CushionCats {
    /** Same priority as the vanilla CatSitOnBlockGoal, so owners, temptation and panic still win. */
    public static final int GOAL_PRIORITY = 7;

    private CushionCats() {}

    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Cat cat)) return;
        if (goalOf(cat) == null) cat.goalSelector.addGoal(GOAL_PRIORITY, new CatSitOnCushionGoal(cat));
    }

    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getTarget() instanceof Cushion cushion)) return;
        if (shoo(cushion)) {
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
            event.setCanceled(true);
        }
    }

    /** Removes any cat sitting on the cushion. Returns true if there was one. */
    public static boolean shoo(Cushion cushion) {
        boolean shooed = false;
        for (Entity passenger : List.copyOf(cushion.getPassengers())) {
            if (passenger instanceof Cat cat) {
                cat.stopRiding();
                CatSitOnCushionGoal goal = goalOf(cat);
                if (goal != null) goal.shooed();
                cat.hiss();   // the variant-aware hiss (26.x cats have per-variant sound sets)
                shooed = true;
            }
        }
        return shooed;
    }

    public static @Nullable CatSitOnCushionGoal goalOf(Cat cat) {
        for (WrappedGoal wrapped : cat.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof CatSitOnCushionGoal goal) return goal;
        }
        return null;
    }
}
