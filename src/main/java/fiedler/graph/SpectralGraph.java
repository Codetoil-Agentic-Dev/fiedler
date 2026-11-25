package fiedler.graph;

import java.util.Arrays;

/**
 * A small weighted, undirected graph together with the invariants used to judge "update design":
 *
 * <ul>
 *   <li>β₀: the number of connected components (= dim ker L);</li>
 *   <li>β₁ = m − n + β₀: the cycle rank, i.e. the number of independent "synergy loops";</li>
 *   <li>the spectrum of the combinatorial Laplacian L = D − W;</li>
 *   <li>λ₂, the Fiedler value (algebraic connectivity): positive iff the graph is connected;</li>
 *   <li>Newman's mixing coefficient r for (possibly fractional) labels.
 *       r = 1 means every edge stays inside its own update; r ≈ 0 means updates mix as if at random.</li>
 * </ul>
 *
 * <p>This file is deliberately plain Java with no Minecraft imports. The same code runs in the JUnit tests,
 * in the GameTests and in the {@code /fiedler graph} command.
 */
public final class SpectralGraph {
    /** Eigenvalues below this are counted as zero. The spectra used here are of order 1–100. */
    public static final double ZERO_TOLERANCE = 1e-9;

    private final int n;
    private final double[][] w;

    public SpectralGraph(int vertexCount) {
        if (vertexCount < 0) throw new IllegalArgumentException("negative vertex count");
        this.n = vertexCount;
        this.w = new double[vertexCount][vertexCount];
    }

    /** Builds an unweighted graph from an edge list of vertex-index pairs. */
    public static SpectralGraph of(int vertexCount, int[][] edges) {
        SpectralGraph g = new SpectralGraph(vertexCount);
        for (int[] e : edges) g.addEdge(e[0], e[1]);
        return g;
    }

    public SpectralGraph addEdge(int a, int b) {
        return addEdge(a, b, 1.0);
    }

    /**
     * Sets the weight of edge ab. A weight of 0 removes the edge. This is how the meme's
     * "Coming never" edge is modelled.
     */
    public SpectralGraph addEdge(int a, int b, double weight) {
        if (a < 0 || b < 0 || a >= n || b >= n) throw new IndexOutOfBoundsException("edge " + a + "-" + b + " outside 0.." + (n - 1));
        if (a == b) throw new IllegalArgumentException("self-loop at " + a);
        if (weight < 0 || Double.isNaN(weight)) throw new IllegalArgumentException("weights must be non-negative");
        w[a][b] = weight;
        w[b][a] = weight;
        return this;
    }

    public int vertexCount() {
        return n;
    }

