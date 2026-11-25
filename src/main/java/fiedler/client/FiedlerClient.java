package fiedler.client;

import fiedler.Fiedler;
import fiedler.gametest.ClientTests;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entry point. Fiedler has no client features, only the client half of its test suite. */
@Mod(value = Fiedler.MODID, dist = Dist.CLIENT)
public final class FiedlerClient {
    public FiedlerClient(IEventBus modBus) {
        ClientTests.install(FiedlerClientGameTests::run);
        modBus.addListener(FiedlerClientGameTests::registerTests);
        NeoForge.EVENT_BUS.addListener(ClientChatLog::onSystemMessage);
    }
}
