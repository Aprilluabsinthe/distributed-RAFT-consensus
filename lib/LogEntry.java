package lib;

import java.io.Serializable;
import java.util.Objects;

/**
 * The LogEntry class, containing term, index, command
 */
public class LogEntry implements Serializable {
  private static final long serialVersionUID = 1L;
  public int term;
  public int index;
  public int command;

  /**
   * construction function
   * @param term the term of the Entry
   * @param index the index of the Entry
   * @param command the command of the Entry
   */
  public LogEntry(int term, int index, int command) {
    this.term = term;
    this.index = index;
    this.command = command;
  }

  /**
   * getter for Term
   * @return int Term
   */
  public synchronized int getTerm() {
    return term;
  }

  /**
   * Getter for Index
   * @return int index
   */
  public synchronized int getIndex() {
    return index;
  }

  /**
   * Getter for Command
   * @return int command
   */
  public synchronized int getCommand() {
    return command;
  }

  /**
   * override for equals
   * @param o Object to compare
   * @return true if equals, false if not
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LogEntry)) return false;
    LogEntry logEntry = (LogEntry) o;
    return term == logEntry.term && index == logEntry.index && command == logEntry.command;
  }

  /**
   * Override of hashcode
   * @return int
   */
  @Override
  public int hashCode() {
    return Objects.hash(term, index, command);
  }

  /**
   * override for toString()
   * @return String
   */
  @Override
  public String toString() {
    return "LogEntry{" +
            "term=" + term +
            ", index=" + index +
            ", command=" + command +
            '}';
  }
}
