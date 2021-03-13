import lib.*;

import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.State.RUNNABLE;
import static java.lang.Thread.State.TERMINATED;
import static lib.Helper.*;
import static lib.Helper.toByteConverter;

public class RaftNode implements MessageHandling {

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

        this.id = id;
        this.num_peers = num_peers;
        this.persistentState = new PersistentState();
        this.nodeRole = NodeRole.FOLLOWER;
        this.commitIndex = 0;
        this.lastApplied = 0;
        this.nextIndex = new Integer[num_peers];
        Arrays.fill(this.nextIndex,1);
        this.matchIndex = new Integer[num_peers];
        Arrays.fill(this.matchIndex,0);

        this.isHeartBeating = false;
        lib = new TransportLib(port, id, this);
        this.electionTimeout = Helper.RandomTimeout(MIN_ELECTION_TIMEOUT, MAX_ELECTION_TIMEOUT);

        debugLog("Initialize RaftNode...");
        debugLog(String.format(
                "[Random Election timeoput] Node %d, election timeout: %d ms.\n", id, electionTimeout
        ));

        checkHeartBeat();
    }

    public void checkHeartBeat() {
        debugLog(String.format(
                "checkHeartBeat for Node %d", this.id
        ));

        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            if (nodeRole.equals(NodeRole.LEADER) || isHeartBeating ) {
                isHeartBeating = false;
            }
            else {
                newElection();
            }
        }, 0, electionTimeout, TimeUnit.MILLISECONDS);
    }

    private void newElection() {
        persistentState.currentTerm++;
        debugLog(String.format(
                "[New election] by Node %d [role %s] at term %d.\n",
                id, nodeRole,persistentState.currentTerm
        ));

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
                if (i == id) continue; // do not send message to oneself
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
                        debugLog(String.format(
                                "[is Voting...]Node %d receives one vote at term %d.\n",
                                id, persistentState.currentTerm
                        ));
                    }
                }
            }
        }

        debugLog(String.format(
                "[Counting Vote...]Node %d receives %d votes at term %d.\n",
                id, votesCounter, persistentState.currentTerm
        ));

        if (nodeRole.equals(NodeRole.CANDIDATE) && votesCounter > num_peers / 2) {
            toLeaderState();
        }
    }


    @Override
    public synchronized StartReply start(int command) {
        synchronized (persistentState) {
            if (nodeRole.equals(NodeRole.LEADER)) {

                LogEntry entry = persistentState.addCommandToEntry(command);
                int entrylength = persistentState.getStaticEntryLength();

                debugLog(String.format(
                        "Node %d add %s from client, %s at index %d\n",
                        id, entry.toString(), nodeRole.toString(), entrylength
                ));

                leaderSendAppendEntry();

                StartReply startReply = new StartReply(entrylength, persistentState.currentTerm, true);
                return startReply;
            } else { // not a leader
                return new StartReply(0, 0, false);
            }

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

        // stop AppendEntrySendThreads
        cleanThreadList();

        // create new thread for num_peers
        for (int i = 0; i < num_peers; i++) {
            if (i == id) continue;
            AppendEntrySendThread sendThread = new AppendEntrySendThread(
                    i, currentIndex, persistentState.currentTerm,
                    commitOperator, persistentState.logEntries);
            aeSendThreadList.add(sendThread);

            AppendEntrySendThread sendThreadClone = new AppendEntrySendThread(sendThread);
            sendThreadClone.start();
        }

        // wait for commit/timeout
        // https://stackoverflow.com/questions/7547255/java-getting-time-with-currenttimemillis-as-a-long-how-do-i-output-it-with-x
        waitForCommit(commitOperator);

        if(commitOperator.hasCommitted()){
            debugLog(String.format(
                    "[AppendEntry success commit] Leader Node %d committed.\n", id
            ));
        }else{// still not committed, timeout
            debugLog(String.format(
                    "[Leader TimeOut] Leader Node %d timeout.\n", id
            ));
        }

    }

    public void waitForCommit(AppendEntryReceiveOperator commitOperator) {
        long waitUntil = System.currentTimeMillis() + MAX_ELECTION_TIMEOUT;
        do{
            debugLog(".");
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
        if (message.getType() == MessageType.RequestVoteArgs) {
            return buildReply.replyToReqVoteArgs(message);
        }

        if (message.getType() == MessageType.AppendEntriesArgs) {
            return buildReply.replyToApdEntriesArgs(message);
        }

        return null;
    }

    private class BuildReply {
        public BuildReply() {
        }

        private Message replyToReqVoteArgs(Message message) {
            RequestVoteArgs request = (RequestVoteArgs) toObjectConverter(message.getBody());
            // has to be a candidate

            debugLog(String.format(
                    "[receive RequestVote] Node %d receives RequestVote from %d. at term %d\n",
                    id, request.candidateId, persistentState.currentTerm
            ));

            boolean voteGranted = false;
            if (request.term >= persistentState.currentTerm) {
                refreshTerm(request.term);
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
            else{
                debugLog(String.format(
                        "[Outdated] Node %d: recv Request Votes Reply from prev term.\n",id
                ));
            }

            if (voteGranted){
                debugLog(String.format(
                        "[Vote For] Node %d gives vote to Node %d. at term %d\n",
                        id, request.candidateId, persistentState.currentTerm
                ));
            }

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

                // debug message
                if (VERBOSE) {
                    if (appendEntriesArgs.entries.size() == 0) { // is heartbest
                        debugLog(String.format(
                                "[Receive HeartBeat] Node %d received from Node %d.\n",
                                id, appendEntriesArgs.leaderId
                                ));
                    } else { // is Append Entry
                        debugLog(String.format(
                                "[Receive append entry] term %d, Node %d <- append entry <- %d, for index %d. [role %s]\n",
                                persistentState.currentTerm,id, appendEntriesArgs.leaderId,
                                appendEntriesArgs.prevLogIndex + 1, nodeRole.toString()
                        ));
                    }
                }


                success = false;
                AppendEntriesReply replyBody = null;

                // all server #2, change from leader to follower
                if(appendEntriesArgs.term >= persistentState.currentTerm) {
                    isHeartBeating = true;
                    refreshTerm(appendEntriesArgs.term);

                    if (isLegal(appendEntriesArgs)) {
                        success = true;
                        if (nodeRole.equals(NodeRole.CANDIDATE)) {
                            ToFollowerState();
                        }
                        refreshTerm(appendEntriesArgs.term);

                        if (!appendEntriesArgs.entries.isEmpty()) {
                            debugLog(String.format(
                                    "[Success from AppendEntry] on Node %d at term %d receives append entry from %d at term %d for index %d. %s\n",
                                    id, persistentState.currentTerm, appendEntriesArgs.leaderId, appendEntriesArgs.term, appendEntriesArgs.prevLogIndex + 1,
                                    nodeRole.toString()
                            ));

                            persistentState.removeEntriesFrom(appendEntriesArgs.prevLogIndex);
                            debugLog(String.format(
                                        "[Delete logEntries] Node %d, from index %d\n",
                                    appendEntriesArgs.prevLogIndex, id, appendEntriesArgs.prevLogIndex
                                ));
//
                            persistentState.addAllLogEntries(appendEntriesArgs.entries);
                        }

                        // #5
                        if (appendEntriesArgs.leaderCommit > commitIndex) {
                            synchronized(lastApplied){
                                lastApplied = commitIndex;
                            }
                            commitIndex = Math.min(appendEntriesArgs.leaderCommit, persistentState.logEntries.size());
                            // for all server #1
                            for (int i = lastApplied; i < commitIndex; i++) {
                                try {
                                    lib.applyChannel(new ApplyMsg(id, persistentState.logEntries.get(i).index, persistentState.logEntries.get(i).command, false, null));
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                }
                                debugLog(String.format(
                                        "[commits logEntry]Node %d %s commits logEntries at %d, command = %d\n",
                                        id, nodeRole, persistentState.logEntries.get(i).index, persistentState.logEntries.get(i).command
                                ));
                            }

                            lastApplied = commitIndex;
                        }
                    }
                    else{ // illeal, output debug information
                        debugLog(String.format(
                                "[AppendEntry rejected] Node %d reject AppendEntry,out of range\n", id
                        ));
                    }
                }

                replyBody = new AppendEntriesReply(persistentState.currentTerm, success);
                byte[] byteReplyBody = toByteConverter(replyBody);
                Message replyMessage = new Message(MessageType.AppendEntriesReply, id, message.getSrc(),byteReplyBody);
                return replyMessage;
            }
        }

        private boolean isLegal(AppendEntriesArgs request) {
            return ((persistentState.logEntries.size() >= request.prevLogIndex)  &&
                    (persistentState.logEntries.size() == 0 || request.prevLogIndex == 0 || persistentState.logEntries.get(request.prevLogIndex - 1).term == request.prevLogTerm));
        }
    }

    private void refreshTerm(int term) {
        synchronized (persistentState) {
            if (term > persistentState.currentTerm) {
                ToFollowerState();
                persistentState.currentTerm = term;
                persistentState.votedFor = Integer.MIN_VALUE;
                debugLog(String.format(
                        "[refresh Term]Node %d Term updated to %d\n",
                        id, persistentState.currentTerm
                ));
            }
        }
    }

    private synchronized void toLeaderState() {
        nodeRole = NodeRole.LEADER;
        debugLog(String.format(
                "Change to Leader: Term: %s; Node %d -> Leader.\n",
                persistentState.currentTerm, id
        ));
        // Set up heart beat timer
        heartBeatTimer = new Timer();
        heartBeatTimerTask heartBeatTask = new heartBeatTimerTask();
        heartBeatTimer.scheduleAtFixedRate(heartBeatTask, 0, heartBeatFreq);

        // Re-initialization
        cleanThreadList();
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
        }
        nodeRole = NodeRole.FOLLOWER;
        debugLog(String.format(
                "[Node %d become Follower] ", id
        ));
        cleanThreadList();
    }

    private synchronized void cleanThreadList(){
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
            debugLog(String.format(
                    "[Send Heartbeat] Leader %d send heartbeat to Node %d at term %d\n",
                    id, i , persistentState.currentTerm
            ));

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
                                      List<LogEntry> NodeLog) {
            this.followerId = followerId;
            this.currentIndex = currentIndex;
            this.commitOperator = commitOperator;
            this.logCopy = new ArrayList<LogEntry>(NodeLog); // apply for a new list for static visit
            this.currentTerm = currentTerm;
        }

        @Override
        public void run() {
            synchronized (nextIndex) {
                if (currentIndex < nextIndex[this.followerId]) {
                    nextIndex[this.followerId] = currentIndex;
                }
            }
            // should be runnable and role is leader
            while (this.state == RUNNABLE && nodeRole.equals(NodeRole.LEADER)) {
                // get current static entries
                int startIndex = 0;
                if(nextIndex[this.followerId] - 1 > 0){
                    startIndex = nextIndex[this.followerId] - 1;
                }
                List<LogEntry> entries = persistentState.getStaticEntriesBetween(startIndex, currentIndex);

                // get prevLogTerm
                int prevLogTerm = 0;
                int prevPrevIndex = nextIndex[this.followerId] - 2;
                if (prevPrevIndex >= 0) {
                    prevLogTerm = logCopy.get(prevPrevIndex).getTerm();
                }

                // build AppendEntriesArgs
                AppendEntriesArgs appendEntriesArgs = new AppendEntriesArgs(this.currentTerm, id,
                        nextIndex[this.followerId] - 1, prevLogTerm, entries, commitIndex);

                // convert Message body
                byte[] messageBody = toByteConverter(appendEntriesArgs);
                Message message = new Message(
                        MessageType.AppendEntriesArgs, id, followerId, messageBody);

                debugLog(String.format(
                        "[AppendEntry Sending...] {Thread-%d} term %d, Node %d[%s] ->  node %d: index %d, length %d\n",
                        currentThread().getId(),currentTerm, id, nodeRole.toString(),this.followerId, appendEntriesArgs.prevLogIndex+1, appendEntriesArgs.entries.size()
                ));

                // build message
                Message response = null;
                try {
                    response = lib.sendMessage(message);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                // receive Append Entry Reply
                if (response == null || !nodeRole.equals(NodeRole.LEADER)) {
                    return;
                }
                else {
                    AppendEntriesReply reply = (AppendEntriesReply) toObjectConverter(response.getBody());
                    if (reply.isSuccess()) {
                        debugLog(String.format(
                                "[AppendEntriesReply success] {Thread-%d} Term: %d, Node %d receives success from Node %d (%d times).\n",
                                currentThread().getId(),reply.term, id, this.followerId, ++counter
                        ));

                        // comit Log
                        commitOperator.commitLog();

                        // update log index
                        increaseNextIndex(this.followerId);
                        updateMatchIndex(this.followerId, nextIndex[followerId]-1);

                        return;
                    }
                    else { // rejected
                        if (reply.term > currentTerm) {
                            refreshTerm(reply.term); // refresh Term
                        } else {
                            decreaseNextIndex(this.followerId);
                        }
                    }
                }

            }
        }

        @Override
        public long getId() {
            return super.getId();
        }

        public void start() {
            super.start();
        }

        private void threadStop() {
            state = TERMINATED;
        }

        private void increaseNextIndex(int id) {
            synchronized (nextIndex) {
                nextIndex[id]++; // add log
                debugLog(String.format(
                        "[nextIndex++] {Thread-%d} Node %d increase NextIndex to .%d\n",
                         currentThread().getId(),id, nextIndex[id]
                ));
            }
        }

        private void decreaseNextIndex(int id) {
            synchronized (nextIndex) {
                nextIndex[id]--; // roll back
                debugLog(String.format(
                        "[nextIndex++] {Thread-%d} Node %d increase NextIndex to .%d\n",
                        currentThread().getId(),id, nextIndex[id]
                ));
            }
        }

        private void updateMatchIndex(int id, int content) {
            synchronized (matchIndex) {
                matchIndex[id] = content;
                debugLog(String.format(
                        "[matchIndex renew] {Thread-%d} Node %d renew matchIndex to .%d\n",
                        currentThread().getId(),id, content
                ));
            }
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

                recvCounter++;

                // receive majority 1/2 votes
                if (recvCounter > num_peers / 2) {
                    // commit and apply
                    debugLog(String.format(
                            "[Reach Agreement] Node %d received success for %d times \n",
                            id, recvCounter
                    ));
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
                        debugLog(String.format(
                                "[Commit log] Node %d %s commits logEntries at %d, command = %d\n",
                                id, nodeRole.toString(), persistentState.logEntries.get(i).index,
                                persistentState.logEntries.get(i).command
                        ));
                    }

                    commitIndex = indexOnDecide;
                    lastApplied = commitIndex;
                    hasCommitted = true;
                }
            }
        }

        private Boolean hasCommitted() {
            synchronized (this) {
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
