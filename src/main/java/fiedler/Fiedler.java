package fiedler;

import com.mojang.logging.LogUtils;
import fiedler.connection.CushionCats;
import fiedler.connection.HornAlarm;
import fiedler.connection.MossRestoration;
import fiedler.connection.WindAndFire;
import fiedler.gametest.FiedlerGameTests;
import fiedler.graph.FiedlerCommand;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Fiedler adds no blocks, items or entities. Every feature is a connection: an interaction between vanilla features
 * that shipped years apart. The connections are listed, with their eras, in {@code fiedler/feature_graph.json}.
 * The GameTest {@code fiedler:feature_graph} refuses any advertised connection that has no test of its own.
 */
@Mod(Fiedler.MODID)
public final class Fiedler {
    public static final String MODID = "fiedler";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Fiedler(IEventBus modBus, ModContainer container) {
        FiedlerGameTests.register(modBus);

        // Connections that need code. The data-only ones (recipes, tags) live under src/main/resources/data.
        NeoForge.EVENT_BUS.addListener(MossRestoration::onBrushTick);   // brush (1.20) → mossy stone (2009/2011)
        NeoForge.EVENT_BUS.addListener(HornAlarm::onGameEvent);         // goat horn (1.19) → village alarm (1.14)
        NeoForge.EVENT_BUS.addListener(WindAndFire::onDetonate);        // wind charge (1.21) → campfire (1.14)
        NeoForge.EVENT_BUS.addListener(CushionCats::onJoinLevel);       // cat (1.14) → cushion (26.3)
        NeoForge.EVENT_BUS.addListener(CushionCats::onInteract);
        // chiseled bookshelf (1.20) → enchanting table (1.0): see fiedler.mixin.ChiseledBookShelfBlockMixin

        NeoForge.EVENT_BUS.addListener(FiedlerCommand::register);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
