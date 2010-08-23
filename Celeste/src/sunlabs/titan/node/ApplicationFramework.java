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

package sunlabs.titan.node;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;

import java.net.URI;

import java.nio.ByteBuffer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import java.util.logging.Level;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.titan.BeehiveObjectId;
import sunlabs.titan.api.Service;
import sunlabs.titan.api.XHTMLInspectable;
import sunlabs.titan.node.BeehiveObjectStore.NotFoundException;
import sunlabs.titan.node.BeehiveObjectStore.ObjectExistenceException;
import sunlabs.titan.node.services.BeehiveService;
import sunlabs.titan.node.services.WebDAVDaemon;
import sunlabs.titan.node.services.api.AppClass;
import sunlabs.titan.node.services.object.AppClassObjectType;
import sunlabs.titan.node.services.xml.TitanXML;
import sunlabs.titan.node.services.xml.TitanXML.XMLService;
import sunlabs.titan.node.services.xml.TitanXML.XMLServices;
import sunlabs.titan.node.util.DOLRLogger;
import sunlabs.titan.util.DOLRStatus;

/**
 * The ApplicationFramework is the portion of a
 * {@link sunlabs.titan.node.BeehiveNode}
 * which directs messages to the correct
 * {@link BeehiveService}
 * installed on that node.
 * Each message in the system contains
 * the name of the application to which it should be delivered.  The
 * ApplicationFramework contains methods to get an application by name and
 * install it in the ApplicationFramework if it hasn't been already, and to
 * remove (uninstall) an application by name.
 * <p>
 * <h4> Dynamic Service Loading Background</h4>
 * Because Beehive is implemented in Java, we have the opportunity
 * to dynamically load BeehiveServices based on the application named in
 * a message.  This allows Nodes to be automatically updated when they
 * are long-running.  There are at least two interesting use-cases:
 * <ul>
 * <li>   allow services to be updated to new versions
 * <li>   allow Nodes to host brand new applications
 * </ul>
 * <p>
 * The first, allowing version updates of applications already running on
 * Nodes, is compelling.  When using static classes delivered through .jar
 * files (which are found on a VM's {@code CLASSPATH}), changes require
 * updating the jar file, installing it at all participating machines, and
 * restarting all nodes.  How do we find all the existing nodes in a P2P
 * system?  A better, more P2P-like approach, would be to modify the
 * application, install it at a single site in the system, restart a single
 * Node, and let the change propagate through the system automatically.
 * <p>
 * The second use-case is also compelling, but less so.  BeehiveNodes
 * can support any application, so it would be good if they could also
 * dynamically support brand-new (new to that Beehive) applications.
 * This actually motivated the original thinking about this problem:
 * if a new system is built on top of the object pool (say, Neuromancer, a research
 * project that needed an always-available, distributed lookup service), and
 * if the Neuromancer system had a specialized Application
 * but no specialized Node, and there were Celeste nodes
 * all over the world, why not use the Celeste nodes to service the
 * Neuromancer needs?  This line of thinking falls apart, though, when
 * we consider the converse:  why not have a Neuromancer system also
 * service Celeste Applications?  The problem is that Celeste Applications
 * assume a backing Celeste Node which wouldn't be satisified by the
 * Neuromancer system.
 * </p>
 *
 * <h4> Dynamic Application Loading Overview </h4>
 *
 * Java ClassLoaders are the mechanism used to dynamically load classes, but
 * they must know where to look for the .class file being loaded.  By default,
 * classes should be found on the {@code CLASSPATH} provided to the VM as it
 * is started.  We'd like to be able to find classes in use by a different VM
 * that is running a BeehiveNode.  How to find those classes?
 * <p>
 * One common mechanism is to declare a place where classes will be available,
 * typically a standard URL.  This location is known to all starting VMs and
 * declared to them.  An example is RMI's {@code java.rmi.server.codebase}
 * property.  We have an object pool, though, custom-made for reliably storing and
 * retrieving objects.  We simply store the .class for the application in the
 * Beehive object pool.
 * <p>
 * This is trickier than it sounds, though, as classes can refer to
 * other classes or to resources, and these classes or resources may not
 * have been loaded or used yet, so the bytecode might not be available in
 * the running JVM.   The RMI approach of using a well-known
 * location to host the classes typically requires that entire jar files
 * be stored at that location.  We, while running, will decide to store a
 * particular class in the object pool:  we do not know where that class came from.
 * In particular, we do not know what jar file it was packaged in, so we
 * have no way of  storing the entire jar file in the object pool.
 * <p>
 * Another alternative to making the jar file available would be to
 * analyze the .class file we are about to store, and force loading of
 * every Class and resource that it refers to,  making them available to
 * the VM for storing in the object pool.  This could be a time consuming task,
 * as class loading can be a heavy-weight operation.
 * <p>
 * Therefore, we decide that any application to be loaded dynamically through
 * this system <b>must</b> be completely self-contained:  it cannot rely on
 * helper classes and cannot rely on outside resources.  It can, however, make
 * use of inner classes (as any class can reflect on itself to identify those
 * inner classes).
 * <p>
 * Another concern is unexpected {@code ClassCastException}s, which can occur
 * when an object is assigned to an object of a different type.  Recall that
 * Java runtime types are determined by both the static Class plus the
 * ClassLoader that loaded the Class; thus, the same Class loaded by two
 * different ClassLoaders results in objects of two different types.  In our
 * system, this can occur easily if the ApplicationFramework is not the only
 * place an application is being loaded.  Thus, it's important to always let
 * the ApplicationFramework do the loading.  This is accomplished through
 * interfaces.  When designing an application, define interfaces to
 * encapsulate the publicly accessible methods of the application and have the
 * application extend {@code Application} and implement the interface(s).
 * Code should always reference the application through an interface, and the
 * interface needs to be loaded by a parent class loader (delegation-wise) to
 * the application loader.  The coding idiom is:
 * <pre>
 * import sunlabs.titan.node.interfaces.Census;
 * Census census = (Census)
 *   applications.get("sunlabs.titan.node.services.CensusDaemon");</pre>
 * <p>
 * Note that if this had been coded as:
 * </p>
 * <pre>
 * import sunlabs.titan.node.services.Census;
 * Census map = (Census)
 *   applications.get("sunlabs.titan.node.services.CensusDaemon");</pre>
 * <p>
 * the CensusDaemon class would have been loaded two times:  first
 * by the ClassLoader of the current method in order to resolve the type of
 * the {@code map} variable, and then by the ApplicationFramework in {@code
 * get}.
 *
 * </p><p>
 *
 * An easy way to ensure things are correct is to check your import statements.
 * Interfaces should be imported, not the actual applications.
 *
 * </p>
 * <h4> Dynamic Application Loading Mechanism </h4>
 * <p>
 *
 * When the ApplicationFramework is asked to find a version of an application,
 * it checks an internal data structure {@link #applications} to see if it has
 * already been loaded.  If it hasn't, a new class loader is created and the
 * application is loaded either from the local {@code CLASSPATH} or from the
 * object pool.  The application is then stored locally in the BeehiveNode (so it will
 * be available to other Nodes via the object pool) and started.  The application is
 * always stored locally and published if necessary.  Note that the
 * application is stored in the object pool, making it available to other
 * applications, <b>before</b> it is started.  When an application is
 * starting, it can begin sending messages to other nodes; if the new version
 * is not available in the object pool yet, those nodes might not be able to process
 * the messages because they cannot find the application version to which the
 * message is directed.
 *
 * </p><p>
 *
 * Each application is loaded with a separate ClassLoader so the application
 * and ClassLoader can be garbage collected when the application has been
 * replaced in the {@code applications} data structure.  Recall that Class
 * objects are not garbage collected until all Classes loaded by their loading
 * ClassLoader can be garbage collected.
 *
 * </p><p>
 *
 * The ClassLoader used for this loading, {@code ApplicationLoader}, which is
 * a private member of this class, is very simple.  Typically, a user defined
 * ClassLoader will override findClass to find the bytes comprising the Class
 * and then define the Class using those bytes.  This allows the usual
 * delegation behavior, where a ClassLoader's parent attempts to load the
 * Class before the loader itself tries.  Our ApplicationLoader does not
 * override FindClass because the files being stored do not use valid Java
 * binary names, and an exception will be immediately raised by ClassLoader.
 * Thus, once we know a class cannot be loaded locally, we obtain the bytes
 * outside the ClassLoader, than call a new method {@code
 * ApplicationLoader.defineDOLRClass} to actually define it.  Note that
 * changing the application names to be a valid binary name would allow us to
 * move some of the logic currently found in {@link #loadApplication(String)
 * loadApplication} into {@code ApplicationLoader}.
 *
 * </p><p>
 *
 * An earlier design of the system used a separate ClassLoader for each
 * package containing applications, so a single ClassLoader would, say, load
 * all the applications in {@code sunlabs.titan.node.services}.  This is
 * important if one wants to maintain package access correctly in the loaded
 * applications; otherwise, {@code ClassCastException}s can result.  However,
 * for the reasons stated above, this is not a helpful notion for our system
 * because our applications <b>must</b> be self-contained, without relying on
 * anything else in their package, because we only store single classes and
 * their nested classes in the object pool.  The code is much simpler if we don't
 * bother checking for packages, especially when an application is being
 * <i>replaced</i> in the system.  To avoid intra-package {@code
 * ClassCastException}s, the package level class loader would need to be
 * abandoned and all the other applications reloaded by the new loader, as a
 * class loader is forbidden by the Java VM Spec to load two versions of the
 * same Class.
 *
 * </p>
 * <h5>Performance Considerations</h5>
 * <p>
 *
 * When an object is stored in the object pool, it is published, so that code running
 * elsewhere (in particular, on other nodes) can discover it.  Publishing
 * numerous objects at once is potentially quite expensive, and the situation
 * is exacerbated during a cold start, when multiple nodes start at once
 * (which is a frequent occurence during development).  The resulting
 * publishing storms can be quite aggravating.  So it's desirable to avoid
 * unnecessary publishes.
 *
 * </p><p>
 *
 * The current implementation reduces its publishing activity in two ways.
 *
 * </p><ul><li>
 *
 * First, it batches the classes associated with a given application together
 * into a single object (as opposed to the previous implementation, which
 * stored each class as a separate object).
 *
 * </li><li>
 *
 * Second, it relies on the {@code ObjectStore.create} method to notice when a
 * given object has already been stored locally and to avoid republishing that
 * object unnecessarily (that is, outside of the normal periodic republishing
 * cycle).
 *
 * </li></ul><p>
 *
 * The hope (yet to be verified) is that these measures reduce the message
 * overhead induced by storing application classes in the object pool to an
 * acceptable level.
 *
 * </p>
 * <h5> Versions </h5>
 *
 * The first paragraph of this section says "is asked to find a version of an
 * application".  What does this mean?  How is this specified?
 *
 * </p><p>
 *
 * Application versions are numerically increasing {@code long} values.
 * Application versions are backward compatible:  version 2 must be able to
 * support version 1 (put another way, if I asked for version 1 but only
 * version 2 is available, version 2 needs to be able to service my request).
 * If this is not possible, a new application name should be selected:  the
 * two "versions" aren't really the same application any more.  By convention,
 * version 0 is the same as specifying no version at all:  it means any
 * version will do.
 *
 * </p><p>
 *
 * Generally, code using the object pool does not need to specify a particular
 * version of an application, as whatever is on the {@code CLASSPATH} will
 * suffice.  No version is named, meaning any will do.
 *
 *</p><p>
 *
 * However, when a message is sent to an application, it uses the full name of
 * the app, which includes a version number.  The format of this full name is
 * created via
 * {@link sunlabs.titan.node.services.BeehiveService#makeName(Class, long)
 *  Application.makeName},
 * and this is what is returned by
 * {@link sunlabs.titan.api.Service#getName
 *  BeehiveServiceAPI.getName}.
 * The arguments input to {@code makeName} are the application class and the
 * application version number, which is, by convention, the application's
 * {@code serialVersionUID}.  This encoded full name can be decoded via
 * {@link sunlabs.titan.node.services.BeehiveService#getNameTokens(String)}.
 * It is through this mechanism that applications are dynamically updated
 * throughout the Beehive.
 * If a Node has an updated application, when it eventually sends a
 * message to some object in the system which should be serviced by that
 * application, the application's full name (including version number) will be
 * in the message.  Upon receipt of the message by a different Node, the new
 * application version will be loaded before delivering the message.
 *
 * </p><p>
 *
 * We have discussed whether simply including a version number in the
 * application's name and renaming the Class for each version update is more
 * appropriate.  A naming convention for applications would be declared, with
 * the name including a version number.  A new version, then, means that the
 * application developer would create a new Java file with a new name.
 * Presumably, jar files sent to users would include only the latest of those
 * files (or they could grow hopelessly large).
 *
 * </p><p>
 *
 * This approach is probably doable, but makes application versions more
 * heavy-weight than I'm comfortable with.  I have not experimented with this
 * approach.
 *
 * </p>
 * <h5> Format of Stored Data </h5>
 * <p>
 *
 * The logic for storing applications is in the private method {@code
 * storeAppInDOLR}; the logic for loading them is in {@code loadApplication}.
 *
 * </p><p>
 *
 * We need to be able to retrieve from the Beehive a particular application
 * version and all its inner classes (which are compiled by javac to separate
 * .class files).  First, we recursively reflect on the application class to
 * find all the declared inner classes, for each class capturing information
 * sufficient to (re)load that class into a instance of the private {@code
 * AppClassLoadingInfo} class, and packaging those instances into an instance
 * of the private {@code AppClassInfoList} class.  We store the {@code
 * AppClassInfoList} instance in the Beehive under the full name of the
 * application, which includes the version number.
 *
 * </p><p>
 *
 * It is important to note that class names (especially inner class names) do
 * not reflect the class version number.  Recall that versions are simply an
 * attribute of the application, and the "full name" is composed of the
 * application name plus the version number - this is the name used in
 * messages, and the name used for storing the {@code AppClassInfoList}.  We
 * need to ensure that inner classes respect versions:  suppose an inner class
 * changes between two version numbers?  We handle this by embedding inner
 * classes along with the application class itself into a single object and
 * storing that object whenever the version changes.
 *
 * </p><p>
 *
 * Some of the properties used on stored objects:
 *
 * </p><ul>
 *
 * <li> RANK_PERMANENT is set.  I could never decide when it is correct to
 *      delete an older version of an application...  Perhaps when this node
 *      replaces the app with a new version?  Can we simply delete the old
 *      application from the local store after we load a new version?
 *
 * <li> METADATA_APPLICATION is set to
 *      "sunlabs.titan.node.services.AppClassApplication".  This
 *      application is empty, and exists just to give this METADATA property a
 *      correct name; the Beehive is moving towards enforcing that each object
 *      stored is stored via an application.  We could potentially move some
 *      of the {@code storeAppInDOLR} and {@code loadApplication} logic to
 *      AppClassApplication.  I have not thought this through.  It'd be wise
 *      to have exhaustive unit tests in place before refactoring this code.
 *
 * <li> The property "ApplicationFramework" is set to the particular class
 *      name being stored.
 *
 * </ul><p>
 *
 * Applications (the {@code AppClassInfoList} objects) are all stored locally,
 * at the current Node.  This has several positive effects:
 *
 * </p><ul>
 *
 * <li> applications referred to by a Node in messages from that Node can be
 *      found at that Node if it is still reachable
 *
 * <li> stored applications will be automatically replicated in the Beehive by
 *      Nodes using those applications
 *
 * <li> unfortunate bootstrap issues are avoided:  it's very difficult to
 *      store an application using the {@code StoreApplication} application if
 *      it has not been started yet (but we want to store each application
 *      before starting it)
 *
 * </ul><p>
 *
 * There is one negative effect:
 *
 * </p><ul><li>
 *
 * more objects are stored at each Node, no matter what
 *
 * </li></ul><p>
 *
 * I worry that Nodes running on devices with limited storage available might
 * run into problems.  Right now, if the applications used at bootstrap time
 * of a Node cannot be stored, the Node will refuse to start (Note:  not unit
 * tested).  If the application is being loaded from the Beehive and cannot be
 * stored, the exception is logged but otherwise ignored (see {@link
 * #get(String) get}).
 *
 * </p>
 * <h4> IMPORTANT missing functionality!! </h4>
 * <p>
 *
 * The code currently assumes <b>all nodes are trusted</b>.  This, obviously,
 * is wrong.  We need to add some security measures in the
 * ApplicationFramework.
 *
 * </p><p>
 *
 * Here's the scenario we need to guard against:
 *
 * </p><p>
 *
 * I'm an evil Node, so I make my StoreApplication (which is very fundamental
 * functionality!)  version actually <b>delete</b> objects from the Beehive.  I
 * simply make sure my evil StoreApplication has a higher version number than
 * other StoreApplications in the system I wish to disrupt, place it in a jar
 * file on my {@code CLASSPATH}, and start a new Node, attaching it to the
 * system.  As my node sends out store messages to other nodes, they will
 * automatically load up my evil StoreApplication, replacing their own.
 * Because applications are backward compatible, my version can service any
 * other message's StoreApplication smaller (and correct) version request,
 * thus Nodes will begin deleting when they should be storing.
 *
 * </p><p>
 *
 * Obviously, this is an easy way to write a virus.
 *
 * </p><p>
 *
 * When storing an application into a Beehive, we need to attach a signature
 * vouching for the node that stored it.  When loading an application from the
 * Beehive, we need to be able to check the signature to be sure it came from a
 * Node we can trust.  The outer stored object, the {@code AppClassInfo}, is
 * probably the place where this signature needs to be applied.  I've never
 * coded this sort of thing (much less in Java), so I just never got around to
 * writing the code.
 *
 * </p>
 */

