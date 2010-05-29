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

import os
from subprocess import Popen, PIPE
import sys
import time

from jvm import Jvm
from properties import Properties
from util import getCelesteHome
from util import IllegalArgumentException

class Celestesh:
    """ A class for interacting directly with the Celeste File Store.

        The file system layer does not intervene.
    """

    defaultReplicationParams = \
        "AObject.Replication.Store=3;" \
        "VObject.Replication.Store=3;" \
        "BObject.Replication.Store=3"

    def __init__(self,
            credentialName, credentialPassword,
            address="127.0.0.1",
            port=14000,
            jvmopts=["-server"],
            replicationParams=defaultReplicationParams):
        """ Initializes a Celestesh instance.

            The credentialName and credentialPassword arguments together
            specify the credential that will be used to authenticate requests
            made to Celeste through the other methods of this class.  This
            credential may be changed to another with the
            setAccessorCredentialAndPassword() method.

            The address and port arguments designate the IP address and port
            number of the client interface of some node in the Celeste
            confederation of interest.

            The jvmopts argument gives a list of JVM-specific arguments (as
            documented in java(1)) for the Jvm instance that will execute this
            instance's methods.  This argument defaults to ["-server"]; if
            overridden and if "-server" is to remain as a component, it must
            be given as the first component.

            The replicationParams argument gives the replication parameters to
            be used in subsequent method invocations that don't explicitly
            override them.
        """

        self.setAccessorCredentialAndPassword(
            credentialName, credentialPassword)
        self.setCelesteAddress(address, port)
        self.setReplicationParams(replicationParams)

        self.elapsedTime = 0
        self.verbose = False

        #
        # Set up a JVM instance that's prepared to invoke celeste.jar.
        #
        # Note that the jararg assignment depends on the detailed layout of
        # the Celeste distribution.
        #
        jararg = ["-cp", getCelesteHome() + "/dist/celeste.jar", "sunlabs.celeste.client.application.CelesteSh"]
        opts = jvmopts + jararg
        self.jvm = Jvm(opts)

    #
    # Accessors for state information.
    #

    def getCelesteAddress(self):
        """ Returns the (hostname, port) pair used to communicate with Celeste.
        """
        addressPort = self.celesteAddress[1]
        (address, portString) = addressPort.split(":")
        return (address, int(portString))

    def setCelesteAddress(self, address, port):
        """ Sets the address used to communicate with Celeste.
            This address consists of a hostname, port number pair.
        """
        self.celesteAddress = ["--celeste-address", "%s:%s" % (address, port)]

    def getAccessorCredentialName(self):
        """ Returns the name of the credential used to authenticate requests
            to Celeste.
        """
        return self.accessorCredentialName

    def setAccessorCredentialAndPassword(self,
            credentialName, credentialPassword):
        """ Sets the credential used to authenticate requests to Celeste.
        """
        self.accessorCredentialName = credentialName
        self._accessorCredentialPassword = credentialPassword

    def getReplicationParams(self):
        """ Gets the replication parameters to be used when not overridden by
            explicit specification in a method invocation.
        """
        return self.replicationParams

    def setReplicationParams(self, replicationParams):
        """ Sets the replication parameters to be used when not overridden by
            explicit specification in a method invocation.
        """
        self.replicationParams = replicationParams

    #
    # Machinery for building up and sending requests to Celeste.
    #

    def cmdPrelude(self):
        """ Build a sequence containing the common prefix for all jvm
            invocations that this class's public methods will issue.
        """
        #
        # In contrast to celestefs.py, there's not a lot here -- the Java
        # CelesteSh class accepts very little common information in the form
        # of options, which forces each command to place things in
        # command-specific locations and that, in turn, precludes factoring
        # them out here.
        #
        return self.celesteAddress

    def execute(self, command):
        """ Instruct the JVM to execute the given command, returning its result.
        """
        cmd = self.cmdPrelude() + command
        self.jvm.verbose = self.verbose
        startTime = time.time()
        result = self.jvm.execute(cmd)
        self.elapsedTime = time.time() - startTime
        return result

    #
    # The commands themselves.
    #

    def createFile(self, nameSpace, nameSpacePassword,
            fileId, ownerId, groupId, deleteToken,
            replicationParams=None,
            bObjectSize=1024 * 1024 * 8,
            timeToLive=86400 * 100,
            writesMustBeSigned=True):
        """ Create a named file within a specified name space, owned by the
            supplied owner and group.
        """

        if replicationParams == None:
            replicationParams = self.replicationParams
        options = []
        if not writesMustBeSigned:
            options += ["--unsigned-writes"]
        return self.execute(["create-file"] + options + [
            self.accessorCredentialName, self._accessorCredentialPassword,
            nameSpace, nameSpacePassword,
            fileId, ownerId, groupId,
            deleteToken, replicationParams,
            str(bObjectSize), str(timeToLive)])

    def deleteFile(self, nameSpace, fileId,
            deleteToken, timeToLive):
        """ Delete an existing file.

            The timeToLive parameter dictates how long Celeste should remember
            that the file formerly existed but has been deleted.
        """
        return self.execute(["delete-file",
            self.accessorCredentialName, self._accessorCredentialPassword,
            nameSpace, fileId, deleteToken,
            str(timeToLive)])

    def inspectFile(self,
            nameSpace, fileId,
            clientMetadataFile, celesteMetadataFile,
            vObjectId="null"):
        """ Obtain the user metadata and Celeste metadata for the specified
            file, storing them into clientMetadataFile and celesteMetadataFile
            respectively.
        """
        return self.execute(["inspect-file",
            self.accessorCredentialName, self._accessorCredentialPassword,
            nameSpace, fileId, vObjectId,
            clientMetadataFile, celesteMetadataFile])

    def getFileAttributes(self,
            nameSpace, fileId,
            keyList=None,
            vObjectId="null"):
        """ Obtain the items of Celeste metadata designated by the contents
            of keyList, returning them as a dictionary.

            If keyList is None, include all Celeste metadata attributes in
            the result.
        """
        #
        # If keyList is non-empty, replace it with a single element flattened
        # version of itself, thereby putting it into the form that the
        # CelesteSh java class expects.
        #
        if keyList != None:
            keyList = [ ",".join(keyList) ]
        else:
            keyList = []
        #
        # Set up and invoke the command in a way that provides access to its
        # stdout stream, which is needed for converting the command's output
        # to dictionary form.
        #
        cmd = self.cmdPrelude() +  ["get-file-attributes",
            self.accessorCredentialName, self._accessorCredentialPassword,
            nameSpace, fileId, vObjectId] + keyList
        self.jvm.verbose = self.verbose
        startTime = time.time()
        p = self.jvm.invoke(cmd, stdin=PIPE, stdout=PIPE)
        return Properties(p.stdout).dict

