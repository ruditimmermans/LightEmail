(use-modules (gnu)
             (gnu packages)
             (gnu system)
             (guix profiles)
             (gnu packages gcc)
             (gnu packages java)
             (gnu packages linux))

(concatenate-manifests
 (list (packages->manifest
        (append (list (list gcc "lib")
                      (list openjdk17 "jdk")
                      (list util-linux "lib"))
                %base-packages))
       (specifications->manifest
        (list "unzip"
              "curl"
              "dbus"
              "expat"
              "libx11"
              "libxcb"
              "libxcomposite"
              "libxcursor"
              "libxdamage"
              "libxext"
              "libxfixes"
              "libxi"
              "libxrender"
              "libxtst"
              "libglvnd"
              "nspr"
              "zlib"
              "alsa-lib"
              "pulseaudio"
              "nss"
              "nss-certs"
              "sdkmanager"
              "maven"))))
