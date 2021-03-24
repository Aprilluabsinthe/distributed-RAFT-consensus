## ref https://piazza.com/class/kjjdvmwmw2b2s2?cid=189

echo "Initial-Election"
rm test1.txt #Ensure file doesn't exist
for i in $(seq 9); do java RaftTest Initial-Election 5678 >> test1.txt; done
echo $(grep -c "Passed" test1.txt) #Prints out number of times passed
rm test1.txt #Delete when done
#
echo "Re-Election"
rm test2.txt #Ensure file doesn't exist
for i in $(seq 9); do java RaftTest Re-Election 5678 >> test2.txt; done
echo $(grep -c "Passed" test2.txt) #Prints out number of times passed
rm test2.txt #Delete when done

echo "Basic-Agree"
rm test3.txt #Ensure file doesn't exist
for i in $(seq 9); do java RaftTest Basic-Agree 5678 >> test3.txt; done
echo $(grep -c "Passed" test3.txt) #Prints out number of times passed
rm test3.txt #Delete when done

echo "Fail-Agree"
rm test4.txt #Ensure file doesn't exist
for i in $(seq 9); do java RaftTest Fail-Agree 5678 >> test4.txt; done
echo $(grep -c "Passed" test4.txt) #Prints out number of times passed
rm test4.txt #Delete when done
#
echo "Fail-NoAgree"
rm test5.txt #Ensure file doesn't exist
for i in $(seq 9); do java RaftTest Fail-NoAgree 5678 >> test5.txt; done
echo $(grep -c "Passed" test5.txt) #Prints out number of times passed
rm test5.txt #Delete when done
##
echo "Rejoin"
rm test6.txt #Ensure file doesn't exist
for i in $(seq 9); do java RaftTest Rejoin 5678 >> test6.txt; done
echo $(grep -c "Passed" test6.txt) #Prints out number of times passed
rm test6.txt #Delete when done
#
echo "Backup"
rm test7.txt #Ensure file doesn't exist
for i in $(seq 9); do java RaftTest Backup 5678 >> test7.txt; done
echo $(grep -c "Passed" test7.txt) #Prints out number of times passed
rm test7.txt #Delete when done

echo "Count"
rm test8.txt #Ensure file doesn't exist
for i in $(seq 9); do java RaftTest Count 5678 >> test8.txt; done
echo $(grep -c "Passed" test8.txt) #Prints out number of times passed
rm test8.txt #Delete when done

