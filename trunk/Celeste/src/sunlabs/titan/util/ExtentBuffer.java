/*
 * Copyright 2007-2009 Sun Microsystems, Inc. All Rights Reserved.
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
package sunlabs.titan.util;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Comparator;

/**
 * <p>
 *
 * The {@code ExtentBuffer} class pairs together a {@code ByteBuffer} holding
 * data from an extent overlaid on an underlying sequentially indexable entity
 * with an offset that denotes the starting position within the sequence of
 * that data.  That is, position 0 of {@code getByteBuffer()} corresponds to
 * position {@code getStartOffset()} within the sequence.
 *
 * </p><p>
 *
 * The class implements the {@code Extent} interface, specializing it to
 * describe sequences of bytes.
 *
 * </p><p>
 *
 * The class provides convenience factory methods corresponding to ones in
 * {@link java.nio.ByteBuffer ByteBuffer} for creating new instances.  It also
 * provides analogs of many of {@code ByteBuffer}'s instance methods, with a
 * given analog method being specified as invoking the corresponding method on
 * this {@code ExtentBuffer}'s {@code ByteBuffer} component.
 *
 * </p><p>
 *
 * Instances of the class are intended to convey data between clients at
 * different levels of abstraction.  In a typical use, one software layer
 * would create an instance, populate it with data, and then pass a read-only
 * version of it to a layer that consumes the data.  The step of converting to
 * a read-only view allows the first layer to avoid copying the data before
 * handing it to the consumer layer.
 *
 * </p><p>
 *
 * Instances of the class are ordered by their starting offsets.  This
 * relationship allows a sequence's data to be captured in a sorted set or map
 * of extents, so that the data at a given offset can be located efficiently.
 *
 * </p><p>
 *
 * Note:  This class has a natural ordering that is inconsistent with equals.
 * However, such inconsistencies manifest themselves only when extents
 * overlap, in particular when they have common starting offests.  Avoiding
 * such usage avoids the inconsistency with equals.  For situations where a
 * consistent ordering is required, the class supplies a comparator with the
 * requisite properties via the {@link #getExtentBufferComparator()} method.
 *
 * </p><p>
 *
 * TODO:  This class is intended to parallel {@link java.nio.ByteBuffer} in
 * the methods it offers.  However, some methods are still missing, and many
 * methods still don't have javadoc descriptions.  The omissions should be
 * rectified.  Until then, it's safe to consult the corresponding {@code
 * ByteBuffer} method documentation for any method whose documentation is
 * missing here.
 *
 * </p>
 */
