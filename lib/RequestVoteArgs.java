package lib;

import java.io.Serializable;
import java.util.Objects;

/**
 * This class is a wrapper for packing all the arguments that you might use in
 * the RequestVote call, and should be serializable to fill in the payload of
 * Message to be sent.
 * Invoked by candidates to garher votes(&sect;5.2)
 *
 */
public class RequestVoteArgs implements Serializable{
    private static final long serialVersionUID = 1L;
    public int term;
    public int candidateId;
    public int lastLogIndex;
    public int lastLogTerm;

    /**
     * <p>
     *   Invoked by candidates to garher votes(&sect;5.2)
     * @param term andidate's term
     * @param candidateId candidate requesting vote
     * @param lastLogIndex index of candidate's last log entry(&sect;5.4)
     * @param lastLogTerm term of candidate's last log entry(&sect;5.4)
     */
    public RequestVoteArgs(int term, int candidateId, int lastLogIndex, int lastLogTerm) {
        this.term = term;
        this.candidateId = candidateId;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }

    public int getTerm() {
        return term;
    }

    public int getCandidateId() {
        return candidateId;
    }

    public int getLastLogIndex() {
        return lastLogIndex;
    }

    public int getLastLogTerm() {
        return lastLogTerm;
    }

    @Override
    public String toString() {
        return "RequestVoteArgs{" +
                "term=" + term +
                ", candidateId=" + candidateId +
                ", lastLogIndex=" + lastLogIndex +
                ", lastLogTerm=" + lastLogTerm +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RequestVoteArgs)) return false;
        RequestVoteArgs that = (RequestVoteArgs) o;
        return term == that.term && candidateId == that.candidateId && lastLogIndex == that.lastLogIndex && lastLogTerm == that.lastLogTerm;
    }

    @Override
    public int hashCode() {
        return Objects.hash(term, candidateId, lastLogIndex, lastLogTerm);
    }
}
