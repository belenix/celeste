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

package sunlabs.celeste.util;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

//
// XXX: Need to determine whether principals cover both individual entities
//      and group entities (in a single name space) or whether we need two
//      distinct name spaces for them.
//
//      Some ACL systems (e.g., WebDAV) allow a wider range of principals
//      than stated above.  Some such extended principals, such as "all",
//      "authenticated", and "unauthenticated", are essentially aggregations.
//      Others are more complex.  For example, WebDAV allows properties as
//      principals, and, in particular, uses this technique to handle the
//      "owner" principal and "group" principal requirements stated below.
//
//      Also need way to specify whether an ACE corresponds to:
//      -   a specific individual
//      -   a specific group
//      -   the "owner" principal of the entity to which the ACS's ACL is
//          attached
//      -   the "group" principal of the entity to which the ACS's ACL is
//          attached
//      -   anybody but the named principal
//
//      More complications:  Some ACL systems (e.g., WebDAV) support notions
//      of ACE inheritance and of ACE protection.  Need to model these (or,
//      rather, not preclude modeling them later).
//
//      The PrincipalMatcher interface is intended to allow these variations
//      to be modeled.
//
//      WebDAV allows for aggregated privileges (where privileges correspond
//      to Ops in the code below).  For example, DAV:write is the union of
//      DAV:write-acl, DAV:write-content, and DAV:write-properties.  To model
//      this aggregation, we'll need to use sets of privileges (or Ops) in
//      some contexts where a single privilege would otherwise appear.
//      Concrete realizations will also want to define constant sets that
//      reify composite privileges of this sort.
//
/**
 * <p>
 *
 * ...
 *
 * </p><p>
 *
 * {@code ACL}s and their contained {@code ACE}s are intended to be immutable;
 * this class and its nested {@code ACE} class provide no methods for
 * modifying their instances.  Subclasses of either of them should maintain
 * this property.
 *
 * </p><p>
 *
 * Note that, although the type parameter {@code P} is not explicitly listed
 * as extending the {@code Serializable} interface, it must in fact implement
 * that interface.  This requirement follows from the fact that the {@code
 * ACE} components of access control lists must be able to name principals.
 *
 * </p>
 *
 * @param <Ops> an enumeration of operation names (or names of privileges to
 *              perform those operations) that an access control list is to
 *              govern
 * @param <P>   the type of a principal, instances of which designate an
 *              operation invoker's identity and are recorded in access
 *              control entries (ACEs)
 */
