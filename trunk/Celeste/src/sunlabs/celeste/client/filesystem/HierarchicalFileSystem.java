/*
 * Copyright 2007-2010 Oracle. All Rights Reserved.
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
 * Please contact Oracle., 16 Network Circle, Menlo Park, CA 94025
 * or visit www.oracle.com if you need additional information or have any questions.
 */
package sunlabs.celeste.client.filesystem;

import java.io.IOException;
import java.util.Properties;

import sunlabs.beehive.api.Credential;
import sunlabs.beehive.util.OrderedProperties;
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.client.filesystem.FileException.Retry;

/**
 * A Hierarchical File-system is a file-system organised
 * into directories of files and other directories.
 */
public interface HierarchicalFileSystem extends FileSystem {
    public interface Factory {
        /**
         * 
         * @param name
         * @return
         * @throws FileException.CelesteFailed
         * @throws FileException.CelesteInaccessible
         * @throws FileException.CredentialProblem
         * @throws FileException.Deleted
         * @throws FileException.DirectoryCorrupted
         * @throws FileException.IOException
         * @throws FileException.PermissionDenied
         * @throws FileException.Runtime
         * @throws FileException.ValidationFailed
         * @throws FileException.NotFound 
         * @throws IOException 
         * @throws ClassNotFoundException 
         */
        public HierarchicalFileSystem mount(String fileSystemName) throws FileException.BadVersion,
            FileException.NotFound,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.IOException,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed, FileException.NotFound, IOException, ClassNotFoundException;
        
        /**
         * 
         * @param name
         * @return
         */
        public boolean exists(String fileSystemName);

        /**
         * Create the named file system, using {@code fileSystemName} and {@code password} to initialise the file-system.
         * Note that the credential for the creating entity must already exist, see {@link #createCredential()}.
         * @param fileSystemName the name of the filesystem (this is not necessarily a path name).
         * @param password the password used to initialise the file system's credential. 
         * @param attributes
         * @return the created {@link HierarchicalFileSystem}.
         * @throws CelesteException.RuntimeException
         * @throws CelesteException.AlreadyExistsException
         * @throws CelesteException.NoSpaceException
         * @throws CelesteException.VerificationException
         * @throws CelesteException.CredentialException
         * @throws IOException
         * @throws ClassNotFoundException
         * @throws CelesteException.NotFoundException
         */
        public HierarchicalFileSystem create(String fileSystemName, String password, OrderedProperties attributes)
            throws CelesteException.RuntimeException, CelesteException.NoSpaceException,
            CelesteException.VerificationException, CelesteException.CredentialException, IOException, ClassNotFoundException, CelesteException.NotFoundException,
            FileSystem.AlreadyExistsException;

        /**
         * Create the {@link Credential} of the entity manipulating this FileSystem.
         * 
         * @return the {@link Credential} of the entity manipulating this FileSystem.
         * @throws Credential.Exception 
         * @throws CelesteException.RuntimeException 
         */
        public Credential createCredential() throws Credential.Exception, CelesteException.RuntimeException;
        /**
         * Get the {@link Credential} of the entity manipulating this FileSystem.
         * 
         * @return the {@link Credential} of the entity manipulating this FileSystem.
         */
        public String getCredentialName();
        
        /**
         * Get the {@link Credential} password of the entity manipulating this FileSystem.
         * 
         * @return the {@link Credential} password of the entity manipulating this FileSystem.
         */
        public String getCredentialPassword();
    }
    
    public interface FileName extends Iterable<HierarchicalFileSystem.FileName>, FileSystem.FileName {
        /**
         * Get the last component of the hierarchical {@code FileName}.
         * @return the last component of the hierarchical {@code FileName}.
         */
        public String getBaseName();

        /**
         * Returns a new {@code PathName} consisting of the parent directory of
         * this path name.
         */
        public HierarchicalFileSystem.FileName getDirName();
        

        /**
         * Get the total number of components in this path.
         * <p>
         * Absolute paths have an initial component consisting of the {@code null} String.
         * As a result the {@code #size()} of the absolute path <tt>/a/b</tt> is 3.
         * </p>
         * 
         * @return the total number of components in this path.
         */
        public int size();
        
        /**
         * Return this file name components.
         * The components are returned in a newly created array, with the final element (file basename) in the highest-indexed position.
         * The leading (root) component of an absolute path is represented by the string {@code "/"}.
         *
         * @return  an array of the file name's components
         */
        public String[] getComponents();        

        /**
         * Create a new {@code PathName} instance composed from this {@code PathName} and the given {@code name}.
         * 
         * @param name
         * @return a new {@code PathName} instance composed from this {@code PathName} and the given {@code name}.
         */
        public HierarchicalFileSystem.FileName append(String name);
    }
    
