#!/bin/sh

set -eou

cd ./fauna-jvm-repository

PACKAGE_VERSION=$(sbt faunadb-common/version | tail -n 1 | cut -c 8-)

sbt clean doc

echo "Current docs version: $PACKAGE_VERSION"

cd ../
git clone fauna-jvm-repository-docs fauna-jvm-repository-updated-docs

cd fauna-jvm-repository-updated-docs

mkdir "${PACKAGE_VERSION}"
cd "${PACKAGE_VERSION}"

echo "================================="
echo "Common project docs"
echo "================================="

echo "Copying..."

mkdir faunadb-common
cp -R "../../fauna-jvm-repository/faunadb-common/target/api" faunadb-common

echo "Adding google manager tag to head..."

HEAD_GTM=$(cat ../../fauna-jvm-repository/concourse/scripts/head_gtm.dat)
sed -i.bak "0,/<\/title>/{s/<\/title>/<\/title>${HEAD_GTM}/}" ./faunadb-common/api/index.html

echo "Adding google manager tag to body..."

BODY_GTM=$(cat ../../fauna-jvm-repository/concourse/scripts/body_gtm.dat)
sed -i.bak "0,/<body>/{s/<body>/<body>${BODY_GTM}/}" ./faunadb-common/api/index.html

rm ./faunadb-common/api/index.html.bak

echo "================================="
echo "Java project docs"
echo "================================="

echo "Copying..."

mkdir faunadb-java
cp -R "../../fauna-jvm-repository/faunadb-java/target/api" faunadb-java

echo "Adding google manager tag to head..."

HEAD_GTM=$(cat ../../fauna-jvm-repository/concourse/scripts/head_gtm.dat)
sed -i.bak "0,/<\/title>/{s/<\/title>/<\/title>${HEAD_GTM}/}" ./faunadb-java/api/index.html

echo "Adding google manager tag to body..."

BODY_GTM=$(cat ../../fauna-jvm-repository/concourse/scripts/body_gtm.dat)
sed -i.bak "0,/<body>/{s/<body>/<body>${BODY_GTM}/}" ./faunadb-java/api/index.html

rm ./faunadb-java/api/index.html.bak

echo "================================="
echo "Scala project docs"
echo "================================="

echo "Copying..."

mkdir faunadb-scala
cp -R "../../fauna-jvm-repository/faunadb-scala/target/scala-2.12/api" faunadb-scala

echo "Adding google manager tag to head..."

HEAD_GTM=$(cat ../../fauna-jvm-repository/concourse/scripts/head_gtm.dat)
sed -i.bak "0,/<\/title>/{s/<\/title>/<\/title>${HEAD_GTM}/}" ./faunadb-scala/api/index.html

echo "Adding google manager tag to body..."

BODY_GTM=$(cat ../../fauna-jvm-repository/concourse/scripts/body_gtm.dat)
sed -i.bak "0,/<body>/{s/<body>/<body>${BODY_GTM}/}" ./faunadb-scala/api/index.html

rm ./faunadb-scala/api/index.html.bak

git config --global user.email "nobody@fauna.com"
git config --global user.name "Fauna, Inc"

git add -A
git commit -m "Update docs to version: $PACKAGE_VERSION"
