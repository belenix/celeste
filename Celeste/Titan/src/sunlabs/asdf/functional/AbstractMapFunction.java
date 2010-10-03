package sunlabs.asdf.functional;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * An abstract implementation of the {@link MapFunction} interface.
 * Extensions of this class implement a functional-programming <em>map</em>
 * operation which is subsequently applied to a {@link Collection} of objects.
 *
 * @param <I> The Java class of the elements in the input Collection.
 * @param <O> The Java class of the elements in the output List.
 */
abstract public class AbstractMapFunction<I,O> implements MapFunction<I,O> {
    private static class SimpleThreadFactory implements ThreadFactory {
        private String name;
        private long counter;

        public SimpleThreadFactory(String name) {
            this.name = name;
            this.counter = 0;
        }

        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(String.format("%s-pool-%d", this.name, this.counter));
            this.counter++;
            return thread;
        }
    }

    /**
     * A container intended to hold a value of the Java type {@code T} or
     * an {@link Exception}.
     *
     * @param <T> the Java type of the contained value.
     */
    public static class Result<T> implements MapFunction.Result<T> {
        private T result;
        private Exception exception;

        public Result(T value) {
            this.result = value;
            this.exception = null;
        }

        public Result(Exception e) {
            // This is either ExecutionException or InterruptedException thrown by an invocation of the map function.
            this.exception = e;
            if ((e instanceof ExecutionException) || (e instanceof InterruptedException)) {
                this.exception = e;
            } else {
                throw new IllegalArgumentException(String.format("Must be either java.util.concurrent.ExecutionException or java.lang.InterruptedException.  Got %s", e.toString()));
            }
        }

        public T get() throws ExecutionException, InterruptedException {
            if (this.exception != null) {
                if (this.exception instanceof ExecutionException) {
                    throw (ExecutionException) this.exception;
                }
                if (this.exception instanceof InterruptedException) {
                    throw (InterruptedException) this.exception;
                }
                throw new IllegalStateException(String.format("Result must be either ExecutionException or InterruptedException.  Got %s", this.exception.toString()));
            }
            return this.result;
        }

        @Override
        public String toString() {
            if (this.exception != null)
                return this.exception.toString();
            return this.result.toString();
        }
    }

    private ExecutorService executor;
    private boolean stopOnException;


    /**
     * Initialise a new instance of this class.
     *
     */
    public AbstractMapFunction() {
        this.stopOnException = false;
    }

    /**
     * Initialise a new instance of this class.
     * <p>
     * If non-null, the {@link ExecutorService} {@code executor} is used to
     * apply the {@link #map(Collection)} operation in parallel, using {@code Threads}
     * in the {@code ExecutorService}.  If the value is {@code null} the {@code #map(Collection)} operation
     * is applied linearly.
     * </p>
     */
    public AbstractMapFunction(ExecutorService executor) {
        this.stopOnException = false;
        this.setExecutorService(executor);
    }

    public void setStopOnException(boolean value) {
        this.stopOnException = value;
    }

    public boolean getStopOnException() {
        return this.stopOnException;
    }

    public void setExecutorService(ExecutorService executor) {
        this.executor = executor;
    }

    /**
     * An abstract class implementation of the {@link MapFunction.Function} interface.
     * Extending classes provide the per-item processing in a Map operation.
     */
    abstract public class Function implements MapFunction.Function<I,O> {
        protected CountDownLatch countDown;
        protected I item;

        public Function() {
            this.countDown = null;
        }

        public Function(I o) {
            this();
            this.item = o;
        }

        public Function(CountDownLatch countDown) {
            this.countDown = countDown;
        }

        public Function(I o, CountDownLatch countDown) {
            this(o);
            this.countDown = countDown;
        }

        /**
         * Set the {@link CountDownLatch} for this function.
         * When this instance has completed the invocation of
         * {@code #function(Object)} {@link CountDownLatch#countDown()}
         * (if not {@code null} is invoked.
         */
        public void setCountDownLatch(CountDownLatch countDown) {
            this.countDown = countDown;
        }

        public O call() throws Exception {
            try {
                return this.function(this.item);
            } finally {
                if (this.countDown != null)
                    this.countDown.countDown();
            }
        }

        /**
         * This method implements the function to apply to a input value {@code item} of type {@code I}
         * returning the corresponding mapped value of type {@code O}.
         *
         * @throws Exception under any condition which the implementation must throw an Exception.
         *         The thrown Exception is ultimately encapsulated in a MapFunction.Result instance
         *         and will be re-thrown in an {@link ExecutionException} when the result of this invocation
         *         is retrieved via the {@link MapFunction.Result#get()} method.
         */
        abstract public O function(I item) throws Exception;
    }

    /**
     * Create a {@link AbstractMapFunction.Function} instance that will perform the application of
     * {@link AbstractMapFunction} to the given {@code item}.
     *
     * @param item return a new instance of {@link AbstractMapFunction.Function}
     * which will operate on the given input value {@code item}.
     */
    abstract public AbstractMapFunction<I,O>.Function newFunction(I item);

    /**
     * Functional Map (apply-to-all) operation.
     * <p>
     * Applies the {@link #newFunction} method of the subclass extending this abstract class
     * to each item in the given {@link Collection} returning a corresponding sequence of results.
     * The order in which the function is applied to the {@code Collection} is not defined and the caller should not rely
     * on any particular order even if one is observed empirically.
     * </p>
     *
     * @param sequence A Collection of items, of type <em>I</em> for which each will have this MapFunction's inner function applied.
     */
    public List<MapFunction.Result<O>> map(Collection<I> sequence) throws ExecutionException {
        if (this.executor == null) {
            return this.mapLinearly(sequence);
        }

        LinkedList<FutureTask<O>> tasks = new LinkedList<FutureTask<O>>();

        // Each thread will countDown() on this counter.
        CountDownLatch counterCountdown = new CountDownLatch(sequence.size());

        for (I item : sequence) {
            // Call the MapFunction's method to create a new instance of the function to apply.
            AbstractMapFunction<I,O>.Function func = this.newFunction(item);
            func.setCountDownLatch(counterCountdown);

            FutureTask<O> task = new FutureTask<O>(func);
            tasks.add(task);
            this.executor.execute(task);
        }

        List<MapFunction.Result<O>> result = new LinkedList<MapFunction.Result<O>>();

        // We've applied the function to each of the object in the sequence.
        // Wait for them to complete.
        try {
            counterCountdown.await();

            while (!counterCountdown.await(10000, TimeUnit.MILLISECONDS)) {
                System.err.printf("hung? Count remaining=%d", counterCountdown.getCount());
                for (FutureTask<O> task : tasks) {
                    System.err.printf(" Task %s: done=%b%n", task.toString(), task.isDone());
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (FutureTask<O> task : tasks) {
            try {
                result.add(new Result<O>(task.get()));
            } catch (ExecutionException e) {
                result.add(new Result<O>(e));
            } catch (InterruptedException e) {
                result.add(new Result<O>(e));
            }
        }

        return result;
    }

    private List<MapFunction.Result<O>> mapLinearly(Collection<I> sequence) throws ExecutionException  {
        List<MapFunction.Result<O>> result = new LinkedList<MapFunction.Result<O>>();
        for (I item : sequence) {
            AbstractMapFunction<I,O>.Function func = this.newFunction(item);
            try {
                O value = func.call();
                result.add(new Result<O>(value));
            } catch (Exception e) {
                if (this.stopOnException) {
                    throw new ExecutionException(e);
                }
                result.add(new Result<O>(e));
            }
        }

        return result;
    }

    // --------------------------

    /**
     * An example of a Mappable operation.
     * <p>
     * Define a map function which multiplies each value in a Collection of Integer values by a Double constant.
     * </p>
     * <p>
     * The class extends the {@link AbstractMapFunction} class specialising it to {@code AbstractMapFunction<Integer,Double>}
     * and defines an inner class extending Map<Integer,Double>.Function which is applied to each
     * element in the Collection given to the constructor.
     * </p>
     */
    public static class Multiply extends AbstractMapFunction<Integer,Double> {
        private double multiplier;

        public Multiply(ExecutorService executor, double multiplier) {
            super(executor);
            this.multiplier = multiplier;
        }

        /**
         * Define the function that is applied to each member of the set of items mapped.
         *
         */
        public class Function extends AbstractMapFunction<Integer,Double>.Function {
            public Function(Integer item) {
                super(item);
            }

            @Override
            public Double function(Integer item) throws Exception {
                if (Multiply.this.multiplier == 0.0)
                    throw new Exception("oops");
                return new Double(this.item *Multiply.this.multiplier);
            }
        }

        @Override
        public AbstractMapFunction<Integer,Double>.Function newFunction(Integer item) {
            return this.new Function(item);
        }
    }


    public static class Addition extends AbstractReduceFunction<Double,Long> {
        public Addition() {

        }

        @Override
        public Long function(Long partialResult, Double item) {
            if (partialResult == null) {
                return new Long(item.longValue());
            }
            return partialResult + new Long(item.longValue());
        }
    }

    public static void main(String[] args) throws Exception {

        Collection<Integer> collection = new HashSet<Integer>();
        for (int i = 0; i < 5; i++) {
            collection.add(i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(3, new SimpleThreadFactory("map-function"));
        System.out.printf("executor %s%n", executor);

        MapFunction<Integer,Double> multiply = new Multiply(null, 2.0);
        List<MapFunction.Result<Double>> a = multiply.map(collection);
        System.out.printf("%s%n", a);

//        AbstractReduceFunction<Double,Long> addition = new Addition();
//        Long b = addition.reduce(a, Long.valueOf(0));
//        System.out.printf("%s%n", b);
//
//
//        b = addition.reduce(multiply.map(collection), Long.valueOf(0));
//        executor.shutdown();
//        System.out.printf("%s%n", b);

    }
}
