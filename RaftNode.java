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
    private NodeRole nodeRole;

    // node construction Members
    private int id;
    private static TransportLib lib;
    private int num_peers;

    //Persistent State for all servers
    // include currentTerm, votesFor and log[]
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


    /**
     * construction function for RaftNode
     * <p>
     *     role start by <code>FOLLOWER</code>, <code>commitIndex</code> initialized to 0,<code>LastApplied</code> initialized to 0,
     *     <code>nextIndex[]</code> initialized to 1(leader last log index+1), <code>matchIndex[]</code> initialized to 0
     *     every node has a random election timepout, generated between <code>MIN_ELECTION_TIMEOUT</code> and <code>MAX_ELECTION_TIMEOUT</code>
     *     the time range can be set in <code>lib/Helper</code>
     *     The FOLLOWER has no heart beat
     * <p>
     *     After constructing and initializing a RaftNode, start check current leader's heartbeat
     * @param port the prot to start service
     * @param id the id of the number
     * @param num_peers total number of the nodes
     */
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

        /* start chcek current Heart Beat, if no heartbeat reached within one's time out,
           start to elect itself */
        checkHeartBeat();
    }

    /**
     * <p>
     *     start chcek current Heart Beat, if no heartbeat reached within one's time out,
     *     start to elect itself
     * </p>
     * <p>
     *     reference:<a href="https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ScheduledExecutorService.html"></a>
     *     Creates and executes a periodic action that becomes enabled first after the given initial delay,
     *     and subsequently with the given period; that is executions will commence after initialDelay then initialDelay+period,
     *     then initialDelay + 2 * period, and so on. If any execution of the task encounters an exception, subsequent executions are suppressed.
     *     Otherwise, the task will only terminate via cancellation or termination of the executor.
     *     If any execution of this task takes longer than its period, then subsequent executions may start late, but will not concurrently execute.
     *     usage:
     *     <code>
     *         ScheduledFuture scheduleAtFixedRate(Runnable command,
     *                                               long initialDelay,
     *                                               long period,
     *                                               TimeUnit unit)
     *     </code>
     * </p>
     */
    public void checkHeartBeat() {
        debugLog(String.format(
                "checkHeartBeat for Node %d", this.id
        ));

        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            if (nodeRole.equals(NodeRole.LEADER) || isHeartBeating ) {
                synchronized (this) {
                    isHeartBeating = false;
                }
            }
            else {
                /*if election timeout elapses, statr new election*/
                newElection();
            }
        }, 0, electionTimeout, TimeUnit.MILLISECONDS);
    }

    /**
     * start new Election
     * Role: FOLLOWER or CANDIDATE
     * invoke election and nominate itself as a leader
     * each team starts with a election,
      */
    public void newElection() {
        /* candidate(&sect;5.2) practice 1: Increment current Term */
        persistentState.currentTerm++;
        debugLog(String.format(
                "[New election] by Node %d [role %s] at term %d.\n",
                id, nodeRole,persistentState.currentTerm
        ));

        /* candidate(&sect;5.2) practice 2: vote for self;
           VoteFor indicates the node itself
           Set the current Node Role to CANDIDATE */
        synchronized (persistentState) {
            persistentState.votedFor = id;
            nodeRole = NodeRole.CANDIDATE;
        }

        /* candidate(&sect;5.2) practice 4:
        requestVote RPC to all other servers */
        LogEntry lastLogEntry = persistentState.getLastEntry();
        RequestVoteArgs requestVoteArgs = new RequestVoteArgs(persistentState.currentTerm, id, lastLogEntry.index, lastLogEntry.term);
        byte[] byteReqVoteBody = toByteConverter(requestVoteArgs);

        int votesCounter = 1;

        if (nodeRole.equals(NodeRole.CANDIDATE)){
            for (int i = 0; i < num_peers; i++) {
                if (i == id) continue; // do not send message to oneself

                /* generate <code>RequestVoteArgs</code> Message*/
                Message message = new Message(MessageType.RequestVoteArgs, id, i, byteReqVoteBody);
                Message response = null;

                try {
                    /* transmitting Layer */
                    response = lib.sendMessage(message);
                } catch (RemoteException e) {
                    System.err.printf("[Remote Exception] Node %d fails to send RequestVoteArgs to Node %d at it's term %d.\n",
                            id, i, persistentState.currentTerm);
                    System.out.flush();
                } catch (ClassCastException e) {
                    e.printStackTrace();
                }
                /* deal with <code>RequestVoteReply</code>*/
                if (response != null) {
                    /* strip out the <code>byte[]</code> body, convert to <code>Object</code>*/
                    RequestVoteReply reply = (RequestVoteReply) toObjectConverter(response.getBody());
                    /* if RPC response contains term T > currentTerm
                       set CurrentTerm = T, convert to follower*/
                    if (reply.term > persistentState.currentTerm) {
                        refreshTerm(reply.term);
                        break;
                    }
                    /* if received one vote*/
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
        /* candidate(&sect;5.2)
         * practice 5: if votes received from majority if servers, become leader
         */
        if (nodeRole.equals(NodeRole.CANDIDATE) && votesCounter > num_peers / 2) {
            toLeaderState();
        }
    }


    /**
     * if command received from client, append entry to local log,
     * respond after entry entry applied to state machine(&sect;5.3)
     * @param command the command to append.
     * @return StartReply
     */
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

                /* send AppendEntries to all other server */
                leaderSendAppendEntry();

                StartReply startReply = new StartReply(entrylength, persistentState.currentTerm, true);
                return startReply;
            } else { // not a leader
                return new StartReply(0, 0, false);
            }

        }
    }

    /**
     * Construct a get state packet with given term and leader.
     * @return GetStateReply
     */
    @Override
    public GetStateReply getState() {
        GetStateReply StateReply = new GetStateReply(persistentState.currentTerm, nodeRole.equals(NodeRole.LEADER));
        return StateReply;
    }

    /**
     * Send AppendEntry message to all the followers in parallel.
     */
    public void leaderSendAppendEntry() {
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

    /**
     * do nothing until committed or timeout
     * @param commitOperator the AppendEntryReceiveOperator to operate
     */
    public void waitForCommit(AppendEntryReceiveOperator commitOperator) {
        long waitUntil = System.currentTimeMillis() + MAX_ELECTION_TIMEOUT;
        do{
            debugLog(".");
        }
        while (!commitOperator.hasCommitted() && System.currentTimeMillis() < waitUntil);
    }

    /**
     * deliver Message in response to AppendEntries RPC or RequestVote RPC
     * <ol>
     *     <li>if is a AppendEntries RPC, reply AppendEntriesReply Message</li>
     *     <li>if is a RequestVote RPC, reply AppendEntriesReply Message</li>
     * </ol>
     *     Wrap all the receiver implementation in to inner class <code>BuildReply</code>
     *     see <code>BuildReply</code> for detailed information
     *
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

    /**
     * receiver implementations for ReqVoteArgs and AppendEntriesArgs
     * This inner class is a combination of Receiver Implementation for
     * <ol>
     *     <li>AppendEntries RPC (&sect;5.1  &sect;5.3)</li>
     *     <li>RequestVote RPC (&sect;5.1  &sect;5.2  &sect;5.4)</li>
     * </ol>
     */
    public class BuildReply {
        public BuildReply() {
        }

        /**
         * Receiver implementation to RequestVote RPC(&sect;5.1  &sect;5.2  &sect;5.4)
         * @param message Message carring RequestVoteArgs(term,candidateId,lastLogIndex,lastLogTerm)
         * @return Message carrying RequestVoteReply(term,voteGranted)
         */
        public Message replyToReqVoteArgs(Message message) {
            RequestVoteArgs request = (RequestVoteArgs) toObjectConverter(message.getBody());
            // has to be a candidate

            debugLog(String.format(
                    "[receive RequestVote] Node %d receives RequestVote from %d. at term %d\n",
                    id, request.candidateId, persistentState.currentTerm
            ));

            boolean voteGranted = false;

            if (request.term >= persistentState.currentTerm) {
                /* for all server, update Term (&sect;5.1)*/
                refreshTerm(request.term);
                synchronized (persistentState.votedFor) {
                    /* if meet all GrantVote requirements(&sect;5.2  &sect;5.4)*/
                        if (requirementsToGrantVote(request)) {
                            /* then Grant Vote*/
                            voteGranted = true;
                            persistentState.votedFor = request.candidateId;
                        }
                }
            }
            else{
                /* if term < currentTerm, reply voteGranted = false(&sect;5.1)*/
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

            /* pack voteGranted information and form RequestVoteReply Message*/
            return new Message(MessageType.RequestVoteReply, id, request.candidateId,
                    toByteConverter(new RequestVoteReply(persistentState.currentTerm, voteGranted)));
        }

        /***
         * The judgement for setting VoteGranted, if the candidate's log is at least up-to-date as local og(&sect;5.2  &sect;5.4)
         * <p>
         *     if VoteFor is null or candidateId
         *     and if Candidate's log is at least as up-to-date as receiver's log
         *     then voteGranted == true
         * </p>
         * @param request to make judgement
         * @return true if vote for the candidate, false if not
         */
        public boolean requirementsToGrantVote(RequestVoteArgs request) {
            LogEntry lastEntry = persistentState.getLastEntry();
            return  (persistentState.votedFor == Integer.MIN_VALUE || persistentState.votedFor == request.candidateId)
                    && ((request.lastLogTerm == lastEntry.term && request.lastLogIndex >= lastEntry.index)
                    || (request.lastLogTerm > lastEntry.term));
        }

        /**
         * Receiver implementation to AppendEntries RPC(&sect;5.1  &sect;5.3)
         * the AppendEntries can be a Leader Log list or empty(heartbeat)
         * @param message Message carring AppendEntriesArgs(term,leaderID, prevLogIndex, prevLogTerm, entries[], leaderCommit)
         * @return Message carrying AppendEntriesReply(term,success)
         */
        public Message replyToApdEntriesArgs(Message message) {
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

                /* the sefault success flag is false, do not change unless meet all requirements*/
                success = false;
                AppendEntriesReply replyBody = null;

                /* Rule AppendEntries RPC - Receiver Implementation - #1: success = false; if term < persistentState.currentTerm */
                if(appendEntriesArgs.term >= persistentState.currentTerm) {
                    isHeartBeating = true;
                    refreshTerm(appendEntriesArgs.term);

                    /* if the requirements for success are all met, set success to true*/
                    if (isLegal(appendEntriesArgs)) {
                        success = true;
                        /* Rule all server #2(&sect;5.1):if term refreshed, change from leader to follower*/
                        if (nodeRole.equals(NodeRole.CANDIDATE)) {
                            ToFollowerState();
                        }
                        refreshTerm(appendEntriesArgs.term);

                        /* if is a Entry List */
                        if (!appendEntriesArgs.entries.isEmpty()) {
                            debugLog(String.format(
                                    "[Success receive AppendEntry] on Node %d at term %d receives append entry from %d at term %d for index %d. %s\n",
                                    id, persistentState.currentTerm, appendEntriesArgs.leaderId, appendEntriesArgs.term, appendEntriesArgs.prevLogIndex + 1,
                                    nodeRole.toString()
                            ));

                            /* Rule AppendEntries RPC - Receiver Implementationreply - #3,#4
                               forcefully append any new entries not already in th log
                             */
                            persistentState.removeEntriesFrom(appendEntriesArgs.prevLogIndex);
                            debugLog(String.format(
                                        "[Rewrite logEntries - DELETE] Node %d, from index %d\n",
                                    id, appendEntriesArgs.prevLogIndex
                                ));
                            persistentState.addAllLogEntries(appendEntriesArgs.entries);
                            debugLog(String.format(
                                    "[Rewrite logEntries - ADD] Node %d, from index %d\n",
                                    id, appendEntriesArgs.prevLogIndex
                            ));
                        }

                        /* Rule AppendEntries RPC - Receiver Implementationreply - #5:
                          if leaderCommit > commitIndex, set commitIndex=min(leaderCommit, index of last new entry)
                         */
                        if (appendEntriesArgs.leaderCommit > commitIndex) {
                            /* Rule State - Volatile state on all server:
                            update highest log entry index */
                            synchronized(lastApplied){
                                lastApplied = commitIndex;
                            }
                            commitIndex = Math.min(appendEntriesArgs.leaderCommit, persistentState.logEntries.size());
                            /* Rule for Servers - All Servers - #1:
                            if commitIndex > lastApplied, increment lastApplied, apply log[lastApplied] to state machine(&sect;5.3) */
                            for (int i = lastApplied; i < commitIndex; i++) {
                                try {
                                    /* the wirteup indicates not need to implement snapshotting, set useSnapshot to false ans snapshot to null */
                                    lib.applyChannel(
                                            new ApplyMsg(
                                                    id, persistentState.logEntries.get(i).index,
                                                    persistentState.logEntries.get(i).command, false, null
                                            ));
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                }
                                debugLog(String.format(
                                        "[Apply Log to State Machine]Node %d %s apply log[%d] to state machine at Term %d, command = %d\n",
                                        id, nodeRole, persistentState.logEntries.get(i).index , persistentState.currentTerm, persistentState.logEntries.get(i).command
                                ));
                            }
                            lastApplied = commitIndex;
                        }
                    }
                    else{
                        /* Rule AppendEntries RPC - Receiver Implementationreply - #3:
                         forcefully delete conflicted entries and all the follow it
                         */
                        persistentState.removeEntriesFrom(appendEntriesArgs.prevLogIndex);
                        debugLog(String.format(
                                "[AppendEntry rejected] Node %d reject AppendEntry,out of range\n", id
                        ));
                    }
                }

                /* form AppendEntriesReply Message carrying success*/
                replyBody = new AppendEntriesReply(persistentState.currentTerm, success);
                byte[] byteReplyBody = toByteConverter(replyBody);
                Message replyMessage = new Message(MessageType.AppendEntriesReply, id, message.getSrc(),byteReplyBody);
                return replyMessage;
            }
        }

        /**
         * <p>
         *     AppendEntries RPC - Receiver Implementation - #3:
         *     check for seeting <code>success</code>
         *     reply <code>false</code> if log does not contain an entry at <code>prevLogIndex</code> whose term matches <code>prevLogTerm</code>(&sect; 5.3)
         *     reply <code>false</code> if exiting entry conflicts with a new one(&sect; 5.3)
         * </p>
         * @param request AppendEntriesArgs request for checking
         * @return true if is Legal, false if conflicted
         */
        public boolean isLegal(AppendEntriesArgs request) {
            return ((persistentState.logEntries.size() >= request.prevLogIndex)  &&
                    (persistentState.logEntries.size() == 0 || request.prevLogIndex == 0 || persistentState.logEntries.get(request.prevLogIndex - 1).term == request.prevLogTerm));
        }
    }

    /**
     * if RPC response contains term T &gt; currentTerm
     * set CurrentTerm = T, convert to follower
     * @param term term in request or response
     */
    public void refreshTerm(int term) {
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

    /**
     * LEADERS initialization
     */
    public synchronized void toLeaderState() {
        nodeRole = NodeRole.LEADER;
        debugLog(String.format(
                "Change to Leader: Term: %s; Node %d -> Leader.\n",
                persistentState.currentTerm, id
        ));
        /* Set up heart beat timer
           repeat during idle periods to prevent election timeouts */
        heartBeatTimer = new Timer();
        heartBeatTimerTask heartBeatTask = new heartBeatTimerTask();
        heartBeatTimer.scheduleAtFixedRate(heartBeatTask, 0, heartBeatFreq);

        /* Reinitialized after election
        see State - Volatile state on leaders*/
        cleanThreadList();
        nextIndex = new Integer[num_peers];
        LogEntry lastEntry = persistentState.getLastEntry();
        Arrays.fill(this.nextIndex, lastEntry.getIndex() + 1);
        matchIndex = new Integer[num_peers];
        Arrays.fill(this.matchIndex, 0);

    }

    /**
     * extends TimerTask abstract class for leader sending heartBeats
     */
    public class heartBeatTimerTask extends TimerTask {
        @Override
        public void run() {
            leaderSendHeartBeats();
        }
    }

    /**
     * Send heartbeat(Empty AppendEntried RPCs) to all servers.
     */
    public void leaderSendHeartBeats() {
        LogEntry lastLogEntry = persistentState.getLastEntry();
        /*generate empty AppendEntried, entries = Collections.emptyList()*/
        AppendEntriesArgs appendEntriesArgs = new AppendEntriesArgs(persistentState.currentTerm, id, lastLogEntry.index,
                lastLogEntry.term, Collections.emptyList(), commitIndex);
        byte[] messageBody = toByteConverter(appendEntriesArgs);

        /*send to all servers except for self*/
        for (int i = 0; i < num_peers; i++) {
            if (i == id) continue;
            debugLog(String.format(
                    "[Send Heartbeat] Leader %d send heartbeat to Node %d at term %d , (next %d match %d)\n",
                    id, i , persistentState.currentTerm,nextIndex[i],matchIndex[i]
            ));

            try {
                Message reply = lib.sendMessage(new Message(MessageType.AppendEntriesArgs, id, i, messageBody));
                if (reply != null) {
                    AppendEntriesReply appendReply = (AppendEntriesReply) toObjectConverter(reply.getBody());

                    /* if any of the followers has a newer term see Receiver Implementation #1*/
                    if (!appendReply.success)
                        refreshTerm(appendReply.term);
                }
            } catch (RemoteException e) {
                System.err.printf("[Remote Exception] Node %d fails to send AppendEntries to Node %d.\n", id, i);
            }
        }
    }

    /**
     * Change to Follower State
     * <p> can be :
     *     CANDIDATE discovers cyrrent LEADER or new term
     *     LEADER discovers sever with higher rerm
     * </p>
     */
    public synchronized void ToFollowerState() {
        /* if change from leader state, end heartbeat*/
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

    /**
     * synchronized helper function to clean all Leader's AppendEntries List
     * including threads for heartbeat and threads for LogLists
     * */
    public synchronized void cleanThreadList(){
        if (aeSendThreadList != null) {
            for (AppendEntrySendThread thread : aeSendThreadList) {
                thread.threadStop();
            }
        }
        this.aeSendThreadList = new ArrayList<>();
    }

    /**
     * The Self-defined Thread for Send AppendEntry
     * The leader will send HeartBeat and AppendLogs to all server/specific server
     * Thus will need to construct multiple thread tasks
     */
    public class AppendEntrySendThread extends Thread {
        private int followerId;
        private int currentIndex;
        private State state = RUNNABLE;
        private AppendEntryReceiveOperator commitOperator;
        private int counter = 0;
        private List<LogEntry> logCopy;
        private int currentTerm;

        /**
         * for copy from a existing AppendEntrySendThread
         * can be used to clone an identical thread for running, while adding the original one into the store list
         * @param aeSendThread the AppendEntriesSendThread to be cloned
         */
        public AppendEntrySendThread(AppendEntrySendThread aeSendThread) {
            this.followerId = aeSendThread.followerId;
            this.currentIndex = aeSendThread.currentIndex;
            this.commitOperator = aeSendThread.commitOperator;
            this.logCopy = aeSendThread.logCopy;
            this.currentTerm = aeSendThread.currentTerm;
        }

        /**
         * The constrcutor for AppendEntrySendThread
         * @param followerId the FOLLOWERS of this LEADER node in this term
         * @param currentIndex LEADER's current Index of logs
         * @param currentTerm LEADER's current Term
         * @param commitOperator the Reply-Receive-Operator for this thread, call if AppendEntry response <code>success</code>
         * @param NodeLog The current LogEntries List, need to make a copy, do not modify the original Log in State Machine
         */
        public AppendEntrySendThread(int followerId, int currentIndex, int currentTerm, AppendEntryReceiveOperator commitOperator,
                                      List<LogEntry> NodeLog) {
            this.followerId = followerId;
            this.currentIndex = currentIndex;
            this.commitOperator = commitOperator;
            this.logCopy = new ArrayList<LogEntry>(NodeLog); // apply for a new list for static visit
            this.currentTerm = currentTerm;
        }

        @Override
        public void run() {
            /*forcefully mark the FOLLOWER's next log entry to send to the server the same as the current Index*/
            synchronized (nextIndex) {
                if (currentIndex < nextIndex[this.followerId]) {
                    nextIndex[this.followerId] = currentIndex;
                }
            }
            /* Rules for Servers - Leaders - #3:
            if last LogIndex >= nextIndex for a follower send AppendEntries RPC with log entries starting at nextIndex */
            while (this.state == RUNNABLE && nodeRole.equals(NodeRole.LEADER)) {
                /* get FOLLOWER's prevLogTerm
                * the prev Log is the one immediately preceding the new ones */
                int prevIndex = (nextIndex[this.followerId] - 2 >=0) ? nextIndex[this.followerId] - 2 : 0 ;
                int prevLogTerm = logCopy.get(prevIndex).getTerm();

                /* get current static entries */
                int startIndex = (nextIndex[this.followerId] - 1 >= 0) ? nextIndex[this.followerId] - 1 : 0;

                /* get the current different entries
                 * startIndex,inclusive
                 * currentIndex, exclusive*/
                List<LogEntry> entries = persistentState.getStaticEntriesBetween(startIndex, currentIndex);

                /* LEADER build AppendEntriesArgs
                send AppendEntries RPC with log entries starting at nextIndex */
                AppendEntriesArgs appendEntriesArgs = new AppendEntriesArgs(this.currentTerm, id,
                        startIndex, prevLogTerm, entries, commitIndex);

                /* form Message body
                * Object to byte[], byte[] wrapped as message*/
                byte[] messageBody = toByteConverter(appendEntriesArgs);
                Message message = new Message(
                        MessageType.AppendEntriesArgs, id, followerId, messageBody);

                debugLog(String.format(
                        "[AppendEntry Sending...] {Thread-%d} term %d, Node %d[%s] ->  node %d: index %d, length %d\n",
                        currentThread().getId(),currentTerm, id, nodeRole.toString(),this.followerId, appendEntriesArgs.prevLogIndex+1, appendEntriesArgs.entries.size()
                ));

                /* communicating and get response */
                Message response = null;
                try {
                    response = lib.sendMessage(message);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                /* error handling */
                if (response == null || !nodeRole.equals(NodeRole.LEADER)) {
                    return;
                }
                else {
                    /* strip from Reply Message to get Object response */
                    AppendEntriesReply reply = (AppendEntriesReply) toObjectConverter(response.getBody());
                    /* Rule for Servers - Leader - #3:(&sect;5.3)
                    Receiver Implementation for Leader while receive Reply of AppendEntries from Followers
                    see inner class commitOperator for detail*/
                    if (reply.isSuccess()) {
                        /* if successfully, update NextIndex and matchIndex for follower*/
                        debugLog(String.format(
                                "[AppendEntriesReply success] {Thread-%d} Term: %d, Node %d receives success from Node %d (%d times).\n",
                                currentThread().getId(),reply.term, id, this.followerId, ++counter
                        ));

                        /* count success numbers*/
                        commitOperator.commitLog();

                        /* Rule for Servers - Leader - #3-1:(&sect;5.3) */
                        increaseNextIndex(this.followerId);
                        updateMatchIndex(this.followerId, nextIndex[followerId]-1);
                        return;
                    }
                    else {
                        /* Rule for Servers - Leader - #3-2:(&sect;5.3)
                        if AppendEntries fails because of log inconsistency: decrement nextIndex and retry */
                        if (reply.term > currentTerm) {
                            refreshTerm(reply.term); // refresh Term
                        } else {
                            decreaseNextIndex(this.followerId);
                        }
                    }
                }
            }
            probNSituation();
        }

        /**
         * Thread id, for debugLog output
         * @return Thread id
         */
        @Override
        public long getId() {
            return super.getId();
        }

        /**
         * explicitly adopt start for clonning Thread to start
         */
        public void start() {
            super.start();
        }

        /**
         * stop thread, set running state to TERMINATED
         */
        public void threadStop() {
            state = TERMINATED;
        }

        /**
         * synchronized function to increase NextIndex
         * @param followerId the follower to be updated
         */
        public void increaseNextIndex(int followerId) {
            synchronized (nextIndex) {
                nextIndex[followerId]++; // add log
                debugLog(String.format(
                        "[nextIndex++] {Thread-%d} Node %d increase NextIndex to .%d\n",
                         currentThread().getId(),followerId, nextIndex[followerId]
                ));
            }
        }

        /**
         * synchronized function to decrease NextIndex
         * @param followerId the follower to be updated
         */
        public void decreaseNextIndex(int followerId) {
            synchronized (nextIndex) {
                nextIndex[followerId]--; // roll back
                debugLog(String.format(
                        "[nextIndex++] {Thread-%d} Node %d increase NextIndex to .%d\n",
                        currentThread().getId(),followerId, nextIndex[followerId]
                ));
            }
        }

        /**
         * synchronized function to updateMatchIndex
         * @param followerId the follower to be updated
         * @param highestIndex the highest log entry known to be replicated on server
         */
        public void updateMatchIndex(int followerId, int highestIndex) {
            synchronized (matchIndex) {
                matchIndex[followerId] = highestIndex;
                debugLog(String.format(
                        "[matchIndex renew] {Thread-%d} Node %d renew matchIndex to .%d\n",
                        currentThread().getId(),followerId, highestIndex
                ));
            }
        }
    }


    /**
     * ReceiveOperator for each AppendEntries Send Thread
     */
    public class AppendEntryReceiveOperator{
        private int indexOnDecide; // it probably equals to currentIndex
        private int successCounter = 1;
        private boolean hasCommitted = false;

        /**
         * construction for AppendEntryReceiveOperator
         * @param index index to be decided
         * defult successCounter is 1
         * default hasCommitted = false
         */
        public AppendEntryReceiveOperator(int index) {
            this.indexOnDecide = index;
            synchronized(lastApplied){
                lastApplied = commitIndex;
            }
        }

        /**
         * synchronized State Machine
         * every time this function is called, one success is replied
         * count whether there is majority votes
         */
        public void commitLog() {
            synchronized (this) {
                if(!hasCommitted){
                    successCounter++;
                    /* receive majority 1/2 votes */
                    if (successCounter > num_peers / 2) {
                        debugLog(String.format(
                                "[Reach Agreement] Node %d received success for %d times \n",
                                id, successCounter
                        ));
                    /* Rule All server #1:
                    update state machine from lastApplied to indexOnDecide*/
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

                        /* commitIndex = currentIndex
                         * lastApplied = currentIndex
                         * hasCommitted = true
                         * */
                        commitIndex = indexOnDecide;
                        lastApplied = commitIndex;
                        hasCommitted = true;
                    }
                }
            }
        }

        /**
         * getter for hasCommitted, for other members to visit
         * @return true if has committed, false if not
         */
        public Boolean hasCommitted() {
            synchronized (this) {
                return hasCommitted;
            }
        }
    }

    /**
     * Special Case
     * see Rules for Servers - Leaders - #4:
     * if there exists an N such that N &gt; commitIndex, a majority of matchIndex[i] &gt;=  N
     * and log[N].term == currentTerm, set commitIndex == N(&sect;5.3 , &sect;5.4)
     */
    public synchronized void probNSituation() {
        List<Integer> arrList = Arrays.asList(matchIndex);
        int N;
        Collections.sort(arrList);
        /* if a majority of matchIndex[i] > N, find the (small) Integer median for matchIndex*/
        if(num_peers % 2 == 1){
            N = arrList.get((num_peers-1)/2);
        }else{
            N = arrList.get(num_peers/2 -1);
        }
        debugLog(String.format(
                "[Probing N-Situation] the N that will receive majority matchIndex[i] >= N is % d\n",
                N
        ));
        /* if there exists an N such that N > commitIndex and log[N].term == currentTerm */
        if(N > commitIndex && persistentState.logEntries.get(N).getTerm() == persistentState.currentTerm){
            /* commitIndex == N */
            this.commitIndex = N;
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
