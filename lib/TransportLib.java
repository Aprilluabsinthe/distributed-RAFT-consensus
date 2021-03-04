package lib;

import java.rmi.Naming;
import java.rmi.RemoteException;

/**
 * TransportLib -- enables communication among Raft nodes
 */
public class TransportLib {
    /**
     * ID of this Raft node
     */
    private int id;
    /**
     * The simulated network layer
     */
    private MessagingLayer ms;
    /**
     * The remote interface to register callback for the node
     */
    private RemoteControllerInterface remoteController;

    /**
     * Constructor for the TransportLib of a Raft node
     *
     * @param port             the port of server
     * @param id               the id of this node
     * @param messageHandling  the instance of the node
     */
    public TransportLib(int port, int id, MessageHandling messageHandling) {
        try {
            this.remoteController = new RemoteController(messageHandling);
            ms = (MessagingLayer) Naming.lookup("rmi://localhost:" + port + "/MessageServer");
            ms.register(id, remoteController);
        } catch (Exception e) {
            System.out.println(port);
            e.printStackTrace();
            System.exit(-1);
        }
        this.id = id;
    }

    /**
     * Send message through message server(underlayer network), this function is
     * a synchronous call which means the thread will be blocked until the
     * response returns or a timeout occurs.
     * Always use this API to send a message.
     *
     * @param message         the message to send
     * @return                on success, returns the response, otherwise null
     *
     * @throws RemoteException when RMI fails
     */
    public Message sendMessage(Message message) throws RemoteException {
        return ms.send(message);
    }

    /**
     * Apply an ApplyMsg to the framework for testing, to be called whenever
     * agreement is finished. Refer to ApplyMsg class for details.
     *
     * @param msg       the msg to apply.
     * @throws          RemoteException when RMI fails
     */
    public void applyChannel(ApplyMsg msg) throws RemoteException {
        ms.applyChannel(msg);
    }
}
