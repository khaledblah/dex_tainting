import java.lang.Integer;
import java.lang.Math;

public class Test {
  private int fieldA = 0;
  private long fieldB = 1;
  private float fieldC = 2.0f;
  private double fieldD = 3.0d;

  public static void main(String argv[]) {
    Test a = new Test();
    a.fieldA = 1;
    a.fieldB = 2;
    System.out.println(Integer.toString(a.fieldA));
    Object d = new Object();
    Foo e = new Foo();
    e.bar = true;
    Foo f = new Foo();
    f.bar = e.bar;
    return;
  }
}
