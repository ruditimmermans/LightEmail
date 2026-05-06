#!/bin/sh

# Update version
FILE="app/build.gradle"
CODE=`grep "versionCode .*" $FILE | sed "s/versionCode //"`
VERSIONCODE=$(($CODE + 1))
sed -i "s/versionCode .*$/versionCode $VERSIONCODE/; s/versionName .*$/versionName \"$1\"/" $FILE


