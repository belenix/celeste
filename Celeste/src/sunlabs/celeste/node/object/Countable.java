/*
 * Copyright 2007-2010 Oracle. All Rights Reserved.
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
 * Please contact Oracle Corporation, 500 Oracle Parkway, Redwood Shores, CA 94065
 * or visit www.oracle.com if you need additional information or
 * have any questions.
 */
package sunlabs.celeste.node.object;

import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.object.TitanObjectHandler;

/**
 *
 */
public class Countable {
    public interface Handler <T extends TitanObject> extends TitanObjectHandler {
        public interface Object extends TitanObjectHandler.ObjectAPI {

        }

        /**
         * <p>
         * Count the number of words in object specified by the BeehiveMessage.
         * </p>
         * @param message
         * @return The reply BeehiveMessage containing the entire result of the operation.
         */
        public TitanMessage extension(TitanMessage message);
    }
}