#    def keystoreCredential(self, keystoreFileName, keystorePassword,
#           erasureCoder="ErasureCodeIdentity/1"):
#        ""
#        return self.celestesh("keystore-credential %s %s %s %s %s %s %s" %
#           (keystoreFileName, keystorePassword, erasureCoder))

    def newNameSpace(self, nameSpaceName, nameSpacePassword,
            replicationParams=None):
        """ Create a new name space in the Celeste system.
        """
        if replicationParams == None:
            replicationParams = self.replicationParams
        return self.execute(["new-credential",
            nameSpaceName, nameSpacePassword, replicationParams])

    def newCredential(self, credentialName, credentialPassword,
            replicationParams=None):
        """ Create a new Celeste credential.
        """
        if replicationParams == None:
            replicationParams = self.replicationParams
        return self.execute(["new-credential",
            credentialName, credentialPassword, replicationParams])

    def readFile(self, nameSpace, fileId, offset=0, length=-1, stdout=PIPE):
        """ Initiate a subprocess to read length bytes from the file denoted
            by nameSpace and fileId starting at the given offset, writing the
            result to the stream named by stdout.  If length is -1, read the
            entire file.

            Return a Popen object representing the subprocess.
        """
        cmd = self.cmdPrelude() + ["read-file",
            self.accessorCredentialName, self._accessorCredentialPassword,
            nameSpace, fileId,
            str(offset), str(length)]
        self.jvm.verbose = self.verbose
        startTime = time.time()
        return self.jvm.invoke(cmd, stdin=PIPE, stdout=stdout)

    def readFileVersion(self, nameSpace, fileId, vObjectId,
            offset=0, length=-1, stdout=PIPE):
        """ Initiate a subprocess to read length bytes from version vObjectId
            of the file denoted by nameSpace and fileId starting at the given
            offset, writing the result to the stream named by stdout.  If
            length is -1, read the entire file.

            Return a Popen object representing the subprocess.
        """
        cmd = self.cmdPrelude() + ["read-file-version",
            self.accessorCredentialName, self._accessorCredentialPassword,
            nameSpace, fileId, vObjectId,
            str(offset), str(length)]
        return self.jvm.invoke(cmd, stdin=PIPE, stdout=stdout)

    def readCredential(self, credentialId, contents=None, metadata=None):
        """ Read a raw credential.

            Store the credential's body into the file named by contents and
            its metadata into the file named by metadata.  Either can be
            omitted, but if metadata is supplied, contents must be as well.
        """

        #
        # The CelesteSh Java class's implementation of this command allows
        # arguments to be omitted, but only at the very end.
        #
        if metadata != None and contents == None:
            raise IllegalArgumentException(
                "metadata argument given without contents argument")

        #
        # Convert the keyword arguments to lists.
        #
        if contents == None:
            contents = []
        else:
            contents = [contents]
        if metadata == None:
            metadata = []
        else:
            metadata = [metadata]

        return self.execute(["read-credential",
            credentialId] + contents + metadata)

    def setFileLength(self, nameSpace, fileId, length):
        """ Set the length of an existing data file.
        """
        return self.execute(["set-file-length",
            self.accessorCredentialName, self._accessorCredentialPassword,
            nameSpace, fileId, str(length)])

    def setFileOwnerAndGroup(self, nameSpace, fileId, ownerId, groupId):
        """ Set the owner and group of an existing file.
        """
        return self.execute(["set-file-owner-and-group",
            self.accessorCredentialName, self._accessorCredentialPassword,
            nameSpace, fileId, ownerId, groupId])

    def writeFile(self, nameSpace, fileId, start, inputFileName):
        """ Write data to an existing data file.
        """
        return self.execute(["write",
            self.accessorCredentialName, self._accessorCredentialPassword,
            nameSpace, fileId,
            str(start), inputFileName])

    def credentialExists(self, credentialName):
        """ Returns True if credentialName denotes an extant credential and
            False otherwise.
        """

        cmd = self.cmdPrelude() + ["test", "-c", credentialName]
        self.jvm.verbose = self.verbose
        if self.jvm.call(cmd) == 0:
            return True
        else:
            return False

    def nameSpaceExists(self, nameSpaceName):
        """ Returns True if nameSpaceName denotes an extant name space and
            False otherwise.
        """

        cmd = self.cmdPrelude() + ["test", "-c", nameSpaceName]
        self.jvm.verbose = self.verbose
        if self.jvm.call(cmd) == 0:
            return True
        else:
            return False

    def fileExists(self, nameSpaceName, fileName):
        """ Returns True if fileName denotes an extant file and False
            otherwise.
        """

        cmd = self.cmdPrelude() + ["test", "-e",
            self.accessorCredentialName, self._accessorCredentialPassword,
            nameSpaceName, fileName]
        self.jvm.verbose = self.verbose
        if self.jvm.call(cmd) == 0:
            return True
        else:
            return False

