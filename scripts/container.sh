#!/bin/sh

guix shell \
     -m manifest.scm \
     --container -F -N \
     --share=/opt/android-sdk \
     --share=$HOME \
     --preserve='^DISPLAY$' \
     --preserve='^XAUTHORITY$' \
     --preserve='^DBUS_' \
     --expose=$XAUTHORITY \
     --expose=/var/run/dbus \
     --expose=/sys/dev \
     --expose=/sys/devices \
     --expose=/dev/dri \
     --expose=/dev/kvm \
     $@