//
// Historical notes on testing:  Now obsolete, because the DummyNode class
// became too hard to maintain when the object storage code changed to require
// more services from the Node interface.
//
// XXX: We really need some sort of replacement for these tests.
//
/*
 * <h4> Testing </h4>
 * Unit tests for ApplicationFramework are in
 * sunlabs.titan.node.ApplicationFrameworkTest, in the test/unit directory.
 * They use a simple Node, DummyNode, which is mostly stubbed out Node
 * functionality.  The DummyNode is not intended to hook up with other nodes:
 * it mostly provides logging services and can store and load objects.  It is
 * assumed the full Node functionality will be tested in a NodeTest at some
 * point.
 * <p>
 * One of the key things to test is that the correct application version is
 * loaded as appropriate.  This is easy to test if the application can be
 * found locally (either because it's already been loaded, or it's available
 * on the local {@code CLASSPATH}).
 * <p>
 * To test loading from the Beehive, though, we need to arrange to have a
 * DummyNode store a particular application version, say 5, and then test with
 * a local {@code CLASSPATH} that contains a lower version, say 3.  We need to
 * request application version 5 and ensure that it is found in the Beehive.
 * This means:
 * <ul>
 * <li> We need two copies of the application class files, with two version numbers
 * <li> We need to start a VM with the higher version number on the
 *      {@code CLASSPATH}, and store it in a DummyNode.
 * <li> A new VM needs to be started with the lower version in the
 *     {@code CLASSPATH} (or none at all:  we need to ensure the higher version
 *     number cannot be found locally!).
 * <li> The new VM needs to restart the DummyNode with an empty ApplicationFramework,
 *      and the higher version app needs to be requested.
 * </ul>
 * This involves more control-flow than possible in simple JUnit tests, as JUnit does
 * not ensure an order of test execution.  However, it's possible to have an ant task
 * that orders JUnit tasks, causing the necessary work to happen.
 * <p>
 * I'm currently working on this test.
 * <p>
 * For testing with a live Beehive, I copy my celeste.jar to some known location
 * and start up several nodes.   I then change an application version and regenerate
 * celeste.jar (this is why the original celeste.jar needs to be copied;  it would
 * confuse the running Nodes if the celeste.jar file is replaced from under them),
 * and start a new Node with that new celeste.jar, using one of the running nodes
 * as a gateway to the running system.  Cause an application message to be sent,
 * either through the web interface or by changing the version of an app that sends
 * out periodic messages automatically, and watch the changes propagate through the
 * system.  The web interface shows the "full" application name that's registered
 * in the ApplicationFramework;  this includes the version number.  You can also
 * see which objects are stored locally (we expect this to be each application).
 */

