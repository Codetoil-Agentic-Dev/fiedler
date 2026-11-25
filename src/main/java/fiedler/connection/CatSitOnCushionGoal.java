package fiedler.connection;

import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.decoration.Cushion;
import org.jspecify.annotations.Nullable;

/** An idle tame cat walks to the nearest free cushion, sits on it for 30–90 seconds, then gets up. */
public class CatSitOnCushionGoal extends Goal {
    public static final double SEARCH_RADIUS = 8.0;
    private static final int GIVE_UP_AFTER_TICKS = 400;
    private static final int SHOOED_COOLDOWN_TICKS = 1200;

    private final Cat cat;
    private @Nullable Cushion target;
    private int cooldown;
    private int sitTicks;
    private int walkTicks;
    private boolean shooed;

    public CatSitOnCushionGoal(Cat cat) {
        this.cat = cat;
        this.cooldown = 100 + cat.getRandom().nextInt(300);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.cat.getVehicle() instanceof Cushion seat) {   // e.g. after a reload: finish the nap properly
            this.target = seat;
            this.sitTicks = this.napLength();
            return true;
        }
        if (!this.cat.isTame() || this.cat.isOrderedToSit() || this.cat.isPassenger() || this.cat.isLeashed()) return false;
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        this.cooldown = 200 + this.cat.getRandom().nextInt(400);
        this.target = this.findFreeCushion();
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        Cushion seat = this.target;
        if (this.shooed || seat == null || !seat.isAlive() || this.cat.isOrderedToSit()) return false;
        if (this.cat.isPassenger()) return this.cat.getVehicle() == seat && this.sitTicks > 0;
        return !seat.isVehicle() && this.walkTicks < GIVE_UP_AFTER_TICKS;
    }

    @Override
    public void start() {
        this.walkTicks = 0;
        this.shooed = false;
        if (!this.cat.isPassenger()) {
            this.sitTicks = this.napLength();
            if (this.target != null) this.cat.getNavigation().moveTo(this.target, 0.8);
        }
    }

    @Override
    public void stop() {
        if (this.cat.getVehicle() instanceof Cushion) this.cat.stopRiding();
        if (!this.cat.isOrderedToSit()) this.cat.setInSittingPose(false);
        this.target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Cushion seat = this.target;
        if (seat == null) return;
        if (this.cat.isPassenger()) {
            this.sitTicks--;
            this.cat.setInSittingPose(true);
            return;
        }
        this.walkTicks++;
        if (this.cat.distanceToSqr(seat) < 2.25 && !seat.isVehicle()) {
            if (this.cat.startRiding(seat)) {
                this.cat.getNavigation().stop();
                seat.playSound(SoundEvents.CUSHION_SIT, 0.6F, 1.4F);
            }
        } else if (this.walkTicks % 20 == 0) {
            this.cat.getNavigation().moveTo(seat, 0.8);
        }
    }

    /**
     * Called when a player shoos the cat off its cushion. This ends the running goal, so the cat doesn't walk straight
     * back onto the seat the player wanted, and keeps the cat away for a minute.
     */
    void shooed() {
        this.shooed = true;
        this.cooldown = SHOOED_COOLDOWN_TICKS;
    }

    /** Lets GameTests skip the random idle wait. */
    public void searchNow() {
        this.cooldown = 0;
    }

    private int napLength() {
        return 600 + this.cat.getRandom().nextInt(1200);
    }

    private @Nullable Cushion findFreeCushion() {
        return this.cat.level()
                .getEntitiesOfClass(Cushion.class, this.cat.getBoundingBox().inflate(SEARCH_RADIUS), c -> c.isAlive() && !c.isVehicle())
                .stream()
                .min(Comparator.comparingDouble(this.cat::distanceToSqr))
                .orElse(null);
    }
}
