package sunlabs.titan.node.services.census;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class SelectComparator implements Serializable {
    private static final long serialVersionUID = 1L;

    enum CompareOperation {
        EQUAL,
        NOTEQUAL,
        LESS,
        LESSEQUAL,
        GREATER,
        GREATEREQUAL
    };

    private final static Map<String,CompareOperation> operations = new HashMap<String,CompareOperation>();
    static {
        SelectComparator.operations.put("=", CompareOperation.EQUAL);
        SelectComparator.operations.put("!=", CompareOperation.NOTEQUAL);
        SelectComparator.operations.put("<", CompareOperation.LESS);
        SelectComparator.operations.put("<=", CompareOperation.LESSEQUAL);
        SelectComparator.operations.put(">", CompareOperation.GREATER);
        SelectComparator.operations.put(">=", CompareOperation.GREATEREQUAL);
    }
    
    private String name;
    private CompareOperation op;
    private String value;
    
    private static String operators = "!=<>";
    
    public SelectComparator(String name, SelectComparator.CompareOperation operation, String value) {
        this.name = name;
        this.op = operation;
        this.value = value;
    }

    public SelectComparator(String name, String spec) throws IllegalArgumentException {
        StringBuilder o = new StringBuilder();
        StringBuilder v = new StringBuilder();
        
        // collect name
        int index = 0;

        for (/**/; index < spec.length(); index++) {
            char c = spec.charAt(index);
            if (operators.indexOf(c) == -1) {
                break;
            }
            o.append(c);
        }

        v.append(spec.substring(index));
        this.name = name;
        this.op = SelectComparator.operations.get(o.toString());
        this.value = v.toString();
    }
    
    public SelectComparator(String spec) throws IllegalArgumentException {
        StringBuilder n = new StringBuilder();
        StringBuilder o = new StringBuilder();
        StringBuilder v = new StringBuilder();
        
        // collect name
        int index = 0;
        for (/**/; index < spec.length(); index++) {
            char c = spec.charAt(index);
            if (operators.indexOf(c) != -1) {
                break;
            }
            n.append(c);
        }

        for (/**/; index < spec.length(); index++) {
            char c = spec.charAt(index);
            if (operators.indexOf(c) == -1) {
                break;
            }
            o.append(c);
        }

        v.append(spec.substring(index));
        this.name = n.toString();
        this.op = SelectComparator.operations.get(o.toString());
        this.value = v.toString();
    }
    
    public String getName() {
        return this.name;
    }
    
    public boolean match(String other) {
        // Figure out what type the value is: Long, Float, or String
        if (other == null)
            return false;

        try {
            long v1 = new Long(other);
            long v2 = new Long(this.value);
            switch (this.op) {
            case EQUAL:
                return (v2 == v1);
            case NOTEQUAL:
                return (v2 != v1);
            case LESS:
                return (v2 > v1);
            case LESSEQUAL:
                return (v2 >= v1);
            case GREATER:
                return (v2 < v1);
            case GREATEREQUAL:
                return (v2 <= v1);
            }
        } catch (NumberFormatException e) {
            //
        }

        try {
            double v1 = new Double(other);
            double v2 = new Double(this.value);
            switch (this.op) {
            case EQUAL:
                return (v2 == v1);
            case NOTEQUAL:
                return (v2 != v1);
            case LESS:
                return (v2 > v1);
            case LESSEQUAL:
                return (v2 >= v1);
            case GREATER:
                return (v2 < v1);
            case GREATEREQUAL:
                return (v2 <= v1);
            }
        } catch (NumberFormatException e) {
            //
        }

        int comparison = other.compareTo(this.value);

        switch (this.op) {
        case EQUAL:
            return comparison == 0;
        case NOTEQUAL:
            return comparison != 0;
        case LESS:
            return comparison < 0;
        case LESSEQUAL:
            return comparison <= 0;
        case GREATER:
            return comparison > 0;
        case GREATEREQUAL:
            return comparison >= 0;
        }

        return false;
    }
    
    public String toString() {
        return String.format("name='%s' op='%s' value='%s'", this.name, this.op, this.value);
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
//        SelectComparator s = new SelectComparator("name<=def");
        SelectComparator s = new SelectComparator("name", "<=def");
        System.out.printf("%s%n", s);
        
        // Apply the SelectComparator to a fixed value and return true if the comparator's condition is true.
        boolean matched = s.match("abc");
        System.out.printf("%s versus %s = %b%n", s, "abc", matched);
        matched = s.match("def");
        System.out.printf("%s versus %s = %b%n", s, "def", matched);
        matched = s.match("efg");
        System.out.printf("%s versus %s = %b%n", s, "efg", matched);
    }
}
