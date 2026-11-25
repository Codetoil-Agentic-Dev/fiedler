package fiedler.graph;

import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /fiedler graph} prints the invariants of the curated feature graph before and after this mod's connections.
 * {@code /fiedler connections} lists the connections, with the era each end shipped in.
 */
public final class FiedlerCommand {
    private FiedlerCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fiedler")
                .then(Commands.literal("graph").executes(FiedlerCommand::graph))
                .then(Commands.literal("connections").executes(FiedlerCommand::connections)));
    }

    private static int graph(CommandContext<CommandSourceStack> ctx) {
        FeatureGraph g = FeatureGraph.load();
        FeatureGraph.Report before = g.report(false), after = g.report(true);
        say(ctx, Component.literal("Fiedler feature graph: " + g.nodes().size() + " features (a curated slice)").withStyle(ChatFormatting.GOLD));
        say(ctx, Component.literal("  vanilla 26.3:  " + line(before)));
        say(ctx, Component.literal("  with Fiedler:  " + line(after)).withStyle(ChatFormatting.GREEN));
        say(ctx, Component.literal("  " + g.fiedlerEdges().size() + " connections, each between two different eras and each backed by a GameTest.")
                .withStyle(ChatFormatting.GRAY));
        say(ctx, Component.literal("  β₀ = components, β₁ = independent loops, λ₂ = Fiedler value (>0 ⇔ connected), r = era mixing (1 = every edge stays in its era).")
                .withStyle(ChatFormatting.DARK_GRAY));
        return after.summary().beta0();
    }

    private static int connections(CommandContext<CommandSourceStack> ctx) {
        FeatureGraph g = FeatureGraph.load();
        for (FeatureGraph.Edge e : g.fiedlerEdges()) {
            FeatureGraph.Node a = g.node(e.a()), b = g.node(e.b());
            say(ctx, Component.literal(String.format(Locale.ROOT, "• %s (%s) ↔ %s (%s): %s", a.label(), a.era(), b.label(), b.era(), e.how())));
        }
        return g.fiedlerEdges().size();
    }

    private static String line(FeatureGraph.Report r) {
        return r.summary() + String.format(Locale.ROOT, "  r=%+.3f", r.eraMixing());
    }

    private static void say(CommandContext<CommandSourceStack> ctx, Component message) {
        ctx.getSource().sendSuccess(() -> message, false);
    }
}
