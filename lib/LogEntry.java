package lib;

import java.io.Serializable;
import java.util.Objects;

public class LogEntry implements Serializable {
  private static final long serialVersionUID = 1L;
  private int term;
  private int index;
  private int command;

  public LogEntry(int term, int index, int command) {
    this.term = term;
    this.index = index;
    this.command = command;
  }

  public int getTerm() {
    return term;
  }

  public void setTerm(int term) {
    this.term = term;
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public int getCommand() {
    return command;
  }

  public void setCommand(int command) {
    this.command = command;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LogEntry)) return false;
    LogEntry logEntry = (LogEntry) o;
    return term == logEntry.term && index == logEntry.index && command == logEntry.command;
  }

  @Override
  public int hashCode() {
    return Objects.hash(term, index, command);
  }

  @Override
  public String toString() {
    return "LogEntry{" +
            "term=" + term +
            ", index=" + index +
            ", command=" + command +
            '}';
  }
}
