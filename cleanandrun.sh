make clean
make

echo "Test Initial-Election..."
java RaftTest Initial-Election 736 > Initial-Election.txt
tail -2 Initial-Election.txt
echo "Test Re-Election..."
java RaftTest Re-Election 736 > Re-Election.txt
tail -2 Re-Election.txt