    /** Number of edges with non-zero weight. */
    public int edgeCount() {
        int m = 0;
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) if (w[i][j] != 0) m++;
        return m;
    }

    /** Number of neighbours joined by a non-zero-weight edge. */
    public int degree(int v) {
        int d = 0;
        for (int j = 0; j < n; j++) if (w[v][j] != 0) d++;
        return d;
    }

    public boolean adjacent(int a, int b) {
        return w[a][b] != 0;
    }

    /** β₀, computed combinatorially with union–find (exact, independent of the eigen-solver). */
    public int components() {
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        int count = n;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (w[i][j] == 0) continue;
                int ri = find(parent, i), rj = find(parent, j);
                if (ri != rj) {
                    parent[ri] = rj;
                    count--;
                }
            }
        }
        return count;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    /** β₁ = m − n + β₀ (the first Betti number of the graph as a 1-complex). */
    public int cycleRank() {
        return edgeCount() - n + components();
    }

    /** The combinatorial Laplacian L = D − W. */
    public double[][] laplacian() {
        double[][] l = new double[n][n];
        for (int i = 0; i < n; i++) {
            double d = 0;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                l[i][j] = -w[i][j];
                d += w[i][j];
            }
            l[i][i] = d;
        }
        return l;
    }

    /** Laplacian eigenvalues in ascending order. */
    public double[] laplacianSpectrum() {
        return symmetricEigenvalues(laplacian());
    }

    /** λ₂, the Fiedler value. Returns 0 for graphs with fewer than two vertices. */
    public double fiedlerValue() {
        if (n < 2) return 0;
        double l2 = laplacianSpectrum()[1];
        return Math.abs(l2) < ZERO_TOLERANCE ? 0 : l2;
    }

    /** Multiplicity of the eigenvalue 0. Equals β₀ (Fiedler); tests check the two agree. */
    public int zeroEigenvalueMultiplicity() {
        int k = 0;
        for (double ev : laplacianSpectrum()) if (Math.abs(ev) < ZERO_TOLERANCE) k++;
        return k;
    }

    /**
     * Newman's mixing coefficient for categorical labels, extended to fractional membership.
     * Row v of {@code membership} is vertex v's distribution over the labels (each row sums to 1).
     *
     * <p>e_ij = (1/2W) Σ_{uv} w_uv (m_ui m_vj + m_vi m_uj),  a_i = Σ_j e_ij,
     * r = (Tr e − Σ a_i²) / (1 − Σ a_i²).
     *
     * @return r ∈ [−1, 1], or NaN if every edge sits inside a single label (0/0)
     */
    public double mixing(double[][] membership) {
        if (membership.length != n) throw new IllegalArgumentException("need one membership row per vertex");
        int k = membership[0].length;
        double[][] e = new double[k][k];
        double total = 0;
        for (int u = 0; u < n; u++) {
            for (int v = u + 1; v < n; v++) {
                double wt = w[u][v];
                if (wt == 0) continue;
                for (int i = 0; i < k; i++) {
                    for (int j = 0; j < k; j++) {
                        e[i][j] += wt * (membership[u][i] * membership[v][j] + membership[v][i] * membership[u][j]);
                    }
                }
                total += 2 * wt;
            }
        }
        if (total == 0) return Double.NaN;
        double trace = 0, sumA2 = 0;
        for (int i = 0; i < k; i++) {
            double a = 0;
            for (int j = 0; j < k; j++) {
                e[i][j] /= total;
                a += e[i][j];
            }
            trace += e[i][i];
            sumA2 += a * a;
        }
        double denominator = 1 - sumA2;
        return Math.abs(denominator) < 1e-15 ? Double.NaN : (trace - sumA2) / denominator;
    }

    /**
     * Eigenvalues of a real symmetric matrix by the cyclic Jacobi method, in ascending order.
     * Each rotation is orthogonal, so the spectrum is preserved exactly; the off-diagonal Frobenius mass
     * decreases monotonically and converges quadratically. That is plenty for n ≲ 100.
     */
    public static double[] symmetricEigenvalues(double[][] matrix) {
        int size = matrix.length;
        double[][] a = new double[size][];
        double frobenius2 = 0;
        for (int i = 0; i < size; i++) {
            if (matrix[i].length != size) throw new IllegalArgumentException("matrix must be square");
            a[i] = matrix[i].clone();
            for (int j = 0; j < size; j++) {
                if (Math.abs(matrix[i][j] - matrix[j][i]) > 1e-12 * (1 + Math.abs(matrix[i][j]))) {
                    throw new IllegalArgumentException("matrix must be symmetric");
                }
                frobenius2 += matrix[i][j] * matrix[i][j];
            }
        }
        double tolerance = 1e-30 * Math.max(1.0, frobenius2);
        for (int sweep = 0; sweep < 100; sweep++) {
            double off = 0;
            for (int p = 0; p < size; p++) for (int q = p + 1; q < size; q++) off += a[p][q] * a[p][q];
            if (off <= tolerance) break;
            for (int p = 0; p < size; p++) {
                for (int q = p + 1; q < size; q++) {
                    double apq = a[p][q];
                    if (apq == 0) continue;
                    double theta = (a[q][q] - a[p][p]) / (2 * apq);
                    double t = (theta >= 0 ? 1 : -1) / (Math.abs(theta) + Math.sqrt(theta * theta + 1));
                    double c = 1 / Math.sqrt(t * t + 1), s = t * c;
                    for (int k = 0; k < size; k++) {            // A ← A·J
                        double akp = a[k][p], akq = a[k][q];
                        a[k][p] = c * akp - s * akq;
                        a[k][q] = s * akp + c * akq;
                    }
                    for (int k = 0; k < size; k++) {            // A ← Jᵀ·A
                        double apk = a[p][k], aqk = a[q][k];
                        a[p][k] = c * apk - s * aqk;
                        a[q][k] = s * apk + c * aqk;
                    }
                }
            }
        }
        double[] ev = new double[size];
        for (int i = 0; i < size; i++) ev[i] = a[i][i];
        Arrays.sort(ev);
        return ev;
    }

    /** A compact human-readable summary, used by the {@code /fiedler graph} command and in logs. */
    public Summary summarize() {
        return new Summary(n, edgeCount(), components(), cycleRank(), fiedlerValue());
    }

    public record Summary(int vertices, int edges, int beta0, int beta1, double lambda2) {
        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "n=%d m=%d β₀=%d β₁=%d λ₂=%.4f", vertices, edges, beta0, beta1, lambda2);
        }
    }
}
