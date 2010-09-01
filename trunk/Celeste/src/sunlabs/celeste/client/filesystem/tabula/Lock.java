/*
 * Copyright 2009 Sun Microsystems, Inc. All Rights Reserved.
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

package sunlabs.celeste.client.filesystem.tabula;

import java.io.Serializable;

import sunlabs.titan.api.TitanGuid;

/**
 * Information characterizing a lock applied to the occupant of a position in a
 * Tabula file system's name space.
 */
//
// XXX: Should say more about lock semantics here.  In WebDAV terminology, we
//      support only exclusive locks (no shared locks).  Applying a lock to an
//      object restricts the ability to perform modification operations to the
//      holder of the lock (as defined by the contents of the Lock.holder
//      field).
//
//      (But where an how does the token come into play?  To unlock, one must
//      present the proper token.  It also has relevance to determining
//      whether two locks conflict (different tokens conflict), which must be
//      checked when doing rename operations.  Are there other semantics?
//
public class Lock implements Serializable {
    private static final long serialVersionUID = 0L;

    /**
     * The values of this enumeration characterize whether or not a lock
     * applies just to a file or directory or to its descendants in the file
     * system name space as well.
     */
    public enum Depth {
        /**
         * The lock applies only to the file or directory itself, not to
         * any of its descendants in the name space.
         */
        ZERO,

        /**
         * The lock applies not only to the file or directory itself, but also
         * to all of its descendants in the name space.
         */
        INFINITY
    }

    /**
     * The object id of the entity holding the lock
     */
    public final TitanGuid locker;

    /**
     * This lock's depth
     */
    public final Depth depth;

    /**
     * The lock token set with this lock
     */
    public final String token;

    public Lock(TitanGuid locker, Depth depth, String token) {
        if (locker == null || token == null)
            throw new IllegalArgumentException(
            "locker and token arguments both must be non-null");
        this.locker = locker;
        this.depth = depth;
        this.token = token;
    }

    public boolean conflictsWith(Lock other) {
        if (other == null)
            throw new IllegalArgumentException("argument must be non-null");
        //
        // A conflict occurs when the owners differ, the depth differs, or
        // the lock tokens differ.
        //
        if (!this.locker.equals(other.locker))
            return true;
        if (!this.depth.equals(other.depth))
            return true;
        if (!this.token.equals(other.token))
            return true;
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Lock))
            return false;
        Lock other = (Lock)o;
        return this.locker.equals(other.locker) &&
            this.depth.equals(other.depth) &&
            this.token.equals(other.token);
    }

    @Override
    public int hashCode() {
        return this.locker.hashCode() ^ this.depth.ordinal() ^
            this.token.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Lock[depth=%s, locker=%s, token=%s]",
            this.depth, this.locker, this.token);
    }
}
