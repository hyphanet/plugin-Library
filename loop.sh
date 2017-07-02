#!/bin/sh -ex

while test -f library.continue.loop
do
    # tail wrapper.log
    # ls -ltr library.index.*
    java -jar ../projects/freenet/github/plugin-Library/dist/uploader.jar
    sleep 60
done
