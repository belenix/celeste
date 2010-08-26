package sunlabs.asdf.util;
public class sorter {
    // Find the position at which a fits in this array.
    public static int findLeast(int[] array, int a) {
        for (int i = 0; i < array.length; i++) {
            if ( array[i] > i) {
                return i;
            } else if (array[i] == Integer.MAX_VALUE) {
                return i;
            }
        }
        // not reached.
        return -1;
    }

    public static void shiftRight(int[] result, int position) {
        // Starting at position, shift all of the values up one element.

        for (int i = result.length -1; i > position; i--) {
            result[i] = result[i-1];
        }
    }

    public static void sort(int[] n) {
        int[] result = new int[n.length];
       
        for (int i = 0; i < n.length; i++) {
            result[i] = Integer.MAX_VALUE;
        }   

       // iterate through the input array
        int j = 1;
        result[0] = n[0];
        // initialise result to all MAX_VALUE

        for (int i = 1; i < n.length; i++) {
            int index = findLeast(result, n[i]); // gets the index of the postion that this element fits
            if (index < i)
                shiftRight(result, index);
            result[j++] = n[i];       
        }

        for (int i = 0; i < n.length; i++) {
            n[i] = result[i];
        }   
    }

    public static void main(String[] args) {
        int[] array = new int[5];
        for (int i = array.length-1; i > 0; i--) {
            array[i] = i;
        }
        
        sort(array);
        
        for (int i = 0; i < array.length; i++) {
            System.out.printf("%d ", array[i]);
        }
        System.out.println();
    }
}