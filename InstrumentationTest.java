import com.google.common.collect.Lists;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.builder.BuilderInstruction;
import org.jf.dexlib2.builder.MutableMethodImplementation;
import org.jf.dexlib2.builder.instruction.BuilderInstruction11x;
import org.jf.dexlib2.builder.instruction.BuilderInstruction21c;
import org.jf.dexlib2.builder.instruction.BuilderInstruction35c;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableMethod;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.jf.dexlib2.immutable.reference.ImmutableStringReference;
 
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
 
public class InstrumentationTest {
    public static void main(String[] args) throws IOException {
        File srcFile = new File(args[0]);
        DexFile dexFile = DexFileFactory.loadDexFile(srcFile, 15);
 
        final List<ClassDef> classes = Lists.newArrayList();
 
        for (ClassDef classDef: dexFile.getClasses()) {
            List<Method> methods = Lists.newArrayList();
            boolean modifiedMethod = false;
 
            for (Method method: classDef.getMethods()) {
                MethodImplementation implementation = method.getImplementation();
                if (implementation != null) { // && methodNeedsModification(implementation)) {
                    modifiedMethod = true;
                    methods.add(new ImmutableMethod(
                            method.getDefiningClass(),
                            method.getName(),
                            method.getParameters(),
                            method.getReturnType(),
                            method.getAccessFlags(),
                            method.getAnnotations(),
                            modifyMethod(implementation)));
                } else {
                    methods.add(method);
                }
            }
 
            if (!modifiedMethod) {
                classes.add(classDef);
            } else {
                classes.add(new ImmutableClassDef(
                        classDef.getType(),
                        classDef.getAccessFlags(),
                        classDef.getSuperclass(),
                        classDef.getInterfaces(),
                        classDef.getSourceFile(),
                        classDef.getAnnotations(),
                        classDef.getFields(),
                        methods));
            }
        }
 
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
 
    private static boolean methodNeedsModification(@Nonnull MethodImplementation implementation) {
        for (Instruction instruction: implementation.getInstructions()) {
            System.out.println("opcode: " + Integer.toHexString(instruction.getOpcode().value) + "\tclass name: " + instruction.getClass().getName());
            if (instruction instanceof TwoRegisterInstruction) {
              TwoRegisterInstruction tri = (TwoRegisterInstruction) instruction;
              System.out.println("vA: " + tri.getRegisterA() + "\t\tvB: " + tri.getRegisterB());
            }
            else if (instruction instanceof OneRegisterInstruction) {
              OneRegisterInstruction one = (OneRegisterInstruction) instruction;
              System.out.println("vA: " + one.getRegisterA());
            }
            if (instruction instanceof NarrowLiteralInstruction) {
                // a rough heuristic for detecting resource ids
                if ((((NarrowLiteralInstruction)instruction).getNarrowLiteral() >> 24) == 0x7f) {
                    return true;
                }
            }
        }
        return false;
    }
 
    private static MethodImplementation modifyMethod(@Nonnull MethodImplementation implementation) {
        MutableMethodImplementation mutableImplementation = new MutableMethodImplementation(implementation);
 
        List<BuilderInstruction> instructions = mutableImplementation.getInstructions();

        boolean insAdded = false; 
        for (int i=0; i<instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            if (insAdded == false && instruction.getOpcode() == Opcode.INVOKE_STATIC) {
              System.out.println("modify");
              int register = ((BuilderInstruction35c)instruction).getRegisterC();
              mutableImplementation.addInstruction(i++,
                                    new BuilderInstruction35c(Opcode.INVOKE_STATIC, 1, register, 0, 0, 0, 0,
                                      new ImmutableMethodReference("Ljava/io/PrintStream;", "print",
                                            Lists.newArrayList("Ljava/lang/String;"), "V")));
              insAdded = true;
            }
            if (instruction instanceof NarrowLiteralInstruction) {
                // a rough heuristic for detecting resource ids
                if ((((NarrowLiteralInstruction)instruction).getNarrowLiteral() >> 24) == 0x7f) {
                    int register = ((OneRegisterInstruction)instruction).getRegisterA();
 
                    // const-string vN, "R.id.blah"
                    mutableImplementation.replaceInstruction(i++,
                            new BuilderInstruction21c(Opcode.CONST_STRING, register,
                                    new ImmutableStringReference("R.id.blah")));
 
                    // invoke-static {vN} Lmy/utility/class;->methodToGetId(Ljava/lang/String;)I
                    mutableImplementation.addInstruction(i++,
                            new BuilderInstruction35c(Opcode.INVOKE_STATIC, 1, register, 0, 0, 0, 0,
                                    new ImmutableMethodReference("Lmy/utility/class;", "methodToGetId",
                                            Lists.newArrayList("Ljava/lang/String;"), "I")));

                    // move-result vN
                    mutableImplementation.addInstruction(i++,
                            new BuilderInstruction11x(Opcode.MOVE_RESULT, register));
                }
            }
        }
 
        return mutableImplementation;
    }
}
