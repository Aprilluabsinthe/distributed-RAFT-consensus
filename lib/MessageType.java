package lib;

/**
 * MessageType -- enum to list all available message types
 */
public enum MessageType {
    RequestVoteArgs, RequestVoteReply,
    AppendEntriesArgs,AppendEntriesReply,
}
