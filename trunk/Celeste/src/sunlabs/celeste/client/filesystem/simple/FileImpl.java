/*
 * Copyright 2007-2009 Sun Microsystems, Inc. All Rights Reserved.
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

package sunlabs.celeste.client.filesystem.simple;

import static java.lang.Math.min;
import static sunlabs.celeste.client.filesystem.FileAttributes.Names.ACL_NAME;
import static sunlabs.celeste.client.filesystem.FileAttributes.Names.BLOCK_SIZE_NAME;
import static sunlabs.celeste.client.filesystem.FileAttributes.Names.CACHE_ENABLED_NAME;
import static sunlabs.celeste.client.filesystem.FileAttributes.Names.CLIENT_METADATA_NAME;
import static sunlabs.celeste.client.filesystem.FileAttributes.Names.CONTENT_TYPE_NAME;
import static sunlabs.celeste.client.filesystem.FileAttributes.Names.CREATED_TIME_NAME;
import static sunlabs.celeste.client.filesystem.FileAttributes.Names.DELETION_TIME_TO_LIVE_NAME;
import static sunlabs.celeste.client.filesystem.FileAttributes.Names.FILE_SERIAL_NUMBER_NAME;
import static sunlabs.celeste.client.filesystem.FileAttributes.Names.IS_DELETED_NAME;
import static sunlabs.celeste.client.filesystem.FileAttributes.Names.METADATA_CHANGED_TIME_NAME;
import static sunlabs.celeste.client.filesystem.FileAttributes.Names.MODIFIED_TIME_NAME;
import static sunlabs.celeste.client.filesystem.FileAttributes.Names.REPLICATION_PARAMETERS_NAME;
import static sunlabs.celeste.client.filesystem.FileAttributes.Names.SIGN_MODIFICATIONS_NAME;
import static sunlabs.celeste.client.filesystem.FileAttributes.Names.TIME_TO_LIVE_NAME;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import sunlabs.asdf.jmx.JMX;
import sunlabs.asdf.util.Time;
import sunlabs.asdf.util.TimeProfiler;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.celeste.CelesteException;
import sunlabs.celeste.FileIdentifier;
import sunlabs.celeste.ResponseMessage;
import sunlabs.celeste.api.CelesteAPI;
import sunlabs.celeste.client.CelesteProxy;
import sunlabs.celeste.client.ClientMetaData;
import sunlabs.celeste.client.Profile_;
import sunlabs.celeste.client.filesystem.FileAttributes;
import sunlabs.celeste.client.filesystem.FileException;
import sunlabs.celeste.client.filesystem.simple.BufferCache.ReadResult;
import sunlabs.celeste.client.operation.CreateFileOperation;
import sunlabs.celeste.client.operation.DeleteFileOperation;
import sunlabs.celeste.client.operation.ExtensibleOperation;
import sunlabs.celeste.client.operation.InspectFileOperation;
import sunlabs.celeste.client.operation.InspectLockOperation;
import sunlabs.celeste.client.operation.LockFileOperation;
import sunlabs.celeste.client.operation.NewCredentialOperation;
import sunlabs.celeste.client.operation.ReadFileOperation;
import sunlabs.celeste.client.operation.SetACLOperation;
import sunlabs.celeste.client.operation.SetFileLengthOperation;
import sunlabs.celeste.client.operation.SetOwnerAndGroupOperation;
import sunlabs.celeste.client.operation.UnlockFileOperation;
import sunlabs.celeste.client.operation.WriteFileOperation;
import sunlabs.celeste.node.CelesteACL;
import sunlabs.celeste.node.ProfileCache;
import sunlabs.celeste.node.services.api.AObjectVersionMapAPI;
import sunlabs.celeste.util.CelesteEncoderDecoder;
import sunlabs.celeste.util.ACL.Disposition;
import sunlabs.titan.BeehiveObjectId;
import sunlabs.titan.api.Credential;
import sunlabs.titan.util.Extent;
import sunlabs.titan.util.ExtentBuffer;
import sunlabs.titan.util.ExtentBufferStreamer;
import sunlabs.titan.util.ExtentImpl;
import sunlabs.titan.util.LRUCache;
import sunlabs.titan.util.OrderedProperties;

/**
 * FileImpl represents files as they are stored in Celeste.
 */
public class FileImpl {
    /**
     * <p>
     *
     * A cache for {@code FileImpl} instances.
     *
     * </p><p>
     *
     * Callers should obtain and release entries from the cache according to
     * the design pattern described in {@link sunlabs.titan.util.LRUCache
     * LRUCache's class comment}.
     *
     * </p>
     */
    public static class Cache extends LRUCache<FileIdentifier, FileImpl> {
        private final static long serialVersionUID = 1L;

        /**
         * Creates a cache with the given {@code capacity} for managing {@code
         * FileImpl} instances that communicate with the Celeste node at
         * {@code address}.  Each such instance will use {@code proxyCache} to
         * manage its connections to that node.
         *
         * @param capacity      the maximum number of entries the cache should
         *                      contain
         * @param address       the address of the Celeste node that this
         *                      cache's entries should communicate with
         * @param proxyCache    a cache for managing connections to the
         *                      Celeste node with the given {@code address}
         */
        //
        // XXX: Can we get away without an explicit address argument?
        //      (Probably not, but worth thinking through.)
        //
        public Cache(int capacity, InetSocketAddress address,
                CelesteProxy.Cache proxyCache) {
            //
            // This cache's entries can be shared among multiple clients.
            //
            super(capacity, new FileImpl.Factory(address, proxyCache), false,
                null);
        }

        /**
         * This method specializes the superclass implementation by reclaiming
         * whatever resources {@code fileImpl} is willing to relinquish.
         */
        @Override
        protected void disposeItem(FileIdentifier id, FileImpl fileImpl) {
            fileImpl.dispose();
            super.disposeItem(id, fileImpl);
        }
    }

    /**
     * A factory class for producing {@code FileImpl} instances.  Intended to
     * be used in conjunction with a {@link sunlabs.titan.util.LRUCache
     * LRUCache} for reusing such instances.
     */
    private static class Factory implements
            LRUCache.Factory<FileIdentifier, FileImpl> {
        private final InetSocketAddress address;
        private final CelesteProxy.Cache proxyCache;

        /**
         * Creates a new factory that will produce {@code FileImpl} instances
         * that communicate with the Celeste node at {@code address}.
         */
        @Deprecated
        public Factory(InetSocketAddress address) {
            this(address, null);
        }

        /**
         * Creates a new factory that will produce {@code FileImpl} instances
         * that communicate with the Celeste node at {@code address} and that
         * use {@code proxyCache} to manage connections to that node.
         */
        public Factory(InetSocketAddress address,
                CelesteProxy.Cache proxyCache) {
            this.address = address;
            this.proxyCache = proxyCache;
        }

        public FileImpl newInstance(FileIdentifier key) {
            //
            // XXX: Debug.
            //
            //System.err.printf("FileImplCache miss: [%s, %s]%n",
            //    key.getNameSpaceId().toString(),
            //    key.getFileId().toString());
            return new FileImpl(key, this.address, this.proxyCache);
        }
    }

    //
    // A tuple class that records the per-version metadata associated with a
    // given file version.  Used as the value class for the versionToMetadata
    // map.
    //
    // XXX: Consider making all members be Strings.  Doing so would probably
    //      reduce the amount of conversion needed between the native type of
    //      a given attribute and its string encoding.
    //
    private static class VersionMetadata {
        //
        // The time of last change to the file's contents (as opposed to
        // client-supplied metadata associated with it).
        //
        // This value is with respect to the local clock of the client making
        // the modification.  As with the creation time, there's no attempt
        // made at reconciling differing local clocks.
        //
        public final long               modifiedTime;
        //
        // The time of last change to client-supplied metadata.
        //
        // This value is with respect to the local clock of the client making
        // the modification.  As with the creation time, there's no attempt
        // made at reconciling differing local clocks.
        //
        public final long               metadataChangedTime;
        //
        // Each modification creates a new version, so the length is immutable
        // for a given version.
        //
        // XXX: This property might not hold in the face of the proposed
        //      introduction of transactions allowing multiple writes in their
        //      bodies.
        //
        public final long               fileLength;
        //
        // A file may be 'deleted' (fileExists() == false) even though it
        // takes up an object-id.  Keeping a reference to a deleted file makes
        // sense for archival purposes (e.g., history of a repository).
        // The file needs to know it is deleted, as it might be referenced from
        // multiple directories.  Things are a bit tricky here:  Keep in mind
        // that uniqueFileId is really random, and retrieved from a directory.
        // So if one directory is used to mark the file as deleted, the others
        // still referencing this can learn about that fact only by reading
        // the deleted file.  If after deletion somebody wants to create a
        // file with 'this' name, what they will really do is pick a different
        // uniqueId -- as this present file will become unreachable when no
        // more directories reference it (unless you go to old versions of
        // that directory, that is!)
        //
        // Note that marking a file as deleted creates a new version.  Thus
        // the property is immutable for a given version.
        //
        public final boolean            isDeleted;
        //
        // FileImpl clients may supply their own version-specific metadata in
        // the form of an Ordered Properties object.  FileImpl does not
        // interpret this metadata, but does include it in the signed state
        // for each version.
        //
        // This information should be clearly distinguished from information
        // that the file system implementation uses to provide proper file
        // semantics.  To help do so, the nomenclature "properties" (or
        // "client properties") refers to the former and "attributes" to the
        // latter.
        //
        public final OrderedProperties  clientProperties;
        //
        // The Internet media type describing the file's contents, in string
        // form.
        //
        public final String             contentType;
        //
        // Although the current Celeste implementation fixes a file's maximum
        // BObject size creation time, this restriction could easily be
        // relaxed.  So we record that size in the version-specific data,
        // viewing it as the block size to use for reads.
        //
        public final int                blockSize;

        //
        // The time to live for the file's anti-objects after the file has
        // been deleted.  In contrast to the time to live itself, the deletion
        // time to live can be changed up until the file is actially deleted,
        // so it's recorded as version metadata.
        //
        public long                     deletionTimeToLive;

        //
        // Now that FileImpl supports ownership changes, the delete token must
        // be re-encrypted each time ownership changes, which makes the
        // encrypted value version-specific.  The delete token is encrypted in
        // the first place to spare FileImpl clients from having to remember
        // it separately for presentation at deletion time; requiring that
        // would be an unacceptably severe usability problem.
        //
        public final byte[]             encryptedDeleteToken;

        //
        // The Celeste layer maintains the following three fields for use in
        // making access control decisions when operations are invoked on the
        // file.
        //
        public final BeehiveObjectId    ownerId;
        public final BeehiveObjectId    groupId;
        public final CelesteACL         acl;

        public VersionMetadata(long modifiedTime, long metadataChangedTime,
                long fileLength, boolean isDeleted,
                OrderedProperties clientProperties,
                String contentType,
                int blockSize, long deletionTimeToLive,
                byte[] encryptedDeleteToken,
                BeehiveObjectId ownerId, BeehiveObjectId groupId,
                CelesteACL acl) {
            this.modifiedTime = modifiedTime;
            this.metadataChangedTime = metadataChangedTime;
            this.fileLength = fileLength;
            this.isDeleted = isDeleted;
            this.clientProperties = clientProperties;
            this.contentType = contentType;
            //
            // Force the block size to a sane value.  Note that we now allow
            // it to be a non-power of two value.
            //
            if (blockSize <= 0)
                blockSize = FileImpl.maxBufferLength;
            this.blockSize = blockSize;
            this.deletionTimeToLive = deletionTimeToLive;
            this.encryptedDeleteToken = encryptedDeleteToken;
            this.ownerId = ownerId;
            this.groupId = groupId;
            this.acl = acl;
        }
    }

    //
    // Objects of the CelesteReader class perform readFile() calls to Celeste
    // when the buffer cache decides that i/o is necessary.  Note that the
    // class is not static, so it has an implicit reference to this FileImpl.
    //
    // An instance of this class can be instructed to use an alternative
    // communication path to Celeste via a proxy obtained from the proxy
    // cache.  This possibility accounts for the try ... finally statement in
    // the call() method, which ensures that proxies are returned to the cache
    // after use.
    //
    private class CelesteReader extends BufferCache.Reader {
        private Credential readerCredential;
        private char[] readerPassword; /// XXX DO NOT DO THIS.

        public CelesteReader(Credential readerId, char[] readerPassword) {
            super();
            this.readerCredential = readerId;
            this.readerPassword = readerPassword;
        }

        public Object call() throws
                FileException.BadVersion,
                FileException.CelesteInaccessible,
                FileException.CredentialProblem,
                FileException.Deleted,
                FileException.IOException,
                FileException.NotFound,
                FileException.PermissionDenied,
                FileException.Runtime,
                FileException.ValidationFailed,
                Exception {
            Extent desiredExtent = this.getDesiredExtent();
            long startOffset = desiredExtent.getStartOffset();
            //
            // Impedance matching:  Translate "to EOF" expressed in
            // extent style to the fileRead() convention.
            //
            long endOffset = desiredExtent.getEndOffset();
            if (endOffset == Long.MIN_VALUE)
                endOffset = -1;
            CelesteAPI proxy = null;
            InetSocketAddress addr = FileImpl.this.socketAddr;
            try {
                proxy = FileImpl.this.proxyCacheGetAndRemove(addr);

                ReadFileOperation operation = new ReadFileOperation(
                    FileImpl.this.getFileIdentifier(),
                    this.readerCredential.getObjectId(),
                    startOffset,
                    (int) (endOffset - startOffset));

                Credential.Signature signature = null;
                try {
                    signature = this.readerCredential.sign(
                        this.readerPassword, operation.getId());
                } catch (Credential.Exception e) {
                    throw new FileException.CredentialProblem(e);
                }

                ResponseMessage msg = null;
                try {
                    msg = proxy.readFile(operation, signature);
                } catch (IOException e) {
                    throw new FileException.IOException(e);
                } catch (ClassNotFoundException e) {
                    throw new FileException.IOException(e);
                } catch (CelesteException.CredentialException e) {
                    throw new FileException.CredentialProblem(e);
                } catch (CelesteException.AccessControlException e) {
                    throw new FileException.PermissionDenied(e);
                } catch (CelesteException.DeletedException e) {
                    throw new FileException.Deleted(e);
                } catch (CelesteException.IllegalParameterException e) {
                    throw new FileException.Runtime(e);
                } catch (CelesteException.NotFoundException e) {
                    throw new FileException.NotFound(e);
                } catch (CelesteException.RuntimeException e) {
                    throw new FileException.Runtime(e);
                } catch (CelesteException.VerificationException e) {
                    throw new FileException.Runtime(e);
                }

                OrderedProperties metadata = msg.getMetadata();
                BeehiveObjectId versionId = new BeehiveObjectId(
                    metadata.getProperty(CelesteAPI.VOBJECTID_NAME));

                //
                // See what Celeste told us about the state of the file.  If
                // the version has advanced beyond what we thought it was,
                // this call will advance the version and the metadata that
                // goes with it.
                //
                FileImpl.this.processReplyMetadata(metadata);

                //
                // XXX: Going from an ExtentBufferStreamer to a byte array and
                //      then back to an extent is annoyingly awkward.  We
                //      should consider changing the BufferCache.Reader
                //      interface to have it deliver its results as an
                //      ExtentBufferMap or an ExtentBufferStreamer.
                //
                ExtentBuffer newlyReadBuffer = new ExtentBuffer(startOffset,
                    ByteBuffer.wrap(
                        msg.get(ExtentBufferStreamer.class).render()));
                this.setResult(new ReadResult(versionId, newlyReadBuffer));

                return null;
            } finally {
                FileImpl.this.proxyCacheAddAndEvictOld(addr, proxy);
            }
        }
    }

    //
    // TimeProfiler configuration information
    //
    // Should contain a colon-separated list of <class>.<method> strings,
    // where class omits the package prefix and method omits all signature
    // information but the method name itself.  Instrumented methods check
    // this string to see whether they're mentioned in it.
    //
    private static final String profileConfig = System.getProperty(
        "sunlabs.celeste.client.filesystem.ProfilerConfiguration", "");
    private static final String profileOutputFileName = System.getProperty(
        "sunlabs.celeste.client.filesystem.ProfilerOutputFile");

    static private final int defaultDataEncodingVersion = 1;

    //
    // File properties that FileImpl manipulates directly (as opposed to ones
    // that higher level clients pass down for FileImpl to store and
    // retrieve).
    //
    // These names are stored as part of file metadata, so changing them
    // implies changing the data encoding version or explicitly handling both
    // the old and new names.
    //
    // Many of these names names are borrowed from the
    // sunlabs.celeste.client.filesystem layer above.  They are:
    //
    //      ACL_NAME
    //      BLOCK_SIZE_NAME
    //      CACHE_ENABLED_NAME
    //      CONTENT_TYPE_NAME
    //      CREATED_TIME_NAME
    //      FILE_SERIAL_NUMBER_NAME
    //      IS_DELETED_NAME
    //      METADATA_CHANGED_TIME_NAME
    //      MODIFIED_TIME_NAME
    //
    static private final String ENCRYPTED_DTOKEN_NAME = "EncDeleteToken";
    static private final String VERSION_NAME = "Version";

    static private final String defaultReplicationParameters =
        "AObject.Replication.Store=3;" +
        "VObject.Replication.Store=3;" +
        "BObject.Replication.Store=3";
    //
    // The size that FileImpl requests Celeste to use internally for BObjects.
    //
    // During the system's evolution, this value has fluctuated up and down,
    // between extremes of 64K and 8M.  Currently, it's back up to the large
    // extreme.
    //
    // Note that this is only a default; clients can specify a block size
    // value at file creation time.
    //
    static private final int maxBufferLength = 1024*1024 * 8;

    //
    // The last resort attributes to be applied when a file is created.
    // FileImpl clients have the opportunity to override them by using the
    // variant of the create() method that accepts creation attributes.
    //
    static private final OrderedProperties defaultCreationAttributes =
        new OrderedProperties();
    static {
        //
        // Use the ACL equivalent of 644 permissions.
        //
        // XXX: Perhaps this default ACL ought to be more permissive, allowing
        //      everybody to do everything.
        //
        CelesteACL acl = new CelesteACL(
            new CelesteACL.CelesteACE(
                new CelesteACL.OwnerMatcher(),
                EnumSet.of(
                    CelesteACL.CelesteOps.deleteFile,
                    CelesteACL.CelesteOps.inspectFile,
                    CelesteACL.CelesteOps.lockFile,
                    CelesteACL.CelesteOps.readFile,
                    CelesteACL.CelesteOps.setUserAndGroup,
                    CelesteACL.CelesteOps.setACL,
                    CelesteACL.CelesteOps.setFileLength,
                    CelesteACL.CelesteOps.writeFile),
                Disposition.grant
            ),
            new CelesteACL.CelesteACE(
                new CelesteACL.GroupMatcher(),
                EnumSet.of(CelesteACL.CelesteOps.readFile),
                Disposition.grant
            ),
            new CelesteACL.CelesteACE(
                new CelesteACL.AllMatcher(),
                EnumSet.of(CelesteACL.CelesteOps.readFile),
                Disposition.grant
            )
        );
        FileImpl.defaultCreationAttributes.setProperty(ACL_NAME,
            acl.toEncodedString());
        FileImpl.defaultCreationAttributes.setProperty(BLOCK_SIZE_NAME,
            Integer.toString(FileImpl.maxBufferLength));
        FileImpl.defaultCreationAttributes.setProperty(CACHE_ENABLED_NAME,
            Boolean.toString(false));
        FileImpl.defaultCreationAttributes.setProperty(CONTENT_TYPE_NAME,
            "application/octet-stream");
        //
        // XXX: What should the default be?  For now, use the value that used
        //      to be wired into purgeForever().
        //
        FileImpl.defaultCreationAttributes.setProperty(DELETION_TIME_TO_LIVE_NAME,
            Long.toString(1000 * Time.DAYS_IN_SECONDS));
        FileImpl.defaultCreationAttributes.setProperty(REPLICATION_PARAMETERS_NAME,
            FileImpl.defaultReplicationParameters);
        FileImpl.defaultCreationAttributes.setProperty(SIGN_MODIFICATIONS_NAME,
            Boolean.toString(true));
        FileImpl.defaultCreationAttributes.setProperty(TIME_TO_LIVE_NAME,
            Long.toString(Long.MAX_VALUE));
    }

    //
    // The ProxyCache that this FileImpl instance uses to manage its
    // connection(s) with the Celeste node that handles its requests.
    //
    private final CelesteProxy.Cache proxyCache;

    //
    // The socket address of a Celeste proxy through which we interact with
    // Celeste.
    //
    private final InetSocketAddress socketAddr;

    //
    // The equivalent of "inode number" for this file, except that it is
    // unique across all Celeste confederations.  (If file systems built on
    // top of FileImpl were to use this value in place of smaller, values that
    // are unique only across a file system instance, then cross-filesystem
    // moves and renames would be possible.  But "ls -i" and other things that
    // manipulate file serial numbers would have a tough time of it.)
    //
    // See also the serialNumber field defined below.
    //
    private final FileIdentifier fileIdentifier;

    //
    // Metadata items whose values are common across all file versions
    // associated with this FileImpl's file.
    //
    // Note that most (or perhaps even all) of them do not change once they
    // are assigned (but they can't be final, since their true assignment
    // happens after construction time).
    //

