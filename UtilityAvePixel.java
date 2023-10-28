import java.io.*;
import java.util.*;

public class UtilityAvePixel {

    public void Compress(int[][][] pixels, String outputFileName) throws IOException {
        // The following is a bad implementation that we have intentionally put in the
        // function to make App.java run, you should
        // write code to reimplement the function without changing any of the input
        // parameters, and making sure the compressed file
        // gets written into outputFileName


        int[][][] compressedRGBPixels = averagePixels(pixels);

        // Step 1: Calculate the frequency of each color value

        Map<Integer, Integer> colorFrequency = new HashMap<>();
        int width = compressedRGBPixels.length;
        int height = 0;
        for (int[][] row : compressedRGBPixels) {
            height = row.length;
            for (int[] pixel : row) {
                for (int color : pixel) {
                    colorFrequency.put(color, colorFrequency.getOrDefault(color, 0) + 1);
                }
            }
        }

        // Step 2: Build the Huffman tree
        HuffmanTree huffmanTree = new HuffmanTree(width, height, 3);
        huffmanTree.buildHuffmanTree(colorFrequency);
        // Step 3: Create a mapping of color values to Huffman codes
        Map<Integer, String> huffmanCodes = huffmanTree.generateHuffmanCodes();

        // Step 4: Encode the pixel data using Huffman codes
        List<String> encodedData = new ArrayList<>();
        for (int[][] row : compressedRGBPixels) {
            for (int[] pixel : row) {
                for (int color : pixel) {
                    encodedData.add(huffmanCodes.get(color));
                }
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
            oos.writeObject(huffmanTree); // Serialize Huffman tree for decoding
            oos.writeObject(compressedDataBytes); // Serialize the compressed data

            int[] dimensions = {pixels.length, pixels[0].length};
            oos.writeObject(dimensions);
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
    
                // Step 3: Read the original width and height
                int[] originalDimensions = (int[]) ois.readObject();
                int originalWidth = originalDimensions[0];
                int originalHeight = originalDimensions[1];
    
                // Step 4: Calculate the compressed width and height
                int compressedWidth = originalWidth / 2;
                int compressedHeight = originalHeight / 2;
    
                // Step 5: Reconstruct the original int[][][] pixel array
                int colorDepth = huffmanTree.colorDepth;
                int[][][] pixels = new int[originalWidth][originalHeight][colorDepth];
    
                int currentBit = 0; // Initialize the bit position
                HuffmanTree.HuffmanNode currentNode = huffmanTree.root; // Start from the root of the Huffman tree
    
                for (int x = 0; x < compressedWidth; x++) {
                    for (int y = 0; y < compressedHeight; y++) {
                        for (int z = 0; z < colorDepth; z++) {
                            currentNode = huffmanTree.root; // Reset to the root for each pixel
                            while (true) {
                                // Check if we've reached the end of the compressed data
                                if (currentBit >= compressedDataByteArray.length * 8) {
                                    return pixels; // Return the reconstructed image
                                }
    
                                if (currentNode.isLeaf()) {
                                    // We've reached a leaf node, which represents a color value
                                    int color = currentNode.color;
    
                                    // Determine the position in the decompressed array
                                    int decompressedX = x * 2;
                                    int decompressedY = y * 2;
                                    // Fill the corresponding 2x2 block in the decompressed data
                                    pixels[decompressedX][decompressedY][z] = color;
                                    pixels[decompressedX + 1][decompressedY][z] = color;
                                    pixels[decompressedX][decompressedY + 1][z] = color;
                                    pixels[decompressedX + 1][decompressedY + 1][z] = color;
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

   

    public static int[][][] averagePixels(int[][][] pixels) {
        int width = pixels.length;
        int height = pixels[0].length;
    
        int newWidth = width / 2;
        int newHeight = height / 2;
        int[][][] averagedPixels = new int[newWidth][newHeight][3];
    
        for (int i = 0; i < newWidth; i++) {
            for (int j = 0; j < newHeight; j++) {
                int sumR = 0, sumG = 0, sumB = 0;
    
                for (int x = i * 2; x < i * 2 + 2; x++) {
                    for (int y = j * 2; y < j * 2 + 2; y++) {
                        sumR += pixels[x][y][0];
                        sumG += pixels[x][y][1];
                        sumB += pixels[x][y][2];
                    }
                }
    
                int avgR = sumR / 4;
                int avgG = sumG / 4;
                int avgB = sumB / 4;
    
                averagedPixels[i][j][0] = avgR;
                averagedPixels[i][j][1] = avgG;
                averagedPixels[i][j][2] = avgB;
            }
        }
    
        return averagedPixels;
    }



}
