package fiedler.connection;

import fiedler.Fiedler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.component.InstrumentComponent;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.VanillaGameEvent;

/**
 * Goat horns (The Wild Update, 2022) → bells, villagers and raids (Village &amp; Pillage, 2019).
 *
 * <p>A goat horn is a war horn, yet villagers ignore it. Now blowing a horn from {@code #fiedler:sounds_village_alarm}
 * (default: Call and Seek) does what ringing a bell does, using the bell's own radii. Villagers within 32 blocks take
 * cover, and raiders within 48 blocks glow for three seconds. Bells keep their role at the village centre; the horn
 * is the portable version.
 */
public final class HornAlarm {
    public static final TagKey<Instrument> SOUNDS_ALARM = TagKey.create(Registries.INSTRUMENT, Fiedler.id("sounds_village_alarm"));
    public static final double HEAR_RADIUS = 32.0;      // BellBlockEntity.HEAR_BELL_RADIUS
    public static final double REVEAL_RADIUS = 48.0;    // BellBlockEntity.HIGHLIGHT_RAIDERS_RADIUS
    public static final int GLOW_TICKS = 60;            // BellBlockEntity.GLOW_DURATION

    private HornAlarm() {}

    public static void onGameEvent(VanillaGameEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.getVanillaEvent().is(GameEvent.INSTRUMENT_PLAY.key())) return;
        if (!(event.getCause() instanceof LivingEntity user)) return;
        InstrumentComponent horn = user.getUseItem().get(DataComponents.INSTRUMENT);
        if (horn != null && horn.instrument().is(SOUNDS_ALARM)) {
            soundAlarm(level, event.getEventPosition());
        }
    }

    /** @return how many entities heard or were revealed (for tests and the advancement-free feedback loop) */
    public static int soundAlarm(ServerLevel level, Vec3 at) {
        int affected = 0;
        AABB box = new AABB(BlockPos.containing(at)).inflate(REVEAL_RADIUS);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            double d2 = entity.distanceToSqr(at);
            if (d2 <= HEAR_RADIUS * HEAR_RADIUS) {
                // Exactly what BellBlockEntity#updateEntities does: brains without the module ignore it.
                entity.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, level.getGameTime());
            }
            if (d2 <= REVEAL_RADIUS * REVEAL_RADIUS && entity.is(EntityTypeTags.RAIDERS)) {
                entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS));
                affected++;
            } else if (d2 <= HEAR_RADIUS * HEAR_RADIUS) {
                affected++;
            }
        }
        return affected;
    }
}
