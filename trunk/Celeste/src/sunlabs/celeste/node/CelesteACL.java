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

package sunlabs.celeste.node;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;


import sunlabs.celeste.node.services.object.VersionObject;
import sunlabs.celeste.util.ACL;
import sunlabs.celeste.util.CelesteEncoderDecoder;
import sunlabs.titan.BeehiveObjectId;

import static sunlabs.celeste.util.ACL.Disposition.grant;

/**
 * {@code CelesteACL} specializes the base {@code ACL} class to use object-ids
 * to name principals and to use the operations defined
 * in the {@code Celeste} interface as its privileges.  It provides nested
 * classes that implement corresponding specializations of the nested classes
 * and interfaces defined in {@code ACL}.
 */
public class CelesteACL extends ACL<CelesteACL.CelesteOps, BeehiveObjectId> {
    private static final long serialVersionUID = 4L;

    /**
     * An enumeration corresponding to the set of operations invokable through
     * the {@code Celeste} interface, abstracted to remove overloadings.  The
     * members of this enumeration are the privileges that a Celeste ACL
     * manages.
     */
    //
    // Abbreviations, in sequence:
    // 	    cdilNrRatuw
    //
    public enum CelesteOps {
        createFile,
        deleteFile,
        inspectFile,
        lockFile,
        newProfile,
        readFile,
        readProfile,
        setACL,
        setFileLength,
        setUserAndGroup,
        writeFile,
    }

    /**
     * {@code FileAttributeAccessor} specializes the {@link
     * sunlabs.celeste.util.ACL.ACLBearerAccessor ACL.ACLBearerAccessor}
     * marker interface to add methods for accessing owner and group
     * principals from files that record principals as object-ids.
     */
    //
    // This interface does _not_ extend Serializable, since objects of classes
    // implementing it are not expected to go across the wire.
    //
    public interface FileAttributeAccessor
            extends ACL.ACLBearerAccessor<BeehiveObjectId> {
        //
        // If matchers for other file attributes are needed, methods for
        // grabbing the attributes go here.
        //
        public BeehiveObjectId getOwner();
        public BeehiveObjectId getGroup();
    }

    /**
     * This class is intended to be used locally at the Celeste level as a
     * source of the {@code FileAttributeAccessor} argument to {@code
     * ACL.PrincipalMatcher#matches()}.
     */
    public static class CelesteFileAttributeAccessor
            implements FileAttributeAccessor {
        private VersionObject.Object vObject;

        public CelesteFileAttributeAccessor(VersionObject.Object vObject) {
            this.vObject = vObject;
        }

        public BeehiveObjectId getOwner() {
            return vObject.getOwner();
        }

        public BeehiveObjectId getGroup() {
            return vObject.getGroup();
        }
    }

    /**
     * An {@code OwnerMatcher} determines whether a given principal is a
     * file's owner.
     */
    public static class OwnerMatcher
            implements ACL.PrincipalMatcher<BeehiveObjectId> {
        private static final long serialVersionUID = 1L;

        /**
         * @param accessor  must be an instance of {@code
         *                  FileAttributeAccessor}
         *
         * @return {@code true} if the principal that {@code
         *          accessor.getOwner()} retrieves is equal to {@code
         *          principal} and {@code false} otherwise
         */
        public boolean matches(BeehiveObjectId principal,
                ACL.ACLBearerAccessor<BeehiveObjectId> accessor) {
            //
            // Sigh... Must resort to a run-time check, since the type system
            // can't express what's needed here.
            //
            if (!(accessor instanceof FileAttributeAccessor))
                throw new IllegalArgumentException(
                    "accessor argument must be a FileAttributeAccessor");
            FileAttributeAccessor attrAccessor =
                (FileAttributeAccessor)accessor;
            BeehiveObjectId owner = attrAccessor.getOwner();
            return owner.equals(principal);
        }

        @Override
        public String toString() {
            return "owner@";
        }
    }

