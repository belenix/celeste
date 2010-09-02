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
package sunlabs.celeste.node.object;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.Callable;
import java.util.jar.Attributes;
import java.util.zip.ZipException;

import sunlabs.celeste.client.operation.ExtensibleOperation;
import sunlabs.titan.api.TitanGuid;
import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.object.BeehiveObjectHandler;
import sunlabs.titan.util.DOLRStatus;

/**
 * {@link BeehiveObjectHandler}s that implement {@link ExtensibleObject.Handler} interface are able to execute
 * "extensions" -- client supplied Java code which is executed by an object handler.
 *
 * @author Glenn Scott - Oracle Sun Labs
 */
public class ExtensibleObject {
    public interface Handler <T extends ExtensibleObject.Handler.Object> extends BeehiveObjectHandler {
        /**
         *
         */
        public interface Object extends BeehiveObjectHandler.ObjectAPI {

        }

        /**
         * Bottom-half of an extension invocation.
         * <p>
         * BeehiveObjectHandler classes implementing this interface must implement this
         * method to receive an extension message invoking the extension mechanism in the handler.
         * </p>
         * @param message
         * @return The reply {@link sunlabs.titan.node.BeehiveMessage BeehiveMessage} containing the entire result of the operation.
         * @see ExtensibleObject#extensibleOperation(BeehiveObjectHandler, BeehiveMessage)
         */
        public TitanMessage extensibleOperation(TitanMessage message);

        /**
         * Top-half {@link BeehiveObjectHandler} method to invoke to start an extension.
         *
         * @param <C> The Java Class, specified by the parameter {@code resultClass}, of the returned result.
         * @param resultClass the {@link Class} of the returned value.
         * @param objectId the {@link TitanGuid} of the target object.
         * @param operation the {@link ExtensibleObject.Operation} to perform.
         *
         * @throws ClassCastException
         * @throws ClassNotFoundException
         * @throws TitanMessage 
         * @see ExtensibleObject#extension(Handler, Class, TitanGuid, ExtensibleObject.Operation.Request)
         */
        public <C> C extension(Class<? extends C> resultClass, TitanGuid objectId, ExtensibleObject.Operation.Request operation) throws ClassCastException, ClassNotFoundException, TitanMessage.RemoteException;
    }

    /**
     * Classes implementing this interface are invokable via the extension mechanism and produce a result of the specified generic type.
     * <p>
     * Classes implementing this interface must implement a constructor with the signature:
     * <code>public <i>className</i>(ExtensibleOperation operation, TitanGuid objectId, ExtensibleObject.Handler<? extends ExtensibleObject.Handler.Object> handler)</code>
     * </p>
     */
    public interface Extension<R> extends Serializable, Callable<R> {

    }

    /**
     * A {@link ClassLoader} that loads classes from a Jar file named by a {@link URL}.
     * The class loader of an instance of this class is set as the parent.
     *
     * @See java.net.URLClassLoader
     */
    public static class JarClassLoader extends URLClassLoader {
        private JarURLConnection jarConnection;

        /**
         * Construct a new {@code JarClassLoader} which will load classes from the given array of {@link URL} instances.
         * The resulting instance will have the class loader of this class set as the parent {@link ClassLoader}.
         * <p>
         * Note that the {@link #getMainClassName()} method returns the class name named by the Jar file attribute
         * {@code "Main-Class} from the Jar file specified in {@code urls[0]}.
         * </p>
         *
         * @param urls
         * @See URLClassLoader
         */
        public JarClassLoader(URL[] urls) {
            super(urls, JarClassLoader.class.getClassLoader());
        }