//
// XXX: Write more Junit tests for this class.  Pay particular attention to
//      its behavior for extent buffers whose position() is non-zero.  It is
//      _not_ valid to assume that that position corresponds to position
//      getStartOffset() in the underlying entity that the extent buffer is
//      buffering.  In this scenario, there are two kinds of possible errors
//      to test for:  the position()/getStartOffset() confusion mentioned
//      above; and invalid contents in buffer slots preceding position().
//      (This may be more of an issue for clients of this class than for the
//      class itself, although the intersect() method is certainly suspect.)
//
public class ExtentBuffer implements
        BufferableExtent, /*Streamable,*/ Serializable {
    private final static long   serialVersionUID = 2L;
    private final long          startOffset;

    //
    // Since ByteBuffer isn't itself Serializable (probably because of the
    // complications induced by the asReadOnlyBuffer() method), it requires
    // special handling.  We mark it as transient here and handle mashalling
    // and unmarshalling it in the writeObject() and readObject() methods.
    //
    // XXX: It ought to be final as well, but that doesn't seem to be
    //      possible, at least not without lots of fooling around.
    //
    private transient ByteBuffer    buffer;

    //
    // The comparator returned by getExtentBufferComparator().
    //
    private final static Comparator<ExtentBuffer> extentBufferComparator =
        new Comparator<ExtentBuffer>() {
            public int compare(ExtentBuffer ebl, ExtentBuffer ebr) {
                int comparison = ebl.compareTo(ebr);
                if (comparison != 0)
                    return comparison;
                ByteBuffer bl = ebl.getByteBuffer().duplicate();
                ByteBuffer br = ebr.getByteBuffer().duplicate();
                int blCapacity = bl.capacity();
                int brCapacity = br.capacity();
                bl.position(0).limit(blCapacity);
                br.position(0).limit(brCapacity);
                return bl.compareTo(br);
            }
        };

    /**
     * Create a new {@code ExtentBuffer} that pairs the given {@code
     * startOffset} with a buffer for data starting at that position in some
     * (not explicitly named) underlying byte sequence.
     *
     * @param startOffset   the starting offset of the extent with respect to
     *                      its underlying sequence
     * @param buffer        a buffer for data starting at {@code startOffset}
     */
    public ExtentBuffer(long startOffset, ByteBuffer buffer) {
        this.startOffset = startOffset;
        this.buffer = buffer;
        checkConstraints();
    }

    //
    // Factories
    //
    // XXX: Might want to add more, corresponding to additional ByteBuffer
    //      factory methods.
    //

    /**
     * Create a new {@code ExtentBuffer} for an extent starting at {@code
     * startOffset}, setting its byte buffer component to the result of
     * invoking {@link java.nio.ByteBuffer#allocate(int)
     * ByteBuffer.allocate(capacity)}.
     *
     * @param startOffset   the starting offset of the extent with respect to
     *                      its underlying sequence
     * @param capacity      the capacity, in bytes, of the new extent buffer's
     *                      byte buffer component
     *
     * @return  a buffer for data starting at {@code startOffset} with the
     *          given capacity
     *
     * @throws IllegalArgumentException
     *      if {@code capacity} is a negative integer
     */
    public static ExtentBuffer allocate(long startOffset, int capacity) {
        return new ExtentBuffer(startOffset, ByteBuffer.allocate(capacity));
    }

    /**
     * Create a new {@code ExtentBuffer} for an extent starting at {@code
     * startOffset} holding the data in {@code array}.
     *
     * @param startOffset   the beginning position of the extent within its
     *                      underlying byte sequence
     * @param array         a byte array holding the extent's content
     *
     * @return  an encapsulation of the given extent
     *
     * @see java.nio.ByteBuffer#wrap(byte[])
     */
    public static ExtentBuffer wrap(long startOffset, byte[] array) {
        return wrap(startOffset, array, 0, array.length);
    }

    /**
     * <p>
     *
     * Create a new {@code ExtentBuffer} for an extent starting at {@code
     * startOffset} holding the data in the subarray of {@code array}
     * designated by {@code bufferOffset} and {@code length}.
     *
     * </p><p>
     *
     * Note that {@code startOffset} will become the offset of position {@code
     * 0} of {@code array}, and that the resulting extent buffer's
     * position will be {@code bufferOffset} (and therefore will be at offset
     * {@code startOffset + bufferOffset} within the extent's underlying byte
     * sequence).
     *
     * </p>
     *
     * @param startOffset   the beginning offset of {@code array} within its
     *                      underlying byte sequence (which potentially may
     *                      extend beyond {@code array}'s bounds on either
     *                      end)
     * @param array         a byte array holding the extent's content
     * @param bufferOffset  the starting position within {@code array} of the
     *                      extent's content
     * @param length        the length within {@code array} of the extent's
     *                      content
     *
     * @return  an encapsulation of the given extent
     *
     * @see java.nio.ByteBuffer#wrap(byte[], int, int)
     */
    public static ExtentBuffer wrap(long startOffset, byte[] array,
            int bufferOffset, int length) {
        return new ExtentBuffer(startOffset,
            ByteBuffer.wrap(array, bufferOffset, length));
    }

    /**
     * Returns a new extent buffer whose bounds are the intersection of this
     * extent buffer and the given {@code extent}.  The new extent buffer's
     * {@code ByteBuffer} component is a slice of the original's {@code
     * ByteBuffer} component, aligned such that elements at equal offsets (as
     * opposed to positions) have equal values.  Its position and limit values
     * correspond to those of the original, except that they are adjusted as
     * necessary to keep them within the bounds determined by the
     * intersection.  Throws {@code IllegalArgumentException} if the bounds
     * are disjoint.
     *
     * @param extent    the extent against which bounds are to be intersected
     *
     * @return  a new extent buffer holding data for the intersection of this
     *          buffer's extent with the one supplied as argument
     *
     * @throws IllegalArgumentException
     *      if the two extents are disjount
     */
    public ExtentBuffer intersect(Extent extent) {
        //
        // Obtain the overall bounds of the resulting extent (while checking
        // for disjointness as well).  Get it as a BufferableExtent, so that
        // the length is directly available.
        //
        BufferableExtent resultingBounds =
            new BufferableExtentImpl(this.intersection(extent));

        //
        // Capture slice-invariant representations of the original buffer's
        // position and limit values.
        //
        long offsetForOriginalPosition = this.positionToOffset(this.position());
        long offsetForOriginalLimit = this.positionToOffset(this.limit());

        //
        // Adjust the buffer.  This happens in two steps.  First, the buffer's
        // overall bounds must be set to match those of resultingBounds.  We
        // do this by setting suitable position and limit values and then
        // slicing.
        //
        // Second, the sliced buffer's position and limit value must be set so
        // that they correspond to the same offsets as did the original
        // values, unless one or both of the original values would now be out
        // of range.  If so, the offending value is set to 0 or to the sliced
        // buffer's capacity, as appropriate.
        //
        long thisStart = this.getStartOffset();
        long resultStart = resultingBounds.getStartOffset();
        int startDelta = ((int)(resultStart - thisStart));
        ByteBuffer buffer = this.getByteBuffer().duplicate();
        buffer.position(startDelta).
            limit(startDelta + resultingBounds.getLength());
        buffer = buffer.slice();
        assert buffer.capacity() == resultingBounds.getLength();

        ExtentBuffer result = new ExtentBuffer(resultStart, buffer);

        if (offsetForOriginalPosition < resultStart)
            offsetForOriginalPosition = resultStart;
        result.position(result.offsetToPosition(offsetForOriginalPosition));

        int size = result.capacity();
        if (offsetForOriginalLimit > resultStart + size)
            result.limit(size);
        else
            result.limit(result.offsetToPosition(offsetForOriginalLimit));

        return result;
    }

    /**
     * Return the offset in the underlying byte sequence corresponding to
     * offset {@code 0} of this buffer.
     *
     * @return  the offset in the underlying sequence of offset {@code 0} of
     *          this buffer
     */
    public long getStartOffset() {
        return this.startOffset;
    }

    /**
     * Return the offset in the underlying byte sequence corresponding to the
     * first position beyond the end of this extent.
     *
     * @return  the offset corresponding to the first position beyond the end
     *          of this buffer
     */
    public long getEndOffset() {
        return this.getStartOffset() + this.capacity();
    }

    /**
     * Return the offset corresponding to the given position within this
     * extent buffer.
     *
     * @return {@code pos}'s offset
     *
     * @throws IndexOutOfBoundsException
     *      if {@code pos} is negative or falls outside of this extent
     *      buffer's capacity
     */
    public long positionToOffset(int pos) {
        if (pos < 0 || pos > this.capacity())
            throw new IndexOutOfBoundsException(String.format(
                "%d out of range", pos));
        return pos + this.getStartOffset();
    }

    /**
     * Return the position within this extent buffer corresponding to the
     * given offset.
     *
     * @return {@code offset}'s position
     *
     * @throws IndexOutOfBoundsException
     *      if {@code offset} is outside of the range given by {@code
     *      getStartOffset()} and {@code getEndOffset()}
     */
    public int offsetToPosition(long offset) {
        //
        // Do validity checking.  There is only one possible situation where
        // offset < getStartOffset() is valid:  when it coincides with
        // getEndOffset() (and both equal Long.MIN_VALUE, denoting the
        // maximally extending extent -- but we don't need an explict check
        // for that value.)
        //
        // Thus, the first clause of the test below checks for being out of
        // range to the left of the extent and the second for being out of
        // range to the right.
        //
        long so = this.getStartOffset();
        long eo = this.getEndOffset();
        if ((offset < so && offset != eo) || offset > eo)
            throw new IndexOutOfBoundsException(String.format(
                "%d out of range", offset));

        return (int)(offset - so);
    }

    public boolean intersects(Extent other) {
        //
        // Use ExtentImpl's implementation of this method.
        //
        ExtentImpl me = new ExtentImpl(this);
        return me.intersects(other);
    }

    public boolean contains(Extent other) {
        //
        // Use ExtentImpl's implementation of this method.
        //
        ExtentImpl me = new ExtentImpl(this);
        return me.contains(other);
    }

    public boolean isVacuous() {
        return this.capacity() == 0;
    }

    public Extent intersection(Extent other) {
        //
        // In contrast to intersect(), return a (simple) extent.  Use the
        // ExtentImpl implementation to do so.
        //
        return new ExtentImpl(this).intersection(other);
    }

    /**
     * <p>
     *
     * Return the length of this {@code ExtentBuffer}.
     *
     * </p><p>
     *
     * As a given {@code ExtentBuffer}'s ending offset is determined by the
     * size of its internal {@code ByteBuffer} component, the notion of length
     * for extent buffers coincides with that of {@link #capacity()}.
     *
     * @return  the length of this extent buffer, that is, its capacity
     */
    public int getLength() {
        return this.capacity();
    }

    /**
     * Return the {@code ByteBuffer} holding data for this extent.
     *
     * @return  the data buffer for this extent
     */
    public ByteBuffer getByteBuffer() {
        return this.buffer;
    }

    /**
     * Determine the relative ordering of two {@code Extent}s.  The comparison
     * considers only their startOffset attributes and is thus inconsistent
     * with equals.  However, restricting the comparands to non-overlapping
     * extents suffices to avoid the inconsistency.
     *
     * @inheritDoc
     */
    public int compareTo(Extent other) {
        long myValue = this.getStartOffset();
        long otherValue = other.getStartOffset();
        return myValue < otherValue ? -1 :
            myValue == otherValue ? 0 : 1;
    }

    /**
     * <p>
     *
     * Returns a {@code Comparator} that compares extent buffers by taking
     * both bounds and buffer contents into account, thus yielding a total
     * ordering on {@code ExtentBuffer} objects that is consistent with
     * equals.
     *
     * </p><p>
     *
     * The comparison first considers the {@code startOffset}
     * attributes.  If these are equal, it then performs a lexicographic
     * comparison of the {@code ByteBuffer} components of the comparands (and
     * thereby implicitly takes the {@code endOffset} attribute into account).
     *
     * </p><p>
     *
     * The lexicographic comparison operates over the entire range of the byte
     * buffers, from position {@code 0} to {@code capacity() - 1}.  If a
     * comparison is desired only over the range of nominally valid bytes
     * (from {@code position()} to {@code limit() - 1}), the comparands should
     * first be {@link #slice() sliced} accordingly.
     *
     * </p>
     *
     * @return  a comparator that imposes a total ordering on extent buffers
     *          that is consistent with equals
     */
    public static Comparator<ExtentBuffer> getExtentBufferComparator() {
        return extentBufferComparator;
    }

    /**
     * <p>
     *
     * Return a string representation of this extent buffer's bounds and
     * contents.
     *
     * </p><p>
     *
     * This method is useful only when the buffer is known to be modest in
     * size and to have string-representable contents.  It makes no attempt to
     * convert bytes into a viewable representation and thus is primarily
     * intended to be used for debugging.
     *
     * </p>
     *
     * @return  a string representation of the buffer's bounds and contents
     */
    public String asString() {
        return this.asString(true);
    }

    /**
     * <p>
     *
     * Return a string representation of this extent buffer's bounds.  If
     * {@code includeContents} is {@code true} include the buffer's contents
     * in the returned string.
     *
     * </p><p>
     *
     * When {@code includeContents} is set, this method is useful only when
     * the buffer is known to be modest in size and to have
     * string-representable contents.  It makes no attempt to convert bytes
     * into a viewable representation and thus is primarily intended to be
     * used for debugging.
     *
     * </p>
     *
     * @param includeContents   if {@code true} include the buffer's contents
     *                          in the returned string; otherwise, return a
     *                          string representation of only the buffer's
     *                          bounds
     *
     * @return  a string representation of the buffer's bounds and contents
     */
    public String asString(boolean includeContents) {
        long eo = this.getEndOffset();
        String eoAsString = (eo == Long.MIN_VALUE) ?
            "infinity" : Long.toString(eo);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%d, %s)",
            this.getStartOffset(), eoAsString));
        if (includeContents) {
            sb.append(" \"");
            for (int i = this.position(); i < this.limit(); i++) {
                sb.append(String.format("%c", this.get(i))); // XXX this throws a format exception
            }
            sb.append("\"");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        //
        // Since the buffer can be large, we content ourselves with reporting
        // bounds only.
        //
        return String.format("ExtentBuffer:%s", this.asString(false));
    }

    //
    // Instance methods mirroring ones in ByteBuffer and its Buffer
    // superclass.
    //
    // XXX: Not a complete set, but probably should be.
    // XXX: Need to add javadoc headers for many of the methods.
    //

    /**
     * <p>
     *
     * Returns the byte array that backs this buffer's byte buffer
     * <em>(optional operation)</em>.
     *
     * </p><p>
     *
     * Modifications to this extent buffer's byte buffer's content will cause
     * the returned array's content to be modified, and vice versa.
     *
     * </p><p>
     *
     * Invoke the {@link #hasArray()} method before invoking this method to
     * ensure that this buffer has an accessible backing array.
     *
     * </p>
     *
     * @return  the byte array that backs this extent buffer's byte buffer
     *
     * @throws ReadOnlyBufferException
     *      if this buffer's byte buffer is backed by an array, but is
     *      read-only
     * @throws UnsupportedOperationException
     *      if this buffer's byte buffer is not backed by an accessible array
     */
    public final byte[] array() {
        return this.getByteBuffer().array();
    }

    /**
     * <p>
     *
     * Returns the offset within this extent buffer's byte buffer component's
     * backing array of the first element of the byte buffer <em>(optional
     * operation)</em>.
     *
     * </p><p>
     *
     * If this buffer's byte buffer is backed by an array, then buffer
     * position {@code p} corresponds to array index {@code p +
     * arrayOffset()}.
     *
     * </p><p>
     *
     * Invoke the {@link #hasArray()} method before invoking this method to
     * ensure that this buffer has an accessible backing array.
     *
     * </p>
     **/
    public final int arrayOffset() {
        return this.getByteBuffer().arrayOffset();
    }

    /**
     * <p>
     *
     * Creates a new {@code ExtentBuffer} that duplicates this one, except
     * that its content is a read-only view of this buffer's content.
     *
     * </p><p>
     *
     * The data buffers of the existing and new {@code ExtentBuffer}s are
     * related as described in {@code ByteBuffer}'s {@link
     * java.nio.ByteBuffer#asReadOnlyBuffer() asReadOnlyBuffer()} method
     * documentation.
     *
     * </p>
     *
     * @return  a new extent buffer with a read-only data buffer view of this
     *          extent buffer's content
     */
    public ExtentBuffer asReadOnlyBuffer() {
        return new ExtentBuffer(this.getStartOffset(),
            this.getByteBuffer().asReadOnlyBuffer());
    }

    /**
     * Returns the capacity of this extent buffer's byte buffer component.
     *
     * @return  the capacity of this extent buffer's byte buffer
     */
    public final int capacity() {
        return this.getByteBuffer().capacity();
    }

    public ExtentBuffer duplicate() {
        return new ExtentBuffer(this.getStartOffset(),
            this.getByteBuffer().duplicate());
    }

    public final ExtentBuffer flip() {
        this.getByteBuffer().flip();
        return this;
    }

    public byte get() {
        return this.getByteBuffer().get();
    }

    public ExtentBuffer get(byte[] dst) {
        this.getByteBuffer().get(dst);
        return this;
    }

    public ExtentBuffer get(byte[] dst, int offset, int length) {
        this.getByteBuffer().get(dst, offset, length);
        return this;
    }

    public byte get(int i) {
        return this.getByteBuffer().get(i);
    }

    /**
     * <p>
     *
     * Tells whether or not this extent buffer's byte buffer component is
     * backed by an accessible byte array.
     *
     * </p><p>
     *
     * If this method returns {@code true} then the {@link #array()} and {@link
     * #arrayOffset()} methods may be safely invoked.
     *
     * </p>
     *
     * @return  {@code true} if, and only if, this extent buffer's byte buffer
     *          is backed by an array ans is no read-only
     */
    public final boolean hasArray() {
        return this.getByteBuffer().hasArray();
    }

    /**
     * Returns the limit of this extent buffer's byte buffer component.
     *
     * @return  the limit of this extent buffer's byte buffer
     */
    public final int limit() {
        return this.getByteBuffer().limit();
    }

    public final ExtentBuffer limit(int newLimit) {
        this.getByteBuffer().limit(newLimit);
        return this;
    }

    /**
     * Returns the position of this extent buffer's byte buffer component.
     *
     * @return  ths position of this extent buffer's byte buffer
     */
    public final int position() {
        return this.getByteBuffer().position();
    }

    /**
     * Sets this position of this extent buffer's byte buffer component.  If
     * the mark is defined and larger than the new position then it is
     * discarded.
     *
     * @param newPosition   the new position value; must be non-negative and
     *                      no larger than the current limit
     *
     * @return  this extent buffer
     *
     * @throws IllegalArgumentException
     *      If the preconditions on {@code newPosition} do not hold
     */
    public final ExtentBuffer position(int newPosition) {
        this.getByteBuffer().position(newPosition);
        return this;
    }

    public ExtentBuffer put(byte b) {
        this.getByteBuffer().put(b);
        return this;
    }

    public ExtentBuffer put(byte[] src) {
        this.getByteBuffer().put(src);
        return this;
    }

    public ExtentBuffer put(byte[] src, int offset, int length) {
        this.getByteBuffer().put(src, offset, length);
        return this;
    }

    public ExtentBuffer put(int index, byte b) {
        this.getByteBuffer().put(index, b);
        return this;
    }

    /**
     * <p>
     *
     * Relative bulk <em>put</em> operation.
     *
     * </p><p>
     *
     * This method transfers the bytes remaining in the given source buffer
     * into this buffer.  If there are more bytes remaining in the source
     * buffer than in this buffer, that is, if {@code src.remaining() >
     * remaining()}, then no bytes are transferred and a {@link
     * java.nio.BufferOverflowException BufferOverflowException} is thrown.
     *
     * </p><p>
     *
     * Otherwise, this method copies {@code <em>n</em> = src.remaining()}
     * bytes from the given buffer into this buffer, starting at each buffer's
     * current position.  The positions of both buffers are then incremented
     * by <em>n</em>.
     *
     * </p><p>
     *
     * In other words, an invocation of this method of the form {@code
     * dst.put(src)} has exactly the same effect as the loop
     *
     * <pre>
     *     for (int i = off; i < off + len; i++)
     *         dst[i] = src.get();</pre>
     *
     * except that it first checks that there are sufficient bytes in this
     * buffer and it is potentially much more efficient.
     *
     * @param src   the source buffer from which bytes are to be read; must
     *              not be this buffer
     *
     * @return  this extent buffer
     *
     * @throws  BufferOverflowException
     *      if there is insufficient space in this buffer for the remaining
     *      bytes in the source buffer
     * @throws  IllegalArgumentException
     *      if the source buffer is this buffer
     * @throws ReadOnlyBufferException
     *      if this buffer is read-only
     *
     */
    public ExtentBuffer put(ByteBuffer src) {
        this.getByteBuffer().put(src);
        return this;
    }

    /**
     * <p>
     *
     * Relative bulk <em>put</em> operation.
     *
     * </p><p>
     *
     * This method is equivalent to {@link #put(ByteBuffer) put(}{@code
     * src.getByteBuffer())} except that it first verifies that the two
     * buffers are properly aligned, that is that
     *
     * <pre>    this.getStartOffset() + this.position() ==
     *         src.getStartOffset() + src.position()</pre>
     *
     * throwing {@code IllegalStateException} if they are not.
     *
     * @throws IllegalStateException
     *      if the two buffers don't have the same offsets at their current
     *      positions
     *
     * </p>
     */
    public ExtentBuffer put(ExtentBuffer src) {
        if (this.getStartOffset() + this.position() !=
                src.getStartOffset() + src.position()) {
            String msg = String.format(
                "non-aligned buffers [%d+%d] vs [%d+%d]",
                this.getStartOffset(), this.position(),
                src.getStartOffset(), src.position());
            throw new IllegalStateException(msg);
        }

        this.getByteBuffer().put(src.getByteBuffer());
        return this;
    }

    /**
     * Returns the number of elements between the current position and the
     * limit.
     *
     * @return  the number of bytes remaining in this extent buffer
     */
    public final int remaining() {
        return this.getByteBuffer().remaining();
    }

