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

from subprocess import Popen, PIPE
import time

from jvm import Jvm
from properties import Properties
from util import getCelesteHome

#
# XXX:  Need to understand why the PathName code within Celeste isn't
#       transforming paths like "//ns/stuff" into "/ns/stuff".  Currently, the
#       former path is being treated as if it specified an empty name space
#       identifier.
#

#
# XXX:  Need to add doc strings for all of the public methods.  Need to decide
#       which methods (and fields) are private and rename them accordingly.
#
class Celestefs:
    """ A class for interacting with the file system layer built on top of the
        Celeste file store.

        Credentials and name spaces:

            The constructor takes pair of arguments that denote a client
            credential and its corresponding pasword.  This credential is used
            to authorize all Celeste operations made through the resulting
            Celestefs class instance.

            The mkfs() method takes arguments denoting a distinct credential
            and password pair.  The class uses this credential as a name space
            identifier that distinguishes the resulting file system from
            others within the Celeste confederation.

            These two credentials can be identical, but need not be.

        Path names:

            Many of the methods take path name arguments.  Each path name must
            be presented in absolute form, as:
                /<name space>/<remaining path relative to the name space>
            That is, all paths must start with a slash, followed immediately
            by a name space identifier, followed (if there are additional path
            components) by another slash.

            The path "/nameSpace" denotes the root directory of the file
            system associated with "nameSpace".

    """

    defaultBufferSize = 32 * 1024 * 1024

    defaultReplicationParams = \
        "AObject.Replication.Store=2;" \
        "VObject.Replication.Store=2;" \
        "BObject.Replication.Store=2"

    def __init__(self, clientName, clientPassword,
            address="127.0.0.1",
            port=14000,
            replicationParams=defaultReplicationParams,
            timeout=None,
            jvmopts=["-server"]):
        """ Initializes a Celestefs instance.

            The clientName and clientPassword arguments specify the credential
            that will be used to authenticate operations invoked on this
            celestefs instance.

            The address and port arguments designate the IP address and port
            number of the client interface of some node in the Celeste
            confederation of interest.

            The replicationParams argument specifies the storage redundancy
            that files created through this instance are to have.

            The timeout argument gives the number of seconds to wait on a
            socket i/o operation before aborting it as having timed out.  A
            value of 0 disables the timeout feature.  Not specifying this
            argument selects a default value chosen by the underlying
            implementation.

            The jvmopts argument gives a list of JVM-specific arguments (as
            documented in java(1)) for the Jvm instance that will execute this
            instance's methods.  This argument defaults to ["-server"]; if
            overridden and if "-server" is to remain as a component, it must
            be given as the first component.
        """

        self.celesteAddress = ["--celeste-address", "%s:%s" % (address, port)]

        self.timeoutOption = []
        if timeout != None:
            #
            # Convert timeout to an int and back to a string to ensure that a
            # num
            #
            timeoutValue = int(timeout)
            if timeout < 0:
                print >> stderr, "timeout value mut be non-negative"
                sys.exit(1)
            self.timeoutOption = [ "--timeout", str(timeoutValue) ]

        #
        # Set up a JVM instance that's prepared to invoke celeste.jar.
        #
        # Note that the jararg assignment depends on the detailed layout of
        # the Celeste distribution.
        #
        jararg = ["-cp", getCelesteHome() + "/dist/celeste.jar", "sunlabs.celeste.client.application.CelesteFs"]
        opts = jvmopts + jararg
        self.jvm = Jvm(opts)

        #
        # XXX:  Should these be public so that callers can change them on the
        #       fly?
        #
        self.clientName = clientName
        self.clientPassword = clientPassword
        self.replicationParams = replicationParams

        self.elapsedTime = 0
        self.verbose = False

    def cmdPrelude(self):
        """ Build a sequence containing the common prefix for all jvm
            invocations that this class's public methods will issue.
        """

        return self.timeoutOption + [ "--id", self.clientName,
            "--password", self.clientPassword ]

    def execute(self, command):
        """ Instruct the JVM to execute the given command, returning its result.
        """

        cmd = self.cmdPrelude() + command
        self.jvm.verbose = self.verbose
        startTime = time.time()
        result = self.jvm.execute(cmd)
        self.elapsedTime = time.time() - startTime
        return result

    def call(self, command, stderr=None):
        """ Instruct the JVM to execute command, returning its exit status.

            The stderr keyword argument is passed through to the call method
            of this instance's jvm attribute and behaves as documented there.
        """

        cmd = self.cmdPrelude() + command
        self.jvm.verbose = self.verbose
        startTime = time.time()
        result = self.jvm.call(cmd, stderr=stderr)
        self.elapsedTime = time.time() - startTime
        return result

    #
    # XXX:  Really ought to throw an exception if creation fails.
    #
    def mkfs(self, nameSpace, nameSpacePassword, attrs=None):
        """ Creates a file system and associates it with nameSpace.

            If the attrs argument is specified, it should refer to a
            dictionary with string keys and values whose contents are
            attributes to be applied to the file system.

            Returns True if the file system was created successfully and False
            otherwise.
        """

        #
        # Turn the attrs dictionary into a sequence of ["--attrs", value]
        # pairs to be added to be argument list given to execute().
        #
        options = []
        if attrs != None:
            for key, value in attrs.iteritems():
                options += ["--attr", "%s=%s" % (key, value)]

        cmd = ["mkfs"] + options + [nameSpace, nameSpacePassword]
        if self.call(cmd) == 0:
            return True
        else:
            return False

    def mkdir(self, path, options=None, attrs=None, props=None):
        """ Creates the directory denoted by path.

            If the options argument is ["-p"], mkdir creates intermediate
            directories as needed.  In this case the attrs and props arguments
            apply to all such intermediate directories, as well as to the one
            denoted by path.

            If the attrs argument is specified, it should refer to a
            dictionary with string keys and values whose contents are
            attributes to be applied to the directory's initial version.
            (Only keys whose names match those in the Java
            sunlabs.celeste.client.filesystem.FileAttributes.creationSet will
            be used.)

            If the props argument is specified, it should refer to a
            dictionary with string keys and values whose contents are
            properties to be applied to the directory's initial version.
            (Properties are completely user-defined, in contrast to the
            attributes described above.)
        """

        #
        # Turn the options argument into a list.
        #
        # XXX:  Ought to check it for validity first.
        #
        if options == None:
            options = []
        else:
            options = [options]

        #
        # Turn the attrs dictionary into a sequence of ["--attr", value]
        # pairs to be added to be argument list given to execute().
        #
        attributes = []
        if attrs != None:
            for key, value in attrs.iteritems():
                attributes += ["--attr", "%s=%s" % (key, value)]

        #
        # Convert the props dictionary into a list of ["--prop, value] pairs.
        #
        properties = []
        if props != None:
            for key, value in props.iteritems():
                properties += ["--prop", "%s=%s" % (key, value)]

        return self.execute(["mkdir"] + options + attributes + properties + 
            [path])

    def mkid(self):
        """ Creates the credential denoted by the clientName and
            clientPassword arguments to the Celestefs constructor.
        """

        return self.execute(["mkid"])

    def create(self, path, contentType="", attrs=None, props=None):
        """ Creates a new file at the location designated by path.

            If the contentType argument is supplied, it should name a MIME
            file type.  If not supplied, it defaults to the file system's
            default file type, which is "application/octet-stream".  This
            argument overrides any ContentType entry that might be supplied in
            the attrs dictionary.

            If the attrs argument is specified, it should refer to a
            dictionary with string keys and values whose contents are
            attributes to be applied to the file's initial version.  (Only
            keys whose names match those in the Java
            sunlabs.celeste.client.filesystem.FileAttributes.creationSet will
            be used.)

            If the props argument is specified, it should refer to a
            dictionary with string keys and values whose contents are
            properties to be applied to the file's initial version.
            (Properties are completely user-defined, in contrast to the
            attributes described above.)
        """

        #
        # Turn the attrs dictionary into a sequence of ["--attr", value]
        # pairs to be added to be argument list given to execute().
        #
        options = []
        if attrs != None:
            for key, value in attrs.iteritems():
                options += ["--attr", "%s=%s" % (key, value)]
        
        if contentType != "":
            options += ["--attr", "ContentType=%s" % contentType]

        #
        # Convert the props dictionary into a list of ["--prop, value] pairs.
        #
        properties = []
        if props != None:
            for key, value in props.iteritems():
                options += ["--prop", "%s=%s" % (key, value)]

        return self.execute(["create"] + options + properties + [path])

    def pread(self, path, offset=0, length=-1, stdout=PIPE):
        """ Initiate a subprocess to read length bytes from the file denoted
            by path starting at the given offset, writing the result to the
            stream named by stdout.  If length is -1, read the entire file.

            Return a Popen object representing the subprocess.
        """

        cmd = self.cmdPrelude() + self.celesteAddress + [
            "pread", path, "%d" % offset, "%d" % length]
        self.jvm.verbose = self.verbose
        startTime = time.time()
        return self.jvm.invoke(cmd, stdin=PIPE, stdout=stdout)

    def pwrite(self, path, offset=0, stdin=PIPE,
            bufferSize=defaultBufferSize):
        """ Initiate a subprocess to write from stdin into the Celeste file
            denoted by path starting at the given offset.

            Return a Popen object representing the subprocess.

            For the best performance, bufferSize should be the largest multiple
            of the Celeste file's block size that doesn't exceed the capacity
            of the underlying JVM instance that does the actual copy; with
            default heap parameters and and reasonable system memory capacity,
            32MB should be a conservatively safe size.
        """

        cmd = self.cmdPrelude() + self.celesteAddress + ["pwrite",
            "--buffer-size", "%d" % bufferSize,
            path, "%d" % offset]
        self.jvm.verbose = self.verbose
        startTime = time.time()
        return self.jvm.invoke(cmd, stdin=stdin, stdout=PIPE)

    def write(self, path, localFileName, offset=0,
            bufferSize=defaultBufferSize):
        """ Copy the contents of the file in the local file system named by
            localFileName to the Celeste file named by path.

            The first position written in the Celeste file is given by offset.

            The copy proceeds as a series of writes, with each write, except
            for possibly the last, writing bufferSize bytes.

            For the best performance, bufferSize should be the largest multiple
            of the Celeste file's block size that doesn't exceed the capacity
            of the underlying JVM instance that does the actual copy; with
            default heap parameters and and reasonable system memory capacity,
            32MB should be a conservatively safe size.

            Performance will suffer if offset is not a multiple of the Celeste
            file's block size.
        """

        cmd = ["write", "--buffer-size", "%d" % bufferSize, localFileName,
            "%d" % offset, path]
        if self.call(cmd) == 0:
            return True
        else:
            return False

    def remove(self, path):
        """ Removes the file or directory located at path.

            Makes no checks for directory emptiness; use with care!
        """

        return self.execute(["rm", path])

    def rename(self, fromPath, toPath):
        return self.execute(["rename", fromPath, toPath])

    def setLength(self, path, length):
        return self.execute(["set-length", path, "%d" % length])

    def getAttributes(self, path, attrNames=None):
        """ Returns a dictionary of attributes associated with the file at path.

            If attrNames is left with its default value of None, the resulting
            dictionary will have entries for all of the file's attributes.
            Otherwise, attrNames should be a list whose entries name the
            attributes to be retrieved.  If an attribute in this list does not
            exist, it will be omitted from the dictionary.

            Attributes are metadata associated with a file that the file
            system implementation maintains and consults as part of providing the
            file's behavior.
        """
        if attrNames == None:
            attrNames = []

        #
        # Convert attrNames into a sequence of ["--attr", <name>] options.
        #
        options = []
        for name in attrNames:
            options += ["--attr", name]

        cmd = self.cmdPrelude() + self.celesteAddress + [
            "getAttributes"] + options + [path]
        self.jvm.verbose = self.verbose
        startTime = time.time()
        p =  self.jvm.invoke(cmd, stdin=PIPE, stdout=PIPE)
        return Properties(p.stdout).dict

    def getProperties(self, path, propNames=None):
        """ Returns a dictionary of properties associated with the file at path.

            If propNames is left with its default value of None, the resulting
            dictionary will have entries for all of the file's properties.
            Otherwise, propNames should be a list whose entries name the
            properties to be retrieved.  If a property in this list does not
            exist, it will be omitted from the dictionary.

            Properties are uninterpreted string data that the Celeste file
            system stores and retrieves on behalf of its users, but does not
            use itself for any purpose.
        """
        if propNames == None:
            propNames = []

        #
        # Convert attrNames into a sequence of ["--prop", <name>] options.
        #
        options = []
        for name in propNames:
            options += ["--prop", name]

        cmd = self.cmdPrelude() + self.celesteAddress + [
            "getProperties"] + options + [path]
        self.jvm.verbose = self.verbose
        startTime = time.time()
        p =  self.jvm.invoke(cmd, stdin=PIPE, stdout=PIPE)
        return Properties(p.stdout).dict

    def stat(self, path, options=None):
        if options == None:
            options = []
        return self.execute(["ls"] + options + [path])

    def readdir(self, path, options=None):
        if options == None:
            options = []
        return self.execute(["readdir"] + options + [path])

    def test(self, path, options=["-e"]):
        result = self.execute(["--verbose", "test"] + options + [path])
        if (result == "true"):
            return True
        return False

    def credentialExists(self, credentialName):
        """ Returns True if credentialName denotes an extant credential and
            False otherwise.
        """

        cmd = self.celesteAddress + ["test", "-c", credentialName]
        if self.call(cmd) == 0:
            return True
        else:
            return False

    def fileSystemExists(self, fsName):
        """ Returns True if fsName denotes an extant file system and False
            otherwise.
        """

        cmd = self.celesteAddress + ["test", "-f", fsName]
        if self.call(cmd) == 0:
            return True
        else:
            return False

    def fileExists(self, fileName):
        """ Returns True if fileName denotes an extant file and False
            otherwise.
        """

        cmd = self.celesteAddress + ["test", "-e", fileName]
        if self.call(cmd) == 0:
            return True
        else:
            return False