    /**
     * A {@code GroupMatcher} determines whether a given principal is a
     * file's group.
     */
    public static class GroupMatcher
            implements ACL.PrincipalMatcher<BeehiveObjectId> {
        private static final long serialVersionUID = 1L;

        /**
         * @param accessor  must be an instance of {@code
         *                  FileAttributeAccessor}
         *
         * @return {@code true} if the principal that {@code
         *          accessor.getGroup()} retrieves is equal to {@code
         *          principal} and {@code false} otherwise
         */
        public boolean matches(BeehiveObjectId principal,
                ACL.ACLBearerAccessor<BeehiveObjectId> accessor) {
            //
            // Sigh... Must resort to a run-time check, since the type system
            // can't express what's needed here.
            //
            if (!(accessor instanceof FileAttributeAccessor))
                throw new IllegalArgumentException(
                    "accessor argument must be a FileAttributeAccessor");
            FileAttributeAccessor attrAccessor =
                (FileAttributeAccessor)accessor;
            BeehiveObjectId group = attrAccessor.getGroup();
            //
            // XXX: This check isn't right.  We need to see whether principal
            //      is a member of group, not that ut is group.  (But that
            //      requires machinery that's not yet in place.)
            //
            return group.equals(principal);
        }

        @Override
        public String toString() {
            return "group@";
        }
    }

    /**
     * An {@code IndividualMatcher} determines whether the principal whose
     * access is being checked is identical to the principal named as argument
     * to its constructor.
     */
    //
    // XXX: Want some way to have toString() emit the name of the profile that
    //      individualId denotes.  One possibility is to create a subclass
    //      that has a constructor taking a ProfileCache, so that the
    //      toString() method can look up the profile in the cache and then
    //      use its getName() method.
    //
    public static class IndividualMatcher
            implements ACL.PrincipalMatcher<BeehiveObjectId> {
        private static final long serialVersionUID = 1L;

        private final BeehiveObjectId individualId;

        public IndividualMatcher(BeehiveObjectId individualId) {
            if (individualId == null)
                throw new IllegalArgumentException(
                    "individualId must be non-{@code null}");
            this.individualId = individualId;
        }

        public boolean matches(BeehiveObjectId principal,
                ACL.ACLBearerAccessor<BeehiveObjectId> accessor) {
            return this.individualId.equals(principal);
        }

        @Override
        public String toString() {
            return String.format("user=%s", this.individualId);
        }
    }

    //
    // XXX: Need a GroupMembershipMatcher to go along with the
    //      IndividualMatcher class above.
    //

    /**
     * An {@code AllMatcher} matches all principals and is useful for
     * constructing <q>other</q> entries when emulating POSIX-style
     * permissions.
     */
    public static class AllMatcher
            implements ACL.PrincipalMatcher<BeehiveObjectId> {
        private static final long serialVersionUID = 1L;

        public boolean matches(BeehiveObjectId principal,
                ACL.ACLBearerAccessor<BeehiveObjectId> accessor) {
            return true;
        }

        @Override
        public String toString() {
            return "all";
        }
    }

    /**
     * The {@code CelesteACE} class specializes general access control entries
     * (as defined by {@link sunlabs.celeste.util.ACL.ACE ACE} to the
     * specifics of Celeste files and operations on them.
     */
    public static class CelesteACE extends ACL.ACE<CelesteOps, BeehiveObjectId> {
        private static final long serialVersionUID = 1L;

        public CelesteACE(ACL.PrincipalMatcher<BeehiveObjectId> matcher,
                Set<CelesteOps> privileges, ACL.Disposition disposition) {
            super(matcher, privileges, disposition);
        }
    }

    /**
     * Assembles the given access control entries into a {@code CelesteACL}.
     *
     * @param aceList   a list of {@code CelesteACE}s
     */
    public CelesteACL(CelesteACE... aceList) {
        super(aceList);
    }

