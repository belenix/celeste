package sunlabs.celeste.client.application;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

import java.util.Random;

/**
 * Create data akin to the data produced by the SortGen.c
 * 
 * @author Glenn Scott
 */
public class SortGen {
    public static Random random = new Random();
    
    public static String makeKey(int length) {
        byte[] bytes = new byte[length];
        
        SortGen.random.nextBytes(bytes);
        
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) ((Math.abs(bytes[i]) % 95) + 32);
        }
        return new String(bytes);
    }

    static byte character = 'A';
    static int charCounter = 0;
    public static String makeCrap(int length) {
        byte[] bytes = new byte[length];

        for (int i = 0; i < length; i++) {
            bytes[i] = character;
            charCounter = (charCounter + 1) % 260;
            character = (byte) ((byte) 'A' + (byte) (charCounter / 10));
        }
        
        charCounter = ((charCounter / 10) * 10) + 10;
        character = (byte) ((byte) 'A' + (byte) (charCounter / 10));        

        return new String(bytes);
    }
    
    public static void main(String[] args) throws Exception {
        long lines = 10;
        
        OutputStream out = System.out;

        if (args.length > 0) {
            lines = Long.parseLong(args[0]);
            if (args.length > 1) {
                out = new BufferedOutputStream(new FileOutputStream(args[1]), 16*1024*1024);
            }
        }
        
        for (long i = 0; i < lines; i++) {
            String line = String.format("%s%10d%s\r\n", makeKey(10), i, makeCrap(78));
            out.write(line.getBytes());
        }
        out.flush();
    }
}
