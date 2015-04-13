import java.io.File;
import java.io.IOException;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.builder.BuilderInstruction;
import org.jf.dexlib2.builder.MutableMethodImplementation;
import org.jf.dexlib2.builder.instruction.BuilderInstruction21c;
import org.jf.dexlib2.builder.instruction.BuilderInstruction31i;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableMethod;

import com.google.common.collect.Lists;

public class DexTainting {
  private static final List<ClassDef> classes = Lists.newArrayList();

  public static void main(String args[]) throws IOException {
    if (args.length < 2) {
      System.out.println("Usage: inFile.dex outFile.dex");
      return;
    }

    String inFile = args[0];
    String outFile = args[1];

    DexFile dexFile = readDexFile(inFile);
    taintDexFile(dexFile);

    DexFileFactory.writeDexFile(args[1], new DexFile() {
      @Nonnull @Override public Set<? extends ClassDef> getClasses() {
        return new AbstractSet<ClassDef>() {
          @Nonnull @Override public Iterator<ClassDef> iterator() {
            return classes.iterator();
          }
   
          @Override public int size() {
            return classes.size();
          }
        };
      }
    });
  }
  
  private static DexFile readDexFile(String fileName) throws IOException {
    File srcFile = new File(fileName);
    return DexFileFactory.loadDexFile(srcFile, 15);
  }

  private static void taintDexFile(@Nonnull DexFile dexFile) {
    for (ClassDef classDef: dexFile.getClasses()) {
      classes.add(taintDexClass(classDef));
    }
  }
  
  private static ClassDef taintDexClass(@Nonnull ClassDef classDef) {
    return new ImmutableClassDef(classDef.getType(),
      classDef.getAccessFlags(),
      classDef.getSuperclass(),
      classDef.getInterfaces(),
      classDef.getSourceFile(),
      classDef.getAnnotations(),
      taintDexClassFields(classDef),
      taintDexMethods(classDef));
  }

  private static List<Field> taintDexClassFields(@Nonnull ClassDef classDef) {
    List<Field> taintedFields = Lists.newArrayList();
    for (Field field: classDef.getFields()) {
      // TODO: taint fields
      taintedFields.add(field);
    }
    return taintedFields;
  }

  private static List<Method> taintDexMethods(@Nonnull ClassDef classDef) {
    List<Method> taintedMethods = Lists.newArrayList();
    for (Method method: classDef.getMethods()) {
      MethodImplementation implementation = method.getImplementation();
      MutableMethodImplementation mutableImplementation = new MutableMethodImplementation(implementation);
      List<BuilderInstruction> instructions = mutableImplementation.getInstructions();

      int ni_index = -1;
      for (int i = 0; i < instructions.size(); i++) {
        Instruction instruction = instructions.get(i);
        if (ni_index >= 0 && i == (ni_index + 2)) {
          int register = ((OneRegisterInstruction)instruction).getRegisterA();
          mutableImplementation.addInstruction(ni_index + 2,
            new BuilderInstruction31i(Opcode.CONST_WIDE_32, register, 0));
          ni_index = -1;
        }
        if (instruction.getOpcode() == Opcode.NEW_INSTANCE) {
          ni_index = i;
        }
      }

      taintedMethods.add(new ImmutableMethod(
        method.getDefiningClass(),
        method.getName(),
        method.getParameters(),
        method.getReturnType(),
        method.getAccessFlags(),
        method.getAnnotations(),
        mutableImplementation));
    }
    return taintedMethods;
  }
}
