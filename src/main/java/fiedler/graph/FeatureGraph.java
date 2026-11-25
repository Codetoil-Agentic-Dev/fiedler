package fiedler.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The design document as data: a curated slice of the vanilla feature graph around the features this mod touches.
 * Nodes are features, tagged with the era they shipped in. Edges are interactions, either already in vanilla or
 * added by Fiedler. The file is {@code /fiedler/feature_graph.json}.
 *
 * <p>The slice is hand-picked, so its absolute numbers are illustrative. The GameTests enforce the claims that
 * matter instead: every node points at things that exist in this Minecraft version, every Fiedler edge joins two
 * different eras and names the GameTest that proves it, the whole slice is one connected component (λ₂ &gt; 0), and the
 * mod's edges reduce how much the graph clusters by era.
 */
public record FeatureGraph(List<String> eras, List<Node> nodes, List<Edge> edges) {
    public static final String RESOURCE = "/fiedler/feature_graph.json";

    public record Node(String id, String label, String era, List<String> refs) {}

    public record Edge(String a, String b, String by, String how, @Nullable String test) {
        public boolean byFiedler() {
            return "fiedler".equals(this.by);
        }
    }

    /** Invariants of one version of the graph. */
    public record Report(SpectralGraph.Summary summary, double eraMixing) {}

    private static @Nullable FeatureGraph cached;

    public static synchronized FeatureGraph load() {
        if (cached == null) {
            try (InputStream in = FeatureGraph.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException("missing " + RESOURCE);
                cached = parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return cached;
    }

    static FeatureGraph parse(JsonObject root) {
        List<String> eras = strings(root.getAsJsonArray("eras"));
        List<Node> nodes = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("nodes")) {
            JsonObject o = e.getAsJsonObject();
            nodes.add(new Node(o.get("id").getAsString(), o.get("label").getAsString(), o.get("era").getAsString(), strings(o.getAsJsonArray("refs"))));
        }
        List<Edge> edges = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("edges")) {
            JsonObject o = e.getAsJsonObject();
            edges.add(new Edge(o.get("a").getAsString(), o.get("b").getAsString(), o.get("by").getAsString(), o.get("how").getAsString(),
                    o.has("test") ? o.get("test").getAsString() : null));
        }
        FeatureGraph graph = new FeatureGraph(List.copyOf(eras), List.copyOf(nodes), List.copyOf(edges));
        graph.validateShape();
        return graph;
    }

    private static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<>(array.size());
        for (JsonElement e : array) out.add(e.getAsString());
        return out;
    }

    /** Structural sanity: unique ids, known eras, edges between known nodes. */
    private void validateShape() {
        Map<String, Integer> seen = new HashMap<>();
        for (Node n : this.nodes) {
            if (seen.put(n.id(), seen.size()) != null) throw new IllegalStateException("duplicate node " + n.id());
            if (!this.eras.contains(n.era())) throw new IllegalStateException("unknown era " + n.era() + " on " + n.id());
        }
        for (Edge e : this.edges) {
            if (!seen.containsKey(e.a()) || !seen.containsKey(e.b())) throw new IllegalStateException("edge to unknown node: " + e);
        }
    }

    public int indexOf(String id) {
        for (int i = 0; i < this.nodes.size(); i++) if (this.nodes.get(i).id().equals(id)) return i;
        throw new IllegalArgumentException(id);
    }

    public Node node(String id) {
        return this.nodes.get(this.indexOf(id));
    }

    public List<Edge> fiedlerEdges() {
        return this.edges.stream().filter(Edge::byFiedler).toList();
    }

    public SpectralGraph spectral(boolean withFiedler) {
        SpectralGraph g = new SpectralGraph(this.nodes.size());
        for (Edge e : this.edges) {
            if (withFiedler || !e.byFiedler()) g.addEdge(this.indexOf(e.a()), this.indexOf(e.b()));
        }
        return g;
    }

    /** One-hot era membership, the label set for {@link SpectralGraph#mixing}. */
    public double[][] eraMembership() {
        double[][] m = new double[this.nodes.size()][this.eras.size()];
        for (int i = 0; i < this.nodes.size(); i++) m[i][this.eras.indexOf(this.nodes.get(i).era())] = 1.0;
        return m;
    }

    public Report report(boolean withFiedler) {
        SpectralGraph g = this.spectral(withFiedler);
        return new Report(g.summarize(), g.mixing(this.eraMembership()));
    }

    public boolean crossesEras(Edge e) {
        return !this.node(e.a()).era().equals(this.node(e.b()).era());
    }
}
