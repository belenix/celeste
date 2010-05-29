/*
 * Copyright 2007-2008 Sun Microsystems, Inc. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 *
 * This code is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * only, as published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License version 2 for more details (a copy is
 * included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA
 *
 * Please contact Sun Microsystems, Inc., 16 Network Circle, Menlo
 * Park, CA 94025 or visit www.sun.com if you need additional
 * information or have any questions.
 */

package sunlabs.celeste.client.filesystem.samba;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;

import java.net.InetSocketAddress;

import sunlabs.asdf.util.Time;
import sunlabs.celeste.client.filesystem.CelesteFileSystem;
import sunlabs.celeste.client.filesystem.samba.SambaOps.Result;
import sunlabs.celeste.client.filesystem.samba.SambaOps.SambaSeek;

//
// Much of the code below originated in the (now obsolete)
// sunlabs.celeste.storage.client.SMBClient class.  It's been modified to use
// the CelesteFileSystem infrastructure (via the SambaOps class) and to match
// changes in its peer celstore VFS plugin for smbd.
//

/**
 * <p>
 *
 * The {@code SMBServer} class acts as a helper application that bridges
 * between a Celeste instance and a VFS plugin for the <b>smbclient</b>
 * application.  It accepts messages from the plugin that represent VFS and
 * vnode operations directed at the {@code CelesteFileSystem} instance it
 * represents, and responds with messages encapsulating the results of the
 * requested operations.
 *
 * </p><p>
 *
 * The class expects to run as a standalone application with its {@code
 * System.in} and {@code System.out} streams set up as pipes leading from/to
 * the plugin.  The plugin itself is closely coupled to this class, to the
 * point where they should be treated as a unit when making changes.  It is
 * the plugin's responsibility to arrange to run this class's {@code main()}
 * method with suitable arguments after having set up the requisite plumbing.
 *
 * </p><p>
 *
 * Communication between the plugin and this class is structured as a
 * telnet-style request/response protocol, with command names and arguments
 * given as ASCII strings (except for file data, which appears in raw form)
 * and with responses starting with a three digit status code followed by
 * ASCII strings encoding the results (again, except for raw file data).
 *
 * </p>
 */
public class SMBServer {
    private static BufferedInputStream stdin =
        new BufferedInputStream(System.in);

    //
    // Prevent inadvertent instantiation.
    //
    private SMBServer() {
    }

    //
    // Read a line from stdin (i.e., from the smbd VFS plugin).
    //
    // The current implementation expects all output from smbd to be
    // terminated with CR.  In principle, this getLine should accept lines
    // that are terminated by CR, CRLF, or LF.  CRLF are NOT currently
    // handled!
    //
    // (But note that all communication to/from smbd is mediated by the
    // celstore VFS plugin, which can and should be changed in coordination
    // with this class.  Thus, some of the complexity below isn't strictly
    // necessary.)
    //
    private static String getLine() {
        StringBuilder result = new StringBuilder();
        boolean done = false;
        try {
            do {
                //
                // This read may block, or return -1 on EOF.  It will throw an
                // exception if an I/O error occurs.
                //
                int r = stdin.read();
                if (r == -1)
                    done = true;   // EOF
                if (r == 10)
                    done = true;  // a newline has been encountered. we are done...
                if (r == 13) { // carriage return
                    done = true;
                    //
                    // Now it gets tricky.  there may be a following newline.
                    // Or we may be at end of file or we may just run into a
                    // blocking read() that waits forever, since we are
                    // supposed to reply...  Let's make a reasonable effort to
                    // find trailing newlines...
                    //
                    if (stdin.available() > 0) {
                        // we can actually check, isn't that nice?
                    } else {
                        //
                        // We are hosed.  Now if I could get at the actual
                        // underlying filedescriptor, I could make this read
                        // non-blocking.  HOWEVER, that's not to be done here.
                        // right?  Sooo, I spawn a thread, do the blocking
                        // read, and kill it if necessary?  There is still no
                        // guarantee...  Also, if the following content starts
                        // with a newline, what am I supposed to do in that
                        // case?  *duh*
                        //
                    }
                } else
                    result.append((char)r);
            } while (!done);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return result.toString();
    }

    //
    // Emit a 500 series failure message.
    //
    // XXX: Ought to redo SambaOps to return SyscallExceptions for failure, so
    //      that errno values can be emitted.
    //
    private static void sendFailure(Result result) {
        Throwable t = result.getFailure();
        assert t != null;
        String reason = t.getLocalizedMessage();
        System.out.printf("500 %s%n", reason);
    }

    /**
     * Initiate the bridge between a Celeste confederation identified by the
     * string given as the first argument and the <b>celstore</b> VFS plugin
     * for <b>smbd</b>.
     *
     * @param addr  a string containing an <internet-host>:<port-number> pair
     *              denoting one of the nodes in the Celeste confederation
     */

    public static InetSocketAddress makeAddress(String addr) {
        String[] tokens = addr.split(":");
        return new InetSocketAddress(tokens[0], Integer.parseInt(tokens[1]));
    }
    
    //
    // XXX: Might want to pass more setup info via args, as opposed to reading
    //      it from stdin.
    //
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Celeste address:port missing from command line.");
            System.exit(1);
        }
        String celesteAddress = args[0];

