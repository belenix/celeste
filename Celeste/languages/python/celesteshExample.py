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
#       celesteshExample.py [repetitions [inFileName]]
#           repetitions:    the number of times to repeat the test (defaults
#                           to 1); negative value means "forever"
#           inFileName:     the name of an existing input file (defaults to
#                           /etc/passwd)
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
        inFileName = sys.argv[2]
    else:
        inFileName = "/etc/passwd"

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
    clientName = "example6client"
    clientPassword = "clientPassword"
    nameSpaceName = "example6nameSpace"
    nameSpacePassword = "asdf"
    ownerName = "example6owner"
    ownerPassword = "owner"
    groupName = "example6group"
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
    # Set the file's name in Celeste to be the leaf name part of the input
    # file name.
    #
    fileName = inFileName.split("/")[-1]

    #
    # Set up CelesteSh-level access to Celeste.
    #
    sh = Celestesh(clientName, clientPassword,
        replicationParams=replicationParams)
    sh.verbose = True

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
    # Make the client credential's contents and metadata be available in the
    # local file system.
    #
    sh.readCredential(clientName, "/tmp/cred-d", "/tmp/cred-m")

    #
    # Ingest the input file if necessary.
    #
    if not sh.fileExists(nameSpaceName, fileName):
        sh.createFile(nameSpaceName, nameSpacePassword, fileName,
            ownerName, groupName, deleteToken,
            bObjectSize=blockSize,
            timeToLive=timeToLive,
            writesMustBeSigned=signWrites)

    #
    # Check the file's length.  If it's zero, write its contents.
    #
    attrDict = sh.getFileAttributes(nameSpaceName, fileName)
    fileLength = int(attrDict["Celeste.FileSize"])
    if fileLength == 0:
        sh.writeFile(nameSpaceName, fileName, 0, inFileName)

    #
    # Now that the file's been ingested, repeatedly read it and verify that
    # what we've read is the same as the original.
    #
    count = 0
    if repetitions < 0:
        while True:
            count += 1
            (rv, output) = readAndCompare(sh, nameSpaceName, fileName)
            if rv != 0:
                print "after %s repetitions, failed with '%s'" % (
                    count, output)
                sys.exit(rv)
    else:
        while count < repetitions:
            count += 1
            (rv, output) = readAndCompare(sh, nameSpaceName, fileName)
            if rv != 0:
                print "after %s repetitions, failed with '%s'" % (
                    count, output)
                sys.exit(rv)
    sys.exit(0)
