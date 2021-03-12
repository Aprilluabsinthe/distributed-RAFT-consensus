import lib.*;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static java.lang.Thread.State.RUNNABLE;
import static java.lang.Thread.State.TERMINATED;
import static lib.Helper.*;
import static lib.Helper.toByteConverter;

public class RaftNode implements MessageHandling {
    // for outputing and printing
    public static boolean VERBOSE = true;
    private static final Logger logger = Logger.getLogger(RaftNode.class.getName());

    // node construction Members
    private int id;
    private static TransportLib lib;
    private int num_peers;

    //Persistent State for all servers
    private PersistentState persistentState;

    //Volatile state on all servers:
    private int commitIndex;
    private Integer lastApplied;

    // Volatile state on leaders:
    //(Reinitialized after election)
    private Integer[] nextIndex;
    private Integer[] matchIndex;
    private boolean isHeartBeating;

    // Timing members
    private Timer heartBeatTimer;
    private int electionTimeout;

    // Thread lists
    private List<AppendEntrySendThread> aeSendThreadList;
    private NodeRole nodeRole;

    public RaftNode(int port, int id, int num_peers) {
//        logger.setLevel(Level.INFO);
//        FileHandler fileHandler = null;
//        try {
//            fileHandler = new FileHandler("logfile.txt");
////            SimpleFormatter formatter = new SimpleFormatter();
////            fileHandler.setFormatter(formatter);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        fileHandler.setLevel(Level.INFO);
//        logger.addHandler(fileHandler);
//        logger.setUseParentHandlers(false);
        FileHandler fh = null;
        try {
            fh = new FileHandler("testlog.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.addHandler(fh);
        logger.setUseParentHandlers(false);
        SimpleFormatter formatter = new SimpleFormatter();
        fh.setFormatter(formatter);


        this.id = id;
        this.num_peers = num_peers;
        this.persistentState = new PersistentState();
        this.nodeRole = NodeRole.FOLLOWER;
        this.commitIndex = 0;
        this.lastApplied = 0;

        this.isHeartBeating = false;
        lib = new TransportLib(port, id, this);
        this.electionTimeout = Helper.RandomTimeout(MIN_ELECTION_TIMEOUT, MAX_ELECTION_TIMEOUT);

        if (VERBOSE) {
            System.out.println("Initialize RaftNode...");
            System.out.printf("[Random Election timeoput] Node %d, election timeout: %d ms.\n", id, electionTimeout);
        }
        logger.info("Initialize RaftNode...");
        logger.info(String.format("[Random Election timeoput] Node %d, election timeout: %d ms.\n", id, electionTimeout));
        checkHeartBeat();
    }

    public void checkHeartBeat() {
        logger.info(String.format("checkHeartBeat for Node %d", this.id));
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            if (nodeRole.equals(NodeRole.LEADER) || isHeartBeating) {
                isHeartBeating = false;
            } else {
                newElection();
            }
        }, 0, electionTimeout, TimeUnit.MILLISECONDS);
    }

    private void newElection() {
        persistentState.currentTerm++;
        if (VERBOSE){
            System.out.printf("[New election] by Node %d [role %s]" +
                    "at term %d.\n", id, nodeRole,persistentState.currentTerm);
            System.out.flush();
        }
        logger.info(String.format("[New election] by Node %d [role %s]" +
                "at term %d.\n", id, nodeRole,persistentState.currentTerm));

        synchronized (persistentState) {
            persistentState.votedFor = id;
            nodeRole = NodeRole.CANDIDATE;
        }

        // send out requestVote msg
        LogEntry lastLogEntry = persistentState.getLastEntry();
        RequestVoteArgs requestVoteArgs = new RequestVoteArgs(persistentState.currentTerm, id, lastLogEntry.index, lastLogEntry.term);
        byte[] byteReqVoteBody = toByteConverter(requestVoteArgs);

        int votesCounter = 1;
        // has to ge a candidate
        if (nodeRole.equals(NodeRole.CANDIDATE)){
            for (int i = 0; i < num_peers; i++) {
                if (i == id) continue;
                Message message = new Message(MessageType.RequestVoteArgs, id, i, byteReqVoteBody);
                Message response = null;

                try {
                    response = lib.sendMessage(message);
                } catch (RemoteException e) {
                    System.err.printf("[Remote Exception] Node %d fails to send RequestVote to Node %d at term %d.\n",
                            id, i, persistentState.currentTerm);
                    System.out.flush();
                } catch (ClassCastException e) {
                    e.printStackTrace();
                }

                if (response != null) {
                    RequestVoteReply reply = (RequestVoteReply) toObjectConverter(response.getBody());
                    if (reply.term > persistentState.currentTerm) {
                        refreshTerm(reply.term);
                        break;
                    }
                    if (reply.voteGranted){
                        votesCounter++;
                    }
                }
            }
        }

        if (VERBOSE){
            System.out.printf("[Counting Vote...]Node %d receives %d votes at term %d.\n",
                    id, votesCounter, persistentState.currentTerm);
            System.out.flush();
        }
        logger.info(String.format("[Counting Vote...]Node %d receives %d votes at term %d.\n",
                id, votesCounter, persistentState.currentTerm));
        if (nodeRole.equals(NodeRole.CANDIDATE) && votesCounter > num_peers / 2) {
            toLeaderState();
        }
    }


