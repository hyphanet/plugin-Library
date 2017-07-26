#!/bin/sh -x

while test -f library.continue.loop
do
    java -jar `dirname $0`/dist/uploader.jar
    sleep 60
done