    /**
     * Creates a new directory at the location named by {@code path},
     * associating the properties contained in {@code clientProperties} with
     * the new directory and setting the attributes contained in {@code
     * directoryAttributes} on the directory.
     *
     * @param path                  the path name of the new directory
     * @param directoryProperties   initial attributes for the directory,
     *                              which will be restricted to the set
     *                              denoted by {@link
     *                              FileAttributes.WhenSettable#atCreation}
     * @param clientProperties      properties to be associated with the new
     *                              directory
     *
     * @return  a newly created directory
     *
     * @throws FileException.CredentialProblem
     *      if some problem occurs in accessing or using the credential
     *      associated with the caller
     * @throws FileException.Exists
     *      if a file or directory already exists at {@code path}
     */
    public HierarchicalFileSystem.Directory createDirectory(HierarchicalFileSystem.FileName path, OrderedProperties directoryProperties, OrderedProperties clientProperties)
        throws
            FileException.BadVersion,
            FileException.Exists,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DirectoryCorrupted,
            FileException.FileSystemNotFound,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotDirectory,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed;

    public void deleteDirectory(HierarchicalFileSystem.FileName path) throws FileException;

    /**
     * 
     * @param path
     * @param fileProperties
     * @param userProperties
     * @return
     * @throws FileException
     */
    public FileSystem.File createFile(FileName path, Properties fileProperties, OrderedProperties userProperties) throws FileException;

    public void deleteFile(HierarchicalFileSystem.FileName path) throws FileException, IOException, ClassNotFoundException;

    public interface Directory extends FileSystem.Node {
        /**
         * Return an array of Strings containing the names of the children Nodes.
         * 
         * @return
         * @throws FileException.BadVersion
         * @throws FileException.CelesteFailed
         * @throws FileException.CelesteInaccessible
         * @throws FileException.CredentialProblem
         * @throws FileException.Deleted
         * @throws FileException.DirectoryCorrupted
         * @throws FileException.IOException
         * @throws FileException.NotFound
         * @throws FileException.PermissionDenied
         * @throws FileException.Runtime
         * @throws FileException.ValidationFailed
         */
        public String[] list() throws
        FileException.BadVersion,
        FileException.CelesteFailed,
        FileException.CelesteInaccessible,
        FileException.CredentialProblem,
        FileException.Deleted,
        FileException.DirectoryCorrupted,
        FileException.IOException,
        FileException.NotFound,
        FileException.PermissionDenied,
        FileException.Runtime,
        FileException.ValidationFailed;

        public File getFile(String entryName) throws
        FileException.BadVersion,
        FileException.CelesteFailed,
        FileException.CelesteInaccessible,
        FileException.CredentialProblem,
        FileException.Deleted,
        FileException.DirectoryCorrupted,
        FileException.IOException,
        FileException.InvalidName,
        FileException.NotFound,
        FileException.PermissionDenied,
        FileException.Runtime,
        FileException.ValidationFailed;
    }

    public void renameFile(FileName fromName, FileName toName) throws
      FileException.BadVersion,
      FileException.CapacityExceeded,
      FileException.CelesteFailed,
      FileException.CelesteInaccessible,
      FileException.CredentialProblem,
      FileException.Deleted,
      FileException.DirectoryCorrupted,
      FileException.Exists,
      FileException.FileSystemNotFound,
      FileException.IOException,
      FileException.InvalidName,
      FileException.IsDirectory,
      FileException.Locked,
      FileException.NotDirectory,
      FileException.NotEmpty,
      FileException.NotFound,
      FileException.PermissionDenied,
      FileException.RetriesExceeded,
      FileException.Runtime,
      FileException.ValidationFailed;

    public boolean fileExists(FileName path) throws
    FileException.BadVersion,
    FileException.CelesteFailed,
    FileException.CelesteInaccessible,
    FileException.CredentialProblem,
    FileException.DirectoryCorrupted,
    FileException.FileSystemNotFound,
    FileException.IOException,
    FileException.NotDirectory,
    FileException.PermissionDenied,
    FileException.Runtime,
    FileException.ValidationFailed, FileException.Retry;

    public FileSystem.Node getNode(HierarchicalFileSystem.FileName path) throws FileException.NotFound, FileException.IOException, FileException.BadVersion,
        FileException.CelesteFailed, FileException.CelesteInaccessible, FileException.CredentialProblem, FileException.Deleted, FileException.DirectoryCorrupted,
        FileException.FileSystemNotFound, FileException.NotDirectory, FileException.PermissionDenied, FileException.Runtime, FileException.ValidationFailed, FileException.Retry;

}
