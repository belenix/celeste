package sunlabs.titan;

import java.io.Serializable;

import sunlabs.titan.api.BeehiveObject;
import sunlabs.titan.node.BeehiveMessage;
import sunlabs.titan.node.BeehiveNode;
import sunlabs.titan.node.NodeAddress;
import sunlabs.titan.node.BeehiveNode.NoSuchNodeException;
import sunlabs.titan.node.object.BeehiveObjectHandler;
import sunlabs.titan.node.services.BeehiveService;

public interface TitanNode {
    /**
     * Start the node.
     * This consists of initialising all of the listening sockets,
     * establishing our first connection via a Join operation,
     * and booting each of the internal applications in this Node.
     */
    public Thread start();
    

    public void stop();

    /**
     * Get this Node's {@link NodeAddress}.
     */
    public NodeAddress getNodeAddress();
    
    /**
     * Send the given {@link Serializable} {@code data} via a
     * {@link BeehiveMessage.Type#RouteToNode} to the {@link BeehiveNode}
     * that is the root of the {@link BeehiveObjectId} {@code objectId}.
     *
     * @param klasse The name of the {@link BeehiveService} to handle the reception of this message.
     * @param method The name of the method to invoke in {@code klasse} on the receiving node.
     * @param payload Serializable data as the input parameter to the {@code method} method.
     */
    public BeehiveMessage sendToNode(BeehiveObjectId nodeId, String klasse, String method, Serializable payload);

    /**
     * Transmit a {@link BeehiveMessage.Type#RouteToNode}
     * message to the specified node object-id.
     *
     * @param nodeId the {@link BeehiveObjectId} of the destination node.
     * @param objectClass the {@link String} name of the destination object handler class.
     * @param method the {@link String} name of the method to invoke.
     * @param payload the payload of this message.
     * @throws NoSuchNodeException if the receiver cannot route the message further and is not the specified destination object-id.
     */
    public BeehiveMessage sendToNodeExactly(BeehiveObjectId nodeId, String klasse, String method, Serializable payload) throws NoSuchNodeException;

    /**
     * Send the given {@link Serializable} {@code data} to the {@link BeehiveObject}
     * specified by {@link BeehiveObjectId} {@code objectId}.
     * The object must be of the {@link BeehiveObjectHandler} {@code klasse} and the
     * method invoked is specified by String {@code method}.
     *
     * @param objectId The {@link BeehiveObjectId} of the target {@link BeehiveObject}.
     * @param klasse The name of the {@link BeehiveObjectHandler} to use.
     * @param method The name of the method to invoke.
     * @param payload The data to transmit to the object.
     */
    public BeehiveMessage sendToObject(BeehiveObjectId objectId, String klasse, String method, Serializable payload);


}
