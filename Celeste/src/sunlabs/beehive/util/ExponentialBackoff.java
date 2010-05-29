/*
 * Copyright 2007-2008 Sun Microsystems, Inc. All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 16 Network Circle, Menlo
 * Park, CA 94025 or visit www.sun.com if you need additional
 * information or have any questions.
 */
package sunlabs.beehive.util;

import java.util.Random;

/**
 * The {@code ExponentialBackoff} class provides a means for threads to delay
 * themselves with exponentially increasing backoffs.
 */
public class ExponentialBackoff {
    private final long initialMaxWaitMillis;
    private final long cappedMaxWaitMillis;

    private long nextMaxWaitMillis;

    private Random random = new Random();

    /**
     * Create a new {@code ExponentialBackoff} instance with the given initial
     * and maximum delays.
     *
     * @param initialMaxWaitMillis  the upper bound on the delay that the
     *                              first call to {@code backOff()} will
     *                              impose
     * @param cappedMaxWaitMillis   the upper bound on the delay that a call
     *                              to {@code backOff()} might ever impose
     */
    public ExponentialBackoff(long initialMaxWaitMillis,
            long cappedMaxWaitMillis) {
        if (initialMaxWaitMillis < 0 || cappedMaxWaitMillis < 0)
            throw new IllegalArgumentException(
                "wait times must be non-negative");
        if (cappedMaxWaitMillis < initialMaxWaitMillis)
            throw new IllegalArgumentException(
                "initial wait time cannot exceed capped wait time");

        this.initialMaxWaitMillis = initialMaxWaitMillis;
        this.cappedMaxWaitMillis = cappedMaxWaitMillis;

        this.nextMaxWaitMillis = initialMaxWaitMillis;
    }

    /**
     * Return the upper bound of the maximum delay that the first call to
     * {@link #backOff() backOff()} will impose.
     *
     * @return  the upper bound of the delay that the first call to {@code
     *          backOff()} will impose
     */
    public long getInitialMaxWaitMillis() {
        return this.initialMaxWaitMillis;
    }

    /**
     * Return the upper bound of the maximum delay that a call to {@link
     * #backOff() backOff()} will ever impose.
     *
     * @return  the upper bound of the delay that a call to {@code backOff()}
     *          might ever impose
     */
    public long getCappedMaxWaitMillis() {
        return this.cappedMaxWaitMillis;
    }

    /**
     * Return the upper bound (expressed in milliseconds) of the delay that
     * the next call to {@link #backOff() backOff()} will impose.
     *
     * @return  the upper bound of the delay to be imposed by the next call to
     *          {@code backOff()}
     */
    public long getNextMaxWaitMillis() {
        return this.nextMaxWaitMillis;
    }

    /**
     * <p>
     * 
     * Delay this thread by a random amount of time uniformly distributed
     * between zero (inclusive) and the current backoff bound (exclusive) and
     * then double the backoff bound, subject to its maximum capping value.
     *
     * </p><p>
     *
     * It is possible that an interruption could occur that shortens the delay
     * from its expected value.
     *
     * </p>
     */
    public void backOff() {
        try {
            //
            // There's no method that returns a uniformly distributed long
            // value in the range [0..n), so implement it here...
            //
            long backoffTime =
                (long)(this.random.nextDouble() * this.nextMaxWaitMillis);
            Thread.sleep(backoffTime);
        } catch (InterruptedException e) {
            //
            // Ignore.  A short sleep is acceptable for this method's purpose.
            //
        }
        this.nextMaxWaitMillis *= 2;
        if (this.nextMaxWaitMillis < 0 ||
                this.nextMaxWaitMillis > this.cappedMaxWaitMillis)
            this.nextMaxWaitMillis = this.cappedMaxWaitMillis;
    }
    
    public void backOff(long timeOffset) {
        long backoffTime = this.nextMaxWaitMillis - timeOffset;
        if (backoffTime > 0) {
            try {
                //
                // There's no method that returns a uniformly distributed long
                // value in the range [0..n), so implement it here...
                //

                Thread thisThread = Thread.currentThread();
                synchronized (thisThread) {
                    thisThread.wait(backoffTime);
                }
            } catch (InterruptedException e) {
                //
                // Ignore.  A short sleep is acceptable for this method's purpose.
                //
            }
        }
        this.nextMaxWaitMillis *= 2;
        if (this.nextMaxWaitMillis < 0 ||
                this.nextMaxWaitMillis > this.cappedMaxWaitMillis)
            this.nextMaxWaitMillis = this.cappedMaxWaitMillis;
    }

    /**
     * Reset this {@code ExponentialBackoff} instance to its initial state.
     */
    public void reset() {
        this.nextMaxWaitMillis = initialMaxWaitMillis;
    }
}
