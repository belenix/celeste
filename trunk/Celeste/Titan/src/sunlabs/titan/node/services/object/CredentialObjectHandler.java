/*
 * Copyright 2009-2010 Oracle. All Rights Reserved.
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
 * Please contact Oracle, 16 Network Circle, Menlo
 * Park, CA 94025 or visit www.oracle.com if you need additional
 * information or have any questions.
 */

package sunlabs.titan.node.services.object;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.api.BeehiveObject.Metadata;
import sunlabs.titan.api.Credential;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.node.BeehiveMessage;
import sunlabs.titan.node.BeehiveMessage.RemoteException;
import sunlabs.titan.node.BeehiveNode;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.BeehiveObjectStore;
import sunlabs.titan.node.Publishers.PublishRecord;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.object.RetrievableObject;
import sunlabs.titan.node.object.StorableObject;
import sunlabs.titan.node.services.BeehiveService;
import sunlabs.titan.node.services.PublishDaemon;

public final class CredentialObjectHandler extends AbstractObjectHandler implements CredentialObject {
    private final static long serialVersionUID = 1L;

    private final static String name = BeehiveService.makeName(CredentialObjectHandler.class, CredentialObjectHandler.serialVersionUID);

    //
    // At this point a typical object handler incorporates a nested static
    // class definition of the kind of object it manages.  But credentials
    // have a pre-existent definition (Profile_) within the system, so we
    // leave that definition intact.
    //

    public CredentialObjectHandler(BeehiveNode node) throws
            MalformedObjectNameException,
            NotCompliantMBeanException,
            InstanceAlreadyExistsException,
            MBeanRegistrationException {
        super(node, CredentialObjectHandler.name, "Credential Object Handler");
    }

    //
    // Methods from BeehiveObjectHandler
    //
    //
    // This method is executed by the credential's root node.
    //
    public BeehiveMessage publishObject(BeehiveMessage msg) {
        try {
            PublishDaemon.PublishObject.Request publishRequest = msg.getPayload(PublishDaemon.PublishObject.Request.class, this.node);
            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("node %s publishing %s", publishRequest.getPublisherAddress().getObjectId(), publishRequest.getObjectsToPublish());
            }

            // Don't add a new object if it differs from the objects we already have with this object-id.
            for (Map.Entry<TitanGuid, Metadata> object : publishRequest.getObjectsToPublish().entrySet()) {
                Set<PublishRecord> alreadyPublishedObjects = this.node.getObjectPublishers().getPublishers(object.getKey());
                if (alreadyPublishedObjects.size() > 0) {
                    for (PublishRecord record : alreadyPublishedObjects) {
                        String dataHash = record.getMetadataProperty(BeehiveObjectStore.METADATA_DATAHASH, "error");
                        if (dataHash.compareTo(object.getValue().getProperty(BeehiveObjectStore.METADATA_DATAHASH)) != 0) {
                            return msg.composeReply(this.node.getNodeAddress(), new BeehiveObjectPool.DisallowedDuplicateException("Credential already exists"));
                        }
                        break;
                    }
                }
            }

            AbstractObjectHandler.publishObjectBackup(this, publishRequest);
            // Dup the getObjectsToPublish set as it's backed by a Map and is not serializable.
            return msg.composeReply(this.node.getNodeAddress(), new PublishDaemon.PublishObject.Response(new HashSet<TitanGuid>(publishRequest.getObjectsToPublish().keySet())));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return msg.composeReply(this.node.getNodeAddress(), e);
        } catch (RemoteException e) {
            e.printStackTrace();
            return msg.composeReply(this.node.getNodeAddress(), e);
        }
    }

    public BeehiveMessage unpublishObject(BeehiveMessage msg) {
        return msg.composeReply(this.node.getNodeAddress());
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
    public Credential retrieve(TitanGuid objectId) throws
            BeehiveObjectStore.DeletedObjectException,
            BeehiveObjectStore.NotFoundException {

        Credential credential = RetrievableObject.retrieve(this, Credential .class, objectId);
        if (credential == null)
            throw new BeehiveObjectStore.NotFoundException("Credential %s not found.", objectId);
        return credential;
    }

    public BeehiveMessage retrieveLocalObject(BeehiveMessage message) {
        return RetrievableObject.retrieveLocalObject(this, message);
    }

    //
    // Methods from CredentialObject's StorableObjectType super-interface
    //

    public BeehiveMessage storeLocalObject(BeehiveMessage message) {
        if (this.log.isLoggable(Level.FINER)) {
            this.log.finest("%s", message.traceReport());
        }
        try {
            Credential credential = message.getPayload(Credential.class, this.node);
            BeehiveMessage reply = StorableObject.storeLocalObject(this, credential, message);
            return reply;
        } catch (ClassNotFoundException e) {
            return message.composeReply(node.getNodeAddress(), e);
        } catch (ClassCastException e) {
            return message.composeReply(node.getNodeAddress(), e);
        } catch (RemoteException e) {
            return message.composeReply(node.getNodeAddress(), e);
        }
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

    //
    // Methods from CredentialObject's StorableObjectType super-interface
    //

    public Credential storeObject(Credential credential) throws
            IOException,
            BeehiveObjectStore.NoSpaceException,BeehiveObjectStore.DeleteTokenException, BeehiveObjectStore.UnacceptableObjectException, BeehiveObjectPool.Exception {
        //
        // Store the credential under its stated object id (rather than under
        // the id that its contents would dictate).
        //
        // XXX: It seems odd that the decision on how an object's
        //      BeehiveObjectId is determined is deferred until the object is
        //      stored.  One would naively expect it to be an inherent
        //      property of the object that's established immediately when
        //      it's constructed.
        //
        // That would only work for objects that are immutable after they've
        // been constructed which isn't a constraint placed upon
        // BeehiveObjects.  Implementors are free to set the object-id in
        // either place.  Setting an object's object-id is only advisory
        // anyway.  The actual object-id is computed by the storing node and
        // returned in the reply.
        //
        BeehiveObjectStore.CreateSignatureVerifiedObject(credential.getObjectId(), credential);
        StorableObject.storeObject(this, credential);
        return credential;
    }
}
