/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.func;

/**
** Tuple classes that retain type-safety through generics. These classes can
** be used to create throwaway anonymous types that can be referred back to,
** unlike anonymous classes. They are also useful for when you want to have a
** {@link Closure} with more than 1 input parameter.
**
** Example usage:
**
**   import static plugins.Library.func.Tuples.*;
**   import plugins.Library.func.Closure;
**
**   ...
**
**   Closure<$2<Integer, String>> my_closure = new
**   Closure<$2<Integer, String>>() {
**
**       public void invoke($2<Integer, String> param) {
**
**       ...
**
**       }
**
**   }
**
**   my_closure.invoke($2(3, "test"));
**
** Note that (for n > 0) each n-tuple class extends the (n-1)-tuple class,
** recursively, so that you can give any (k+e)-tuple to a {@code Closure} that
** takes a k-tuple, assuming the types also match.
**
** @author infinity0
*/
final public class Tuples {

	private Tuples() {}

	/**
	** An immutable 0-tuple.
	*/
	public static class $0 {
		@Override public String toString() { return "()"; }
	}

	/**
	** An immutable 1-tuple.
	*/
	public static class $1<T0> extends $0 {
		final public T0 _0;
		public $1(T0 v0) { super(); _0 = v0; }
		@Override public String toString() { return "(" + _0 + ")"; }
	}

	/**
	** An immutable 2-tuple.
	*/
	public static class $2<T0, T1> extends $1<T0> {
		final public T1 _1;
		public $2(T0 v0, T1 v1) { super(v0); _1 = v1; }
		@Override public String toString() { return "(" + _0 + ", " + _1 + ")"; }
	}

	/**
	** An immutable 3-tuple.
	*/
	public static class $3<T0, T1, T2> extends $2<T0, T1> {
		final public T2 _2;
		public $3(T0 v0, T1 v1, T2 v2) { super(v0, v1); _2 = v2; }
		@Override public String toString() { return "(" + _0 + ", " + _1 + ", " + _2 + ")"; }
	}

	/**
	** An immutable 4-tuple.
	*/
	public static class $4<T0, T1, T2, T3> extends $3<T0, T1, T2> {
		final public T3 _3;
		public $4(T0 v0, T1 v1, T2 v2, T3 v3) { super(v0, v1, v2); _3 = v3; }
		@Override public String toString() { return "(" + _0 + ", " + _1 + ", " + _2 + ", " + _3 + ")"; }
	}

	final public static $0 $0 = new $0();

	/**
	** Returns a {@link $0 0-tuple}.
	*/
	public static $0 $0() {
		return $0;
	}

	/**
	** Creates a new {@link $1 1-tuple} from the given parameters.
	*/
	public static <T0> $1<T0> $1(T0 v0) {
		return new $1<T0>(v0);
	}

	/**
	** Creates a new {@link $2 2-tuple} from the given parameters.
	*/
	public static <T0, T1> $2<T0, T1> $2(T0 v0, T1 v1) {
		return new $2<T0, T1>(v0, v1);
	}

	/**
	** Creates a new {@link $3 3-tuple} from the given parameters.
	*/
	public static <T0, T1, T2> $3<T0, T1, T2> $3(T0 v0, T1 v1, T2 v2) {
		return new $3<T0, T1, T2>(v0, v1, v2);
	}

	/**
	** Creates a new {@link $4 4-tuple} from the given parameters.
	*/
	public static <T0, T1, T2, T3> $4<T0, T1, T2, T3> $4(T0 v0, T1 v1, T2 v2, T3 v3) {
		return new $4<T0, T1, T2, T3>(v0, v1, v2, v3);
	}

}
