make clean
make

echo "Test Initial-Election..."
java RaftTest Initial-Election 736 > Initial-Election.txt
tail -2 Initial-Election.txt
echo "Test Re-Election..."
java RaftTest Re-Election 736 > Re-Election.txt
tail -2 Re-Election.txt
#echo "Test Basic-Agree..."
#java RaftTest Basic-Agree 736 > Basic-Agree.txt
#tail -2 Basic-Agree.txt
#echo "Test Fail-Agree..."
#java RaftTest Fail-Agree 736 > Fail-Agree.txt
#tail -2 Fail-Agree.txt
#echo "Test Fail-NoAgree..."
#java RaftTest Fail-NoAgree 736 > Fail-NoAgree.txt
#tail -2 Fail-NoAgree.txt
#echo "Test Rejoin..."
#java RaftTest Rejoin 736 > Rejoin.txt
#tail -2 Rejoin.txt
#echo "Test Backup..."
#java RaftTest Backup 736 > Backup.txt
#tail -2 Backup.txt
#echo "Test Count..."
#java RaftTest Count 736 > Count.txt
#tail -2 Count.txt