        /**
         * Return the String value of the {@code Main-Class} attribute of the Jar file
         * specified in {@code url[0]} of the constructor.
         * @throws IOException
         */
        public String getMainClassName() throws IOException {
            URL primaryURL = this.getURLs()[0];
            try {
                URL u = new URL("jar", "", new URL(primaryURL.toString() + "!/").toURI().toURL().toString());

                this.jarConnection = (JarURLConnection) u.openConnection();
                this.jarConnection.setRequestProperty("Connection", "close");
                Attributes attr = this.jarConnection.getMainAttributes();
                return attr != null ? attr.getValue(Attributes.Name.MAIN_CLASS) : null;
            } catch (ZipException e) {
                System.err.printf("Offending URL: '%s'%n", primaryURL);
                throw e;
            } catch (URISyntaxException e) {
                e.printStackTrace();
                throw new IOException(e);
            }
        }

        /**
         * Call the class constructor for the {@code className} matching the {@link Class}es of the arguments in {@code args}.
         * <p>
         * The class constructor MUST have the formal parameter types starting with {@link JarClassLoader} followed buy the classes of the supplied arguments {@code args}.
         * </p>
         *
         * @param className
         * @param args
         * @returns the instantiated class
         * @throws ClassNotFoundException
         * @throws SecurityException
         * @throws NoSuchMethodException
         * @throws IllegalArgumentException
         * @throws InstantiationException
         * @throws IllegalAccessException
         * @throws InvocationTargetException
         */
        @SuppressWarnings("unchecked")
		public Callable<Serializable> construct(String className, Object...args)
        throws ClassNotFoundException, SecurityException, NoSuchMethodException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
            Class<Callable<Serializable>> c = (Class<Callable<Serializable>>) this.loadClass(className, true);

            // Generate the list of Classes that we need to match a constructor.
            // This could be made much smarter and look for super-classes and interfaces to match.
            Class<?>[] classes = new Class<?>[args.length+1];
            Object[] params = new Object[args.length+1];
            params[0] = this;
            classes[0] = JarClassLoader.class;
            for (int i = 1; i < classes.length; i++) {
                classes[i] = args[i-1].getClass();
                params[i] = args[i-1];
            }

            Callable<Serializable> result = c.getConstructor(classes).newInstance(params);
            return result;
        }

