package edu.rit.cs.graph_matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * Goel-Kapralov-Khanna Algorithm for Perfect Matching in Regular Bipartite Graphs
 * Reference: SIAM J. COMPUT. Vol. 42, No. 3, pp. 1392-1404 (2013)
 *
 * Implements Algorithm 2 (High Probability) with:
 * 1. Loop-Erased Random Walks (Optimized to O(|walk|) using sparse cleanup)
 * 2. Pre-matching Injection Support (for Adversarial Testing)
 * 3. CSR Graph Representation (for Cache Efficiency)
 */
public class GKKAlgorithm {
    class BipartiteGraphRegular {
        int n;
        int d;

        // CSR Data for Left side (P) -> Right side (Q)
        int[] adj;

        // Matchings
        int[] matchP; // P[u] -> v
        int[] matchQ; // Q[v] -> u

        // Optimization: Reuse this vector to avoid O(n) initialization cost per step
        int[] posInPath;

        Random random;

        public BipartiteGraphRegular(int n, int d, ArrayList<int[]> edges) {
            this.n = n;
            this.d = d;
            this.random = new Random(42);

            this.adj = new int[n * d];
            int[] current_idx = new int[n];
            Arrays.fill(current_idx, 0);

            for (int[] e : edges) {
                int u = e[0];
                int v = e[1];
                // Boundary & deg check
                if (u < n && current_idx[u] < d) {
                    adj[u * d + current_idx[u]] = v;
                    current_idx[u]++;
                }
            }

            matchP = new int[n];
            Arrays.fill(matchP, -1);
            matchQ = new int[n];
            Arrays.fill(matchQ, -1);

            // Initialize once. O(n) total cost.
            posInPath = new int[n];
            Arrays.fill(posInPath, -1);
        }

        // Allows forcing the graph into a specific "bad" state before solving.
        void injectMatching(int[] presetMatchP) {
            Arrays.fill(matchP, -1);
            Arrays.fill(matchQ, -1);

            for (int u = 0; u < n; ++u) {
                int v = presetMatchP[u];
                if (v != -1) {
                    matchP[u] = v;
                    matchQ[v] = u;
                }
            }
        }

        // Allows forcing the graph into a specific "bad" state before solving.
        int sampleOutEdge(int u) {
            return adj[u * d + random.nextInt(d)];
        }

        // Performs loop erasure in O(|walk|) time instead of O(n)
        ArrayList<Integer> removeLoops(ArrayList<Integer> walkP) {
            ArrayList<Integer> path = new ArrayList<>();

            // Note: posInPath is already all -1 from previous cleanups
            for (int u : walkP) {
                if (posInPath[u] != -1) {
                    // Cycle detected: Erase loop
                    int truncatePos = posInPath[u];

                    // Sparse Cleanup: Reset only the nodes we are about to remove
                    for (int k = truncatePos + 1; k < path.size(); ++k) {
                        posInPath[path.get(k)] = -1;
                    }

                    path.subList(truncatePos + 1, path.size()).clear();
                }
                else {
                    posInPath[u] = path.size();
                    path.add(u);
                }
            }

            // Final Cleanup: Reset the remaining nodes in the valid path
            // so posInPath is clean for the next augmentation
            for (int u : path) {
                posInPath[u] = -1;
            }
            
            return path;
        }

