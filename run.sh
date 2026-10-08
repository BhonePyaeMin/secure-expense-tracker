#!/usr/bin/env sh
# Starts Expense Tracker with low-memory JVM settings (about 230 MB).
# Builds the jar first if it's missing. After pulling or changing code, rebuild with:
#   ./mvnw -DskipTests package
# Extra options are passed to the app, e.g.  ./run.sh --spring.profiles.active=demo
set -e
cd "$(dirname "$0")"

JAR=target/expense-tracker-0.0.1-SNAPSHOT.jar
if [ ! -f "$JAR" ]; then
  echo "Building $JAR (first run only)..."
  ./mvnw -B -q -DskipTests package
fi

exec java -Xmx256m -Xms64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -jar "$JAR" "$@"
