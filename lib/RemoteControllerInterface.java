package lib;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
/**
 * RemoteControllerInterface -- defines the remote callback functions that will
 * provide APIs for a MessageHandling interface for your Raft node
 */
public interface RemoteControllerInterface extends Remote {
    public Message deliverMessage(Message message) throws RemoteException;
    public GetStateReply getState() throws RemoteException;
    public StartReply start(int command) throws RemoteException;
}