public class ACL<Ops extends Enum<Ops>, P> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Provides values that record whether a given access control list (or
     * access control entry within such a list) grants access to a given
     * principal or denies that principal access.
     */
    public enum Disposition { grant, deny }

    /**
     * Instances of {@code ACLException} report the cause of a failed access
     * check by recording the string form of either the specific access
     * control entry that triggered the failure or, when no access control
     * entry matched the principal being checked, the entire access control
     * list.
     */
    //
    // XXX: Called ACLException rather than AccessException to avoid conflict
    //      with the class in java.rmi.  But perhaps such avoidance isn't
    //      needed.
    //
    public static class ACLException extends RuntimeException {
        private final static long serialVersionUID = 1L;

        /**
         * Constructs an {@code ACLException} recording the fact that the
         * access control entry at position {@code i} within {@code acl}
         * caused an access check to fail.
         *
         * @param acl   the acl being checked
         * @param i     the position within {@code acl} of the access control
         *              entry triggering the failure
         */
        public <A extends ACL<?, ?>> ACLException(A acl, int i) {
            super(String.format(
                "access denied: %s", acl.getACE(i)));
        }

        /**
         * Constructs an {@code ACLException} recording the fact that {@code
         * ace} caused an access check to fail.
         *
         * @param ace   the access control entry triggering the failure
         */
        public <A extends ACE<?, ?>> ACLException(A ace) {
            super(String.format("access denied: %s", ace));
        }

        /**
         * Constructs an {@code ACLException} recording the fact that {@code
         * ace} caused an access check for {@code op} to fail.
         *
         * @param op    the privilege being checked
         * @param ace   the access control entry triggering the failure
         */
        public <Ops extends Enum<Ops>, A extends ACE<Ops, ?>>
        ACLException(Ops op, A ace) {
            super(String.format("access denied for %s: %s", op, ace));
        }

        /**
         * Constructs an {@code ACLException} recording the fact that {@code
         * acl} contains no entry matching the principal at hand.
         *
         * @param acl   the acl for which no matching {@code ACE} exists
         */
        public <A extends ACL<?, ?>> ACLException(A acl) {
            super(String.format(
                "access denied [no matching entry]: %s", acl));
        }

        /**
         * Constructs an {@code ACLException} recording the fact that {@code
         * acl} contains no entry for privilege {@code op} matching the
         * principal at hand.
         *
         * @param op    the privilege being checked
         * @param acl   the acl for which no matching {@code ACE} exists
         */
        public <Ops extends Enum<Ops>, A extends ACL<Ops, ?>>
        ACLException(Ops op, A acl) {
            super(String.format(
                "access denied for %s [no matching entry]: %s", op, acl));
        }
    }

    /**
     * Implementations of the {@code ACLBearerAccessor} interface provide
     * access to attributes of the object to which an ACL is attached, thereby
     * allowing ACEs to base matching decisions on those attributes.  This
     * interface itself is merely a marker indicating that its implementations
     * access attributes of the ACL bearer.
     *
     * @param <P>   the type of a principal whose attributes are to be
     *              accessed through this interface
     */
    public interface ACLBearerAccessor<P> {
    }

    //
    // PrincipalMatchers can be constructed to handle ACE semantics such as
    // inversion, checks against properties of the object to which an ACE's
    // ACL is attached, and so on.
    //
    // The toString() method of a PrincipalMatcher should produce a string
    // that distinguishes its containing ACE from ones containing matchers
    // offering different semantics.  (That is, this string participates in
    // forming the cryptographic signature for operations that modify ACLs,
    // and, therefore, a change in matcher semantics should induce a change in
    // signature.)
    //
    public interface PrincipalMatcher<P> extends Serializable {
        /**
         * Returns {@code true} if the {@code ACE} to which this matcher is
         * attached should trigger on {@code p}.
         *
         * @param principal the principal to be matched
         * @param accessor  an accessor for attributes of the object bearing
         *                  the ACL this matcher is evaluating
         *
         * @return  {@code true} if {@code p} should trigger the {@code ACE}
         *          to which this principal matcher is attached and {@code
         *          false} otherwise
         */
        public boolean matches(P principal, ACLBearerAccessor<P> accessor);
    }

    //
    // XXX: Bundling invert semantics into this class isn't quite right.
    //      Inversion ought to be applicable to any kind of matched principal,
    //      not just to individuals.  So there should be a separate
    //      InvertMatcher class whose constructor takes a PrincipalMatcher.
    //      (But to be very picky about matching WebDAV semantics for invert,
    //      the argument PrincipalMatcher shouldn't be allowed to be itself an
    //      InvertMatcher.)
    //
    public static class IndividualMatcher<P>
            implements PrincipalMatcher<P>, Serializable {
        private static final long serialVersionUID = 1L;

        private final P         principal;
        private final boolean   invert;

        /**
         * Construct a new {@code IndividualMatcher} that matches {@code
         * principal}.  {@code principal} can be {@code null}, in which case
         * {@code matches()} will always return {@code false}.  (This
         * configuration is useful for handling things like a file that has no
         * group.)
         *
         * @param principal if non-{@code null} the {@code principal} against
         *                  which {@code matches()} returns {@code true}
         */
        public IndividualMatcher(P principal) {
            this(principal, false);
        }

        /**
         * Construct a new {@code PrincipalMatcher} that matches or does not
         * match {@code principal} according to whether or not {@code invert}
         * is respectively {@code false} or {@code true}.  {@code principal}
         * can be {@code null}, in which case it is treated as being distinct
         * from all principals.
         *
         * @param principal if non-{@code null} the {@code principal} against
         *                  which {@code matches()} returns {@code true}
         * @param invert    if {@code true}, cause {@code matches()} to return
         *                  the negation of what it otherwise would return
         */
        public IndividualMatcher(P principal, boolean invert) {
            this.principal = principal;
            this.invert = invert;
        }

        public boolean matches(P p, ACLBearerAccessor<P> accessor) {
            boolean match = (this.principal == null) ?
                false : this.principal.equals(p);
            return match != invert;
        }

        @Override
        public String toString() {
            return String.format("IndividualMatcher[%s%s]",
                this.invert ? "!" : "",
                (this.principal == null) ? "" : this.principal.toString());
        }
    }

    //
    // XXX: Although there may be reasons related to abstraction w.r.t.  type
    //      that force the ACE class to be static, must the class logically be
    //      static?  It comes down to whether or not it makes sense to attach
    //      the same ACL (and constituent ACEs) to multiple objects.  That, in
    //      turn, comes down to whether any ACE needs to know the specific
    //      identity of the object it's controlling access to, as opposed to
    //      being given a reference to that object at the time someone
    //      requests an access control decision of it.
    //
    public static class ACE<O extends Enum<O>, P> implements Serializable {
        private static final long serialVersionUID = 1L;

        private final PrincipalMatcher<P>   matcher;
        private final Set<O>                privileges;
        private final Disposition           disposition;
        private final boolean               _protected;
        private final boolean               inherited;

        public ACE(PrincipalMatcher<P> matcher, Set<O> privileges,
                Disposition disposition) {
            this.matcher = matcher;
            this.privileges = privileges;
            this.disposition = disposition;
            this._protected = false;
            this.inherited = false;
        }

        public PrincipalMatcher<P> getMatcher() {
            return this.matcher;
        }

        /**
         * Return a read-only view of the set of privileges that this {@code
         * ACE} governs.
         *
         * @return  this {@code ACE}'s privilege set
         */
        public Set<O> getPrivileges() {
            return Collections.unmodifiableSet(this.privileges);
        }

        /**
         * Return a {@code Disposition} value indicating whether or not this
         * {@code ACE} grants access.
         *
         * @return this {@code ACE}'s disposition
         */
        public Disposition getDisposition() {
            return this.disposition;
        }

        @Override
        public String toString() {
            //
            // The idea here is to follow the representation of ACEs given by
            // the Solaris "ls -v " command.
            //
            // XXX: The format for the privileges set needs tweaking.
            // XXX: Ought to ensure that none of the individual components
            //      includes ":" as a substring in its representation (or
            //      quote them if so).
            // XXX: Need to figure out how to represent inversion and
            //      inheritance (provided that we end up supporting them at
            //      all).
            //
            return String.format(
                "%s:%s:%s",
                this.matcher.toString(),
                this.privileges.toString(),
                this.disposition.toString());
        }
    }

    //
    // Note that ACEs are ordered; evaluation proceeds by considering each in
    // sequence.
    //
    private final List<ACE<Ops, P>> aces = new ArrayList<ACE<Ops, P>>();

    /**
     * Construct a new access control list from the given access control
     * entries.
     *
     * @param aceList   zero or more {@code ACE}s, naked or packed into arrays
     */
    public <A extends ACE<Ops, P>> ACL(A... aceList) {
        if (aceList == null)
            return;
        for (A ace : aceList)
            this.aces.add(ace);
    }

    /**
     * Return the access control entry at position {@code i}.
     *
     * @param i the positional index of the desired {@code ACE}
     *
     * @return  the {@code ACE} at position {@code i}
     *
     * @throws IndexOutOfBoundsException
     *      if {@code i} is out of range for this {@code ACL}'s list of {@code
     *      ACE}s
     */
    public ACE<Ops, P> getACE(int i) {
        return aces.get(i);
    }

    /**
     * Return a read-only view of this {@code ACL}'s list of access control
     * entries.
     *
     * @return  a read-only view of this {@code ACL}'s list of access control
     *          entries
     */
    public List<ACE<Ops, P>> getACEs() {
        return Collections.unmodifiableList(this.aces);
    }

    /**
     * Evaluates whether {@code p} should be granted access to the privilege
     * (or operation) denoted by {@code op} and returns the resulting
     * disposition.
     *
     * @param op        the privilege being checked
     * @param accessor  an {@code ACLBearerAccessor} for examining attributes
     *                  of the object to which this ACL is attached
     * @param p         the principal whose access is being checked
     *
     * @return  a {@code Disposition} value indicating whether or not access
     *          is granted
     */
    public Disposition evaluate(Ops op, ACLBearerAccessor<P> accessor, P p) {
        for (ACE<Ops, P> ace : this.aces) {
            PrincipalMatcher<P> matcher = ace.getMatcher();
            if (!matcher.matches(p, accessor))
                continue;
            if (!ace.getPrivileges().contains(op))
                continue;
            return ace.getDisposition();
        }
        return Disposition.deny;
    }

    /**
     * Checks whether {@code p} should be granted access to the privilege (or
     * operation) denoted by {@code op}.
     *
     * @param op        the privilege being checked
     * @param accessor  an {@code ACLBearerAccessor} for examining attributes
     *                  of the object to which this ACL is attached
     * @param p         the principal whose access is being checked
     *
     * @throws ACLException
     *      if the principal is not granted access to the privilege designated
     *      by {@code op}
     */
    public void check(Ops op, ACLBearerAccessor<P> accessor, P p) {
        //
        // Ensure there's actually a privilege being checked for.  (Arguably
        // it's an error not to supply one, but be lenient.)
        //
        if (op == null)
            return;
        for (ACE<Ops, P> ace : this.aces) {
            PrincipalMatcher<P> matcher = ace.getMatcher();
            if (!matcher.matches(p, accessor))
                continue;
            if (!ace.getPrivileges().contains(op))
                continue;
            if (ace.getDisposition() == Disposition.grant)
                return;
            else
                throw new ACLException(op, ace);
        }
        throw new ACLException(op, this);
    }

    @Override
    public String toString() {
        //
        // The string representation of an ACL is the indexed representation
        // of its ACEs, one per line.
        //
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < aces.size(); i++)
            sb.append(String.format("%d:%s%n", i, aces.get(i)).toString());
        return sb.toString();
    }
}
