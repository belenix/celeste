#!/usr/bin/env python
#
# Copyright 2008 Sun Microsystems, Inc. All Rights Reserved.
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
# Miscellaneous utility functions for use in building Celeste accessors
# written in Python
#

#
# getCelesteHome() (below) uses this import, but only to obtain its location
# in the file system name space.
#
import jvm

import os

class IllegalArgumentException(Exception):
    """ Raised when arguments to a method don't meet that method's requirements.
    """

    def __init__(self, format, *args):
        """ Constructs an instance by formatting args.
        """
        complaint = format % args
        Exception.__init__(self, complaint)

def getCelesteHome():

    """ Return the location of the base Celeste installation directory as a
        string.
    """

    #
    # Get the location of a known module as an absolute path name.  This
    # module is chosen because it has a known position relative the Celeste
    # installation directory.  (If this relative position changes, this
    # function will have to be rewritten to match.)
    #
    sep = os.path.sep
    moduleLocation = jvm.__file__
    lastSep = moduleLocation.rfind(sep)
    if lastSep == -1:
        moduleLocation = os.getcwd() + sep + moduleLocation

    #
    # Navigate from the module's location to the installation location by
    # stripping off the "/languages/python/<modulename>.py" suffix.
    #
    # We could use:
    #
    #basedir = \
    #    moduleLocation[0:moduleLocation.rfind("/languages/python/jvm.py")]
    #
    # at the cost of committing to Unix-stype pathname component separators.
    #
    moduledir = moduleLocation[0:moduleLocation.rfind(sep)]
    langdir = moduleLocation[0:moduledir.rfind(sep)]
    basedir = moduleLocation[0:langdir.rfind(sep)]

    return basedir
