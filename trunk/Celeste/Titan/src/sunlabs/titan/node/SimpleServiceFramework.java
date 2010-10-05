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
package sunlabs.titan.node;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XHTML.EFlow;
import sunlabs.asdf.web.http.HTTP.Message;
import sunlabs.titan.api.TitanNode;
import sunlabs.titan.api.TitanService;
import sunlabs.titan.api.TitanServiceFramework;
import sunlabs.titan.node.services.WebDAVDaemon;
import sunlabs.titan.node.services.xml.TitanXML;
import sunlabs.titan.node.services.xml.TitanXML.XMLServices;

/**
 *
 */
public class SimpleServiceFramework implements TitanServiceFramework {
    private final Map<String, TitanService> services;
    private final TitanNode node;
    /**
     * The running state of this node.
     * If {@code true} the node is running then any subsequent creation (see {@link TitanServiceFramework#get(String)})
     * of a {@link TitanService} will be started (see {@link TitanService#start()}).
     */
    private Boolean running;
    
    public SimpleServiceFramework(TitanNode node) {
        this.services = new HashMap<String, TitanService>();
        this.node = node;
        this.running = Boolean.FALSE;
    }

    /**
     * @see sunlabs.titan.api.XHTMLInspectable#toXHTML(java.net.URI, java.util.Map)
     */
    public EFlow toXHTML(URI uri, Map<String, Message> props) {
        try {
            if (uri.getPath().startsWith("/service/")) {
                String name = uri.getPath().substring("/service/".length());
                TitanService service = this.node.getService(name);
                if (service != null) {
                    return service.toXHTML(uri, props);
                }
                return null;
            }
            // Sort them by name...
            Set<String> apps = new TreeSet<String>(this.keySet());

            XHTML.Table.Head thead = new XHTML.Table.Head(
                    new XHTML.Table.Row(
                            new XHTML.Table.Heading("Service Name"),
                            new XHTML.Table.Heading("Description"),
                            new XHTML.Table.Heading("Status")));

            XHTML.Table.Body tbody = new XHTML.Table.Body();

            for (String name: apps) {
                TitanService app = this.get(name);
                XHTML.Anchor link = WebDAVDaemon.inspectServiceXHTML(app.getName());

                tbody.add(new XHTML.Table.Row(new XHTML.Table.Data(link),
                        new XHTML.Table.Data(app.getDescription()),
                        new XHTML.Table.Data(app.getStatus())));
            }

            XHTML.Table table = new XHTML.Table(new XHTML.Table.Caption("Node Services"), thead, tbody).setId("applications");

            XHTML.Div result = new XHTML.Div(table).setClass("section");
            return result;
        } catch (NullPointerException e) {
            return new XHTML.Div(e.toString());
        } catch (ClassNotFoundException e) {
            return new XHTML.Div(e.toString());
        } catch (IllegalArgumentException e) {
            return new XHTML.Div(e.toString());
        } catch (NoSuchMethodException e) {
            return new XHTML.Div(e.toString());
        } catch (InstantiationException e) {
            return new XHTML.Div(e.toString());
        } catch (IllegalAccessException e) {
            return new XHTML.Div(e.toString());
        } catch (InvocationTargetException e) {
            return new XHTML.Div(e.toString());
        } finally {

        }
    }

    /**
     * @see sunlabs.titan.api.TitanServiceFramework#get(java.lang.String)
     */
    public TitanService get(String serviceName) throws ClassNotFoundException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException,
        InvocationTargetException {
        //XXX Fix up the versioned name:
        
        int semiColon = serviceName.indexOf(';');
        if (semiColon != -1) {
            serviceName = serviceName.substring(0, semiColon);
        }
        
        synchronized (this.services) {
            TitanService service = this.services.get(serviceName);
            if (service != null) {
                return service;
            }
            // Invoke a class loader to load the service.
            Class<?> genericClass = Class.forName(serviceName, false, this.getClass().getClassLoader());

            if (!TitanService.class.isAssignableFrom(genericClass))
                throw new ClassCastException(String.format("%s is not an implmementation of the TitanService interface.", serviceName));
            @SuppressWarnings(value="unchecked")
            Class <? extends TitanService> serviceClass = (Class<TitanService>) genericClass;

            Constructor<? extends TitanService> ctor = serviceClass.getDeclaredConstructor(TitanNode.class);
            service = (TitanService) ctor.newInstance(this.node);
            if (this.running) {
                service.start();
            }

            this.services.put(serviceName, service);
            return service;
        }
    }

    /**
     * @see sunlabs.titan.api.TitanServiceFramework#dispatch(sunlabs.titan.node.TitanMessage)
     */
    public TitanMessage dispatch(TitanMessage request) throws IllegalArgumentException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        String subjectClass = request.getSubjectClass();
        String subjectMethod = request.getSubjectClassMethod();

        TitanService a = this.get(subjectClass);

        if (a != null) {
            return a.invokeMethod(subjectMethod, request);
        }

        this.node.getLogger().severe("Unimplemented TitanService class: message type=%s class=%s method=%s (found=%s)", request.getType(), subjectClass, subjectMethod, a);

        return request.composeReply(this.node.getNodeAddress(),
                new ClassNotFoundException(String.format("Unimplemented TitanService class: message type=%s class=%s method=%s (found=%s)", request.getType(), subjectClass, subjectMethod, a)));
    }

    /**
     * @see sunlabs.titan.api.TitanServiceFramework#startAll()
     */
    public void startAll() throws IOException {
        synchronized (this.services) {
            for (Map.Entry<String,TitanService> entry : this.services.entrySet()) {
                TitanService service = entry.getValue();
                service.start();
            }
            this.running = Boolean.TRUE;
        }
    }

    /**
     * @see sunlabs.titan.api.TitanServiceFramework#keySet()
     */
    public Set<String> keySet() {
        synchronized (this.services) {
            return this.services.keySet();
        }
    }

    public XMLServices toXML() {
        TitanXML xml = new TitanXML();

        XMLServices services = xml.newXMLServices();
        synchronized (this.services) {
            for (Map.Entry<String,TitanService> entry : this.services.entrySet()) {
                services.add(xml.newXMLService(entry.getValue()));
            }
        }

        return services;
    }
}
