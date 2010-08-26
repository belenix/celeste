/*
 * Copyright 2008-2009 Sun Microsystems, Inc. All Rights Reserved.
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

package sunlabs.titan.node.services.api;

import java.io.Serializable;

import java.util.concurrent.Callable;

/**
 * NB: This class and all of the extension implementation are under heavy development.  Proceed carefully.
 * 
 * Classes implementing this interface create object instances that are
 * transmitted to BeehiveObjects in the object pool for evaluation.
 * <p>
 * Evaluation consists of invoking the {@code call()} method specified
 * by the {@link java.util.concurrent.Callable Callable} interface.
 * </p>
 * @author Glenn Scott - Sun Microsystems Laboratories
 */
public class BeehiveExtension {
    //
    // XXX: This definition and its uses compile and execute properly.  But it
    //       might be useful to make it more precise by adding <T extends
    //       Serializable> as a parameter to the interface and changing the
    //       parameter given to Callable from Serializable to T.  That said, no
    //       current invoker of BeehiveExtension.call() cares that the return type
    //       be more precise than Serializable.
    //
    public interface Implementation extends Serializable, Callable<Serializable> {
        
    }

}
