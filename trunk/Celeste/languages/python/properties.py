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

from cStringIO import StringIO

class Properties:
    """ The Properties cless contains classes for creating dictionaries from
        stored Java Properties objects.

        It  contains translations into Python of key parts of the
        java.util.Properties implementaion.  However, it makes no attempt to
        handle general Unicode code points; that task is deferred to such time
        as we convert our Python code base to use Python 3.x.

        Note that the class only supports input of stored Java Properties
        objects; it does not support writing dictionaries into stored Java
        Properties form.
    """

    def __init__(self, inStream):
        """ Creates a Properties object from inStream.

            inStream must refer to a stored Java Properties object.

            The resulting object has an attribute named "dict" referring to a
            dictionary containing the attribute-value pairs obtained from
            inStream.
        """
        self.inStream = inStream
        self.dict = {}

        #
        # Set self.dict from inStream.
        #
        while True:
            line = self.readLine()
            if line == "" or line == None:
                break
            rawKey, rawVal = self.extractRawKeyAndValue(line)
            self.dict[self.unquote(rawKey)] = self.unquote(rawVal)

    #
    # XXX:  In places the code below follows the implementation of
    #       java.util.Properties too slavishly; it could undoubtedly be
    #       improved to use Python more effectively.
    #

    def readLine(self):
        """ Read and return a logical line of input.

            Respect the definition of line given in the javadoc comment for
            java.util.Properties.load().
        """
        outLine = StringIO()
        for physLine in self.inStream:
            #
            # An empty line means no more input.  Therefore, we must return
            # what we have.
            #
            if physLine == "":
                return outLine.getvalue()

            #
            # Trim off leading and trailing white space.  This includes the
            # line terminating character(s).
            #
            physLine = physLine.strip()
            #
            # Ignore blank lines.
            #
            if physLine == "":
                continue

            c = physLine[0]
            if c == '#' or c == '!':
                #
                # It's a comment line -- skip it.
                #
                continue

            #
            # Check the line's end to see whether it's continued onto another
            # line.  If it ends with an odd number of backslashes, it is.
            #
            backslashParity = 0
            endIndex = len(physLine) - 1
            index = endIndex
            while index >= 0 and physLine[index] == '\\':
                backslashParity = 1 - backslashParity
                index -= 1
            if backslashParity == 1:
                physLine = physLine[0:endIndex]
                outLine.write(physLine)
                continue

            #
            # We have a complete logical line.  Return it.
            #
            outLine.write(physLine)
            return outLine.getvalue()

    def extractRawKeyAndValue(self, line):
        #
        # We expect line to have been produced by readLine(), so that it
        # contains no leading or trailing white space and is not a comment
        # line.
        #
        limit = len(line)
        i = 0
        backslashParity = 0
        explicitSeparator = False
        while i < limit:
            #
            # Look for a key-value separator.
            #
            c = line[i]
            if (c == '=' or c == ':') and backslashParity == 0:
                #
                # The separator is explicit.
                #
                explicitSeparator = True
                rawKey = line[0:i]
                break
            elif (c == ' ' or c == '\t' or c == '\f') and backslashParity == 0:
                #
                # The separator is unescaped white space.
                #
                rawKey = line[0:i]
                break

            if c == '\\':
                backslashParity = 1 - backslashParity
            else:
                backslashParity = 0
            i += 1

        #
        # Trim the key and separator parts from line.  Toss out white space
        # following the separator as well.  If we explictly found a separator,
        # that leaves us with the value in raw form.  If we didn't, there
        # still might be one to be stripped out.
        #
        rawValue = line[i+1:].lstrip()
        if not explicitSeparator:
            line = rawValue
            limit = len(line)
            i = 0
            backslashParity = 0
            while i < limit:
                c = line[i]
                if (c == '=' or c == ':') and backslashParity == 0:
                    rawValue = line[i+1:].lstrip()
                    break

                if c == '\\':
                    backslashParity = 1 - backslashParity
                else:
                    backslashParity = 0
                i += 1

        return (rawKey, rawValue)

    def unquote(self, string):
        unquoted = StringIO()
        limit = len(string)
        i = 0
        while i < limit:
            c = string[i]
            if c != '\\':
                unquoted.write(c)
            else:
                i += 1
                #
                # Check for backslash at EOL.  For lack of a better answer,
                # transcribe it intact.
                #
                if i == limit:
                    unquoted.write(c)
                    break
                c = string[i]
                if c == 't':
                    unquoted.write('\t')
                elif c == 'r':
                    unquoted.write('\r')
                elif c == 'n':
                    unquoted.write('\n')
                elif c == 'f':
                    unquoted.write('\f')
                else:
                    unquoted.write(c)
            i += 1
        return unquoted.getvalue()

    def addToDict(self, key, value):
        self.dict[key] = value

if  __name__ == "__main__":
    f = open("/tmp/propfile")
    p = Properties(f)

    print p.dict
