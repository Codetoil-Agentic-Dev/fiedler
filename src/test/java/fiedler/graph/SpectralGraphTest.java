package fiedler.graph;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The fixtures are the three panels of the r/MinecraftMemes post "This is Legacy Minecraft / This is good update design /
 * This is whatever Mojang is doing" (u/Isjoni). They were transcribed from the image pixels: every edge was traced
 * from both of its ends, and no ink was left unaccounted for. The reference spectra were computed independently
 * with numpy/sympy.
 */
class SpectralGraphTest {
    // Panel 1, "Legacy": vertex order A,B,C,D,E,F.
    static final int[][] LEGACY_EDGES = {{0, 1}, {0, 3}, {1, 2}, {1, 3}, {1, 4}, {1, 5}, {2, 4}, {3, 4}, {3, 5}, {4, 5}};

    // Panel 2, "good update design": vertex order N1..N17. The Legacy graph sits inside it as {N8,N6,N7,N11,N12,N14}.
    static final int[][] GOOD_EDGES = {{0, 5}, {0, 7}, {0, 9}, {0, 10}, {1, 2}, {1, 5}, {1, 7}, {1, 9}, {2, 3}, {2, 5}, {2, 7},
            {3, 5}, {3, 6}, {3, 8}, {4, 6}, {4, 8}, {4, 11}, {5, 6}, {5, 7}, {5, 9}, {5, 10}, {5, 11}, {5, 13}, {6, 10}, {6, 11},
            {7, 10}, {7, 11}, {8, 11}, {8, 14}, {9, 10}, {9, 12}, {10, 11}, {10, 12}, {10, 13}, {10, 14}, {10, 15}, {11, 13},
            {11, 14}, {11, 16}, {12, 15}, {13, 14}, {13, 15}, {13, 16}, {15, 16}};
    static final double[] GOOD_SPECTRUM = {0.0, 1.51012, 1.574933, 2.540597, 3.120708, 3.23242, 3.760352, 4.420913, 4.699919,
            4.998207, 5.772007, 6.34353, 6.573682, 7.028522, 9.913081, 11.134455, 11.376555};
    // Colour membership (grey, red, green, blue). Pie-chart nodes are features that several updates touched.
    static final double[][] GOOD_MEMBERSHIP = {{0, 0, 1, 0}, {0, 1, 0, 0}, {0, 0, 0, 1}, {0, .5, 0, .5}, {0, 0, 1, 0},
            {.5, .25, 0, .25}, {1, 0, 0, 0}, {.5, 0, .5, 0}, {0, 1, 0, 0}, {0, 0, .5, .5}, {1, 0, 0, 0}, {.5, .25, .25, 0},
            {0, 0, 1, 0}, {.5, 0, 0, .5}, {0, .25, .5, .25}, {0, 1, 0, 0}, {0, 0, 0, 1}};

    // Panel 3, "whatever Mojang is doing", as drawn. The zig-zag-severed and X'd edges are absent.
    // "Fletching table (Coming never)" is its own vertex FT, attached by an edge to the tiny grey node.
    // Vertex order: Bl1..Bl4, G1..G6 (Legacy copy; G4 is the obsoleted D), R1..R3, Gr1..Gr4, FT.
    static final int[][] MOJANG_EDGES = {{0, 1}, {0, 3}, {1, 2}, {1, 3}, {4, 5}, {5, 6}, {5, 8}, {5, 9}, {6, 8}, {6, 17}, {8, 9},
            {10, 11}, {11, 12}, {13, 15}, {14, 15}, {15, 16}};
    static final int FT = 17, G3 = 6;

    @Test
    void legacyPanelHasTheExactSpectrum() {
        SpectralGraph g = SpectralGraph.of(6, LEGACY_EDGES);
        double r13 = Math.sqrt(13), r5 = Math.sqrt(5);
        // characteristic polynomial x(x−6)(x²−7x+9)(x²−7x+11)
        double[] exact = {0, (7 - r13) / 2, (7 - r5) / 2, (7 + r5) / 2, (7 + r13) / 2, 6};
        assertArrayEquals(exact, g.laplacianSpectrum(), 1e-10);
        assertEquals(1, g.components());
        assertEquals(5, g.cycleRank());
        assertEquals((7 - r13) / 2, g.fiedlerValue(), 1e-10);
    }