    @Override
    public synchronized StartReply start(int command) {
        synchronized (persistentState) {
            int replyindex;
            if (nodeRole.equals(NodeRole.LEADER)) {
                int oldIndex = persistentState.logEntries.size();
                LogEntry entry = new LogEntry(persistentState.currentTerm, oldIndex + 1, command);
                persistentState.logEntries.add(entry);
                if (VERBOSE){
                    System.out.printf("Node %d add %s from client, %s at index %d\n",
                            id, entry.toString(), nodeRole.toString(), oldIndex + 1);
                    System.out.flush();
                }

                logger.info(String.format("Node %d add %s from client, %s at index %d\n",
                        id, entry.toString(), nodeRole.toString(), oldIndex + 1));
                leaderSendAppendEntry();

                replyindex = oldIndex + 1;
            } else {
                replyindex = persistentState.getLastEntry().index;
            }
            StartReply startReply = new StartReply(replyindex, persistentState.currentTerm, nodeRole.equals(NodeRole.LEADER));
            return startReply;
        }
    }

    @Override
    public GetStateReply getState() {
        GetStateReply StateReply = new GetStateReply(persistentState.currentTerm, nodeRole.equals(NodeRole.LEADER));
        return StateReply;
    }

    /**
     * Send AppendEntry message to all the followers in parallel.
     */
    private void leaderSendAppendEntry() {
        int currentIndex = persistentState.getLastEntry().index;
        AppendEntryReceiveOperator commitOperator = new AppendEntryReceiveOperator(currentIndex);
        List<LogEntry> logCopy = new ArrayList<>(persistentState.logEntries);

        // stop AppendEntrySendThreads
        for (AppendEntrySendThread thread : aeSendThreadList) {
            thread.threadStop();
        }
        aeSendThreadList = new ArrayList<>();

        // create new thread for num_peers
        for (int i = 0; i < num_peers; i++) {
            if (i == id) continue;
            AppendEntrySendThread sendThread = new AppendEntrySendThread(i, currentIndex, persistentState.currentTerm, commitOperator, logCopy);
            aeSendThreadList.add(sendThread);

            AppendEntrySendThread sendThreadClone = new AppendEntrySendThread(sendThread);
//            (new AppendEntrySendThread(sendThread)).start();
            sendThreadClone.start();
//            aeSendThreadList.add(sendThread);
        }

        // wait for commit/timeout
        waitForCommit(commitOperator);

        if (VERBOSE){
            System.out.printf("[AppendEntry success commit] Leader Node %d committed.\n", id);
            System.out.flush();
        }
        logger.info(String.format("[AppendEntry success commit] Leader Node %d committed.\n", id));
    }

    public void waitForCommit(AppendEntryReceiveOperator commitOperator) {
        long waitUntil = System.currentTimeMillis() + MAX_ELECTION_TIMEOUT;
        do{
            // do nothing
        }
        while (!commitOperator.hasCommitted() && System.currentTimeMillis() < waitUntil);
    }

