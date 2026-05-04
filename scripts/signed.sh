#!/bin/sh

function prop {
  grep "${1}" local.properties|cut -d'=' -f2
}

read -s -p "Key Password: " PASSWORD
RELEASE_STORE_FILE=$(prop "RELEASE_STORE_FILE")
RELEASE_KEY_ALIAS=$(prop "RELEASE_KEY_ALIAS")

./gradlew assembleRelease -Pandroid.injected.signing.store.file=$RELEASE_STORE_FILE -Pandroid.injected.signing.store.password=$PASSWORD -Pandroid.injected.signing.key.alias=$RELEASE_KEY_ALIAS -Pandroid.injected.signing.key.password=$PASSWORD