        // Algorithm 2: Perfect Matching with Truncated Random Walks
        int solve() {
            ArrayList<Integer> freeP = new ArrayList<>();
            int matched_count = 0;

            // CRITICAL FIX: Respect existing matching state (for Adversary injection)
            // Instead of assuming empty, we scan matchP.
            for (int i = 0; i < n; ++i) {
                if (matchP[i] == -1) {
                    freeP.add(i);
                }
                else {
                    matched_count++;
                }
            }

            while (matched_count < n) {
                // Calculate truncation limit b_j
                double free_count = (double)(n - matched_count);
                int b_j = (int)(2.0 * (4.0 + (2.0 * n) / free_count));

                boolean success = false;

                while (!success) {
                    if (freeP.isEmpty()) {
                        break;
                    }

                    // Swap-to-End Removal for O(1)
                    int random_idx = random.nextInt(freeP.size());
                    int u_start = freeP.get(random_idx);

                    ArrayList<Integer> walkP = new ArrayList<>();
                    walkP.add(u_start);

                    int curr_u = u_start;
                    int steps = 0;
                    int end_v = -1;

                    // Perform the Random Walk
                    while (steps < b_j) {
                        int v = sampleOutEdge(curr_u);

                        if (matchQ[v] != -1) {
                            int next_u = matchQ[v]; // Step 2: u_{j+1} := M(v)
                            curr_u = next_u;
                            walkP.add(curr_u);
                            steps++;
                        }
                        else {
                            end_v = v;
                            success = true;
                            break;
                        }
                    }

                    if (success) {
                        // Step 3: Loop Erasure
                        ArrayList<Integer> pathP = removeLoops(walkP);

                        // Backward Augmentation Logic
                        int v_next = end_v;

                        for (int i = pathP.size() - 1; i >=0; --i) {
                            int u = pathP.get(i);

                            // Retrieve the Q node 'u' was matched to *before* this augmentation
                            int v_old_match = matchP[u];

                            // Augment the edge
                            matchP[u] = v_next;
                            matchQ[v_next] = u;

                            // Pass the old match down the chain
                            v_next = v_old_match;
                        }

                        // Remove u_start from free list
                        freeP.set(random_idx, freeP.get(freeP.size() - 1));
                        freeP.remove(freeP.size() - 1);

                        matched_count++;
                    }
                }
            }
            return matched_count;
        }
    }

    class GraphGenerator {
        // Generates a random d-regular bipartite graph by unioning d random perfect matchings.
        public static ArrayList<int[]> generateRandomRegular(int n, int d) {
            ArrayList<int[]> edges = new ArrayList<>();
            
            Random random = new Random(12345);
            int[] right_nodes = new int[n];
            for (int i = 0; i < right_nodes.length; i++) {
                right_nodes[i] = i;
            }

            for (int k = 0; k < d; ++k) {
                shuffle(right_nodes, random);
                for (int u = 0; u < n; ++u) {
                    edges.add(new int[]{u, right_nodes[u]});
                }
            }

            return edges;
        }

        // Generates the "Hall's Trap" Adversary
        // 75% of P (locked) <-> 75% of Q (locked)
        // 25% of P (free) -> Forced into Q (locked) by d-1 layers
        static HallTrapResult generateHallTrap(int n, int d) {
            ArrayList<int[]> edges = new ArrayList<>();
            int[] trapMatching = new int[n];
            Arrays.fill(trapMatching, -1);
            int k = (n * 3) / 4; // 75% cutoff
            Random random = new Random(999);

            // 1. Define the Trap Matching (P_locked <-> Q_locked)
            int[] p_locked = new int[k];
            int[] q_locked = new int[k];
            for (int i = 0; i < k; ++i) {
                p_locked[i] = i;
                q_locked[i] = i;
            }
            shuffle(q_locked, random);

            for (int i = 0; i < k; ++i) {
                trapMatching[p_locked[i]] = q_locked[i];
            }

            // 2. Build Layers
            int[] q_free = new int[n - k];
            for (int i = 0; i < n - k; ++i) {
                q_free[i] = i + k;
            }
            shuffle(q_free, random);

            // Layer 1: The Hidden Perfect Matching (1 edge per node)
            for (int i = 0; i < n - k; ++i) {
                edges.add(new int[]{k + i, q_free[i]});
            }
            for (int i = 0; i < k; ++i) {
                edges.add(new int[]{p_locked[i], q_locked[i]});
            }

            // Layers 2..d: The Interference (Force P_free -> Q_locked)
            for (int m = 1; m < d; ++m) {
                int[] targets_locked = Arrays.copyOf(q_locked, q_locked.length);
                int[] targets_free = Arrays.copyOf(q_free, q_free.length);
                shuffle(targets_locked, random);
                shuffle(targets_free, random);

                // Connect P_free nodes to Q_locked nodes
                for (int u = k; u < n; ++u) {
                    if (targets_locked.length != 0) {
                        edges.add(new int[]{u, targets_locked[targets_locked.length - 1]});
                        targets_locked = Arrays.copyOf(targets_locked, targets_locked.length - 1);
                    }
                    else {
                        edges.add(new int[]{u, targets_free[targets_free.length - 1]});
                        targets_free = Arrays.copyOf(targets_free, targets_free.length - 1);
                    }
                }

                // Connect P_locked to whatever is left
                for (int u = 0; u < k; ++u) {
                    if (targets_locked.length != 0) {
                        edges.add(new int[]{u, targets_locked[targets_locked.length - 1]});
                        targets_locked = Arrays.copyOf(targets_locked, targets_locked.length - 1);
                    }
                    else {
                        edges.add(new int[]{u, targets_free[targets_free.length - 1]});
                        targets_free = Arrays.copyOf(targets_free, targets_free.length - 1);
                    }
                }
            }
            return new HallTrapResult(edges, trapMatching);
        }
    }

