#!/bin/bash

if [[ -z "$ZOOBINDIR" ]]
then
	echo "Error!! ZOOBINDIR is not set" 1>&2
	exit 1
fi

. $ZOOBINDIR/zkEnv.sh

# TODO Include your ZooKeeper connection string here. Make sure there are no spaces.
# 	Replace with your server names and client ports.
export ZKSERVER=lab2-1.cs.mcgill.ca:21807,lab2-2.cs.mcgill.ca:21807,lab2-6.cs.mcgill.ca:21807

java -cp $CLASSPATH:../task:.: DistClient "$@"
