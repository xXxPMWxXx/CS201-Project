import java.io.*;
import java.util.*;

public class UtilityColorQuantization {

    public void Compress(int[][][] pixels, String outputFileName) throws IOException {

        int maxColors = 126;
        Cube cube = new Cube(pixels, maxColors);
        cube.classification();
        cube.reduction();

        int[][] assignment = cube.assignment();
        int[] colormap = cube.colormap;

        // Step 1: Calculate the frequency of each color value
        Map<Integer, Integer> colorFrequency = new HashMap<>();
        int width = assignment.length;
        int height = assignment[0].length;
        for (int[] row : assignment) {
            for (int mapIndex : row) {
                colorFrequency.put(mapIndex, colorFrequency.getOrDefault(mapIndex, 0) + 1);

            }
        }

        // Step 2: Build the Huffman tree
        HuffmanTree huffmanTree = new HuffmanTree(width, height, 3);
        huffmanTree.buildHuffmanTree(colorFrequency);
        // Step 3: Create a mapping of color values to Huffman codes
        Map<Integer, String> huffmanCodes = huffmanTree.generateHuffmanCodes();

        // Step 4: Encode the pixel data using Huffman codes
        List<String> encodedData = new ArrayList<>();
        for (int[] row : assignment) {
            for (int mapIndex : row) {
                encodedData.add(huffmanCodes.get(mapIndex));

            }
        }

        StringBuilder compressedDataBuilder = new StringBuilder();
        for (String code : encodedData) {
            compressedDataBuilder.append(code);
        }
        String compressedDataString = compressedDataBuilder.toString();
        // Convert binary string to bytes
        byte[] compressedDataBytes = convertBinaryStringToBytes(compressedDataString);

        // Step 5: Write the compressed data into the output file
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(outputFileName))) {
            oos.writeObject(huffmanTree);
            oos.writeObject(compressedDataBytes);
            oos.writeObject(colormap);
        }
    }

    public int[][][] Decompress(String inputFileName) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(inputFileName))) {
            // Step 1: Read the Huffman tree from the input file
            Object huffmanTreeObject = ois.readObject();

            if (huffmanTreeObject instanceof HuffmanTree) {
                HuffmanTree huffmanTree = (HuffmanTree) huffmanTreeObject;

                // Step 2: Read the compressed data as a byte array
                byte[] compressedDataByteArray = (byte[]) ois.readObject();
                int[] colormap = (int[]) ois.readObject();

                // Step 3: Reconstruct the original int[][][] pixel array
                int width = huffmanTree.width;
                int height = huffmanTree.height;
                int colorDepth = huffmanTree.colorDepth;
                int[][][] pixels = new int[width][height][colorDepth];

                int currentBit = 0; // Initialize the bit position
                HuffmanTree.HuffmanNode currentNode = huffmanTree.root; // Start from the root of the Huffman tree

                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        currentNode = huffmanTree.root; // Reset to the root for each pixel
                        while (true) {
                            // Check if we've reached the end of the compressed data
                            if (currentBit >= compressedDataByteArray.length * 8) {
                                return pixels; // Return the reconstructed image
                            }

                            if (currentNode.isLeaf()) {
                                // We've reached a leaf node, which represents a color value
                                int color = colormap[currentNode.color];
                                int red = (color >> 16) & 0xFF;
                                int green = (color >> 8) & 0xFF;
                                int blue = color & 0xFF;
                                pixels[x][y][0] = red;
                                pixels[x][y][1] = green;
                                pixels[x][y][2] = blue;
                                break;
                            }

                            // Read one bit from the compressed data
                            int bit = (compressedDataByteArray[currentBit >> 3] >> (7 - (currentBit % 8))) & 1;
                            currentBit++;

                            // Traverse the Huffman tree based on the bit
                            if (bit == 0) {
                                currentNode = currentNode.left;
                            } else {
                                currentNode = currentNode.right;
                            }
                        }

                    }
                }

                return pixels;
            } else {
                throw new IOException("Invalid object type in the input file");
            }
        }
    }

    public static byte[] convertBinaryStringToBytes(String binaryString) {
        int length = binaryString.length();
        int byteCount = (length + 7) / 8; // Calculate the number of bytes required

        byte[] bytes = new byte[byteCount];

        for (int i = 0; i < byteCount; i++) {
            int start = i * 8;
            int end = Math.min(start + 8, length);
            String chunk = binaryString.substring(start, end);
            bytes[i] = (byte) Integer.parseInt(chunk, 2);
        }

        return bytes;
    }

    static class HuffmanTree implements Serializable {
        private HuffmanNode root;
        private int width;
        private int height;
        private int colorDepth;

        public HuffmanTree(int width, int height, int colorDepth) {
            this.width = width;
            this.height = height;
            this.colorDepth = colorDepth;
        }

        public void buildHuffmanTree(Map<Integer, Integer> colorFrequency) {
            PriorityQueue<HuffmanNode> priorityQueue = new PriorityQueue<>((a, b) -> a.frequency - b.frequency);

            // Create leaf nodes for each color value
            for (Map.Entry<Integer, Integer> entry : colorFrequency.entrySet()) {
                HuffmanNode node = new HuffmanNode(entry.getKey(), entry.getValue());
                priorityQueue.offer(node);
            }

            // Build the Huffman tree
            while (priorityQueue.size() > 1) {
                HuffmanNode left = priorityQueue.poll();
                HuffmanNode right = priorityQueue.poll();
                HuffmanNode parent = new HuffmanNode(-1, left.frequency + right.frequency);
                parent.left = left;
                parent.right = right;
                priorityQueue.offer(parent);
            }
            root = priorityQueue.poll();
        }

        public Map<Integer, String> generateHuffmanCodes() {
            Map<Integer, String> huffmanCodes = new HashMap<>();
            if (root != null) {
                String code = "";
                generateHuffmanCodesRecursive(root, code, huffmanCodes);
            }
            return huffmanCodes;
        }

        private void generateHuffmanCodesRecursive(HuffmanNode node, String code, Map<Integer, String> huffmanCodes) {
            if (node.isLeaf()) {
                huffmanCodes.put(node.color, code);
            } else {
                if (node.left != null) {
                    // Append a '0' bit to the code
                    generateHuffmanCodesRecursive(node.left, code + "0", huffmanCodes);
                }
                if (node.right != null) {
                    // Append a '1' bit to the code
                    generateHuffmanCodesRecursive(node.right, code + "1", huffmanCodes);
                }
            }
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getColorDepth() {
            return colorDepth;
        }

        static class HuffmanNode implements Serializable {
            int color;
            int frequency;
            HuffmanNode left;
            HuffmanNode right;

            HuffmanNode(int color, int frequency) {
                this.color = color;
                this.frequency = frequency;
            }

            boolean isLeaf() {
                return left == null && right == null;
            }
        }

    }

    final static boolean QUICK = true;

    final static int MAX_RGB = 255;
    final static int MAX_NODES = 266817;
    final static int MAX_TREE_DEPTH = 8;

    // these are precomputed in advance
    static int SQUARES[];
    static int SHIFT[];

    static {
        SQUARES = new int[MAX_RGB + MAX_RGB + 1];
        for (int i = -MAX_RGB; i <= MAX_RGB; i++) {
            SQUARES[i + MAX_RGB] = i * i;
        }

        SHIFT = new int[MAX_TREE_DEPTH + 1];
        for (int i = 0; i < MAX_TREE_DEPTH + 1; ++i) {
            SHIFT[i] = 1 << (15 - i);
        }
    }

    static class Cube {
        int pixels[][][];
        int max_colors;
        int colormap[];

        Node root;
        int depth;

        // counter for the number of colors in the cube. this gets
        // recalculated often.
        int colors;

        // counter for the number of nodes in the tree
        int nodes;

        Cube(int pixels[][][], int max_colors) {
            this.pixels = pixels;
            this.max_colors = max_colors;

            int i = max_colors;
            // tree_depth = log max_colors
            // 4
            for (depth = 1; i != 0; depth++) {
                i /= 4;
            }
            if (depth > 1) {
                --depth;
            }
            if (depth > MAX_TREE_DEPTH) {
                depth = MAX_TREE_DEPTH;
            } else if (depth < 2) {
                depth = 2;
            }

            root = new Node(this);
        }

        void classification() {
            int pixels[][][] = this.pixels;

            int width = pixels.length;
            int height = pixels[0].length;

            // convert to indexed color
            for (int x = width; x-- > 0;) {
                for (int y = height; y-- > 0;) {
                    int red = pixels[x][y][0];
                    int green = pixels[x][y][1];
                    int blue = pixels[x][y][2];

                    // a hard limit on the number of nodes in the tree
                    if (nodes > MAX_NODES) {
                        System.out.println("pruning");
                        root.pruneLevel();
                        --depth;
                    }

                    // walk the tree to depth, increasing the
                    // number_pixels count for each node
                    Node node = root;
                    for (int level = 1; level <= depth; ++level) {
                        int id = (((red > node.mid_red ? 1 : 0) << 0) |
                                ((green > node.mid_green ? 1 : 0) << 1) |
                                ((blue > node.mid_blue ? 1 : 0) << 2));
                        if (node.child[id] == null) {
                            new Node(node, id, level);
                        }
                        node = node.child[id];
                        node.number_pixels += SHIFT[level];
                    }

                    ++node.unique;
                    node.total_red += red;
                    node.total_green += green;
                    node.total_blue += blue;
                }
            }
        }

        void reduction() {
            int threshold = 1;
            while (colors > max_colors) {
                colors = 0;
                threshold = root.reduce(threshold, Integer.MAX_VALUE);
            }
        }

        static class Search {
            int distance;
            int color_number;
        }

        int[][] assignment() {
            colormap = new int[colors];

            colors = 0;
            root.colormap();

            int pixels[][][] = this.pixels;

            int width = pixels.length;
            int height = pixels[0].length;
            int results[][] = new int[width][height];

            Search search = new Search();

            // convert to indexed color
            for (int x = width; x-- > 0;) {
                for (int y = height; y-- > 0;) {
                    int red = pixels[x][y][0];
                    int green = pixels[x][y][1];
                    int blue = pixels[x][y][2];

                    // walk the tree to find the cube containing that color
                    Node node = root;
                    for (;;) {
                        int id = (((red > node.mid_red ? 1 : 0) << 0) |
                                ((green > node.mid_green ? 1 : 0) << 1) |
                                ((blue > node.mid_blue ? 1 : 0) << 2));
                        if (node.child[id] == null) {
                            break;
                        }
                        node = node.child[id];
                    }

                    if (QUICK) {
                        // if QUICK is set, just use that
                        // node. Strictly speaking, this isn't
                        // necessarily best match.
                        results[x][y] = node.color_number;
                    } else {
                        // Find the closest color.
                        search.distance = Integer.MAX_VALUE;
                        node.parent.closestColor(red, green, blue, search);
                        results[x][y] = search.color_number;
                    }
                }
            }
            return results;
        }

        int[] getColorMap() {
            return this.colormap;
        }

        static class Node {
            Cube cube;

            // parent node
            Node parent;

            // child nodes
            Node child[];
            int nchild;

            // our index within our parent
            int id;
            // our level within the tree
            int level;
            // our color midpoint
            int mid_red;
            int mid_green;
            int mid_blue;

            // the pixel count for this node and all children
            int number_pixels;

            // the pixel count for this node
            int unique;
            // the sum of all pixels contained in this node
            int total_red;
            int total_green;
            int total_blue;

            // used to build the colormap
            int color_number;

            Node(Cube cube) {
                this.cube = cube;
                this.parent = this;
                this.child = new Node[8];
                this.id = 0;
                this.level = 0;

                this.number_pixels = Integer.MAX_VALUE;

                this.mid_red = (MAX_RGB + 1) >> 1;
                this.mid_green = (MAX_RGB + 1) >> 1;
                this.mid_blue = (MAX_RGB + 1) >> 1;
            }

            Node(Node parent, int id, int level) {
                this.cube = parent.cube;
                this.parent = parent;
                this.child = new Node[8];
                this.id = id;
                this.level = level;

                // add to the cube
                ++cube.nodes;
                if (level == cube.depth) {
                    ++cube.colors;
                }

                // add to the parent
                ++parent.nchild;
                parent.child[id] = this;

                // figure out our midpoint
                int bi = (1 << (MAX_TREE_DEPTH - level)) >> 1;
                mid_red = parent.mid_red + ((id & 1) > 0 ? bi : -bi);
                mid_green = parent.mid_green + ((id & 2) > 0 ? bi : -bi);
                mid_blue = parent.mid_blue + ((id & 4) > 0 ? bi : -bi);
            }

            /**
             * Remove this child node, and make sure our parent
             * absorbs our pixel statistics.
             */
            void pruneChild() {
                --parent.nchild;
                parent.unique += unique;
                parent.total_red += total_red;
                parent.total_green += total_green;
                parent.total_blue += total_blue;
                parent.child[id] = null;
                --cube.nodes;
                cube = null;
                parent = null;
            }

            /**
             * Prune the lowest layer of the tree.
             */
            void pruneLevel() {
                if (nchild != 0) {
                    for (int id = 0; id < 8; id++) {
                        if (child[id] != null) {
                            child[id].pruneLevel();
                        }
                    }
                }
                if (level == cube.depth) {
                    pruneChild();
                }
            }

            /**
             * Remove any nodes that have fewer than threshold
             * pixels. Also, as long as we're walking the tree:
             *
             * - figure out the color with the fewest pixels
             * - recalculate the total number of colors in the tree
             */
            int reduce(int threshold, int next_threshold) {
                if (nchild != 0) {
                    for (int id = 0; id < 8; id++) {
                        if (child[id] != null) {
                            next_threshold = child[id].reduce(threshold, next_threshold);
                        }
                    }
                }
                if (number_pixels <= threshold) {
                    pruneChild();
                } else {
                    if (unique != 0) {
                        cube.colors++;
                    }
                    if (number_pixels < next_threshold) {
                        next_threshold = number_pixels;
                    }
                }
                return next_threshold;
            }

            /*
             * colormap traverses the color cube tree and notes each
             * colormap entry. A colormap entry is any node in the
             * color cube tree where the number of unique colors is
             * not zero.
             */
            void colormap() {
                if (nchild != 0) {
                    for (int id = 0; id < 8; id++) {
                        if (child[id] != null) {
                            child[id].colormap();
                        }
                    }
                }
                if (unique != 0) {
                    int r = ((total_red + (unique >> 1)) / unique);
                    int g = ((total_green + (unique >> 1)) / unique);
                    int b = ((total_blue + (unique >> 1)) / unique);
                    cube.colormap[cube.colors] = (((0xFF) << 24) |
                            ((r & 0xFF) << 16) |
                            ((g & 0xFF) << 8) |
                            ((b & 0xFF) << 0));
                    color_number = cube.colors++;
                }
            }

            /*
             * ClosestColor traverses the color cube tree at a
             * particular node and determines which colormap entry
             * best represents the input color.
             */
            void closestColor(int red, int green, int blue, Search search) {
                if (nchild != 0) {
                    for (int id = 0; id < 8; id++) {
                        if (child[id] != null) {
                            child[id].closestColor(red, green, blue, search);
                        }
                    }
                }

                if (unique != 0) {
                    int color = cube.colormap[color_number];
                    int distance = distance(color, red, green, blue);
                    if (distance < search.distance) {
                        search.distance = distance;
                        search.color_number = color_number;
                    }
                }
            }

            /**
             * Figure out the distance between this node and som color.
             */
            final static int distance(int color, int r, int g, int b) {
                return (SQUARES[((color >> 16) & 0xFF) - r + MAX_RGB] +
                        SQUARES[((color >> 8) & 0xFF) - g + MAX_RGB] +
                        SQUARES[((color >> 0) & 0xFF) - b + MAX_RGB]);
            }

            public String toString() {
                StringBuffer buf = new StringBuffer();
                if (parent == this) {
                    buf.append("root");
                } else {
                    buf.append("node");
                }
                buf.append(' ');
                buf.append(level);
                buf.append(" [");
                buf.append(mid_red);
                buf.append(',');
                buf.append(mid_green);
                buf.append(',');
                buf.append(mid_blue);
                buf.append(']');
                return new String(buf);
            }
        }

    }

}
