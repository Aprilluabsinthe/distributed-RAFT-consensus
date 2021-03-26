## ref https://piazza.com/class/kjjdvmwmw2b2s2?cid=189
make clean
make

echo "Initial-Election"
rm test1.txt #Ensure file doesn't exist
for i in $(seq 9); do gtimeout 30 java RaftTest Initial-Election 1234 >> test1.txt; done
echo $(grep -c "Passed" test1.txt) #Prints out number of times passed
#
echo "Re-Election"
rm test2.txt #Ensure file doesn't exist
for i in $(seq 9); do gtimeout 30 java RaftTest Re-Election 1234 >> test2.txt; done
echo $(grep -c "Passed" test2.txt) #Prints out number of times passed

echo "Basic-Agree"
rm test3.txt #Ensure file doesn't exist
for i in $(seq 9); do gtimeout 30 java RaftTest Basic-Agree 1234 >> test3.txt; done
echo $(grep -c "Passed" test3.txt) #Prints out number of times passed

echo "Fail-Agree"
rm test4.txt #Ensure file doesn't exist
for i in $(seq 9); do gtimeout 40 java RaftTest Fail-Agree 1234 >> test4.txt; done
echo $(grep -c "Passed" test4.txt) #Prints out number of times passed
#
echo "Fail-NoAgree"
rm test5.txt #Ensure file doesn't exist
for i in $(seq 9); do gtimeout 40 java RaftTest Fail-NoAgree 1234 >> test5.txt; done
echo $(grep -c "Passed" test5.txt) #Prints out number of times passed
##
echo "Rejoin"
rm test6.txt #Ensure file doesn't exist
for i in $(seq 9); do gtimeout 40 java RaftTest Rejoin 1234 >> test6.txt; done
echo $(grep -c "Passed" test6.txt) #Prints out number of times passed
#
echo "Backup"
rm test7.txt #Ensure file doesn't exist
for i in $(seq 9); do gtimeout 60 java RaftTest Backup 1234 >> test7.txt; done
echo $(grep -c "Passed" test7.txt) #Prints out number of times passed

echo "Count"
rm test8.txt #Ensure file doesn't exist
for i in $(seq 9); do gtimeout 30 java RaftTest Count 1234 >> test8.txt; done
echo $(grep -c "Passed" test8.txt) #Prints out number of times passed

