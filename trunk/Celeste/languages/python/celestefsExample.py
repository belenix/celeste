#!/usr/bin/env python
#
# Copyright 2008-2009 Sun Microsystems, Inc. All Rights Reserved.
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
# A test case that doubles as an example.
#
# Usage:
#       celestefsExample.py [inFileName]
#           inFileName:     the name of an existing input file (defaults to
#                           /etc/passwd)
#
# Ingests inFileName into Celeste and then writes it back to a pipeline that
# invokes cmp to compare the ingested file to the original.
#

from celestefs import Celestefs
from os import sep
from subprocess import Popen, PIPE
import sys
from sys import stderr

if __name__ == "__main__":
    #
    # Wire down various parameters.
    #
    myId = "exampleId"
    myPassword = "exampleId's-password"
    #
    # Twiddle the replication parameters away from their default values, just
    # because we can.  (But leave the BObject value modest, so that a large
    # file doesn't eat up excessive space in a deployment with modest
    # resources.)
    #
#    replicationParams = \
#        "AObject.Replication.Store=3;" \
#        "VObject.Replication.Store=4;" \
#        "BObject.Replication.Store=2"
    replicationParams = \
        "AObject.Replication.Store=1;" \
        "VObject.Replication.Store=1;" \
        "BObject.Replication.Store=1;" \
        "Credential.Replication.Store=1"
    fileSystemName = "exampleFS"
    fileSystemPassword = "exampleFS's-password"

    #
    # Use the 64-bit Jvm so that we can handle large files.  Disable socket
    # timeouts, so that lengthy i/os don't trigger them.  Request chattiness
    # from the CelesteFs implementation layer.
    #
    celestefs = Celestefs(myId, myPassword, "127.0.0.1", 14000,
        replicationParams, timeout=0,
        jvmopts=["-server", "-ea", "-Dverbose=true", "-Xmx600m", "-Xms600m", "-XX:+UseParallelGC"])
    #
    # Arrange to see what JVM invocations are issued.
    #
    celestefs.verbose = True
    

    #
    # Ensure that the credential corresponding to myId and myPassword exists.
    #
    if not celestefs.credentialExists(myId):
        celestefs.mkid()

    #
    # Now do the same thing for the file system in which inFileName's to be
    # stored.
    #
    if not celestefs.fileSystemExists(fileSystemName):
        celestefs.mkfs(fileSystemName, fileSystemPassword)
    #
    # Belt and suspenders sanity check:  Verify that the create succeeded.
    #
    if not celestefs.fileSystemExists(fileSystemName):
        #
        # Bail out.  Either we couldn't create the file system or something
        # else is wrong; in either case, it's not evident how to recover.
        #
        print >> stderr, "%s non-inspectable; aborting" % fileSystemName
        sys.exit(1)

    #
    # Obtain the name of the file to ingest.  If it's not specified on the
    # command line, use "/etc/passwd" as a modest-sized default.
    #
    if len(sys.argv) > 1:
        inFileName = sys.argv[1]
    else:
        inFileName = "/etc/passwd"

    #
    # Set the file's location in Celeste to be the leaf name part of the input
    # file name, placed directly in the root directory of our Celeste file
    # system.
    #
    celesteFilePath = sep + fileSystemName + sep + inFileName.split(sep)[-1]
    celesteFilePath2 = sep + fileSystemName + sep + inFileName.split(sep)[-1] + \
        "2"

    #
    # Does the file we're to ingest already exist in Celeste?
    #
    if not celestefs.fileExists(celesteFilePath):
        print >> stderr, "%s does not exist" % celesteFilePath
        celestefs.create(celesteFilePath)
    else:
        print >> stderr, "%s exists" % celesteFilePath
    if not celestefs.fileExists(celesteFilePath2):
        print >> stderr, "%s does not exist" % celesteFilePath2
        celestefs.create(celesteFilePath2)
    else:
        print >> stderr, "%s exists" % celesteFilePath2

    #
    # Write the file's contents if necessary.
    #
    statOutput = celestefs.stat(celesteFilePath2, options=["-s"])
    fileSize = int(statOutput.split()[1])
    print >> stderr, "file size: %d" % fileSize

    if fileSize == 0:
        celestefs.write(inFileName, celesteFilePath2, 0, bufferSize=134217728)

        statOutput = celestefs.stat(celesteFilePath2, options=["-s"])
        fileSize = int(statOutput.split()[1])
        print >> stderr, "file size now: %d" % fileSize

    #
    # Read the file and compare it to the local file we used to initialize
    # it.
    #
    pRead = celestefs.pread(celesteFilePath2, 0, -1, stdout=PIPE)
    pCmp = Popen(["/usr/bin/cmp", inFileName, "-"], stdin=pRead.stdout,
        stdout=PIPE)
    print pCmp.communicate()[0]

    #
    # Write the file's contents if necessary.
    #
    statOutput = celestefs.stat(celesteFilePath, options=["-s"])
    fileSize = int(statOutput.split()[1])
    print >> stderr, "file size: %d" % fileSize

    if fileSize == 0:
        pCat = Popen(["/bin/cat", inFileName], stdout=PIPE)
        pWrite = celestefs.pwrite(celesteFilePath, 0, stdin=pCat.stdout,
            bufferSize=134217728)
        pWrite.communicate()

        statOutput = celestefs.stat(celesteFilePath, options=["-s"])
        fileSize = int(statOutput.split()[1])
        print >> stderr, "file size now: %d" % fileSize

    #
    # Read the file and compare it to the local file we used to initialize
    # it.
    #
    pRead = celestefs.pread(celesteFilePath, 0, -1, stdout=PIPE)
    pCmp = Popen(["/usr/bin/cmp", inFileName, "-"], stdin=pRead.stdout,
        stdout=PIPE)
    print pCmp.communicate()[0]

    sys.exit(pCmp.returncode)