    /**
     * Construct a Celeste ACL from its byte-encoded form.
     *
     * @param bytes the encoded form of the acl, as produced by {@link
     *              #toByteArray() toByteArray()}}
     *
     * @throws ClassNotFoundException
     *      if the encoded form was produced by an obsolete version of this
     *      class
     */
    public static CelesteACL getEncodedInstance(byte[] bytes)
            throws ClassNotFoundException {
        CelesteACL celesteACL = null;
        ByteArrayInputStream bais = null;
        ObjectInputStream ois = null;
        try {
            bais = new ByteArrayInputStream(bytes);
            ois = new ObjectInputStream(bais);
            celesteACL = (CelesteACL)ois.readObject();
        } catch (IOException e) {
            //
            // Given that we're using an underlying ByteArrayInputStream,
            // this exception shouldn't occur.  Thus, making callers check for
            // it is counterproductive.  Compromise by turning it into an
            // unchecked exception.
            //
            throw new RuntimeException(e);
            } finally {
            try {
                if (ois != null)
                    ois.close();
            } catch (IOException e) {}
            try {
                if (bais != null)
                    bais.close();
            } catch (IOException e) {}
        }
        return celesteACL;
    }

    /**
     * Construct a Celeste ACL from its string-encoded form.
     *
     * @param encodedString the encoded form of the acl, as produced by {@link
     *                      #toEncodedString() toEncodedString()}}
     *
     * @throws ClassNotFoundException
     *      if the encoded form was produced by an obsolete version of this
     *      class
     */
    public static CelesteACL getEncodedInstance(String encodedString)
            throws ClassNotFoundException {
        byte[] encoding = CelesteEncoderDecoder.fromHexString(encodedString);
        return CelesteACL.getEncodedInstance(encoding);
    }

    /**
     * Return this Celeste ACL's list of {@code CelesteACE}s.  The returned
     * list is freely modifiable.
     *
     * @return a copy of this ACL's list of ACEs
     */
    public List<CelesteACE> getCelesteACEs() {
        List<CelesteACE> celesteAces = new ArrayList<CelesteACE>();
        for (ACE<CelesteOps, BeehiveObjectId> ace : this.getACEs()) {
            //
            // I'm aware of no way to avoid this run-time check and therefore
            // of the need to construct a copy of the list of ACEs.
            //
            CelesteACE celesteACE = (CelesteACE) ace;
            celesteAces.add(celesteACE);
        }
        return celesteAces;
    }

    /**
     * Encodes this {@code CelesteACL} into a byte array, for later
     * reconstitution via {@link #getEncodedInstance(byte[])}.
     *
     * @return  a serialized representation of this Celeste ACL
     */
    public byte[] toByteArray() {
        ByteArrayOutputStream baos = null;
        ObjectOutputStream oos = null;
        try {
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(this);
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            //
            // Given that we're using an underlying ByteArrayOutputStream,
            // this exception shouldn't occur.  Thus, making callers check for
            // it is counterproductive.  Compromise by turning it into an
            // unchecked exception.
            //
            throw new RuntimeException(e);
        } finally {
            try {
                if (oos != null)
                    oos.close();
            } catch (IOException e) {}
            try {
                if (baos != null)
                    baos.close();
            } catch (IOException e) {}
        }
    }

    /**
     * Encodes this {@code CelesteACL} into a string, for later
     * reconstitution via {@link #getEncodedInstance(String)}.
     */
    public String toEncodedString() {
        byte[] encoding = this.toByteArray();
        return CelesteEncoderDecoder.toHexString(encoding);
    }

    /**
     * Runs a small sanity check that constructs and prints out an ACL.
     */
    public static void main(String[] args) {
        OwnerMatcher om = new OwnerMatcher();
        GroupMatcher gm = new GroupMatcher();
        EnumSet<CelesteOps> r = EnumSet.of(CelesteOps.readFile);
        EnumSet<CelesteOps> rw =
            EnumSet.of(CelesteOps.readFile, CelesteOps.writeFile);
        CelesteACE ownerRWAce = new CelesteACE(om, rw, grant);
        CelesteACE groupRAce = new CelesteACE(gm, r, grant);
        CelesteACL acl = new CelesteACL(ownerRWAce, groupRAce);
        System.out.printf("the ACL:%n%s%n", acl.toString());
    }
}
