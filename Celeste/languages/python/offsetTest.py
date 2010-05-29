#!/usr/bin/env python
#
# Copyright 2009 Sun Microsystems, Inc. All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
# 
# This code is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License version 2
# only, as published by the Free Software Foundation.
# 
# This code is distributed in the hope that it will be useful, but
# WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# General Public License version 2 for more details (a copy is
# included in the LICENSE file that accompanied this code).
# 
# You should have received a copy of the GNU General Public License
# version 2 along with this work; if not, write to the Free Software
# Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
# 02110-1301 USA
# 
# Please contact Sun Microsystems, Inc., 16 Network Circle, Menlo
# Park, CA 94025 or visit www.sun.com if you need additional
# information or have any questions.
#

#
# A test that verifies that writes throught the celestefs interface maintain
# file offsets properly.
#
# XXX:  This test should be tidied up a bit.  It should accept the file name
#       as argument, so that it can be run repeatedly.  It should also accept
#       a block size argument.
#

from celestefs import Celestefs
from sys import exit, stderr

if __name__ == "__main__":

    name = "offsetTest"

    c = Celestefs(name, name)
    c.verbose = True

    if not c.credentialExists(name):
        c.mkid()

    if not c.fileSystemExists(name):
        if not c.mkfs(name, name):
            print "mkfs(%s, %s) failed" % (name, name)
            exit(1);        

    filename = "/" + name + "/" + "file"

    #
    # Create or truncate the test file, as approriate.
    #
    if not c.fileExists(filename):
        createAttrs = {
            #"BlockSize" : "3"
            #"BlockSize" : "16"
        }
        c.create(filename, contentType="text/plain", attrs=createAttrs)
    else:
        c.setLength(filename, 0)

    #
    # Write a sequence of characters that give clues to their offsets in the
    # file.
    #
    p = c.pwrite(filename)
    p.stdin.write("0123456789012345678901234567890123456789")
    p.communicate()

    #
    # See what we have so far.
    #
    p = c.pread(filename)
    output = p.communicate()[0]
    print "%s's contents: \"%s\"" % (filename, output)

    #
    # Now for the test proper.  Overwrite some of the data in the middle with
    # something else.
    #
    p = c.pwrite(filename, offset=5)
    p.stdin.write("@five?")
    p.communicate()

    #
    # Report on the results.
    #
    p = c.pread(filename)
    output = p.communicate()[0]
    print "%s's contents: \"%s\"" % (filename, output)

    #
    # Finally, pick out a span that surrounds the last one written above by a
    # character on each side, read it, and report what was read.
    #
    p = c.pread(filename, offset=4, length=8)
    output = p.communicate()[0]
    print "interior span from %s: \"%s\"" %(filename, output)
