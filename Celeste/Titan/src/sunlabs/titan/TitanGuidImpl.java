/*
 * Copyright 2007-2010 Oracle. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 *
 * This code is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * only, as published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License version 2 for more details (a copy is
 * included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA
 *
 * Please contact Oracle Corporation, 500 Oracle Parkway, Redwood Shores, CA 94065
 * or visit www.oracle.com if you need additional information or
 * have any questions.
 */
package sunlabs.titan;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;

import sunlabs.asdf.util.TimeProfiler;
import sunlabs.titan.api.TitanGuid;

/**
 * A {@code TitanGuid} is how things (anything and everything) are named in a Titan system.
 */
public class TitanGuidImpl implements TitanGuid {
    private static final long serialVersionUID = 1L;
    
    /**
     * The name of the hash function to use to produce object identifiers.
     * Hash functions, like those used here, produce values of different
     * bit-lengths.  SHA-1, for example, produces 160-bit values and SHA-256
     * produces 256-bit values.  In all cases the bit length of hashes affects
     * other parameters expressed in this class.  Be aware that if you change
     * the hash function, you may need to change some of the other values here
     * as well.
     */
//    public final static String hashFunction = "SHA-1";
    public final static String hashFunction = "SHA-256";

    /**
     * <p>
     * The length of the identifier in hexadecimal numerals.  This is related
     * to the bit length of the hash function specified in {@code hashFunction}.
     * </p><p>
     * For example, for 160-bit value of SHA-1 hash is encoded as a 40 digit
     * hexadecimal number.  See the value for {@code radix} to encode the base
     * of the number encoded.
     * </p><p>
     * Effectively the value for n_digits should be something like:
     * {@code MessageDigest.getInstance(TitanGuidImpl.hashFunction).digest().length * 2}
     * </p>
     */
    public static short n_digits;
    static {
        try {
            TitanGuidImpl.n_digits = (short) (MessageDigest.getInstance(TitanGuidImpl.hashFunction).digest().length * 2);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Shorthand for a wild-card TitanGuid.
     */
    public final static TitanGuid ANY = null;


    /**
     * The radix of the object identifier.
     */
    public final static int radix = 16;

    public final static TitanGuid ZERO = new TitanGuidImpl(new byte[TitanGuidImpl.n_digits / 2], "");


    protected byte[] asBytes;

    /**
     * Return true if the given String is suitable for a TitanGuid.
     * @param string
     */
    public static boolean IsValid(String string) {
        try {
            if (string.length() == TitanGuidImpl.n_digits) {
                new BigInteger(string, TitanGuidImpl.radix);
                return true;
            }
        } catch (NumberFormatException e) {
            /**/
        }
        return false;
    }

    private final static String hexArray = "0123456789ABCDEF";

    public static class IdComparator implements Comparator<TitanGuid> {
        public IdComparator() {

        }

        public int compare(TitanGuid o1, TitanGuid o2) {
        // Compares its two arguments for order.
            return o1.compareTo(o2);
        }

        @Override
        public boolean equals(Object obj) {
            return false;
        }
    }

    /**
     * A constructor that takes an array of bytes as the raw object identifier.
     * The second argument is simply to differentiate the constructor's signature from {@link TitanGuidImpl#TitanGuidImpl(byte[])}.
     * @param bytes
     * @param string
     */
    protected TitanGuidImpl(byte[] bytes, String string) {
        if (bytes.length != TitanGuidImpl.n_digits / 2) {
            throw new IllegalArgumentException("byte array must be " + (TitanGuidImpl.n_digits / 2) + " elements in length");
        }
        this.asBytes = bytes;
    }

    /**
     * Construct a random object-id.
     * <p>
     * See {@link SecureRandom}.
     * </p>
     */
    public TitanGuidImpl() {
        this.asBytes = new byte[TitanGuidImpl.n_digits / 2];
        new SecureRandom().nextBytes(this.asBytes);
    }

    /**
     * Construct a unique duplicate of the given object-id.
     * @param objectId the object-id to duplicate
     */
    public TitanGuidImpl(TitanGuid objectId) {
        this.asBytes = new byte[TitanGuidImpl.n_digits / 2];
        System.arraycopy(objectId.getBytes(), 0, this.asBytes, 0, this.asBytes.length);
    }

    /**
     * Construct a new TitanGuidImpl instance from a String representation of the identifier in hex.
     * <p>
     * The given value must be a contiguous string of hexadecimal numerals {@link TitanGuidImpl#n_digits} in length.
     * </p>
     */
    public TitanGuidImpl(String hexValue, int ignore) throws NumberFormatException {
        if (hexValue.length() == TitanGuidImpl.n_digits) {
            this.asBytes = new byte[TitanGuidImpl.n_digits / 2];
            for (int j = 0; j < this.asBytes.length; j++) {
                int i = j * 2;
                byte hi = (byte) ((TitanGuidImpl.hexArray.indexOf(hexValue.charAt(i)) << 4) & 0xF0);
                byte lo = (byte) (TitanGuidImpl.hexArray.indexOf(hexValue.charAt(i + 1)) & 0x0F);
                this.asBytes[j] = (byte) (hi | lo);
            }
            return;
        }

        throw new NumberFormatException("Improperly formatted object-id: '" + hexValue + "'");
    }

    private static byte[] table =
    { -1, -1, -1, -1, -1, -1, -1, -1,  -1, -1, -1, -1, -1, -1, -1, -1, /*   0-15 */
      -1, -1, -1, -1, -1, -1, -1, -1,  -1, -1, -1, -1, -1, -1, -1, -1, /*  16-31 */
      -1, -1, -1, -1, -1, -1, -1, -1,  -1, -1, -1, -1, -1, -1, -1, -1, /*  32-47 */
       0,  1,  2,  3,  4,  5,  6,  7,   8,  9, -1, -1, -1, -1, -1, -1, /*  48-63 */
      -1, 10, 11, 12, 13, 14, 15, -1,  -1, -1, -1, -1, -1, -1, -1, -1, /*  64-79 */
      -1, -1, -1, -1, -1, -1, -1, -1,  -1, -1, -1, -1, -1, -1, -1, -1, /*  80-95 */
      -1, 10, 11, 12, 13, 14, 15, -1,  -1, -1, -1, -1, -1, -1, -1, -1, /*  96-111 */
      -1, -1, -1, -1, -1, -1, -1, -1,  -1, -1, -1, -1, -1, -1, -1, -1, /* 112-127 */ };
    
    /**
     * Construct a new TitanGuidImpl instance from a String representation of the identifier in hex.
     * <p>
     * The given value must be a contiguous string of hexadecimal numerals {@link TitanGuidImpl#n_digits} in length.
     * </p>
     */
    public TitanGuidImpl(String hexValue) throws NumberFormatException {
    	if (hexValue.length() == TitanGuidImpl.n_digits) {
    		this.asBytes = new byte[TitanGuidImpl.n_digits / 2];
    		byte[] bytes = hexValue.getBytes();

    		for (int j = 0; j < this.asBytes.length; j++) {
    			int i = j * 2;
    			byte hi = (byte) ((TitanGuidImpl.table[bytes[i]] << 4) & 0xF0);
    			byte lo = (byte) (TitanGuidImpl.table[bytes[i + 1]] & 0x0F);
    			this.asBytes[j] = (byte) (hi | lo);
    		}
    		return;
    	}

    	throw new NumberFormatException("Improperly formatted object-id: '" + hexValue + "'");
    }

    /**
     * Construct from an arbitrary byte array.
     *
     * The byte array contains data to hash to produce the identifier.
     */
    public TitanGuidImpl(byte[] data) {
        this(ByteBuffer.wrap(data));
    }

    /**
     * Construct from an arbitrary {@link ByteBuffer}.
     * <p>
     * The {@code ByteBuffer} contains data to hash to produce the identifier.
     * </p>
     */
    public TitanGuidImpl(ByteBuffer buffer) {
        try {
            MessageDigest hash = MessageDigest.getInstance(TitanGuidImpl.hashFunction);
             hash.update(buffer.duplicate());
            this.asBytes = hash.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Construct from a {@link PublicKey} instance.
     */
    public TitanGuidImpl(PublicKey key) {
        this(key.getEncoded());
    }

    /**
     * Create a new {@code TitanGuid} by combining more data to this {@code TitanGuid}.
     *
     * @param data The data to combine.
     */
    public TitanGuid add(byte[] data) {
        return (data == null) ? this : this.add(ByteBuffer.wrap(data));
    }
    
    /**
     * Create a new {@code TitanGuid} by combining more data to this {@code TitanGuid}.
     *
     * @param data The data to combine.
     */
    public TitanGuid add(ByteBuffer data) {
        if (data == null)
            return this;

        try {
            MessageDigest hash = MessageDigest.getInstance(TitanGuidImpl.hashFunction);
            hash.update(this.asBytes);
            hash.update(data);
            return new TitanGuidImpl(hash.digest(), null);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Construct a new {@code TitanGuid} by combining the given instance {@code other} with this instance.
     * If the given {@code TitanGuid} is null, this instance is returned (a new instance is not created).
     */
    public TitanGuid add(TitanGuid other) {
        return (other == null) ? this : this.add(other.getBytes());
    }
    
    /**
     * Construct a new {@code TitanGuid} by combining the bytes from the given String with this instance.
     * If the given String is null, this instance is returned (a new instance is not created).
     */
    public TitanGuid add(String string) {
        return (string == null) ? this : this.add(string.getBytes());
    }

    public TitanGuid add(long v) {
        byte[] buffer = new byte[8];
        
        buffer[0] = (byte)(v >>> 56);
        buffer[1] = (byte)(v >>> 48);
        buffer[2] = (byte)(v >>> 40);
        buffer[3] = (byte)(v >>> 32);
        buffer[4] = (byte)(v >>> 24);
        buffer[5] = (byte)(v >>> 16);
        buffer[6] = (byte)(v >>>  8);
        buffer[7] = (byte)(v >>>  0);
        return this.add(buffer);
    }

    /**
     * Get the bytes that comprise this object identifier as an array.
     */
    public byte[] getBytes() {
        return this.asBytes;
    }

    /**
     * A String representation of the identifier.
     */
//    @Override
    public String toString2() {
        StringBuilder s = new StringBuilder();
		for (int i = 0; i < this.asBytes.length; i++) {
		    s.append(hexArray.charAt((this.asBytes[i] >> 4) & 0xF));
		    s.append(hexArray.charAt((this.asBytes[i]     ) & 0xF));
		}
		return s.toString();
    }
    
    private static String[] table2 = {
    		"00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "0A", "0B", "0C", "0D", "0E", "0F", 
    		"10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "1A", "1B", "1C", "1D", "1E", "1F", 
    		"20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "2A", "2B", "2C", "2D", "2E", "2F", 
    		"30", "31", "32", "33", "34", "35", "36", "37", "38", "39", "3A", "3B", "3C", "3D", "3E", "3F", 
    		"40", "41", "42", "43", "44", "45", "46", "47", "48", "49", "4A", "4B", "4C", "4D", "4E", "4F", 
    		"50", "51", "52", "53", "54", "55", "56", "57", "58", "59", "5A", "5B", "5C", "5D", "5E", "5F", 
    		"60", "61", "62", "63", "64", "65", "66", "67", "68", "69", "6A", "6B", "6C", "6D", "6E", "6F", 
    		"70", "71", "72", "73", "74", "75", "76", "77", "78", "79", "7A", "7B", "7C", "7D", "7E", "7F", 
    		"80", "81", "82", "83", "84", "85", "86", "87", "88", "89", "8A", "8B", "8C", "8D", "8E", "8F", 
    		"90", "91", "92", "93", "94", "95", "96", "97", "98", "99", "9A", "9B", "9C", "9D", "9E", "9F", 
    		"A0", "A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "AA", "AB", "AC", "AD", "AE", "AF", 
    		"B0", "B1", "B2", "B3", "B4", "B5", "B6", "B7", "B8", "B9", "BA", "BB", "BC", "BD", "BE", "BF", 
    		"C0", "C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "CA", "CB", "CC", "CD", "CE", "CF", 
    		"D0", "D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8", "D9", "DA", "DB", "DC", "DD", "DE", "DF", 
    		"E0", "E1", "E2", "E3", "E4", "E5", "E6", "E7", "E8", "E9", "EA", "EB", "EC", "ED", "EE", "EF", 
    		"F0", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "FA", "FB", "FC", "FD", "FE", "FF", 
    };
    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
		for (int i = 0; i < this.asBytes.length; i++) {
			s.append(TitanGuidImpl.table2[this.asBytes[i] & 0xFF]);
		}
		return s.toString();
    }

    public int sharedPrefix(TitanGuid other) {
        byte[] otherBytes = other.getBytes();
        for (int i = 0; i < TitanGuidImpl.n_digits / 2; i++) {
            if (this.asBytes[i] != otherBytes[i]) {
                if ((this.asBytes[i] >> 4) == (otherBytes[i] >> 4))
                    return i * 2 + 1;
                return i * 2;
            }
        }

        return n_digits;
    }

    /**
     * Returns a hash code for this instance.
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(this.asBytes);
    }

    /**
     * Return true if the other object-id is equal to this one.
     * @param other - the object-id instance to compare.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (other == null)
            return false;

        if (other instanceof TitanGuidImpl) {
            TitanGuidImpl o = (TitanGuidImpl) other;
            if (this.asBytes.length != o.asBytes.length) {
                return false;
            }

            for (int i = 0; i < this.asBytes.length; i++) {
                if (this.asBytes[i] != o.asBytes[i]) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public int compareTo(final TitanGuid other) {
        final byte[] otherBytes = other.getBytes();

        for (int i = 0; i < this.asBytes.length; i++) {
            int b1 = this.asBytes[i] & 0xFF;
            int b2 = otherBytes[i] & 0xFF;
            if (b1 != b2) {
                int diff = b1 - b2;
                int result = diff < 0 ? -1 : (diff == 0) ? 0 : 1;
                return result;
            }
        }

        return 0;
    }

    /**
     * Encode the routing table "distance" of this object-id with another.
     *
     * The value 0 indicates equality. Another other value is an integer encoding of
     * the length of the shared digit prefix and the difference between this object-id and the other at the first non-shared digit.
     *
     * @param other
     */
    public int distance(final TitanGuid other) {
//        final byte[] otherBytes = other.getBytes();

//        System.out.printf("comparer %s %s ", this.toString(), other.toString());

        for (int i = 0; i < TitanGuidImpl.n_digits; i++) {
            int b1 = this.digit(i);
            int b2 = other.digit(i);
            if (b1 != b2) {
                int diff = b2 - b1;
                if (diff < 0)
                    diff += 16;
//                System.out.printf("%d_%d%n", (TitanGuidImpl.n_digits-i), diff);
                return (TitanGuidImpl.n_digits-i) * 100 + diff;
            }
        }

//        System.out.printf("%d_%d%n", 0,0);
        return 0;
    }

    public TitanGuid getGuid() {
        return new TitanGuidImpl(this.getBytes());
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeInt(this.asBytes.length);
        out.write(this.asBytes);
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        int length = in.readInt();
        this.asBytes = new byte[length];
        in.read(this.asBytes);
    }

    /**
     * Return the integer value of the n'th hexadecimal digit in this object-id. 
     * @param n
     * @return The integer value of the digit at position <code>n</code>.
     */
    public int digit(int n) {
        try {
            int i = n / 2;
            if ((n % 2) == 0) { // even
                return ((this.asBytes[i] >> 4) & 0xF);
            } else { // odd
                return (this.asBytes[i] & 0xF);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.printf("N=%d, string=%s, bytes=%d\n", n, this.toString(), this.asBytes.length);
            throw new RuntimeException(e);
        }
    }
    
    public static void main(String[] args) {
		System.setProperty("enableProfiling", "true");
		
    	TitanGuidImpl id;
    	
    	TimeProfiler profiler = new TimeProfiler();
    	for (int i = 0; i < 10000; i++) {
    		id = new TitanGuidImpl();
    		if (!id.equals(new TitanGuidImpl(id.toString()))) {
    			System.err.printf("error with %s%n", id);
    		}
    	}
    	profiler.stamp("fini");
    	System.out.printf("%s%n", profiler);

    	profiler = new TimeProfiler();
    	for (int i = 0; i < 10000; i++) {
    		id = new TitanGuidImpl();
    		if (!id.equals(new TitanGuidImpl(id.toString(), (int) 1))) {
    			System.err.printf("error with %s%n", id);
    		}
    	}
    	profiler.stamp("fini");
    	System.out.printf("%s%n", profiler);
    	

    	profiler = new TimeProfiler();
    	for (int i = 0; i < 10000; i++) {
    		id = new TitanGuidImpl();
    		if (!id.equals(new TitanGuidImpl(id.toString2(), (int) 1))) {
    			System.err.printf("error with %s%n", id);
    		}
    	}
    	profiler.stamp("fini");
    	System.out.printf("%s%n", profiler);
    	
    }

    public short getHopCount() {
        return TitanGuidImpl.n_digits;
    }
}
