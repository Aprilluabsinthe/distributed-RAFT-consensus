package lib;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PersistentState {
    public int currentTerm;
    public Integer votedFor;
    public List<LogEntry> logEntries;

    public PersistentState(int currentTerm, Integer votedFor, List<LogEntry> logEntries) {
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
        this.logEntries = logEntries;
    }

    public PersistentState() {
        this.currentTerm = 0;
        this.votedFor = Integer.MIN_VALUE;
        this.logEntries = new ArrayList<>();
    }

    public synchronized LogEntry getLastEntry() {
        if (logEntries ==null || logEntries.size() == 0) {
            return new LogEntry(0, 0, 0);
        } else {
            return logEntries.get(logEntries.size() - 1);
        }
    }

    public synchronized int getCurrentTerm() {
        return currentTerm;
    }

    public synchronized Integer getVotedFor() {
        return votedFor;
    }

    public synchronized List<LogEntry> getLogEntries() {
        return logEntries;
    }

    @Override
    public String toString() {
        return "PersistentState{" +
                "currentTerm=" + currentTerm +
                ", votedFor=" + votedFor +
                ", logEntries=" + logEntries +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersistentState)) return false;
        PersistentState that = (PersistentState) o;
        return currentTerm == that.currentTerm && Objects.equals(votedFor, that.votedFor) && Objects.equals(logEntries, that.logEntries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentTerm, votedFor, logEntries);
    }
}
