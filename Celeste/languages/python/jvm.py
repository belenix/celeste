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
# jvm:  A class for execing Java Virtual Machines.  Tries to abstract away
#       some of the platform-specific details of invoking Java.
#

from errno import ENOEXEC
import os
from os import X_OK
from subprocess import Popen, PIPE
import sys
from sys import stderr

class Jvm:
    """ A class for execing Java Virtual Machines.  Tries to abstract away
        some of the platform-specific details of invoking Java.

        In addition to its primary behavior of mediating JVM invocations, the
        class provides support for probing their execution via DTrace.  Each
        instance has a "dtrace" attribute whose value defaults to the empty
        list.  If this attribute is set to a non-empty list, its elements are
        interpreted as arguments to be supplied to a dtrace(1M) invocation and
        subsequent JVM invocations are run under DTrace control.  For example,
        suppose we have:

            myJvm = Jvm(["-cp", "/my/class/path"])
            myJvm.dtrace = ["-s", "/my/script.d"]
            myJvm.execute([".org.me.MyClass"])

        This sequence will have the same effect as executing the following
        command line:

            dtrace -c "java -cp /my/class/path .org.me.MyClass" -s /my/script.d

        (Note that DTrace requires elevated privilege to run, so setting the
        dtrace attribute may require changes elsewhere to supply the requisite
        privilege.)

    """

    def __init__(self, jvmopts=None):
        """ Creates a new Jvm instance.

            The jvmopts argument gives a list of JVM-specific arguments (as
            documented in java(1)) for each invocation of java(1).  This
            argument defaults to ["-server"] on systems that support that flag
            and to the empty list otherwise.  If overridden and if "-server"
            is to remain as a component, it must be given as the first
            component.
            
            Although its primary purpose is to set arguments for the JVM
            itself, the jvmopts argument can also be used to factor out other
            arguments that are to be common to each JVM instantiated through
            this instance.
        """

        #
        # Try to account for differences between Windows and Unix-heritage
        # OSes.
        #
        if os.name == "nt":
            javaprog = "java.exe"
            serveropt = []
        else:
            javaprog = "java"
            serveropt = ["-server"]

        if jvmopts == None:
            jvmopts = serveropt

        #
        # Find where the java utility is located.  If it's not specified
        # explicitly in the environment, assume that it lives in
        # "/usr/bin/java", which is true at least on Solaris and MacOS X.
        # (However, in both cases, that path might not refer to 1.6 or later,
        # which is what we need.)
        #
        javahome = ""
        try:
            javahome = os.environ["JAVAHOME"]
        except KeyError, e:
            pass
        if javahome == "":
            javahome = "/usr"
        javautil = os.path.join(javahome, "bin", javaprog)
        #
        # Verify that the java location determined above refers to an
        # executable file.
        #
        if not os.access(javautil, X_OK):
            raise EnvironmentError(ENOEXEC, "not executable", javautil)
        self.javautil = javautil

        self.jvmopts = jvmopts
        self.verbose = False

        self.dtrace = []

    def execute(self, args, stderr=None):
        """ Execute the command given by args, gathering and returning as a
            string anything it writes to its standard output.  If the command
            attempts to read from its standard input, the results are
            undefined.

            Args is expected to be a sequence of strings that form the tokens
            of a java(1) command line argument, for example
                ["-jar", "jarfilepath.jar", "arg-for-main-method"]

            The stderr keyword argument is passed to the Popen constructor and
            behaves as documented there.

            If this instance's verbose attribute has been set to True, the
            fully constructed command line will be echoed to stderr before it
            is executed.
        """

        pin, pout = self.popen2(args, stderr=stderr)
        return pout.read()

    def popen2(self, args, stderr=None):
        """ Execute the command given by args as a subprocess, returning the
            file objects (child_stdin, child_stdout), as in os.popen2().

            Args is expected to be a sequence of strings that form the tokens
            of a java(1) command line argument, for example
                ["-jar", "jarfilepath.jar", "arg-for-main-method"]

            The stderr keyword argument is passed to the Popen constructor and
            behaves as documented there.

            If this instance's verbose attribute has been set to True, the
            fully constructed command line will be echoed to stderr before it
            is executed.
        """

        p = self.invoke(args, stdin=PIPE, stdout=PIPE, stderr=stderr)
        return (p.stdin, p.stdout)

    #
    # XXX:  Ideally, stderr should be discarded as well, but doing so prevents
    #       self.verbose from acting as documented.
    #
    def call(self, args, stderr=None):
        """ Execute the command given by args as a subprocess, returning its
            exit code.  The command's output is discarded.

            Args is expected to be a sequence of strings that form the tokens
            of a java(1) command line argument, for example
                ["-jar", "jarfilepath.jar", "arg-for-main-method"]

            The stderr keyword argument is passed to the Popen constructor and
            behaves as documented there.

            If this instance's verbose attribute has been set to True, the
            fully constructed command line will be echoed to stderr before it
            is executed.
        """

        devnull = open(os.devnull, "w")
        p = self.invoke(args, stdout=devnull, stderr=stderr)
        rv = p.wait()
        devnull.close()
        return rv

    def invoke(self, args, stdin=None, stdout=None, stderr=None):
        """ Execute the command given by args as a subprocess, returning a
            Popen object representing the resulting subprocess.

            Args is expected to be a sequence of strings that form the tokens
            of a java(1) command line argument, for example
                ["-jar", "jarfilepath.jar", "arg-for-main-method"]

            The stdin, stdout, and stderr keyword arguments are passed to the
            Popen constructor and behave as documented there.

            If this instance's verbose attribute has been set to True, the
            fully constructed command line will be echoed to stderr before it
            is executed.
        """

        #
        # As a convenience, allow jmvopts to be a string.
        #
        if isinstance(self.jvmopts, str):
            cmdpfx = [self.javautil, self.jvmopts]
        else:
            cmdpfx = [self.javautil] + self.jvmopts
        cmd = cmdpfx + args

        #
        # If dtrace is to be used for this invocation, set it up.
        #
        # XXX:  If the arguments to the JVM invocation contain embedded
        #       spaces, those spaces will become indistinguishable from
        #       inter-argument spaces.  I don't know of any way to avoid this
        #       problem.
        #
        if len(self.dtrace) != 0:
            javacmd = self.flatten(cmd)
            cmd = ["/usr/sbin/dtrace", "-c", javacmd] + self.dtrace

        if self.verbose:
            print >> stderr, cmd
        return Popen(cmd, shell=False,
            stdin=stdin, stdout=stdout, stderr=stderr)

    def flatten(self, strings, separator=" "):
        """ Flatten the list of strings into a single string, separating the
            list elements with the separator string.
        """
        return separator.join(strings)
