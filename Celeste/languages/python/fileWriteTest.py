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
# Exercises CelesteFileSystem's code paths for writing data by creating a
# file and then writing it, reading it back and comparing with the original,
# and truncating it a given number of times.
#
# Usage:
#       fileWriteTest [-p timingsFile] localFile celesteFile [repetitions]
#
# If "-p timingsFile" is given, enable profiling and append the timings to the
# file given as operand to the flag.
#
# localFile is the name of a file in the local file system to copy into the
# Celeste file system.
#
# celesteFile is the name to use for the file copied into the (root directory
# of) the Celeste file system.  (It should be a base name, that is, contain no
# slash characters.)
#
# repetitions is the number of times to write the file into the Celeste file
# system.  It defaults to 1.  A negative value or zero requests indefinite
# repetition.
#

from celestefs import Celestefs
#from util import IllegalArgumentException

from cStringIO import StringIO
from getopt import getopt
import random
from subprocess import Popen, PIPE
import sys
from sys import stderr

if __name__ == "__main__":
    #
    # Process arguments.  Check for "-p timingsFile".  Verify proper number of
    # arguments.
    #
    try:
        opts, args = getopt(sys.argv[1:], "p:")
    except getopt.GetoptError, err:
        print str(err)
        sys.exit(1)
    timingsFile = None
    for opt, val in opts:
        if opt == "-p":
            timingsFile = str(val)
    if len(args) != 2 and len(args) != 3:
        print "missing or extra arguments"
        sys.exit(1)
    localFile = str(args[0])
    celesteName = str(args[1])
    repetitions = 1
    if len(args) == 3:
        repetitions = int(args[2])

    #
    # Wire down various parameters.
    #
    myId = "fileWriteTest"
    myPassword = "fileWriteTest's-password"
    #
    # Use absolutely minimal replication.
    #
    replicationParams = \
        "AObject.Replication.Store=1;" \
        "VObject.Replication.Store=1;" \
        "BObject.Replication.Store=1"
    fileSystemName = "fileWriteTestFS"
    fileSystemPassword = "filwWriteTestFS's-password"

    #
    # If profiling has been requested, arrange to get timing information in
    # CSV format in timingsFile.  Do so by supplying the methods to profile as
    # the argument to the Java property named immediately below and by naming
    # the file to write the timings to as the value of the second property
    # below.
    #
    if timingsFile != None:
        methodProp = "sunlabs.celeste.client.filesystem.ProfilerConfiguration"
        outputProp = "sunlabs.celeste.client.filesystem.ProfilerOutputFile"
        profiledMethods = [
            "CelesteFileSystem.CelesteOutputStream.write",
            "CelesteFileSystem.File.write",
            "FileImpl.write",
            "FileImpl.tryWriteVersion"
        ]
        profilingOpt = \
            [ "-DenableProfiling=true" ] + \
            [ "-D%s=%s" % (methodProp, ":".join(profiledMethods)) ] + \
            [ "-D%s=%s" % (outputProp, timingsFile) ]
    else:
        profilingOpt = []

    celestefs = Celestefs(myId, myPassword, "127.0.0.1", 14000,
        jvmopts=["-server", "-ea"] + profilingOpt)
    #
    # Arrange to see what JVM invocations are issued.
    #
    celestefs.verbose = True

    #
    # Start interactions with CelesteFileSystem and Celeste.
    #

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

    #
    # If it doesn't already exist, create the file.
    #
    path = "/%s/%s" % (fileSystemName, celesteName)
    if not celestefs.fileExists(path):
        fileAttrs = {
            "CacheEnabled" : "true",
            "ContentType" : "text/plain",
            "ReplicationParameters" : replicationParams
        }
        celestefs.create(path, attrs=fileAttrs)

    #
    # Repeatedly ingest and fill the file, read it back to compare it with the
    # original, and truncate it.  (If profiling is enabled, each repetition
    # will append information to timingsFile.)
    #
    remainingRepetitions = repetitions
    while repetitions <= 0 or remainingRepetitions > 0:
        pCat = Popen(["/bin/cat", localFile], stdout=PIPE)
        pWrite = celestefs.pwrite(path, 0, stdin=pCat.stdout)
        pWrite.communicate()

        pRead = celestefs.pread(path, 0, -1, stdout=PIPE)
        pCmp = Popen(["/usr/bin/cmp", localFile, "-"], stdin=pRead.stdout,
            stdout=PIPE)
        pCmp.communicate()[0]

        if pCmp.returncode != 0:
            print "%s does not compare identical to %s; exiting" % \
                (path, localFile)

        celestefs.setLength(path, 0)

        remainingRepetitions = remainingRepetitions - 1

    sys.exit(0)