        public void disconnect() throws IOException {
        }
    }


    public static class Operation {
        /**
         * A {@code Request} is composed and conveyed to a {@link BeehiveObjectHandler} that
         * implements the {@link ExtensibleObject.Handler} interface.
         * 
         * Each {@code BeehiveObjectHandler} implementing the {@code ExtensibleObject.Handler}
         * interface must implement the method {@link ExtensibleObject.Handler#extension(Class, TitanGuid, Request)}
         * which takes a {@code Request} as a parameter.
         */
        public static class Request implements Serializable {
            private static final long serialVersionUID = 1L;

            private String classToUse;
            private ExtensibleOperation operation;
            private Serializable parameter;
            
            // The ClassLoader is transient because it is not valid in another JVM and thus is not
            // transported across the network to other nodes.  Recipients of this class must create
            // a ClassLoader for use on the local JVM. This is signified on the receiver by classLoader
            // having a null value.
            transient private JarClassLoader classLoader;

            /**
             * Create a new {@code Request}.
             * @param classToUse The Java class to instantiate and invoke on the receiving {@link ExtensibleObject.Handler}.
             * @param classLoader A {@link ClassLoader} to be used by the receiving {@link ExtensibleObject.Handler} to load necessary classes.
             * @param operation The {@link ExtensibleOperation} containing parameters for {@code classToUse}.
             */
            public Request(Class<?> classToUse, JarClassLoader classLoader, ExtensibleOperation operation) {
                // classToUse must be converted to a String consisting of the name, not a reference to the actual
                // class because it cannot be resolved by nodes along the transmission path because the ClassLoader
                // to load it is not available to them.
                this.classToUse = classToUse.getName();
                this.classLoader = classLoader;
                this.operation = operation;
                this.parameter = null;
            }
            
            /**
             * Create a new {@code Request}.
             * @param classToUse The Java class to instantiate and invoke on the receiving {@link ExtensibleObject.Handler}.
             * @param classLoader A {@link ClassLoader} to be used by the receiving {@link ExtensibleObject.Handler} to load necessary classes.
             * @param operation The {@link ExtensibleOperation} containing parameters for {@code classToUse}.
             * @param parameter A {@link Serializable} object that contains arbitrary data to be used by the received of this request.
             */
            public Request(Class<?> classToUse, JarClassLoader classLoader, ExtensibleOperation operation, Serializable parameter) {
                // classToUse must be converted to a String consisting of the name, not a reference to the actual
                // class because it cannot be resolved by nodes along the transmission path because the ClassLoader
                // to load it is not available to them.
                this.classToUse = classToUse.getName();
                this.classLoader = classLoader;
                this.operation = operation;
                this.parameter = parameter;
            }

            public String getClassToUse() {
                return this.classToUse;
            }

            /**
             * Get the {@link JarClassLoader} that will be used to resolve classes used by the extension specified by this {@code Request}.
             *
             */
            public JarClassLoader getClassLoader() {
                // If this Request came from off-node, then it will be serialized and the transient field "classLoader" will be null.
                // Otherwise, the message is from on-node then it was not serialized and therefore the "classLoader" field will have a matching classLoader.
                // The problem is that if we generate a new class loader and then the reply payload is a class loaded from the new class loader and the
                // corresponding BeehiveMessage.get() for the reply will be using the other ClassLoader and will throw ClassNotFoundException because it
                // is technically not the same class (different class loader).
                if (this.classLoader == null) {
                    System.out.printf("%s creating missing class loader for %s%n", Thread.currentThread().getName(), this.operation.toString());
                    this.classLoader = new JarClassLoader(operation.getJarFileURLs());
                }
                return this.classLoader;
            }
            
            /**
             * Get the parameter object from this {@code Request}.
             */
            public Serializable getParameter() {
                return this.parameter;
            }
        }

        public static class Response implements Serializable {
            private static final long serialVersionUID = 1L;

            public Response() {

            }
        }
    }

    /**
     * Helper method for the bottom-half of an {@link ExtensibleObject.Handler} invoking
     * an extension on an {@link ExtensibleObject.Handler.Object}.
     *
     */
    public static TitanMessage extensibleOperation(BeehiveObjectHandler handler, TitanMessage message) {
        try {
            ExtensibleObject.Operation.Request request = message.getPayload(ExtensibleObject.Operation.Request.class, handler.getNode());
            ExtensibleOperation operation = request.operation;
            TitanGuid objectId = message.getObjectId();

            JarClassLoader classLoader =  request.getClassLoader();
            Callable<Serializable> extension = classLoader.construct(request.getClassToUse(), operation, objectId, handler);

            return message.composeReply(handler.getNode().getNodeAddress(), extension.call());
        } catch (Exception e) {
            e.printStackTrace();
            return message.composeReply(handler.getNode().getNodeAddress(), DOLRStatus.THROWABLE, e);
        }
    }

    /**
     * Helper method for the top-half of an {@link ExtensibleObject.Handler} to invoke an extension on an {@link ExtensibleObject.Handler.Object}.
     * <p>
     * Transmit the given {@link ExtensibleObject.Operation.Request} to the destination {@link BeehiveObject} identified by the {@link TitanGuid}.
     * </p>
     * @param <C>
     * @param handler The originating object handler invoking this extension.
     * @param resultClass The Java class of the result.
     * @param objectId The {@link TitanGuid} of the destination {@link BeehiveObject} 
     * @param request The request to perform.
     * @return
     * @throws ClassCastException
     * @throws ClassNotFoundException
     * @throws TitanMessage 
     */
    public static <C> C extension(ExtensibleObject.Handler<? extends ExtensibleObject.Handler.Object> handler,
            Class<? extends C> resultClass,
            TitanGuid objectId,
            ExtensibleObject.Operation.Request request)
    throws ClassCastException, ClassNotFoundException, TitanMessage.RemoteException {
        TitanMessage reply = handler.getNode().sendToObject(objectId, handler.getName(), "extensibleOperation", request);
        if (reply.getStatus().isSuccessful()) {
            return reply.getPayload(resultClass, handler.getNode());
        }
        // XXX throw an exception here.
        return null;
    }
}