    /**
     * @param message the message this node receives.
     * @return The message this node should reply for the incoming message.
     */
    @Override
    public Message deliverMessage(Message message) {
        BuildReply buildReply = new BuildReply();
        if (message.getType().equals(MessageType.RequestVoteArgs)) {
            return buildReply.replyToReqVoteArgs(message);
        }

        if (message.getType().equals(MessageType.AppendEntriesArgs)) {
            return buildReply.replyToApdEntriesArgs(message);
        }

        return null;
    }

    private class BuildReply {
        public BuildReply() {
        }

        private Message replyToReqVoteArgs(Message message) {
            RequestVoteArgs request = (RequestVoteArgs) toObjectConverter(message.getBody());
            if (VERBOSE) {
                System.out.printf("Node %d receives RequestVote from %d. at term %d\n",
                        id, request.candidateId, persistentState.currentTerm);
                System.out.flush();
            }
            logger.info(String.format(
                    "Node %d receives RequestVote from %d. at term %d\n",
                    id, request.candidateId, persistentState.currentTerm));

            boolean voteGranted = false;
            if (request.term >= persistentState.currentTerm) {
                refreshTerm(request.term);
                if (VERBOSE) {
                    System.out.printf("Node %d receives RequestVote from %d. at term %d\n", id, request.candidateId, persistentState.currentTerm);
                    System.out.flush();
                }
                logger.info(String.format(
                        "Node %d receives RequestVote from %d. at term %d\n",
                        id, request.candidateId, persistentState.currentTerm));
                synchronized (persistentState.votedFor) {
                    if (persistentState.votedFor == Integer.MIN_VALUE) {
                        LogEntry lastEntry = persistentState.getLastEntry();
                        if (isGranted(request)) {
                            voteGranted = true;
                            persistentState.votedFor = request.candidateId;
                        }
                    }
                }
            }
            if (VERBOSE && voteGranted){
                System.out.printf("Node %d gives vote to Node %d. at term %d\n",
                        id, request.candidateId, persistentState.currentTerm);
                System.out.flush();
            }
            logger.info(String.format(
                    "Node %d gives vote to Node %d. at term %d\n",
                    id, request.candidateId, persistentState.currentTerm));
            return new Message(MessageType.RequestVoteReply, id, request.candidateId,
                    toByteConverter(new RequestVoteReply(persistentState.currentTerm, voteGranted)));
        }

        private boolean isGranted(RequestVoteArgs request) {
            LogEntry lastEntry = persistentState.getLastEntry();
            return (request.lastLogTerm == lastEntry.term && request.lastLogIndex >= lastEntry.index)
                    || (request.lastLogTerm > lastEntry.term);
        }

