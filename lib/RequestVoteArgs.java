package lib;

import java.io.Serializable;
import java.util.Objects;

/**
 * RequestVoteArgs -- wrapper for packing any arguments you want in a RequestVote,
 * should be serializable to fill in the payload of a Message to send
 */
public class RequestVoteArgs implements Serializable {
    private static final long serialVersionUID = 1L;
    private int term;
    private int candidateID;
    private int LastLogIndex;
    private int LastLogTerm;

    /**
     * Construction for RequestVoteArgs
     * @param term candidate's term
     * @param candidateID candidate's requesting vote
     * @param lastLogIndex index of candidate's last log entry
     * @param lastLogTerm Term of candidate's last log entry
     */
    public RequestVoteArgs(int term, int candidateID, int lastLogIndex, int lastLogTerm) {
        this.term = term;
        this.candidateID = candidateID;
        this.LastLogIndex = lastLogIndex;
        this.LastLogTerm = lastLogTerm;
    }

    /**
     * Getter for candidate's term
     * @return candidate's term
     */
    public int getTerm() {
        return term;
    }

    /**
     * Setter for candidate's term
     * @param term candidate's term
     */
    public void setTerm(int term) {
        this.term = term;
    }

    /**
     * Getter for candidate's requesting vote
     * @return candidate's requesting vote
     */
    public int getCandidateID() {
        return candidateID;
    }

    /**
     * Setter for candidate's requesting vote
     * @param candidateID candidate's requesting vote
     */
    public void setCandidateID(int candidateID) {
        this.candidateID = candidateID;
    }

    /**
     * Getter for index of candidate's last log entry
     * @return index of candidate's last log entry
     */
    public int getLastLogIndex() {
        return LastLogIndex;
    }

    /**
     * Setter index of candidate's last log entry
     * @param lastLogIndex index of candidate's last log entry
     */
    public void setLastLogIndex(int lastLogIndex) {
        LastLogIndex = lastLogIndex;
    }

    /**
     * Getter for Term of candidate's last log entry
     * @return Term of candidate's last log entry
     */
    public int getLastLogTerm() {
        return LastLogTerm;
    }

    /**
     * Setter for Term of candidate's last log entry
     * @param lastLogTerm Term of candidate's last log entry
     */
    public void setLastLogTerm(int lastLogTerm) {
        LastLogTerm = lastLogTerm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RequestVoteArgs)) return false;
        RequestVoteArgs that = (RequestVoteArgs) o;
        return term == that.term && candidateID == that.candidateID && LastLogIndex == that.LastLogIndex && LastLogTerm == that.LastLogTerm;
    }

    @Override
    public int hashCode() {
        return Objects.hash(term, candidateID, LastLogIndex, LastLogTerm);
    }

    @Override
    public String toString() {
        return "RequestVoteArgs{" +
                "term=" + term +
                ", candidateID=" + candidateID +
                ", LastLogIndex=" + LastLogIndex +
                ", LastLogTerm=" + LastLogTerm +
                '}';
    }
}
