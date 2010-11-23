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
 * Please contact Oracle Corporation, 500 Oracle Parkway, Redwood Shores, CA 94065
 * or visit www.oracle.com if you need additional information or
 * have any questions.
 */
package sunlabs.titan.node.services.object;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.management.JMException;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.api.Credential;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.api.TitanObject.Metadata;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.BeehiveObjectPool.DisallowedDuplicateException;
import sunlabs.titan.node.TitanObjectStoreImpl;
import sunlabs.titan.node.Publishers.PublishRecord;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.object.RetrievableObject;
import sunlabs.titan.node.object.StorableObject;
import sunlabs.titan.node.services.AbstractTitanService;
import sunlabs.titan.node.services.api.Publish;
import sunlabs.titan.node.services.objectstore.PublishDaemon;

public final class CredentialObjectHandler extends AbstractObjectHandler implements CredentialObject {
    private final static long serialVersionUID = 1L;

    private final static String name = AbstractTitanService.makeName(CredentialObjectHandler.class, CredentialObjectHandler.serialVersionUID);

    //
    // At this point a typical object handler incorporates a nested static
    // class definition of the kind of object it manages.  But credentials
    // have a pre-existent definition (Profile_) within the system, so we
    // leave that definition intact.
    //

    public CredentialObjectHandler(TitanNode node) throws JMException {
        super(node, CredentialObjectHandler.name, "Credential Object Handler");
    }

    //
    // Methods from TitanObjectHandler
    //
    //
    // This method is executed by the credential's root node.
    //

    public PublishDaemon.PublishObject.PublishUnpublishResponseImpl publishObject(TitanMessage message, Publish.PublishUnpublishRequest request) throws DisallowedDuplicateException, ClassCastException, ClassNotFoundException {
        //            PublishDaemon.PublishObject.PublishUnpublishRequestImpl publishRequest = msg.getPayload(PublishDaemon.PublishObject.PublishUnpublishRequestImpl.class, this.node);
        if (this.log.isLoggable(Level.FINE)) {
            this.log.fine("node %s publishing %s", request.getPublisherAddress().getObjectId(), request.getObjects());
        }

        // Don't add a new object if it differs from the objects we already have with this object-id.
        for (Map.Entry<TitanGuid, Metadata> object : request.getObjects().entrySet()) {
            Set<PublishRecord> alreadyPublishedObjects = this.node.getObjectPublishers().getPublishers(object.getKey());
            if (alreadyPublishedObjects.size() > 0) {
                for (PublishRecord record : alreadyPublishedObjects) {
                    String dataHash = record.getMetadataProperty(TitanObjectStoreImpl.METADATA_DATAHASH, "error");
                    if (dataHash.compareTo(object.getValue().getProperty(TitanObjectStoreImpl.METADATA_DATAHASH)) != 0) {
                        throw new BeehiveObjectPool.DisallowedDuplicateException(String.format("Credential %s already exists", object.getKey()));
                    }
                    break;
                }
            }
        }

        AbstractObjectHandler.publishObjectBackup(this, request);
        // Dup the getObjectsToPublish set as it's backed by a Map and is not serializable.
        return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress(), new HashSet<TitanGuid>(request.getObjects().keySet()));
    }
    
    public Publish.PublishUnpublishResponse unpublishObject(TitanMessage message, Publish.PublishUnpublishRequest request) {
        return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress());
    }

    //
    // Methods from CredentialObject
    //

    //
    // Methods from CredentialObject's RetrievableObjectType super-interface
    //

    //
    // N.B.  Here we assume that Credential is equivalent to (the
    // hypothetical) CredentialObject.CredentialAPI interface.
    //
    public Credential retrieve(TitanGuid objectId) throws TitanObjectStoreImpl.DeletedObjectException, TitanObjectStoreImpl.NotFoundException, ClassCastException, ClassNotFoundException {
        Credential credential = RetrievableObject.retrieve(this, Credential .class, objectId);
        if (credential == null)
            throw new TitanObjectStoreImpl.NotFoundException("Credential %s not found.", objectId);
        return credential;
    }

    public TitanObject retrieveLocalObject(TitanMessage message, TitanGuid objectId) throws TitanObjectStoreImpl.NotFoundException {
        return node.getObjectStore().get(TitanObject.class, message.subjectId);
    }

    //
    // Methods from CredentialObject's StorableObjectType super-interface
    //

    public Publish.PublishUnpublishResponse storeLocalObject(TitanMessage message, Credential credential) throws ClassNotFoundException,
    BeehiveObjectPool.Exception, TitanObjectStoreImpl.InvalidObjectIdException, TitanObjectStoreImpl.Exception {
        if (this.log.isLoggable(Level.FINER)) {
            this.log.finest("%s", message.traceReport());
        }
        Publish.PublishUnpublishResponse response = StorableObject.storeLocalObject(this, credential, message);
        return response;
    }

    //
    // Methods from CredentialObject's StorableObjectType super-interface
    //

    public Credential storeObject(Credential credential) throws IOException, TitanObjectStoreImpl.NoSpaceException,TitanObjectStoreImpl.DeleteTokenException,
        TitanObjectStoreImpl.UnacceptableObjectException, BeehiveObjectPool.Exception, ClassCastException, ClassNotFoundException {
        //
        // Store the credential under its specified object id (rather than under
        // the id that its contents would dictate).
        //
        // ???: It seems odd that the decision on how an object's
        //      TitanGuid is determined is deferred until the object is
        //      stored.  One would naively expect it to be an inherent
        //      property of the object that's established immediately when
        //      it's constructed.
        //
        // That would only work for objects that are immutable after they've
        // been constructed which isn't a constraint placed upon
        // TitanObjects.  Implementors are free to set the object-id in
        // either place.  Setting an object's object-id is only advisory
        // anyway.  The actual object-id is computed by the storing node and
        // returned in the reply.
        //
        TitanObjectStoreImpl.CreateSignatureVerifiedObject(credential.getObjectId(), credential);
        StorableObject.storeObject(this, credential);
        return credential;
    }
    
    //
    // Methods from CredentialObject's InspectableObjectType super-interface
    //
    // Also from the XHTMLInspectable super-interface of
    // BeehiveObjectHandler's Service super-interface
    //

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        return new XHTML.Div("nothing here");
    }
}
