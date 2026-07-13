#!/bin/sh
set -eu

mode="${1:-server}"
if [ "$#" -gt 0 ]; then
  shift
fi

case "${mode}" in
  server)
    exec java -jar /opt/serviceos/app.jar "$@"
    ;;
  migrate)
    # PropertiesLauncher 允许同一 Boot fat jar 选择受限迁移 main，不启动业务应用。
    exec java \
      -Dloader.main=com.serviceos.bootstrap.DatabaseMigrationMain \
      -cp /opt/serviceos/app.jar \
      org.springframework.boot.loader.launch.PropertiesLauncher "$@"
    ;;
  *)
    echo "unsupported ServiceOS container mode: ${mode}" >&2
    exit 64
    ;;
esac