        private Message replyToApdEntriesArgs(Message message) {
            synchronized (persistentState) {
                AppendEntriesArgs appendEntriesArgs = (AppendEntriesArgs) toObjectConverter(message.getBody());
                boolean success;

                // VERBOSE message
                if (VERBOSE) {
                    if (appendEntriesArgs.entries.size() == 0) {
                        System.out.printf("[Normal HeartBeating] Node %d receives heartbeat from %d.\n",
                                id, appendEntriesArgs.leaderId);
                        System.out.flush();
                        logger.info(String.format(
                                "[Normal HeartBeating] Node %d receives heartbeat from %d.\n",
                                id, appendEntriesArgs.leaderId));
                    } else {
                        System.out.printf("[Receive append entry] term %d, Node %d <- append entry <- %d, for index %d. [role %s]\n",
                                persistentState.currentTerm,id, appendEntriesArgs.leaderId,
                                appendEntriesArgs.prevLogIndex + 1, nodeRole.toString());
                        System.out.flush();
                        logger.info(String.format(
                                "[Receive append entry] term %d, Node %d <- append entry <- %d, for index %d. [role %s]\n",
                                persistentState.currentTerm,id, appendEntriesArgs.leaderId,
                                appendEntriesArgs.prevLogIndex + 1, nodeRole.toString()));
                    }
                }

                if (appendEntriesArgs.term < persistentState.currentTerm) {
                    success = false;
                }
                else if (notIllegle(appendEntriesArgs)) {
                    success = false;
                    isHeartBeating = true;
                    refreshTerm(appendEntriesArgs.term);
                }
                else {
                    success = true;
                    isHeartBeating = true;
                    if (nodeRole.equals(NodeRole.CANDIDATE))
                        ToFollowerState();
                    refreshTerm(appendEntriesArgs.term);

                    if (!appendEntriesArgs.entries.isEmpty()) {
                        if (VERBOSE) {
                            System.out.printf("Success on Node %d.\n", id);
                            System.out.printf("Node %d at term %d receives append entry from %d at term %d for index %d. %s\n",
                                    id, persistentState.currentTerm, appendEntriesArgs.leaderId, appendEntriesArgs.term, appendEntriesArgs.prevLogIndex + 1,
                                    nodeRole.toString());
                            System.out.flush();
                            logger.info(String.format(
                                    "Success on Node %d at term %d receives append entry from %d at term %d for index %d. %s\n",
                                    id, persistentState.currentTerm, appendEntriesArgs.leaderId, appendEntriesArgs.term, appendEntriesArgs.prevLogIndex + 1,
                                    nodeRole.toString()));
                        }

                        LogEntry lastEntry = persistentState.getLastEntry();
                        while (lastEntry != null && lastEntry.index > appendEntriesArgs.prevLogIndex) {
                            if (VERBOSE) {
                                System.out.printf("Delete logEntries on index %d at Node %d. (prevLogIndex = %d)\n",
                                        lastEntry.index, id, appendEntriesArgs.prevLogIndex);
                                System.out.flush();
                                logger.info(String.format(
                                        "Delete logEntries on index %d at Node %d. (prevLogIndex = %d)\n",
                                        lastEntry.index, id, appendEntriesArgs.prevLogIndex));
                            }
                            persistentState.logEntries.remove(persistentState.logEntries.size() - 1);
                            lastEntry = persistentState.getLastEntry();
                        }

                        persistentState.logEntries.addAll(appendEntriesArgs.entries);
                    }

                    if (appendEntriesArgs.leaderCommit > commitIndex) {
                        synchronized(lastApplied){
                            lastApplied = commitIndex;
                        }
                        commitIndex = Integer.min(appendEntriesArgs.leaderCommit, persistentState.logEntries.size());
                        for (int i = lastApplied; i < commitIndex; i++) {
                            try {
                                if (VERBOSE) {
                                    System.out.printf("Node %d commits logEntries at %d (command = %d)\n", id, persistentState.logEntries.get(i).index, persistentState.logEntries.get(i).command);
                                    System.out.printf("Node %d logEntries: %s\n", id, persistentState.logEntries.toString());
                                    System.out.flush();
                                    logger.info(String.format(
                                            "Node %d logEntries: %s\n", id, persistentState.logEntries.toString()));
                                }
                                lib.applyChannel(new ApplyMsg(id, persistentState.logEntries.get(i).index, persistentState.logEntries.get(i).command, false, null));
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                lastApplied = commitIndex;
                AppendEntriesReply replyBody = new AppendEntriesReply(persistentState.currentTerm, success);
                byte[] byteReplyBody = toByteConverter(replyBody);
                Message replyMessage = new Message(MessageType.AppendEntriesReply, id, message.getSrc(),byteReplyBody);
                return replyMessage;
            }
        }

        private boolean notIllegle(AppendEntriesArgs request) {
            return (persistentState.logEntries.size() < request.prevLogIndex
                    ||
                    (persistentState.logEntries.size() > 0 && request.prevLogIndex > 0
                            && persistentState.logEntries.size() >= request.prevLogIndex
                            && persistentState.logEntries.get(request.prevLogIndex - 1).term != request.prevLogTerm));
        }
    }

    private void refreshTerm(int term) {
        synchronized (persistentState) {
            if (term > persistentState.currentTerm) {
                ToFollowerState();
                persistentState.currentTerm = term;
                persistentState.votedFor = Integer.MIN_VALUE;
            }
        }
    }

    private synchronized void toLeaderState() {
        nodeRole = NodeRole.LEADER;
        if (VERBOSE) {
            System.out.printf("Change to Leader: Term: %s; Node %d -> Leader.\n\n",
                    persistentState.currentTerm, id);
            System.out.flush();
            logger.info(String.format(
                    "Change to Leader: Term: %s; Node %d -> Leader.\n\n",
                    persistentState.currentTerm, id));
        }
        // Set up heart beat timer
        heartBeatTimer = new Timer();
        heartBeatTimerTask heartBeatTask = new heartBeatTimerTask();
        heartBeatTimer.scheduleAtFixedRate(heartBeatTask, 0, heartBeatFreq);

        // Re-initialization
        aeSendThreadList = new ArrayList<>();

        nextIndex = new Integer[num_peers];
        LogEntry lastEntry = persistentState.getLastEntry();
        Arrays.fill(this.nextIndex, lastEntry.getIndex() + 1);

        matchIndex = new Integer[num_peers];
        Arrays.fill(this.matchIndex, 0);

    }

    class heartBeatTimerTask extends TimerTask {
        @Override
        public void run() {
            leaderSendHeartBeats();
        }
    }

    private synchronized void ToFollowerState() {
        if (nodeRole.equals(NodeRole.LEADER) && this.heartBeatTimer != null) {
            this.heartBeatTimer.cancel();
            this.heartBeatTimer.purge();

            if(VERBOSE){
                System.out.printf("[Node %d become Follower]", id);
                System.out.flush();
                logger.info(String.format(
                        "[Node %d become Follower]", id));
            }
        }
        nodeRole = NodeRole.FOLLOWER;
        if (aeSendThreadList != null) {
            for (AppendEntrySendThread thread : aeSendThreadList) {
                thread.threadStop();
            }
        }
        this.aeSendThreadList = new ArrayList<>();
    }

    /**
     * Send heartbeat to all the followers.
     */
    private void leaderSendHeartBeats() {
        LogEntry lastLogEntry = persistentState.getLastEntry();
        AppendEntriesArgs appendEntriesArgs = new AppendEntriesArgs(persistentState.currentTerm, id, lastLogEntry.index,
                lastLogEntry.term, Arrays.asList(), commitIndex);
        byte[] messageBody = toByteConverter(appendEntriesArgs);
        for (int i = 0; i < num_peers; i++) {
            if (i == id) continue;
            try {
                Message reply = lib.sendMessage(new Message(MessageType.AppendEntriesArgs, id, i, messageBody));
                if (reply != null) {
                    AppendEntriesReply appendReply = (AppendEntriesReply) toObjectConverter(reply.getBody());
                    if (!appendReply.success)
                        refreshTerm(appendReply.term);
                }
            } catch (RemoteException e) {
                System.err.printf("[Remote Exception] Node %d fails to send AppendEntries to Node %d.\n", id, i);
            }
        }
    }

    private class AppendEntrySendThread extends Thread {
        private int followerId;
        private int currentIndex;
        private State state = RUNNABLE;
        private AppendEntryReceiveOperator commitOperator;
        private int counter = 0;
        private List<LogEntry> logCopy;
        private int currentTerm;

        private AppendEntrySendThread(AppendEntrySendThread aeSendThread) {
            this.followerId = aeSendThread.followerId;
            this.currentIndex = aeSendThread.currentIndex;
            this.commitOperator = aeSendThread.commitOperator;
            this.logCopy = aeSendThread.logCopy;
            this.currentTerm = aeSendThread.currentTerm;
        }

        private AppendEntrySendThread(int followerId, int currentIndex, int currentTerm, AppendEntryReceiveOperator commitOperator,
                                      List<LogEntry> logCopy) {
            this.followerId = followerId;
            this.currentIndex = currentIndex;
            this.commitOperator = commitOperator;
            this.logCopy = logCopy;
            this.currentTerm = currentTerm;
        }

        @Override
        public void run() {
            synchronized (nextIndex) {
                if (currentIndex < nextIndex[this.followerId]) {
                    nextIndex[this.followerId] = currentIndex;
                }
            }

            while (this.state == RUNNABLE && nodeRole.equals(NodeRole.LEADER)) {
                // copy entries
                List<LogEntry> entries = new ArrayList<>();
                int startIndex = 0;
                if(nextIndex[this.followerId] - 1 > 0){
                    startIndex = nextIndex[this.followerId] - 1;
                }
//                int startIndex = Math.max(0, nextIndex[this.followerId] - 1);
                entries.addAll(logCopy.subList(startIndex, currentIndex));

                // get prevLogTerm
                int prevLogTerm = 0;
                int prevPrevIndex = nextIndex[this.followerId] - 2;
                if (prevPrevIndex >= 0) {
                    prevLogTerm = logCopy.get(prevPrevIndex).getTerm();
                }

                // apply for new AppendEntriesArgs
                AppendEntriesArgs appendEntriesArgs = new AppendEntriesArgs(this.currentTerm, id,
                        nextIndex[this.followerId] - 1, prevLogTerm, entries, commitIndex);

                // apply for Message
                byte[] messageBody = toByteConverter(appendEntriesArgs);
                Message message = new Message(
                        MessageType.AppendEntriesArgs, id, followerId, messageBody);

                Message response = null;
                try {
                    response = lib.sendMessage(message);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                if (response == null) {
                    return;
                } else {
                    AppendEntriesReply reply = (AppendEntriesReply) toObjectConverter(response.getBody());
                    if (reply.isSuccess()) {
                        if (VERBOSE) {
                            System.out.printf("[AppendEntriesReply success] Term: %d, Node %d receives success from Node %d (%d times).\n",
                                    reply.term, id, this.followerId, ++counter);
                            System.out.flush();
                            logger.info(String.format(
                                    "[AppendEntriesReply success] Term: %d, Node %d receives success from Node %d (%d times).\n",
                                            reply.term, id, this.followerId, ++counter));
                        }

                        // comit Log
                        commitOperator.commitLog();

                        // update log index
                        increaseNextIndex(this.followerId);
                        return;
                    } else { // if reply not success
                        if (reply.term > currentTerm) {
                            refreshTerm(reply.term); // refresh Term
                        } else {
                            decreaseNextIndex(this.followerId);
                        }
                    }
                }
            }
        }

        public void start() {
            super.start();
        }

        private void threadStop() {
            state = TERMINATED;
        }

    }

    private void increaseNextIndex(int id) {
        synchronized (nextIndex) {
            nextIndex[id]++; // add log
        }
    }

    private void decreaseNextIndex(int id) {
        synchronized (nextIndex) {
            nextIndex[id]--; // roll back
        }
    }

    private class AppendEntryReceiveOperator{
        private int indexOnDecide;
        private int recvCounter = 1;
        private boolean hasCommitted = false;

        private AppendEntryReceiveOperator(int index) {
            this.indexOnDecide = index;
            synchronized(lastApplied){
                lastApplied = commitIndex;
            }
        }

        private void commitLog() {
            synchronized (this) {
                if (hasCommitted) {
                    return;
                }

                recvCounter++;

                // receive majority 1/2 votes
                if (recvCounter > num_peers / 2) {
                    // refresh commit Index
                    if (VERBOSE) {
                        System.out.printf("[Reach Agreement] Node %d " +
                                        "received success for %d times \n",
                                id, recvCounter);
                        System.out.flush();
                    }
                    // receive Agreement
                    for (int i = lastApplied; i < indexOnDecide; ++i) {

                        ApplyMsg applymsg = new ApplyMsg(id, persistentState.logEntries.get(i).index,
                                persistentState.logEntries.get(i).command, false, null);
                        try {
                            lib.applyChannel(applymsg);
                        } catch (RemoteException e) {
                            System.err.printf("[Error] Node %d " +
                                            "failed to commit logEntries at index %d " +
                                            "(command = %d)[%s]\n",
                                    id, persistentState.logEntries.get(i).index,
                                    persistentState.logEntries.get(i).command, nodeRole.toString());
                            e.printStackTrace();
                        }
                        if (VERBOSE) {
                            System.out.printf("[Commit log] Node %d " +
                                            "commits logEntries at %d " +
                                            "(command = %d)[%s]\n",
                                    id, persistentState.logEntries.get(i).index,
                                    persistentState.logEntries.get(i).command, nodeRole.toString());
                            System.out.flush();
                        }
                    }

                    commitIndex = indexOnDecide;
                    lastApplied = commitIndex;
                    hasCommitted = true;
                }
            }
        }

        private Boolean hasCommitted() {
            synchronized (this) {
                if (VERBOSE) {
                    System.out.printf("[Reach Agreement] Node %d has successfully committed \n", id);
                    System.out.flush();
                }
                return hasCommitted;
            }
        }
    }

    public static void main(String args[]) throws Exception {
        if (args.length != 3) throw new Exception("Need 2 args: <port> <id> <num_peers>");
        //new usernode
        try {
            RaftNode UN = new RaftNode(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        } catch (Exception | Error e) {
            e.printStackTrace();
        }
    }
}
