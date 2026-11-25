package fiedler.client;

import fiedler.Fiedler;
import fiedler.connection.CatSitOnCushionGoal;
import fiedler.connection.CushionCats;
import fiedler.gametest.ClientTests;
import fiedler.gametest.FiedlerGameTests;
import fiedler.graph.FeatureGraph;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.Instruments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/**
 * GameTests that drive the real game client. The test bodies run on the integrated server's thread like any GameTest,
 * but every player action goes through the client: the local player uses items, right-clicks entities, holds the
 * use key and types commands, and the packets travel client→server as in real play. Each test then checks both what
 * the server did and what the client shows.
 *
 * <p>The tests need a client-controlled player, so they are registered only on the client. Each runs in its own
 * environment, which puts it in its own batch, because they share that one player.
 */
public final class FiedlerClientGameTests {
    private FiedlerClientGameTests() {}

    public static void registerTests(RegisterGameTestsEvent event) {
        ClientTests.TESTS.forEach((name, maxTicks) -> {
            Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Fiedler.id(name));
            event.registerTest(Fiedler.id(name), new FunctionGameTestInstance(
                    ResourceKey.create(Registries.TEST_FUNCTION, Fiedler.id(name)),
                    new TestData<>(environment, FiedlerGameTests.ARENA, maxTicks, 0, true)));
        });
    }

    public static void run(String name, GameTestHelper helper) {
        switch (name) {
            case "client_horn_alarm" -> hornAlarm(helper);
            case "client_cat_cushion" -> catCushion(helper);
            case "client_brush_moss" -> brushMoss(helper);
            case "client_graph_command" -> graphCommand(helper);
            case "client_enchanting_glyphs" -> enchantingGlyphs(helper);
            default -> helper.fail("unknown client test " + name);
        }
    }

    // ---- plumbing ---------------------------------------------------------------------------------------------------

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Runs a player action on the client thread. */
    private static void onClient(Runnable action) {
        mc().execute(action);
    }

    /** A wait step that keeps asking the client thread until {@code probe} holds, without ever blocking the server. */
    private static Runnable untilClient(GameTestHelper helper, Supplier<Boolean> probe, String message) {
        AtomicReference<CompletableFuture<Boolean>> pending = new AtomicReference<>();
        return () -> {
            CompletableFuture<Boolean> answer = pending.get();
            if (answer != null && answer.isDone() && Boolean.TRUE.equals(answer.getNow(Boolean.FALSE))) return;
            if (answer == null || answer.isDone()) pending.set(CompletableFuture.supplyAsync(() -> safely(probe), mc()));
            helper.assertTrue(false, Component.literal(message));
        };
    }

    private static boolean safely(Supplier<Boolean> probe) {
        try {
            return Boolean.TRUE.equals(probe.get());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, Component.literal(message));
    }

    /** The player the game client controls, as the server sees it. Mock players from other tests are not this one. */
    private static ServerPlayer realPlayer(GameTestHelper helper) {
        LocalPlayer local = mc().player;
        ServerPlayer player = local == null ? null : helper.getLevel().getServer().getPlayerList().getPlayer(local.getUUID());
        if (player == null) {
            helper.fail("no client-controlled player is in the world");
            throw new IllegalStateException("unreachable");
        }
        return player;
    }

    /** Moves the real player to an arena-relative spot and empties their hand. The client follows via the usual packets. */
    private static void place(GameTestHelper helper, ServerPlayer player, Vec3 relativeFeet, float yaw, float pitch) {
        onClient(() -> mc().options.keyUse.setDown(false));
        player.stopRiding();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.teleportTo(helper.getLevel(), at.x, at.y, at.z, Set.of(), yaw, pitch, true);
        player.setDeltaMovement(Vec3.ZERO);
    }

    /** {yaw, pitch} that make an eye at {@code eye} look at {@code target}. */
    private static float[] aim(Vec3 eye, Vec3 target) {
        Vec3 d = target.subtract(eye);
        return new float[]{(float) -Math.toDegrees(Math.atan2(d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()))};
    }

    private static void rightClick(int entityId) {
        Entity target = mc().level.getEntity(entityId);
        if (target != null) mc().gameMode.interact(mc().player, target, new EntityHitResult(target), InteractionHand.MAIN_HAND);
    }

    // ---- tests ------------------------------------------------------------------------------------------------------

    /** The client blows a Call horn: the server's villager takes cover, and the client sees the raider glow. */
    private static void hornAlarm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        Pillager pillager = helper.spawn(EntityTypes.PILLAGER, new BlockPos(7, 1, 7));
        pillager.setNoAi(true);
        int pillagerId = pillager.getId();
        ServerPlayer player = realPlayer(helper);
        place(helper, player, new Vec3(4.5, 1.0, 4.5), 0.0F, 0.0F);
        Holder<Instrument> call = level.registryAccess().lookupOrThrow(Registries.INSTRUMENT).getOrThrow(Instruments.CALL_GOAT_HORN);
        player.setItemInHand(InteractionHand.MAIN_HAND, InstrumentItem.create(Items.GOAT_HORN, call));

        helper.startSequence()
                .thenWaitUntil(untilClient(helper, () -> mc().player.getMainHandItem().is(Items.GOAT_HORN), "the client never received the goat horn"))
                .thenExecute(() -> onClient(() -> mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND)))
                .thenWaitUntil(() -> {
                    check(helper, villager.getBrain().getMemory(MemoryModuleType.HEARD_BELL_TIME).isPresent(), "the villager never heard the horn the client blew");
                    check(helper, pillager.hasEffect(MobEffects.GLOWING), "the raider does not glow on the server");
                })
                .thenWaitUntil(untilClient(helper, () -> {
                    Entity seen = mc().level.getEntity(pillagerId);
                    return seen != null && seen.isCurrentlyGlowing();
                }, "the client never showed the raider glowing"))
                .thenExecute(() -> {
                    onClient(() -> mc().gameMode.releaseUsingItem(mc().player));
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                })
                .thenSucceed();
    }

    /**
     * The client sees the tame cat sit on the cushion. One right-click from the client shoos it (without seating the
     * player, and the cat stays off), and a second right-click seats the player.
     */
    private static void catCushion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = realPlayer(helper);
        place(helper, player, new Vec3(5.5, 1.0, 2.5), 0.0F, 20.0F);
        Cushion cushion = EntityTypes.CUSHION.create(level, EntitySpawnReason.TRIGGERED);
        check(helper, cushion != null, "could not create a cushion");
        BlockPos seat = helper.absolutePos(new BlockPos(5, 1, 4));
        cushion.snapTo(Vec3.atCenterOfWithY(seat, seat.getY()), 0.0F, 0.0F);
        level.addFreshEntity(cushion);
        Cat cat = helper.spawn(EntityTypes.CAT, new BlockPos(2, 1, 4));
        cat.tame(player);
        CatSitOnCushionGoal goal = CushionCats.goalOf(cat);
        check(helper, goal != null, "cats should get the cushion goal when they join the level");
        goal.searchNow();
        int catId = cat.getId(), cushionId = cushion.getId();

        helper.startSequence()
                .thenWaitUntil(() -> check(helper, cat.getVehicle() == cushion, "the cat has not sat on the cushion"))
                .thenWaitUntil(untilClient(helper, () -> mc().level.getEntity(catId) instanceof Cat seen
                        && seen.getVehicle() != null && seen.getVehicle().getId() == cushionId && seen.isInSittingPose(),
                        "the client never showed the cat sitting on the cushion"))
                .thenExecute(() -> onClient(() -> rightClick(cushionId)))
                .thenWaitUntil(() -> {
                    check(helper, !cat.isPassenger(), "the client's right-click did not shoo the cat");
                    check(helper, !player.isPassenger(), "the first right-click should only shoo the cat, not seat the player");
                })
                .thenWaitUntil(untilClient(helper, () -> mc().level.getEntity(catId) instanceof Cat seen && !seen.isPassenger(),
                        "the client still shows the cat on the cushion"))
                .thenExecuteAfter(40, () -> check(helper, !cat.isPassenger(), "the shooed cat climbed back onto the cushion"))
                .thenExecute(() -> onClient(() -> rightClick(cushionId)))
                .thenWaitUntil(() -> check(helper, player.getVehicle() == cushion, "the second right-click should seat the player"))
                .thenWaitUntil(untilClient(helper, () -> mc().player.getVehicle() != null && mc().player.getVehicle().getId() == cushionId,
                        "the client does not show the player seated"))
                .thenExecute(player::stopRiding)
                .thenSucceed();
    }

    /** The client looks at a mossy block and holds right-click with a brush, like a player; the moss comes off. */
    private static void brushMoss(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(4, 1, 6);
        helper.setBlock(rel, Blocks.MOSSY_COBBLESTONE);
        BlockPos abs = helper.absolutePos(rel);
        ServerPlayer player = realPlayer(helper);
        Vec3 feet = new Vec3(4.5, 1.0, 4.5);
        float[] angles = aim(helper.absoluteVec(feet).add(0.0, player.getEyeHeight(), 0.0), helper.absoluteVec(new Vec3(4.5, 1.5, 6.5)));
        place(helper, player, feet, angles[0], angles[1]);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BRUSH));

        helper.startSequence()
                .thenWaitUntil(untilClient(helper, () -> mc().player.getMainHandItem().is(Items.BRUSH)
                        && mc().hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(abs),
                        "the client's crosshair never settled on the mossy block"))
                .thenExecute(() -> onClient(() -> mc().options.keyUse.setDown(true)))
                .thenWaitUntil(() -> check(helper, level.getBlockState(abs).is(Blocks.COBBLESTONE), "holding right-click with the brush has not scrubbed the block"))
                .thenExecute(() -> onClient(() -> mc().options.keyUse.setDown(false)))
                .thenWaitUntil(untilClient(helper, () -> mc().level.getBlockState(abs).is(Blocks.COBBLESTONE), "the client never showed the clean block"))
                .thenExecute(() -> {
                    helper.assertItemEntityPresent(Items.MOSS_CARPET, rel, 2.5);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                })
                .thenSucceed();
    }

    /** The client types /fiedler graph and /fiedler connections and receives the reports in chat. */
    private static void graphCommand(GameTestHelper helper) {
        ClientChatLog.clear();
        int connections = FeatureGraph.load().fiedlerEdges().size();
        helper.startSequence()
                .thenExecute(() -> onClient(() -> mc().player.connection.sendCommand("fiedler graph")))
                .thenWaitUntil(() -> check(helper, ClientChatLog.snapshot().stream().anyMatch(m -> m.contains("with Fiedler:") && m.contains("β₀=1")),
                        "the client never received the /fiedler graph report"))
                .thenExecute(() -> onClient(() -> mc().player.connection.sendCommand("fiedler connections")))
                .thenWaitUntil(() -> check(helper, ClientChatLog.snapshot().stream().filter(m -> m.startsWith("• ")).count() == connections,
                        "the client should receive one line per connection (" + connections + ")"))
                .thenSucceed();
    }

    /** On the client, chiseled shelves with books feed the enchanting table (that is what draws the glyph particles). */
    private static void enchantingGlyphs(GameTestHelper helper) {
        BlockPos table = new BlockPos(4, 1, 4);
        helper.setBlock(table, Blocks.ENCHANTING_TABLE);
        List<BlockPos> booked = EnchantingTableBlock.BOOKSHELF_OFFSETS.subList(0, 15);
        BlockPos bare = EnchantingTableBlock.BOOKSHELF_OFFSETS.get(15);
        BlockState full = Blocks.CHISELED_BOOKSHELF.defaultBlockState();
        for (BooleanProperty slot : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) full = full.setValue(slot, true);
        BlockState fullShelf = full;
        booked.forEach(offset -> helper.setBlock(table.offset(offset), fullShelf));
        helper.setBlock(table.offset(bare), Blocks.CHISELED_BOOKSHELF.defaultBlockState());
        BlockPos absTable = helper.absolutePos(table);

        helper.startSequence()
                .thenWaitUntil(untilClient(helper, () -> booked.stream().allMatch(o -> EnchantingTableBlock.isValidBookShelf(mc().level, absTable, o))
                        && !EnchantingTableBlock.isValidBookShelf(mc().level, absTable, bare),
                        "on the client, shelves with books should feed the enchanting table and the empty shelf should not"))
                .thenSucceed();
    }
}
