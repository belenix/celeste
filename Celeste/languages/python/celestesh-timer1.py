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
import sys
import time
import celestesh
import macGrapher

nameSpaceName="celeste-timer-ns"
nameSpacePassword="celeste-timer-pw"
owner="celeste-timer-owner"
ownerPw="celeste-timer-ownerpw"
group="celeste-timer-group"
groupPw="celeste-timer-grouppw"
client="celeste-timer-client"
clientPw="celeste-timer-clientpw"
#replicationParams="Amin=6;Vmin=3;Vmax=5;Bmin=3;BQf=1;BQb=1"
replicationParams="Amin=3;Vmin=3;Vmax=5;Bmin=3;BQf=1;BQb=1"

blockSize=8*1024*1024
timeToLive=60*60*24

celeste = celestesh.Celestesh(client, clientPw)

grapher = macGrapher.macGrapher("celeste-timer.txt")

celeste.newNamespace(nameSpaceName, nameSpacePassword)

celeste.newCredential(owner, ownerPw)
celeste.newCredential(group, groupPw)
celeste.newCredential(client, clientPw)

for i in range(0,30):
    fileName = "%s%d" % ("data", i)
    celeste.createFile(nameSpaceName, nameSpacePassword, fileName,
        owner, group, "deleteToken", replicationParams, blockSize,
        timeToLive, False)
    print celeste.writeFile(nameSpaceName, fileName, 0, sys.argv[1])
    print "elapsed time %f" % (celeste.elapsedTime)
    grapher.addPoint(i, celeste.elapsedTime)