    //
    // The version of the data encoding as sent back and forth over the wire.
    // A value of 0 indicates that this FileImpl hasn't yet communicated with
    // Celeste to complete its initialization.
    //
    private int             dataEncodingVersion;
    //
    // As per the creator's local clock; no attempt is made to reconcile
    // differing local clocks.
    //
    private long            createdTime;
    private boolean         signModifications;
    private long            timeToLive;
    //
    // XXX: Perhaps should have type ReplicationParameters.
    //
    private String          replicationParams;

    //
    // A value supplied by upper layers (e.g., CelesteFileSystem) to identify
    // this file uniquely with respect to some other object maintained by the
    // upper layer (in the case of CelesteFileSystem, the file system itself).
    // It is the upper layer's responsibility to maintain consistency between
    // this value and that of the fileId field.
    //
    // This value is conceptually unsigned and 0 is reserved as an out of band
    // "not defined" value; 0 is used in the absence of an upper layer or when
    // the upper layer declines to supply a value at create() time.
    //
    private long serialNumber = 0;

    //
    // This is the currently 'latest' version that FileImpl knows about.  It
    // is set after query and update interactions with Celeste, and is used on
    // updates to tell Celeste which version we think we're updating.  (If the
    // version is no longer current, Celeste's response will include the
    // now-current version information.)
    //
    private BeehiveObjectId latestVersionId;

    //
    // A map holding the version-specific metadata for each version we know
    // about.  In this implementation, once an entry is added to the map, it
    // is never replaced or removed.
    //
    private final Map<BeehiveObjectId, VersionMetadata> versionToMetadata =
        Collections.synchronizedMap(
            new HashMap<BeehiveObjectId, VersionMetadata>());

    //
    // The repository of cached data for this file.
    //
    private final BufferCache cache;

    //
    // The name by which this instance would be known to JMX.  The instance
    // isn't actually registered (and can't be since FileImpl doesn't
    // implement an MBean interface), but its name forms a prefix for the name
    // of its BufferCache member, which _is_ registered.
    //
    private final ObjectName jmxObjectName;

    /**
     * Creates a local handle for the file designated by {@code
     * fileIdentifier}, which may or may not actually exist in Celeste.
     * Interactions with Celeste are to the node with the given {@code
     * address} and connections to that node are managed via {@code
     * proxyCache}.
     *
     * @param fid           the file's globally unique identifier
     * @param address       the address of the Celeste node through which
     *                      access to the file is mediated
     * @param proxyCache    a cache for managing connections to the Celeste
     *                      node at  {@code address}
     *
     */
    public FileImpl(FileIdentifier fid, InetSocketAddress address,
            CelesteProxy.Cache proxyCache) {
        this(fid, address, proxyCache, null);
    }

    /**
     * Creates a local handle for the file designated by {@code fileId}, which
     * may or may not actually exist in Celeste.  The {@code address}
     * parameter designates a particular Celeste node as the entry point for
     * interactions with Celeste.
     *
     * @param address           a socket address through which the Celeste
     *                          node that will handle file operations accepts
     *                          client interactions
     * @param fileIdentifier    the file's globally unique identifier
     */
    public FileImpl(InetSocketAddress address, FileIdentifier fileIdentifier) {
        this(fileIdentifier, address, null);
    }

    //
    // Private constructor that handles setup common to all public
    // constructors.
    //
    private FileImpl(FileIdentifier fileIdentifier,
            InetSocketAddress socketAddr, CelesteProxy.Cache proxyCache,
            ObjectName jmxObjectNamePrefix) {
        this.fileIdentifier = fileIdentifier;
        this.socketAddr = socketAddr;

        //
        // If the caller doesn't supply a proxy cache, set one up here with
        // default parameters that might or might not match actual
        // requirements.
        //
        if (proxyCache == null) {
            proxyCache = new CelesteProxy.Cache(4,
                Time.secondsInMilliseconds(300));
        }
        this.proxyCache = proxyCache;

        //
        // These fields will be initialized after Celeste is first contacted.
        //
        this.latestVersionId = null;
        this.dataEncodingVersion = 0;
        this.createdTime = 0;

        //
        // JMX-related setup.
        //
        // Set this instance's jmxObjectName to disambiguate it from that of
        // other instances regardless of whether jmxObjectNamePrefix itself
        // suffices for disambiguation.  (That is, don't count on
        // jmxObjectNamePrefix to do so.)
        //
        // Ignore other problems in registration; allowing JMX manageability
        // for this object is nice, but not essential.
        //
        ObjectName jmxObjectName = null;
        //
        // Use our unique file id to uniquify the object name by which JMX
        // identifies this instance.  (By selecting a prefix of the fileId,
        // we're counting on the prefix length property setting to be
        // sufficiently large for this purpose.)
        //
//        String nameSuffix = String.format("FileImpl-%s",
//                DOLRLogFormatter.prefixObjId(this.getUniqueFileId()));
        String nameSuffix = String.format("FileImpl-%s", this.getUniqueFileId());
        try {
            //
            // If jmxObjectNamePrefix is null, use our package name as a
            // substitute for it in setting jmxObjectName.
            //
            if (jmxObjectNamePrefix == null) {
                String packageName = this.getClass().getPackage().getName();
                jmxObjectName = JMX.objectName(packageName, nameSuffix);
            } else {
                jmxObjectName = JMX.objectName(jmxObjectNamePrefix, nameSuffix);
            }
        } catch (MalformedObjectNameException e) {
            jmxObjectName = null;
        }
        this.jmxObjectName = jmxObjectName;

        //
        // Use the JMX object name to create a BufferCache instance whose name
        // is subordinate to it.
        //
        this.cache = new BufferCache(this.jmxObjectName);
    }

    /**
     * Prepare this {@code FileImpl} instance for reclamation by discarding
     * cached resources it holds.
     */
    public void dispose() {
        //
        // Flush out the buffer cache.  Assume that there's little to be
        // gained by discarding the cache itself.
        //
        this.cache.flush();
        //
        // N.B.  Since the proxy cache is shared among all instances, flushing
        // it is probably unwise.  Even if we could detect that this is the
        // last instance, it's not clear that the proxy cache should be
        // flushed.  So, at least for now, we don't.
        //
    }

    /**
     * <p>
     *
     * Creates a file using the supplied {@code attributes}, which should be
     * populated using attributes noted as being settable at creation time in
     * {@link sunlabs.celeste.client.filesystem.FileAttributes.Names}, with
     * attribute values given as strings.  If {@code clientProperties} is
     * non-{@code null} any properties it contains are included among the new
     * file's metadata and can later be retrieved with {@link
     * #getClientProperties()}.
     *
     * </p><p>
     *
     * This method can only be called on a <q>fresh</q> {@code FileImpl},
     * before any read/write etc.  has taken place.
     *
     * </p>
     *
     * @param attributes        attributes for the initial version of the file
     * @param clientProperties  client-supplied properties to be associated
     *                          with the file's initial version; {@code null}
     *                          if there are none
     * @param invokerCredential the credential to use to do the create
     * @param invokerPassword   a password allowing {@code invokerCredential}
     *                          to produce signatures
     * @param groupId           the object-id identifying the group of the
     *                          file's initial version
     *
     * @throws FileException.CredentialProblem
     *      if {@code invokerCredential} could not successfully sign the
     *      creation operation or encrypt the file's deletion token
     * @throws FileException.Deleted
     *      if this file formerly existed, but has been deleted
     * @throws FileException.Exists
     *      if the file has already been created
     */
    //
    // XXX: Throws IllegalArgumentException for some bogus arguments and
    //      FileException for others.  Ought to be consistent...
    //
    // XXX: groupid should perhaps be treated as an attribute.
    //
    public void create(Properties attributes, OrderedProperties clientProperties, Credential invokerCredential, char[] invokerPassword, BeehiveObjectId groupId)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.Exists,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {

        //
        // XXX: Profiling/Debugging support.
        //
        boolean debug = FileImpl.profileConfig.contains(
            "FileImpl.create");
        TimeProfiler timer = new TimeProfiler("FileImpl.create");

        //
        // Set up the delete token, its hash, and its encrypted form.
        //
        // XXX: The encryption is quite expensive.  We should consider
        //      supporting an alternative whereby we would use a symmetric key
        //      to encrypt the token and protect the symmetric key by
        //      encrypting and decrypting it with invokerCredential's private
        //      key.  If we could find a way to decrypt the symmetric key just
        //      once for a batch of create (and/or delete) operations, this
        //      approach might turn out to be an overall win.
        //
        //      Another possibility is simply to use the private key (or some
        //      chunk of it) as the symmetric key.  In this approach, we would
        //      need to add a bit of Celeste-protected metadata that indicates
        //      which way the delete token was encrypted.  (Unless, of course,
        //      we switch over completely and make no compatibility
        //      provisions.)
        //
        String dtoken = (new BeehiveObjectId()).toString();
        BeehiveObjectId dthash = new BeehiveObjectId(dtoken).getObjectId();
        byte[] tentativeEncryptedDeleteToken = null;
        try {
            tentativeEncryptedDeleteToken = invokerCredential.encrypt(dtoken.getBytes());
        } catch (Credential.Exception e) {
            throw new FileException.CredentialProblem(e);
        }
        timer.stamp("dtoken");

        //
        // XXX: Some preliminary experimentation about using symmetric
        //      delete token encryption.  The private key's length has been
        //      observed to be in the range of 633 to 635 bytes.
        //
        //byte[] encodedPrivateKey =
        //    invokerCredential.getPrivateKey(invokerPassword).getEncoded();
        //System.err.printf("FileImpl.create(): private key len %d%n",
        //    encodedPrivateKey.length);

        //
        // File ownership is derived from the credentials (as held in the
        // supplied profile) of the caller.
        //
        // XXX: The file's group should be derived from this profile as well,
        //      but there's as yet no way to single out a distinguished group
        //      from zero or more group attestations held in the profile.
        //
        BeehiveObjectId ownerId = invokerCredential.getObjectId();

        //
        // Build up tentative state that will be committed only after a
        // successful createFile() call to Celeste succeeds.  Start with state
        // derived from the attributes argument and then proceed with state
        // derived by other means.
        //

        CelesteACL tentativeACL = this.aclFromAttrs(attributes);
        int tentativeBlockSize = this.blockSizeFromAttrs(attributes);
        boolean tentativeCacheEnabled = this.cacheEnabledFromAttrs(attributes);
        String tentativeContentType = this.contentTypeFromAttrs(attributes);
        String tentativeReplicationParams =
            this.replicationParamsFromAttrs(attributes);
        long tentativeSerialNumber = this.serialNumberFromAttrs(attributes);
        boolean tentativeSignModifications =
            this.signModificationsFromAttrs(attributes);
        long tentativeTimeToLive = this.timeToLiveFromAttrs(attributes);
        long tentativeDeletionTimeToLive =
            this.deletionTimeToLiveFromAttrs(attributes);

        long tentativeCreatedTime = System.currentTimeMillis();

        //
        // Select those attributes from the ones calculated above that are to
        // be stored as Celeste-level metadata and package them up.
        //
        // Note that this code converts from attributes defined at the
        // CelesteFileSystem level to ones that FileImpl defines.  As a
        // shortcut, the two different attribute sets use the same key names,
        // but strictly speaking they're conceptually distinct.
        //
        // XXX: Might it be possible to avoid converting attribute values to
        //      and from their String (in-Properties) representations to
        //      their native types, instead simply transferring the reference
        //      to external representation from attributes to props?  (The fact
        //      that the *FromAttrs() methods supply default values probably
        //      will prevent doing so.)
        //
        OrderedProperties props = new OrderedProperties();
        props.put(BLOCK_SIZE_NAME, Integer.toString(tentativeBlockSize));
        props.put(CONTENT_TYPE_NAME, tentativeContentType);
        props.put(CREATED_TIME_NAME, Long.toString(tentativeCreatedTime));
        props.put(FileImpl.ENCRYPTED_DTOKEN_NAME,
            CelesteEncoderDecoder.toHexString(tentativeEncryptedDeleteToken));
        props.put(FILE_SERIAL_NUMBER_NAME,
            Long.toString(tentativeSerialNumber));
        props.put(IS_DELETED_NAME, Boolean.toString(false));
        props.put(METADATA_CHANGED_TIME_NAME,
            Long.toString(tentativeCreatedTime));
        props.put(MODIFIED_TIME_NAME, Long.toString(tentativeCreatedTime));
        props.put(REPLICATION_PARAMETERS_NAME, tentativeReplicationParams);
        props.put(SIGN_MODIFICATIONS_NAME,
            Boolean.toString(tentativeSignModifications));
        props.put(TIME_TO_LIVE_NAME, Long.toString(tentativeTimeToLive));
        props.put(DELETION_TIME_TO_LIVE_NAME,
            Long.toString(tentativeDeletionTimeToLive));
        props.put(FileImpl.VERSION_NAME,
            Integer.toString(defaultDataEncodingVersion));
        //
        // Ensure that each version of the file has a client properties map
        // (possibly empty) by forcing this initial version to have one.  Each
        // subsequent version either carries the previous version's client
        // properties map forward or replaces it with a new map.  (Note that
        // there's code here and there that checks whether a version's
        // clientProperties field is non-null; these checks ought to be
        // removed or replaced with asserts.)
        //
        if (clientProperties == null)
            clientProperties = new OrderedProperties();
        //
        // Put the client properties into serialized form, so that they can be
        // transmitted to and from Celeste along with the file's attributes.
        //
        String encoding = CelesteEncoderDecoder.toHexString(
            clientProperties.toByteArray());
        props.setProperty(CLIENT_METADATA_NAME, encoding);
        //
        // N.B.  The ClientMetaData initialized here is metadata from the
        // viewpoint of the Celeste layer.  It includes as one of its
        // components the client properties (from FileImpl's perspective)
        // supplied as argument to this method.
        //
        ClientMetaData fileMetaData = new ClientMetaData(props.toByteBuffer());

        timer.stamp("md_setup");

        CreateFileOperation operation = new CreateFileOperation(
            invokerCredential.getObjectId(),
            this.getFileIdentifier(),
            dthash, tentativeTimeToLive,
            tentativeBlockSize,
            tentativeReplicationParams, fileMetaData, ownerId, groupId,
            tentativeACL, tentativeSignModifications);

        //
        // Communicate with Celeste and use the results to initialize both the
        // version-independent part of this's state and the parts that depend
        // on the latest version.  Make sure that there's a well-defined
        // winner in a race with some other entity attempting to access this
        // file.  (For example, there could be a malicious client that
        // attempts to create this file twice in parallel.)
        //
        // XXX: Wrapping this code in a synchronized statement is at best an
        //      optimization.  When we get down to it, coordination comes from
        //      Celeste's response to the predicated createFile() call.
        //
        synchronized(this) {
            Credential.Signature sig = null;
            try {
                sig = invokerCredential.sign(invokerPassword,
                    operation.getId(), fileMetaData.getId());
            } catch (Credential.Exception e) {
                throw new FileException.CredentialProblem(e);
            }

            OrderedProperties metadata = null;
            CelesteAPI proxy = null;
            try {
                proxy = this.proxyCacheGetAndRemove(this.socketAddr);
                try {
                    timer.stamp("slop1");
                    metadata = proxy.createFile(operation, sig);
                    timer.stamp("celeste");
                } catch (IOException e) {
                    e.printStackTrace(System.err);
                    throw new FileException.CelesteFailed(e);
                } catch (ClassNotFoundException e) {
                    //
                    // Indicates that CelesteProxy failed to understand the
                    // encoding of a reply from Celeste proper.  So
                    // CelesteFailed seems right.
                    //
                    e.printStackTrace(System.err);
                    throw new FileException.CelesteFailed(e);
                } catch (CelesteException.AccessControlException e) {
                    throw new FileException.PermissionDenied(e);
                } catch (CelesteException.IllegalParameterException e) {
                    throw new FileException.Runtime(e);
                } catch (CelesteException.AlreadyExistsException e) {
                    throw new FileException.Exists(e);
                } catch (CelesteException.DeletedException e) {
                    throw new FileException.Deleted(e);
                } catch (CelesteException.CredentialException e) {
                    throw new FileException.CredentialProblem(e);
                } catch (CelesteException.RuntimeException e) {
                    throw new FileException.Runtime(e);
                } catch (CelesteException.NotFoundException e) {
                    throw new FileException.NotFound(e);
                } catch (CelesteException.NoSpaceException e) {
                  throw new FileException.CapacityExceeded(e);
                } catch (CelesteException.VerificationException e) {
                  throw new FileException.Runtime(e);
                }
            } finally {
                this.proxyCacheAddAndEvictOld(this.socketAddr, proxy);
            }

            timer.stamp("slop2");
            //
            // We now have a base file version id (the one that was just
            // allocated for us).
            //
            this.latestVersionId = new BeehiveObjectId(
                metadata.getProperty(CelesteAPI.VOBJECTID_NAME));

            OrderedProperties retrievedProps =
                setOrCheckCommonMetadata(fileMetaData, metadata);
            extractVersionMetadata(this.latestVersionId, retrievedProps,
                metadata);

            //
            // The cacheEnabled attribute gets special treatment.  It is not
            // version-specific, but also can be changed at arbitrary times.
            // Handle it by calling its setter here.  Note that the call is
            // deferred until after everything else has succeeded, so that the
            // attribute setting won't change if the create fails.
            //
            this.setCacheEnabled(tentativeCacheEnabled);

            timer.stamp("md_ingest");
            if (debug) {
                //
                // 1.  overall time
                // 2.  time to create and encrypt the delete token
                // 3.  time to set up metadata sent to Celeste
                // 4.  slop1
                // 5.  Celeste communication time
                // 6.  slop2
                // 7.  time to process the metadata returned from Celeste
                //
                timer.printCSV(FileImpl.profileOutputFileName);
            }
        }
    }

    //
    // The following two methods provide shorthand access to information
    // provided by getFileIdentifier().
    //

    /**
     * Get this file's unique file identifier.
     *
     * @return  the unique file identifier
     */
    public BeehiveObjectId getUniqueFileId() {
        return this.fileIdentifier.getFileId();
    }

    /**
     * Get the object-id of the profile denoting the name space
     * in which this file resides.
     *
     * @return  the file's name space Id
     */
    public BeehiveObjectId getNameSpaceId() {
        return this.fileIdentifier.getNameSpaceId();
    }

    /**
     * Get the identity of the file this {@code FileImpl} object represents.
     *
     * @return  the {@code FileIdentifier} object identifying this {@code
     *          FileImpl}'s file
     */
    public FileIdentifier getFileIdentifier() {
        return this.fileIdentifier;
    }

    //
    // Methods to fetch various file attributes.  Note that their results
    // could be invalid (in the sense of referring to a no-longer-latest file
    // version) by the time control returns to the caller unless the calls are
    // part of a critical section.
    //
    // XXX: Versions of these methods that take a version id as argument?
    //      Those variants wouldn't be subject to the race problem.
    //
    // XXX: Need accessors for new attributes such as signModifications,
    //      timeToLive, etc.  (Note that getAttributes() can be used for this
    //      purpose, so the need is not as acute as it was before that
    //      method's advent.)
    //

