#! /bin/bash
#
# Check for version hygiene and samples all pass before releasing
#

echo "==== Checking top-level for dependencies up to date"
lein ancient

echo "==== Checking samples for dependencies up to date"
pushd samples

for d in *; do
    pushd $d
    echo "===== $d"
    lein ancient
    popd
done
popd

echo "==== Running tests in samples"
pushd samples

for d in *; do
    pushd $d
    echo "===== $d"
    lein test
    popd
done
popd


echo "==== Look at all project.clj version declarations"
find . -path ./template/src -prune -o -name "project.clj" -print | xargs grep defproject

echo "==== Check copyright declarations"
CURRENT_YEAR=`date +%Y`
find . \( -path ./.git -o -path ./samples/pet-store/resources -o -path ./samples/petstore-full/resources -o -path ./script \) -prune -o -type f -print | xargs grep -i "copyright.*20" | grep -v Relevance | grep -v $CURRENT_YEAR
