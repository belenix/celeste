package sunlabs.asdf.functional;

import java.util.Collection;

/**
 * Extendable class for implementations of the functional programming Reduce operation.
 *
 * @param <I> The Java type of items in the input {@link Collection}
 * @param <O> The Java type of the output result.
 *
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
abstract public class AbstractReduceFunction<I,O> {
    protected O accumulator;

    public AbstractReduceFunction() {
        this.accumulator = null;
    }

    /**
     * Perform the reduction by iterating through the given {@link Collection}, invoking the method
     * {@link #function(Object, Object)} for each element in the {@code Collection}.
     *
     * @param items the input {@link Collection} of items to apply the reduce operation.
     * @param accumulator the initial and cummulative result of the operation.
     * @return the result of reducing the input {@code Collection} to the {@code accumulator}.
     * @throws Exception if the underlying reduce function {@link #function(Object, Object)} throws an Exception
     */
    public O reduce(Collection<I> items, O accumulator) throws Exception {

        this.accumulator = accumulator;

        // Iterate through the input Collection, invoking {@code function(O accumulator, I item).
        // Each invocation of {@code function} returns a new value for accumulator.
        for (I item : items) {
            this.accumulator = this.function(this.accumulator, item);
        }

        return this.accumulator;
    }

    /**
     * Apply the function to the given item.
     * Using the given value of {@code accumulator} produce a new value for the {@code accumulator} and return it.
     *
     * @param accumulator The current value of the accumulator.
     * @param item The item to apply the function.
     * @return The new value of the accumulator.
     */
    abstract public O function(O accumulator, I item) throws Exception;
}
