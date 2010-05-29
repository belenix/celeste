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

package sunlabs.beehive.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class OrderedPropertiesTest {

    //
    // Verify that a property containing a non-ASCII Unicode character in
    // its value survices being stored and reloaded unchanged.
    //
    @Test
    public final void testUnicodeCharacterStoreAndLoad() throws IOException {
        OrderedProperties props = new OrderedProperties();
        props.setProperty("UnicodeValuedProperty", "\uD800\uDC00");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        props.store(baos, "meaningless comment");
        ByteArrayInputStream bais = new ByteArrayInputStream(
            baos.toByteArray());
        OrderedProperties inProps = new OrderedProperties();
        inProps.load(bais);
        assertEquals("property value is unaltered after store->load",
            "\uD800\uDC00", inProps.getProperty("UnicodeValuedProperty"));
    }
}

