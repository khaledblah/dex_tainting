import java.lang.Integer;
import java.lang.Math;

public class Test {
  private int fieldA = 0;
  private long fieldB = 1;
  private float fieldC = 2.0f;
  private double fieldD = 3.0d;

  public static class Foo {
    public boolean bar = false;
  }

  public static void main(String argv[]) {
    int a = 0;
    int b = 1;
    int c = a + b;
    System.out.println(Integer.toString(c));
    Object d = new Object();
    Foo e = new Foo();
    e.bar = true;
    Foo f = new Foo();
    f.bar = e.bar;
    return;
  }
}