    /**
     * <p>
     *
     * Obtain metadata pertinent to the most recent version of this file and
     * return the length attribute from that metadata.
     *
     * </p><p>
     *
     * Unless some external means is used to prevent it, it is possible that
     * the version could be superceded by a new one between the time of
     * inquiry and use of its results, so that the length becomes out of date.
     *
     * </p>
     *
     * @return  the length of the current version of the file
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public long getFileLength() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.getFileLength(true);
    }

    /**
     * <p>
     *
     * Return the length of the current version of this file.  If {@code
     * refetch} is set, contact Celeste to obtain up to date information;
     * otherwise rely on information from the most recent previous contact.
     *
     * </p><p>
     *
     * Even when {@code refetch} is set, unless some external means is used to
     * prevent it, the then-current version could be superceded by a new one
     * between the time of inquiry and use of its results, rendering the
     * method's result obsolete.
     *
     * </p>
     *
     * @param refetch   if {@code true}, refetch file metadata from Celeste
     *                  before reporting results
     *
     * @return  the length of the most recently observed version of this file
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public long getFileLength(boolean refetch) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        VersionMetadata versionMetadata = refetch ? this.refreshMetadata() :
            this.versionToMetadata.get(this.latestVersionId);
        return versionMetadata.fileLength;
    }

    /**
     * <p>
     *
     * Obtain metadata pertinent to the most recent version of this file and
     * return the modification time attribute from that metadata.  The time is
     * reported with respect to the local clock of the entity that last
     * modified the file.
     *
     * </p><p>
     *
     * Unless some external means is used to prevent it, it is possible that
     * the version could be superceded by a new one between the time of
     * inquiry and use of its results, so that the modification time becomes
     * out of date.
     *
     * </p>
     *
     * @return  the modification time of the current version of the file,
     *          expressed in milliseconds since midnight, January 1, 1970 UTC
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public long getModifiedTime() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.getModifiedTime(true);
    }

    /**
     * <p>
     *
     * Return the modification time of the current version of this file.  If
     * {@code refetch} is set, contact Celeste to obtain up to date
     * information; otherwise rely on information from the most recent
     * previous contact.  The time is reported with respect to the local clock
     * of the entity that last modified the file.
     *
     * </p><p>
     *
     * Even when {@code refetch} is set, unless some external means is used to
     * prevent it, the then-current version could be superceded by a new one
     * between the time of inquiry and use of its results, rendering the
     * method's result obsolete.
     *
     * </p>
     *
     * @param refetch   if {@code true}, refetch file metadata from Celeste
     *                  before reporting results
     *
     * @return  the modification time of the most recently observed version of
     *          this file, expressed in milliseconds since midnight, January
     *          1, 1970 UTC
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public long getModifiedTime(boolean refetch) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        VersionMetadata versionMetadata = refetch ? this.refreshMetadata() :
            this.versionToMetadata.get(this.latestVersionId);
        return versionMetadata.modifiedTime;
    }

    /**
     * <p>
     *
     * Obtain metadata pertinent to the most recent version of this file and
     * return the client metadata change time attribute from that metadata.
     * The time is reported with respect to the local clock of the entity that
     * last modified the file.
     *
     * </p><p>
     *
     * Unless some external means is used to prevent it, it is possible that
     * the version could be superceded by a new one between the time of
     * inquiry and use of its results, so that the modification time becomes
     * out of date.
     *
     * </p>
     *
     * @return  the client metadata change time of the current version of the
     *          file, expressed in milliseconds since midnight, January 1,
     *          1970 UTC
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public long getClientMetadataChangeTime() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.getClientMetadataChangeTime(true);
    }

    /**
     * <p>
     *
     * Return the client metadata change time of the current version of this
     * file.  If {@code refetch} is set, contact Celeste to obtain up to date
     * information; otherwise rely on information from the most recent
     * previous contact.  The time is reported with respect to the local clock
     * of the entity that last modified the file.
     *
     * </p><p>
     *
     * Even when {@code refetch} is set, unless some external means is used to
     * prevent it, the then-current version could be superceded by a new one
     * between the time of inquiry and use of its results, rendering the
     * method's result obsolete.
     *
     * </p>
     *
     * @param refetch   if {@code true}, refetch file metadata from Celeste
     *                  before reporting results
     *
     * @return  the client metadata change time of the most recently observed
     *          version of this file, expressed in milliseconds since
     *          midnight, January 1, 1970 UTC
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public long getClientMetadataChangeTime(boolean refetch) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        VersionMetadata versionMetadata = refetch ? this.refreshMetadata() :
            this.versionToMetadata.get(this.latestVersionId);
        return versionMetadata.metadataChangedTime;
    }

    /**
     * <p>
     *
     * Obtain metadata pertinent to the most recent version of this file and
     * return the object-id of that version.
     *
     * </p><p>
     *
     * Unless some external means is used to prevent it, it is possible that
     * the version could be superceded by a new one between the time of
     * inquiry and use of its results, rendering the method's result obsolete.
     *
     * </p>
     *
     * @return  the object-id of the current version of the file
     *
     * @throws FileException.CelesteFailed
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public BeehiveObjectId getLatestVersionId() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.getLatestVersionId(true);
    }

    /**
     * <p>
     *
     * Return the object-id of the current version of this file.
     * If {@code refetch} is set, contact Celeste to obtain up to date
     * information; otherwise rely on information from the most recent
     * previous contact.
     *
     * </p><p>
     *
     * When {@code refetch} is not set, this method is appropriate as a
     * precursor to a modification operation that is predicated on a
     * particular version of the file being current.  Note that when such an
     * operation fails, it updates the most recently observed version, so that
     * a loop whose body consists of a non-refetching call to this method
     * followed by a modification operation predicated on the value it reports
     * is guaranteed to perform at least as well (and be as correct as) one
     * starting with a refetching call.
     *
     * </p><p>
     *
     * Even when {@code refetch} is set, unless some external means is used to
     * prevent it, the then-current version could be superceded by a new one
     * between the time of inquiry and use of its results, rendering the
     * method's result obsolete.
     *
     * </p>
     *
     * @param refetch   if {@code true}, refetch file metadata from Celeste
     *                  before reporting results
     *
     * @return  the object-id of the most recently observed version
     *          of this file
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public BeehiveObjectId getLatestVersionId(boolean refetch) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        if (refetch)
            this.refreshMetadata();
        else
            this.ensureMetadata();
        return this.latestVersionId;
    }

    /**
     * <p>
     *
     * Obtain metadata pertinent to the most recent version of this file and
     * return the portion of it that was previously supplied as client
     * properties.
     *
     * </p><p>
     *
     * Unless some external means is used to prevent it, it is possible that
     * the version could be superceded by a new one between the time of
     * inquiry and use of its results, so that the client metadata becomes out
     * of date.
     *
     * </p>
     *
     * @return  the client properties associated with this version of the file
     */
    public OrderedProperties getClientProperties() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.getClientProperties(true);
    }

    /**
     * <p>
     *
     * Return the client properties of the current version of this file.  If
     * {@code refetch} is set, contact Celeste to obtain up to date
     * information; otherwise rely on information from the most recent
     * previous contact.
     *
     * </p><p>
     *
     * Even when {@code refetch} is set, unless some external means is used to
     * prevent it, the then-current version could be superceded by a new one
     * between the time of inquiry and use of its results, rendering the
     * method's result obsolete.
     *
     * </p>
     *
     * @param refetch   if {@code true}, refetch file metadata from Celeste
     *                  before reporting results
     *
     * @return  the client properties of the most recently observed version of
     *          this file
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public OrderedProperties getClientProperties(boolean refetch) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        VersionMetadata versionMetadata = refetch ? this.refreshMetadata() :
            this.versionToMetadata.get(this.latestVersionId);
        return versionMetadata.clientProperties;
    }

    /**
     * <p>
     *
     * Obtain metadata pertinent to the most recent version of this file and
     * return the file owner id attribute from that metadata.
     *
     * </p><p>
     *
     * Unless some external means is used to prevent it, it is possible that
     * the version could be superceded by a new one between the time of
     * inquiry and use of its results, so that the owner id becomes out of
     * date.
     *
     * </p>
     *
     * @return the owner id attribute of the current version of the file
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public BeehiveObjectId getOwnerId() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.getOwnerId(true);
    }

    /**
     * <p>
     *
     * Return the file owner id attribute of the current version of this file.
     * If {@code refetch} is set, contact Celeste to obtain up to date
     * information; otherwise rely on information from the most recent
     * previous contact.
     *
     * </p><p>
     *
     * Even when {@code refetch} is set, unless some external means is used to
     * prevent it, the then-current version could be superceded by a new one
     * between the time of inquiry and use of its results, rendering the
     * method's result obsolete.
     *
     * </p>
     *
     * @param refetch   if {@code true}, refetch file metadata from Celeste
     *                  before reporting results
     *
     * @return  the owner id attribute of the most recently observed version
     *          of this file
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public BeehiveObjectId getOwnerId(boolean refetch) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        VersionMetadata versionMetadata = refetch ? this.refreshMetadata() :
            this.versionToMetadata.get(this.latestVersionId);
        return versionMetadata.ownerId;
    }

    /**
     * <p>
     *
     * Obtain metadata pertinent to the most recent version of this file and
     * return the file group id attribute from that metadata.
     *
     * </p><p>
     *
     * Unless some external means is used to prevent it, it is possible that
     * the version could be superceded by a new one between the time of
     * inquiry and use of its results, so that the group id becomes out of
     * date.
     *
     * </p>
     *
     * @return the group id attribute of the current version of the file
     *
     * @throws FileException.CelesteFailed
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public BeehiveObjectId getGroupId() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.getGroupId(true);
    }

    /**
     * <p>
     *
     * Return the file group id attribute of the current version of this file.
     * If {@code refetch} is set, contact Celeste to obtain up to date
     * information; otherwise rely on information from the most recent
     * previous contact.
     *
     * </p><p>
     *
     * Even when {@code refetch} is set, unless some external means is used to
     * prevent it, the then-current version could be superceded by a new one
     * between the time of inquiry and use of its results, rendering the
     * method's result obsolete.
     *
     * </p>
     *
     * @param refetch   if {@code true}, refetch file metadata from Celeste
     *                  before reporting results
     *
     * @return  the group id attribute of the most recently observed version
     *          of this file
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public BeehiveObjectId getGroupId(boolean refetch) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        VersionMetadata versionMetadata = refetch ? this.refreshMetadata() :
            this.versionToMetadata.get(this.latestVersionId);
        return versionMetadata.groupId;
    }

    /**
     * <p>
     *
     * Obtain metadata pertinent to the most recent version of this file and
     * return the access control list from that metadata.
     *
     * </p><p>
     *
     * Unless some external means is used to prevent it, it is possible that
     * the version could be superceded by a new one between the time of
     * inquiry and use of its results, so that the access control list becomes
     * out of date.
     *
     * </p>
     *
     * @return the access control list of the current version of the file
     *
     * @throws FileException.CelesteFailed
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public CelesteACL getACL() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.getACL(true);
    }

    /**
     * <p>
     *
     * Return the access control list of the current version of this file.  If
     * {@code refetch} is set, contact Celeste to obtain up to date
     * information; otherwise rely on information from the most recent
     * previous contact.
     *
     * </p><p>
     *
     * Even when {@code refetch} is set, unless some external means is used to
     * prevent it, the then-current version could be superceded by a new one
     * between the time of inquiry and use of its results, rendering the
     * method's result obsolete.
     *
     * </p>
     *
     * @param refetch   if {@code true}, refetch file metadata from Celeste
     *                  before reporting results
     *
     * @return  the access control list of the most recently observed version
     *          of this file
     *
     * @throws FileException.CelesteFailed
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public CelesteACL getACL(boolean refetch) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        VersionMetadata versionMetadata = refetch ? this.refreshMetadata() :
            this.versionToMetadata.get(this.latestVersionId);
        return versionMetadata.acl;
    }

    /**
     * <p>
     *
     * Obtain metadata pertinent to the most recent version of this file and
     * return the {@link
     * sunlabs.celeste.client.filesystem.FileAttributes.Names#DELETION_TIME_TO_LIVE_NAME
     * deletion time to live} attribute from that metadata.
     *
     * </p><p>
     *
     * Unless some external means is used to prevent it, it is possible that
     * the version could be superceded by a new one between the time of
     * inquiry and use of its results, so that the access control list becomes
     * out of date.
     *
     * </p>
     *
     * @return  the deletion time to live attribute of the current version of
     *          the file
     *
     * @throws FileException.CelesteFailed
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public long getDeletionTimeToLive() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.getDeletionTimeToLive(true);
    }

    /**
     * <p>
     *
     * Return the {@link
     * sunlabs.celeste.client.filesystem.FileAttributes.Names#DELETION_TIME_TO_LIVE_NAME
     * deletion time to live} attribute of the current version of this file.
     * If {@code refetch} is set, contact Celeste to obtain up to date
     * information; otherwise rely on information from the most recent
     * previous contact.
     *
     * </p><p>
     *
     * Even when {@code refetch} is set, unless some external means is used to
     * prevent it, the then-current version could be superceded by a new one
     * between the time of inquiry and use of its results, rendering the
     * method's result obsolete.
     *
     * </p>
     *
     * @param refetch   if {@code true}, refetch file metadata from Celeste
     *                  before reporting results
     *
     * @return  the deletion time to live attribute of the most recently
     *          observed version of this file
     *
     * @throws FileException.CelesteFailed
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public long getDeletionTimeToLive(boolean refetch) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        VersionMetadata versionMetadata = refetch ? this.refreshMetadata() :
            this.versionToMetadata.get(this.latestVersionId);
        return versionMetadata.deletionTimeToLive;
    }

    /**
     * <p>
     *
     * Obtain metadata pertinent to the most recent version of this file and
     * return the preferred block size from that metadata.
     *
     * </p><p>
     *
     * Unless some external means is used to prevent it, it is possible that
     * the version could be superceded by a new one between the time of
     * inquiry and use of its results, so that the preferred block size
     * becomes out of date.
     *
     * </p>
     *
     * @return the preferred block size for the current version of the file
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    //
    // XXX: The javadoc comment ought to be augmented with a discussion of
    //      what the preferred block size is.  In the current implementation,
    //      it matches the default BObject size, so using this size for reads
    //      will be maximally efficient.  However, since each distinct write
    //      creates a new version (no transactions yet...), version
    //      proliferation needs to be traded off against i/o efficiency for
    //      writes.
    //
    public int getPreferredBlockSize() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.getPreferredBlockSize(true);
    }

    /**
     * <p>
     *
     * Return the preferred block size for the current version of this file.
     * If {@code refetch} is set, contact Celeste to obtain up to date
     * information; otherwise rely on information from the most recent
     * previous contact.
     *
     * </p><p>
     *
     * Even when {@code refetch} is set, unless some external means is used to
     * prevent it, the then-current version could be superceded by a new one
     * between the time of inquiry and use of its results, rendering the
     * method's result obsolete.
     *
     * </p>
     *
     * @param refetch   if {@code true}, refetch file metadata from Celeste
     *                  before reporting results
     *
     * @return  the preferred block size for the most recently observed
     *          version of this file
     *
     * @throws FileException.CelesteFailed
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.Runtime
     */
    public int getPreferredBlockSize(boolean refetch) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        VersionMetadata versionMetadata = refetch ? this.refreshMetadata() :
            this.versionToMetadata.get(this.latestVersionId);
        return versionMetadata.blockSize;
    }

    /**
     * <p>
     *
     * Return {@code true} if the file has been marked as deleted and {@code
     * false} otherwise.
     *
     * </p><p>
     *
     * Once the file has been marked as deleted, it cannot revert to being
     * non-deleted.  However, it is possible that concurrent activity could
     * invalidate a {@code false} response by the time control returns to the
     * caller.
     *
     * </p>
     *
     * @return  {@code true} if the file has been deleted, {@code false}
     *          otherwise
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public boolean getIsDeleted() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.getIsDeleted(true);
    }

    /**
     * <p>
     *
     * Return {@code true} if the file has been marked as deleted and {@code
     * false} otherwise.  If {@code refetch} is set, contact Celeste to obtain
     * up to date information; otherwise rely on information from the most
     * recent previous contact.
     *
     * </p><p>
     *
     * Once the file has been marked as deleted, it cannot revert to being
     * non-deleted.  However, it is possible that concurrent activity could
     * invalidate a {@code false} response by the time control returns to the
     * caller.
     *
     * </p>
     *
     * @param refetch   if {@code true}, refetch file metadata from Celeste
     *                  before reporting results
     *
     * @return  {@code true} if the file has been deleted, {@code false}
     *          otherwise
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public boolean getIsDeleted(boolean refetch) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        VersionMetadata versionMetadata = refetch ? this.refreshMetadata() :
            this.versionToMetadata.get(this.latestVersionId);
        return versionMetadata.isDeleted;
    }

    /**
     * Return the time that this file was created, or {@code 0} if this file
     * does not exist.  The time is reported with respect to the local clock
     * of the entity that created the file.
     *
     * @return  the file's creation time expressed in milliseconds since
     *          midnight, January 1, 1970 UTC
     */
    public long getCreationTime() {
        return this.createdTime;
    }

    /**
     * <p>
     *
     * Obtain metadata pertinent to the most recent version of this file and
     * return the content type value from that metadata.
     *
     * </p><p>
     *
     * Unless some external means is used to prevent it, it is possible that
     * the version could be superceded by a new one between the time of
     * inquiry and use of its results, so that the preferred block size
     * becomes out of date.
     *
     * </p>
     *
     * @return the file's content type
     */
    public String getContentType() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.getContentType(true);
    }

    /**
     * <p>
     *
     * Return the content type value for the current version of this file.  If
     * {@code refetch} is set, contact Celeste to obtain up to date
     * information; otherwise rely on information from the most recent
     * previous contact.
     *
     * </p><p>
     *
     * Even when {@code refetch} is set, unless some external means is used to
     * prevent it, the then-current version could be superceded by a new one
     * between the time of inquiry and use of its results, rendering the
     * method's result obsolete.
     *
     * </p>
     *
     * @param refetch   if {@code true}, refetch file metadata from Celeste
     *                  before reporting results
     *
     * @return  the content type value for the most recently observed version
     *          of this file
     */
    public String getContentType(boolean refetch) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        VersionMetadata versionMetadata = refetch ? this.refreshMetadata() : this.versionToMetadata.get(this.latestVersionId);
        return versionMetadata.contentType;
    }

    /**
     * <p>
     *
     * Return this file's serial number if it was set at
     * {@link #create(OrderedProperties, OrderedProperties, Credential, char[], BeehiveObjectId) create()}
     * time or {@code 0} if not.
     *
     * </p><p>
     *
     * The serial number attribute is an integral value that higher level
     * software can set to act as a POSIX-like file serial number (often known
     * as an inode or vnode number}.  If no such higher level software is
     * present, its value defaults to zero.
     *
     * </p>
     *
     * @return  the file's serial number if present; {@code 0} otherwise
     *
     * @throws FileException.CelesteInaccessible
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Runtime
     */
    public long getSerialNumber() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        //
        // Avoid unnecessary communication with Celeste if possible; once
        // serialNumber is set, it's immutable.
        //
        if (this.serialNumber != 0)
            return this.serialNumber;

        refreshMetadata();
        return this.serialNumber;
    }

    //
    // XXX: Add static getDefaultCreationAttributes() method.  It should
    //      return a copy of FileImpl.defaultCreationAttributes, so that
    //      callers can't mess up subsequent creation.  (Probably shouldn't be
    //      located here, but rather with other static methods.
    //
    // XXX: Add a setDefaultCreationAttributes() method to match?  Perhaps is
    //      overkill, since upper levels allow doing so, and doing it here
    //      would probably be overkill.
    //
    // XXX: It would be good to have a setAttributes() method to match
    //      getAttributes() immediately below.  It would confine itself to
    //      setting only attributes listed in FileAttributes.settableSet (as
    //      those are the ones that can be set after a file has been created).
    //      But there's a difficulty:  The method should be atomic, creating a
    //      single new version of the file.  However, the ACL attribute, as
    //      something Celeste understands, must be set separately from the
    //      other attributes, which to Celeste are just uninterpreted client
    //      metadata.  So atomicity goes out the window.
    //

    /**
     * Returns a map containing values for each of the attributes contained in
     * {@code attributes} that appear in {@link FileAttributes#attributeSet}.
     * If {@code refetch} is set, contact Celeste to obtain up to date
     * information; otherwise rely on information from the most recent
     * previous contact.
     *
     * @param attributes    a set containing the names of the attributes of
     *                      interest
     *
     * @return a map containing the requested attributes in string form
     */
    public OrderedProperties getAttributes(Set<String> attributes,
            boolean refetch)
        throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        OrderedProperties result = new OrderedProperties();
        synchronized(this) {
            if (refetch)
                this.refreshMetadata();
            else
                this.ensureMetadata();
            for (String attrName : attributes) {
                if (!FileAttributes.attributeSet.contains(attrName))
                    continue;
                //
                // Unfortunately, there's no better way to do this than to
                // slog through each attribute individually.
                //
                if (attrName.equals(ACL_NAME)) {
                    //
                    // XXX: The ACL code needs to be reworked to provide a
                    //      string form that can subsequently be read back in
                    //      and converted to an ACL.  Lacking that, this
                    //      accessor is a cheat.
                    //
                    result.put(ACL_NAME, this.getACL(false).toString());
                } else if (attrName.equals(BLOCK_SIZE_NAME)) {
                    result.put(BLOCK_SIZE_NAME,
                        Integer.toString(this.getPreferredBlockSize()));
                } else if (attrName.equals(CACHE_ENABLED_NAME)) {
                    result.put(CACHE_ENABLED_NAME,
                        Boolean.toString(this.isCacheEnabled()));
                } else if (attrName.equals(CLIENT_METADATA_NAME)) {
                    //
                    // XXX: Another problematic attribute:  There's no
                    //      guarantee that client-supplied metadata has a
                    //      meaningful string form.
                    //
                    OrderedProperties md = this.getClientProperties(false);
                    if (md != null)
                    result.put(CLIENT_METADATA_NAME, md.toString());
                } else if (attrName.equals(CONTENT_TYPE_NAME)) {
                    result.put(CONTENT_TYPE_NAME, this.getContentType());
                } else if (attrName.equals(CREATED_TIME_NAME)) {
                    result.put(CREATED_TIME_NAME,
                        Long.toString(this.getCreationTime()));
                } else if (attrName.equals(DELETION_TIME_TO_LIVE_NAME)) {
                    result.put(DELETION_TIME_TO_LIVE_NAME,
                        Long.toString(this.getDeletionTimeToLive(false)));
                } else if (attrName.equals(FILE_SERIAL_NUMBER_NAME)) {
                    result.put(FILE_SERIAL_NUMBER_NAME,
                        Long.toString(this.getSerialNumber()));
                } else if (attrName.equals(IS_DELETED_NAME)) {
                    result.put(IS_DELETED_NAME,
                        Boolean.toString(this.getIsDeleted(false)));
                } else if (attrName.equals(METADATA_CHANGED_TIME_NAME)) {
                    result.put(METADATA_CHANGED_TIME_NAME,
                        Long.toString(this.getClientMetadataChangeTime(false)));
                } else if (attrName.equals(MODIFIED_TIME_NAME)) {
                    result.put(MODIFIED_TIME_NAME,
                        Long.toString(this.getModifiedTime(false)));
                } else if (attrName.equals(REPLICATION_PARAMETERS_NAME)) {
                    result.put(REPLICATION_PARAMETERS_NAME,
                        this.replicationParams);
                } else if (attrName.equals(SIGN_MODIFICATIONS_NAME)) {
                    result.put(SIGN_MODIFICATIONS_NAME,
                        Boolean.toString(this.signModifications));
                } else if (attrName.equals(TIME_TO_LIVE_NAME)) {
                    result.put(TIME_TO_LIVE_NAME,
                        Long.toString(this.timeToLive));
                }
            }
        }
        return result;
    }

    /**
     * Create a new version of the file that is identical to the current
     * version except for its client properties, which are replaced with those
     * contained in {@code clientProperties}.
     *
     * @param clientProperties  an {@code OrderedProperties} object whose
     *                          entries are property items that will replace
     *                          all currently extant client property items
     * @param invokerCredential the credential to be used to authenticate the
     *                          new file version
     * @param invokerPassword   the password associated with {@code
     *                          invokerCredential}
     *
     * @throws FileException.BadVersion
     * @throws FileException.CapacityExceeded
     * @throws FileException.CelesteFailed
     * @throws FileException.CelesteInaccessible
     * @throws FileException.CredentialProblem
     * @throws FileException.Deleted
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.PermissionDenied
     * @throws FileException.RetriesExceeded
     * @throws FileException.Runtime
     * @throws FileException.ValidationFailed
     */
    //
    // XXX: Supply a predicated version of this method as well?
    //
    public void setClientProperties(OrderedProperties clientProperties, Credential invokerCredential, char[] invokerPassword)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        doTruncate(-1, false, clientProperties, invokerCredential, invokerPassword, null);
    }

    /**
     * <p>
     *
     * Fill the range in {@code destBuffer} starting at {@code
     * destOffset} for no more than {@code maxReadLen} bytes with data
     * starting at {@code fileOffset} from the file.
     *
     * </p><p>
     *
     * The read may be satisfied with data buffered locally from previous
     * reads or writes.  In this case only locally buffered data will be
     * returned until the local buffer is exhausted or invalidated.
     *
     * </p><p>
     *
     * The read may return less data than requested, either because it
     * consumed the entire local buffer or because the file contains
     * insufficient data to satisfy the read.
     *
     * </p>
     *
     * @param readerCredential  the credential to be used to authenticate the
     *                          read
     * @param readerPassword    the password associated with {@code
     *                          readerCredential}
     * @param destBuffer        a byte array to receive the data read from the
     *                          local buffer or file
     * @param destOffset        the starting position in {@code destBuffer}
     *                          into which data should be copied
     * @param maxReadLen        the maximum number of bytes to copy into
     *                          {@code destBuffer}
     * @param fileOffset        the beginning position in the file from which
     *                          to read (possibly via an intermediate local
     *                          buffer)
     *
     * @return  the number of bytes read
     */
    //
    // XXX: Reconsider the "satisfy from local buffering" semantics.  They're
    //      dubious on two counts:  their tendency to result in short reads;
    //      and the possibility that the data may be stale (the file has
    //      advanced to a new version).  The staleness concern is partially
    //      countervailed by the fact that one can never (absent higher level
    //      locking) guarantee that the version hasn't advanced by the time
    //      the results of the read are acted upon and by the availability of
    //      interfaces (getFileLength() or getLatestVersionId()) to advance to
    //      the now-latest version.
    //
    public int read(Credential readerCredential, char[] readerPassword,
            byte[] destBuffer, int destOffset, int maxReadLen, long fileOffset)
        throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {
        ExtentBuffer extentBuffer = this.read(readerCredential, readerPassword, maxReadLen, fileOffset);
        int length = min(maxReadLen, extentBuffer.getLength());
        extentBuffer.get(destBuffer, destOffset, length).position(0);
        return extentBuffer.getLength();
    }

    /**
     * <p>
     *
     * Read bytes from position {@code fileOffset} onward in the file, filling
     * slots in {@code destBuffer} starting at its position and not going
     * beyond its limit.  Upon successful return, {@code destBuffer}'s
     * position will have advanced by the number of bytes read.
     *
     * </p><p>
     *
     * The read may be satisfied with data buffered locally from previous
     * reads or writes.  In this case only locally buffered data will be
     * returned until the local buffer is exhausted or invalidated.
     *
     * </p><p>
     *
     * The read may return less data than requested, either because it
     * consumed the entire local buffer or because the file contains
     * insufficient data to satisfy the read.
     *
     * </p>
     *
     * @param readerCredential  the credential to be used to authenticate the
     *                          read
     * @param readerPassword    the password associated with {@code
     *                          readerCredential}
     * @param destBuffer        a byte buffer to receive the data read from
     *                          the local buffer or file
     * @param fileOffset        the beginning position in the file from which
     *                          to read (possibly via an intermediate local
     *                          buffer)
     *
     * @return  the number of bytes read
     */
    public int read(Credential readerCredential, char[] readerPassword, ByteBuffer destBuffer, long fileOffset)
        throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {
        //
        // Don't read more than the empty slots in the buffer can hold.
        //
        int destPos0 = destBuffer.position();
        int destLim = destBuffer.limit();
        int maxReadLen = destLim - destPos0;
        ExtentBuffer extentBuffer = this.read(
            readerCredential, readerPassword, maxReadLen, fileOffset);
        //
        // Copy into destBuffer.
        //
        if (destBuffer.hasArray()) {
            byte[] dest = destBuffer.array();
            int offset = destBuffer.arrayOffset();
            //
            // Constrain the transfer to no more than what the read produced
            // and what the destination can hold.
            //
            int length = min(extentBuffer.remaining(), maxReadLen);
            extentBuffer.get(dest, offset + destPos0, length);
            destBuffer.position(destPos0 + length);
            return length;
        } else {
            //
            // Fall back to a byte by byte copy.
            //
            int srcLim = extentBuffer.limit();
            int destPos = destPos0;
            while (destPos < destLim) {
                if (extentBuffer.position() >= srcLim)
                    break;
                destBuffer.put(extentBuffer.get());
                destPos++;
            }
            return destPos - destPos0;
        }
    }

    /**
     * <p>
     *
     * Read at most {@code maxLength} bytes starting at {@code fileOffset}
     * from the file, returning the data read in a read-only extent buffer.
     *
     * </p><p>
     *
     * The read may be satisfied with data buffered locally from previous
     * reads or writes.  In this case only locally buffered data will be
     * returned until the local buffer is exhausted or invalidated.
     *
     * </p><p>
     *
     * The read may return less data than requested, either because it
     * consumed the entire local buffer or because the file contains
     * insufficient data to satisfy the read.  To determine how many bytes
     * were actually read, invoke the {@link
     * sunlabs.titan.util.ExtentBuffer#remaining() remaining()} method on
     * the resulting extent buffer.
     *
     * </p>
     *
     * @param readerCredential  the credential to be used to authenticate the
     *                          read
     * @param readerPassword    the password associated with {@code
     *                          readerCredential}
     * @param maxLength         the maximum number of bytes to copy into
     *                          {@code destBuffer}
     * @param fileOffset        the beginning position in the file from which
     *                          to read (possibly via an intermediate local
     *                          buffer)
     *
     * @return  a read-only {@code ExtentBuffer} containing the data read
     *
     * @throws  IllegalArgumentException
     *      if either {@code maxLength} or {@code fileOffset} is negative
     */
    //
    // XXX: Vulnerable to zero-length extent buffers.  (There shouldn't be
    //      any, but if there are, defensive programming dictates that this
    //      method not be fooled by them.)
    //
    public ExtentBuffer read(Credential readerCredential, char[] readerPassword, int maxLength, long fileOffset)
        throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {

        //
        // Sanity checks...
        //
        if (maxLength < 0)
            throw new IllegalArgumentException(
                "requested read length must be non-negative");
        if (fileOffset < 0)
            throw new IllegalArgumentException(
                "attempt to read a negative offset");

        //
        // If the file has been marked as deleted, that's it.  There is
        // nothing to read from (or even open, for that matter).
        //
        VersionMetadata versionMetadata =
            this.versionToMetadata.get(this.latestVersionId);
        if (versionMetadata.isDeleted) {
            throw new FileException.Deleted();
        }

        //
        // Don't attempt to read past the end of the file.
        //
        long requestedEndOffset = fileOffset + maxLength;
        if (requestedEndOffset > versionMetadata.fileLength)
            requestedEndOffset = versionMetadata.fileLength;
        //
        // Don't allow the i/o to extend beyond an alignment boundary.  Use
        // the block size recorded for the version we're reading to determine
        // the required alignment.
        //
        // XXX: May want to rethink this.  Under the covers, Celeste will be
        //      sizing its BObjects to the block size, and having Celeste
        //      deliver an entire block will be no more expensive than having
        //      it deliver some fragment of that block.  (This statement
        //      assumes that latency overwhelms the incremental cost of
        //      delivering additional bytes; it's not guaranteed to be true,
        //      but it's quite likely true.)  So perhaps we should adjust the
        //      extent we request to block boundaries on both ends (but making
        //      sure we respect EOF.)
        //
        final int bs = versionMetadata.blockSize;
        long endOffset = ((fileOffset + bs) / bs) * bs;
        if (endOffset > requestedEndOffset)
            endOffset = requestedEndOffset;

        if (requestedEndOffset > endOffset) {
            //
            // The request requires multiple reads.  Initiate a read-ahead for
            // its next chunk.
            //
            // XXX: By placing the read-ahead results in the buffer cache,
            //      we're depriving Celeste of the ability to check the
            //      credentials of subsequent readers wanting to access that
            //      part of the file.  Actually, this is a fundamental issue
            //      with the whole buffer caching scheme; it applies to all
            //      data placed in the cache.
            //
            long raEndOffset =
                min(requestedEndOffset, endOffset + FileImpl.maxBufferLength);
            Extent raExtent = new ExtentImpl(endOffset, raEndOffset);
            try {
                this.cache.read(this.latestVersionId, raExtent,
                    new CelesteReader(readerCredential, readerPassword), true);
            } catch (FileException e) {
                //
                // A read-ahead failure shouldn't be allowed to affect the
                // outcome of the read proper.  If the failure is persistent,
                // it will be caught when the primary read path tries to read
                // this extent.  So do nothing here.
                //
                // XXX: Logging the failure would be useful.
                //
            }
        }

        //
        // Do a synchronous read for the initial chunk and return the
        // resulting extent.
        //
        Extent desiredExtent = new ExtentImpl(fileOffset, endOffset);
        ReadResult result = null;
        //
        // Since the cache has to use a Callable invoke the CelesteReader
        // that's supplied as an argument to this call, it has to assume that
        // call() might throw an arbitrary Exception.  It's able to translate
        // that to FileException, but can't do better.  However, we're in a
        // position to do better, since we know the semantics of the
        // CelesteReader, in particular, the specific exceptions it can throw,
        // which are all FileException subclasses.  So we catch each of them
        // and re-throw them cast to their specific types.
        //
        try {
            result = this.cache.read(this.latestVersionId,
                desiredExtent,
                new CelesteReader(readerCredential, readerPassword),
                false);
        } catch (FileException.BadVersion e) {
            throw (FileException.BadVersion)e;
        } catch (FileException.CelesteFailed e) {
            throw (FileException.CelesteFailed)e;
        } catch (FileException.CelesteInaccessible e) {
            throw (FileException.CelesteInaccessible)e;
        } catch (FileException.CredentialProblem e) {
            throw (FileException.CredentialProblem)e;
        } catch (FileException.Deleted e) {
            throw (FileException.Deleted)e;
        } catch (FileException.IOException e) {
            throw (FileException.IOException)e;
        } catch (FileException.NotFound e) {
            throw (FileException.NotFound)e;
        } catch (FileException.PermissionDenied e) {
            throw (FileException.PermissionDenied)e;
        } catch (FileException.Runtime e) {
            throw (FileException.Runtime)e;
        } catch (FileException.ValidationFailed e) {
            throw (FileException.ValidationFailed)e;
        } catch (FileException e) {
            //
            // The clauses above should cover all possibilities.  If we get
            // here, the CelesteReader has gotten out of sync with this code.
            //
            System.err.printf("unexpected exception during read%n");
            e.printStackTrace(System.err);
            throw new FileException.Runtime(e);
        }
        this.latestVersionId = result.version;
        return result.buffer;
    }

    //
    // XXX: Need to rethink the implementations of update operations.  They
    //      all share the same design pattern (a loop updating the predicated
    //      version and associated metadata that then calls a "try it" method)
    //      and it ought to be possible to do code factoring to eliminate some
    //      of the repetition.
    //

    //
    // XXX: Do we need versions of write that allow their callers to supply
    //      fresh client metadata (thereby allowing for a completely atomic
    //      update)?
    //

    /**
     * Write bytes to the file, obtaining them from {@code source}'s position
     * to its limit, placing them in a span starting at the file offset
     * dictated by {@code source}'s start offset and position.  If {@code
     * predicatedVersion} is non-{@code null}, write only to the indicated
     * version of the file, failing if that version is not current.  Otherwise
     * write to whatever version of the file is current, retrying if necessary
     * to avoid a concurrent conflicting write.
     *
     * @param source            an extent buffer containing the data to be
     *                          written
     * @param invokerCredential the credential to use to perform the write
     * @param invokerPassword   a password allowing {@code invokerCredential}
     *                          to produce signatures
     * @param predicatedVersion the version that must be current when the
     *                          write is performed, or {@code null} if any
     *                          version will do
     *
     * @throws FileException.Deleted
     *      if this file has been deleted
     * @throws FileException.RetriesExceeded
     *      if {@code predicatedVersion} is non-{@code null} and was not the
     *      current version or if {@code predicatedVersion} is {@code null}
     *      and a limit was exceeded for retries in the face of conflicting
     *      concurrent writes to this file
     */
    //
    // XXX: The retry limit probably ought to become a parameter (to the
    //      class, if not to this method).
    //
    // XXX: Need to decide and document whether a successful write updates
    //      source's position.  (It almost certainly should.)
    //
    public void write(ExtentBuffer source, Credential invokerCredential, char[] invokerPassword, BeehiveObjectId predicatedVersion)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        //
        // XXX: Profiling/Debugging support.
        //
        boolean debug = FileImpl.profileConfig.contains(
            "FileImpl.write");
        TimeProfiler timer = new TimeProfiler("FileImpl.write");

        final int retryLimit = (predicatedVersion == null) ? 5 : 1;
        ensureMetadata();

        long fileStartOffset = source.getStartOffset() + source.position();
        int writeLen = source.remaining();

        //
        // No blocking (and fetching of borders, etc.)  occurs at this time.
        // This would be the case for encryption, and to build reasonable hash
        // trees for authentication.
        //

        //
        // Repeatedly attempt the write until our notion of the latest version
        // id coincides with Celeste's.  This code is vulnerable to livelock,
        // but retryCount limits the vulnerability.
        //
        BeehiveObjectId profileId = invokerCredential.getObjectId();
        BeehiveObjectId latestVersionId = (predicatedVersion != null) ?
            predicatedVersion : this.latestVersionId;
        int retryCount = 0;
        for (;;) {
            //
            // This write will be predicated on the version named by
            // latestVersionId.  Get the version's metadata and verify that the
            // write has some prospect of succeeding.
            //
            VersionMetadata versionMetadata =
                versionToMetadata.get(latestVersionId);
            if (versionMetadata.isDeleted)
                throw new FileException.Deleted();

            //
            // Set up metadata that matches the data to be written.
            //
            timer.stamp("pre_md_setup");
            OrderedProperties props = new OrderedProperties();
            props.put(CONTENT_TYPE_NAME, versionMetadata.contentType);
            props.put(FILE_SERIAL_NUMBER_NAME,
                Long.toString(this.serialNumber));
            props.put(FileImpl.VERSION_NAME,
                Integer.toString(this.dataEncodingVersion));
            props.put(CREATED_TIME_NAME, Long.toString(this.createdTime));
            props.put(DELETION_TIME_TO_LIVE_NAME,
                Long.toString(versionMetadata.deletionTimeToLive));
            props.put(IS_DELETED_NAME, Boolean.toString(false));
            //
            // This part of the metadata depends on that of its predicated
            // version.
            //
            long newModifiedTime = System.currentTimeMillis();
            props.put(MODIFIED_TIME_NAME, Long.toString(newModifiedTime));
            props.put(METADATA_CHANGED_TIME_NAME,
                Long.toString(versionMetadata.metadataChangedTime));
            props.put(FileImpl.ENCRYPTED_DTOKEN_NAME, CelesteEncoderDecoder.
                    toHexString(versionMetadata.encryptedDeleteToken));
            //
            // Carry the client properties forward from the predicated version.
            //
            String encoding = CelesteEncoderDecoder.toHexString(
                versionMetadata.clientProperties.toByteArray());
            props.setProperty(CLIENT_METADATA_NAME, encoding);

            ClientMetaData woc = new ClientMetaData(props.toByteBuffer());
            WriteFileOperation wcc = new WriteFileOperation(
                this.getFileIdentifier(), profileId,
                latestVersionId, woc, fileStartOffset, writeLen);

            timer.stamp("pre_try_write");
            BeehiveObjectId newLatestVersionId = tryWriteVersion(
                latestVersionId, source, props, wcc,
                invokerCredential, invokerPassword);
            //System.out.printf(" tryWrite:\tprev:\t%s%n\t\tnew:\t%s%n",
            //    latestVersionId, newLatestVersionId);
            latestVersionId = newLatestVersionId;
            timer.stamp("post_try_write");

            if (latestVersionId == null) {
                //
                // The write succeeded.  We're done!
                //
                break;
            }

            //
            // Guard against livelock by bounding the number of retries.
            //
            // XXX: I'm not sure this is really a good idea, but for now here
            //      it is.  If this code survives, the number of retries ought
            //      to be made configurable.
            //
            if (++retryCount >= retryLimit)
                throw new FileException.RetriesExceeded();
        }
        //System.out.printf("FileImpl.write(): return: retryCount %d%n",
        //    retryCount);

        if (debug) {
            //
            // 1.  overall time
            // 2.  time to reach the metadata setup code
            // 3.  time to set up metadata
            // 4.  time spent in tryWriteVersion
            //
            timer.printCSV(FileImpl.profileOutputFileName);
        }
    }

    /**
     * <p>
     *
     * Write {@code writeLen} bytes of data, starting from position {@code
     * sourceOffset} of {@code sourceBuffer}, to the file, placing them in a
     * span starting at position {@code fileStartOffset} in the file.
     *
     * </p>
     *
     * @param sourceBuffer      the buffer containing data to be written to
     *                          the file
     * @param sourceOffset      the starting position in {@code sourceBuffer}
     *                          of the byte span to be written
     * @param writeLen          the number of bytes to be written
     * @param fileStartOffset   the starting offset in the file of the span
     *                          into which data are to be written
     * @param invokerCredential the credential to use to perform the write
     * @param invokerPassword   a password allowing {@code invokerCredential}
     *                          to produce signatures
     */
    public void write(byte[] sourceBuffer, int sourceOffset, int writeLen,
            long fileStartOffset, Credential invokerCredential, char[] invokerPassword)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        //System.err.printf(
        //    "FileImpl.write():%n\tsourceOffset: %d%n" +
        //        "\twriteLen: %d%n\tfileOffset: %d%n",
        //    sourceOffset, writeLen, fileStartOffset);

        //
        // Validate arguments.
        //
        if (sourceOffset < 0 || writeLen < 0 || fileStartOffset < 0)
            throw new IllegalArgumentException(
                "offsets and length must be non-negative");
        if (sourceOffset + writeLen > sourceBuffer.length)
            throw new IllegalArgumentException(
                "buffer too short for specified offset and length");
        if (fileStartOffset - sourceOffset < 0)
            throw new IllegalArgumentException(
                "start of buffer must fall at a valid file offset");

        ExtentBuffer source = ExtentBuffer.wrap(fileStartOffset - sourceOffset,
            sourceBuffer, sourceOffset, writeLen);
        //System.err.printf(
        //    "FileImpl.write() source: start %d, pos %d, length %d%n",
        //    source.getStartOffset(), source.position(),
        //    source.limit() - source.position());
        this.write(source, invokerCredential, invokerPassword, null);
        //System.err.printf("FileImpl.write(): length now %d%n",
        //    this.getFileLength(true));
    }

    private BeehiveObjectId tryWriteVersion(BeehiveObjectId versionId, ExtentBuffer source, OrderedProperties props,
            WriteFileOperation op, Credential invokerCredential,
            char[] invokerPassword)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.InvalidName,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {

        //
        // XXX: Profiling/Debugging support.
        //
        boolean debug = FileImpl.profileConfig.contains(
            "FileImpl.tryWriteVersion");
        TimeProfiler timer = new TimeProfiler("FileImpl.tryWriteVersion");

        BeehiveObjectId latestVersionId = null;
        try {
            timer.stamp("pre_sign");
            ByteBuffer data = source.getByteBuffer();
            BeehiveObjectId dataId = new BeehiveObjectId(data);
            Credential.Signature signature =
                invokerCredential.sign(invokerPassword, op.getId(), dataId);
            timer.stamp("post_sign");
            OrderedProperties metadata = null;
            CelesteAPI proxy = null;
            try {
                proxy = this.proxyCacheGetAndRemove(this.socketAddr);
                metadata = proxy.writeFile(op, signature, data);
                timer.stamp("post_cel_wr");
            } catch (CelesteException.OutOfDateException e) {
                //
                // We lost a race and someone else created a new version of
                // the file.  Grab its metadata and notify our caller of the
                // lost race by returning the new version id.
                //
                return processReplyMetadata(e.getMetaData());
            } catch (CelesteException.CredentialException e) {
                throw new FileException.CredentialProblem(e);
            } catch (CelesteException.AccessControlException e) {
                throw new FileException.PermissionDenied(e);
            } catch (CelesteException.FileLocked e) {
                throw new FileException.Locked(e);
            } catch (CelesteException.NotFoundException e) {
                throw new FileException.NotFound(e);
            } catch (CelesteException.IllegalParameterException e) {
                //
                // XXX: Is this really the right translation?  (Can it really
                //      happen at all?  If so, shouldn't it translate to
                //      IllegalParameterException?)
                //
                throw new FileException.InvalidName(e);
            } catch (CelesteException.NoSpaceException e) {
                throw new FileException.CapacityExceeded(e);
            } catch (CelesteException.RuntimeException e) {
                throw new FileException.Runtime(e);
            } catch (CelesteException.VerificationException e) {
                throw new FileException.Runtime(e);
            } catch (CelesteException.DeletedException e) {
                throw new FileException.Deleted(e);
            } catch (IOException e) {
                throw new FileException.IOException(e);
            } catch (ClassNotFoundException e) {
                throw new FileException.Runtime(e);
            } finally {
                this.proxyCacheAddAndEvictOld(this.socketAddr, proxy);
            }

            latestVersionId = this.processReplyMetadata(metadata);

            //
            // Create a cache entry for the version of the file this write
            // produced.
            //
            // XXX: Need better coordination with our caller to avoid copying.
            //      One issue is trust:  We can promise not to mess up the
            //      underlying byte array our caller has given us (and our
            //      caller potentially can even enforce this by giving us a
            //      read-only ByteBuffer or ExtentBuffer), but what about the
            //      other direction?  How can we be assured that our caller
            //      won't muck with the buffer handed to us?  We can copy the
            //      data to be sure, but short of that, we must trust the
            //      caller.
            //
            timer.stamp("pre_cache");
            ExtentBuffer cachedCopy =
                ExtentBuffer.allocate(op.getFileOffset(),
                    source.remaining());
            cachedCopy.put(source.duplicate()).position(0).
                limit(cachedCopy.capacity());
            this.cache.predicatedWrite(versionId, latestVersionId, cachedCopy);

            this.latestVersionId = latestVersionId;
            timer.stamp("post_cache");
        } catch (Credential.Exception e) {
            throw new FileException.CredentialProblem(e);
        }

        if (debug) {
            //
            // 1.  overall time
            // 2.  slop to get to the signing code
            // 3.  time to sign the data
            // 4.  slop to get to the call to Celeste to write
            // 5.  time Celeste takes to write
            // 6.  slop to get to the cache
            // 7.  time the cache takes to cache the write
            //
            timer.printCSV(FileImpl.profileOutputFileName);
        }

        return null;
    }

    public void truncate(long offset, Credential invokerCredential, char[] invokerPassword)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        this.doTruncate(offset, false, null,
            invokerCredential, invokerPassword, null);
    }

    public void truncate(long offset, Credential invokerCredential, char[] invokerPassword, BeehiveObjectId predicatedVersion)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        this.doTruncate(offset, false, null,
            invokerCredential, invokerPassword, predicatedVersion);
    }

    //
    // doTruncate serves several purposes:
    //  -   To handle truncates proper;
    //  -   To handle marking the file as deleted; and
    //  -   To handle client metadata updates.
    //
    // The clientProperties argument is null in all but the last case.  When
    // it's set, the file modified time is left unchanged and the metadata
    // change time is updated, which is the reverse of the other cases.
    //
    // An offset value of -1 is interpreted as a request to retain the current
    // file size.
    //
    // predicatedVersion is the version id of the VObject against which this
    // modification is to be performed; null is taken to mean "don't care".
    //
    // XXX: The retry limit probably ought to become a parameter (to the
    //      class, if not to this method).
    //
    private void doTruncate(long offset, boolean markDeleted,
            OrderedProperties clientProperties, Credential invokerCredential,
            char[] invokerPassword, BeehiveObjectId predicatedVersion)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        final int retryLimit = (predicatedVersion == null) ? 5 : 1;
        ensureMetadata();

        //
        // Repeatedly attempt the truncate until our notion of the latest
        // version id coincides with Celeste's.  This code is vulnerable to
        // livelock, but retryCount limits the vulnerability.
        //
        BeehiveObjectId profileId = invokerCredential.getObjectId();
        BeehiveObjectId latestVersionId = (predicatedVersion != null) ?
            predicatedVersion : this.latestVersionId;
        int retryCount = 0;
        for (;;) {
            //
            // This truncate will be predicated on the version named by
            // latestVersionId.  Get the version's metadata and verify that the
            // truncate has some prospect of succeeding.
            //
            VersionMetadata versionMetadata =
                versionToMetadata.get(latestVersionId);
            if (versionMetadata.isDeleted) {
                throw new FileException.Deleted();
            }

            //
            // Set up metadata that matches the data to be written.
            //
            // XXX: Need to use setProperty() below rather than put().
            //
            OrderedProperties props = new OrderedProperties();
            props.put(FILE_SERIAL_NUMBER_NAME,
                Long.toString(this.serialNumber));
            props.put(FileImpl.VERSION_NAME,
                Integer.toString(this.dataEncodingVersion));
            props.put(CREATED_TIME_NAME, Long.toString(this.createdTime));
            props.put(IS_DELETED_NAME,Boolean.toString(markDeleted));
            //
            // This part of the metadata depends on that of its predicated
            // version.  Either the client metadata change time or the modify
            // time gets updated, but not both.
            //
            if (offset == -1)
                offset = versionMetadata.fileLength;
            props.put(CONTENT_TYPE_NAME, versionMetadata.contentType);
            props.put(DELETION_TIME_TO_LIVE_NAME,
                Long.toString(versionMetadata.deletionTimeToLive));
            props.put(FileImpl.ENCRYPTED_DTOKEN_NAME, CelesteEncoderDecoder.
                toHexString(versionMetadata.encryptedDeleteToken));
            long now = System.currentTimeMillis();
            if (clientProperties != null) {
                props.put(MODIFIED_TIME_NAME,
                    Long.toString(versionMetadata.modifiedTime));
                props.put(METADATA_CHANGED_TIME_NAME, Long.toString(now));
                //
                // Replace the existing client properties with the ones
                // specified in the call.
                //
                String encoding = CelesteEncoderDecoder.toHexString(
                    clientProperties.toByteArray());
                props.setProperty(CLIENT_METADATA_NAME, encoding);
            } else {
                props.put(MODIFIED_TIME_NAME, Long.toString(now));
                props.put(METADATA_CHANGED_TIME_NAME,
                    Long.toString(versionMetadata.metadataChangedTime));
                //
                // Carry the client properties forward from the predicated
                // version.
                //
                String encoding = CelesteEncoderDecoder.toHexString(
                    versionMetadata.clientProperties.toByteArray());
                props.setProperty(CLIENT_METADATA_NAME, encoding);
            }

            ClientMetaData woc = new ClientMetaData(props.toByteBuffer());
            SetFileLengthOperation operation = new SetFileLengthOperation(
                this.getFileIdentifier(), profileId,
                latestVersionId, woc, offset);

            latestVersionId = tryTruncateVersion(latestVersionId, props,
                operation, invokerCredential, invokerPassword);

            if (latestVersionId == null) {
                //
                // The truncate succeeded.  We're done!
                //
                break;
            }

            //
            // Guard against livelock by bounding the number of retries.
            //
            // XXX: I'm not sure this is really a good idea, but for now here
            //      it is.  If this code survives, the number of retries ought
            //      to be made configurable.
            // XXX: See retryCount comment above.
            //
            if (++retryCount >= retryLimit)
                throw new FileException.RetriesExceeded();
        }
    }

    //
    // Attempt to perform a truncate (as characterized by props and wcc)
    // against the file version given by versionId.
    //
    // If the truncate succeeds, update our metadata to match it and return
    // null.  If the truncate fails because versionId is out of date, return
    // the version id that Celeste handed us as being current.  Finally, if
    // things go completely awry, throw a suitable exception.
    //
    // XXX: Note that the props object is embedded (in encoded form) into
    //      operation as operation's ClientMetaData component.  Thus, it need
    //      not be passed as a separate argument.  Taking this thought a bit
    //      further, it might be feasible to to have extractVersionMetadata
    //      take operation as argument instead of props.  (Doing so would
    //      probably require some refactoring, so that extractVersionMetadata
    //      could expect a common superclass of the various modification
    //      operations.)
    //
    private BeehiveObjectId tryTruncateVersion(BeehiveObjectId versionId,
            OrderedProperties props, SetFileLengthOperation operation,
            Credential invokerCredential, char[] invokerPassword)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {

        BeehiveObjectId latestVersionId = null;
        try {
            Credential.Signature sig =
                invokerCredential.sign(invokerPassword, operation.getId());
            OrderedProperties metadata = null;
            CelesteAPI proxy = null;
            try {
                proxy = this.proxyCacheGetAndRemove(this.socketAddr);
                metadata = proxy.setFileLength(operation, sig);
            } catch (CelesteException.OutOfDateException e) {
                //
                // We lost a race and someone else created a new version of
                // the file.  Grab its metadata and notify our caller of the
                // lost race by returning the new version id.
                //
                return processReplyMetadata(e.getMetaData());
            } catch (CelesteException.CredentialException e) {
                throw new FileException.CredentialProblem(e);
            } catch (CelesteException.AccessControlException e) {
                throw new FileException.PermissionDenied(e);
            } catch (CelesteException.FileLocked e) {
                throw new FileException.Locked(e);
            } catch (CelesteException.NotFoundException e) {
                throw new FileException.NotFound(e);
            } catch (CelesteException.RuntimeException e) {
                throw new FileException.Runtime(e);
            } catch (CelesteException.DeletedException e) {
                throw new FileException.Deleted(e);
            } catch (CelesteException.NoSpaceException e) {
                throw new FileException.CapacityExceeded(e);
            } catch (CelesteException.VerificationException e) {
                throw new FileException.Runtime(e);
            } catch (IOException e) {
                throw new FileException.IOException(e);
            } catch (ClassNotFoundException e) {
                throw new FileException.Runtime(e);
            } finally {
                this.proxyCacheAddAndEvictOld(this.socketAddr, proxy);
            }

            latestVersionId = this.processReplyMetadata(metadata);

            //
            // Create a cache entry for the version of the file this
            // truncate produced.
            //
            this.cache.predicatedTruncate(this.latestVersionId, latestVersionId,
                operation.getLength());

            this.latestVersionId = latestVersionId;
        } catch (Credential.Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return null;
    }

    //
    // Set file attributes by piggybacking on the Celeste setFileLength()
    // operation, instructing it to leave the length unchanged, but supplying
    // a new (to Celeste) uninterpreted clientMetaData object.
    //
    // A successful invocation updates the file's metadata change time
    // attribute as well as whatever attributes are explicitly supplied.
    //
    private void setFileAttributes(OrderedProperties requestedAttrs, Credential invokerCredential, char[] invokerPassword, BeehiveObjectId predicatedVersion)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        final int retryLimit = (predicatedVersion == null) ? 5 : 1;
        ensureMetadata();

        //
        // Repeatedly attempt the update until our notion of the latest
        // version id coincides with Celeste's.  This code is vulnerable to
        // livelock, but retryCount limits the vulnerability.
        //
        BeehiveObjectId profileId = invokerCredential.getObjectId();
        BeehiveObjectId latestVersionId = (predicatedVersion != null) ?
            predicatedVersion : this.latestVersionId;
        int retryCount = 0;
        for (;; ) {
            //
            // This update will be predicated on the version named by
            // latestVersionId.  Get the version's metadata and verify that the
            // write has some prospect of succeeding.
            //
            VersionMetadata versionMetadata =
                versionToMetadata.get(latestVersionId);
            if (versionMetadata.isDeleted)
                throw new FileException.Deleted();

            //
            // Set up the ClientMetaData that will be part of the operation
            // given to Celeste.  From Celeste's perspective, this is all
            // uninterpreted information.  From FileImpl's perspective, it's a
            // collection of attributes based on those of the current file
            // version, but altered as dictated by the contents of
            // requestedAttrs.
            //
            OrderedProperties attrs = new OrderedProperties();
            attrs.setProperty(FileImpl.VERSION_NAME,
                Integer.toString(this.dataEncodingVersion));
            attrs.setProperty(CREATED_TIME_NAME,
                Long.toString(this.createdTime));
            String deletionTimeToLiveString =
                requestedAttrs.getProperty(DELETION_TIME_TO_LIVE_NAME);
            attrs.setProperty(IS_DELETED_NAME, Boolean.toString(false));
            //
            // This part of the metadata depends on that of its predicated
            // version.
            //
            // Start by replacing the client properties if new ones were
            // supplied.  If not, carry the client properties from the
            // predicated version forward.
            //
            String encodedClientProperties = requestedAttrs.getProperty(
                CLIENT_METADATA_NAME);
            if (encodedClientProperties == null) {
                 encodedClientProperties = CelesteEncoderDecoder.toHexString(
                    versionMetadata.clientProperties.toByteArray());
            }
            attrs.setProperty(CLIENT_METADATA_NAME, encodedClientProperties);
            String contentType = requestedAttrs.getProperty(CONTENT_TYPE_NAME);
            if (contentType == null)
                contentType = versionMetadata.contentType;
            attrs.setProperty(CONTENT_TYPE_NAME, contentType);
            if (deletionTimeToLiveString == null)
                deletionTimeToLiveString =
                    Long.toString(versionMetadata.deletionTimeToLive);
            attrs.setProperty(DELETION_TIME_TO_LIVE_NAME,
                deletionTimeToLiveString);
            attrs.setProperty(FileImpl.ENCRYPTED_DTOKEN_NAME,
                CelesteEncoderDecoder.toHexString(
                    versionMetadata.encryptedDeleteToken));
            attrs.setProperty(MODIFIED_TIME_NAME,
                Long.toString(versionMetadata.modifiedTime));
            long now = System.currentTimeMillis();
            attrs.setProperty(METADATA_CHANGED_TIME_NAME, Long.toString(now));

            ClientMetaData clientMetaData =
                new ClientMetaData(attrs.toByteBuffer());

            //
            // Set up a SetFileLengthOperation that contains the updated
            // attributes.
            //
            SetFileLengthOperation operation = new SetFileLengthOperation(
                this.getFileIdentifier(), profileId,
                latestVersionId, clientMetaData, versionMetadata.fileLength);

            //
            // Attempt the predicated operation.
            //
            latestVersionId = tryTruncateVersion(latestVersionId, attrs,
                operation, invokerCredential, invokerPassword);

            if (latestVersionId == null) {
                //
                // The update succeeded.  We're done!
                //
                break;
            }

            //
            // Guard against livelock by bounding the number of retries.
            //
            // XXX: I'm not sure this is really a good idea, but for now here
            //      it is.  If this code survives, the number of retries ought
            //      to be made configurable.
            // XXX: See retryCount comment above.
            //
            if (++retryCount >= retryLimit)
                throw new FileException.RetriesExceeded();
        }
    }

    public Serializable runExtension(Credential invokerCredential, char[] invokerPassword, URL[] jarFileURLs, String[] args)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.doRunExtension(false, null, invokerCredential, invokerPassword, null, jarFileURLs, args);
    }

    public Serializable runExtension(Credential invokerCredential, char[] invokerPassword, BeehiveObjectId predicatedVersion,
            URL[] jarFileURLs, String[] args)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        return this.doRunExtension(false, null, invokerCredential, invokerPassword, predicatedVersion, jarFileURLs, args);
    }

    private Serializable doRunExtension(boolean markDeleted, OrderedProperties clientProperties, Credential invokerCredential,
            char[] invokerPassword, BeehiveObjectId predicatedVersion,
            URL[] jarFileURLs, String[] args)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        ensureMetadata();

        //
        // Repeatedly attempt the truncate until our notion of the latest
        // version id coincides with Celeste's.  This code is vulnerable to
        // livelock, but retryCount limits the vulnerability.
        //
        BeehiveObjectId latestVersionId = (predicatedVersion != null) ? predicatedVersion : this.latestVersionId;
        //
        // This truncate will be predicated on the version named by
        // latestVersionId.  Get the version's metadata and verify that the
        // truncate has some prospect of succeeding.
        //
        VersionMetadata versionMetadata = versionToMetadata.get(latestVersionId);
        if (versionMetadata.isDeleted) {
            throw new FileException.Deleted();
        }

        ExtensibleOperation operation = new ExtensibleOperation(this.getFileIdentifier(), invokerCredential.getObjectId(), jarFileURLs, args);

        return tryRunExtension(latestVersionId, operation, invokerCredential, invokerPassword);
    }

    private Serializable tryRunExtension(BeehiveObjectId versionId, ExtensibleOperation operation, Credential invokerCredential, char[] invokerPassword)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {

        try {
            Credential.Signature sig = invokerCredential.sign(invokerPassword, operation.getId());
            ResponseMessage reply = null;
            CelesteAPI proxy = null;
            try {
                proxy = this.proxyCacheGetAndRemove(this.socketAddr);
                reply = proxy.runExtension(operation, sig, null);

                return reply.get(Serializable.class);
            } catch (CelesteException.CredentialException e) {
                throw new FileException.CredentialProblem(e);
            } catch (CelesteException.AccessControlException e) {
                throw new FileException.PermissionDenied(e);
            } catch (CelesteException.NotFoundException e) {
                throw new FileException.NotFound(e);
            } catch (CelesteException.RuntimeException e) {
                throw new FileException.Runtime(e);
            } catch (CelesteException.DeletedException e) {
                throw new FileException.Deleted(e);
            } catch (CelesteException.NoSpaceException e) {
                throw new FileException.CapacityExceeded(e);
            } catch (CelesteException.VerificationException e) {
                throw new FileException.Runtime(e);
            } catch (IOException e) {
                throw new FileException.IOException(e);
            } catch (CelesteException.FileLocked e) {
                throw new FileException.Locked(e);
            } catch (CelesteException.IllegalParameterException e) {
                throw new FileException.Runtime(e);
            } finally {
                this.proxyCacheAddAndEvictOld(this.socketAddr, proxy);
            }
        } catch (Credential.Exception e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Expunge this file, permanently removing all its data.
     *
     * @param invokerCredential the profile to be used to perform the expunge
     * @param invokerPassword   the password for {@code invokerCredential}
     */
    public void purgeForever(Credential invokerCredential, char[] invokerPassword)
        throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.DTokenMismatch,
            FileException.IOException,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {

        ensureMetadata();
        VersionMetadata versionMetadata =
            versionToMetadata.get(this.latestVersionId);
        byte[] raw_token = null;
        try {
            raw_token = invokerCredential.decrypt(invokerPassword, versionMetadata.encryptedDeleteToken);
        } catch (Credential.Exception e) {
            throw new FileException.CredentialProblem(e);
        }
        BeehiveObjectId dtoken = new BeehiveObjectId(new String(raw_token));

        //
        // XXX: See the comment in create() about how to generate the delete
        //      token id.
        //
        BeehiveObjectId calculated_dthash =
            new BeehiveObjectId(dtoken.toString().getBytes());
        calculated_dthash = dtoken.getObjectId();

        //
        // XXX: Ideally, this code would be arranged to have Celeste perform
        //      the ACL check for this operation before the delete token is
        //      actually exposed.  As the implementation stands, an
        //      unscrupulous CelesteNode could steal the exposed delete token
        //      in the face of an ACL forbidding deletion.
        //
        //      (There's an underlying who's in control question here:  The
        //      entity performing the deletion should only need permission
        //      from the ACL for the operation, but our design for deletion
        //      requires that that entity present an unlocked profile for the
        //      file's current owner, regardless of who the owner is.  This
        //      requirement is the other half of the requirement for
        //      setOwner(), where permission from the ACL also isn't enough
        //      and presenting the new owner's unlocked profile is also
        //      required.)
        //
        try {
            InspectFileOperation operation = new InspectFileOperation(
                    this.getFileIdentifier(),
                    invokerCredential.getObjectId());

            OrderedProperties metadata = null;
            CelesteAPI proxy = null;
            try {
                proxy = this.proxyCacheGetAndRemove(this.socketAddr);
                metadata = proxy.inspectFile(operation).getMetadata();
            } catch (CelesteException.NotFoundException e) {
                throw new FileException.NotFound(e);
            } catch (CelesteException.RuntimeException e) {
                throw new FileException.Runtime(e);
            } catch (CelesteException.DeletedException e) {
                throw new FileException.Runtime(e);
            } catch (ClassNotFoundException e) {
                throw new FileException.Runtime(e);
            } finally {
                this.proxyCacheAddAndEvictOld(this.socketAddr, proxy);
            }

            BeehiveObjectId deleteTokenId = metadata.getPropertyAsObjectId(
                CelesteAPI.DELETETOKENID_NAME, null);
            if (!deleteTokenId.equals(calculated_dthash)) {
                // delete token mismatch
                System.out.println("Delete tokens mismatched: " +
                    deleteTokenId + " " + calculated_dthash);
                throw new FileException.DTokenMismatch();
            }
        } catch (IOException e) {
            //
            // This should not happen.
            //
            System.err.printf(
                "purgeForever(): inspectFile() threw exception.%n");
            e.printStackTrace(System.err);
        }

        DeleteFileOperation operation = new DeleteFileOperation(
            this.getFileIdentifier(),
            invokerCredential.getObjectId(), dtoken,
            this.getDeletionTimeToLive());

        Credential.Signature sig = null;
        try {
            sig = invokerCredential.sign(invokerPassword, operation.getId());
        } catch (Credential.Exception e) {
            throw new FileException.CredentialProblem(e);
        }
        CelesteAPI proxy = null;
        try {
            proxy = this.proxyCacheGetAndRemove(this.socketAddr);
            proxy.deleteFile(operation, sig);
        } catch (CelesteException.CredentialException e) {
            throw new FileException.CredentialProblem(e);
        } catch (CelesteException.AccessControlException e) {
            throw new FileException.PermissionDenied(e);
        } catch (CelesteException.NotFoundException e) {
            throw new FileException.NotFound(e);
        } catch (RuntimeException e) {
            throw new FileException.Runtime(e);
        } catch (CelesteException.DeletedException e) {
            throw new FileException.Deleted(e);
        } catch (CelesteException.RuntimeException e) {
            throw new FileException.Deleted(e);
        } catch (CelesteException.VerificationException e) {
            throw new FileException.Runtime(e);
        } catch (CelesteException.NoSpaceException e) {
            throw new FileException.Runtime(e);
        } catch (ClassNotFoundException e) {
            throw new FileException.Runtime(e);
        } catch (IOException e) {
            throw new FileException.IOException(e);
        } finally {
            this.proxyCacheAddAndEvictOld(this.socketAddr, proxy);
        }

        // this file is now totally invalidated
        this.dataEncodingVersion = 0;
    }

    /**
     * Create a new version of this file that is marked as deleted.  Previous
     * versions are unaffected.
     *
     * @param invokerCredential the credential to be used to perform the
     *                          deletion
     * @param invokerPassword   the password for {@code invokerCredential}
     *
     * @throws FileException.BadVersion
     * @throws FileException.CapacityExceeded
     * @throws FileException.CelesteInaccessible
     * @throws FileException.CredentialProblem
     * @throws FileException.Deleted
     * @throws FileException.IOException
     * @throws FileException.NotFound
     * @throws FileException.Locked
     * @throws FileException.PermissionDenied
     * @throws FileException.RetriesExceeded
     * @throws FileException.Runtime
     * @throws FileException.ValidationFailed
     */
    public void markDeleted(Credential invokerCredential, char[] invokerPassword)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        //
        // The implementation strategy is to use truncate as a way to convey
        // the "file is deleted" property to Celeste.
        //
        doTruncate(0, true, null, invokerCredential, invokerPassword, null);
    }

    /**
     * Change the file's owner.
     *
     * @param invokerCredential the profile of the entity setting the owner
     * @param invokerPassword   the password for {@code invokerCredential}
     * @param predicatedVersion the version that must be current when the
     *                          ownership change is performed, or {@code null}
     *                          if any version will do
     * @param newOwner          the credential identifying the new owner
     */
    public void setOwner(Credential invokerCredential, char[] invokerPassword,
            BeehiveObjectId predicatedVersion, Credential newOwner)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteInaccessible,
            FileException.CelesteFailed,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        final BeehiveObjectId newOwnerId = newOwner.getObjectId();
        final int retryLimit = (predicatedVersion == null) ? 5 : 1;
        ensureMetadata();

        //
        // Repeatedly attempt the ownership change until our notion of the
        // latest file version coincides with Celeste's.  This code is
        // vulnerable to livelock, but retryCount limits the vulnerability.
        //
        BeehiveObjectId latestVersion = (predicatedVersion != null) ?
            predicatedVersion : this.latestVersionId;
        int retryCount = 0;
        for (;;) {
            VersionMetadata versionMetadata =
                versionToMetadata.get(latestVersion);

            //
            // Decrypt the delete token using the current owner's profile
            // and then re-encrypt it using the new owner's profile.  This is
            // necessary to allow the new owner to (eventually) delete the
            // file.
            //
            byte[] reencryptedToken = null;
            try {
                byte[] rawToken = invokerCredential.decrypt(invokerPassword,
                    versionMetadata.encryptedDeleteToken);
                reencryptedToken = newOwner.encrypt(rawToken);
            } catch (Credential.Exception e) {
                throw new FileException.CredentialProblem(e);
            }

            //
            // Set up metadata that matches the data to be written.
            //
            OrderedProperties props = new OrderedProperties();
            props.put(CONTENT_TYPE_NAME, versionMetadata.contentType);
            props.put(FILE_SERIAL_NUMBER_NAME,
                Long.toString(this.serialNumber));
            props.put(FileImpl.VERSION_NAME,
                Integer.toString(this.dataEncodingVersion));
            props.put(CREATED_TIME_NAME, Long.toString(this.createdTime));
            props.put(DELETION_TIME_TO_LIVE_NAME,
                Long.toString(versionMetadata.deletionTimeToLive));
            props.put(FileImpl.ENCRYPTED_DTOKEN_NAME, CelesteEncoderDecoder.
                    toHexString(reencryptedToken));
            props.put(IS_DELETED_NAME, Boolean.toString(false));
            props.put(MODIFIED_TIME_NAME,
                Long.toString(versionMetadata.modifiedTime));
            long newMetadataChangedTime = System.currentTimeMillis();
            props.put(METADATA_CHANGED_TIME_NAME,
                Long.toString(newMetadataChangedTime));
            if (versionMetadata.clientProperties != null) {
                String encoding = CelesteEncoderDecoder.toHexString(
                    versionMetadata.clientProperties.toByteArray());
                props.setProperty(CLIENT_METADATA_NAME, encoding);
            }

            ClientMetaData clientMetaData =
                new ClientMetaData(props.toByteBuffer());

            SetOwnerAndGroupOperation op = new SetOwnerAndGroupOperation(
                this.getFileIdentifier(),
                invokerCredential.getObjectId(),
                latestVersion,
                clientMetaData,
                newOwnerId,
                versionMetadata.groupId
            );

            BeehiveObjectId newLatestVersion =
                trySetOwner(props, op, invokerCredential, invokerPassword);
            latestVersion = newLatestVersion;

            if (newLatestVersion == null) {
                //
                // The operation succeeded.  We're done!
                //
                break;
            }

            //
            // Guard against livelock by bounding the number of retries.
            //
            // XXX: I'm not sure this is really a good idea, but for now here
            //      it is.  If this code survives, the number of retries ought
            //      to be made configurable.
            //
            if (++retryCount >= retryLimit)
                throw new FileException.RetriesExceeded();
        }
    }

    private BeehiveObjectId trySetOwner(OrderedProperties props,
            SetOwnerAndGroupOperation op, Credential invokerCredential, char[] invokerPassword)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {

        BeehiveObjectId latestVersionId = null;
        try {
            Credential.Signature signature =
                invokerCredential.sign(invokerPassword, op.getId());
            OrderedProperties metadata = null;
            CelesteAPI proxy = null;
            try {
                proxy = this.proxyCacheGetAndRemove(this.socketAddr);
                metadata = proxy.setOwnerAndGroup(op, signature);
            } catch (CelesteException.OutOfDateException e) {
                //
                // We lost a race and someone else created a new version of
                // the file.  Grab its metadata and notify our caller of the
                // lost race by returning the new version id.
                //
                return processReplyMetadata(e.getMetaData());
            } catch (CelesteException.AccessControlException e) {
                throw new FileException.PermissionDenied(e);
            } catch (CelesteException.CredentialException e) {
                throw new FileException.CredentialProblem(e);
            } catch (CelesteException.DeletedException e) {
                throw new FileException.Deleted(e);
            } catch (CelesteException.FileLocked e) {
                throw new FileException.Locked(e);
            } catch (CelesteException.NotFoundException e) {
                throw new FileException.NotFound(e);
            } catch (CelesteException.NoSpaceException e) {
                throw new FileException.CapacityExceeded(e);
            } catch (CelesteException.RuntimeException e) {
                throw new FileException.Runtime(e);
            } catch (CelesteException.VerificationException e) {
                throw new FileException.Runtime(e);
            } catch (IOException e) {
                throw new FileException.IOException(e);
            } catch (ClassNotFoundException e) {
                throw new FileException.Runtime(e);
            } finally {
                this.proxyCacheAddAndEvictOld(this.socketAddr, proxy);
            }

            latestVersionId = this.processReplyMetadata(metadata);

            //
            // Create a cache entry for the version of the file this
            // operation produced.
            //
            this.cache.predicatedAttributeChange(
                this.latestVersionId, latestVersionId);

            this.latestVersionId = latestVersionId;
        } catch (Profile_.Exception e) {
            throw new FileException.CredentialProblem(e);
        }
        return null;
    }

    /**
     * Change the file's access control list.
     *
     * @param invokerCredential the credential of the entity setting the owner
     * @param invokerPassword   the password for {@code invokerCredential}
     * @param predicatedVersion the version that must be current when the
     *                          ownership change is performed, or {@code null}
     *                          if any version will do
     * @param acl               the file's new access control list
     */
    public void setACL(Credential invokerCredential, char[] invokerPassword, BeehiveObjectId predicatedVersion, CelesteACL acl)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteInaccessible,
            FileException.CelesteFailed,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        final int retryLimit = (predicatedVersion == null) ? 5 : 1;
        ensureMetadata();

        //
        // Repeatedly attempt to set the new ACL until our notion of the
        // latest file version coincides with Celeste's.  This code is
        // vulnerable to livelock, but retryCount limits the vulnerability.
        //
        BeehiveObjectId latestVersion = (predicatedVersion != null) ?
            predicatedVersion : this.latestVersionId;
        int retryCount = 0;
        for (;;) {
            VersionMetadata versionMetadata =
                versionToMetadata.get(latestVersion);

            //
            // Set up metadata that matches the data to be written.
            //
            OrderedProperties props = new OrderedProperties();
            props.put(CONTENT_TYPE_NAME, versionMetadata.contentType);
            props.put(FILE_SERIAL_NUMBER_NAME,
                Long.toString(this.serialNumber));
            props.put(FileImpl.VERSION_NAME,
                Integer.toString(this.dataEncodingVersion));
            props.put(CREATED_TIME_NAME, Long.toString(this.createdTime));
            props.put(DELETION_TIME_TO_LIVE_NAME,
                Long.toString(versionMetadata.deletionTimeToLive));
            props.put(FileImpl.ENCRYPTED_DTOKEN_NAME, CelesteEncoderDecoder.
                toHexString(versionMetadata.encryptedDeleteToken));
            props.put(IS_DELETED_NAME, Boolean.toString(false));
            props.put(MODIFIED_TIME_NAME,
                Long.toString(versionMetadata.modifiedTime));
            long newMetadataChangedTime = System.currentTimeMillis();
            props.put(METADATA_CHANGED_TIME_NAME,
                Long.toString(newMetadataChangedTime));
            if (versionMetadata.clientProperties != null) {
                String encoding = CelesteEncoderDecoder.toHexString(
                    versionMetadata.clientProperties.toByteArray());
                props.setProperty(CLIENT_METADATA_NAME, encoding);
            }

            ClientMetaData clientMetaData =
                new ClientMetaData(props.toByteBuffer());

            SetACLOperation op = new SetACLOperation(
                this.getFileIdentifier(),
                invokerCredential.getObjectId(),
                latestVersion,
                clientMetaData,
                acl
            );

            BeehiveObjectId newLatestVersion =
                trySetACL(props, op, invokerCredential, invokerPassword);
            latestVersion = newLatestVersion;

            if (newLatestVersion == null) {
                //
                // The operation succeeded.  We're done!
                //
                break;
            }

            //
            // Guard against livelock by bounding the number of retries.
            //
            // XXX: I'm not sure this is really a good idea, but for now here
            //      it is.  If this code survives, the number of retries ought
            //      to be made configurable.
            //
            if (++retryCount >= retryLimit)
                throw new FileException.RetriesExceeded();
        }
    }

    private BeehiveObjectId trySetACL(OrderedProperties props, SetACLOperation op, Credential invokerCredential, char[] invokerPassword)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed {

        BeehiveObjectId latestVersionId = null;
        try {
            Credential.Signature signature =
                invokerCredential.sign(invokerPassword, op.getId());
            OrderedProperties metadata = null;
            CelesteAPI proxy = null;
            try {
                proxy = this.proxyCacheGetAndRemove(this.socketAddr);
                metadata = proxy.setACL(op, signature);
            } catch (CelesteException.OutOfDateException e) {
                //
                // We lost a race and someone else created a new version of
                // the file.  Grab its metadata and notify our caller of the
                // lost race by returning the new version id.
                //
                return processReplyMetadata(e.getMetaData());
            } catch (CelesteException.AccessControlException e) {
                throw new FileException.PermissionDenied(e);
            } catch (CelesteException.CredentialException e) {
                throw new FileException.CredentialProblem(e);
            } catch (CelesteException.DeletedException e) {
                throw new FileException.Deleted(e);
            } catch (CelesteException.FileLocked e) {
                throw new FileException.Locked(e);
            } catch (CelesteException.NotFoundException e) {
                throw new FileException.NotFound(e);
            } catch (CelesteException.NoSpaceException e) {
                throw new FileException.CapacityExceeded(e);
            } catch (CelesteException.RuntimeException e) {
                throw new FileException.Runtime(e);
            } catch (CelesteException.VerificationException e) {
                throw new FileException.Runtime(e);
            } catch (IOException e) {
                throw new FileException.IOException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } finally {
                this.proxyCacheAddAndEvictOld(this.socketAddr, proxy);
            }

            latestVersionId = this.processReplyMetadata(metadata);

            //
            // Create a cache entry for the version of the file this
            // operation produced.
            //
            this.cache.predicatedAttributeChange(
                this.latestVersionId, latestVersionId);

            this.latestVersionId = latestVersionId;
        } catch (Profile_.Exception e) {
            throw new FileException.CredentialProblem(e);
        }
        return null;
    }

    /**
     * Change the file's content type attribute.
     *
     * @param invokerCredential the credential of the entity setting the
     *                          content type
     * @param invokerPassword   the password for {@code invokerCredential}
     * @param predicatedVersion the version that must be current when the
     *                          ownership change is performed, or {@code null}
     *                          if any version will do
     * @param contentType       the file's new content type attribute value
     */
    public void setContentType(Credential invokerCredential,
            char[] invokerPassword, BeehiveObjectId predicatedVersion,
            String contentType)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteInaccessible,
            FileException.CelesteFailed,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        OrderedProperties attrs = new OrderedProperties();
        attrs.setProperty(CONTENT_TYPE_NAME, contentType);
        this.setFileAttributes(attrs, invokerCredential, invokerPassword,
            predicatedVersion);
    }

    /**
     * Change the file's deletion time to live attribute.
     *
     * @param invokerCredential     the credential of the entity setting the
     *                              deletion time to live
     * @param invokerPassword       the password for {@code invokerCredential}
     * @param predicatedVersion     the version that must be current when the
     *                              ownership change is performed, or {@code
     *                              null} if any version will do
     * @param deletionTimeToLive    the file's new deletion time to live
     *                              attribute value
     */
    public void setDeletionTimeToLive(Credential invokerCredential, char[] invokerPassword, BeehiveObjectId predicatedVersion, long deletionTimeToLive)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteInaccessible,
            FileException.CelesteFailed,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {
        OrderedProperties attrs = new OrderedProperties();
        attrs.setProperty(DELETION_TIME_TO_LIVE_NAME,
            Long.toString(deletionTimeToLive));
        this.setFileAttributes(attrs, invokerCredential, invokerPassword,
            predicatedVersion);
    }

    /**
     * <p>
     *
     * Acquire the modification lock on this file, thereby preventing all
     * invocations of operations that modify this file except ones that
     * supply {@code invokerCredential} as their credential argument.
     *
     * </p><p>
     *
     * The caller of this method should ensure that {@code lockToken} contains
     * a value unique across space and time (so that two distinct locks never
     * have equal tokens).
     *
     * </p>
     *
     * @param invokerCredential the credential of the entity locking the file
     * @param invokerPassword   the password for {@code invokerCredential}
     * @param lockToken         a string whose value distinguishes this lock
     *                          from all others across space and time
     * @param annotation        uninterpreted client-supplied information
     *                          associated with the lock
     */
    //
    // Since FileImpl has no notion of a directory hierarchy, the WebDAV
    // concept of lock depth (1 or infinity) doesn't apply at this level.
    //
    public void acquireModificationLock(Credential invokerCredential, char[] invokerPassword, String lockToken, Serializable annotation)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteInaccessible,
            FileException.CelesteFailed,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        ensureMetadata();

        LockFileOperation op = new LockFileOperation(
            this.getFileIdentifier(),
            invokerCredential.getObjectId(),
            lockToken,
            null,
            annotation);
        try {
            Credential.Signature signature =
                invokerCredential.sign(invokerPassword, op.getId());
            OrderedProperties metadata = null;
            CelesteAPI proxy = null;
            try {
                proxy = this.proxyCacheGetAndRemove(this.socketAddr);
                ResponseMessage response = proxy.lockFile(op, signature);
                metadata = response.getMetadata();
            } catch (CelesteException.CredentialException e) {
                throw new FileException.CredentialProblem(e);
            } catch (CelesteException.AccessControlException e) {
                throw new FileException.PermissionDenied(e);
            } catch (CelesteException.DeletedException e) {
                throw new FileException.Deleted(e);
            } catch (CelesteException.FileLocked e) {
                //
                // XXX: Should obtain the lock token for the conflicting lock
                //      and stuff it into the exception thrown.  (Is that
                //      information directly accessible from e?  Yes, in its
                //      metadata.)
                //
                throw new FileException.Locked(e);
            } catch (CelesteException.IllegalParameterException e) {
                throw new FileException.Runtime(e);
            } catch (CelesteException.NotFoundException e) {
                throw new FileException.NotFound(e);
            } catch (CelesteException.OutOfDateException e) {
                //
                // XXX: What to do here?  Does this indicate that our
                //      predication isn't right?  (If so, then we shouldn't be
                //      getting the exception at all, so Runtime might be a
                //      suitable response.)
                //
                throw new FileException.Runtime(e);
            } catch (CelesteException.RuntimeException e) {
                throw new FileException.Runtime(e);
            } catch (CelesteException.VerificationException e) {
                throw new FileException.Runtime(e);
            } catch (IOException e) {
                throw new FileException.IOException(e);
            } catch (ClassNotFoundException e) {
                throw new FileException.Runtime(e);
            } finally {
                this.proxyCacheAddAndEvictOld(this.socketAddr, proxy);
            }

            this.latestVersionId = this.processReplyMetadata(metadata);
        } catch (Profile_.Exception e) {
            throw new FileException.CredentialProblem(e);
        }
    }

    /**
     * Relinquish the modification lock on this file, thereby allowing
     * unrestricted invocations of operations that modify this file.
     *
     * @param invokerCredential the credential of the entity unlocking the file
     * @param invokerPassword   the password for {@code invokerCredential}
     * @param lockToken         the string given as argument to the {@link
     *                          #acquireModificationLock(Credential, char[], String, Serializable)
     *                          acquireModificationLock()} call that acquired
     *                          the lock being released
     */
    public void releaseModificationLock(Credential invokerCredential, char[] invokerPassword, String lockToken)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteInaccessible,
            FileException.CelesteFailed,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.NotLocked,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        ensureMetadata();

        UnlockFileOperation op = new UnlockFileOperation(
            this.getFileIdentifier(),
            invokerCredential.getObjectId(),
            lockToken);
        try {
            Credential.Signature signature =
                invokerCredential.sign(invokerPassword, op.getId());
            OrderedProperties metadata = null;
            CelesteAPI proxy = null;
            try {
                proxy = this.proxyCacheGetAndRemove(this.socketAddr);
                ResponseMessage response = proxy.unlockFile(op, signature);
                metadata = response.getMetadata();
            } catch (CelesteException.CredentialException e) {
                throw new FileException.CredentialProblem(e);
            } catch (CelesteException.AccessControlException e) {
                throw new FileException.PermissionDenied(e);
            } catch (CelesteException.DeletedException e) {
                throw new FileException.Deleted(e);
            } catch (CelesteException.FileLocked e) {
                //
                // XXX: Should obtain the lock token for the conflicting lock
                //      and stuff it into the exception thrown.  (Is that
                //      information directly accessible from e?  Yes, in its
                //      metadata.)
                //
                throw new FileException.Locked(e);
            } catch (CelesteException.FileNotLocked e) {
               throw new FileException.NotLocked(e);
            } catch (CelesteException.IllegalParameterException e) {
                throw new FileException.Runtime(e);
            } catch (CelesteException.NotFoundException e) {
                throw new FileException.NotFound(e);
            } catch (CelesteException.OutOfDateException e) {
                //
                // XXX: What to do here?  Does this indicate that our
                //      predication isn't right?  (If so, then we shouldn't be
                //      getting the exception at all, so Runtime might be a
                //      suitable response.)
                //
                throw new FileException.Runtime(e);
            } catch (CelesteException.RuntimeException e) {
                throw new FileException.Runtime(e);
            } catch (CelesteException.VerificationException e) {
                throw new FileException.Runtime(e);
            } catch (IOException e) {
                throw new FileException.IOException(e);
            } catch (ClassNotFoundException e) {
                throw new FileException.Runtime(e);
            } finally {
                this.proxyCacheAddAndEvictOld(this.socketAddr, proxy);
            }

            this.latestVersionId = this.processReplyMetadata(metadata);
        } catch (Profile_.Exception e) {
            throw new FileException.CredentialProblem(e);
        }
    }

    public AObjectVersionMapAPI.Lock inspectModificationLock(Credential invokerCredential, char[] invokerPassword)
        throws
            FileException.BadVersion,
            FileException.CapacityExceeded,
            FileException.CelesteInaccessible,
            FileException.CelesteFailed,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.Locked,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.RetriesExceeded,
            FileException.Runtime,
            FileException.ValidationFailed {

        ensureMetadata();

        InspectLockOperation op = new InspectLockOperation(
            this.getFileIdentifier(),
            invokerCredential.getObjectId());
        try {
            Credential.Signature signature =
                invokerCredential.sign(invokerPassword, op.getId());
            CelesteAPI proxy = null;
            try {
                proxy = this.proxyCacheGetAndRemove(this.socketAddr);
                //
                // XXX: Extract the reply's metadata and use it to update the
                //      current version.
                //
                ResponseMessage reply = proxy.inspectLock(op/*, signature*/);
                return reply.get(AObjectVersionMapAPI.Lock.class);
            } catch (CelesteException.DeletedException e) {
                throw new FileException.Deleted(e);
            } catch (CelesteException.NotFoundException e) {
                throw new FileException.NotFound(e);
            } catch (CelesteException.RuntimeException e) {
                throw new FileException.Runtime(e);
            } catch (IOException e) {
                throw new FileException.IOException(e);
            } catch (ClassNotFoundException e) {
                throw new FileException.Runtime(e);
            } catch (Exception e) {
                throw new FileException.Runtime(e);
            } finally {
                this.proxyCacheAddAndEvictOld(this.socketAddr, proxy);
            }
        } catch (Profile_.Exception e) {
            throw new FileException.CredentialProblem(e);
        }
    }

    /**
     * <p>
     *
     * Return {@code true} if this file exists and {@code false} otherwise.
     *
     * </p><p>
     *
     * Unless some external means is used to prevent it, it is possible that
     * concurrent activity could invalidate the result value before control
     * returns to the caller.
     *
     * </p>
     *
     * @return {@code true} if the file exists and {@code false otherwise}
     */
    public boolean fileExists() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.Runtime,
            FileException.ValidationFailed {
        //
        // Getting any exception except the one explicitly caught implies that
        // we don't know definitively whether or not the file exists.  So we
        // pass those exceptions through.
        //
        try {
            //
            // Get up-to-date metadata, so that if the file's been deleted we
            // learn about it.
            //
            refreshMetadata();
        } catch (FileException.NotFound e) {
            return false;
        }
        return true;
    }

    //
    // Cache management methods.
    //

    /**
     * Discard all cached data from this {@code FileImpl} instance.
     */
    public void flush() {
        this.cache.flush();
    }

    /**
     * Discard cached data from this {@code FileImpl} instance.  If {@code
     * retainCurrentVersion} is {@code true} retain data cached for the file
     * version that's current at the time of the call; otherwise, discard all
     * data.
     *
     * @param retainCurrentVersion  if {@code true}, retain data cached for
     *                              the current version while discarding data
     *                              belonging only to previous versions;
     *                              otherwise, discard all cached data
     */
    public void flush(boolean retainCurrentVersion) {
        this.cache.flush(retainCurrentVersion);
    }

    /**
     * Reports whether or not read, write, and truncate operations will add
     * new data to the local cache that this {@code FileImpl} instance
     * maintains.
     *
     * @return  {@code true} if i/o operations will add new data to the cache
     *          and {@code false} otherwise
     */
    public boolean isCacheEnabled() {
        return this.cache.isCacheEnabled();
    }

    /**
     * Controls whether or not read, write, and truncate operations will add
     * new data to the local cache that this {@code FileImpl} instance
     * maintains.
     *
     * @param enable    {@code true} to enable adding new data to the cache
     *                  and {@code false} to prevent new data from being added
     */
    public void setCacheEnabled(boolean enable) {
        this.cache.setCacheEnabled(enable);
    }

    //
    // Methods for reporting various statistics.
    //
    // For now, statistics consist solely of those reported by the buffer
    // cache.  They could probably be augmented, at least at the aggregate
    // level, with information on numbers of files in play, and so on.
    //

    /**
     * Return an XHTML table containing performance statistics for accesses to
     * this file.
     *
     * @return  this file's performance statistics, as an XHTML table
     */
    public XHTML.Table getStatisticsAsXHTML() {
        return this.cache.getStatisticsAsXHTML();
    }

    /**
     * Return an XHTML table containing performance statistics for accesses to
     * all files mediated by this instantiation of {@code FileImpl}.
     *
     * @return  aggregate file access statistics, as an XHTML table
     */
    public static XHTML.Table getAggregateStatisticsAsXHTML() {
        return BufferCache.getAggregateStatisticsAsXHTML();
    }

    // utility functions for directories, really

    /**
     * Get the default data encoding version that identifies the communication
     * protocol this implementation uses with Celeste.
     *
     * @return  the default data encoding version
     */
    public static int getDefaultDataEncodingVersion() {
        return defaultDataEncodingVersion;
    }

    //
    // Ensure that we have metadata for the file (without worrying about
    // whether anything we have already is up to date).  Once we have some,
    // return the per-version metadata for version current at the time of the
    // call (if we already had one in hand) or the version we fetched (if we
    // didn't).
    //
    private VersionMetadata ensureMetadata() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {

        VersionMetadata versionMetadata = null;
        //
        // If we've seen any metadata at all, we've seen a version id for the
        // file.
        //
        synchronized (this) {
            if (this.latestVersionId == null)
                versionMetadata = refreshMetadata();
            assert this.latestVersionId != null;
            if (versionMetadata == null)
                versionMetadata = versionToMetadata.get(this.latestVersionId);
        }
        return versionMetadata;
    }

    //
    // Private methods for managing file metadata
    //

    //
    // Getters from OrderedProperties as supplied as the attributes argument
    // to create().  (That is, they concern themselves with transferring
    // information back and forth between FileImpl and its clients.  They are
    // _not_ appropriate for use in transferring information back and forth
    // between FileImpl and Celeste.)
    //
    // All expect their attribute, if present at all, to be in string form,
    // throwing ClassCastException if it's not.
    //
    // XXX: Is it really wise to use ClassCastException, which is a
    //      RuntimeException, rather than one that must be explicitly handled?
    //
    // Each supplies a default values if the attribute in question is missing.
    // For attributes that can be set by client code at create time, these
    // defaults come from the static defaultCreationAttributes map.
    //

    private CelesteACL aclFromAttrs(Properties attrs) {
        String rawACL = attrs.getProperty(ACL_NAME);
        if (rawACL == null) {
            rawACL = FileImpl.defaultCreationAttributes.getProperty(ACL_NAME);
        }
        try {
            return CelesteACL.getEncodedInstance(rawACL);
        } catch (ClassNotFoundException e) {
            //
            // The encoded value didn't successfully yield a CelesteACL.  For
            // consistency with the other *FromAttrs() methods, report the
            // failure as a runtime exception rather than one that must be
            // explicitly handled.  Indeed, report it as a ClassCastException,
            // since that's what's documented above.
            //
            throw new ClassCastException(e.getMessage());
        }
    }

    private int blockSizeFromAttrs(Properties attrs) {
        String rawBlockSize = attrs.getProperty(BLOCK_SIZE_NAME);
        if (rawBlockSize == null)
            rawBlockSize = FileImpl.defaultCreationAttributes.getProperty(
                BLOCK_SIZE_NAME);
        return Integer.valueOf(rawBlockSize);
    }

    private boolean cacheEnabledFromAttrs(Properties attrs) {
        String rawCacheEnabled = attrs.getProperty(CACHE_ENABLED_NAME);
        if (rawCacheEnabled == null)
            rawCacheEnabled = FileImpl.defaultCreationAttributes.getProperty(
                CACHE_ENABLED_NAME);
        return Boolean.valueOf(rawCacheEnabled);
    }

    //
    // XXX: At least for the moment, omit this attribute from
    //      FileImpl.defaultCreationAttributes.  Need to decide how to handle
    //      it, since it's an implementation vehicle for handling
    //      client-supplied properties rather than something intended to be
    //      exposed directly to clients.
    //
    private OrderedProperties clientPropertiesFromAttrs(Properties attrs) {
        String rawClientMetadata = attrs.getProperty(CLIENT_METADATA_NAME);
        if (rawClientMetadata == null)
            return new OrderedProperties();
        OrderedProperties clientProperties = new OrderedProperties();
        clientProperties.load(ByteBuffer.wrap(
            CelesteEncoderDecoder.fromHexString(rawClientMetadata)));
        return clientProperties;
    }

    private String contentTypeFromAttrs(Properties attrs) {
        String rawContentType = attrs.getProperty(CONTENT_TYPE_NAME);
        if (rawContentType == null)
            rawContentType = FileImpl.defaultCreationAttributes.getProperty(
                CONTENT_TYPE_NAME);
        return rawContentType;
    }

    private long deletionTimeToLiveFromAttrs(Properties attrs) {
        String rawDeletionTimeToLive =
            attrs.getProperty(DELETION_TIME_TO_LIVE_NAME);
        if (rawDeletionTimeToLive == null) {
            rawDeletionTimeToLive = FileImpl.defaultCreationAttributes.getProperty(
                DELETION_TIME_TO_LIVE_NAME);
        }
        return Long.valueOf(rawDeletionTimeToLive);
    }

    private boolean isDeletedFromAttrs(Properties attrs) {
        String rawIsDeleted = attrs.getProperty(IS_DELETED_NAME);
        if (rawIsDeleted == null)
            return false;
        return Boolean.valueOf(rawIsDeleted);
    }

    private long metadataChangedTimeFromAttrs(Properties attrs) {
        String rawMetadataModificationTime =
            attrs.getProperty(METADATA_CHANGED_TIME_NAME);
        if (rawMetadataModificationTime == null)
            return 0;
        return Long.valueOf(rawMetadataModificationTime);
    }

    private long modifiedTimeFromAttrs(Properties attrs) {
        String rawModifiedTime =
            attrs.getProperty(MODIFIED_TIME_NAME);
        if (rawModifiedTime == null)
            return 0;
        return Long.valueOf(rawModifiedTime);
    }

    private String replicationParamsFromAttrs(Properties attrs) {
        String rawReplicationParams =
            attrs.getProperty(REPLICATION_PARAMETERS_NAME);
        if (rawReplicationParams == null) {
            rawReplicationParams = FileImpl.defaultCreationAttributes.getProperty(
                REPLICATION_PARAMETERS_NAME);
        }
        return rawReplicationParams;
    }

    private long serialNumberFromAttrs(Properties attrs) {
        String rawSerialNumber = attrs.getProperty(FILE_SERIAL_NUMBER_NAME);
        if (rawSerialNumber == null)
            return 0;
        return Long.valueOf(rawSerialNumber);
    }

    private boolean signModificationsFromAttrs(Properties attrs) {
        String rawSignModifications =
            attrs.getProperty(SIGN_MODIFICATIONS_NAME);
        if (rawSignModifications == null) {
            rawSignModifications = FileImpl.defaultCreationAttributes.getProperty(
                SIGN_MODIFICATIONS_NAME);
        }
        return Boolean.valueOf(rawSignModifications);
    }

    private long timeToLiveFromAttrs(Properties attrs) {
        String rawTimeToLive = attrs.getProperty(TIME_TO_LIVE_NAME);
        if (rawTimeToLive == null) {
            rawTimeToLive = FileImpl.defaultCreationAttributes.getProperty(
                TIME_TO_LIVE_NAME);
        }
        return Long.valueOf(rawTimeToLive);
    }

    //
    // Unconditionally fetch fresh information about the file from Celeste.
    // Set the current version to the one the fetch reveals and return its
    // per-version metadata.
    //
    private VersionMetadata refreshMetadata() throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        return refreshMetadata(null);
    }

    //
    // Unconditionally fetch fresh information about the given version of the
    // file from Celeste.  Return the per-version metadata associated with
    // that version.  If the given version is null, update the current version
    // to the latest version as fetched from Celeste.
    //
    private VersionMetadata refreshMetadata(BeehiveObjectId vObjectId) throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed{

        InspectFileOperation operation = new InspectFileOperation(
            this.getFileIdentifier(),
            BeehiveObjectId.ZERO);
        ResponseMessage msg = null;
        CelesteAPI proxy = null;
        try {
            proxy = this.proxyCacheGetAndRemove(this.socketAddr);
            msg = proxy.inspectFile(operation);
        } catch (IOException e) {
            throw new FileException.IOException(e);
        } catch (CelesteException.NotFoundException e) {
            throw new FileException.NotFound(e);
        } catch (CelesteException.RuntimeException e) {
            throw new FileException.Runtime(e);
        } catch (CelesteException.DeletedException e) {
            throw new FileException.Runtime(e);
        } catch (ClassNotFoundException e) {
            throw new FileException.Runtime(e);
        } finally {
            this.proxyCacheAddAndEvictOld(this.socketAddr, proxy);
        }

        OrderedProperties metadata = msg.getMetadata();

        boolean updateLatestVguid = (vObjectId == null);
        if (updateLatestVguid) {
            vObjectId = new BeehiveObjectId(
                metadata.getProperty(CelesteAPI.VOBJECTID_NAME));
            //
            // If we've already seen this version, it suffices to return the
            // information that's already been obtained for it.
            //
            if (vObjectId.equals(this.latestVersionId))
                return versionToMetadata.get(vObjectId);
        }

        try {
            ClientMetaData cmd = msg.get(ClientMetaData.class);
            OrderedProperties props = setOrCheckCommonMetadata(cmd, metadata);
            VersionMetadata versionMetadata =
                extractVersionMetadata(vObjectId, props, metadata);

            if (updateLatestVguid)
                this.latestVersionId = vObjectId;

            return versionMetadata;
        } catch (ClassCastException e) {
            //
            // The metadata is in a format we don't understand (a mismatch in
            // ClientMetaData class versions or something even more serious).
            // There's a choice of blame that dictates which exception to throw.
            // Take the blame here rather than assigning it to Celeste.
            //
            throw new FileException.Runtime(e);
        } catch (FileException.BadVersion e) {
            throw e;
        } catch (FileException.ValidationFailed e) {
            throw e;
        } catch (Exception e) {
            //
            // The message is reporting a failure in executing the
            // inspectFile() operation that induced an exception on the Celeste
            // side.
            //
            throw new FileException.CelesteFailed(e);
        }
    }

    //
    // We've obtained new metadata in a reply message.  Unpack and process it,
    // returning the file version to which it applies.
    //
    private BeehiveObjectId processReplyMetadata(OrderedProperties metadata)
        throws
            FileException.BadVersion,
            FileException.IOException,
            FileException.ValidationFailed {

        BeehiveObjectId versionId = new BeehiveObjectId(
            metadata.getProperty(CelesteAPI.VOBJECTID_NAME));
        synchronized (this) {
            if (!versionId.equals(this.latestVersionId)) {
                //
                // The file has been modified in some way and thus has a new
                // version.  Reset our metadata to match the new version.
                //
                String encodedClientMetaData =
                    metadata.getProperty(CelesteAPI.CLIENTMETADATA_NAME);
                //
                // XXX: Still haven't implemented verification of version
                //      authenticity for files.  Code to do so will go here,
                //      which will require inspecting more of the metadata
                //      (in particular, the signature).
                //
                ClientMetaData clientMetadata = null;
                try {
                    clientMetadata = new ClientMetaData(encodedClientMetaData);
                } catch (ClientMetaData.ContextException e) {
                    throw new FileException.IOException(e);
                }
                OrderedProperties props =
                    setOrCheckCommonMetadata(clientMetadata, metadata);
                extractVersionMetadata(versionId, props, metadata);
                this.latestVersionId = versionId;
            }
            //
            // XXX: NYI:  Decryption / hash tree authentication and the like
            //      should take place on the basis of the meta data just
            //      obtained.
            //

            return versionId;
        }
    }

    //
    // Inspect the (Celeste-level) client metadata and use it to set metadata
    // that's common across all versions, if that metadata hasn't already been
    // initialized; or to verify that it's what we think it should be, if it
    // has been initialized.  Return an OrderedProperties object holding all
    // the metadata from the argument.
    //
    private OrderedProperties setOrCheckCommonMetadata(ClientMetaData fileImplMetadata, OrderedProperties celesteMetadata) throws FileException.BadVersion,
            FileException.ValidationFailed {
        //
        // Get version-independent information from attributes that Celeste
        // understands an interprets.  (There's currently only one such
        // attribute: the replication parameters.)
        //
        String putativeReplicationParams = celesteMetadata.getProperty(CelesteAPI.DEFAULTREPLICATIONPARAMETERS_NAME);

        //
        // Convert fileImplMetadata into a usable (OrderedProperties) form.
        // The resulting props contain metadata that Celeste does not
        // interpret but that FileImpl el al do.
        //
        OrderedProperties props = new OrderedProperties();
        props.load(ByteBuffer.wrap(fileImplMetadata.getContext()));

        int putativeDataEncodingVersion = Integer.parseInt(props.getProperty(FileImpl.VERSION_NAME));
        long putativeCreatedTime = Long.parseLong(props.getProperty(CREATED_TIME_NAME));
        long putativeSerialNumber = this.serialNumberFromAttrs(props);
        boolean putativeSignModifications = this.signModificationsFromAttrs(props);
        long putativeTimeToLive = this.timeToLiveFromAttrs(props);

        //
        // Make sure that any updates we make are all mutually consistent with
        // each other.
        //
        synchronized(this) {
            if (this.dataEncodingVersion == 0) {
                //
                // Not yet initialized.  Do so.
                //
                this.dataEncodingVersion = putativeDataEncodingVersion;
                this.createdTime = putativeCreatedTime;
                this.serialNumber = putativeSerialNumber;
                this.replicationParams = putativeReplicationParams;
                this.signModifications = putativeSignModifications;
                this.timeToLive = putativeTimeToLive;
            } else {
                //
                // Do sanity checks.  (For the sake of efficiency and out of
                // laziness, the code below only checks a few of the
                // attributes.)
                //
                // XXX: How much paranoia is justified here?
                //
                if (putativeDataEncodingVersion != FileImpl.defaultDataEncodingVersion)
                    throw new FileException.BadVersion();
                if (this.createdTime != putativeCreatedTime) {
                    System.err.printf("createdTime %d != putative %d\n", this.createdTime, putativeCreatedTime);
                    System.err.println(props.toString());
                    throw new FileException.ValidationFailed();
                }
                if (this.serialNumber != putativeSerialNumber)
                    throw new FileException.ValidationFailed();
            }
        }

        return props;
    }

    //
    // Extract per-version metadata for the given version from the file
    // properties given as argument.  Return the extracted metadata.
    //
    // Note that msgMetadata contains a WriterOpaqueContext from which
    // OrderedProperties can be extracted.  In many cases, these will duplicate
    // the ones passed in as the props argument.  However, in some cases,
    // callers might wish to override the ones embedded in msgMetadata, which
    // is why the explicit OrderedProperties argument remains.
    //
    // XXX: It's time to rework this code again.  WriterOpaqueContexts are
    //      history (having changed into ClientMetaData instances).  Some of
    //      the information in msgMetadata is available locally and it's worth
    //      seeing whether it's necessary to pass it into Celeste (as data in
    //      the CelesteOperation argument) just so that Celeste can hand it
    //      back in msgMetadata.
    //
    private VersionMetadata extractVersionMetadata(BeehiveObjectId version,
            OrderedProperties props, OrderedProperties msgMetadata) {
        //
        // If we already have the metadata, we're done (since it's immutable
        // per-version).
        //
        VersionMetadata versionMetadata = versionToMetadata.get(version);
        if (versionMetadata != null)
            return versionMetadata;

        long metadataChangedTime = this.metadataChangedTimeFromAttrs(props);
        String fileSizeString =
            msgMetadata.getProperty(CelesteAPI.FILE_SIZE_NAME);
        long fileSize = Long.parseLong(fileSizeString);
        String defaultBObjectSizePropertyValue =
            msgMetadata.getProperty(CelesteAPI.DEFAULTBOBJECTSIZE_NAME);
        int blockSize = (defaultBObjectSizePropertyValue != null) ?
            Integer.parseInt(defaultBObjectSizePropertyValue) :
            FileImpl.maxBufferLength;
        String contentType = props.getProperty(CONTENT_TYPE_NAME);
        String encryptedDeleteTokenString =
            props.getProperty(FileImpl.ENCRYPTED_DTOKEN_NAME);
        byte[] encryptedDeleteToken =
            CelesteEncoderDecoder.fromHexString(encryptedDeleteTokenString);
        String ownerString =
            msgMetadata.getProperty(CelesteAPI.VOBJECT_OWNER_NAME);
        BeehiveObjectId owner = (ownerString == null) ? null :
            new BeehiveObjectId(ownerString);
        String groupString =
            msgMetadata.getProperty(CelesteAPI.VOBJECT_GROUP_NAME);
        BeehiveObjectId group = (groupString == null) ? null :
            new BeehiveObjectId(groupString);
        //
        // XXX: It would be nice to be able to use aclFromAttrs() to obtain
        //      the ACL, except it comes from the message's Celeste-level
        //      metadata rather than from its FileImpl-maintained metadata.
        //      We could still almost get away with using the method, except
        //      that there's no guarantee that the names for the attribute
        //      coincide.
        //
        String aclString =
            msgMetadata.getProperty(CelesteAPI.VOBJECT_ACL_NAME);
        CelesteACL acl = null;
        try {
            acl = CelesteACL.getEncodedInstance(
                CelesteEncoderDecoder.fromHexString(aclString));
        } catch (ClassNotFoundException e) {
            //
            // This could potentially happen if we dig up an ACL that was
            // encoded from an old version of the CelesteACL class.
            //
            // XXX: The question is how best to respond.  For now, punt and
            //      throw a RuntimeException.
            //
            throw new RuntimeException(e);
        }
        versionMetadata = new VersionMetadata(
            this.modifiedTimeFromAttrs(props),
            metadataChangedTime,
            fileSize,
            this.isDeletedFromAttrs(props),
            this.clientPropertiesFromAttrs(props),
            contentType,
            blockSize, deletionTimeToLiveFromAttrs(props),
            encryptedDeleteToken, owner, group, acl);
        versionToMetadata.put(version, versionMetadata);
        return versionMetadata;
    }

    //
    // By converting an arbitrary exception (resulting from the proxy cache's
    // factory having trouble with its InetSocketAddress argument) into a
    // FileException, this impedance matching method allows client code to
    // avoid having to handle arbitrary exceptions at each use site.
    //
    private CelesteAPI proxyCacheGetAndRemove(InetSocketAddress addr) throws
            FileException.CelesteInaccessible {
        try {
            return this.proxyCache.getAndRemove(addr);
        } catch (Exception e) {
            throw new FileException.CelesteInaccessible(e);
        }
    }

    //
    // Present for symmetry with proxyCacheGetAndRemove() above.
    //
    private void proxyCacheAddAndEvictOld(InetSocketAddress addr,
            CelesteAPI proxy) {
        this.proxyCache.addAndEvictOld(addr, proxy);
    }

    //
    // Convenience method; combines length interrogation with reads
    // until extents spanning the reported length have been read and dumped.
    // That is, it reads the whole thing and dumps it out.
    //
    // N.B. Intended only for the short files used below in doMain()!
    // N.B. Performs actions with side effects; in particular, the current
    //      file version may be updated.
    //
    // Now (perhaps temporarily) public, to allow FileImpl clients to more
    // easily debug their code.
    //
    public static String dumpFile(FileImpl file, Credential readerCredential, char[] readerPassword)
        throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.CredentialProblem,
            FileException.Deleted,
            FileException.IOException,
            FileException.NotFound,
            FileException.PermissionDenied,
            FileException.Runtime,
            FileException.ValidationFailed,
            FileException {
        StringBuilder sb = new StringBuilder();
        long length = file.getFileLength();
        sb.append(String.format("Length: %d, extents:%n", length));
        long offset = 0;
        while (offset < length) {
            ExtentBuffer eb =
                file.read(readerCredential, readerPassword, 512, offset);
            if (eb.remaining() == 0) {
                throw new FileException("zero-length read!");
            }
            sb.append("  ").append(eb.asString()).append("\n");
            offset = eb.getEndOffset();
            if (offset == Long.MIN_VALUE)
                break;
        }
        return sb.toString();
    }

    private static String dumpOwnerAndGroup(ProfileCache cache, FileImpl file)
        throws
            FileException.BadVersion,
            FileException.CelesteFailed,
            FileException.CelesteInaccessible,
            FileException.IOException,
            FileException.NotFound,
            FileException.Runtime,
            FileException.ValidationFailed {
        BeehiveObjectId ownerId = file.getOwnerId(false);
        BeehiveObjectId groupId = file.getGroupId(false);

        try {
            String owner = "none";
            if (ownerId != null) {
                Credential p = cache.get(ownerId);
                if (p != null)
                    owner = p.getName();
            }

            //
            // XXX: Need full group machinery before it's sensible to try to
            //      turn a group id into a readable name.
            //
            String group = "none";
            if (groupId != null)
                group = groupId.toString();

            return String.format("owner: %s\tgroup: %s", owner, group);
        } catch (Exception e) {
            throw new FileException.Runtime(e);
        }
    }

    //
    // Obtain a Profile from the given cache, name, and password, using the
    // given Celeste node and erasure coder.  If the Profile does not exist in
    // the cache, it is created.
    //
    private static Credential obtainProfile(ProfileCache cache, String name,
            String passwd, CelesteProxy node, String coder)
            throws
            FileException.CredentialProblem,
            FileException.Runtime,
            IOException {

        Credential p = null;
        try {
            p = cache.get(name);
        } catch (Exception e) {
            //
            // Fall through to the code below.
            //
        }
        try {
            if (p == null) {
                p = new Profile_(name, passwd.toCharArray());
                NewCredentialOperation operation = new NewCredentialOperation(p.getObjectId(), BeehiveObjectId.ZERO, coder);
                Credential.Signature signature = p.sign(passwd.toCharArray(), operation.getId());
                node.newCredential(operation, signature, p);
            }
        } catch (ClassNotFoundException e) {
            throw new FileException.CredentialProblem(e);
        } catch (Credential.Exception e) {
            throw new FileException.CredentialProblem(e);
        } catch (CelesteException.AlreadyExistsException e) {
            throw new FileException.CredentialProblem(e);
        } catch (CelesteException.CredentialException e) {
            throw new FileException.CredentialProblem(e);
        } catch (CelesteException.VerificationException e) {
            throw new FileException.Runtime(e);
        } catch (CelesteException.NoSpaceException e) {
            throw new FileException.Runtime(e);
        } catch (CelesteException.RuntimeException e) {
            throw new FileException.Runtime(e);
        }
        return p;
    }

    //
    // Emit time-stamped output.  Used in doMain() below.
    //
    private static void datePrintf(String format, Object... args) {
        Date date = new Date();
        System.out.printf("%tT %s", date, String.format(format, args));
    }

    /**
     * <p>
     *
     * Test the {@code FileImpl} implementation.
     *
     * </p><p>
     *
     * Accepts an optional numeric argument giving the number of times to
     * repeat the test.  The default value is 1.  Negative values request the
     * test to be repeated indefinitely.
     *
     * </p>
     *
     */
    public static void main(String[] args) {
        //
        // Process arguments.
        //
        int repetitions = 1;
        if (args.length > 0) {
            try {
                repetitions = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                //
                // Ignore; simply use the default repetition value.
                //
            }
        }

        //
        // Run the test.
        //
        if (repetitions < 0) {
            while (true)
                doMain();
        } else {
            for (int testNum = 0; testNum < repetitions; testNum++)
                doMain();
        }
    }

    private static void doMain() {
        String profileName = "FileImpl-tester@celeste.sun.com";
        String otherProfileName = "FileImpl-other-tester@celeste.sun.com";
        String replicationParams =
            "AObject.Replication.Store=2;" +
            "VObject.Replication.Store=2;" +
            "BObject.Replication.Store=2";
        long celesteTimeOut = Time.secondsInMilliseconds(60);

        try {
            datePrintf(
                "================ FileImpl test start ================%n");

            InetSocketAddress nodeAddr =
                new InetSocketAddress("127.0.0.1", 14000);
            CelesteProxy node = new CelesteProxy(nodeAddr,
                celesteTimeOut, TimeUnit.MILLISECONDS);

            ProfileCache pcache = new ProfileCache(node);

            //
            // Set up the profile of the principal that will act as creator
            // and owner for the file(s) created during the test.
            //
            String pPassword = "12345";
            Credential p = FileImpl.obtainProfile(pcache, profileName, pPassword, node, replicationParams);

            //
            // Set up a profile to be used for a negative ACL check.
            //
            String pOtherPassword = "23456";
            Credential pOther = FileImpl.obtainProfile(pcache, otherProfileName, pOtherPassword, node, replicationParams);

            //
            // Cons up an identifier for a new file and then create a handle
            // for that (as yet nonexistent) file.
            //
            BeehiveObjectId fid = new BeehiveObjectId();
            FileIdentifier fileId = new FileIdentifier(p.getObjectId(), fid);
            FileImpl file = new FileImpl(nodeAddr, fileId);

            //
            // Verify that the file doesn't yet exist.
            //
            if (file.fileExists())
                datePrintf("file unexpectedly existed before creation!%n");

            //
            // Set up a client metadata property to be supplied when file is
            // created and to be checked afterward.
            //
            OrderedProperties myProps = new OrderedProperties();
            myProps.setProperty("myAttribute", "myAttribute's value");

            //
            // Reduce load induced by multiple executions by specifying a 10
            // minute time to live.  Once anti-objects get the time to live of
            // their originals, that should bound anti-object publishing
            // traffic induced by this test.
            //
            OrderedProperties attrs = new OrderedProperties();
            attrs.setProperty(BLOCK_SIZE_NAME, "1024");
            attrs.setProperty(CONTENT_TYPE_NAME, "text/plain");
            attrs.setProperty(REPLICATION_PARAMETERS_NAME, replicationParams);
            attrs.setProperty(TIME_TO_LIVE_NAME, "600");
            file.create(attrs, myProps, p, pPassword.toCharArray(),
                BeehiveObjectId.ZERO);

            datePrintf(
                "file created, user attributes:%n%s%nclientProperties: %s%n",
                dumpOwnerAndGroup(pcache, file), file.getClientProperties());

            //
            // Exercise the getAttributes() method by pulling out and
            // reporting on block size and whether file modifications must be
            // signed.
            //
            Set<String> attrNames = new HashSet<String>(Arrays.asList(
                new String[] {
                    BLOCK_SIZE_NAME,
                    CONTENT_TYPE_NAME,
                    SIGN_MODIFICATIONS_NAME
                }
            ));
            datePrintf("selected attributes: %s%n",
                file.getAttributes(attrNames, false));

            datePrintf("writing \"67890\" at offset 0%n");
            file.write("67890".getBytes(), 0, 5, 0,
                p, pPassword.toCharArray());

            //
            // Try creating the same file again.  Doing so should induce an
            // exception.
            //
            try {
                FileImpl file2 = new FileImpl(nodeAddr, fileId);
                file2.create(attrs, null, p, pPassword.toCharArray(),
                    BeehiveObjectId.ZERO);
                datePrintf("FAILED: created same file twice.%n");
            } catch (FileException.Exists e1) {
                datePrintf(
                    "Attempt to recreate the same file failed (as expected)%n");
            } catch (FileException e1) {
                datePrintf(
                    "Attempt to recreate the same file failed with bogus exception:%n");
                e1.printStackTrace();
            }

            datePrintf("file contents now:%n");
            System.out.printf("%s%n",
                dumpFile(file, p, pPassword.toCharArray()));

            datePrintf("writing \"ab\" at offset 3%n");
            file.write("ab".getBytes(), 0, 2, 3, p, pPassword.toCharArray());
            datePrintf("%s%n", dumpFile(file, p, pPassword.toCharArray()));

            //
            // Create a hole in the file by way of a write starting beyond the
            // end.
            //
            datePrintf("writing \"W\" at offset 8 (beyond current) EOF%n");
            try {
                file.write("W".getBytes(), 0, 1, 8,
                    p, pPassword.toCharArray());
            } catch (FileException e1) {
                datePrintf("FAIL: write should have succeeded, but did not.%n");
                e1.printStackTrace();
            }
            datePrintf("%s%n", dumpFile(file, p, pPassword.toCharArray()));

            //
            // Try having someone else write.  The default ACL on the file
            // should prohibit doing so.
            //
            datePrintf(
                "attempting to write \"BAD\" at offset 1 " +
                " via different profile%n");
            System.out.flush();
            try {
                file.write("BAD".getBytes(), 0, 3, 1,
                    pOther, pOtherPassword.toCharArray());
                datePrintf("FAIL: write succeeded, but shouldn't have%n");
            } catch (FileException.PermissionDenied e1) {
                datePrintf("write failed as expected%n%n");
            } catch (FileException e1) {
                datePrintf("FAIL: write failed in unexpected way: %s%n%n", e1);
                e1.printStackTrace();
            }

            //
            // Change file's ACL to explicitly permit the principal that
            // pOther represents to write the file.
            //
            datePrintf("changing file's ACL to allow pOther to write%n");
            List<CelesteACL.CelesteACE> aces = file.getACL().getCelesteACEs();
            CelesteACL.CelesteACE newACE = new CelesteACL.CelesteACE(
                new CelesteACL.IndividualMatcher(pOther.getObjectId()),
                EnumSet.of(CelesteACL.CelesteOps.writeFile),
                Disposition.grant
            );
            aces.add(newACE);
            CelesteACL extendedACL = new CelesteACL(
                aces.toArray(new CelesteACL.CelesteACE[aces.size()]));
            file.setACL(p, pPassword.toCharArray(), null, extendedACL);
            datePrintf("file's ACL now:%n%s%n", file.getACL());

            datePrintf("writing \"@-+=\" at offset 2%n");
            file.write("@-+=".getBytes(), 0, 4, 2, p, pPassword.toCharArray());
            datePrintf("%s%n", dumpFile(file, p, pPassword.toCharArray()));

            datePrintf("truncating to length 1%n");
            file.truncate(1, p, pPassword.toCharArray());
            datePrintf("%s%n", dumpFile(file, p, pPassword.toCharArray()));
            //
            // Verify a bug fix by checking the client properties again.
            //
            datePrintf(
                "after truncate: clientProperties: %s%n",
                file.getClientProperties());

            //
            // Now extend the length again.  Doing so had better not re-expose
            // the bytes truncated above.
            //
            try {
                datePrintf("truncating (via file) to length 9%n");
                file.truncate(9, p, pPassword.toCharArray());
                datePrintf("%s%n", dumpFile(file, p, pPassword.toCharArray()));
            } catch (FileException e1) {
                System.out.println("truncate extend failed:");
                e1.printStackTrace();
            }

            //
            // Create another handle for the file and write to it through that
            // handle.
            //
            FileImpl fileAlias = new FileImpl(nodeAddr, fileId);
            //
            // XXX: Force an update to fileAlias's metadata.  (Shouldn't be
            //      necessary.)
            //
            datePrintf(
                "forcing fileAlias metadata update by getting length (%d)%n%n",
                fileAlias.getFileLength());

            datePrintf("writing \"#10\" (via fileAlias) at offset 10%n");
            fileAlias.write("#10".getBytes(), 0, 3, 10,
                p, pPassword.toCharArray());
            datePrintf("%s%n", dumpFile(fileAlias,
                p, pPassword.toCharArray()));

            //
            // Read twice, first through the alias and second through the
            // original handle.  (For the same reasons as above, the second
            // read will be short.)
            //
            // XXX: This section of the test needs revamping.  dumpFile() is
            //      written in a way that forces metadata updates, so using it
            //      here forces file and fileAlias to both reflect the latest
            //      version.
            //
            //System.out.println("contents as viewed through fileAlias");
            //System.out.printf("%s%n", dumpFile(fileAlias));
            //System.out.println("contents as viewed through file");
            //System.out.printf("%s%n", dumpFile(file));

            //
            // Write to the handle that has an outdated version.
            //
            datePrintf("writing \"XYZ\" (via file) at offset 5%n");
            file.write("XYZ".getBytes(), 0, 3, 5, p, pPassword.toCharArray());
            datePrintf("%s%n", dumpFile(fileAlias,
                p, pPassword.toCharArray()));
            datePrintf("%s%n", dumpFile(file, p, pPassword.toCharArray()));

            //
            // Mark file contents as deleted (stops file evolution, but
            // retains past content, which can only accessed specifically by
            // version).
            //
            datePrintf("deleting file%n");
            file.markDeleted(p, pPassword.toCharArray());
            try {
                file.read(p, pPassword.toCharArray(), 200, 0);
                System.err.printf("%tT FAIL: read should have failed?",
                    new Date());
            } catch (FileException e) {
                datePrintf("PASS: read on deleted file correctly produced:%n");
                e.printStackTrace(System.err);
            }

            file = new FileImpl(nodeAddr, fileId);
            datePrintf("File marked as deleted: %s%n", file.getIsDeleted());

            file.purgeForever(p, pPassword.toCharArray());

            //
            // Verify that attempted access results in the proper failure.
            //
            try {
                file = new FileImpl(nodeAddr, fileId);
                file.create(attrs, null, p, pPassword.toCharArray(),
                    BeehiveObjectId.ZERO);
            } catch (FileException.Deleted e) {
                datePrintf(
                    "PASS: attempted create after expunge threw expected exception%n");
            } catch (FileException e) {
                datePrintf(
                    "FAIL: attempted create after expunge threw bogus exception:%n");
                e.printStackTrace();
            }

            //
            // Create another file, then verify that changing its owner works.
            // Wrap the sequence in a <acquireModificationLock(),
            // releaseModificationLock()> pair, and try writing to the file
            // with the new owner's credentials to verify that the lock is
            // effective.
            //
            BeehiveObjectId fid3 = new BeehiveObjectId();
            FileIdentifier fileId3 = new FileIdentifier(
                p.getObjectId(), fid3);
            FileImpl file3 = new FileImpl(nodeAddr, fileId3);
            file3.create(attrs, null, p, pPassword.toCharArray(),
                BeehiveObjectId.ZERO);
            datePrintf("file3 created, attributes:%n%s%n%n",
                dumpOwnerAndGroup(pcache, file3));
            //
            // Get the lock.
            //
            String lockToken = new BeehiveObjectId().toString();
            try {
                file3.acquireModificationLock(p, pPassword.toCharArray(),
                    lockToken, null);
            } catch (FileException.PermissionDenied e) {
                Throwable cause = e.getCause();
                if (cause != null) {
                    datePrintf("FAIL: bogus PermissionDenied caused by:%n");
                    e.getCause().printStackTrace(System.err);
                } else {
                    datePrintf("FAIL: bogus PermissionDenied:%n");
                    e.printStackTrace(System.err);
                }
                datePrintf("bailing out; can't continue%n");
                return;
            }

            datePrintf("changing file3 owner to %s%n", pOther.getName());
            file3.setOwner(p, pPassword.toCharArray(), null, pOther);

            datePrintf("file3 attributes now:%n%s%n%n",
                dumpOwnerAndGroup(pcache, file3));

            try {
                datePrintf(
                    "attempting write while someone else holds the" +
                    " modification lock%n");
                ExtentBuffer eb = new ExtentBuffer(0,
                    ByteBuffer.wrap("disallowed content".getBytes()));
                file3.write(eb, pOther, pOtherPassword.toCharArray(), null);
            } catch (FileException.Locked e) {
                datePrintf("PASS: atttempt to write under the modification" +
                " lock threw expected FileException.Locked%n");
            } catch (FileException e) {
                datePrintf("FAIL: atttempt to write under the modification" +
                " lock threw unexpected %s%n",
                e.getClass().getName());
            }

            //
            // Release the lock.
            //
            file3.releaseModificationLock(p, pPassword.toCharArray(),
                lockToken);

            //
            // Use the new owner's credentials to delete the file.
            //
            file3.purgeForever(pOther, pOtherPassword.toCharArray());

            datePrintf(
                "================ FileImpl test complete ================%n");
        } catch (RuntimeException e) {
            //
            // Make findbugs happy by showing that we explicitly intend to
            // capture runtime exceptions here, as well as checked exceptions.
            //
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //
    // Utility functions to support putting FileImpls into a Map, and finding
    // them in it.
    //

    //
    // XXX: The proper key for a map containing FileImpls is not a FileImpl,
    //      but rather the FileIdentifier of the file that the FileImpl
    //      mediates access to.  Once code that uses FileImpls as keys is
    //      modified to use FileIdentifiers instead, these overrides should be
    //      removed to allow the Object implementation to show through.
    //

    /**
     * <p>
     * Return {@code true} if this {@code FileImpl} object is equal to {@code
     * other}.
     *
     * </p><p>
     *
     * Two {@code FileImpl}s are deemed to be equal if they access the same
     * Celeste node (as defined by accessing it through the same proxy), and
     * identify the same {@code AObject} (as determined by its object id).
     * These checks are accomplished by comparing the {@code FileIdentifier}
     * objects associated with the two {@code FileImpl}s.
     *
     * </p><p>
     *
     * The comparison does <em>not</em> include anything related to the
     * current state of the file as viewed through the {@code FileImpl}.
     *
     * </p>
     *
     * @param other an object to be compared to this {@code FileImpl} for
     *              equality
     *
     * @return  {@code true} if this {@code FileImpl} object is equal to {@code
     *          other} and {@code false} otherwise
     */
    @Override
    public boolean equals(Object other) {
        if (other == null)
            return false;
        if (!(other instanceof FileImpl))
            return false;
        FileImpl f = (FileImpl) other;
        return this.getFileIdentifier().equals(f.getFileIdentifier());
    }

    @Override
    public int hashCode() {
        return this.getFileIdentifier().hashCode();
    }
}
