package lib;

import java.io.Serializable;

/**
 * Results for Append Entries
 */
public class AppendEntriesReply implements Serializable {
  private static final long serialVersionUID = 1L;
  public int term;
  public boolean success;

  /**
   * Results for AppendEntries RPC
   * @param term current Term, for leader to update itself
   * @param success true if follower contained entry matching prevLogIndex and prevLogTerm
   */
  public AppendEntriesReply(int term, boolean success) {
    this.term = term;
    this.success = success;
  }

  /**
   * Getter for Current Term
   * @return Current Term
   */
  public synchronized int getTerm() {
    return term;
  }

  /**
   * Setter for current Term
   * @param term Current Term
   */
  public synchronized void setTerm(int term) {
    this.term = term;
  }

  /**
   * whether follower contained entry matching prevLogIndex
   * @return true if follower contained entry matching prevLogIndex
   */
  public synchronized boolean isSuccess() {
    return success;
  }

  /**
   * Setter for status follower contained entry matching prevLogIndex
   * @param success status
   */
  public synchronized void setSuccess(boolean success) {
    this.success = success;
  }
}
