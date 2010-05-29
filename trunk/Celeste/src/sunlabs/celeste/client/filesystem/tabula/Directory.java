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

import sunlabs.celeste.FileIdentifier;

/**
 * Implements the public Directory API.
 */
//
// XXX: Should be split into an interface and an implementation class.
//
// XXX: Needs refactoring to provide support for allowing multi-level
//      directories (as in the current Tabula file system implementation) to
//      exist alongside traditional single-level directories.
//
public class Directory extends Occupant {
    private static final long serialVersionUID = 0L;

    private DirectoryACL acl;

    public Directory(FileIdentifier fid, DirectoryACL acl) {
        super(fid);
        this.acl = acl;
    }

    /**
     * Returns this directory's access control list.
     *
     * @return this directory's {@code ACL}
     */
    public DirectoryACL getACL() {
        return this.acl;
    }

    /**
     * Sets this directory's access control list to {@code acl}.
     *
     * @param acl   the new {@code ACL} for this directory.
     */
    //
    // XXX: Need to perform an ACL check before re-setting the ACL.  Where
    //      should this occur:  here or at the call site?  (The call site will
    //      have a PathName available for the exception, whereas getting it
    //      here would require the ability to look up a PathName from an
    //      Occupant.)
    //
    public void setACL(DirectoryACL acl) {
        this.acl = acl;
    }

    @Override
    public String toString() {
        return String.format("Directory[fid=%s, acl=%s]",
            this.getFileIdentifier(), this.getACL());
    }
}
