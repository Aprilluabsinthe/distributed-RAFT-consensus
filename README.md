## 14736 Spring 2021 Lab 2 -- Raft (Java)

### Makefile Targets

To compile all Java files: `make all`

To generate documentation: `make docs`

To remove .class files: `make clean`


### Other Commands

To create a new Raft node: `java RaftNode <port> <id> <num_peers>`, where 
* `<port>` is the port number
* `<id>` is the ID of the Raft node being created
* `<num_peers>` is the total number of Raft nodes

To run tests: `java RaftTest <test_name> <port>`, where
* `<port>` is the port number
* `<test_name>` is one of:
    * Initial-Election
    * Re-Election
    * Basic-Agree
    * Fail-Agree
    * Fail-NoAgree
    * Rejoin
    * Backup
    * Count
