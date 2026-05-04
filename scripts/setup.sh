#!/bin/sh

use_sdk() {
  if [[ ! -s "${SDKMAN_DIR}/bin/sdkman-init.sh" ]]; then
	  curl -s "https://get.sdkman.io?rcupdate=false" | bash
  fi
  source "${SDKMAN_DIR}/bin/sdkman-init.sh"

  while (( "$#" >= 2 )); do
	  local candidate=$1
	  local candidate_version=$2

    sdk install $candidate $candidate_version
	  SDKMAN_OFFLINE_MODE=true sdk use $candidate $candidate_version

	  shift 2
  done
}

install_kls() {
  local url="https://github.com/fwcd/KotlinLanguageServer"
  local pkgver=$1
  local pkgname="kotlin-language-server"
  local filename="${pkgname}-${pkgver}.tar.gz"

  if [[ -f "./kls/bin/kotlin-language-server" ]]; then
    echo "kotlin-language-server already exist"
    exit 0
  fi
  mkdir -p "kls" && cd "kls"
  rm -f "$filename"

  wget "${url}/archive/${pkgver}.tar.gz" -O "$filename"
  exit_code=$?

  if [[ $exit_code -ne 0 ]]; then
    echo "Error downloading from $url"
    exit 0
  fi

  tar -xzf "$filename"
  rm -f "$filename"

  cd "./${pkgname}-${pkgver}"
  ./gradlew server:installDist
  exit_code=$?

  if [[ $exit_code -eq 0 ]]; then
    cd ../
    cp -r ./${pkgname}-${pkgver}/server/build/install/server/* .
    rm -r "./${pkgname}-${pkgver}"
  fi
}

use_sdk kotlin 1.9.0
install_kls 1.3.3
