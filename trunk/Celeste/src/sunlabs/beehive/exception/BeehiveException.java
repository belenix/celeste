package sunlabs.beehive.exception;

/**
 * A general purpose class expressing Exceptions in Beehive.
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public abstract class BeehiveException extends Exception {
    private final static long serialVersionUID = 1L;

    public BeehiveException() {
        super();
    }

    public BeehiveException(String format, Object...args) {
        super(String.format(format, args));
    }
    
    public BeehiveException(String message) {
        super(message);
    }

    public BeehiveException(Throwable reason) {
        super(reason);
    }
}