public final class ApplicationFramework implements XHTMLInspectable {
    //
    // Debugging and testing support:
    //
    // One can set the
    // sunlabs.titan.node.ApplicationFramework.loadOnlyFromObjectStore
    // property to a string containing the fully resolved (dot form hostname
    // portion, port explicitly specified) URL of the HTTP interface to the
    // Node in which this ApplicationFramework instance is running.  When so
    // set and this instance is fully started (see below), it is to load new
    // applications only from the object pool (i.e., not use the local classpath).
    //
    // XXX: Expose this property and its semantics in the class javadoc
    //      comment?  Probably not; it's a knob we don't want users to have to
    //      concern themselves with.
    //
    static final private String loadOnlyFromObjectStoreValue = System.getProperty(
        "sunlabs.celeste.beehive.node.ApplicationFramework.loadOnlyFromObjectStore");
    final private boolean loadOnlyFromObjectStore;
    //
    // The flip side of loadOnlyFromObjectStore:  When true, this property
    // allows application classes to be stored in the object store.
    //
    static final private String storeClassesInObjectStoreValue =
        System.getProperty("sunlabs.titan.node.ApplicationFramework.storeClassesInObjectStore", "false");
    final private boolean storeClassesInObjectStore;

    //
    // The record of which applications are currently loaded.  Access is a two
    // step process:  first look under the application's unversioned canonical
    // name for a VersionMap, and second look within that map for the version
    // of interest.  But rather than searching this map directly, use one of
    // the versions of the findApplicationVersions() method, which
    // encapsulates the logic described above.  Calls to those methods should
    // be protected by synchronizing on this field.
    //
    private final Map<String, VersionMap> applications =
        new HashMap<String, VersionMap>();

    //
    // Acts as a condition variable for serializing access to the service
    // loading machinery.  (We don't want multiple threads competing to load
    // the same service, as that could lead to orphaned service instances and
    // subsequent failures to load Beehive objects they control.)
    //
    private boolean loadingService = false;

    private final BeehiveNode node;
    private final DOLRLogger log;

    //
    // When an ApplicationObjectInputStream instance is called on to
    // deserialize objects arriving from other nodes, it needs to use the same
    // class loaders to handle Application subclass instances as were used to
    // load them locally in the first place.  This map captures the necessary
    // information.
    //
    // XXX: The map's key is an application's canonical class name (without
    //      version information).  This choice implies that we'll use the same
    //      class loader for all versions of a given application.  Is that
    //      wise?  Perhaps it would be better to use the string resulting from
    //      Application.makeName() as key instead.  That would give each
    //      distinct version a different class loader.
    //
    //      Indeed, we need to do this, but can't until the serialized form of
    //      application classes is extended to include an annotation that
    //      records the app's version number.  Until that happens, we won't be
    //      able to successfully upgrade apps on the fly to newer versions.
    //
    private Map<String, ApplicationLoader> classNameToLoader =
        new HashMap<String, ApplicationLoader>();

    /** The bootstrapping state of this node.  */
    private volatile boolean fullyStarted = false;

    // During normal processing, if this Framework finds a BeehiveService it
    // hasn't seen before, it enters it into the applications Map, starts it,
    // and stores it into the object pool so other Nodes can find this application
    // version if necessary.
    //
    // Bootstrapping is a delicate dance, though.  Storing the application can
    // use an application itself (e.g. a stored object will need to be
    // published) - so we must ensure the necessary applications have been
    // started.  Additionally, starting a Node uses the JoinApplication to
    // populate the NeighbourMap.
    // We don't really want to start applications until we know that the Node's
    // NeighborMap has been filled in.
    //
    // As a result, bootstrapping proceeds in two phases:  first the Node (and
    // applications built on top of it, like Celeste), call get() on all
    // applications that use threads to perform periodic tasks to make sure
    // they are eventually started when the Node is completely started. During
    // this phase, we simply add BeehiveServices into the application Map.
    //
    // Next, the Node tells us it has completed its Join operation.  Once this
    // is done, we know that our neighbours have been found and they know about
    // us.  At this point, we start and store the applications that we know of.
    // Note that it would be dangerous to start the applications before the Join
    // has completed, as we might know about some neighbour node that hasn't
    // yet found out about us.
    //
    // After this phase, we're in normal operation: all BeehiveServices are
    // put in the Map, stored, and then started.  Note that we store first, so
    // any messages that might be transmitted by a Node will refer to
    // applications  that we know have been stored;  otherwise, we might need
    // to throw some messages away because we don't know how to process them
    // (if we cannot find the application that should process them).
    //
    // BeehiveServices and all their inner classes are stored on the local
    // node, always.  This has two advantages:
    //    - The originator Node of any BeehiveMessage that refers to a specific
    //      application version will have that version stored locally.  This
    //      means the receiver will be able to find the application, unless
    //      there is a network partition which divides the sender and receiver
    //      (in which case, it might not matter that we cannot find the
    //      application).
    //    - Bootstrapping is very difficult to reason about if storing an
    //      application required using the StoreObjectApplication.
    //      StoreObjectApplication and any apps it uses would need to be
    //      started, then stored (rather than stored and then started).  (And,
    //      indeed, StoreObjectApplication is now defunct.  So taking this
    //      approach would require understanding its successor.)

