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
package sunlabs.asdf.web.http;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A simple "tap" on an {@link OutputStream}.
 * <p>
 * Wrap an existing {@link OutputStream} with an instance of this class and all
 * output destined for the {@link OutputStream} {@code out} is also written to
 * the {@link OutputStream} {@code tap}.
 * </p>
 * @author Glenn Scott - Sun Microsystems Laboratories, Sun Microsytems, Inc.
 */
public class TappedOutputStream extends OutputStream {
    private OutputStream out;
    private OutputStream tap;
    
    public TappedOutputStream(OutputStream out, OutputStream tap) {
        this.out = out;
        this.tap = tap;
    }
    
    @Override
    public void write(int b) throws IOException {
        this.out.write(b);
        if (this.tap != null) {
            this.tap.write(b);
        }
    }
    
    @Override
    public void write(byte[] b) throws IOException {
        this.out.write(b);
        if (this.tap != null) {
            this.tap.write(b);
        }
    }
    
    public void write(byte[] b, int offset, int length) throws IOException {
        this.out.write(b, offset, length);
        if (this.tap != null) {
            this.tap.write(b, offset, length);
        }
    }
    
    public void flush() throws IOException {
        this.tap.flush();
        this.out.flush();
    }
}
