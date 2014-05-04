#!/bin/bash

dir=$1

if [ ! -d "$dir" ]; then
  echo "$dir is a not directory"
  exit 1
fi

cp -r /Users/bbusjaeger/projects/guava-libraries/guava/target/classes $dir/guava
cp -r /Users/bbusjaeger/projects/guava-libraries/guava-testlib/target/classes $dir/guava-testlib
cp -r /Users/bbusjaeger/projects/guava-libraries/guava-tests/target/test-classes $dir/guava-tests
cp /Users/bbusjaeger/.m2/repository/com/google/code/findbugs/jsr305/1.3.9/jsr305-1.3.9.jar $dir
cp /Users/bbusjaeger/.m2/repository/junit/junit/4.8.2/junit-4.8.2.jar $dir
cp /Users/bbusjaeger/.m2/repository/org/easymock/easymock/3.0/easymock-3.0.jar $dir
cp /Users/bbusjaeger/.m2/repository/cglib/cglib-nodep/2.2/cglib-nodep-2.2.jar $dir
cp /Users/bbusjaeger/.m2/repository/org/objenesis/objenesis/1.2/objenesis-1.2.jar $dir
cp /Users/bbusjaeger/.m2/repository/org/mockito/mockito-core/1.8.5/mockito-core-1.8.5.jar $dir
cp /Users/bbusjaeger/.m2/repository/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar $dir
cp /Users/bbusjaeger/.m2/repository/org/truth0/truth/0.13/truth-0.13.jar $dir
cp /Users/bbusjaeger/.m2/repository/com/google/caliper/caliper/0.5-rc1/caliper-0.5-rc1.jar $dir
cp /Users/bbusjaeger/.m2/repository/com/google/code/gson/gson/1.7.1/gson-1.7.1.jar $dir
cp /Users/bbusjaeger/.m2/repository/com/google/code/java-allocation-instrumenter/java-allocation-instrumenter/2.0/java-allocation-instrumenter-2.0.jar $dir
cp /Users/bbusjaeger/.m2/repository/asm/asm/3.3.1/asm-3.3.1.jar $dir
cp /Users/bbusjaeger/.m2/repository/asm/asm-analysis/3.3.1/asm-analysis-3.3.1.jar $dir
cp /Users/bbusjaeger/.m2/repository/asm/asm-commons/3.3.1/asm-commons-3.3.1.jar $dir
cp /Users/bbusjaeger/.m2/repository/asm/asm-tree/3.3.1/asm-tree-3.3.1.jar $dir
cp /Users/bbusjaeger/.m2/repository/asm/asm-util/3.3.1/asm-util-3.3.1.jar $dir
cp /Users/bbusjaeger/.m2/repository/asm/asm-xml/3.3.1/asm-xml-3.3.1.jar $dir