        //
        // Get and verify server version info.
        //
        String serverVersion = SMBServer.getLine();
        if (serverVersion == null) {
            System.err.println("Expected server version, but got EOF.");
            System.exit(1);
        }
        if (!serverVersion.startsWith("CEL1 Server")) {
            System.err.printf("Unexpected version string: %s%n", serverVersion);
            System.exit(1);
        }
        System.out.println("CEL1 Ready.");

        //
        // Obtain the profile name and confirm that it's ok (or not).
        //
        // XXX: Get via command line argument instead?
        //
        String profileName = SMBServer.getLine();
        if (profileName == null) {
            System.err.println("Expected profile, but got EOF.");
            System.exit(1);
        }
        if (!profileName.startsWith("profile=")) {
            System.out.println("500 No profile name");
            System.exit(1);
        } else
            profileName = profileName.substring("profile=".length());

        try {
            //
            // Create an instance of CelesteFileSystem to represent the
            // configuration information gathered above.  Use the default
            // connection caching policy.
            //
            CelesteFileSystem cfs = new CelesteFileSystem(
                makeAddress(celesteAddress), null, profileName, profileName,
                profileName);
            SambaOps samba = new SambaOps(cfs);

            //
            // Let the VFS plugin know we're ready to go.
            //
            System.out.println("200");

            //
            // Handle commands until told to stop.
            //
            // XXX: Command parsing is very naive.  For example, there's no
            //      provision for handling files whose names contain embedded
            //      blanks (there's no quoting mechanism).  Nor is there any
            //      error checking for missing operands or type checking for
            //      ones that are present.
            //
            while (true) {
                String line = SMBServer.getLine();
                if (line == null) {
                    break;
                } else if (line.startsWith("chdir")) {
                    String path = line.replaceFirst("chdir ", "");
                    Result.Void res = samba.chdir(path);
                    if (res.isSuccessful())
                        System.out.println("200 Chdir ok.");
                    else
                        sendFailure(res);
                } else if (line.startsWith("chmod")) {
                    String[] cmd_args = line.split(" ");
                    String fname = cmd_args[1];
                    //
                    // Expect the new mode to be given in decimal.  (No
                    // symbolic modes here...)
                    //
                    int mode = Integer.parseInt(cmd_args[2]);
                    Result.Void res = samba.chmod(fname, mode);
                    if (res.isSuccessful())
                        System.out.println("200 Chmod succeeded.");
                    else
                        sendFailure(res);
                } else if (line.startsWith("chown")) {
                    String[] cmd_args = line.split(" ");
                    String fname = cmd_args[1];
                    //
                    // Expect the new mode to be given in decimal.  (No
                    // symbolic modes here...)
                    //
                    long uid = Long.parseLong(cmd_args[2]);
                    long gid = Long.parseLong(cmd_args[3]);
                    Result.Void res = samba.chown(fname, uid, gid);
                    if (res.isSuccessful())
                        System.out.println("200 Chown succeeded.");
                    else
                        sendFailure(res);
                } else if (line.startsWith("closedir")) {
                    int index = Integer.parseInt(line.replaceFirst("closedir ", ""));
                    Result.Void res = samba.closedir(index);
                    if (res.isSuccessful())
                        System.out.println("200 Directory closed.");
                    else
                        sendFailure(res);
                } else if (line.startsWith("closefile")) {
                    int index = Integer.parseInt(line.replaceFirst("closefile ", ""));
                    Result.Void res = samba.close(index);
                    if (res.isSuccessful())
                        System.out.println("200 File closed.");
                    else
                        sendFailure(res);
                } else if (line.startsWith("fchmod")) {
                    String[] cmd_args = line.split(" ");
                    int fd = Integer.parseInt(cmd_args[1]);
                    //
                    // Expect the new mode to be given in decimal.  (No
                    // symbolic modes here...)
                    //
                    int mode = Integer.parseInt(cmd_args[2]);
                    Result.Void res = samba.fchmod(fd, mode);
                    if (res.isSuccessful())
                        System.out.println("200 Fchmod succeeded.");
                    else
                        sendFailure(res);
                } else if (line.startsWith("fchown")) {
                    String[] cmd_args = line.split(" ");
                    int fd = Integer.parseInt(cmd_args[1]);
                    //
                    // Expect the new mode to be given in decimal.  (No
                    // symbolic modes here...)
                    //
                    long uid = Long.parseLong(cmd_args[2]);
                    long gid = Long.parseLong(cmd_args[3]);
                    Result.Void res = samba.fchown(fd, uid, gid);
                    if (res.isSuccessful())
                        System.out.println("200 Fchown succeeded.");
                    else
                        sendFailure(res);
                } else if (line.startsWith("fstat")) {
                    int fd = Integer.parseInt(line.replaceFirst("fstat ", ""));
                    Result.Attrs res = samba.fstat(fd);
                    if (res.isSuccessful())
                        System.out.printf("200 %d %d %d %d %d %d %d%n",
                            res.getSerialNumber(), res.getSize(),
                            res.getUid(), res.getGid(),
                            res.getModTime(), res.getCTime(), res.getMode());
                    else
                        sendFailure(res);
                } else if (line.startsWith("ftruncate")) {
                    String[] cmd_args = line.split(" ");
                    int fd = Integer.parseInt(cmd_args[1]);
                    int offset = Integer.parseInt(cmd_args[2]);
                    Result.Void res = samba.ftruncate(fd, offset);
                    if (res.isSuccessful())
                        System.out.println("200 ftruncate ok.");
                    else
                        sendFailure(res);
                } else if (line.startsWith("getwd")) {
                    Result.Path res = samba.getwd();
                    if (res.isSuccessful()) {
                        String path = res.getPath();
                        System.out.printf("200 %d%n", path.length());
                        System.out.print(path);
                        System.out.flush();
                    } else
                        sendFailure(res);
                } else if (line.startsWith("lseek")) {
                    String [] cmd_args=line.split(" ");
                    int fd = Integer.parseInt(cmd_args[1]);
                    int offset = Integer.parseInt(cmd_args[2]);
                    int whence = Integer.parseInt(cmd_args[3]);
                    SambaSeek sWhence = (whence == 0) ? SambaSeek.SEEK_SET :
                        (whence == 1) ? SambaSeek.SEEK_CUR : SambaSeek.SEEK_END;
                    Result.Offset res = samba.lseek(fd, offset, sWhence);
                    if (res.isSuccessful())
                        System.out.printf("200 %d%n", res.getOffset());
                    else
                        sendFailure(res);
                } else if (line.startsWith("mkdir")) {
                    String [] cmd_args = line.split(" ");
                    long uid = Long.parseLong(cmd_args[1]);
                    long gid = Long.parseLong(cmd_args[2]);
                    int mode = Integer.parseInt(cmd_args[3]);
                    int length = Integer.parseInt(cmd_args[4]);
                    byte [] fname = new byte[length];
                    int len_read = stdin.read(fname, 0, length);
                    if (len_read != length) {
                            System.out.println("500 -1 mkdir argument too short.");
                            continue;
                    }
                    Result.Void res =
                        samba.mkdir(new String(fname), uid, gid, mode);
                    if (res.isSuccessful())
                        System.out.println("200 mkdir ok.");
                    else
                        sendFailure(res);
                } else if (line.startsWith("opendir")) {
                    int length = Integer.parseInt(line.replaceFirst("opendir ", ""));
                    byte [] fname = new byte[length];
                    int len_read = stdin.read(fname, 0, length);
                    if (len_read != length) {
                        System.out.println("500 -1 opendir argument too short.");
                        continue;
                    }
                    Result.FD res = samba.opendir(new String(fname));
                    if (res.isSuccessful()) {
                        int fd = res.getFd();
                        System.out.printf("200 %d%n", fd);
                    } else
                        sendFailure(res);
                } else if (line.startsWith("openfile")) {
                    String [] cmd_args = line.split(" ");
                    long uid = Long.parseLong(cmd_args[1]);
                    long gid = Long.parseLong(cmd_args[2]);
                    int flags = Integer.parseInt(cmd_args[3]);
                    int mode = Integer.parseInt(cmd_args[4]);
                    int length = Integer.parseInt(cmd_args[5]);
                    byte [] fname = new byte[length];
                    int len_read = stdin.read(fname, 0, length);
                    if (len_read != length) {
                            System.out.println("500 -1 open argument too short.");
                            continue;
                    }
                    Result.FD res = samba.open(new String(fname), uid, gid,
                        flags, mode);
                    if (res.isSuccessful())
                        System.out.printf("200 %d%n", res.getFd());
                    else
                        sendFailure(res);
                } else if (line.startsWith("pread")) {
                    String [] cmd_args = line.split(" ");
                    int fd = Integer.parseInt(cmd_args[1]);
                    int len = Integer.parseInt(cmd_args[2]);
                    int offset = Integer.parseInt(cmd_args[3]);
                    Result.Bytes res = samba.pread(fd, len, offset);
                    if (res.isSuccessful()) {
                            byte[] bytes = res.getBytes();
                            int bytesRead = res.getLength();
                            System.out.printf("200 %d%n", bytesRead);
                            if (bytesRead > 0)
                                System.out.write(bytes, 0, bytesRead);
                            System.out.flush();
                    } else
                        sendFailure(res);
                } else if (line.startsWith("pwrite")) {
                    String [] cmd_args = line.split(" ");
                    int fd = Integer.parseInt(cmd_args[1]);
                    int offset = Integer.parseInt(cmd_args[2]);
                    int len = Integer.parseInt(cmd_args[3]);
                    byte [] data = new byte[len];
                    int total_len = 0;
                    boolean panic = false;
                    while (total_len != len) {
                        int len_read = stdin.read(data, total_len, len - total_len);
                        total_len += len_read;
                        if (len_read == 0 && panic)
                            break;
                        if (len_read == 0 && !panic) {
                            Thread.sleep(100);
                            panic = true;
                        }
                        if (len_read != 0)
                            panic = false;
                    }
                    if (total_len != len) {
                        Thread.sleep(1000);
                        System.err.printf(
                            "pwrite short read from samba.  Expected %d, got %d.%n",
                            len, total_len);
                        System.err.printf(
                            "There are now %d directly available.%n",
                            System.in.available());
                        System.out.println("500 -1 pwrite argument too short.");
                        continue;
                    }
                    Result.Length res = samba.pwrite(fd, offset, data);
                    if (res.isSuccessful())
                        System.out.printf("200 %d%n", res.getLength());
                    else
                        sendFailure(res);
                } else if (line.startsWith("quit")) {
                    break;
                } else if (line.startsWith("readdir")) {
                    int index = Integer.parseInt(line.replaceFirst("readdir ", ""));
                    Result.DirentAndOffset res = samba.readdir(index);
                    if (res.isSuccessful()) {
                            String component = res.getComponentName();
                            //
                            // Note that the component proper is sent by
                            // itself as a non-terminated line following the
                            // one containing status and component length.
                            //
                            System.out.printf("200 %d %d %d%n",
                                res.getOffset(),
                                res.getComponentSerialNumber(),
                                component.length());
                            System.out.print(component);
                            System.out.flush();
                    } else if (res.getFailure() instanceof EOFException) {
                        System.out.println("200 -1 0"); //eof
                    } else
                        sendFailure(res);
                } else if (line.startsWith("readfile")) {
                    String [] cmd_args = line.split(" ");
                    int fd = Integer.parseInt(cmd_args[1]);
                    int len = Integer.parseInt(cmd_args[2]);
                    Result.Bytes res = samba.read(fd, len);
                    if (res.isSuccessful()) {
                            byte[] bytes = res.getBytes();
                            int bytesRead = res.getLength();
                            System.out.printf("200 %d%n", bytesRead);
                            if (bytesRead > 0)
                                System.out.write(bytes, 0, bytesRead);
                            System.out.flush();
                    } else
                        sendFailure(res);
                } else if (line.startsWith("rename")) {
                    String [] cmd_args = line.split(" ");
                    int old_len = Integer.parseInt(cmd_args[1]);
                    int new_len = Integer.parseInt(cmd_args[2]);
                    byte [] old_path = new byte[old_len];
                    int len_read = stdin.read(old_path, 0, old_len);
                    if (len_read != old_len) {
                        System.out.println("500 -1 rename argument 1 too short.");
                        continue;
                    }
                    byte [] new_path = new byte[new_len];
                    len_read = stdin.read(new_path, 0, new_len);
                    if (len_read != new_len) {
                            System.out.println("500 -1 rename argument 2 too short.");
                            continue;
                    }
                    Result.Void res = samba.rename(new String(old_path), new String(new_path));
                    if (res.isSuccessful())
                        System.out.println("200 rename ok.");
                    else
                        sendFailure(res);
                } else if (line.startsWith("rewinddir")) {
                    int index = Integer.parseInt(line.replaceFirst("rewinddir ", ""));
                    Result.Void res = samba.rewinddir(index);
                    if (res.isSuccessful())
                        System.out.println("200 Directory rewound.");
                    else
                        sendFailure(res);
                } else if (line.startsWith("rmdir")) {
                    int length = Integer.parseInt(line.replaceFirst("rmdir ", ""));
                    byte [] path = new byte[length];
                    int len_read = stdin.read(path, 0, length);
                    if (len_read != length) {
                        System.out.println("500 -1 rmdir argument too short.");
                        continue;
                    }
                    Result.Void res = samba.rmdir(new String(path));
                    if (res.isSuccessful())
                        System.out.println("200 rmdir ok.");
                    else
                        sendFailure(res);
                } else if (line.startsWith("seekdir")) {
                    String [] cmd_args = line.split(" ");
                    int index = Integer.parseInt(cmd_args[1]);
                    int offset = Integer.parseInt(cmd_args[2]);
                    Result.Void res = samba.seekdir(index, offset);
                    if (res.isSuccessful())
                        System.out.println("200 Directory sought.");
                    else
                        sendFailure(res);
                } else if (line.startsWith("stat")) {
                    int length = Integer.parseInt(line.replaceFirst("stat ", ""));
                    byte [] fname = new byte[length];
                    int len_read = stdin.read(fname, 0, length);
                    if (len_read != length) {
                            System.out.println("500 -1 stat argument too short.");
                            continue;
                    }
                    Result.Attrs res = samba.stat(new String(fname));
                    if (res.isSuccessful())
                        System.out.printf("200 %d %d %d %d %d %d %d%n",
                            res.getSerialNumber(), res.getSize(),
                            res.getUid(), res.getGid(),
                            res.getModTime(), res.getCTime(), res.getMode());
                    else
                        sendFailure(res);
                } else if (line.startsWith("telldir")) {
                    int index = Integer.parseInt(line.replaceFirst("telldir ", ""));
                    Result.Offset res = samba.telldir(index);
                    if (res.isSuccessful())
                        System.out.printf("200 %d%n", res.getOffset());
                    else
                        sendFailure(res);
                } else if (line.startsWith("unlink")) {
                    int length = Integer.parseInt(line.replaceFirst("unlink ", ""));
                    byte [] path = new byte[length];
                    int len_read = stdin.read(path, 0, length);
                    if (len_read != length) {
                            System.out.println("500 -1 unlink argument too short.");
                            continue;
                    }
                    Result.Void res = samba.unlink(new String(path));
                    if (res.isSuccessful())
                        System.out.println("200 unlink ok.");
                    else
                        sendFailure(res);
                } else if (line.startsWith("writefile")) {
                    String [] cmd_args = line.split(" ");
                    int fd = Integer.parseInt(cmd_args[1]);
                    int len = Integer.parseInt(cmd_args[2]);
                    byte [] data = new byte[len];
                    int total_len = 0;
                    boolean panic = false;
                    while (total_len != len) {
                        int len_read = stdin.read(data, total_len, len - total_len);
                        total_len += len_read;
                        if (len_read == 0 && panic)
                            break;
                        if (len_read == 0 && !panic) {
                            Thread.sleep(100);
                            panic = true;
                        }
                        if (len_read != 0)
                            panic = false;
                    }
                    if (total_len != len) {
                            Thread.sleep(1000);
                            System.err.println("write short read from samba.  Expected " +
                                len + ", got " + total_len + ".");
                            System.err.println("There are now " + System.in.available() + " directly available.");
                            System.out.println("500 -1 write argument too short.");
                            continue;
                    }
                    Result.Length res = samba.write(fd, data);
                    if (res.isSuccessful())
                        System.out.printf("200 %d%n", res.getLength());
                    else
                        sendFailure(res);
                } else {
                    System.out.println("500 Unknown command.");
                    System.err.println("Unknown command: " + line);
                }
            }

        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }

        System.exit(0);
    }
}
