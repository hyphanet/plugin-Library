/*
 * This code is part of Freenet. It is distributed under the GNU General Public License, version 2
 * (or at your option any later version). See http://www.gnu.org/ for further details of the GPL.
 */
package plugins.Library.util.func;

/**
 ** Tuple classes that retain type-safety through generics. These classes can be used to create
 * throwaway anonymous types that can be referred back to, unlike anonymous classes. They are also
 * useful for when you want to have a {@link Closure} with more than 1 input parameter.
 **
 ** Example usage:
 **
 ** import static plugins.Library.func.Tuples.*; import plugins.Library.func.Closure;
 **
 ** ...
 **
 ** Closure<X2<Integer, String>> my_closure = new Closure<X2<Integer, String>>() {
 **
 ** public void invoke(X2<Integer, String> param) {
 **
 ** ...
 **
 ** }
 **
 ** }
 **
 ** my_closure.invoke(X2(3, "test"));
 **
 ** Note that (for n > 0) each n-tuple class extends the (n-1)-tuple class, recursively, so that you
 * can give any (k+e)-tuple to a {@code Closure} that takes a k-tuple, assuming the types also
 * match.
 **
 ** TODO NORM override equals() and hashCode() for all of these
 **
 ** @author infinity0
 */
final public class Tuples {

  private Tuples() {}

  /**
   ** An immutable 0-tuple.
   */
  public static class X0 {
    @Override
    public String toString() {
      return "()";
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof X0 && !(o instanceof X1);
    }
  }

  /**
   ** An immutable 1-tuple.
   */
  public static class X1<T0> extends X0 {
    final public T0 _0;

    public X1(T0 v0) {
      super();
      _0 = v0;
    }

    @Override
    public String toString() {
      return "(" + _0 + ")";
    }

    @Override
    public int hashCode() {
      return _0.hashCode() + 1;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof X1) || o instanceof X2) {
        return false;
      }
      @SuppressWarnings("unchecked")
      X1<T0> x = (X1<T0>) o;
      return (_0 == null ? x._0 == null : _0.equals(x._0));
    }
  }

  /**
   ** An immutable 2-tuple.
   */
  public static class X2<T0, T1> extends X1<T0> {
    final public T1 _1;

    public X2(T0 v0, T1 v1) {
      super(v0);
      _1 = v1;
    }

    @Override
    public String toString() {
      return "(" + _0 + ", " + _1 + ")";
    }

    @Override
    public int hashCode() {
      return _0.hashCode() + _1.hashCode() + 2;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof X2) || o instanceof X3) {
        return false;
      }
      @SuppressWarnings("unchecked")
      X2<T0, T1> x = (X2<T0, T1>) o;
      return (_0 == null ? x._0 == null : _0.equals(x._0))
          && (_1 == null ? x._1 == null : _1.equals(x._1));
    }
  }

  /**
   ** An immutable 3-tuple.
   */
  public static class X3<T0, T1, T2> extends X2<T0, T1> {
    final public T2 _2;

    public X3(T0 v0, T1 v1, T2 v2) {
      super(v0, v1);
      _2 = v2;
    }

    @Override
    public String toString() {
      return "(" + _0 + ", " + _1 + ", " + _2 + ")";
    }

    @Override
    public int hashCode() {
      return _0.hashCode() + _1.hashCode() + _2.hashCode() + 3;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof X3) || o instanceof X4) {
        return false;
      }
      @SuppressWarnings("unchecked")
      X3<T0, T1, T2> x = (X3<T0, T1, T2>) o;
      return (_0 == null ? x._0 == null : _0.equals(x._0))
          && (_1 == null ? x._1 == null : _1.equals(x._1))
          && (_2 == null ? x._2 == null : _2.equals(x._2));
    }
  }

  /**
   ** An immutable 4-tuple.
   */
  public static class X4<T0, T1, T2, T3> extends X3<T0, T1, T2> {
    final public T3 _3;

    public X4(T0 v0, T1 v1, T2 v2, T3 v3) {
      super(v0, v1, v2);
      _3 = v3;
    }

    @Override
    public String toString() {
      return "(" + _0 + ", " + _1 + ", " + _2 + ", " + _3 + ")";
    }

    @Override
    public int hashCode() {
      return _0.hashCode() + _1.hashCode() + _2.hashCode() + _3.hashCode() + 4;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof X4) /* || o instanceof X5 */) {
        return false;
      }
      @SuppressWarnings("unchecked")
      X4<T0, T1, T2, T3> x = (X4<T0, T1, T2, T3>) o;
      return (_0 == null ? x._0 == null : _0.equals(x._0))
          && (_1 == null ? x._1 == null : _1.equals(x._1))
          && (_2 == null ? x._2 == null : _2.equals(x._2))
          && (_3 == null ? x._3 == null : _3.equals(x._3));
    }
  }

  final private static X0 X0 = new X0();

  /**
   ** Returns a {@link X0 0-tuple}.
   */
  public static X0 X0() {
    return X0;
  }

  /**
   ** Creates a new {@link X1 1-tuple} from the given parameters.
   */
  public static <T0> X1<T0> X1(T0 v0) {
    return new X1<T0>(v0);
  }

  /**
   ** Creates a new {@link X2 2-tuple} from the given parameters.
   */
  public static <T0, T1> X2<T0, T1> X2(T0 v0, T1 v1) {
    return new X2<T0, T1>(v0, v1);
  }

  /**
   ** Creates a new {@link X3 3-tuple} from the given parameters.
   */
  public static <T0, T1, T2> X3<T0, T1, T2> X3(T0 v0, T1 v1, T2 v2) {
    return new X3<T0, T1, T2>(v0, v1, v2);
  }

  /**
   ** Creates a new {@link X4 4-tuple} from the given parameters.
   */
  public static <T0, T1, T2, T3> X4<T0, T1, T2, T3> X4(T0 v0, T1 v1, T2 v2, T3 v3) {
    return new X4<T0, T1, T2, T3>(v0, v1, v2, v3);
  }

}
