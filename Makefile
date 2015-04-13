.PHONY: all clean

CLASSPATH=-cp .:jars/dexlib2-2.0.5-dev.jar:jars/jsr305-1.3.9.jar:jars/util-2.0.5-dev.jar:jars/guava-18.0.jar

all: TestJava TestDex DexTainting DexTaintingExec

clean:
	rm -f *.dex *.dump *.class

TestJava: Test.java
	javac -source 1.7 -target 1.7 Test.java

TestDex: Test.class
	dx --dex --output=Test.dex Test.class

InstrumentationTest: InstrumentationTest.java
	javac $(CLASSPATH) InstrumentationTest.java

InstrumentationTestClass: InstrumentationTest.class TestDex
	java $(CLASSPATH) InstrumentationTest Test.dex TestInst.dex
	dexdump -h -d TestInst.dex

DexTainting: DexTainting.java
	javac $(CLASSPATH) DexTainting.java

DexTaintingExec: DexTainting.class TestDex
	java $(CLASSPATH) DexTainting Test.dex TestInst.dex
	dexdump -h -d TestInst.dex
