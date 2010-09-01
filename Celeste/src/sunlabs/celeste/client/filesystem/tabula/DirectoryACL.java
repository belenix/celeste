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

import java.util.Set;

import sunlabs.celeste.util.ACL;
import sunlabs.titan.api.TitanGuid;

//
// XXX: Should this class exist independently or should it be folded into
//      CelesteACL (by merging DirectoryOps with CelesteOps)?  Merging would
//      simplify interfaces and implementations, but at the cost of losing
//      some of the distinction between directory operations and file
//      operations.
//
public class DirectoryACL extends ACL<DirectoryACL.DirectoryOps, TitanGuid> {
    private static final long serialVersionUID = 0L;

    /**
     * An enumeration corresponding to the set of operations invokable through
     * the {@code Directory class}, abstracted to remove overloadings.  The
     * members of this enumeration are the privileges that a {@code
     * DirectoryACL} manages.
     */
    //
    // XXX: Needs a setacl permission (and maybe getacl as well).
    //
    public enum DirectoryOps {
        enumerate,
        link,
        lookup,
        unlink
    }

    /**
     * The {@code DirectoryACE} class specializes general access control
     * entries (as defined by {@link sunlabs.celeste.util.ACL.ACE ACE} to the
     * specifics of file system directories and operations on them.
     */
    public static class DirectoryACE extends
            ACL.ACE<DirectoryOps, TitanGuid> {
        private static final long serialVersionUID = 1L;

        public DirectoryACE(ACL.PrincipalMatcher<TitanGuid> matcher, Set<DirectoryOps> privileges, ACL.Disposition disposition) {
            super(matcher, privileges, disposition);
        }
    }

    /**
     * Assembles the given access control entries into a {@code DirectoryACL}.
     *
     * @param aceList   a list of {@code CelesteACE}s
     */
    public DirectoryACL(DirectoryACE... aceList) {
        super(aceList);
    }
}
