#!/usr/bin/env python
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

class macGrapher:
    ""
    def __init__(self, fileName, mode='a'):
         self.fileName = fileName
         fout = open(self.fileName, mode)
         fout.close()

    def addPoint(self, x, y):
        fout = open(self.fileName, 'a')
        fout.write("%f, %f\n" % (x, y))
        fout.close()

if __name__ == "__main__":
    grapher = macGrapher("graph.txt")
    grapher.addPoint(0, 0)
    grapher.addPoint(1, 1)
    grapher.addPoint(2, 2)
    grapher.addPoint(3, 3)

