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
# A test case that doubles as an example.
#
# Usage:
#       celesteshCreateTest.py [repetitions]
#           repetitions:    the number of times to repeat the test (defaults
#                           to 1); negative value means "forever"
#
# Ingests inFileName into Celeste and then repeatedly writes it back to a
# pipeline that invokes cmp to compare the ingested file to the original.
#
# Before entering the loop, creates credentials and name spaces as needed and
# writes the client credential's data and metadata into /tmp/cred-d and
# /tmp/cred-m.
#

from celestesh import Celestesh
from subprocess import Popen, PIPE
import sys
from sys import stderr

#
# Helper function:  invoked from within the loop bodies at the end of the
# __main__ code below.
#
def readAndCompare(sh, nameSpace, fileId):
    pRead = sh.readFile(nameSpace, fileId, 0, -1, stdout=PIPE)
    pCmp = Popen(["/usr/bin/cmp", inFileName, "-"], stdin=pRead.stdout,
        stdout=PIPE)
    cmpOutput = pCmp.communicate()[0]
    return (pCmp.returncode, cmpOutput)

if __name__ == "__main__":
    #
    # Process arguments.
    #
    if len(sys.argv) == 1:
        repetitions = 1;
    else:
        repetitions = int(sys.argv[1])
    if len(sys.argv) > 2:
        celestePort = sys.argv[2]
    else:
        celestePort = 14000

    #
    # Wire down various parameters.
    #
    # The "client" pair denotes the entity under whose authority requests will
    # be made of Celeste.
    #
    # The "nameSpace" pair designates the name space in which files this
    # script manipulates will live.
    #
    # The "owner" and "group" pairs provide identities pertinent to access
    # control for those files.
    #
    # They're all chosen to be compatible with the "example6" bash script.
    #
    clientName = "celeteshCreateTest"
    clientPassword = "clientPassword"
    nameSpaceName = "celeteshCreateTestNameSpace"
    nameSpacePassword = "asdf"
    ownerName = "celesteshCreateTestOwner"
    ownerPassword = "owner"
    groupName = "celesteshCreateTestGroup"
    groupPassword = "group"
    replicationParams = \
        "AObject.Replication.Store=2;" \
        "VObject.Replication.Store=2;" \
        "BObject.Replication.Store=2"
    deleteToken = "delete"
    blockSize = 8 * 1024 * 1024
    timeToLive = 86400
    signWrites = False

    #
    # Set up CelesteSh-level access to Celeste.
    #
    sh = Celestesh(clientName, clientPassword, replicationParams=replicationParams)
    sh.setCelesteAddress("127.0.0.1", celestePort)
    sh.verbose = False

    #
    # Create credentials and name spaces as necessary.
    #
    if not sh.credentialExists(nameSpaceName):
        sh.newNameSpace(nameSpaceName, nameSpacePassword)
    if not sh.credentialExists(clientName):
        sh.newCredential(clientName, clientPassword)
    if not sh.credentialExists(ownerName):
        sh.newCredential(ownerName, ownerPassword)
    if not sh.credentialExists(groupName):
        sh.newCredential(groupName, groupPassword)

    #
    # Ingest the input file if necessary.
    #
    for i in range(0,repetitions):
        print "%s-%s" % (sh.getCelesteAddress()[0], sh.getCelesteAddress()[1])
        fileName = "file%s-%s%0-7d" % (sh.getCelesteAddress()[0], sh.getCelesteAddress()[1], i)
        if not sh.fileExists(nameSpaceName, fileName):
            sh.createFile(nameSpaceName, nameSpacePassword, fileName,
                          ownerName, groupName, deleteToken,
                          blockSize=blockSize,
                          timeToLive=timeToLive,
                          writesMustBeSigned=signWrites)

    #
    # Check the file's length.  If it's zero, write its contents.
    #
    #attrDict = sh.getFileAttributes(nameSpaceName, fileName)
    #fileLength = int(attrDict["Celeste.FileSize"])
    #if fileLength == 0:
    #    sh.writeFile(nameSpaceName, fileName, 0, inFileName)

    sys.exit(0)