    /**
     *  Create a new ApplicationFramework.
     *
     * @param node the BeehiveNode hosting this application framework
     * @param log  the log to be used for trace information
     * @throws NullPointerException if node or log are null
     */
    public ApplicationFramework(BeehiveNode node, DOLRLogger log) {
        if (node == null || log == null) {
            throw new NullPointerException("node and log must not be null");
        }

        new File(node.getSpoolDirectory() + File.separator + "applications" + File.separator).mkdirs();

        this.node = node;
        this.log = log;

        //
        // See whether the
        // sunlabs.titan.node.ApplicationFramework.loadOnlyFromObjectStore
        // property's been set to name this instance.
        //
        String httpURL = this.node.getNodeAddress().getHTTPInterface().toString();
        this.loadOnlyFromObjectStore = httpURL.equals(ApplicationFramework.loadOnlyFromObjectStoreValue);
        if (this.loadOnlyFromObjectStore)
            this.log.info("loadOnlyFromObjectStore set for %s", httpURL);

        this.storeClassesInObjectStore = Boolean.parseBoolean(ApplicationFramework.storeClassesInObjectStoreValue);
    }

    //
    // XXX: The loading machinery that follows has a severe deficiency:  It
    //      can only load explictly named versions from the object pool.  If
    //      either the version number is omitted from the full service name or
    //      it is set to the "don't care" value of 0, object pool loading will
    //      fail.
    //
    //      The problem stems from using the full service name (version number
    //      included) as the value that's hashed to form the BeehiveObjectId
    //      for the class information that's stored in the object pool.  A
    //      subsequent lookup must hash the same value or the lookup will
    //      fail.  Thus, we won't be able to find unversioned or off-versioned
    //      variants of the service name.
    //

    /**
     * Get a Beehive {@code Service} by name, loading and starting it if it is
     * not already known.  If a service is returned, it is guaranteed to have
     * been started.
     *
     * @param name  the name of the service to be retrieved
     *
     * @return the named service, or null if it cannot be found or started
     *
     * @throws NullPointerException
     *      if name is null
     */
    public Service get(String name) {
        return this.get(name, false);
    }

    /**
     * Get a Beehive {@code Service} by name, loading and starting it if it is
     * not already known.  If a service is returned, it is guaranteed to have
     * been started.
     *
     * @param name  the name of the service to be retrieved
     * @param trace {@code true} if the process of retrieving the service is
     *              to be traced
     *
     * @return  the named service, or {@code null} if it cannot be found or
     *          started
     *
     * @throws NullPointerException
     *      if name is null
     */
    public Service get(String name, boolean trace) {
        if (name == null) {
            throw new NullPointerException("Service name must not be null");
        }

        synchronized(this.applications) {
            for (;;) {
                Service service = getVersionedApp(name, trace);
                if (service != null)
                    return service;

                //
                // The service has not yet been registered.  If some other
                // thread is in the process of trying to load and register a
                // service, wait for it to complete and then retry.
                // Otherwise, take responsibility for loading it.
                //
                // Note that this code serializes loading all services.  If
                // that turns out to be a bottleneck, this code could be
                // restructured to serialize only attempts to load the service
                // named as argument.  The loadingService boolean would turn
                // into a condition variable specific to its service and
                // locking would be done with a ReentrantLock rather than with
                // a simple synchronization on this.applications.
                //
                if (this.loadingService) {
                    try {
                        this.applications.wait();
                    } catch (InterruptedException ie) {
                        // Fall through...
                    }
                    continue;
                }

                this.loadingService = true;
                try {
                    service = loadAndStartApp(name);
                } catch (Exception e) {
                    e.printStackTrace();
                    this.log.throwing(e);
                    this.log.warning("Caught exception %s during load and start of %s", e, name);
                } finally {
                    this.loadingService = false;
                    this.applications.notifyAll();
                }
                return service;
            }
        }
    }

    /**
     * Get an application that is greater than or equal to a particular
     * version number, which is encoded in the name.  The application must
     * already have been loaded; no attempt is made to find and load a version
     * that's not already in the applications map.
     */
    //
    // XXX: The trace parameter is unused.
    //
    private Service getVersionedApp(String name, boolean trace) {
        AppInfo requested = new AppInfo(name);
        synchronized(this.applications) {
            VersionMap versionMap = this.findApplicationVersions(requested.name);
            Service service = versionMap.findVersion(requested.version);
            return service;
        }
    }

    /**
     *  Check that the given application has a compatible version
     *  number with the requested verison.  A compatible version is
     *  {@code >= } to a requested version, or zero (a version of zero
     *  means "any version").
     *
     * @param app  the application to check
     * @param requested the version number we require
     * @return true if the application's version is compatible with the
     *               requested version
     */
    private boolean checkVersion(Service app, long requested) {
        boolean versionOK = false;

        if (app != null) {
            // Check the version number. If we asked for version 0,
            // we can save a bit of work (found version will never be
            // less than zero).
            if (requested == 0) {
                versionOK = true;
            } else {
                AppInfo found = new AppInfo(app.getName());
                if (found.version < requested) {
                    this.log.finer("checkVersion: found version %d  < requested version %d", found.version, requested);
                } else {
                    versionOK = true;
                }
            }
        }
        return versionOK;
    }

    /**
     * Stop and delete the supplied Application instance from
     * this ApplicationFramework.
     *
     * @param name  the name of the Application to remove
     */
    public void remove(String name) {
        this.log.entering(name);

        Service app = null;
        synchronized(this.applications) {
            AppInfo info = new AppInfo(name);
            VersionMap versionMap = this.findApplicationVersions(info.name);
            app = versionMap.remove(versionMap.findVersion(info.version));
        }

        if (app != null) {
            this.log.finest("stopping " + name);
            //
            // Be sure to give the application a chance to clean up after
            // itself, such as deleting any helper threads it created.  If we
            // stop this application out from under someone, it's OK:  it's
            // just another transient Node failure mode.
            //
            app.stop();
        }
        this.log.exiting();
    }

    /**
     * Callback from the Node, used to coordinate bootstrapping activities.
     * The Node will call this method when the NeighbourMap has been populated
     * and message passing can begin.
     */
    public void fullyStarted() throws IOException, Exception {
        this.log.entering();

        //
        // XXX: Why isn't this assignment protected by the lock acquired
        //      below?
        //
        this.fullyStarted = true;

        try {
            //
            // Capture the list of applications we've already seen so we can
            // start them all.
            //
            Set<VersionMap> saved = null;
            synchronized(this.applications) {
                saved = new HashSet<VersionMap>(this.applications.values());
            }

            //
            // XXX: This isn't quite right, PublishObjectApplication at least
            //      should be started before anything is stored.  Perhaps we
            //      should start everything, and then store, but that won't
            //      quite be right either - starting some apps causes them to
            //      use other apps, which should be stored before first use.
            //
            for (VersionMap versionMap : saved) {
                //
                // Start the newest version of the app.
                //
                Service app = versionMap.findVersion(0);
                storeAppInDOLR(app);
                app.start();
            }
        } finally {
            this.log.exiting();
        }
    }

