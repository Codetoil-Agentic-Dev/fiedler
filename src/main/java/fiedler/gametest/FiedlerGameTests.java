package fiedler.gametest;

import fiedler.Fiedler;
import fiedler.connection.CatSitOnCushionGoal;
import fiedler.connection.CushionCats;
import fiedler.connection.HornAlarm;
import fiedler.connection.MossRestoration;
import fiedler.graph.FeatureGraph;
import fiedler.graph.SpectralGraph;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SulfurCubeArchetype;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.Instruments;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * One GameTest per advertised connection, plus {@code feature_graph}, which checks that every connection named in
 * the design graph has a test here. A connection without a test can't ship.
 */
@SuppressWarnings("removal") // GameTestHelper#makeMockServerPlayerInLevel is still the only in-level mock player in 26.3
public final class FiedlerGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Fiedler.MODID);
    /** name → max ticks */
    private static final Map<String, Integer> TESTS = new LinkedHashMap<>();
    public static final Identifier ARENA = Fiedler.id("arena");    // 9×6×9, smooth-stone floor, open sky

    static {
        test("moss_restoration", 20, FiedlerGameTests::mossRestoration);
        test("horn_alarm", 20, FiedlerGameTests::hornAlarm);
        test("wind_extinguishes_campfire", 20, FiedlerGameTests::windExtinguishesCampfire);
        test("cat_sits_on_cushion", 600, FiedlerGameTests::catSitsOnCushion);
        test("chiseled_bookshelf_power", 20, FiedlerGameTests::chiseledBookshelfPower);
        test("resin_sticky_piston", 20, FiedlerGameTests::resinStickyPiston);
        test("sulfur_cube_archetypes", 20, FiedlerGameTests::sulfurCubeArchetypes);
        test("sulfur_gunpowder", 20, FiedlerGameTests::sulfurGunpowder);
        test("feature_graph", 20, FiedlerGameTests::featureGraph);
        test("graph_command", 20, FiedlerGameTests::graphCommand);
        // Client tests: functions only. Their instances are registered by fiedler.client.FiedlerClientGameTests.
        ClientTests.TESTS.keySet().forEach(name -> FUNCTIONS.register(name, () -> helper -> ClientTests.run(name, helper)));
    }

    private FiedlerGameTests() {}

    private static void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
        FUNCTIONS.register(name, () -> body);
        TESTS.put(name, maxTicks);
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(FiedlerGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Fiedler.id("default"));
        TESTS.forEach((name, maxTicks) -> event.registerTest(Fiedler.id(name), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, Fiedler.id(name)),
                new TestData<>(environment, ARENA, maxTicks, 0, true))));
    }

    // --------------------------------------------------------------------------------------------------------------

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, Component.literal(message));
    }

    /** Brush → mossy stone: six strokes restore the clean block with its shape intact and drop a moss carpet. */
    private static void mossRestoration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(4, 1, 4);
        BlockState stairs = Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP);
        helper.setBlock(rel, stairs);
        BlockPos abs = helper.absolutePos(rel);
        for (int stroke = 1; stroke < MossRestoration.STROKES_TO_RESTORE; stroke++) {
            check(helper, !MossRestoration.stroke(level, abs, Direction.UP, null, ItemStack.EMPTY), "restored too early, at stroke " + stroke);
        }
        check(helper, MossRestoration.stroke(level, abs, Direction.UP, null, ItemStack.EMPTY), "stroke " + MossRestoration.STROKES_TO_RESTORE + " should restore");
        BlockState after = level.getBlockState(abs);
        check(helper, after.is(Blocks.STONE_BRICK_STAIRS), "expected stone brick stairs, got " + after);
        check(helper, after.getValue(StairBlock.FACING) == Direction.EAST && after.getValue(StairBlock.HALF) == Half.TOP, "shape not kept: " + after);
        helper.assertItemEntityPresent(Items.MOSS_CARPET, rel.above(), 2.0);

        // Blocks that were never mossy are left alone, however long you brush.
        helper.setBlock(rel.east(), Blocks.COBBLESTONE);
        for (int stroke = 0; stroke < 3 * MossRestoration.STROKES_TO_RESTORE; stroke++) {
            MossRestoration.stroke(level, abs.east(), Direction.UP, null, ItemStack.EMPTY);
        }
        check(helper, level.getBlockState(abs.east()).is(Blocks.COBBLESTONE), "plain cobblestone must not change");

        // End to end, through the same calls LivingEntity#updateUsingItem makes each tick: the NeoForge use-tick event,
        // then BrushItem#onUseTick. A player 2 blocks away looks down at a mossy block and keeps brushing.
        BlockPos target = new BlockPos(6, 1, 6);
        helper.setBlock(target, Blocks.MOSSY_COBBLESTONE);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 feet = helper.absoluteVec(new Vec3(6.5, 1.0, 4.5)), aim = helper.absoluteVec(new Vec3(6.5, 1.5, 6.5));
        Vec3 eye = feet.add(0.0, player.getEyeHeight(), 0.0), d = aim.subtract(eye);
        float yaw = (float) (-Math.toDegrees(Math.atan2(d.x, d.z))), pitch = (float) (-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
        player.snapTo(feet.x, feet.y, feet.z, yaw, pitch);
        ItemStack brush = new ItemStack(Items.BRUSH);
        player.setItemInHand(InteractionHand.MAIN_HAND, brush);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        int duration = brush.getUseDuration(player);
        for (int remaining = duration; remaining > duration - 10 * MossRestoration.STROKES_TO_RESTORE; remaining--) {
            int r = net.neoforged.neoforge.event.EventHooks.onItemUseTick(player, brush, remaining);
            brush.onUseTick(level, player, r);
        }
        check(helper, level.getBlockState(helper.absolutePos(target)).is(Blocks.COBBLESTONE), "60 ticks of real brushing should restore the block");
        // Wear follows vanilla's rule. The mock player's abilities depend on where the test runs (creative-like on the
        // dedicated test server, survival-like inside a survival singleplayer world), so derive the expectation.
        int expectedWear = player.hasInfiniteMaterials() ? 0 : 1;
        check(helper, brush.getDamageValue() == expectedWear, "brush wear should be " + expectedWear + " here, got " + brush.getDamageValue());
        // ...while in survival one restoration costs exactly one point of durability.
        BlockPos second = new BlockPos(2, 1, 6);
        helper.setBlock(second, Blocks.MOSSY_STONE_BRICK_WALL);
        Player survivor = helper.makeMockServerPlayer(GameType.SURVIVAL);
        ItemStack survivalBrush = new ItemStack(Items.BRUSH);
        for (int stroke = 0; stroke < MossRestoration.STROKES_TO_RESTORE; stroke++) {
            MossRestoration.stroke(level, helper.absolutePos(second), Direction.NORTH, survivor, survivalBrush);
        }
        check(helper, level.getBlockState(helper.absolutePos(second)).is(Blocks.STONE_BRICK_WALL), "walls restore too");
        check(helper, survivalBrush.getDamageValue() == 1, "survival restoration costs one durability point, got " + survivalBrush.getDamageValue());

        // Every mapping pairs blocks of the same shape family.
        MossRestoration.CLEAN.forEach((mossy, clean) -> check(helper,
                MossRestoration.copyProperties(mossy.defaultBlockState(), clean.defaultBlockState()).getProperties().equals(mossy.defaultBlockState().getProperties()),
                mossy + " and " + clean + " should share all block-state properties"));
        helper.succeed();
    }

    /** Goat horn → village alarm: Call warns villagers and reveals raiders; Ponder (not in the tag) does nothing. */
    private static void hornAlarm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        Pillager pillager = helper.spawn(EntityTypes.PILLAGER, new BlockPos(7, 1, 7));
        pillager.setNoAi(true);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 centre = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
        player.snapTo(centre.x, centre.y, centre.z, 0.0F, 0.0F);
        Registry<Instrument> instruments = level.registryAccess().lookupOrThrow(Registries.INSTRUMENT);

        blow(level, player, InstrumentItem.create(Items.GOAT_HORN, instruments.getOrThrow(Instruments.PONDER_GOAT_HORN)));
        check(helper, villager.getBrain().getMemory(MemoryModuleType.HEARD_BELL_TIME).isEmpty(), "Ponder is not an alarm horn");
        check(helper, !pillager.hasEffect(MobEffects.GLOWING), "Ponder must not reveal raiders");

        blow(level, player, InstrumentItem.create(Items.GOAT_HORN, instruments.getOrThrow(Instruments.CALL_GOAT_HORN)));
        check(helper, villager.getBrain().getMemory(MemoryModuleType.HEARD_BELL_TIME).isPresent(), "villager should have heard the alarm");
        check(helper, pillager.hasEffect(MobEffects.GLOWING), "raider should glow");
        check(helper, HornAlarm.soundAlarm(level, centre) >= 2, "direct call should reach both mobs");
        helper.succeed();
    }

    private static void blow(ServerLevel level, ServerPlayer player, ItemStack horn) {
        player.stopUsingItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, horn);
        horn.getItem().use(level, player, InteractionHand.MAIN_HAND);   // vanilla path: startUsingItem + INSTRUMENT_PLAY
    }

    /** Wind burst → campfires: both kinds are blown out inside the burst; one further away keeps burning. */
    private static void windExtinguishesCampfire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos near = new BlockPos(4, 1, 4), soul = new BlockPos(5, 1, 4), far = new BlockPos(1, 1, 7);
        helper.setBlock(near, Blocks.CAMPFIRE.defaultBlockState());
        helper.setBlock(soul, Blocks.SOUL_CAMPFIRE.defaultBlockState());
        helper.setBlock(far, Blocks.CAMPFIRE.defaultBlockState());
        check(helper, CampfireBlock.isLitCampfire(level.getBlockState(helper.absolutePos(near))), "campfires start lit");
        Vec3 c = helper.absoluteVec(new Vec3(5.0, 1.4, 4.5));
        // Exactly the parameters WindCharge#explode uses.
        level.explode(null, null, AbstractWindCharge.EXPLOSION_DAMAGE_CALCULATOR, c.x, c.y, c.z, 1.2F, false, Level.ExplosionInteraction.TRIGGER,
                ParticleTypes.GUST_EMITTER_SMALL, ParticleTypes.GUST_EMITTER_LARGE, WeightedList.of(), SoundEvents.WIND_CHARGE_BURST);
        check(helper, !CampfireBlock.isLitCampfire(level.getBlockState(helper.absolutePos(near))), "campfire inside the burst should be out");
        check(helper, !CampfireBlock.isLitCampfire(level.getBlockState(helper.absolutePos(soul))), "soul campfire inside the burst should be out");
        check(helper, CampfireBlock.isLitCampfire(level.getBlockState(helper.absolutePos(far))), "campfire outside the burst keeps burning");
        helper.succeed();
    }

    /** Cat → cushion: an idle tame cat walks to a free cushion and sits on it; shooing makes it hop off. */
    private static void catSitsOnCushion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cat cat = helper.spawn(EntityTypes.CAT, new BlockPos(1, 1, 4));
        // A tame cat needs its owner nearby: with no owner, vanilla's SitWhenOrderedToGoal keeps it still.
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        Vec3 ownerPos = helper.absoluteVec(new Vec3(3.5, 1.0, 1.5));
        owner.snapTo(ownerPos.x, ownerPos.y, ownerPos.z, 0.0F, 0.0F);
        cat.tame(owner);
        Cushion cushion = EntityTypes.CUSHION.create(level, EntitySpawnReason.TRIGGERED);
        check(helper, cushion != null, "could not create a cushion");
        BlockPos seat = helper.absolutePos(new BlockPos(5, 1, 4));
        cushion.snapTo(Vec3.atCenterOfWithY(seat, seat.getY()), 0.0F, 0.0F);
        level.addFreshEntity(cushion);
        CatSitOnCushionGoal goal = CushionCats.goalOf(cat);
        check(helper, goal != null, "cats should get the cushion goal when they join the level");
        goal.searchNow();
        helper.startSequence()
                .thenWaitUntil(() -> check(helper, cat.getVehicle() == cushion, "cat has not sat on the cushion yet"))
                .thenExecute(() -> {
                    check(helper, CushionCats.shoo(cushion), "shooing should report the cat");
                    check(helper, !cat.isPassenger(), "shooed cat should be off the cushion");
                })
                // Regression: the goal used to keep running after a shoo and put the cat straight back on the seat.
                .thenExecuteAfter(60, () -> check(helper, !cat.isPassenger() && !cushion.isVehicle(), "a shooed cat must not climb back onto the cushion"))
                .thenSucceed();
    }

    /** Chiseled bookshelf → enchanting table: books on shelves count, 6 books = 1 bookshelf, measured in the real menu. */
    private static void chiseledBookshelfPower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState empty = Blocks.CHISELED_BOOKSHELF.defaultBlockState();
        BlockState half = withBooks(empty, 3), full = withBooks(empty, 6);
        check(helper, empty.getEnchantPowerBonus(level, BlockPos.ZERO) == 0.0F, "empty shelf: no power");
        check(helper, half.getEnchantPowerBonus(level, BlockPos.ZERO) == 0.5F, "3 books: half a bookshelf");
        check(helper, full.getEnchantPowerBonus(level, BlockPos.ZERO) == 1.0F, "6 books: one bookshelf");

        BlockPos table = new BlockPos(4, 1, 4);
        helper.setBlock(table, Blocks.ENCHANTING_TABLE);
        List<BlockPos> ring = EnchantingTableBlock.BOOKSHELF_OFFSETS.subList(0, 15);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        EnchantmentMenu menu = new EnchantmentMenu(1, player.getInventory(), ContainerLevelAccess.create(level, helper.absolutePos(table)));

        ring.forEach(offset -> helper.setBlock(table.offset(offset), full));
        int fullCost = topCost(menu);
        check(helper, fullCost == 30, "15 full chiseled shelves = 15 bookshelves should give a level-30 offer, got " + fullCost);

        ring.forEach(offset -> helper.setBlock(table.offset(offset), half));
        int halfCost = topCost(menu);       // power 15 × 0.5 = 7.5 → 7, so the top offer lies in [14, 18]
        check(helper, halfCost >= 14 && halfCost <= 18, "half-full shelves should give power 7 (top offer 14–18), got " + halfCost);

        ring.forEach(offset -> helper.setBlock(table.offset(offset), empty));
        int emptyCost = topCost(menu);
        check(helper, emptyCost <= 8, "empty shelves give no power (top offer ≤ 8), got " + emptyCost);
        helper.succeed();
    }

    private static BlockState withBooks(BlockState shelf, int books) {
        List<BooleanProperty> slots = ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES;
        for (int i = 0; i < slots.size(); i++) shelf = shelf.setValue(slots.get(i), i < books);
        return shelf;
    }

    private static int topCost(EnchantmentMenu menu) {
        menu.getSlot(0).set(ItemStack.EMPTY);
        menu.getSlot(0).set(new ItemStack(Items.IRON_PICKAXE));
        return menu.costs[2];
    }

    /** Resin → sticky piston, while the slimeball recipe keeps working (nothing made obsolete). */
    private static void resinStickyPiston(GameTestHelper helper) {
        check(helper, crafts(helper, 1, 2, List.of(Items.RESIN_CLUMP, Items.PISTON)).is(Items.STICKY_PISTON), "resin over piston should craft a sticky piston");
        check(helper, crafts(helper, 1, 2, List.of(Items.SLIME_BALL, Items.PISTON)).is(Items.STICKY_PISTON), "the vanilla slimeball recipe must still work");
        helper.succeed();
    }

    /** Sulfur → gunpowder: spike + charcoal + bone meal, in any arrangement. */
    private static void sulfurGunpowder(GameTestHelper helper) {
        ItemStack out = crafts(helper, 3, 1, List.of(Items.SULFUR_SPIKE, Items.CHARCOAL, Items.BONE_MEAL));
        check(helper, out.is(Items.GUNPOWDER) && out.getCount() == 2, "expected 2 gunpowder, got " + out);
        ItemStack shuffled = crafts(helper, 2, 2, List.of(Items.BONE_MEAL, Items.AIR, Items.SULFUR_SPIKE, Items.CHARCOAL));
        check(helper, shuffled.is(Items.GUNPOWDER), "shapeless: order must not matter, got " + shuffled);
        check(helper, crafts(helper, 2, 1, List.of(Items.SULFUR_SPIKE, Items.CHARCOAL)).isEmpty(), "two ingredients are not enough");
        helper.succeed();
    }

    private static ItemStack crafts(GameTestHelper helper, int width, int height, List<Item> items) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(width, height, items.stream().map(i -> i == Items.AIR ? ItemStack.EMPTY : new ItemStack(i)).toList());
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(recipe -> recipe.value().assemble(input))
                .orElse(ItemStack.EMPTY);
    }

    /** Slime block → bouncy and honey block → sticky Sulfur Cube archetypes, and both are swallowable. */
    private static void sulfurCubeArchetypes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SulfurCube cube = helper.spawn(EntityTypes.SULFUR_CUBE, new BlockPos(4, 1, 4));
        Registry<SulfurCubeArchetype> archetypes = level.registryAccess().lookupOrThrow(Registries.SULFUR_CUBE_ARCHETYPE);
        expectArchetype(helper, cube, archetypes, Items.SLIME_BLOCK, "bouncy");
        expectArchetype(helper, cube, archetypes, Items.HONEY_BLOCK, "sticky");
        helper.succeed();
    }

    private static void expectArchetype(GameTestHelper helper, SulfurCube cube, Registry<SulfurCubeArchetype> registry, Item item, String archetype) {
        ItemStack stack = new ItemStack(item);
        check(helper, stack.is(ItemTags.SULFUR_CUBE_SWALLOWABLE), item + " should be swallowable");
        List<Identifier> matches = cube.matchingArchetypes(stack).stream().map(registry::getKey).toList();
        check(helper, matches.equals(List.of(Identifier.withDefaultNamespace(archetype))), item + " should give exactly the " + archetype + " archetype, got " + matches);
    }

    /** The design graph: real references, cross-era edges, a test for every edge, one component, less era clustering. */
    private static void featureGraph(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FeatureGraph graph = FeatureGraph.load();
        for (FeatureGraph.Node node : graph.nodes()) {
            for (String ref : node.refs()) check(helper, exists(ref), node.id() + " refers to " + ref + ", which is not in this Minecraft version");
        }
        Registry<?> tests = level.registryAccess().lookupOrThrow(Registries.TEST_INSTANCE);
        for (FeatureGraph.Edge edge : graph.fiedlerEdges()) {
            check(helper, graph.crossesEras(edge), edge.a() + "–" + edge.b() + " stays inside one era; Fiedler only adds cross-era connections");
            check(helper, edge.test() != null && tests.containsKey(Identifier.parse(edge.test())),
                    edge.a() + "–" + edge.b() + " has no registered GameTest (no 'coming never')");
        }
        SpectralGraph vanilla = graph.spectral(false), with = graph.spectral(true);
        double[][] eras = graph.eraMembership();
        check(helper, with.components() == 1 && with.zeroEigenvalueMultiplicity() == 1, "the slice must be one component with Fiedler; β₀ = " + with.components());
        check(helper, with.fiedlerValue() > 0, "λ₂ must be positive");
        check(helper, with.mixing(eras) < vanilla.mixing(eras), "Fiedler's edges must reduce era clustering");
        Fiedler.LOGGER.info("Feature graph: vanilla {} r={} | with Fiedler {} r={}", vanilla.summarize(), vanilla.mixing(eras), with.summarize(), with.mixing(eras));
        helper.succeed();
    }

    /** /fiedler graph returns β₀ with Fiedler's edges (1); /fiedler connections returns how many connections there are. */
    private static void graphCommand(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var source = server.createCommandSourceStack().withSuppressedOutput();
        try {
            int beta0 = server.getCommands().getDispatcher().execute("fiedler graph", source);
            int connections = server.getCommands().getDispatcher().execute("fiedler connections", source);
            check(helper, beta0 == 1, "/fiedler graph should report one component, got " + beta0);
            check(helper, connections == FeatureGraph.load().fiedlerEdges().size(), "/fiedler connections should list every connection, got " + connections);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            helper.fail("command failed: " + e.getMessage());
        }
        helper.succeed();
    }

    private static boolean exists(String ref) {
        int colon = ref.indexOf(':');
        String kind = ref.substring(0, colon);
        Identifier id = Identifier.parse(ref.substring(colon + 1));
        return switch (kind) {
            case "block" -> BuiltInRegistries.BLOCK.containsKey(id);
            case "item" -> BuiltInRegistries.ITEM.containsKey(id);
            case "entity" -> BuiltInRegistries.ENTITY_TYPE.containsKey(id);
            default -> false;
        };
    }
}
