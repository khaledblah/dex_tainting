.PHONY: all clean InstrumentationTest DexTainting

CLASSPATH=-cp .:jars/dexlib2-2.0.5-dev.jar:jars/jsr305-1.3.9.jar:jars/util-2.0.5-dev.jar:jars/guava-18.0.jar

all: DexTainting

clean:
	rm -f *.dex *.dump *.class

Test.class: Test.java
	javac -source 1.7 -target 1.7 Test.java

Test.dex: Test.class
	dx --dex --output=Test.dex Test.class

HashMapTest.class: HashMapTest.java
	javac -source 1.7 -target 1.7 HashMapTest.java

HashMapTest.dex: HashMapTest.class
	dx --dex --output=HashMapTest.dex HashMapTest.class
	dexdump -h -d HashMapTest.dex

InstrumentationTest.class: InstrumentationTest.java
	javac $(CLASSPATH) InstrumentationTest.java

InstrumentationTest: InstrumentationTest.class Test.dex
	java $(CLASSPATH) InstrumentationTest Test.dex TestInst.dex
	dexdump -h -d TestInst.dex

DexTainting.class: DexTainting.java
	javac $(CLASSPATH) DexTainting.java

DexTainting: DexTainting.class Test.dex
	java $(CLASSPATH) DexTainting Test.dex TestInst.dex
	dexdump -h -d TestInst.dex
