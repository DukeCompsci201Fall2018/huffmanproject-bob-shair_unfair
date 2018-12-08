import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 * @author Andrew Kellogg Peeler
 * @author Ben Harris
 */


public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}
	

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		if (myDebugLevel>=DEBUG_HIGH) {
			System.out.printf("wrote magic number %d\n", HUFF_TREE);
		}
		writeHeader(root,out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}
	
	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE + 1];
		freq[256] = 1; //sets freq[PSEUDO_EOF] = 1;
		
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			int dex = val;
			freq[dex]+=1;
		}
		return freq;
	}
	
	private HuffNode makeTreeFromCounts(int[] freq) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for (int dex = 0; dex<freq.length; dex++) {
			if (freq[dex] >0) pq.add(new HuffNode(dex, freq[dex],null,null));
		}
		if (myDebugLevel >= DEBUG_HIGH) {
			System.out.printf("pq created with %d nodes\n", pq.size());
		}
		
		while (pq.size()>1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight+right.myWeight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);
		return encodings;
	}
	
	private void codingHelper(HuffNode root, String path, String[] encodings) {
		if (root.myLeft==null && root.myRight== null) {
			encodings[root.myValue] = path;
			if (myDebugLevel>= DEBUG_HIGH) {
				System.out.printf("encoding for %d is %s\n", root.myValue,path);
			}
			return;
		}
		String pathL = path + "0";
		String pathR = path + "1";
		codingHelper(root.myLeft, pathL, encodings);
		codingHelper(root.myRight, pathR, encodings);
	}
	
	private void writeHeader(HuffNode root, BitOutputStream output) {
		if (root.myLeft == null && root.myRight == null) {
			output.writeBits(1, 1);
			output.writeBits(BITS_PER_WORD+1, root.myValue);
			if (myDebugLevel>=DEBUG_HIGH) {
				System.out.printf("wrote leaf for tree %d\n", root.myValue);
			}
		}
		else {
			output.writeBits(1, 0);
			writeHeader(root.myLeft, output);
			writeHeader(root.myRight, output);
		}
	}
	
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while (true) {
			int dex = in.readBits(BITS_PER_WORD);
			if (dex == -1) break;
			String code = codings[dex];
			out.writeBits(code.length(), Integer.parseInt(code,2));
			if (myDebugLevel>=DEBUG_HIGH) {
				System.out.printf("%d wrote %d for %d bits\n", dex,Integer.parseInt(code,2), code.length());
			}
		}
		String code = codings[256];
		out.writeBits(code.length(), Integer.parseInt(code,2));
		if (myDebugLevel>=DEBUG_HIGH) {
			System.out.printf("wrote %d for %d bits PSEUDO_EOF\n", Integer.parseInt(code,2),code.length());
		}
		
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out) {
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with "+bits);
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}
	
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit==-1) throw new HuffException("reading bit failed");
		if (bit==0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		else {
			int value = in.readBits(BITS_PER_WORD + 1);
			if (myDebugLevel >= DEBUG_HIGH) {
				System.out.printf("added leaf with value %d\n", value);
			}
			return new HuffNode(value, 0, null, null);
		}
		
	}
	
	private void readCompressedBits(HuffNode root, BitInputStream input, BitOutputStream output) {
		HuffNode current = root;
		while (true) {
			int bits = input.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if (bits == 0) current = current.myLeft;
				else current = current.myRight;
				
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) break;
					else {
						output.writeBits(BITS_PER_WORD,current.myValue); 
						if (myDebugLevel>=DEBUG_HIGH) {
							System.out.printf("read leaf with value %d\n", current.myValue,BITS_PER_WORD);
						}
						current = root;
						
					}
				}
			}
		}
	}
	
}