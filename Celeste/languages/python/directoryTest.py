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
# Exercises one aspect of the directory implementation by creating and
# accessing numerous files in a single directory.
#
# Usage:
#       directoryTest [-o timing-output-file] numItems
#

#
# First version:  Create a randomly named (top level) directory and fill it
# with numItems entries each with a randomly generated name.  Report a list of
# times for creating each entry.  Each directory entry (necessarily)
# corresponds to a file.  Each of these files is distinct but empty.  (An
# interesting variation would be to use the same file for each, but requires
# hard link support that hasn't been implemented in CelesteFileSystem.)
#
# Note that the test only adds files and never removes them, so successive
# runs will continually fill up Celeste.
#
# XXX:  The test should be extended to include lookups and deletes, with
#       options to specify various combinations.
#

from celestefs import Celestefs
from util import IllegalArgumentException

from cStringIO import StringIO
from getopt import getopt
import random
from subprocess import Popen, PIPE
import sys
from sys import stderr

#
# XXX:  This class needs to be refined to actually follow the generator and
#       iterator protocols.
# XXX:  Perhaps this class should be moved to the util module.
#
class FileNameGenerator:
    defaultAlphabet = \
        "abcdefghijklmnopqrstuvwxyz" \
        "_-" \
        "0123456789" \
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    def __init__(self, length, alphabet=None):
        if alphabet == None:
            alphabet = FileNameGenerator.defaultAlphabet
        if len(alphabet) == 0:
            raise IllegalArgumentException(
                "alphabet must be a non-empty string")
        self.alphabet = alphabet

        #
        # Set up a random variable to use in generating names.
        #
        self.rv = random.Random()
        self.length = length

    def next(self):
        resultBuilder = StringIO()
        for i in range(0, self.length):
            pos = self.rv.randint(0, len(self.alphabet) - 1)
            resultBuilder.write(self.alphabet[pos])
            i += 1
        return resultBuilder.getvalue()

if __name__ == "__main__":
    #
    # Process arguments.  Check for "-o filename", verify that there's at
    # least one non-option argument, and obtain numItems from that argument.
    #
    try:
        opts, args = getopt(sys.argv[1:], "o:")
    except getopt.GetoptError, err:
        print str(err)
        sys.exit(1)
    outFile = None
    for o, val in opts:
        if o == "-o":
            outFile = open(val, "w")
    if len(args) == 0:
        print "missing numItems argument"
        sys.exit(1)
    numItems = int(args[0])

    #
    # Wire down various parameters.
    #
    myId = "directoryTest"
    myPassword = "directoryTest's password"
    #
    # Use minimal replication (but not a single copy).  The test is likely to
    # be stressful enough without additional copies.
    #
    replicationParams = \
        "AObject.Replication.Store=2;" \
        "VObject.Replication.Store=2;" \
        "BObject.Replication.Store=2"
    fileSystemName = "directoryTestFS"
    fileSystemPassword = "directoryTestFS's-password"

    celestefs = Celestefs(myId, myPassword, "127.0.0.1", 14000,
        replicationParams, jvmopts=["-server", "-ea"])
    #
    # Arrange to see what JVM invocations are issued.
    #
    #celestefs.verbose = True

    #
    # Ensure that the credential corresponding to myId and myPassword exists.
    #
    if not celestefs.credentialExists(myId):
        celestefs.mkid()

    #
    # Now do the same thing for the file system in which the directory and its
    # entries are to live.
    #
    if not celestefs.fileSystemExists(fileSystemName):
        fsAttrs = {
            "MaintainSerialNumbers" : "false"
        }
        celestefs.mkfs(fileSystemName, fileSystemPassword, attrs=fsAttrs)

    nameGen = FileNameGenerator(20)

    #
    # XXX:  The code below assumes that name collisions won't happen.
    #       Assuming that self.rv is freshly seeded each time (as advertised
    #       in the documentation for the random module), a violation of this
    #       assumption should be negligibly improbable.
    #

    #
    # Create the directory that will hold the individual entries.
    #
    dirAttrs = {
        #"CacheEnabled" : "false",
        "CacheEnabled" : "true"
    }
    basedir = "/%s/%s" % (fileSystemName, nameGen.next())
    celestefs.mkdir(basedir, attrs=dirAttrs)

    fileAttrs = {
        #"CacheEnabled" : "false",
        "CacheEnabled" : "true",
        "ContentType" : "text/plain"
    }
    timeList = []
    i = 0
    while i < numItems:
        itemName = "%s/%s" % (basedir, nameGen.next())
        celestefs.create(itemName, attrs=fileAttrs)
        timeList += ["%4.3f" % celestefs.elapsedTime]
        if outFile != None:
            outFile.write("%4.3f\n" % celestefs.elapsedTime)
        i += 1

    print "created %d files in %s" % (numItems, basedir)
    print "successive creation times:", timeList
    if outFile != None:
        outFile.close()
