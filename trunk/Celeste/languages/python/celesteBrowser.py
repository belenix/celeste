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

import string
import os
import time

class celesteBrowser:
    ""
    celeste="127.0.0.1:14000"

    def __init__(self, address, port):
         self.celeste = "%s:%s" % (address, port)
         self.elapsedTime = 0

    def curlGet(self, url):
        cmd = "curl -s '%s/%s'" % (self.celeste, url) 
        print cmd
        startTime = time.time()
        pin, pout = os.popen2(cmd)
        result = pout.read()
        self.elapsedTime = time.time() - startTime
        return result

    def curlPut(self, url, data):
        cmd = "curl -s -T %s %s/%s" % (data, self.celeste, url)
        print cmd
        startTime = time.time()
        pin, pout = os.popen2(cmd)
        #pin.write(data)
        #pin.close()
        result = pout.read()
        self.elapsedTime = time.time() - startTime
        return result

#    def newNameSpace(self, nameSpace, nameSpacePassword, erasureCoder="ErasureCodeIdentity/1"):
#        self.curlGet("/newFileSystem/?profileId=%s&profilePassword=%s&erasureCoder=%s" %
#             (nameSpace, nameSpacePassword, erasureCoder))
#        return

    def newNameSpace(self, nameSpace, nameSpacePassword, erasureCoder="ErasureCodeIdentity/1"):
        cmd = "bin/celestefs --celeste %s --id %s --password %s --replication %s --mkfs %s" % (self.celeste, nameSpace, nameSpacePassword, erasureCoder, nameSpace) 
        print cmd
        startTime = time.time()
        pin, pout = os.popen2(cmd)
        result = pout.read()
        self.elapsedTime = time.time() - startTime
        return result
        

    def newFileSystem(self, nameSpace, nameSpacePassword, erasureCoder="ErasureCodeIdentity/1"):
        self.curlGet("/newFileSystem/?profileId=%s&profilePassword=%s&erasureCoder=%s" %
             (nameSpace, nameSpacePassword, erasureCoder))
        return

    def mount(self, nameSpace, nameSpacePassword, erasureCoder="ErasureCodeIdentity/1"):
        result = self.curlGet("/mount/%s/?profileId=%s&profilePassword=%s&erasureCoder=%s&mountPoint=%s" %
                           (nameSpace, nameSpace, nameSpacePassword, erasureCoder, nameSpace))

        return celesteSession(self, nameSpace, result)

class celesteSession:
    ""
    sessionId=""
    def __init__(self, celeste, nameSpace, session):
         self.celeste = celeste
         self.session = session.rstrip()
         self.nameSpace = nameSpace

    def mkdir(self, path):
        #http://<celeste>/mkdir/path?sessionId=<id>
        result = self.celeste.curlGet("/mkdir/%s/%s?sessionId=%s" %
                           (self.nameSpace, path, self.session))
        return result

    def read(self, path):
        #http://<celeste>/read/path?sessionId=<id>
        result = self.celeste.curlGet("/read/%s/%s?sessionId=%s" %
                                      (self.nameSpace, path, self.session))
        return result

    def write(self, path, data):
        # curl -T /etc/motd 'http://127.0.0.1:15000/write/user/motd.txt?
        # sessionId=7ECFB3B371BAE914611946F87141C784421796F99BD1818E69F0394C620F15A9'
        self.celeste.curlPut("/write/%s/%s?sessionId=%s" % (self.nameSpace, path, self.session), data)
        return

    def isWriteable(self, path):
        #http://<celeste>/isWriteable/path?sessionId=<id>
        result = self.celeste.curlGet("/isWriteable/%s/%s?sessionId=%s" %
                                      (self.nameSpace, path, self.session))
        return result

    def delete(self, path):
        # http://<celeste>/delete/path?sessionId=<id>
        result = self.celeste.curlGet("/delete/%s/%s?sessionId=%s" %
                                      (self.nameSpace, path, self.session))
        return result

    def stat(self, path):
        #http://<celeste>/stat/<namespace>/path?sessionId=<id>
        result = self.celeste.curlGet("/stat/%s/%s?sessionId=%s" %
                                   (self.nameSpace, path, self.session))
        return result
        return

if __name__ == "__main__":
    import sys
    erasureCoder="ErasureCodeIdentity/1"

    nameSpaceName="asdfpy"
    nameSpacePassword="asdfpy"

    browser = celesteBrowser("127.0.0.1", 14000)

    browser.newNameSpace(nameSpaceName, nameSpacePassword)
    print "elapsed time %f" % (browser.elapsedTime)

 #   session = browser.mount(nameSpaceName, nameSpacePassword)
    #print session.mkdir("dir")

#    print session.read(".")

#    for i in range(10,20):
#        session.write("foo" + str(i), "example4")
#        print "%d %f" % (i, browser.elapsedTime)

    #print session.read("foo0")
    #print "elapsed time %f" % (browser.elapsedTime)
    #print session.stat("foo0")
    #print "elapsed time %f" % (browser.elapsedTime)
    #print session.isWriteable("foo0")
    #print "elapsed time %f" % (browser.elapsedTime)
    #print session.delete("foo0")
    #print "elapsed time %f" % (browser.elapsedTime)
