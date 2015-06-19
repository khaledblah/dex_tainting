import java.io.File;
import java.io.IOException;
import java.lang.Iterable;
import java.lang.String;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.builder.BuilderInstruction;
import org.jf.dexlib2.builder.MutableMethodImplementation;
import org.jf.dexlib2.builder.instruction.BuilderInstruction21c;
import org.jf.dexlib2.builder.instruction.BuilderInstruction22c;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.value.EncodedValue;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableMethod;
import org.jf.dexlib2.immutable.ImmutableField;
import org.jf.dexlib2.immutable.reference.ImmutableFieldReference;
import org.jf.dexlib2.immutable.value.ImmutableIntEncodedValue;
import org.jf.dexlib2.rewriter.ClassDefRewriter;
import org.jf.dexlib2.rewriter.DexRewriter;
import org.jf.dexlib2.rewriter.RewriterModule;
import org.jf.dexlib2.rewriter.Rewriter;
import org.jf.dexlib2.rewriter.Rewriters;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class DexTainting {
  private static final List<ClassDef> classes = Lists.newArrayList();

  public static DexFile addField(DexFile dexFile, final String className, final Field field) {
    DexRewriter rewriter = new DexRewriter(new RewriterModule() {
      @Nonnull @Override public Rewriter<ClassDef> getClassDefRewriter(@Nonnull Rewriters rewriters) {
        return new ClassDefRewriter(rewriters) {
          @Nonnull @Override public ClassDef rewrite(@Nonnull ClassDef classDef) {
            if (classDef.getType().equals(className)) {
              return new RewrittenClassDef(classDef) {
                @Nonnull @Override public Iterable<? extends Field> getInstanceFields() {
                  if ((field.getAccessFlags() & AccessFlags.STATIC.getValue()) == 0) {
                    return Iterables.concat(super.getInstanceFields(), ImmutableList.of(field));
                  }
                  return super.getInstanceFields();
                }

                @Nonnull @Override public Iterable<? extends Field> getStaticFields() {
                  if ((field.getAccessFlags() & AccessFlags.STATIC.getValue()) != 0) {
                    return Iterables.concat(super.getStaticFields(), ImmutableList.of(field));
                  }
                  return super.getStaticFields();
                }
              };
            }
            return super.rewrite(classDef);
          }
        };
      }
    });

    return rewriter.rewriteDexFile(dexFile);
  }

  public static void main(String args[]) throws IOException {
    if (args.length < 2) {
      System.out.println("Usage: inFile.dex outFile.dex");
      return;
    }

    String inFile = args[0];
    String outFile = args[1];

    DexFile dexFile = readDexFile(inFile);
    EncodedValue value = new ImmutableIntEncodedValue(0);
    for (ClassDef classDef: dexFile.getClasses()) {
      Field field = new ImmutableField(classDef.getType(), "__taint__", "I", AccessFlags.PUBLIC.getValue(), value, null);
      dexFile = addField(dexFile, classDef.getType(), field);
    }
    taintDexFile(dexFile);

    DexFileFactory.writeDexFile(outFile, new DexFile() {
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
      classDef.getFields(),
      taintDexMethods(classDef));
  }

  private static List<Method> taintDexMethods(@Nonnull ClassDef classDef) {
    List<Method> taintedMethods = Lists.newArrayList();
    for (Method method: classDef.getMethods()) {
      MethodImplementation implementation = method.getImplementation();
      MutableMethodImplementation mutableImplementation = new MutableMethodImplementation(implementation);
      List<BuilderInstruction> instructions = mutableImplementation.getInstructions();
      Reference fieldRef = null;
      // FieldReference fieldRef = null;
      // for(Field field : classDef.getFields()) {
      //   if (field.getDefiningClass().equals(classDef.getType())
      //       && field.getName().equals("__taint__")) {
      //     fieldRef = field;
      //     break;
      //   }
      // }

      int ni_index = -1;
      int object_register = -1;
      for (int i = 0; i < instructions.size(); i++) {
        System.out.println("ni_index: " + ni_index + ", index: " + i);
        Instruction instruction = instructions.get(i);
        if (fieldRef != null && object_register >= 0 && i == (ni_index + 2)) {
          System.out.println("add instruction");
          mutableImplementation.addInstruction(i, new BuilderInstruction22c(Opcode.IPUT, object_register, object_register, fieldRef));
          ni_index = -1;
          object_register = -1;
        }
        if (instruction.getOpcode() == Opcode.NEW_INSTANCE) {
          System.out.println("new-instance: " + i);
          System.out.println("new-instance: " + instruction.getOpcode());
          BuilderInstruction21c old_instruction = (BuilderInstruction21c) instruction;
          object_register = old_instruction.getRegisterA();
          fieldRef = old_instruction.getReference();
          int ref_type = old_instruction.getReferenceType();
          System.out.println("old_instruction: " + old_instruction.getClass().getName());
          System.out.println("fieldRef: " + fieldRef.getClass().getName());
          System.out.println("ref_type: " + ref_type);
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
