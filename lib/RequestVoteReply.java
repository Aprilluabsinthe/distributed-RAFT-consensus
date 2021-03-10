package lib;

import java.io.Serializable;
import java.util.Objects;

/**
 * RequestVoteReply -- wrapper for packing any result information in your
 * implementation of the RequestVote call; should be serializable
 */
public class RequestVoteReply {
    private static final long serialVersionUID = 1L;
    private int term;
    private boolean voteGranted;

    public RequestVoteReply(int term, boolean voteGranted) {
        this.term = term;
        this.voteGranted = voteGranted;
    }

    public int getTerm() {
        return term;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public boolean isVoteGranted() {
        return voteGranted;
    }

    public void setVoteGranted(boolean voteGranted) {
        this.voteGranted = voteGranted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RequestVoteReply)) return false;
        RequestVoteReply that = (RequestVoteReply) o;
        return term == that.term && voteGranted == that.voteGranted;
    }

    @Override
    public int hashCode() {
        return Objects.hash(term, voteGranted);
    }

    @Override
    public String toString() {
        return "RequestVoteReply{" +
                "term=" + term +
                ", voteGranted=" + voteGranted +
                '}';
    }
}
