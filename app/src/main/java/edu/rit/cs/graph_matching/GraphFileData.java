package edu.rit.cs.graph_matching;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Data that can be serialized to a binary graph file format. Byte order is
 * big-endian. Data is written to a single-file compressed ZIP archive. The
 * header format of the uncompressed file is as follows:
 * <p>
 * <ul>
 * <li>A magic header containing raw UTF-8 bytes of "GraphFileData v1" (without
 * a length prefix), verifying that this is a graph file</li>
 * <li>A modified UTF-8 encoded string containing the name of the graph,
 * prefixed by its 2-byte encoded byte length</li>
 * <li>A modified UTF-8 encoded string containing some details about the graph,
 * prefixed by its 2-byte encoded byte length</li>
 * <li>The number of vertices in the graph as a 4-byte signed integer</li>
 * <li>The number of edges in the graph as an 8-byte signed integer</li>
 * </ul>
 * <p>
 * The remainder of the file encodes the edges of the graph in blocks. Vertices
 * are signed 4-byte integers in the range [0, n). Blocks are formatted as
 * follows:
 * <p>
 * <ul>
 * <li>The vertex ID ({@code v}) as a 4-byte integer</li>
 * <li>The number of edges ({@code b}) in this block as a 4-byte integer</li>
 * <li>A listing of the {@code b} neighbors of {@code v} whose ID is greater
 * than {@code v}'s, in ascending order by ID, each as a signed 4-byte
 * integer</li>
 * </ul>
 * <p>
 * After each block, if the number of edges seen so far is less than the total
 * number in the graph, the next block begins. If all edges have been
 * read/written, this is the end of the file.
 */
public record GraphFileData(String name,
                            String details,
                            Graph graph) {
    private static final byte[] HEADER = "GraphFileData v1".getBytes(StandardCharsets.UTF_8);

    /**
     * Write this graph data to a file in raw binary form.
     *
     * @param file
     *     the file to write to
     * @throws IOException
     *     if an IO error occurs
     */
    public void writeToFile(File file) throws IOException {
        try (ZipOutputStream zip =
                new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
             DataOutputStream out = new DataOutputStream(zip)) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            zip.putNextEntry(new ZipEntry(name()));

            out.write(GraphFileData.HEADER);

            out.writeUTF(name);
            out.writeUTF(details);
            out.writeInt(graph.size());

            long edges = 0;
            for (int v = 0; v < graph.size(); v++) {
                edges += graph.getDegree(v);
            }
            edges /= 2;
            out.writeLong(edges);

            List<Integer> neighbors = new ArrayList<>();
            for (int v = 0; v < graph.size(); v++) {
                neighbors.clear();
                for (int w : graph.getAllNeighbors(v)) {
                    if (w > v) {
                        neighbors.add(w);
                    }
                }

                if (neighbors.isEmpty()) {
                    continue;
                }

                out.writeInt(v);
                out.writeInt(neighbors.size());

                neighbors.sort(null);
                for (int w : neighbors) {
                    out.writeInt(w);
                }
            }

            zip.closeEntry();
        }
    }

    /**
     * Read graph data from a file in raw binary form into a new object.
     *
     * @param file
     *     the file to read from
     * @return the data that was read
     * @throws IOException
     *     if an IO error occurs
     */
    public static GraphFileData readFile(File file) throws IOException {
        try (ZipInputStream zip =
                new ZipInputStream(new BufferedInputStream(new FileInputStream(file)));
             DataInputStream in = new DataInputStream(new BufferedInputStream(zip))) {
            zip.getNextEntry();

            byte[] header = new byte[GraphFileData.HEADER.length];
            in.readFully(header);
            if (!Arrays.equals(header, GraphFileData.HEADER)) {
                throw new IllegalArgumentException("Invalid graph file format");
            }

            String name = in.readUTF();
            String details = in.readUTF();

            int vertices = in.readInt();
            MutableGraph graph = new SparseGraphImpl(vertices);

            long edges = in.readLong();
            while (edges > 0) {
                int v = in.readInt();
                int edgeCount = in.readInt();

                for (int i = 0; i < edgeCount; i++) {
                    int w = in.readInt();
                    graph.addEdge(v, w);
                }

                edges -= edgeCount;
            }

            zip.closeEntry();
            if (in.available() > 0) {
                throw new IllegalStateException(
                        "Graph file continues beyond end of protocol content");
            }

            return new GraphFileData(name, details, graph);
        }
    }
}