    /**
     * Call the server side method for the {@link BeehiveService} specified in a {@link BeehiveMessage}.
     *
     * @param request the message being sent to an application
     * @return the reply message from the BeehiveService, or a message with a {@code DOLRStatus} of {@code INTERNAL_SERVER_ERROR}
     *         if the BeehiveService returns no message, or {@code METHOD_NOT_ALLOWED} if the BeehiveService cannot be found.
     */
    public BeehiveMessage sendMessageToApp(BeehiveMessage request) {
        String subjectClass = request.getSubjectClass();

        String subjectMethod = request.getSubjectClassMethod();

        Service a = this.get(subjectClass, request.isTraced());

        if (a != null) {
            if (request.isTraced()) {
                this.log.info("recv(%5.5s) %s %s", request.getMessageId(), subjectClass, request.getSubjectClassMethod());
            }
            try {
                /*
                 * IllegalAccessException - if this Method object enforces Java language access control and the underlying method is inaccessible.
                 * IllegalArgumentException - if the method is an instance method and the specified object argument is not an instance of the class or interface declaring the underlying method (or of a subclass or implementor thereof); if the number of actual and formal parameters differ; if an unwrapping conversion for primitive arguments fails; or if, after possible unwrapping, a parameter value cannot be converted to the corresponding formal parameter type by a method invocation conversion.
                 * InvocationTargetException - if the underlying method throws an exception.
                 * NullPointerException - if the specified object is null and the method is an instance method.
                 * ExceptionInInitializerError - if the initialization provoked by this method fails.
                 */
                return a.invokeMethod(subjectMethod, request);
            } catch (IllegalArgumentException illegalArgument) {
                illegalArgument.printStackTrace(System.err);
                return request.composeReply(this.node.getNodeAddress(), DOLRStatus.EXPECTATION_FAILED, subjectClass);
            } catch (ExceptionInInitializerError exceptionInInitailizer) {
                return request.composeReply(this.node.getNodeAddress(), DOLRStatus.SERVICE_UNAVAILABLE, subjectClass);
            } catch (Exception e) {
            	System.err.printf("Unhandled Exception %s%n", e);
            	e.printStackTrace();
            	throw new RuntimeException(e);
            }
        }

        this.log.severe("Unimplemented BeehiveService class: message type=%s class=%s method=%s (found=%s)", request.getType(), subjectClass, subjectMethod, a);
        return request.composeReply(this.node.getNodeAddress(), DOLRStatus.NOT_IMPLEMENTED);
    }

    /**
     *  Store an application and all its nested classes in the object pool.
     *  <p>
     *  An AppClassInfo instance will be created to record the BeehiveObjectIds
     *  of  the stored classes and their names, for later class loading.
     *  The AppClassInfo object  will be stored under the application's
     *  full name as created by {@link Application#makeName}.
     *  </p><p>
     *  This is necessary as nested classes are used extensively by
     *  applications (it is <bold>required</bold> that applications be
     *  self-contained if they can be dynamically updated).  New class loaders
     *  are created for each version of an application (actually, of any
     *  application in a package).  The nested classes for an application must
     *  be loaded by the same loader as the main class.  Additionally, these
     *  nested classes are not required to carry version information with
     *  them.  Creating a data structure to record which classes go with which
     *  versions simplifies things.
     *  </p>
     *
     * @param app  the application to be stored
     *
     * @throws IOException if storing the application class or any
     *         nested classes in the object pool fails
     */
    private void storeAppInDOLR(Service app) throws IOException {
        //
        // Avoid doing the store if our execution environment says not to.
        //
        if (!this.storeClassesInObjectStore)
            return;

        this.log.entering(app.getName());
        //
        // XXX: Note that app.getName() returns an Application name value
        //      that includes the app's version suffix.  Since we use that
        //      name here to generate the BeehiveObjectId of its stored form,
        //      we'll later have to search for it using the same name.
        //      That is, the search key will have to include the version
        //      information.  In cases where we don't know the version in
        //      advance, this will be problematic.  (So some sort of fix
        //      is required.)
        //
        BeehiveObjectId id = new BeehiveObjectId(app.getName().getBytes());

        // XXX Consider changes to allow packages of applications to be loaded
        //     with a single loader. To allow the classes to eventually be
        //     garbage collected by this VM, we'd need to unload all apps used
        //     by that package when any app's version changes (and we'd need
        //     additional framework to track that information).  This is not
        //     being done now because applications in the system tend to be
        //     quite independent.
        try {
            //
            // XXX: Security issues:  need to insert a way to sign the top
            //      level object.  Nodes that wish to load this application
            //      should be able to check with some authority that this Node
            //      is OK.
            //

            AppClassObjectType.AppClassObject.AppClassInfoList infoList = new AppClassObjectType.AppClassObject.AppClassInfoList();
            ApplicationFramework.fillAppInfo(infoList, app.getClass());
            //this.log.info("infoList: %s", infoList.toString());

            AppClass appClass = (AppClass) this.node.getService("sunlabs.titan.node.services.object.AppClassObjectType");
            AppClass.AppClassObject object = appClass.create(id, infoList);
            try {
                this.node.getObjectStore().create(object);
            } catch (ObjectExistenceException e) {
                //
                // Normal occurrence; this app has already been stored in the
                // Beehive object pool.
                //
                //this.log.info("%s: already stored in Beehive object pool", app.getName());
            } catch (Exception ioe) {
                this.log.warning("store of " + app.getName() + " failed: " + ioe);
                ioe.printStackTrace(System.err);
            }
        } finally {
            this.log.exiting();
        }
    }

    //
    // Augment infoList with class information for both the class given as
    // argument and its nested classes, handling nested classes first and then
    // the class itself.
    //
    private static void fillAppInfo(AppClassObjectType.AppClassObject.AppClassInfoList infoList, Class<?> c)
            throws IOException {
        Class<?>[] classes = c.getDeclaredClasses();
        for (Class<?> nestedClass : classes) {
            ApplicationFramework.fillAppInfo(infoList, nestedClass);
        }
        String name = c.getName();
        byte[] classBytes = ApplicationFramework.getByteCodes(c);
        infoList.add(name, classBytes);
    }

