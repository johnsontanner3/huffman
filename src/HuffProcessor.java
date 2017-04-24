import java.util.ArrayList;
import java.util.Arrays;
import java.util.PriorityQueue;

/**
 *	Interface that all compression suites must implement. That is they must be
 *	able to compress a file and also reverse/decompress that process.
 * 
 *	@author Brian Lavallee
 *	@since 5 November 2015
 *  @author Owen Atrachan
 *  @since December 1, 2016
 */
public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); // or 256
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;  // adds a 1 to end?
	public static final int HUFF_COUNTS = HUFF_NUMBER | 2;

	public enum Header{TREE_HEADER, COUNT_HEADER};
	public Header myHeader = Header.TREE_HEADER;
	
	
	public int[] readForCounts(BitInputStream in) {
		int[] ret = new int[257];
		Arrays.fill(ret, 0);
		// fill in ret
		// what is val? it's the char value read by readBits(8) 
		while (true) {
			// do we need to throw any exceptions --  how's the conditional? 
			int val = in.readBits(BITS_PER_WORD); // this returns an int between 0 - 255
			if (val == -1) {
				ret[PSEUDO_EOF] = 1;
				break;
			}
//			else if (val == -1)
//				throw new HuffException("no EOF in file");
			else 
				ret[val] += 1;
		}
		
		return ret;
	}
	
	public HuffNode makeTreeFromCounts(int[] counts) {
		// pq is ordered by weight based on HuffNode compareTo method in HuffNode class 
		PriorityQueue<HuffNode> pq = new PriorityQueue<HuffNode>();
		for (int i=0; i<counts.length; i++) {
			if (counts[i] > 0)
				pq.add(new HuffNode(i, counts[i], null, null));
		}
//		pq.add(new HuffNode(PSEUDO_EOF, 1, null, null));
		
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(-1, left.weight() + right.weight(), left, right);
			
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	public String[] makeCodingsFromTree(HuffNode root) {
		// recursive -- start w/ root node and visits every node in the tree
		// the strings are actually just 0's and 1's, depending on which direction you go
		// what sort of search should we do?
		String[] codes = new String[257];
		String p = "";
		String[] ans = makeCodingsFromTree(root, p, codes);
//		for (String s : ans) {
//			System.out.println(s);
//		}
		int count = 0;
		for (String s : ans) {
			if (s != null)
				count++;
		}
		System.out.println(count);
		return ans;
		
	}
	public String[] makeCodingsFromTree(HuffNode curr, String path, String[] encodings) {
//		if (curr.value() != -1) {
		if ((curr.right() == null) && (curr.left() == null)){
			encodings[curr.value()] = path;
		}
		else {
			makeCodingsFromTree(curr.left(), path + "0", encodings);
			makeCodingsFromTree(curr.right(), path + "1", encodings);
		}
//		encodings[encodings.length -1] = Integer.toString(PSEUDO_EOF);
		return encodings;
	}
	
	public void writeHeaderHelper(HuffNode root, BitOutputStream out) {
//		HuffNode root = root;
//		if (current.value() != -1){
		if ((root.left() == null) && (root.right() == null)) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.value());
		}
		// this recursion never ends... 
		else {
			out.writeBits(1, 0);
			writeHeaderHelper(root.left(), out);
//			out.writeBits(1, 0);
			writeHeaderHelper(root.right(), out);
		}
	}
	
	public void writeHeader(HuffNode root, BitOutputStream out) {
		// basically keep concatenating to out, starting with HUFF_NUMBER
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		// samesies as the pre-order traversal. each character needs 9 bits
		// what information, precisely, is stored in the header?
		// for internal nodes, we don't give a shit about what info is stored 
		// so can we just put a 0 bit for those? 
		// leaves need the 1 ("hey, I'm a leaf!") followed by a 9-bit chunk to accommodate PSEUDO_EOF (257)
		writeHeaderHelper(root, out);
		
	}
	
	public void writeCompressedBits(BitInputStream in, String[] codings, BitOutputStream out) {
		// don't worry about resetting--we do that in the normal compress method
		// look up every 8-bit character from IN in codings
		// how do we know where to look?
		// don't forget the encoding for PSEUDO_EOF at the end
		while (true) {
			int bits = in.readBits(BITS_PER_WORD);
			if (bits != -1) { // is this the right conditional? 
				// bits is our character
				// look up in codings 
//				for (String c : codings) {
//					System.out.println(c);
//				}
				String code = codings[bits];
//				System.out.println(code);
				out.writeBits(code.length(), Integer.parseInt(code, 2));
			}
			else if (bits == -1) {
				String code = codings[PSEUDO_EOF];
				out.writeBits(code.length(), Integer.parseInt(code, 2));
				break;
			}
		}


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
	   int [] counts = readForCounts(in);
	   HuffNode root = makeTreeFromCounts(counts);
	   String[] codings = makeCodingsFromTree(root);
	   writeHeader(root, out);
	   
	   in.reset();
	   
	   writeCompressedBits(in, codings, out);
	}
	
	// read BitInputStream parameter and return root HuffNode
	public HuffNode readTreeHeader(BitInputStream in) {
		// how many bits do we read? is there some sort of standard length for header stuff? 
		// recursively read the PRE-ORDER traversal and return HuffNode with greatest weight
		// well, in PRE-ORDER (root, left, right), we start with the root. So we just read the first item. 
		// and that first item is 9 bits long?
		// we've already read 8 bits in decompress by the time we call this. 
		// okay next bit should be 0, then next 8 bits should be code
		// ahh, maybe we ought to return a root with the whole tree stored underneath its hood... 
		// that's where the recursion comes into play. 
		// for the recursion: if you read a 0 first, you'll need to make 2 recursive calls --left, right
		// root is the first one
		int bit = in.readBits(1);
		if (bit == 1) {
			// what is weight?
			// how do we check if PSEUDO-EOF?
			int val = in.readBits(BITS_PER_WORD+1);
			return new HuffNode(val, 0, null, null);
		}
		else {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(-1,0, left, right);
		}
	}
	
	// read in compressed bits starting at CONTENT and write translation to out
	// how do we translate 
	public void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
	
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1)
				throw new HuffException("bad input, no PSEUDO_EOF");
			else {
				if (bits == 0) current = current.left();
				else current = current.right();
				
				
				if (current.left() == null && current.right() == null) { //at leaf!
					if (current.value() == PSEUDO_EOF)
						break;
					else {
						out.writeBits(BITS_PER_WORD, current.value());
						current = root;
					}
				}
			}
			
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
	public void decompress(BitInputStream in, BitOutputStream out){
		int id = in.readBits(BITS_PER_INT);

		if (id != HUFF_NUMBER && id != HUFF_TREE)
			throw new HuffException("Invalid id");
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);

	}
	
	public void setHeader(Header header) {
        myHeader = header;
        System.out.println("header set to "+myHeader);
    }
	
}