package lib;

import java.io.Serializable;
import java.util.Objects;

/**
 * This class is a wrapper for packing all the result information that you
 * might use in your own implementation of the RequestVote call, and also
 * should be serializable to return by remote function call.
 * Invoked by candidates to garher votes(&sect;5.2)
 *
 */
public class RequestVoteReply implements Serializable{
    private static final long serialVersionUID = 1L;

    public int term;
    public boolean voteGranted;

    /**
     * constructor for RequestVoteReply
     * @param term the term recorede
     * @param voteGranted true if vote, false if not vote
     */
    public RequestVoteReply(int term, boolean voteGranted) {
        this.term = term;
        this.voteGranted = voteGranted;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public void setVoteGranted(boolean voteGranted) {
        this.voteGranted = voteGranted;
    }

    /**
     * override for toString
     * @return String
     */
    @Override
    public String toString() {
        return "RequestVoteReply{" +
                "term=" + term +
                ", voteGranted=" + voteGranted +
                '}';
    }

    /**
     * override for equals
     * @param o Object
     * @return boolean
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RequestVoteReply)) return false;
        RequestVoteReply that = (RequestVoteReply) o;
        return term == that.term && voteGranted == that.voteGranted;
    }

    /**
     * override for hashcode
     * @return int
     */
    @Override
    public int hashCode() {
        return Objects.hash(term, voteGranted);
    }
}
