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

import sunlabs.celeste.FileIdentifier;

/**
 * Captures behavior common to all objects that can appear in the name space
 * of a Tabula file system.
 */
//
// XXX: Perhaps this ought to be an interface rather than a class.  Or, if a
//      class, perhaps it should be abstract.
//
// XXX: Consider providing constructors for Occupant and its subclasses that
//      take the corresponding OccupantInfo objects as their argument.
//
public class Occupant implements Serializable {
    private static final long serialVersionUID = 0L;

    //
    // All occupants must have a FileIdentifier.  If a given occupant uses the
    // underlying Celeste file store to hold its state, this identifier is
    // what identifies it to Celeste.
    //
    // XXX: Is it ever legitimate for fid to be null?  (If an occupant is
    //      deleted from the name space, it ought to be removed altogether.
    //      If it should somehow remain, its fid should continue to identify
    //      the corpse.  Are there other possibilities?)
    //
    private final FileIdentifier fid;

    //
    // XXX: This implementation currently does not support serial number
    //      attributes for file system objects.  It that changes, this is
    //      where the serial number belongs (either as a distinct field, or as
    //      part of a map of string-valued attributes).
    //
    //      (This particular attribute is the tip of an iceberg.  The design
    //      still has to accommodate file attributes and properties.  Should
    //      occupants uniformly have them or do they apply only to certain
    //      Occupant subclasses?  Should their permanent representation be
    //      stored in per-occupant Celeste files or would it be better to keep
    //      them in the same Celeste file that holds the FileTreeMap?)
    //

    //
    // XXX: Perhaps this constructor should be protected.
    //
    public Occupant(FileIdentifier fid) {
        this.fid = fid;
    }

    /**
     * Returns this occupant's file identifier
     *
     * @return this occupant's {@code FileIdentifier}
     */
    public FileIdentifier getFileIdentifier() {
        return this.fid;
    }

    @Override
    public String toString() {
        return String.format("Occupant[fid=%s]", this.fid);
    }
}