    static class HallTrapResult {
        final ArrayList<int[]> edges;
        final int[] trapMatching;

        HallTrapResult(ArrayList<int[]> edges, int[] trapMatching) {
            this.edges = edges;
            this.trapMatching = trapMatching;
        }
    }

    static void shuffle(int[] a, Random rnd) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
        }
    }

    void runTests() {
        // --- PART 1: Standard Scaling Benchmark ---
        // --- PART 1: Standard Scaling Benchmark ---
        System.out.println("==========================================================");
        System.out.println(" BENCHMARK 1: Random Regular Graphs (Scaling)");
        System.out.println(" Complexity Goal: O(n log n)");
        System.out.println("==========================================================");

        System.out.printf("%10s%5s%15s%15s%n", "N", "d", "Time(ms)", "Ratio");
        System.out.println("----------------------------------------------------------");
        
        int d = 10;
        // Scale N from 4k to 65k
        for (int n = 4096; n <= 65536; n *= 2) {
            ArrayList<int[]> edges = GraphGenerator.generateRandomRegular(n, d);

            long start = System.nanoTime();
            BipartiteGraphRegular graph = new BipartiteGraphRegular(n, d, edges);
            int matched = graph.solve();
            long end = System.nanoTime();

            double time_ms = (end - start) / 1_000_000.0;
            double n_log_n = n * (Math.log(n) / Math.log(2));;
            double ratio = time_ms / n_log_n;

            System.out.printf("%10d%5d%15.2f%15.4f%n", n, d, time_ms, ratio);

            if (matched != n) {
                System.err.println("Error: Matching failed!");
            }
        }

        // --- PART 2: Adversarial Stress Test ---
        System.out.println("\n==========================================================");
        System.out.println(" BENCHMARK 2: Adversary Test (Hall's Trap)");
        System.out.println(" Context: 75% of the graph is pre-matched to a 'dead end'.");
        System.out.println("==========================================================");
    
        int n_trap = 16384;

        // 1. Generate Trap
        HallTrapResult result = GraphGenerator.generateHallTrap(n_trap, d);
        ArrayList<int[]> trapEdges = result.edges;
        int[] badMatching = result.trapMatching;

        // 2. Solve with Injection
        BipartiteGraphRegular graphTrap = new BipartiteGraphRegular(n_trap, d, trapEdges);
        graphTrap.injectMatching(badMatching);

        System.out.println("Trap State Injected. Starting solve...");

        long startTrap = System.nanoTime();
        int matchedTrap = graphTrap.solve();
        long endTrap = System.nanoTime();

        double timeTrap = (endTrap - startTrap) / 1_000_000.0;
        System.out.printf("Result: %d/%d matched.%n", matchedTrap, n_trap);
        System.out.printf("Time to escape trap: %.2f ms%n", timeTrap);

        // 3. Comparison (Clean Solve)
        System.out.println("Comparison: Solving same graph from scratch (0%)...");
        BipartiteGraphRegular graphClean = new BipartiteGraphRegular(n_trap, d, trapEdges);

        long startClean = System.nanoTime();
        graphClean.solve();
        long endClean = System.nanoTime();

        double timeClean = (endClean - startClean) / 1_000_000.0;
        System.out.printf("Time from scratch:   %.2f ms%n", timeClean);
    }
}