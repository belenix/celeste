package sunlabs.titan.node;

/**
 * The global object pool as the aggregate set of node {@link BeehiveObjectStore} instances.
 *
 */
public class BeehiveObjectPool {
    /**
     * {@code BeehiveObjectPool} Exceptions are related to topics involving the whole of the object pool.
     * For those Exceptions related to individual node object stores, see {@link BeehiveObjectStore.Exception} et alia).
     *
     */
    abstract public static class Exception extends java.lang.Exception {
        private static final long serialVersionUID = 1L;

        public Exception() {
            super();
        }
        
        public Exception(Throwable cause) {
            super(cause);
        }
        
        public Exception(String message) {
            super(message);
        }
        
        public Exception(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class DisallowedDuplicateException extends BeehiveObjectPool.Exception {
        private static final long serialVersionUID = 1L;

        public DisallowedDuplicateException() {
            super();
        }
        
        public DisallowedDuplicateException(Throwable cause) {
            super(cause);
        }
        
        public DisallowedDuplicateException(String message) {
            super(message);
        }
        
        public DisallowedDuplicateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
