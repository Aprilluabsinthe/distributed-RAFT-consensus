package lib;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
/**
 * RemoteController -- implementation of RemoteControllerInterface, which
 * includes APIs for MessageHandling interface in the Raft node
 */
public class RemoteController extends UnicastRemoteObject implements RemoteControllerInterface{
    public final MessageHandling message_callback;

    private static final long serialVersionUID = 1L;

    public RemoteController(MessageHandling mh) throws RemoteException {
        this.message_callback = mh;
    }

    public Message deliverMessage(Message message) throws RemoteException {
       return message_callback.deliverMessage(message);
    }

    public GetStateReply getState() throws RemoteException{
        return message_callback.getState();
    }


    public StartReply start(int command) throws RemoteException {
        return message_callback.start(command);
    }
}