    //
    // Obtain the byte codes for the class given as argument, throwing
    // IOException if something goes wrong.
    //
    private static byte[] getByteCodes(Class<?> c) throws IOException {
        String classname = String.format("%s%s.class",
            "/", c.getName().replace(".", File.separator));
        //
        // Magic!  Where is it documented that a class will regurgitate its
        // class file contents under this resource name?
        //
        InputStream istream = c.getResourceAsStream(classname);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            int count;
            byte buffer[] = new byte[4096];
            while ((count = istream.read(buffer)) != -1) {
                baos.write(buffer, 0, count);
            }
            return baos.toByteArray();
        } finally {
            try { baos.close(); } catch (IOException e) {}
            try { istream.close(); } catch (IOException e) {}
        }
    }

    /**
     * Load an application and start it.  BeehiveServices will be loaded from
     * either the local CLASSPATH or from the Beehive object pool.
     *
     * @param appName   the full name of the BeehiveService to load
     *
     * @return the loaded (and started) application
     *
     * @throws Exception if an error was found while loading or starting
     */
    private Service loadAndStartApp(String appName) throws Exception {

        this.log.entering(appName);
        try {
            Service app = loadApplication(appName);

            // There is a window where an application could be started twice:
            //  T1  is at this point in the code
            //   T2 has fullyStarted() called on it, sets this.fullyStarted and
            //      starts all known apps
            //  T1 then checks the fullyStarted flag here, restarts the apps.
            //
            // We could jump through hoops here to ensure start() is only
            // ever called once, but that's futile:  BeehiveServiceAPI start
            // is a public method, so anyone could call start() at any time.
            // BeehiveServices must be aware of this and protect themselves
            // against multiple start calls.
            if (this.fullyStarted) {
                //
                // Note that we start the application after inserting it into
                // the applications Map (in loadAppFromDOLR).  Start code can
                // be arbitrarily complex, and (so far) there are no
                // requirements about what can or cannot occur there.  It is
                // possible to have start code that requires the application
                // to be inserted in the object pool's set already because it
                // sends a message to itself.
                //
                // XXX: It would be good to be able to pass parameters to the
                //      application or its startup.
                //
                storeAppInDOLR(app);
//                this.log.info("starting " + app.getName());
                app.start();
            }
            return app;
        } finally {
            this.log.exiting();
        }
    }

    /**
     * Load an application and insert it into the applications Map.
     * @param appName
     * @return the loaded application, which has not yet been started
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws NoSuchMethodException
     * @throws IOException
     */
    private Service loadApplication(String appName) throws
        ClassNotFoundException, InstantiationException, IllegalAccessException,
        InvocationTargetException, NoSuchMethodException, IOException
    {
        assert(appName != null);
        //this.log.info(appName);

        // BeehiveServices can be found in one of 3 places:
        //   - the applications map (has already been loaded by this Node)
        //   - the Classpath of the VM we're running in (locally)
        //   - the object pool (remotely)
        //
        // We are called when we know the application is not already loaded
        // into this Node.  In this method, we check for the class locally,
        // and then in the object pool as a last resort.
        // Even local class loading is expensive! As a quick optimization,
        // we look to see if *any* version of this class has ever been found.
        // If so, we assume that it won't be found locally and just go straight
        // to the object pool.  This is safe, because higher number versions MUST
        // be backward compatible (so version 2 of an application must be
        // safe to use even if version 1 was requested - otherwise, we need
        // to keep all applications in the system at all times).

        // Each application is loaded in a separate class loader.  This means
        // that applications cannot share any information via static state,
        // which is probably good anyway.

        final AppInfo requested = new AppInfo(appName);
        Class<?> appClass = null;
        Service app = null;

        //
        // Look for the application on the local CLASSPATH.  Simple
        // optimization (Java class loading can be slow):  if we have
        // installed any version of this application, we can skip looking in
        // the local CLASSPATH.  The first time we see an application name,
        // we'll attempt to load from the CLASSPATH, and that will be
        // installed in the map unless it has a version that is too low - in
        // which case we found the application in the object pool.  Because
        // applications are backwards compatible, we install increasing
        // application versions only (e.g.  if version 2 is in the map, and a
        // request comes in for version 1, it can be satisified by version 2
        // of the app), so this current request will be greater still, and
        // won't be found on the CLASSPATH.
        //
        final String unversionedName = requested.name;

        synchronized(this.applications) {
            //
            // Look for any version.
            //
            VersionMap versionMap = this.findApplicationVersions(unversionedName);
            app = versionMap.findVersion(0);
        }

        final boolean allowLocalLoading = !this.fullyStarted ||
            !this.loadOnlyFromObjectStore;
        boolean appInHand = app != null;
        boolean appLoaded = false;
        if (!appInHand && allowLocalLoading) {
            //
            // We've never seen any version of this application before, so it
            // could well be on our local CLASSPATH.
            //
            try {
                synchronized(this) {
                    //
                    // N.B.  Not using versioning information to find the
                    // loader.  See the XXX remark at classNameToLoader's
                    // declaration.
                    //
                    ApplicationLoader loader =
                        this.classNameToLoader.get(unversionedName);
                    if (loader == null) {
                        loader = new ApplicationLoader(
                            this.getClass().getClassLoader());
                        this.classNameToLoader.put(unversionedName, loader);
                    }
                    //this.log.info("locally loading %s using loader %s",
                    //    unversionedName, loader);
                    appClass = Class.forName(unversionedName, false, loader);
                }
            } catch (Exception e) {
                this.log.finest("initial Class.forName got an exception " + e);
            }
            //
            // Instantiate it, and check the version number.
            //
            app = instantiateAndCheck(appClass, requested.version);
            appLoaded = app != null;
        }

        //
        // If it's still not found, we have to look for it in the object pool.
        //
        if (!appLoaded) {
            //
            // The composite object is the outer object referred to by
            // the application name.  It contains the outer class and all
            // inner classes.
            //
            // XXX: In the future, it will contain a signature vouching that
            //      this object was placed in the object pool by a trusted Node.
            //
            // We rely on all inner classes only being used by their
            // outer application class to ensure that they are loaded
            // by the correct loader.  Weird type errors might occur
            // if this rule is violated by application writers!  (Hint:
            // inner application classes should always be private).
            //
            // XXX: Could we use the ClassDepAndJar ant plugin to generate a
            //      jar file that has all and only what the application needs,
            //      and then store the jar itself in the object pool?  Then we could
            //      simply point a class loader at that jar file after
            //      retrieving it?  (I think...)
            //
            //      Back to jar files:  One difficulty is that the Jar class
            //      is not serializable.  That will make storing jar files in
            //      the object pool difficult; although they could be stored as files
            //      using the Celeste interface, doing so would pre-suppose
            //      much of the machinery being implemented here.  Grumble!
            //
            // XXX: Note that we're searching for a specific version of the
            //      app -- the version that's embedded into appname (or not,
            //      if the caller hasn't bothered to name a particular
            //      version).  That means we won't find more recent versions
            //      that have superceded the one that the caller knows about.
            //      Not good...
            //
            BeehiveObjectId nameID = new BeehiveObjectId(appName.getBytes());

            if (this.log.isLoggable(Level.FINE)) {
                this.log.fine("Attempting to load %s %s from object pool",
                    nameID, appName);
            }
            AppClass.AppClassObject.InfoList infoList = null;

            AppClass appClassObjectType = this.node.getService(AppClassObjectType.class);
            try {
                AppClass.AppClassObject object = appClassObjectType.retrieve(nameID);
                infoList = object.getInfoList();
            } catch (BeehiveObjectStore.DeletedObjectException e) {
                throw new RuntimeException("Beehive Service has been deleted in the object pool: " + appName);
            } catch (NotFoundException e) {
                throw new RuntimeException("Beehive Service not found in the object pool: " + appName);
            }
            //
            // Load up each class in turn.  The inner classes will come first,
            // and finally the outer class.  Use a fresh class loader, to
            // avoid trying to load the same class twice with a single loader
            // (which is forbidden).
            //
            // XXX: This looks wrong.  If a down-rev version of the app is
            //      already loaded, we'll end up using the same class loader
            //      to load its new version, which will have the same
            //      (outermost) class name as the down-rev version.  But we're
            //      stuck until app serialization is modified to add version
            //      annotations to its class descriptors.
            //
            //      It is wrong.  See comments elsewhere about use of
            //      classNameToLoader.
            //
            ApplicationLoader loader = null;
            synchronized(this) {
                loader = this.classNameToLoader.get(unversionedName);
                if (loader == null) {
                    loader = new ApplicationLoader(
                        this.getClass().getClassLoader());
                    this.classNameToLoader.put(unversionedName, loader);
                }
            }
            for (AppClass.AppClassObject.InfoList.AppClassLoadingInfo loadingInfo : infoList) {
                String name = loadingInfo.getName();
                byte[] classBytes = loadingInfo.getClassBytes();

                try {
                    appClass = loader.defineDOLRClass(
                        name, ByteBuffer.wrap(classBytes));
                } catch (ClassFormatError cfe) {
                    ClassNotFoundException e =
                        new ClassNotFoundException("Problem with class found in object pool " + name, cfe);
                    this.log.throwing(e);
                    throw e;
                } catch (NoClassDefFoundError ncdfe) {
                    ClassNotFoundException e =
                        new ClassNotFoundException("Problem with class found in object pool " + name, ncdfe);
                    this.log.throwing(e);
                    throw e;
                }

                if (appClass == null) {
                    // This is not good, we couldn't find it either locally or
                    // in the object pool!
                    ClassNotFoundException e =
                        new ClassNotFoundException("Could not find class for "
                                + appName + " locally or in object pool");
                    this.log.throwing(e);
                    throw e;
                }
            }
            //
            // The last appClass in the list was the outer class.
            //
            app = instantiateAndCheck(appClass, requested.version);
        }

        //
        // At this point, we've exhausted our possibilities for instantiating
        // the app.  If it's not yet instantiated, give up.
        //
        if (app == null) {
            ClassNotFoundException e =
                new ClassNotFoundException("Could not find correct class for "
                        + appName + " locally or in object pool");
            this.log.throwing(e);
            throw e;
        }

        //
        // Replace down-rev versions of the application with the new version.
        //
        // XXX: If we used a loader per package (rather than per app), we'd
        //      need to remove all applications loaded by the package loader
        //      here.
        // XXX: Need to worry about updating the classNameToLoader map.  (That
        //      might mean that synchronization ought to be on this as a
        //      whole, rather than on this.applications.  If that changes
        //      here, it should change for other places where we synchronize
        //      on this.applications as well.)
        //
        synchronized(this.applications) {
            //printApplicationFramework();
            //System.out.println("==============================");
            VersionMap versionMap = this.findApplicationVersions(app);

            //
            // Add the new version of the app.
            //
            AppInfo info = new AppInfo(app.getName());
            versionMap.put(info.version, app);
            //this.log.finer("put " + app.getName());

            //
            // Remove down-rev versions.
            //
            Iterator<Map.Entry<Long, Service>> it =
                versionMap.entrySet().iterator();
            Set<Service> removedApps = new HashSet<Service>();
            while (it.hasNext()) {
                Map.Entry<Long, Service> entry = it.next();
                long entryVersion = entry.getKey();
                if (entryVersion >= info.version)
                    continue;

                it.remove();
                removedApps.add(entry.getValue());
                this.log.info("removed version %d", entryVersion);

                //
                // Note that we've now removed local access to this down-rev
                // version of the application, but that version's class files
                // still reside in the object pool (stored as an AppClassInfoList
                // object).
                //
                // It would be really nice to delete the AppClassInfoList
                // object, but there is a problem:
                //
                //  -   There might be some window of time when an outstanding
                //      message will be delivered to a Node that hasn't seen
                //      those class files (so rather than deleting outright,
                //      we'd want to have them be deleted eventually - but
                //      it'd still open a possible window for another Node not
                //      being able to get to a class file it needs).  A retry
                //      operation would be a nice way to handle this, so if a
                //      message is sent with app.v1 and app.v2 is installed
                //      here before the message is delivered to some remote
                //      Node, that remote Node would request a retry, and the
                //      same message would be sent with app.v2.
                //
                // In any event, we wouldn't want to delete the old apps
                // while holding the writeLock;  we'd need to do it outside
                // the lock.
                //
            }

            //
            // Stop the down-rev applications found above.  We're not
            // concerned about stopping them out from under someone; everyone
            // must be resiliant to transient node failures.
            //
            for (Service oldApp : removedApps) {
                oldApp.stop();
            }

            //printApplicationFramework();
        }

        return app;
    }

    /**
     * Instantiate an BeehiveService class and return it if the class's
     * version is >= the requested version.
     *
     * @param theClass  the class to instantiate.  It is assumed to extend
     *                  {@link BeehiveService}, and have a constructor that takes
     *                  a {@link BeehiveNode} as an argument.
     * @param requested the version number the instantiated application must
     *                  be >= to.  It is assumed that all applications are
     *                  backward compatible with previous versions (if they
     *                  aren't, they should be given a new application name).
     *
     * @return  the instantiated application, which meets the requested version
     *          requirements, or {@code null}
     *
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws NoSuchMethodException
     * @throws ClassCastException
     *      if {@code theClass} is not a {@code BeehiveService}
     */
    private Service instantiateAndCheck(Class<?> theClass, long requested)
        throws InstantiationException, IllegalAccessException,
               InvocationTargetException, NoSuchMethodException
    {
        if (theClass == null) {
            return null;
        }

        //
        // Although the class argument to this method is supposed to represent
        // a BeehiveService, it might not.  Verify that it does and then cast
        // it accordingly.
        //
        // XXX: Can this runtime check and failure exception be expressed more
        //      elegantly?  (Maybe Class.cast() might be useful here.)
        //
        if (!Service.class.isAssignableFrom(theClass))
            throw new ClassCastException("not a BeehiveService");
        @SuppressWarnings(value="unchecked")
            Class <? extends Service> appClass =
                (Class<Service>)theClass;

        Service application = null;
        try {
            Constructor<? extends Service> ctor = appClass.getDeclaredConstructor(BeehiveNode.class);
            application = (Service) ctor.newInstance(this.node);
        } catch (NoSuchMethodException nsme) {
            this.log.throwing(nsme);
            throw nsme;
        }

        // Check one more time to make sure the version number is really
        // what we expected.  Need to do this after the instance is created.
        if (!checkVersion(application, requested)) {
            application = null;
        }
        return application;
    }