#
# Test the Celestesh class's methods.
#
if __name__ == "__main__":
    replicationParams = Celestesh.defaultReplicationParams
    nameSpaceName="celestesh.py"
    nameSpacePassword="nspw"
    requestor="celesteshpy-user"
    password="myPass"
    ownerName="celesteshpy-Owner"
    ownerPassword="ownerpw"
    groupName="celesteshpy-Group"
    groupPassword="grouppw"
    fileName="celesteshpy-file"
    deleteToken="celesteshpy-deleteMe"
    blockSize= 8 * 1024 * 1024
    timeToLive=86400

    celestesh = Celestesh(requestor, password, jvmopts=["-ea"])
    celestesh.verbose = True

    if not celestesh.credentialExists(nameSpaceName):
        celestesh.newNameSpace(nameSpaceName, nameSpacePassword)
    if not celestesh.credentialExists(nameSpaceName):
        print "creation of name space %s failed" % nameSpaceName
        sys.exit(1)
    celestesh.newCredential(ownerName, ownerPassword)
    celestesh.newCredential(groupName, groupPassword)
    celestesh.newCredential(requestor, password)

    if not celestesh.fileExists(nameSpaceName, fileName):
        celestesh.createFile(nameSpaceName, nameSpacePassword, fileName,
            ownerName, groupName, deleteToken, replicationParams,
            blockSize, timeToLive)
    if not celestesh.fileExists(nameSpaceName, fileName):
        print "creation of file %s in name space %s failed" % (
            fileName, nameSpaceName)
        sys.exit(1)

    celestesh.setFileOwnerAndGroup(nameSpaceName, fileName,
        ownerName, groupName)

    dataFile = "/tmp/celesteshpy1.txt"
    f = open(dataFile, "w")
    f.write("Hello World\n")
    f.close();

    celestesh.writeFile(nameSpaceName, fileName, 0, dataFile)
    data = celestesh.readFile(nameSpaceName, fileName)
    print data.stdout.read()

    dataFile = "/tmp/celesteshpy2.txt"
    f = open(dataFile, "w")
    f.write("\n")
    f.close();
    celestesh.writeFile(nameSpaceName, fileName, 5, dataFile)

    data = celestesh.readFile(nameSpaceName, fileName)
    print data.stdout.read()

    celestesh.setFileLength(nameSpaceName, fileName, 6)
    data = celestesh.readFile(nameSpaceName, fileName)
    print data.stdout.read()

    celestesh.inspectFile(nameSpaceName, fileName,
        "/tmp/celesteshpyClientMD", "/tmp/celesteshpyCelesteMD")

    print celestesh.getFileAttributes(nameSpaceName, fileName)

    data = celestesh.readFile(nameSpaceName, fileName)
    print data.stdout.read()

    celestesh.setFileLength(nameSpaceName, fileName, 10)
    data = celestesh.readFile(nameSpaceName, fileName)
    print data.stdout.read()

    dataFile = "/tmp/celesteshpy3.txt"
    f = open(dataFile, "w")
    f.write("Hello Again\n")
    f.close();

    celestesh.writeFile(nameSpaceName, fileName, 0, "/tmp/celesteshpy3.txt")
    data = celestesh.readFile(nameSpaceName, fileName)
    print data.stdout.read()

    dataFile = "/tmp/celesteshpy4.txt"
    f = open(dataFile, "w")
    f.write("Do you like my hat?\n")
    f.close();

    celestesh.writeFile(nameSpaceName, fileName, 0, dataFile)
    data = celestesh.readFile(nameSpaceName, fileName)
    print data.stdout.read()

    celestesh.deleteFile(nameSpaceName, fileName, deleteToken, 900)

    data = celestesh.readFile(nameSpaceName, fileName)
    data.stdout.read()

    dataFile = "/tmp/celesteshpy5.txt"
    f = open(dataFile, "w")
    f.write("No, I do not.\n")
    f.close();

    celestesh.writeFile(nameSpaceName, fileName, 0, dataFile)