    @Test
    void goodDesignPanelIsConnectedAndLoopy() {
        SpectralGraph g = SpectralGraph.of(17, GOOD_EDGES);
        assertEquals(44, g.edgeCount());
        assertEquals(1, g.components());
        assertEquals(28, g.cycleRank());
        assertArrayEquals(GOOD_SPECTRUM, g.laplacianSpectrum(), 1e-5);
        assertEquals(-0.032642248834205405, g.mixing(GOOD_MEMBERSHIP), 1e-12, "updates mix about as often as chance");
    }

    @Test
    void mojangPanelFallsApart() {
        SpectralGraph g = SpectralGraph.of(18, MOJANG_EDGES);
        assertEquals(16, g.edgeCount());
        assertEquals(5, g.components());
        assertEquals(5, g.zeroEigenvalueMultiplicity(), "Fiedler: dim ker L = β₀");
        assertEquals(3, g.cycleRank());
        assertEquals(0.0, g.fiedlerValue());
        double[][] monochrome = new double[18][4];
        int[] colour = {3, 3, 3, 3, 0, 0, 0, 0, 0, 0, 1, 1, 1, 2, 2, 2, 2, 0};
        for (int v = 0; v < 18; v++) monochrome[v][colour[v]] = 1;
        assertEquals(1.0, g.mixing(monochrome), 1e-12, "every edge stays inside its own update");
    }

    @Test
    void comingNeverMeansZeroWeight() {
        SpectralGraph g = SpectralGraph.of(18, MOJANG_EDGES).addEdge(G3, FT, 0.0);
        assertEquals(6, g.components(), "the fletching table becomes its own sector");
        assertEquals(6, g.zeroEigenvalueMultiplicity());
        // ...and this mod's job, in one line: give that edge weight again.
        g.addEdge(G3, FT, 1.0);
        assertEquals(5, g.components());
    }

    @Test
    void solverReproducesClosedFormSpectra() {
        for (int n = 2; n <= 24; n++) {
            int[][] path = new int[n - 1][], cycle = new int[n][];
            for (int i = 0; i < n - 1; i++) path[i] = new int[]{i, i + 1};
            for (int i = 0; i < n; i++) cycle[i] = new int[]{i, (i + 1) % n};
            double[] pathExact = new double[n], completeExact = new double[n];
            for (int k = 0; k < n; k++) {
                pathExact[k] = 2 - 2 * Math.cos(Math.PI * k / n);
                completeExact[k] = k == 0 ? 0 : n;
            }
            java.util.Arrays.sort(pathExact);
            assertArrayEquals(pathExact, SpectralGraph.of(n, path).laplacianSpectrum(), 1e-9, "P_" + n);
            if (n >= 3) {
                double[] cycleExact = new double[n];
                for (int k = 0; k < n; k++) cycleExact[k] = 2 - 2 * Math.cos(2 * Math.PI * k / n);
                java.util.Arrays.sort(cycleExact);
                assertArrayEquals(cycleExact, SpectralGraph.of(n, cycle).laplacianSpectrum(), 1e-9, "C_" + n);
            }
            SpectralGraph complete = new SpectralGraph(n);
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) complete.addEdge(i, j);
            assertArrayEquals(completeExact, complete.laplacianSpectrum(), 1e-9, "K_" + n);
        }
    }

    @Test
    void solverPreservesSimilarityInvariants() {
        Random random = new Random(26_3_0_118L);
        for (int trial = 0; trial < 50; trial++) {
            int n = 1 + random.nextInt(40);
            double[][] a = new double[n][n];
            for (int i = 0; i < n; i++) for (int j = i; j < n; j++) a[i][j] = a[j][i] = random.nextGaussian();
            double[] ev = SpectralGraph.symmetricEigenvalues(a);
            double trace = 0, frob = 0, sum = 0, sumSq = 0;
            for (int i = 0; i < n; i++) {
                trace += a[i][i];
                for (int j = 0; j < n; j++) frob += a[i][j] * a[i][j];
                sum += ev[i];
                sumSq += ev[i] * ev[i];
            }
            assertEquals(trace, sum, 1e-9 * (1 + Math.abs(trace)), "Tr A = Σλ");
            assertEquals(frob, sumSq, 1e-9 * (1 + frob), "‖A‖_F² = Σλ²");
            for (int i = 1; i < n; i++) assertTrue(ev[i - 1] <= ev[i], "sorted");
        }
    }
}
