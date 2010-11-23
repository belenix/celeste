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
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.management.JMException;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanObjectStore;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanObject;
import sunlabs.titan.node.AbstractTitanObject;
import sunlabs.titan.node.BeehiveObjectPool;
import sunlabs.titan.node.TitanObjectStoreImpl;
import sunlabs.titan.node.TitanObjectStoreImpl.InvalidObjectException;
import sunlabs.titan.node.TitanObjectStoreImpl.NoSpaceException;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.object.AbstractObjectHandler;
import sunlabs.titan.node.object.RetrievableObject;
import sunlabs.titan.node.object.StorableObject;
import sunlabs.titan.node.services.AbstractTitanService;
import sunlabs.titan.node.services.api.AppClass;
import sunlabs.titan.node.services.api.Publish;
import sunlabs.titan.node.services.objectstore.PublishDaemon;

/**
 * This service currently does nothing except mark
 * objects stored on behalf of ApplicationFramework as
 * belonging to this service.
 *
 * It is a place holder:  I'll be moving some of the ApplicationFramework
 * functionality here eventually.
 *
 * This application also really belongs in the same package as
 * ApplicationFramework, as it should only be used by it.
 */
public class AppClassObjectType extends AbstractObjectHandler implements AppClass, AppClassObjectTypeMBean {
    private final static long serialVersionUID = 2L;
    public final static String name = AbstractTitanService.makeName(AppClassObjectType.class, AppClassObjectType.serialVersionUID);

    public final static class AppClassObject extends AbstractTitanObject implements AppClass.AppClassObject {
        private final static long serialVersionUID = 1L;

        //
        // Each instance of this class captures the information required to
        // reconstruct the classes that are part of a given application.
        //
        // As noted elsewhere, only the class for the application itself and its
        // (recursively) nested classes are captured.  Ideally, we should have a
        // way to find all the classes an application needs that aren't part of
        // the "Celeste core" or the Java platform and capture those.
        //

        public static class AppClassInfoList implements AppClass.AppClassObject.InfoList,
                Serializable, Iterable<AppClass.AppClassObject.InfoList.AppClassLoadingInfo> {
            private static final long serialVersionUID = 1L;

            private final List<AppClass.AppClassObject.InfoList.AppClassLoadingInfo> loadList =
                new ArrayList<AppClass.AppClassObject.InfoList.AppClassLoadingInfo>();

            //
            // A tuple class that records what's needed to feed a class loader to
            // have it load a given class.
            //
            public static class AppClassLoadingInfo implements AppClass.AppClassObject.InfoList.AppClassLoadingInfo, Serializable {
                private static final long serialVersionUID = 1L;

                public final String name;
                public final byte[] classBytes;

                //
                // This method assumes that the caller will not overwrite the
                // classBytes argument.
                //
                public AppClassLoadingInfo(String name, byte[] classBytes) {
                    this.name = name;
                    this.classBytes = classBytes;
                }

                public String getName() {
                    return this.name;
                }

                public byte[] getClassBytes() {
                    return this.classBytes;
                }

                @Override
                public String toString() {
                    return String.format("%s: <%d byte codes>",
                        this.name, this.classBytes.length);
                }
            }

            public AppClassInfoList() {
                super();
            }

            //
            // The iterator feeds back AppClassLoadingInfo entries in the order
            // that they were submitted via the add() method below.
            //
            public Iterator<AppClass.AppClassObject.InfoList.AppClassLoadingInfo> iterator() {
                return this.loadList.iterator();
            }

            public void add(String name, byte[] classBytes) {
                this.loadList.add(new AppClassLoadingInfo(name, classBytes));
            }

            @Override
            public String toString() {
                return this.loadList.toString();
            }
        }

        private AppClass.AppClassObject.InfoList infoList;

        public AppClassObject(TitanGuid objectId, AppClass.AppClassObject.InfoList infoList) {
            super(AppClassObjectType.class, TitanGuidImpl.ZERO, Long.MAX_VALUE);
            this.setProperty(TitanObjectStore.METADATA_REPLICATION_STORE, 1);
            this.infoList = infoList;
            try {
                TitanObjectStoreImpl.CreateSignatureVerifiedObject(objectId, this);
            } catch (TitanObjectStoreImpl.DeleteTokenException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        @Override
        public TitanGuid getDataId() {
            TitanGuid objectId = new TitanGuidImpl("".getBytes());
            for (AppClass.AppClassObject.InfoList.AppClassLoadingInfo i : this.infoList) {
                objectId = objectId.add(i.getClassBytes());
            }
            return objectId;
        }

        public AppClass.AppClassObject.InfoList getInfoList() {
            return this.infoList;
        }
    }

    public AppClassObjectType(TitanNode node) throws JMException {
        super(node, AppClassObjectType.name, "AppClass Application");
    }

    public AppClass.AppClassObject create(TitanGuid objectId, AppClass.AppClassObject.InfoList infoList) {
        AppClass.AppClassObject object = new AppClassObject(objectId, infoList);

        return object;
    }

    public PublishDaemon.PublishObject.PublishUnpublishResponseImpl publishObject(TitanMessage message, Publish.PublishUnpublishRequest request) {
        return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress());
    }

    public Publish.PublishUnpublishResponse unpublishObject(TitanMessage message, Publish.PublishUnpublishRequest request) {
        return new PublishDaemon.PublishObject.PublishUnpublishResponseImpl(this.node.getNodeAddress());
    }

    public TitanObject retrieveLocalObject(TitanMessage message, TitanGuid objectId) throws TitanObjectStoreImpl.NotFoundException {
        return this.node.getObjectStore().get(TitanObject.class, message.subjectId);
    }

    public AppClassObjectType.AppClassObject retrieve(TitanGuid objectId) throws ClassCastException, ClassNotFoundException,
        TitanObjectStoreImpl.NotFoundException, TitanObjectStoreImpl.DeletedObjectException {
        AppClassObjectType.AppClassObject object = RetrievableObject.retrieve(this, AppClassObjectType.AppClassObject.class, objectId);
        return object;
    }
    
    public  Publish.PublishUnpublishResponse storeLocalObject(TitanMessage message, AppClass.AppClassObject aObject) throws ClassCastException, ClassNotFoundException,
    TitanObjectStoreImpl.UnacceptableObjectException, TitanObjectStoreImpl.DeleteTokenException, TitanObjectStoreImpl.InvalidObjectIdException, NoSpaceException,
    InvalidObjectException, BeehiveObjectPool.Exception, TitanObjectStoreImpl.Exception {
        Publish.PublishUnpublishResponse reply = StorableObject.storeLocalObject(this, aObject, message);
        return reply;
    }

    public AppClass.AppClassObject storeObject(AppClass.AppClassObject aObject)
    throws IOException, TitanObjectStoreImpl.NoSpaceException, TitanObjectStoreImpl.DeleteTokenException, TitanObjectStoreImpl.UnacceptableObjectException, BeehiveObjectPool.Exception, ClassCastException, ClassNotFoundException {
        aObject = (AppClass.AppClassObject) TitanObjectStoreImpl.CreateSignatureVerifiedObject(aObject.getObjectId(), aObject);

        return (AppClass.AppClassObject) StorableObject.storeObject(this, aObject);
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {

        return new XHTML.Div("nothing here");
    }
}
