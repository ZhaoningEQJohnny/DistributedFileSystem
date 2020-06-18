#!/bin/sh

rm -f *~
rm -f *.class
echo "client compilation"
javac FileClient.java
echo "done"

echo "server compilation"
javac FileServer.java
echo "done"

chmod 600 *.class
