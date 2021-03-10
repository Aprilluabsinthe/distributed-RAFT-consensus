package lib;

import java.io.Serializable;
import java.util.List;

/**
 * Invoked by leader to replicate log entries, also used as heartbeat
 */
public class AppendEntriesArgs implements Serializable {
  private static final long serialVersionUID = 1L;
  public int term;
  public int leaderId;
  public int prevLogIndex;
  public int prevLogTerm;
  public List<LogEntry> entries;
  public int leaderCommit;

  /**
   *
   * @param term leader's term
   * @param leaderId follower can use leader's ID to redirect clients
   * @param prevLogIndex index of log entry immediately preceding new ones
   * @param prevLogTerm term of prevLogIndex
   * @param entries log entries to store(empty for heartbeat, may send more than one for efficiency)
   * @param leaderCommit leader's commitIndex
   */
  public AppendEntriesArgs(int term, int leaderId, int prevLogIndex, int prevLogTerm, List<LogEntry> entries, int leaderCommit) {
    this.term = term;
    this.leaderId = leaderId;
    this.prevLogIndex = prevLogIndex;
    this.prevLogTerm = prevLogTerm;
    this.entries = entries;
    this.leaderCommit = leaderCommit;
  }

  /**
   * Getter for leader's term
   * @return int leader's term
   */
  public int getTerm() {
    return term;
  }

  /**
   * Setter for leader's term
   * @param term leader's term
   */
  public void setTerm(int term) {
    this.term = term;
  }

  /**
   * Getter for leader's ID
   * @return int leader's ID
   */
  public int getLeaderId() {
    return leaderId;
  }

  /**
   * Setter for leader's ID
   * @param leaderId leader's ID
   */
  public void setLeaderId(int leaderId) {
    this.leaderId = leaderId;
  }

  /**
   * Getter for PrevLogIndex
   * @return PrevLogIndex
   */
  public int getPrevLogIndex() {
    return prevLogIndex;
  }

  /**
   * Setter for PrevLogIndex
   * @param prevLogIndex Previous Log Index
   */
  public void setPrevLogIndex(int prevLogIndex) {
    this.prevLogIndex = prevLogIndex;
  }

  /**
   * Getter for PrevLogTerm
   * @return Previous Log Term
   */
  public int getPrevLogTerm() {
    return prevLogTerm;
  }

  /**
   * Setter for previous Log's Term
   * @param prevLogTerm previous Log's Term
   */
  public void setPrevLogTerm(int prevLogTerm) {
    this.prevLogTerm = prevLogTerm;
  }

  /**
   * Getter for Entries list
   * @return Entries list
   */
  public List<LogEntry> getEntries() {
    return entries;
  }

  /**
   * Setter for Entries list
   * @param entries Entries list
   */
  public void setEntries(List<LogEntry> entries) {
    this.entries = entries;
  }

  /**
   * Getter for leader's commit index
   * @return leader's commit index
   */
  public int getLeaderCommit() {
    return leaderCommit;
  }

  /**
   * Setter for leader's commit index
   * @param leaderCommit leader's commit index
   */
  public void setLeaderCommit(int leaderCommit) {
    this.leaderCommit = leaderCommit;
  }
}
