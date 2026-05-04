#!/bin/sh

# Update version
FILE="app/build.gradle"
CODE=`grep "versionCode .*" $FILE | sed "s/versionCode //"`
VERSIONCODE=$(($CODE + 1))
sed -i "s/versionCode .*$/versionCode $VERSIONCODE/; s/versionName .*$/versionName \"$1\"/" $FILE

# Save change-log
CHNAGELOG="metadata/en-US/changelogs/$VERSIONCODE.txt"
echo -e "$2" > "$CHNAGELOG"
pandoc -f markdown -t plain --wrap=none "$CHNAGELOG" -o "$CHNAGELOG"
tail -n +3 "$CHNAGELOG" > "$CHNAGELOG.tmp" && mv "$CHNAGELOG.tmp" "$CHNAGELOG"
sed -i "s/ \((.*)\)//" "$CHNAGELOG"
# marked -i "$CHNAGELOG" -o "$CHNAGELOG" --sanitize