#
# Test the Celestefs class's methods.
#
if __name__ == "__main__":
    import sys

    myId = "celestefspy.py"
    myPassword = "plugh"
    replicationParams = \
        "AObject.Replication.Store=2;" \
        "VObject.Replication.Store=2;" \
        "BObject.Replication.Store=2"
    replicationAttr = { "--attr" : replicationParams }

    celestefs = Celestefs(myId, myPassword, "127.0.0.1", 14000)
    celestefs.verbose = True

    fileSystemName="celestefspy-fs"
    fileSystemPassword="fspw"
    fileSystemPrefix = "/" + fileSystemName
    ownerName="celestefspy-Owner"
    ownerPassword="owner"
    groupName="celestefspy-Group"
    groupPassword="group"
    #deleteToken="deleteMe"
    #blockSize=8 * 1024 * 1024
    #timeToLive=86400

    celestefs.mkid()
    celestefs.mkfs(fileSystemName, fileSystemPassword, attrs=replicationAttr)
    print celestefs.stat(fileSystemPrefix, ["-l"])
    print celestefs.readdir(fileSystemPrefix)
    celestefs.create(fileSystemPrefix + "/fubar", attrs=replicationAttr)
    p = celestefs.pwrite(fileSystemPrefix + "/fubar", 0)
    p.stdin.write("Hello World")
    p.stdin.close()
    print p.stdout.read()

    print celestefs.stat(fileSystemPrefix + "/fubar", ["-l"])
    p = celestefs.pread(fileSystemPrefix + "/fubar", 0, -1)
    print p.stdout.read()

    celestefs.setLength(fileSystemPrefix + "/fubar", 6)
    print celestefs.stat(fileSystemPrefix + "/fubar", ["-l"])
    p = celestefs.pread(fileSystemPrefix + "/fubar", 0, -1)
    print p.stdout.read()

    celestefs.mkdir(fileSystemPrefix + "/foo", attrs=replicationAttr)
    celestefs.create(fileSystemPrefix + "/foo/bar", attrs=replicationAttr)
    p = celestefs.pwrite(fileSystemPrefix + "/foo/bar", 0)
    p.stdin.write("Hello World")
    p.stdin.close()
    print p.stdout.read()

    p = celestefs.pread(fileSystemPrefix + "/foo/bar", 0, -1)
    print p.stdout.read()

    celestefs.remove(fileSystemPrefix + "/foo/bar")
    p = celestefs.pread(fileSystemPrefix + "/foo/bar", 0, -1)
    print p.stdout.read()
    print celestefs.test(fileSystemPrefix + "/foo/bar", ["-e"])
