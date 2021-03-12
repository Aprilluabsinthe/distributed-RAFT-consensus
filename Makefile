# Makefile variables.
# - JAVAFILES is all of the Java files in the project, including test cases and
#   build tools.
# - DOCDIR gives the relative path to the directory into which the documentation
#   generated by the docs target will be placed.
# - DOCLINK is the URL to Javadoc for the standard Java class library.

JAVAFILES = *.java */*.java
DOCDIR = doc
DOCLINK = http://download.oracle.com/javase/8/docs/api

# Compile all Java files.
.PHONY : all
all :
	javac -Xlint -g *.java

# Delete all class files
.PHONY : clean
clean :
	rm -f *.class lib/*.class *.txt *.log *.txt.* *.log.*

# Generate documentation for everything in to the JAVAFILES directories
.PHONY : docs
docs :
	javadoc -link $(DOCLINK) -d $(DOCDIR) $(JAVAFILES)

