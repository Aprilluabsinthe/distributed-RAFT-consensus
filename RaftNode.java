import lib.*;

import java.util.List;

public class RaftNode implements MessageHandling {
    private int id;
    private static TransportLib lib;
    private int num_peers;

    //Persistent State for all servers
    /**
     * latest term server has seen (initialized to 0 on first boot, increases monotonically)
     */
    private int currentTerm;
    /**
     *candidateId that received vote in current term (or null if none)
     */
    private int votedFor;
    /**
     * log entries;
     * <p>
     *     each entry contains command
     *     for state machine, and term when entry
     *     was received by leader (first index is 1)
     *</p>
     */
    private List<LogEntry> entries;


    //Volatile state on all servers:
    /**
     * <p>
     *     index of highest log entry known to be
     *     committed (initialized to 0, increases
     *     monotonically)
     * </p>
     */
    private int commitIndex;
    /**
     * <p>
     *     index of highest log entry applied to state
     *     machine (initialized to 0, increases
     *     monotonically)
     * </p>
     */
    private int lastApplied;

    //Volatile state on leaders:
    //(Reinitialized after election)
    /**
     * <p>
     *     for each server, index of the next log entry
     *     to send to that server (initialized to leader
     *     last log index + 1)
     * </p>
     *
     */
    private int[] nextIndex[];
    /**
     * <p>
     *     for each server, index of highest log entry
     *     known to be replicated on server
     *     (initialized to 0, increases monotonically)
     * </p>
     */
    private int[] matchIndex[];





    public RaftNode(int port, int id, int num_peers) {
        this.id = id;
        this.num_peers = num_peers;
        lib = new TransportLib(port, id, this);
    }

    /*
     *call back.
     */
    @Override
    public StartReply start(int command) {
        return null;
    }

    @Override
    public GetStateReply getState() {
        return null;
    }

    @Override
    public Message deliverMessage(Message message) {
        return null;
    }

    //main function
    public static void main(String args[]) throws Exception {
        if (args.length != 3) throw new Exception("Need 2 args: <port> <id> <num_peers>");
        //new usernode
        RaftNode UN = new RaftNode(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
    }
}
