#!/bin/sh

set -eou

cd ./fauna-jvm-repository

PACKAGE_VERSION=$(sbt faunadb-common/version | tail -n 1 | cut -c 8-)

echo "Going to publish version: $PACKAGE_VERSION"

# sbt faunadb-common/publishSigned
# sbt faunadb-java/publishSigned
# sbt faunadb-scala/publishSigned
# sbt sonatypeRelease
