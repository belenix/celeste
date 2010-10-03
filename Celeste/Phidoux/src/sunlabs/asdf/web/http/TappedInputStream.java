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
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A simple "tap" on an {@link InputStream}.
 * <p>
 * Wrap an existing {@link InputStream} with an instance of this class and all
 * input read from the {@link InputStream} {@code in} is also copied to the
 * {@link OutputStream} {@code tap}.
 * </p>
 * 
 * @author Glenn Scott - Sun Microsystems Laboratories, Sun Microsytems, Inc.
 */
public class TappedInputStream extends InputStream {
    private InputStream inputStream;
    private OutputStream tap;
    
    public TappedInputStream(InputStream in, OutputStream tap) {
        this.inputStream = in;
        this.tap = tap;
    }

    @Override
    public int read() throws IOException {
        int c = this.inputStream.read();
        if (this.tap != null)
            this.tap.write(c);
        return c;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int n = this.inputStream.read(b);
        if (this.tap != null)
            this.tap.write(b, 0, n);
        return n;        
    }

    @Override
    public int read(byte[] b, int offset, int length) throws IOException {
        int n = this.inputStream.read(b, offset, length);
        if (this.tap != null) {
            if (n > 0)
                this.tap.write(b, offset, n);
        }
        return n;        
    }
    
    @Override
    public void close() throws IOException {
        super.close();
        try { this.tap.close(); } catch (IOException e) { }
    }
    
    public void setTapOutputStream(OutputStream tap) {
        this.tap = tap;
    }
}