//    /**
//     * Helper function, useful for debugging.
//     *
//     */
//    private void printApplicationFramework() {
//        Set<Entry<AppInfo, DOLRApplication>> eset = this.applications.entrySet();
//        System.out.println("APPLICATIONS: ");
//        for (Entry<AppInfo, DOLRApplication> e : eset) {
//            System.out.println("key: " + e.getKey() + " value: " + e.getValue());
//        }
//    }

    //
    // Accumulate all versions of all apps from the value set of the
    // applications map and return the resulting set of application names.
    //
    // XXX: The method name is for compatibility with previous implementations
    //      of ApplicationFramework.  It's no longer particularly accurate.
    //
    public Set<String> keySet() {
        Set<String> result = new HashSet<String>();
        for (Map.Entry<String, VersionMap> app : this.applications.entrySet()) {
            String unversionedName = app.getKey();
            VersionMap versionMap = app.getValue();
            for (Map.Entry<Long, Service> entry :
                    versionMap.entrySet()) {
                result.add(
                        BeehiveService.makeName(unversionedName, entry.getKey()));
            }
        }
        return result;
    }

    //
    // Given a BeehiveService, return a map containing all previously
    // registered versions of that application.  Modifications to the
    // returned map will affect which versions of the application are
    // considered to be registered.
    //
    // The caller is assumed to hold appropriate locks.
    //
    private VersionMap findApplicationVersions(Service app) {
        AppInfo info = new AppInfo(app.getName());
        return this.findApplicationVersions(info.name);
    }

    //
    // Given the name of a BeehiveService (in unversioned form), return a map
    // containing all previously registered versions of that application.
    // Modifications to the returned map will affect which versions of the
    // application are considered to be registered.
    //
    // The caller is assumed to hold appropriate locks.
    //
    private VersionMap findApplicationVersions(String unversionedName) {
        VersionMap versionMap = this.applications.get(unversionedName);

        if (versionMap == null) {
//            this.log.info("%s not found", unversionedName);
            versionMap = new VersionMap();
            this.applications.put(unversionedName, versionMap);
        } else {
//            this.log.info("%s found", unversionedName);
        }
        return versionMap;
    }

    public XMLServices toXML() {
        TitanXML xml = new TitanXML();
        
        XMLServices services = xml.newXMLServices();
        for (String name : this.keySet()) {
            Service service = this.get(name);
            XMLService s = xml.newXMLService(service.getName(), service.getDescription(), service.getStatus());
            services.add(s);
        }

        return services;
    }
    
    /**
     * Produce an {@link XHTML.EFlow} instance containing a representation of the {@link BeehiveService} list.
     * <p>
     * If the given {@link URI} path component is prefixed with the String "/service/" the remainder of the path is
     * considered to be the name of a {@link BeehiveService} which is dispatched to produce the XHTML content.
     * @See WebDAVDaemon
     * </p>
     */
    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
    	if (uri.getPath().startsWith("/service/")) {
            String name = uri.getPath().substring("/service/".length());
            Service service = this.node.getService(name);
            if (service != null) {
            	return service.toXHTML(uri, props);
            }
            return null;
        }
    	
        Set<String> apps = new TreeSet<String>(this.keySet());

        XHTML.Table.Head thead = new XHTML.Table.Head(
            new XHTML.Table.Row(
                new XHTML.Table.Heading("Service Name"),
                new XHTML.Table.Heading("Description"),
                new XHTML.Table.Heading("Status")));

        XHTML.Table.Body tbody = new XHTML.Table.Body();

        for (String name: apps) {
            Service app = this.get(name);
            XHTML.Anchor link = WebDAVDaemon.inspectServiceXHTML(app.getName());
            
            tbody.add(new XHTML.Table.Row(new XHTML.Table.Data(link),
                    new XHTML.Table.Data(app.getDescription()),
                    new XHTML.Table.Data(app.getStatus())));
        }

        XHTML.Table table = new XHTML.Table(
            new XHTML.Table.Caption("Node Services"), thead, tbody).setId("applications");

        XHTML.Div result = new XHTML.Div(table).setClass("section");
        return result;
    }

    //
    // A tuple class that records that canonical class name of a given
    // application and the application's version.
    //
    private static class AppInfo implements Serializable {
        private final static long serialVersionUID = 1L;

        //
        // The application's canonical class name.
        //
        public final String name;
        //
        // The (desired) verison of the application; zero denotes "don't
        // care".
        //
        public final long version;

        //
        // The name argument can be either fully qualified (including the
        // version number suffix) or not (including only the bare name portion
        // without the version suffix).
        //
        public AppInfo(String name) {
            if (name == null)
                throw new IllegalArgumentException("name must be non-null");

            String[] tokens = BeehiveService.getNameTokens(name);
            this.name = tokens[0];
            if (tokens.length < 2) {
                // Any version will do
                this.version = 0;
            } else {
                this.version = Long.parseLong(tokens[1]);
                if (this.version < 0)
                    throw new IllegalArgumentException(
                        "version must be non-negative (or be omitted)");
            }
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof AppInfo)) {
                return false;
            }
            AppInfo otherApp = (AppInfo) other;
            return this.version == otherApp.version &&
                this.name.equals(otherApp.name);
        }

        @Override
        public int hashCode() {
            //
            // Avoid losing precision from the long version value.
            //
            return (int)(this.version >> 32) ^
                (int)(this.version & 0xffffffff) ^ this.name.hashCode();
        }

        @Override
        public String toString() {
           return BeehiveService.makeName(this.name, this.version);
        }
    }

    //
    // Each instance of VersionMap captures all the extant versions of a given
    // application.  (The rest of ApplicationFramework tries to keep the
    // cardinality of the map down to at most one, but this class itself is
    // more general.)
    //
    // We want the application versions present in an instance of VersionMap
    // to be sorted by version, so that it's easy to find and return the
    // highest version present.
    //
    public static class VersionMap extends TreeMap<Long, Service> {
        private final static long serialVersionUID = 1L;

        public VersionMap() {
            super();
        }

        //
        // Return the highest version of the application held in the map whose
        // version is at least that of the argument.  If there's no such
        // version, return null.
        //
        // XXX: Do we need a version that returns precisely the requested
        //      version (except for when the "don't care" version is
        //      requested)?
        //
        public Service findVersion(long version) {
            if (version < 0)
                throw new IllegalArgumentException("version must be non-negative");

            SortedMap<Long, Service> map = this.tailMap(version);

            if (map.isEmpty()) {
                return null;
            }

            return map.get(map.lastKey());
        }
    }

    /**
     * Class loader for BeehiveServices in the Beehive.
     * All BeehiveServices must be
     * loaded by an instance of this class, which gives the object pool control to
     * throw away old versions of an application in favor of newer versions.
     */
    // For debugging, made this class non-static
    private static class ApplicationLoader extends ClassLoader {
        // Temp for debugging
        ClassLoader myParent;
        // Used for debugging, to give this app loader a name
        int count;
        static private int counter = 0;
        static private Object lock = new Object();

        protected ApplicationLoader(ClassLoader parent) {
            super(parent);
            this.myParent = parent;
            synchronized(lock) {
                this.count = counter++;
            }
        }

        final public Class<?> defineDOLRClass(String name, ByteBuffer buffer)
                throws ClassFormatError {
            return defineClass(name, buffer, null);
        }

        @Override
        public String toString() {
            return "ApplicationLoader " + this.count;
        }

    }

    /**
     * <p>
     *
     * A version of {@code ObjectInputStream} that overrides the {@code
     * resolveClass()} method to handle classes loaded from the object pool
     * specially.
     *
     * </p><p>
     *
     * The special handling is needed to ensure that objects of such classes
     * that are sent from one node to another in message payloads end up with
     * the proper class upon receipt.  (Without this special treatment, they
     * would have distinct types because their class would be loaded by the
     * system application class loader instead of by one of the
     * per-application loaders that the application framework sets up.)
     *
     * </p>
     */
    public class ApplicationObjectInputStream extends ObjectInputStream {
        //
        // Counterparts for the ObjectInputStream constructors
        //

        public ApplicationObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        public ApplicationObjectInputStream() throws
                IOException, SecurityException {
            super();
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws
                IOException, ClassNotFoundException {
            //
            // XXX: This version uses a package prefix to hard-wire its
            //      decision about what classes it will intervene on.  When
            //      Celeste starts getting and supporting third party
            //      applications, this technique will become inadequate.  At
            //      that point, switching to having a complementary
            //      ObjectOutputStream class annotate classes with whether or
            //      not they're Application subclasses and checking the
            //      annotation here would probably suffice.
            //
            // XXX: It turns out that there's another, more immediately
            //      compelling, reason for adding such annotations.  When
            //      transitioning from a down-rev version of a given
            //      application to a more current version,
            //      ApplicationFramework will have to load the new version of
            //      the application without any guarantee that the old version
            //      has been excised.  (It can drop its own references to the
            //      old app version , but it has no control over references
            //      elsewhere keeping that version alive.)  Thus, it must use
            //      a distinct class loader to load the new version.  But if
            //      the information available here at class resolution time
            //      doesn't include version numbers, there will be no way to
            //      tell which class loader to use to handle the incoming
            //      reference.
            //

            final String className = desc.getName();
            if (!className.startsWith("sunlabs.titan.node.services"))
                return super.resolveClass(desc);

            //ApplicationFramework.this.log.info("candidate: %s", className);

            //
            // Find the class loader that should handle this class and use it
            // to load the class.
            //
            synchronized(ApplicationFramework.this) {
                //
                // XXX: Linear search.  Ought to have a more efficiently
                //      searchable data structure.
                //
                for (Map.Entry<String, ApplicationLoader> entry:
                        ApplicationFramework.this.classNameToLoader.entrySet()) {
                    //
                    // By checking that name of the the class we're trying to
                    // load starts with the Application class name recorded in
                    // the loader map, we catch classes nested inside the
                    // Application class, as desired.
                    //
                    if (!className.startsWith(entry.getKey()))
                        continue;
                    ClassLoader loader = entry.getValue();
                    //ApplicationFramework.this.log.info("loading %s with %s",
                    //    className, loader);
                    return Class.forName(className, false, loader);
                }
            }

            //
            // XXX: How should Application classes that haven't been seen yet
            //      locally be handled?  (Indeed, will we see any such
            //      classes?)  They should probably be given loaders of their
            //      own, the same as ones first seen locally.  But for now,
            //      punt and use the default class loader.
            //
            ApplicationFramework.this.log.info(
                "%s: seen in BeehiveMessage before being loaded locally",
                className);
            return super.resolveClass(desc);
        }
    }

    //
    // Factory methods for returning ApplicationObjectInputStream instances.
    //
    // Needed to allow non-ApplicationFramework code to obtain them.
    //

    public ApplicationObjectInputStream newApplicationObjectInputStream(
            InputStream in)  throws IOException {
        return new ApplicationObjectInputStream(in);
    }

    public ApplicationObjectInputStream newApplicationObjectInputStream()
            throws IOException, SecurityException {
        return new ApplicationObjectInputStream();
    }


    //
    // Each instance of this class captures the information required to
    // reconstruct the classes that are part of a given application.
    //
    // As noted elsewhere, only the class for the application itself and its
    // (recursively) nested classes are captured.  Ideally, we should have a
    // way to find all the classes an application needs that aren't part of
    // the "Celeste core" or the Java platform and capture those.
    //
//    private static class AppClassInfoList implements
//            Serializable, Iterable<AppClassInfoList.AppClassLoadingInfo> {
//        private static final long serialVersionUID = 1L;
//
//        private final List<AppClassLoadingInfo> loadList =
//            new ArrayList<AppClassLoadingInfo>();
//
//        //
//        // A tuple class that records what's needed to feed a class loader to
//        // have it load a given class.
//        //
//        public static class AppClassLoadingInfo implements Serializable {
//            private static final long serialVersionUID = 1L;
//
//            public final String name;
//            public final byte[] classBytes;
//
//            //
//            // This method assumes that the caller will not overrwite the
//            // classBytes argument.
//            //
//            public AppClassLoadingInfo(String name, byte[] classBytes) {
//                this.name = name;
//                this.classBytes = classBytes;
//            }
//
//            @Override
//            public String toString() {
//                return String.format("%s: <%d byte codes>",
//                    this.name, this.classBytes.length);
//            }
//        }
//
//        public AppClassInfoList() {
//            super();
//        }
//
//        //
//        // The iterator feeds back AppClassLoadingInfo entries in the order
//        // that they were submitted via the add() method below.
//        //
//        public Iterator<AppClassLoadingInfo> iterator() {
//            return this.loadList.iterator();
//        }
//
//        public void add(String name, byte[] classBytes) {
//            this.loadList.add(new AppClassLoadingInfo(name, classBytes));
//        }
//
//        @Override
//        public String toString() {
//            return this.loadList.toString();
//        }
//    }
}
