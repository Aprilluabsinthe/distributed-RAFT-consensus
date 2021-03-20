package lib;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The collections of members of State Machine,
 * PersistentState is updated before stable before responding to RPCS
 */
public class PersistentState {
    public int currentTerm;
    public Integer votedFor;
    public List<LogEntry> logEntries;

    /**
     * construction for PersistentState
     * @param currentTerm currentTerm in state machine
     * @param votedFor  the id the current Node voted for
     * @param logEntries List logEntries
     */
    public PersistentState(int currentTerm, Integer votedFor, List<LogEntry> logEntries) {
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
        this.logEntries = logEntries;
    }

    /**
     * default construction without parameters
     */
    public PersistentState() {
        this.currentTerm = 0;
        this.votedFor = Integer.MIN_VALUE;
        this.logEntries = new ArrayList<>();
    }

    /**
     * synchronized getter for LastEntry
     * @return LogEntry
     */
    public synchronized LogEntry getLastEntry() {
        if (logEntries ==null || logEntries.size() == 0) {
            return new LogEntry(0, 0, 0);
        } else {
            return logEntries.get(logEntries.size() - 1);
        }
    }

    /**
     * synchronized add a Command To the LogEntry
     * @param command the command to be added
     * @return LogEntry
     */
    public synchronized LogEntry addCommandToEntry(int command){
        LogEntry entry = new LogEntry(currentTerm, logEntries.size() + 1, command);
        logEntries.add(entry);
        return entry;
    }

    /**
     * Static get Entries Between startIndex(inclusive) to endIndex(exclusive)
     * @param startIndex the startIndex of the substring
     * @param endIndex the endIndex of the substring
     * @return ArrayList LogEntry
     */
    public ArrayList<LogEntry> getStaticEntriesBetween(int startIndex, int endIndex){
        if(startIndex >= 0 && startIndex <= logEntries.size()){
            return new ArrayList<LogEntry>(logEntries.subList(startIndex, endIndex));
        }
        else{
            return new ArrayList<LogEntry>();
        }
    }

    /**
     * synchronized remove Entries From index
     * @param index the starter index(inclusive)
     */
    public synchronized void removeEntriesFrom(int index){
        LogEntry lastEntry = getLastEntry();
        while (lastEntry != null && lastEntry.index > index) {
            logEntries.remove(logEntries.size() - 1);
            lastEntry = getLastEntry();
        }
    }

    /**
     * synchronized addAll, addAllLogEntries,
     * @param list List LogEntry
     */
    public synchronized void addAllLogEntries(List<LogEntry> list){
        logEntries.addAll(list);
    }

    /**
     * synchronized get Entries Between startIndex(inclusive) to endIndex(exclusive)
     * @param startIndex the startIndex of the substring
     * @param endIndex the endIndex of the substring
     * @return ArrayList LogEntry
     */
    public synchronized ArrayList<LogEntry> getSynchrEntriesBetween(int startIndex,int endIndex){
        if(startIndex >= 0 && startIndex <= logEntries.size()){
            return new ArrayList<LogEntry>(logEntries.subList(startIndex, endIndex));
        }
        else{
            return new ArrayList<LogEntry>();
        }
    }

    /**
     * synchronized get EntryLength
     * @return int length
     */
    public synchronized int getSynchrEntryLength(){
        return logEntries.size();
    }

    /**
     * Static get EntryLength
     * @return int length
     */
    public int getStaticEntryLength(){
        return logEntries.size();
    }


    /**
     * Getter for currentTerm
     * @return int currentTerm
     */
    public synchronized int getCurrentTerm() {
        return currentTerm;
    }

    /**
     * Getter for votedFor
     * @return votedFor
     */
    public synchronized Integer getVotedFor() {
        return votedFor;
    }

    /**
     * synchronized Getter for LogEntries
     * @return logEntries
     */
    public synchronized List<LogEntry> getLogEntries() {
        return logEntries;
    }

    /**
     * Override for toString()
     * @return String
     */
    @Override
    public String toString() {
        return "PersistentState{" +
                "currentTerm=" + currentTerm +
                ", votedFor=" + votedFor +
                ", logEntries=" + logEntries +
                '}';
    }

    /**
     * override for equals
     * @param o object to compare to
     * @return boolean
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersistentState)) return false;
        PersistentState that = (PersistentState) o;
        return currentTerm == that.currentTerm && Objects.equals(votedFor, that.votedFor) && Objects.equals(logEntries, that.logEntries);
    }

    /**
     * override for hashcode
     * @return int
     */
    @Override
    public int hashCode() {
        return Objects.hash(currentTerm, votedFor, logEntries);
    }
}