//    /**
//     * <p>
//     *
//     * Create a new extent buffer that starts at {@code newStartOffset} in its
//     * underlying sequence and whose content is a shared subsequence of this
//     * buffer's content.
//     *
//     * </p><p>
//     *
//     * The content of the new buffer will start at this buffer's current
//     * position.  Changes to this buffer's content will be visible in the new
//     * buffer, and vice versa; the two buffers' position, limit, and mark
//     * values will be independent.
//     *
//     * </p><p>
//     *
//     * The new buffer's position will be zero, its capacity and its limit will
//     * be the number of bytes remaining in this buffer, and its mark will be
//     * undefined.  The new buffer will be direct if, and only if, this buffer
//     * is direct, and it will be read-only if, and only if, this buffer is
//     * read-only.
//     *
//     * </p>
//     *
//     * @deprecated  This method allows the new and old extent buffers to
//     *              disagree on the data appearing at a given offset.  If this
//     *              behavior is actually desired, use the {@link
//     *              #ExtentBuffer(long, ByteBuffer) extent buffer constructor}
//     *              to obtain it.  Otherwise, use {@link #slice()}.
//     *
//     * @return  the new extent buffer
//     */
//    @Deprecated
//    public ExtentBuffer slice(long newStartOffset) {
//        return new ExtentBuffer(newStartOffset, this.getByteBuffer().slice());
//    }

    /**
     * <p>
     *
     * Create a new extent buffer whose content is a shared subsequence of
     * this buffer's content.
     *
     * </p><p>
     *
     * The content of the new buffer will start at this buffer's current
     * position.  Changes to this buffer's content will be visible in the new
     * buffer, and vice versa; the two buffers' position, limit, and mark
     * values will be independent.
     *
     * </p><p>
     *
     * The new buffer's position will be zero, its capacity and its limit will
     * be the number of bytes remaining in this buffer, and its mark will be
     * undefined.  The new buffer will be direct if, and only if, this buffer
     * is direct, and it will be read-only if, and only if, this buffer is
     * read-only.
     *
     * </p><p>
     *
     * The new extent buffer's starting position will be that of this extent
     * buffer's, incremented by this buffer's position.  (Thus, the data
     * appearing at a given offset will be identical, regardless of whether
     * obtained from the new extent buffer or this extent buffer.)
     *
     * </p>
     *
     * @return  the new extent buffer
     */
    public ExtentBuffer slice() {
        long newStartOffset = this.getStartOffset() + this.position();
        return new ExtentBuffer(newStartOffset, this.getByteBuffer().slice());
    }

    //
    // Private support methods
    //

    //
    // Verify that the constraints required of classes implementing the Extent
    // and BufferableExtent interfaces hold.
    //
    private void checkConstraints() {
        long start = this.getStartOffset();
        long end = this.getEndOffset();
        if (!((end == Long.MIN_VALUE || end >= start) && start >= 0))
            throw new IllegalArgumentException(
                String.format("end of extent [%d] before its beginning [%d]",
                    end, start));
        if (!(end - start <= Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("extent too long");
        }
    }

    //
    // Serialization support
    //

    private void writeObject(ObjectOutputStream out) throws IOException {
        if (this.position() < 0 || this.position() > this.limit() || this.limit() > this.capacity()) {
            System.err.printf("position=%d limit=%d capacity=%d%n", this.position(), this.limit(), this.capacity());
            new Throwable().printStackTrace();
        }
        //
        // Deal with all non-transient fields.
        //
        out.defaultWriteObject();

        //
        // Serialize the buffer field.  The information required is the
        // individual serializations of:
        //  isReadOnly
        //  capacity
        //  position
        //  limit
        //  a byte array covering the span between position and limit
        //
        out.writeBoolean(this.buffer.isReadOnly());
        out.writeInt(this.capacity());
        out.writeInt(this.position());
        out.writeInt(this.limit());
        //
        // If the buffer doesn't have an accessible backing array, synthesize
        // one.
        //
        if (this.buffer.hasArray()) {
            out.write(this.buffer.array(),
                this.buffer.arrayOffset() + this.buffer.position(),
                this.buffer.remaining());
        } else {
            byte[] xxx = new byte[this.buffer.remaining()];
            this.buffer.duplicate().get(xxx);
            out.write(xxx, 0, xxx.length);
        }
    }

    private void readObject(ObjectInputStream in) throws
            IOException, ClassNotFoundException {
        //
        // Suck in non-transient fields.
        //
        in.defaultReadObject();

        //
        // De-serialize the buffer field.  Start by reading the serialized
        // state and then use it to reconstruct the buffer.  Note that it's
        // possible that this sequence might result in a buffer whose
        // hasArray() method returns true even when the original buffer's
        // didn't.
        //
        boolean isReadOnly = in.readBoolean();
        int capacity = in.readInt();
        int position = in.readInt();
        int limit = in.readInt();

        //if (position < 0 || position > limit || limit > capacity) {
        //    System.err.printf("position=%d limit=%d capacity=%d%n",
        //        this.position(), this.limit(), this.capacity());
        //    new Throwable().printStackTrace();
        //}

        byte[] array = new byte[capacity];
        in.readFully(array, position, limit - position);
        this.buffer = ByteBuffer.wrap(array, position, limit - position);
        if (isReadOnly)
            this.buffer = this.buffer.asReadOnlyBuffer();
    }

    public long streamLength() {
        return this.remaining();
    }

    public long streamTo(WritableByteChannel out) throws IOException {
        return out.write(this.getByteBuffer().duplicate());
    }

    public long streamTo(DataOutputStream out) throws IOException {
        return this.streamTo(Channels.newChannel(out));
    }
}
