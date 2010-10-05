package sunlabs.titan.api;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import sunlabs.titan.node.TitanMessage;
import sunlabs.titan.node.services.xml.TitanXML.XMLServices;

/**
 * This interface defines the fundamental operations on the set of {@link TitanService} instances for a {@link TitanNode}.
 * 
 *
 */
public interface TitanServiceFramework extends XHTMLInspectable {

    /**
     * Get the {@link TitanService} specified by {@code serviceName} loading it via the class loader if necessary.
     * The name of a {@code TitanService} is its class name, suffixed by additional parameters suitable for the {@code TitanServiceFramework} implementation.
     *
     * @param name  the name of the service to be retrieved
     *
     * @return the fully-qualified class name of the @{link TitanService}.
     *
     * @throws NullPointerException if serviceName is {@code null}.
     * @throws ClassNotFoundException if the {@code TitanService} cannot be found.
     * @throws NoSuchMethodException when the class constructor for the {@code TitanService} cannot be found.
     * @throws InvocationTargetException when the class constructor for the {@code TitanService} threw an Exception.
     * @throws IllegalAccessException 
     * @throws InstantiationException when the {@code TitanService} implementation is an interface, abstract class, or other non-instantiable object.
     * @throws IllegalArgumentException 
     */
    public TitanService get(String serviceName) throws NullPointerException, ClassNotFoundException, NoSuchMethodException, IllegalArgumentException, InstantiationException,
        IllegalAccessException, InvocationTargetException;

    public TitanMessage dispatch(TitanMessage request) throws IllegalArgumentException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException;

    /**
     * Invoke the {@link TitanService#start()} method for each {@link TitanService} in this framework.
     * <p>
     * This is used at node boot-time to start all of the services.  Services  added after boot-time will need to be started. 
     * </p>
     * @see TitanService#start()
     * @throws IOException
     */
    public void startAll() throws IOException;

    public Set<String> keySet();

    /**
     * Produce an XML representation of this {@code TitanServiceFramework}.
     * @return An instance of {@link XMLServices}.
     */
    public XMLServices toXML();
}
