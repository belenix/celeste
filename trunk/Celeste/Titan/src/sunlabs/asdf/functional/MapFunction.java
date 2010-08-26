package sunlabs.asdf.functional;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * A functional programming Map (apply-to-all) operation.
 * <p>
 * A Map operation consists of applying a function <i>F</i> to a set of input values of Java type
 * {@code I} and producing a new set of output values of Java type {@code O}.
 * </p>
 * <p>
 * The order in which the function is applied is not defined, and may be applied in parallel.
 * The order of the output values correspond to the order of the input values.
 * </p>
 * <p>
 * The function <i>F</i> is implemented in classes that implementing the
 * {@link MapFunction.Function} interface. In any given {@code MapFunction operation},
 * individual {@code MapFunction.Function} instances are created for each item in the set of input values.
 * </p>
 *
 * @see AbstractMapFunction
 *
 * @author Glenn Scott - Sun Microsystems Laboratories
 *
 * @param <I> the type of the input values
 * @param <O> the type of the output values
 */
public interface MapFunction<I,O>  {

    /**
     * A simple container class that contains a value of the Java type {@code O} or an {@link Exception}.
     * If the container contains an {@code Exception} it is thrown when invoking the {@link #get()} method.
     * Instances of this class are returned as the result of each application of the map function to an item.
     *
     * @see AbstractMapFunction.Result
     * @see #setStopOnException(boolean)
     *
     * @param <O> the Java type of the contained value.
     */
    public interface Result<O> {
        /**
         * Get the value of the contained instance.
         *
         * @throws ExecutionException if the Function applied to the corresponding item threw an {@code ExecutionException}.
         * @throws InterruptedException if the Function applied to the corresponding item threw an {@code InterruptedException}.
         */
        public O get() throws ExecutionException, InterruptedException;
    }

    /**
     * Classes implementing this interface perform the per-item processing in a map operation.
     * @param <I>
     * @param <O>
     */
    public interface Function<I,O> extends Callable<O> {
        public void setCountDownLatch(CountDownLatch countDown);

        public O function(I item) throws Exception;
    }

    /**
     * If set to {@code true}, an {@link ExecutionException} thrown by the mapped operation when applied to
     * the sequence given to the {@link #map(Collection)} method will immediately be thrown, preempting further processing,
     * rather than capturing the {@code Exception} in the returned {@code List<Result>} from {@code #map(Collection)}.
     * <p>
     * If set to {@code false}, an {@link ExecutionException} thrown by the mapped operation when applied to
     * the sequence given to the {@link #map(Collection)} method will capture the {@code Exception} in the returned
     * {@code List<Result>} from {@code #map(Collection)}.
     * </p>
     * @param value true if {@code ExecutionException} is to be thrown immediately if the map function throws an Exception.
     */
    public void setStopOnException(boolean value);

    /**
     * Get the value of the <em>stop-on-exception</em> flag. 
     * See {@link #setStopOnException(boolean)}
     */
    public boolean getStopOnException();

    /**
     * Set the {@link ExecutorService} to use for processing any subsequent
     * invocation of {@link #map(Collection)}.
     *
     * @param executor
     */
    public void setExecutorService(ExecutorService executor);

    /**
     * Applies this classes implementation of {@link MapFunction.Function} to each item in the given {@link Collection}
     * returning a corresponding sequence of results. The order in which the function is applied to the {@code Collection}
     * is not defined and the caller should not rely on any particular order even if one is observed empirically.
     * @param sequence
     * @throws ExecutionException if the {@code stopOnException} flag is set
     * (see {@link #setStopOnException(boolean)} and the the invocation of {@link Function#function(Object)} throws an exception.
     */
    public List<Result<O>> map(Collection<I> sequence) throws ExecutionException;

    /**
     * Create an instance of a class implementing the {@link MapFunction.Function} interface.
     * The new instance will be used to perform the application of the function to apply to {@code item}.
     *
     * @param item
     * @return a new instance of a class implementing {@link MapFunction.Function}.
     */
    public Function<I,O> newFunction(I item);
}
