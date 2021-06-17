package com.faunadb.client.query;

import com.faunadb.client.types.Encoder;
import com.faunadb.client.types.Value.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static com.faunadb.client.util.SymbolGenerator.genSym;

/**
 * The {@link Language} class contains static constructors for the FaunaDB Query Language expressions.
 * This class is intended to be statically imported into your code with:
 *
 * {@code import static com.faunadb.client.query.Language.*;}
 *
 * <p>
 * Each method in the {@link Language} class constructs a new {@link Expr} instance.
 * No computation is executed until the expression is evaluated by the FaunaDB server.
 * </p>
 *
 * <b>Example:</b>
 * <pre>{@code
 *   // Creates a new expression that, once executed, it will create a new instance
 *   // of the user collection with an username and password fields.
 *   Expr createUserExpr = Create(Collection("user"), Obj("data", Obj(
 *     "username", Value("bob"),
 *     "password", Value("abc123"),
 *   )));
 *
 *   // Executes the expression created above and get its result.
 *   Value result = client.query(createUserExpr).get();
 * }</pre>
 *
 * @see <a href="https://docs.fauna.com/fauna/current/api/fql/">Fauna Query Language</a>
 */
public final class Language {

  private Language() {
  }

  private static Expr varargs(List<? extends Expr> exprs) {
    if (exprs.size() == 1) {
      return exprs.get(0);
    } else {
      return Arr(exprs);
    }
  }

  /**
   * Enumeration for time units.
   *
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/cheat_sheet#timedate">Fauna Time Functions</a>
   * @see #Epoch(Expr, TimeUnit)
   * @see #TimeAdd(Expr, Expr, TimeUnit)
   * @see #TimeSubtract(Expr, Expr, TimeUnit)
   * @see #TimeDiff(Expr, Expr, TimeUnit)
   */
  public enum TimeUnit {
    DAY("day"),
    HALF_DAY("half day"),
    HOUR("hour"),
    MINUTE("minute"),
    SECOND("second"),
    MILLISECOND("millisecond"),
    MICROSECOND("microsecond"),
    NANOSECOND("nanosecond");

    private final Expr value;

    TimeUnit(String value) {
      this.value = Value(value);
    }
  }

  /**
   * Enumeration for event action types.
   *
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/cheat_sheet#write">Fauna Write Functions</a>
   * @see #Insert(Expr, Expr, Action, Expr)
   * @see #Remove(Expr, Expr, Action)
   */
  public enum Action {
    CREATE("create"),
    DELETE("delete");

    private final Expr value;

    Action(String value) {
      this.value = Value(value);
    }
  }

  /**
   * Enumeration for casefold normalizers.
   *
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/cheat_sheet#string">Fauna String Functions</a>
   * @see #Casefold(Expr, Normalizer)
   */
  public enum Normalizer {
    NFD("NFD"),
    NFC("NFC"),
    NFKD("NFKD"),
    NFKC("NFKC"),
    NFKCCaseFold("NFKCCaseFold");

    private final Expr value;

    Normalizer(String value) {
      this.value = Value(value);
    }
  }

  /**
   * Builder for let expressions.
   * To complete the let binding, the {@link LetBinding#in(Expr)} method must be called.
   *
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/cheat_sheet#basic">Fauna Basic Forms</a>
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/let?lang=java">Let</a>
   * @see #Let(Map)
   * @see #Let(String, Expr)
   */
  public static final class LetBinding {

    private final Expr bindings;

    private LetBinding(List<Expr> bindings) {
      this.bindings = Fn.apply(bindings);
    }

    /**
     * Defines the scope where the let bindings will apply.
     * @param in the scope where the let bindings apply
     * @return a new {@link Expr} instance
     */
    public Expr in(Expr in) {
      return Fn.apply("let", bindings, "in", in);
    }
  }

  /**
   * Builder for path selectors. This builder must be constructed using
   * either the {@link Path#at(String...)} or {@link Path#at(int...)} functions.
   *
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/cheat_sheet#miscellaneous">Fauna Miscellaneous Functions</a>
   * @see #Contains(Path, Expr)
   * @see #Select(Path, Expr)
   * @see #Select(Path, Expr, Expr)
   * @see #SelectAll(Path, Expr)
   */
  public static final class Path {

    private final List<Expr> segments;

    private Path() {
      this(Collections.emptyList());
    }

    private Path(List<Expr> segments) {
      this.segments = segments;
    }

    /**
     * Narrow to a specific path in a object key.
     *
     * @param others path selectors
     * @return a new narrowed path
     */
    public Path at(String... others) {
      List<Expr> all = new ArrayList<>(segments.size() + others.length);
      all.addAll(segments);

      for (String segment : others) {
        all.add(Value(segment));
      }

      return new Path(Collections.unmodifiableList(all));
    }

    /**
     * Narrow to a specific element index in an array.
     *
     * @param others path selectors
     * @return a new narrowed path
     */
    public Path at(int... others) {
      List<Expr> all = new ArrayList<>(segments.size() + others.length);
      all.addAll(segments);

      for (int segment : others) {
        all.add(Value(segment));
      }

      return new Path(Collections.unmodifiableList(all));
    }

  }

  /**
   * Creates a new reference.
   * For example {@code Ref(Collection("users"), "123")}.
   *
   * <p>
   * The usage of this method is discouraged.
   * Prefer {@link #Ref(Expr, String)} or {@link #Ref(Expr, Expr)}.
   * </p>
   *
   * @param ref the reference id
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/ref?lang=java">Ref</a>
   * @see #Ref(Expr, String)
   */
  public static Expr Ref(String ref) {
    return Fn.apply("@ref", Value(ref));
  }

  /**
   * Creates a new scoped reference.
   * For example {@code Ref(Collection("users"), "123")}.
   *
   * @param collectionRef the scope reference. Type: Reference
   * @param id the reference id. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/ref?lang=java">Ref</a>
   * @see #Collection(String)
   * @see #Database(String)
   * @see #Index(String)
   * @see #Function(String)
   */
  public static Expr Ref(Expr collectionRef, Expr id) {
    return Fn.apply("ref", collectionRef, "id", id);
  }

  /**
   * Creates a new scoped reference.
   * For example {@code Ref(Collection("users"), "123")}.
   *
   * @param collectionRef the scope reference. Type: Reference
   * @param id the reference id
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/ref?lang=java">Ref</a>
   * @see #Collection(String)
   * @see #Database(String)
   * @see #Index(String)
   * @see #Function(String)
   */
  public static Expr Ref(Expr collectionRef, String id) {
    return Ref(collectionRef, Value(id));
  }

  /**
   * Returns a reference to a set of all classes in the database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/classes?lang=java">Classes</a>
   * @see #Paginate(Expr)
   *
   * @deprecated use Collections instead
   */
  @Deprecated
  public static Expr Classes() {
    return Classes(Null());
  }

  /**
   * Returns a reference to a set of all collections in the database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/collections?lang=java">Collections</a>
   * @see #Paginate(Expr)
   */
  public static Expr Collections() {
    return Collections(Null());
  }

  /**
   * Returns a reference to a set of all classes in the specified database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @param scope a reference to a database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/classes?lang=java">Classes</a>
   * @see #Database(String)
   * @see #Paginate(Expr)
   *
   * @deprecated use Collection instead
   */
  @Deprecated
  public static Expr Classes(Expr scope) {
    return Fn.apply("classes", scope);
  }

  /**
   * Returns a reference to a set of all collections in the specified database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @param scope a reference to a database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/collections?lang=java">Collections</a>
   * @see #Database(String)
   * @see #Paginate(Expr)
   */
  public static Expr Collections(Expr scope) {
    return Fn.apply("collections", scope);
  }

  /**
   * Returns a reference to a set of all databases.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/databases?lang=java">Databases</a>
   * @see #Paginate(Expr)
   */
  public static Expr Databases() {
    return Databases(Null());
  }

  /**
   * Returns a reference to a set of all databases in the specified database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @param scope a reference to a database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/databases?lang=java">Databases</a>
   * @see #Database(String)
   * @see #Paginate(Expr)
   */
  public static Expr Databases(Expr scope) {
    return Fn.apply("databases", scope);
  }

  /**
   * Returns a reference to a set of all indexes in the database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/indexes?lang=java">Indexes</a>
   * @see #Paginate(Expr)
   */
  public static Expr Indexes() {
    return Indexes(Null());
  }

  /**
   * Returns a reference to a set of all indexes in the specified database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @param scope a reference to a database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/indexes?lang=java">Indexes</a>
   * @see #Paginate(Expr)
   */
  public static Expr Indexes(Expr scope) {
    return Fn.apply("indexes", scope);
  }

  /**
   * Returns a reference to a set of all user defined functions in the database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/functions?lang=java">Functions</a>
   * @see #Paginate(Expr)
   */
  public static Expr Functions() {
    return Functions(Null());
  }

  /**
   * Returns a reference to a set of all user defined functions in the specified database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @param scope a reference to a database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/functions?lang=java">Functions</a>
   * @see #Ref(Expr, String)
   * @see #Database(String)
   * @see #Paginate(Expr)
   */
  public static Expr Functions(Expr scope) {
    return Fn.apply("functions", scope);
  }

  /**
   * Returns a reference to a set of all keys in the database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/keys?lang=java">Keys</a>
   * @see #Paginate(Expr)
   */
  public static Expr Keys() {
    return Keys(Null());
  }

  /**
   * Returns a reference to a set of all keys in the specified database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @param scope a reference to a database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/keys?lang=java">Keys</a>
   * @see #Ref(Expr, String)
   * @see #Database(String)
   * @see #Paginate(Expr)
   */
  public static Expr Keys(Expr scope) {
    return Fn.apply("keys", scope);
  }

  /**
   * Returns a reference to a set of all tokens in the database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/tokens?lang=java">Tokens</a>
   * @see #Paginate(Expr)
   */
  public static Expr Tokens() {
    return Tokens(Null());
  }

  /**
   * Returns a reference to a set of all tokens in the specified database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @param scope a reference to a database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/tokens?lang=java">Tokens</a>
   * @see #Ref(Expr, String)
   * @see #Database(String)
   * @see #Paginate(Expr)
   */
  public static Expr Tokens(Expr scope) {
    return Fn.apply("tokens", scope);
  }

  /**
   * Returns a reference to a set of all credentials in the database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/credentials?lang=java">Credentials</a>
   * @see #Paginate(Expr)
   */
  public static Expr Credentials() {
    return Credentials(Null());
  }

  /**
   * Returns a reference to a set of all credentials in the specified database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @param scope a reference to a database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/credentials?lang=java">Credentials</a>
   * @see #Ref(Expr, String)
   * @see #Database(String)
   * @see #Paginate(Expr)
   */
  public static Expr Credentials(Expr scope) {
    return Fn.apply("credentials", scope);
  }

  /**
   * Returns a reference to a set of all roles in the database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/roles?lang=java">Roles</a>
   * @see #Paginate(Expr)
   */
  public static Expr Roles() {
    return Roles(Null());
  }

  /**
   * Returns a reference to a set of all roles in the specified database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @param scope a reference to a database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/roles?lang=java">Roles</a>
   * @see #Ref(Expr, String)
   * @see #Database(String)
   * @see #Paginate(Expr)
   */
  public static Expr Roles(Expr scope) {
    return Fn.apply("roles", scope);
  }

  /**
   * Returns a set of all documents in the given collection.
   * A set must be paginated in order to retrieve its values.
   *
   * @param collection a reference to the collection. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/documents?lang=java">Documents</a>
   * @see #Paginate(Expr)
   */
  public static Expr Documents(Expr collection) {
    return Fn.apply("documents", collection);
  }

  /**
   * Encodes the given object using the reflection {@link Encoder}.
   *
   * @param value the object to be encoded
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#object">Fauna Object type</a>
   * @see Encoder
   */
  public static Expr Value(Object value) {
    return Encoder.encode(value).get();
  }

  /**
   * Encodes the given {@link String} as an {@link Expr} instance.
   *
   * @param value the string to be encoded
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#string">Fauna String type</a>
   */
  public static Expr Value(String value) {
    return new StringV(value);
  }

  /**
   * Encodes the given {@link Long} as an {@link Expr} instance.
   *
   * @param value the number to be encoded
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#number">Fauna Number type</a>
   */
  public static Expr Value(long value) {
    return new LongV(value);
  }

  /**
   * Encodes the given {@link Double} as an {@link Expr} instance.
   *
   * @param value the number to be encoded
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#number">Fauna Number type</a>
   */
  public static Expr Value(double value) {
    return new DoubleV(value);
  }

  /**
   * Encodes the given {@link Boolean} as an {@link Expr} instance.
   *
   * @param value the boolean value to be encoded
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#boolean">Fauna Boolean type</a>
   */
  public static Expr Value(boolean value) {
    return BooleanV.valueOf(value);
  }

  /**
   * Encodes the given {@link Instant} as an {@link Expr} instance.
   *
   * @param value the timestamp to be encoded
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#timestamp">Fauna Timestamp type</a>
   * @see Instant
   */
  public static Expr Value(Instant value) {
    return new TimeV(value);
  }

  /**
   * Encodes the given {@link LocalDate} as an {@link Expr} instance.
   *
   * @param value the date to be encoded
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#timestamp">Fauna Timestamp type</a>
   * @see LocalDate
   */
  public static Expr Value(LocalDate value) {
    return new DateV(value);
  }

  /**
   * Encodes the given {@link Byte} array as an {@link Expr} instance.
   *
   * @param bytes the byte array to be encoded
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#byte">Fauna Byte type</a>
   */
  public static Expr Value(byte[] bytes) {
    return new BytesV(bytes);
  }

  /**
   * Creates a new expression representing a null value.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#null">Fauna Null type</a>
   */
  public static Expr Null() {
    return NullV.NULL;
  }

  /**
   * Creates a new object wrapping the {@link Map} provided.
   * For convenience, the following helpers are available:
   * <ul>
   *   <li>{@link #Obj()}</li>
   *   <li>{@link #Obj(String, Expr)}</li>
   *   <li>{@link #Obj(String, Expr, String, Expr)}</li>
   *   <li>{@link #Obj(String, Expr, String, Expr, String, Expr)}</li>
   *   <li>{@link #Obj(String, Expr, String, Expr, String, Expr, String, Expr)}</li>
   *   <li>{@link #Obj(String, Expr, String, Expr, String, Expr, String, Expr, String, Expr)}</li>
   * </ul>
   *
   * @param values the key/value {@link Map} to be wrapped
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#object">Fauna Object type</a>
   */
  public static Expr Obj(Map<String, ? extends Expr> values) {
    return Fn.apply("object", Fn.apply(values));
  }

  /**
   * Creates an empty object.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#object">Fauna Object type</a>
   */
  public static Expr Obj() {
    return Obj(Collections.emptyMap());
  }

  /**
   * Creates a new object with the provided key and value.
   *
   * @param k1 a key
   * @param v1 a value
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#object">Fauna Object type</a>
   */
  public static Expr Obj(String k1, Expr v1) {
    return Obj(Collections.singletonMap(k1, v1));
  }

  /**
   * Creates a new object with two key/value pairs.
   *
   * @param k1 the first key
   * @param v1 the first value
   * @param k2 the second key
   * @param v2 the second value
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#object">Fauna Object type</a>
   */
  public static Expr Obj(String k1, Expr v1, String k2, Expr v2) {
    Map<String, Expr> obj = new LinkedHashMap<>();
    obj.put(k1, v1);
    obj.put(k2, v2);
    return Obj(Collections.unmodifiableMap(obj));
  }

  /**
   * Creates a new object with three key/value pairs.
   *
   * @param k1 the first key
   * @param v1 the first value
   * @param k2 the second key
   * @param v2 the second value
   * @param k3 the third key
   * @param v3 the third value
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#object">Fauna Object type</a>
   */
  public static Expr Obj(String k1, Expr v1, String k2, Expr v2, String k3, Expr v3) {
    Map<String, Expr> obj = new LinkedHashMap<>();
    obj.put(k1, v1);
    obj.put(k2, v2);
    obj.put(k3, v3);
    return Obj(Collections.unmodifiableMap(obj));
  }

  /**
   * Creates a new object with four key/value pairs.
   *
   * @param k1 the first key
   * @param v1 the first value
   * @param k2 the second key
   * @param v2 the second value
   * @param k3 the third key
   * @param v3 the third value
   * @param k4 the fourth key
   * @param v4 the fourth value
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#object">Fauna Object type</a>
   */
  public static Expr Obj(String k1, Expr v1, String k2, Expr v2, String k3, Expr v3, String k4, Expr v4) {
    Map<String, Expr> obj = new LinkedHashMap<>();
    obj.put(k1, v1);
    obj.put(k2, v2);
    obj.put(k3, v3);
    obj.put(k4, v4);
    return Obj(Collections.unmodifiableMap(obj));
  }

  /**
   * Creates a new object with five key/value pairs.
   *
   * @param k1 the first key
   * @param v1 the first value
   * @param k2 the second key
   * @param v2 the second value
   * @param k3 the third key
   * @param v3 the third value
   * @param k4 the fourth key
   * @param v4 the fourth value
   * @param k5 the fifth key
   * @param v5 the fifth value
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#object">Fauna Object type</a>
   */
  public static Expr Obj(String k1, Expr v1, String k2, Expr v2, String k3, Expr v3, String k4, Expr v4, String k5, Expr v5) {
    Map<String, Expr> obj = new LinkedHashMap<>();
    obj.put(k1, v1);
    obj.put(k2, v2);
    obj.put(k3, v3);
    obj.put(k4, v4);
    obj.put(k5, v5);
    return Obj(Collections.unmodifiableMap(obj));
  }

  /**
   * Creates a new array wrapping the provided {@link List}.
   * For convenience, see the {@link #Arr(Expr...)} helper.
   *
   * @param values the {@link List} instance to be wrapped
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#array">Fauna Array type</a>
   */
  public static Expr Arr(List<? extends Expr> values) {
    return Fn.apply(values);
  }

  /**
   * Creates a new array containing with the values provided.
   *
   * @param values the elements of the new array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/types?lang=java#array">Fauna Array type</a>
   */
  public static Expr Arr(Expr... values) {
    return Arr(Collections.unmodifiableList(Arrays.asList(values)));
  }

  /**
   * Aborts the current transaction with a given message.
   *
   * @param msg a message to be used when aborting the transaction
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/abort?lang=java">Abort</a>
   */
  public static Expr Abort(String msg) {
    return Abort(Value(msg));
  }

  /**
   * Aborts the current transaction with a given message.
   *
   * @param msg a message to be used when aborting the transaction. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/abort?lang=java">Abort</a>
   */
  public static Expr Abort(Expr msg) {
    return Fn.apply("abort", msg);
  }

  /**
   * Calls the given user defined function with the arguments provided.
   *
   * @param ref the reference to the user defined function to be called. Type: Reference
   * @param args the list of arguments for the given user defined function
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/call?lang=java">Call</a>
   */
  public static Expr Call(Expr ref, List<? extends  Expr> args) {
    return Fn.apply("call", ref, "arguments", varargs(args));
  }

  /**
   * Calls the given user defined function with the arguments provided.
   *
   * @param ref the reference to the user defined function to be called. Type: Reference
   * @param args the arguments for the given user defined function
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/call?lang=java">Call</a>
   */
  public static Expr Call(Expr ref, Expr... args) {
    return Call(ref, Collections.unmodifiableList(Arrays.asList(args)));
  }

  /**
   * Creates a new query expression with the given lambda.
   *
   * @param lambda a lambda type
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/query?lang=java">Query</a>
   * @see #CreateFunction(Expr)
   * @see #Lambda(Expr, Expr)
   */
  public static Expr Query(Expr lambda) {
    return Fn.apply("query", lambda);
  }

  /**
   * Execute the reads associated with the given expression at the timestamp provided.
   * Writes are still executed at the current transaction time.
   *
   * @param timestamp the read timestamp. Type: Timestamp
   * @param expr the scoped expression
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/at?lang=java">At</a>
   * @see #Time(Expr)
   * @see #Value(Instant)
   */
  public static Expr At(Expr timestamp, Expr expr) {
    return Fn.apply("at", timestamp, "expr", expr);
  }

  /**
   * Execute the reads associated with the given expression at the timestamp provided.
   * Writes are still executed at the current transaction time.
   *
   * @param timestamp the read timestamp.
   * @param expr the scoped expression
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/at?lang=java">At</a>
   */
  public static Expr At(Instant timestamp, Expr expr) {
    return At(new TimeV(timestamp), expr);
  }

  /**
   * Bind values to one or more variables.
   * Variables must be accessed using the {@link #Var(String)} function.
   *
   * <p>Example:</p>
   * <pre>{@code
   * // Creates a binding map of variable names and their values,
   * // then use the binding map to build a let expression where
   * // the values can be referenced to by the expression provided
   * // using their given names.
   * Map<String, Expr> bindings = new HashMap<>();
   * bindings.add("name", Value("Bob"));
   * bindings.add("age", Value(53));
   *
   * client.query(
   *   Let(bindings).in(
   *     Create(Collection("users"), Obj("data", Obj(
   *       "name", Var("name"),
   *       "age", Var("age"),
   *     )))
   *   )
   * ).get();
   * }</pre>
   *
   * @param bindings a {@link Map} of variable names to values.
   * @return a new {@link LetBinding} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/let?lang=java">Let</a>
   * @see #Var(String)
   * @see LetBinding
   *
   * @deprecated As of release 2.6.0, use alternate Let forms.
   */
  @Deprecated
  public static LetBinding Let(Map<String, ? extends Expr> bindings) {
    List<Expr> let = new ArrayList<>();

    for (Map.Entry<String, ? extends Expr> b : bindings.entrySet()) {
      let.add(Fn.apply(b.getKey(), b.getValue()));
    }

    return new LetBinding(let);
  }

  /**
   * Bind values to one or more variables.
   * Variables must be accessed using the {@link #Var(String)} function.
   *
   * <p>Example:</p>
   * <pre>{@code
   * client.query(
   *   Let("a", Value(1)).in(
   *     Add(Value(1), Var("a"))
   *   )
   * ).get();
   * }</pre>
   *
   * @param v1 the variable name
   * @param d1 the variable value
   * @return a new {@link LetBinding} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/let?lang=java">Let</a>
   * @see #Var(String)
   * @see LetBinding
   */
  public static LetBinding Let(String v1, Expr d1) {
    List<Expr> let = new ArrayList<>();
    let.add(Fn.apply(v1, d1));
    return new LetBinding(let);
  }

  /**
   * Bind values to one or more variables.
   * Variables must be accessed using the {@link #Var(String)} function.
   *
   * <p>Example:</p>
   * <pre>{@code
   * client.query(
   *   Let("a", Value(1), "b", Value(2)).in(
   *     Add(Var("a"), Var("b"))
   *   )
   * ).get();
   * }</pre>
   *
   * @param v1 the first variable name
   * @param d1 the first variable value
   * @param v2 the second variable name
   * @param d2 the second variable value
   * @return a new {@link LetBinding} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/let?lang=java">Let</a>
   * @see #Var(String)
   * @see LetBinding
   */
  public static LetBinding Let(String v1, Expr d1, String v2, Expr d2) {
    List<Expr> let = new ArrayList<>();
    let.add(Fn.apply(v1, d1));
    let.add(Fn.apply(v2, d2));
    return new LetBinding(let);
  }

  /**
   * Bind values to one or more variables.
   * Variables must be accessed using the {@link #Var(String)} function.
   *
   * <p>Example:</p>
   * <pre>{@code
   * client.query(
   *   Let(
   *     "a", Value(1),
   *     "b", Value(2),
   *     "c", Value(3),
   *   ).in(
   *     Add(Var("a"), Var("b"), Var("c"))
   *   )
   * ).get();
   * }</pre>
   *
   * @param v1 the first variable name
   * @param d1 the first variable value
   * @param v2 the second variable name
   * @param d2 the second variable value
   * @param v3 the third variable name
   * @param d3 the third variable value
   * @return a new {@link LetBinding} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/let?lang=java">Let</a>
   * @see #Var(String)
   * @see LetBinding
   */
  public static LetBinding Let(String v1, Expr d1, String v2, Expr d2, String v3, Expr d3) {
    List<Expr> let = new ArrayList<>();
    let.add(Fn.apply(v1, d1));
    let.add(Fn.apply(v2, d2));
    let.add(Fn.apply(v3, d3));
    return new LetBinding(let);
  }

  /**
   * Bind values to one or more variables.
   * Variables must be accessed using the {@link #Var(String)} function.
   *
   * <p>Example:</p>
   * <pre>{@code
   * client.query(
   *   Let(
   *     "a", Value(1),
   *     "b", Value(2),
   *     "c", Value(3),
   *     "d", Value(4),
   *   ).in(
   *     Add(Var("a"), Var("b"), Var("c"), Var("d"))
   *   )
   * ).get();
   * }</pre>
   *
   * @param v1 the first variable name
   * @param d1 the first variable value
   * @param v2 the second variable name
   * @param d2 the second variable value
   * @param v3 the third variable name
   * @param d3 the third variable value
   * @param v4 the fourth variable name
   * @param d4 the fourth variable value
   * @return a new {@link LetBinding} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/let?lang=java">Let</a>
   * @see #Var(String)
   * @see LetBinding
   */
  public static LetBinding Let(String v1, Expr d1, String v2, Expr d2, String v3, Expr d3, String v4, Expr d4) {
    List<Expr> let = new ArrayList<>();
    let.add(Fn.apply(v1, d1));
    let.add(Fn.apply(v2, d2));
    let.add(Fn.apply(v3, d3));
    let.add(Fn.apply(v4, d4));
    return new LetBinding(let);
  }

  /**
   * Bind values to one or more variables.
   * Variables must be accessed using the {@link #Var(String)} function.
   *
   * <p>Example:</p>
   * <pre>{@code
   * client.query(
   *   Let(
   *     "a", Value(1),
   *     "b", Value(2),
   *     "c", Value(3),
   *     "d", Value(4),
   *     "e", Value(5),
   *   ).in(
   *     Add(Var("a"), Var("b"), Var("c"), Var("d"), Var("e"))
   *   )
   * ).get()
   * }</pre>
   *
   * @param v1 the first variable name
   * @param d1 the first variable value
   * @param v2 the second variable name
   * @param d2 the second variable value
   * @param v3 the third variable name
   * @param d3 the third variable value
   * @param v4 the fourth variable name
   * @param d4 the fourth variable value
   * @param v5 the fifth variable name
   * @param d5 the fitfh variable value
   * @return a new {@link LetBinding} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/let?lang=java">Let</a>
   * @see #Var(String)
   * @see LetBinding
   */
  public static LetBinding Let(String v1, Expr d1, String v2, Expr d2, String v3, Expr d3, String v4, Expr d4, String v5, Expr d5) {
    List<Expr> let = new ArrayList<>();
    let.add(Fn.apply(v1, d1));
    let.add(Fn.apply(v2, d2));
    let.add(Fn.apply(v3, d3));
    let.add(Fn.apply(v4, d4));
    let.add(Fn.apply(v5, d5));
    return new LetBinding(let);
  }

  /**
   * Bind values to one or more variables.
   * Variables must be accessed using the {@link #Var(String)} function.
   *
   * <p>Example:</p>
   * <pre>{@code
   * client.query(
   *   Let(
   *     "a", Value(1),
   *     "b", Value(2),
   *     "c", Value(3),
   *     "d", Value(4),
   *     "e", Value(5),
   *     "f", Value(6),
   *   ).in(
   *     Add(Var("a"), Var("b"), Var("c"), Var("d"), Var("e"), Var("f"))
   *   )
   * ).get()
   * }</pre>
   *
   * @param v1 the first variable name
   * @param d1 the first variable value
   * @param v2 the second variable name
   * @param d2 the second variable value
   * @param v3 the third variable name
   * @param d3 the third variable value
   * @param v4 the fourth variable name
   * @param d4 the fourth variable value
   * @param v5 the fifth variable name
   * @param d5 the fitfh variable value
   * @param v6 the sixth variable name
   * @param d6 the sixth variable value
   * @return a new {@link LetBinding} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/let?lang=java">Let</a>
   * @see #Var(String)
   * @see LetBinding
   */
  public static LetBinding Let(String v1, Expr d1, String v2, Expr d2, String v3, Expr d3, String v4, Expr d4, String v5, Expr d5, String v6, Expr d6) {
    List<Expr> let = new ArrayList<>();
    let.add(Fn.apply(v1, d1));
    let.add(Fn.apply(v2, d2));
    let.add(Fn.apply(v3, d3));
    let.add(Fn.apply(v4, d4));
    let.add(Fn.apply(v5, d5));
    let.add(Fn.apply(v6, d6));
    return new LetBinding(let);
  }

  /**
   * Bind values to one or more variables.
   * Variables must be accessed using the {@link #Var(String)} function.
   *
   * <p>Example:</p>
   * <pre>{@code
   * client.query(
   *   Let(
   *     "a", Value(1),
   *     "b", Value(2),
   *     "c", Value(3),
   *     "d", Value(4),
   *     "e", Value(5),
   *     "f", Value(6),
   *     "g", Value(7),
   *   ).in(
   *     Add(Var("a"), Var("b"), Var("c"), Var("d"), Var("e"), Var("f"), Var("g"))
   *   )
   * ).get()
   * }</pre>
   *
   * @param v1 the first variable name
   * @param d1 the first variable value
   * @param v2 the second variable name
   * @param d2 the second variable value
   * @param v3 the third variable name
   * @param d3 the third variable value
   * @param v4 the fourth variable name
   * @param d4 the fourth variable value
   * @param v5 the fifth variable name
   * @param d5 the fitfh variable value
   * @param v6 the sixth variable name
   * @param d6 the sixth variable value
   * @param v7 the seventh variable name
   * @param d7 the seventh variable value
   * @return a new {@link LetBinding} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/let?lang=java">Let</a>
   * @see #Var(String)
   * @see LetBinding
   */
  public static LetBinding Let(String v1, Expr d1, String v2, Expr d2, String v3, Expr d3, String v4, Expr d4, String v5, Expr d5, String v6, Expr d6, String v7, Expr d7) {
    List<Expr> let = new ArrayList<>();
    let.add(Fn.apply(v1, d1));
    let.add(Fn.apply(v2, d2));
    let.add(Fn.apply(v3, d3));
    let.add(Fn.apply(v4, d4));
    let.add(Fn.apply(v5, d5));
    let.add(Fn.apply(v6, d6));
    let.add(Fn.apply(v7, d7));
    return new LetBinding(let);
  }

  /**
   * Bind values to one or more variables.
   * Variables must be accessed using the {@link #Var(String)} function.
   *
   * <p>Example:</p>
   * <pre>{@code
   * client.query(
   *   Let(
   *     "a", Value(1),
   *     "b", Value(2),
   *     "c", Value(3),
   *     "d", Value(4),
   *     "e", Value(5),
   *     "f", Value(6),
   *     "g", Value(7),
   *     "g", Value(8),
   *   ).in(
   *     Add(Var("a"), Var("b"), Var("c"), Var("d"), Var("e"), Var("f"), Var("g"), Var("h"))
   *   )
   * ).get()
   * }</pre>
   *
   * @param v1 the first variable name
   * @param d1 the first variable value
   * @param v2 the second variable name
   * @param d2 the second variable value
   * @param v3 the third variable name
   * @param d3 the third variable value
   * @param v4 the fourth variable name
   * @param d4 the fourth variable value
   * @param v5 the fifth variable name
   * @param d5 the fitfh variable value
   * @param v6 the sixth variable name
   * @param d6 the sixth variable value
   * @param v7 the seventh variable name
   * @param d7 the seventh variable value
   * @param v8 the eighth variable name
   * @param d8 the eighth variable value
   * @return a new {@link LetBinding} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/let?lang=java">Let</a>
   * @see #Var(String)
   * @see LetBinding
   */
  public static LetBinding Let(String v1, Expr d1, String v2, Expr d2, String v3, Expr d3, String v4, Expr d4, String v5, Expr d5, String v6, Expr d6, String v7, Expr d7, String v8, Expr d8) {
    List<Expr> let = new ArrayList<>();
    let.add(Fn.apply(v1, d1));
    let.add(Fn.apply(v2, d2));
    let.add(Fn.apply(v3, d3));
    let.add(Fn.apply(v4, d4));
    let.add(Fn.apply(v5, d5));
    let.add(Fn.apply(v6, d6));
    let.add(Fn.apply(v7, d7));
    let.add(Fn.apply(v8, d8));
    return new LetBinding(let);
  }

  /**
   * Creates a expression that refers to the value of the given variable name in the current lexical scope.
   *
   * @param name the referred variable name
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/var?lang=java">Var</a>
   * @see #Let(Map)
   * @see #Lambda(Expr, Expr)
   */
  public static Expr Var(String name) {
    return Fn.apply("var", Value(name));
  }

  /**
   * Evaluates the then or else branch according to the given condition.
   *
   * @param condition the if condition. Type: Boolean
   * @param thenExpr the then branch for the if expression
   * @param elseExpr the else branch for the if expression
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/if?lang=java">If</a>
   */
  public static Expr If(Expr condition, Expr thenExpr, Expr elseExpr) {
    return Fn.apply("if", condition, "then", thenExpr, "else", elseExpr);
  }

  /**
   * Evaluates the given expressions sequentially evaluates its arguments,
   * and returns the result of the last expression.
   *
   * @param exprs a list of expressions to be evaluated sequentially
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/do?lang=java">Do</a>
   */
  public static Expr Do(List<? extends Expr> exprs) {
    return Fn.apply("do", Fn.apply(exprs));
  }

  /**
   * Evaluates the given expressions sequentially evaluates its arguments,
   * and returns the result of the last expression.
   *
   * @param exprs a list of expressions to be evaluated sequentially
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/do?lang=java">Do</a>
   */
  public static Expr Do(Expr... exprs) {
    return Do(Collections.unmodifiableList(Arrays.asList(exprs)));
  }

  /**
   * Creates an anonymous function that binds one or more variables in the expression provided.
   *
   * <p>Example:</p>
   * <pre>{@code
   * client.query(
   *   Map(userNames, Lambda(Value("name"),
   *     Create(Collection("user"), Obj("data", Obj(
   *       "name", Var("name")
   *     )))
   *   ))
   * ).get();
   * }</pre>
   *
   * <p>An array of variable names can be provided to match multiple variables at once. For example:</p>
   * <pre>{@code
   * // Assuming the variable userFullNames has the following format:
   * // [
   * //   ["name", "surname"],
   * //   ["name", "surname"]
   * // ]
   * client.query(
   *   Map(userFullNames,
   *     Lambda(
   *       Arr(
   *         Value("name"),
   *         Value("surname")
   *       ),
   *       Create(Collection("user"), Obj("data", Obj(
   *         "fullName", Concat(
   *           Var("name"),
   *           Var("surname")
   *         )
   *       )))
   *     )
   *   )
   * ).get();
   * }</pre>
   *
   * @param var the lambda's parameter binding. Type: String or an Array of strings
   * @param expr the lambda's function body. Type: An expression.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/lambda?lang=java">Lambda</a>
   * @see #Map(Expr, Expr)
   * @see #Foreach(Expr, Expr)
   * @see #Filter(Expr, Expr)
   * @see #Query(Expr)
   * @see #Value(String)
   * @see #Arr(List)
   */
  public static Expr Lambda(Expr var, Expr expr) {
    return Fn.apply("lambda", var, "expr", expr);
  }

  /**
   * Creates an anonymous function that binds one or more variables in the expression provided.
   *
   * @param var the lambda's parameter binding.
   * @param expr the lambda's function body. Type: An expression.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/lambda?lang=java">Lambda</a>
   * @see #Lambda(Expr, Expr)
   */
  public static Expr Lambda(String var, Expr expr) {
    return Lambda(new StringV(var), expr);
  }

  /**
   * Applies the given lambda to each element of the provided collection, and returns
   * the results of each application in a new collection of the same type.
   *
   * Map applies the lambda function concurrently to each element in the collection. Side-effects do not affect
   * evaluation of other lambda applications. The order of possible refs being generated within the lambda are
   * non-deterministic.
   *
   * @param collection the source collection. Type: Collection.
   * @param lambda the lambda function to be applied for each element in the given collection. Type: A lambda function.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/map?lang=java">Map</a>
   * @see #Lambda(Expr, Expr)
   */
  public static Expr Map(Expr collection, Expr lambda) {
    return Fn.apply("map", lambda, "collection", collection);
  }

  /**
   * Applies the given lambda to each element of the provided
   * collections, and returns the results of each application in a
   * new collection of the same type.
   *
   * Map applies the lambda function concurrently to each element in
   * the collection. Side-effects do not affect evaluation of other
   * lambda applications. The order of possible refs being generated
   * within the lambda are non-deterministic.
   *
   * @param collection the source collection. Type: Collection.
   * @param lambda the lambda function to be applied for each element in the given collection.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/map?lang=java">Map</a>
   */
  public static Expr Map(Expr collection, UnaryOperator<Expr> lambda) {
    String sym = genSym("map");
    return Map(collection, Lambda(sym, lambda.apply(Var(sym))));
  }

  /**
   * Applies the given lambda to each element of the provided collection.
   *
   * Foreach applies the lambda function concurrently to each element in the collection. Side-effects do not affect
   * evaluation of other lambda applications. The order of possible refs being generated within the lambda are
   * non-deterministic.
   *
   * @param collection the source collection. Type: Collection.
   * @param lambda the lambda function to be applied for each element in the given collection. Type: A lambda function.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/foreach?lang=java">Foreach</a>
   * @see #Lambda(Expr, Expr)
   */
  public static Expr Foreach(Expr collection, Expr lambda) {
    return Fn.apply("foreach", lambda, "collection", collection);
  }

  /**
   * Applies the given lambda to each element of the provided
   * collection.
   *
   * Foreach applies the lambda function concurrently to each element
   * in the collection. Side-effects do not affect evaluation of other
   * lambda applications. The order of possible refs being generated
   * within the lambda are non-deterministic.
   *
   * @param collection the source collection. Type: Collection.
   * @param lambda the lambda function to be applied for each element in the given collection.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/foreach?lang=java">Foreach</a>
   */
  public static Expr Foreach(Expr collection, UnaryOperator<Expr> lambda) {
    String sym = genSym("foreach");
    return Foreach(collection, Lambda(sym, lambda.apply(Var(sym))));
  }

  /**
   * Applies the given lambda to each element of the collection provided, and
   * returns a new collection containing only the elements for which the lambda returns true.
   *
   * @param collection the source collection. Type: Collection
   * @param lambda the filter lambda. Type: A lambda expression that returns a boolean value
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/filter?lang=java">Filter</a>
   * @see #Lambda(Expr, Expr)
   */
  public static Expr Filter(Expr collection, Expr lambda) {
    return Fn.apply("filter", lambda, "collection", collection);
  }

  /**
   * Applies the given lambda to each element of the collection
   * provided, and returns a new collection containing only the
   * elements for which the lambda returns true.
   *
   * @param collection the source collection. Type: Collection
   * @param lambda the filter lambda. Type: A lambda expression that returns a boolean value
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/filter?lang=java">Filter</a>
   */
  public static Expr Filter(Expr collection, UnaryOperator<Expr> lambda) {
    String sym = genSym("filter");
    return Filter(collection, Lambda(sym, lambda.apply(Var(sym))));
  }

  /**
   * Returns a new collection containing the given number of elements taken from the provided collection.
   *
   * @param num the number of elements to be taken from the source collection. Type: Number
   * @param collection the source collection. Type: Collection
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/take?lang=java">Take</a>
   */
  public static Expr Take(Expr num, Expr collection) {
    return Fn.apply("take", num, "collection", collection);
  }

  /**
   * Returns a new collection containing the given number of elements taken from the provided collection.
   *
   * @param num the number of elements to be taken from the source collection.
   * @param collection the source collection. Type: Collection
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/take?lang=java">Take</a>
   */
  public static Expr Take(long num, Expr collection) {
    return Take(new LongV(num), collection);
  }

  /**
   * Returns a new collection containing after dropping the given number of elements from the provided collection.
   *
   * @param num the number of elements to be dropped from the source collection. Type: Number
   * @param collection the source collection. Type: Collection
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/drop?lang=java">Drop</a>
   */
  public static Expr Drop(Expr num, Expr collection) {
    return Fn.apply("drop", num, "collection", collection);
  }

  /**
   * Returns a new collection containing after dropping the given number of elements from the provided collection.
   *
   * @param num the number of elements to be dropped from the source collection.
   * @param collection the source collection. Type: Collection
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/drop?lang=java">Drop</a>
   */
  public static Expr Drop(long num, Expr collection) {
    return Drop(new LongV(num), collection);
  }

  /**
   * Returns a new collection with the given elements prepended to the provided collection.
   *
   * @param elements the elements to be prepended to the source collection. Type: Array
   * @param collection the source collection. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/prepend?lang=java">Prepend</a>
   */
  public static Expr Prepend(Expr elements, Expr collection) {
    return Fn.apply("prepend", elements, "collection", collection);
  }

  /**
   * Returns a new collection with the given elements appended to the provided collection.
   *
   * @param elements the elements to be appended to the source collection. Type: Array
   * @param collection the source collection. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/append?lang=java">Append</a>
   */
  public static Expr Append(Expr elements, Expr collection) {
    return Fn.apply("append", elements, "collection", collection);
  }

  /**
   * Returns true if the given collection is empty, or false otherwise.
   *
   * @param collection the source collection to check for emptiness. Type: Collection
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isempty?lang=java">IsEmpty</a>
   */
  public static Expr IsEmpty(Expr collection) {
    return Fn.apply("is_empty", collection);
  }

  /**
   * Returns true if the given collection is not empty, or false otherwise.
   *
   * @param collection the source collection to check for non-emptiness. Type: Collection
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isnonempty?lang=java">IsNonEmpty</a>
   */
  public static Expr IsNonEmpty(Expr collection) {
    return Fn.apply("is_nonempty", collection);
  }

  /**
   * Retrieves the document identified by the given reference.
   *
   * @param ref the reference to be retrieved. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/get?lang=java">Get</a>
   * @see #Ref(Expr, String)
   */
  public static Expr Get(Expr ref) {
    return Fn.apply("get", ref);
  }

  /**
   * Retrieves the document identified by the given reference at a specific point in time.
   *
   * @param ref the reference to be retrieved. Type: Reference
   * @param timestamp the timestamp from which the reference's data will be retrieved. Type: Timestamp
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/get?lang=java">Get</a>
   * @see #Ref(Expr, String)
   * @see #Time(Expr)
   * @see #Value(Instant)
   * @see #At(Expr, Expr)
   */
  public static Expr Get(Expr ref, Expr timestamp) {
    return Fn.apply("get", ref, "ts", timestamp);
  }

  /**
   * Retrieves the document identified by the given reference at a specific point in time.
   *
   * @param ref the reference to be retrieved. Type: Reference
   * @param timestamp the timestamp from which the reference's data will be retrieved.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/get?lang=java">Get</a>
   * @see #Ref(Expr, String)
   * @see #Time(Expr)
   * @see #Value(Instant)
   * @see #At(Expr, Expr)
   */
  public static Expr Get(Expr ref, Instant timestamp) {
    return Get(ref, new TimeV(timestamp));
  }

  /**
   * Retrieves the key object given the key's secret string.
   *
   * @param secret the key's secret string. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/keyfromsecret?lang=java">KeyFromSecret</a>
   */
  public static Expr KeyFromSecret(Expr secret) {
    return Fn.apply("key_from_secret", secret);
  }

  /**
   * Retrieves the key object given the key's secret string.
   *
   * @param secret the key's secret string.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/keyfromsecret?lang=java">KeyFromSecret</a>
   */
  public static Expr KeyFromSecret(String secret) {
    return KeyFromSecret(new StringV(secret));
  }

  /**
   * Reduce the collection.
   *
   * @param lambda the accumulator lambda
   * @param initial the initial value for the accumulator.
   * @param collection the collection to be reduced.
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/reduce?lang=java">Reduce</a>
   * @return a new {@link Expr} instance
   */
  public static Expr Reduce(Expr lambda, Expr initial, Expr collection) {
    return Fn.apply("reduce", lambda, "initial", initial, "collection", collection);
  }

  /**
   * Reverse the order of the given source.
   *
   * @param source the source set. Type: Array, Set, or Page.
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/reverse?lang=java">Reverse</a>
   * @return a new {@link Expr} instance
   */
  public static Expr Reverse(Expr source) {
    return Fn.apply("reverse", source);
  }

  /**
   * Returns a Page object that groups a page of results and cursors for retrieving pages before or after
   * the current page. Pages are collections and can be passed directly to some functions such as
   * {@link #Map(Expr, Expr)}, {@link #Foreach(Expr, Expr)}, or {@link #Filter(Expr, Expr)}.
   * Transformations are applied to the Page's data array; cursors are passed through.
   *
   * <p>Example:</p>
   * <pre>{@code
   * Pagination paginateUsersQuery = Paginate(Match(Index("all_users_refs"))).withSize(20);
   * Optional<Value> nextPageCursor = Optional.absent();
   *
   * do {
   *   if (nextPageCursor.isPresent()) {
   *     paginateUsersQuery.after(nextPageCursor);
   *   }
   *
   *   Value result = client.query(
   *     Map(
   *       paginateUsersQuery,
   *       Lambda(
   *         Value("ref"),
   *         Get(Var("ref"))
   *       )
   *     )
   *   ).get();
   *
   *   Collection<User> allUsers = result.at("data")
   *     .asCollectionOf(User.class)
   *     .get();
   *
   *   // The before and after cursors must be considered opaque values.
   *   // There is no need to convert them to different types. However,
   *   // this call will safe guard our code from the absence of a cursor.
   *   nextPageCursor = result.at("after")
   *     .to(Value.class)
   *     .getOptional();
   *
   *   doSomething(allUsers);
   *
   * } while(nextPageCursor.isPresent());
   * }</pre>
   *
   * @param resource the resource to paginate
   * @return a {@link Pagination} builder
   * @see Pagination
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/paginate?lang=java">Paginate</a>
   * @see #Match(Expr)
   * @see #Map(Expr, Expr)
   * @see #Foreach(Expr, Expr)
   * @see #Filter(Expr, Expr)
   */
  public static Pagination Paginate(Expr resource) {
    return new Pagination(resource);
  }

  /**
   * Returns true if the provided reference exists, or false otherwise.
   *
   * @param ref the reference to check for existence. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/exists?lang=java">Exists</a>
   * @see #Ref(Expr, String)
   */
  public static Expr Exists(Expr ref) {
    return Fn.apply("exists", ref);
  }

  /**
   * Returns true if the provided reference exists at a specific point in time, or false otherwise.
   *
   * @param ref the reference to check for existence. Type: Reference
   * @param timestamp a timestamp to check for ref's existence. Type: Timestamp
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/exists?lang=java">Exists</a>
   * @see #Ref(Expr, String)
   */
  public static Expr Exists(Expr ref, Expr timestamp) {
    return Fn.apply("exists", ref, "ts", timestamp);
  }

  /**
   * Creates a new document of the given collection with the parameters provided.
   *
   * @param ref the collection reference for which a new document will be created. Type: Reference
   * @param params the parameters used to create the new document. Type: Object
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/create?lang=java">Create</a>
   * @see #Collection(Expr)
   * @see #Ref(Expr, String)
   * @see #Obj(Map)
   */
  public static Expr Create(Expr ref, Expr params) {
    return Fn.apply("create", ref, "params", params);
  }

  /**
   * Updates the resource identified by the given reference. Updates are partial, and only modify values specified.
   * Scalar values or arrays are replaced by new versions, objects are merged, and null removes a value.
   *
   * @param ref the resource reference to update. Type: Reference
   * @param params the parameters used to update the new document. Type: Object
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/update?lang=java">Update</a>
   * @see #Ref(Expr, String)
   * @see #Obj(Map)
   */
  public static Expr Update(Expr ref, Expr params) {
    return Fn.apply("update", ref, "params", params);
  }

  /**
   * Replaces the resource identified by the given reference.
   *
   * @param ref the resource reference to be replaced. Type: Reference
   * @param params the parameters used to replace the resource's old values. Type: Object
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replace?lang=java">Replace</a>
   * @see #Ref(Expr, String)
   * @see #Obj(Map)
   */
  public static Expr Replace(Expr ref, Expr params) {
    return Fn.apply("replace", ref, "params", params);
  }

  /**
   * Delete the resource identified by the given reference.
   *
   * @param ref the resource reference to be deleted. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/delete?lang=java">Delete</a>
   * @see #Ref(Expr, String)
   */
  public static Expr Delete(Expr ref) {
    return Fn.apply("delete", ref);
  }

  /**
   * Inserts a new event in the document's history.
   *
   * @param ref the target resource. Type: Reference
   * @param timestamp the timestamp in which the event will be inserted in the
   *                  document's history. Type: Timestamp
   * @param action the event action. Type: action
   * @param params the event's parameters. Type: Object
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/insert?lang=java">Insert</a>
   * @see Action
   * @see #Ref(Expr, String)
   * @see #Obj(Map)
   * @see #Time(Expr)
   * @see #Value(Instant)
   */
  public static Expr Insert(Expr ref, Expr timestamp, Expr action, Expr params) {
    return Fn.apply("insert", ref, "ts", timestamp, "action", action, "params", params);
  }

  /**
   * Inserts a new event in the document's history.
   *
   * @param ref the target resource. Type: Reference
   * @param timestamp the timestamp in which the event will be inserted in the
   *                  document's history. Type: Timestamp
   * @param action the event action
   * @param params the event's parameters. Type: Object
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/insert?lang=java">Insert</a>
   * @see #Ref(Expr, String)
   * @see #Obj(Map)
   * @see #Time(Expr)
   * @see #Value(Instant)
   */
  public static Expr Insert(Expr ref, Expr timestamp, Action action, Expr params) {
    return Insert(ref, timestamp, action.value, params);
  }

  /**
   * Removes an event from an document's history.
   *
   * @param ref the target resource. Type: Reference
   * @param timestamp the timestamp in which the event happened. Type: Timestamp
   * @param action the event's action. Type: event action
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/remove?lang=java">Remove</a>
   * @see Action
   * @see #Ref(Expr, String)
   * @see #Obj(Map)
   * @see #Time(Expr)
   * @see #Value(Instant)
   */
  public static Expr Remove(Expr ref, Expr timestamp, Expr action) {
    return Fn.apply("remove", ref, "ts", timestamp, "action", action);
  }

  /**
   * Removes an event from an document's history.
   *
   * @param ref the target resource. Type: Reference
   * @param timestamp the timestamp in which the event happened. Type: Timestamp
   * @param action the event's action
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/remove?lang=java">Remove</a>
   * @see #Ref(Expr, String)
   * @see #Obj(Map)
   * @see #Time(Expr)
   * @see #Value(Instant)
   */
  public static Expr Remove(Expr ref, Expr timestamp, Action action) {
    return Remove(ref, timestamp, action.value);
  }

  /**
   * Creates a new class in the current database.
   *
   * @param params the class's configuration parameters. Type: Object
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/createclass?lang=java">CreateClass</a>
   * @see #Obj(Map)
   * 
   * @deprecated use CreateCollection instead.
   */
  @Deprecated
  public static Expr CreateClass(Expr params) {
    return Fn.apply("create_class", params);
  }

  /**
   * Creates a new collection in the current database.
   *
   * @param params the collection's configuration parameters. Type: Object
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/createcollection?lang=java">CreateCollection</a>
   * @see #Obj(Map)
   */
  public static Expr CreateCollection(Expr params) {
    return Fn.apply("create_collection", params);
  }

  /**
   * Creates a new database.
   *
   * <p>Example:</p>
   * <pre>{@code
   * client.query(
   *   CreateDatabase(Obj(
   *     "name", Value("my_database")
   *   ))
   * ).get()
   * }</pre>
   *
   * @param params the database's configuration parameters. Type: Object
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/createdatabase?lang=java">CreateDatabase</a>
   * @see #Obj(Map)
   */
  public static Expr CreateDatabase(Expr params) {
    return Fn.apply("create_database", params);
  }

  /**
   * Create a new key in the current database.
   *
   * @param params the key's configuration parameters: Type Object
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/createkey?lang=java">CreateKey</a>
   * @see #Obj(Map)
   */
  public static Expr CreateKey(Expr params) {
    return Fn.apply("create_key", params);
  }

  /**
   * Creates a new index in the current database.
   *
   * @param params the index's configuration parameter. Type: Object
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/createindex?lang=java">CreateIndex</a>
   * @see #Obj(Map)
   */
  public static Expr CreateIndex(Expr params) {
    return Fn.apply("create_index", params);
  }

  /**
   * Creates a new user defined function in the current database.
   *
   * @param params the function's configuration parameters. Type: Object
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/createfunction?lang=java">CreateFunction</a>
   * @see #Obj(Map)
   */
  public static Expr CreateFunction(Expr params) {
    return Fn.apply("create_function", params);
  }

  /**
   * Creates a new user role in the current database.
   *
   * @param params the role's configuration parameters. Type: Object
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/createrole?lang=java">CreateRole</a>
   * @see #Obj(Map)
   */
  public static Expr CreateRole(Expr params) {
    return Fn.apply("create_role", params);
  }

  /**
   * Move database to a new hierarchy.
   *
   * @param from database reference to be moved.
   * @param to new parent database reference.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/movedatabase?lang=java">MoveDatabase</a>
   */
  public static Expr MoveDatabase(Expr from, Expr to) {
    return Fn.apply("move_database", from, "to", to);
  }

  /**
   * Returns the history of an instance's presence for the given reference.
   *
   * @param ref the document's reference to retrieve the singleton history. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/singleton?lang=java">Singleton</a>
   * @see #Ref(Expr, String)
   */
  public static Expr Singleton(Expr ref) {
    return Fn.apply("singleton", ref);
  }

  /**
   * Returns the history of an document's data for the given reference.
   *
   * @param refSet the resource to retrieve events for. Type: Reference or set reference.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/events?lang=java">Events</a>
   * @see #Ref(Expr, String)
   */
  public static Expr Events(Expr refSet) {
    return Fn.apply("events", refSet);
  }

  /**
   * Returns the set of resources for the given index.
   * Sets must be paginated with the function {@link #Paginate(Expr)} in order to retrieve their values.
   * If the provided index has terms configured, check the {@link #Match(Expr, Expr)} function.
   *
   * @param index an index reference. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/match?lang=java">Match</a>
   * @see #Index(String)
   * @see #Paginate(Expr)
   */
  public static Expr Match(Expr index) {
    return Fn.apply("match", index);
  }

  /**
   * Returns the set of resources that match the terms for the given index.
   * Sets must be paginated with the function {@link #Paginate(Expr)} in order to retrieve their values.
   *
   * @param index an index reference. Type: Reference
   * @param term a value to search in the index provided. Type: any value
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/match?lang=java">Match</a>
   * @see #Index(String)
   * @see #Paginate(Expr)
   */
  public static Expr Match(Expr index, Expr term) {
    return Fn.apply("match", index, "terms", term);
  }

  /**
   * Returns the set of resources present in at least on of the sets provided.
   *
   * @param sets the sets to execute the union operation
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/union?lang=java">Union</a>
   * @see #Match(Expr)
   */
  public static Expr Union(List<? extends Expr> sets) {
    return Fn.apply("union", varargs(sets));
  }

  /**
   * Returns the set of resources present in at least on of the sets provided.
   *
   * @param sets the sets to execute the union operation. Type: Array of sets
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/union?lang=java">Union</a>
   * @see #Match(Expr)
   */
  public static Expr Union(Expr... sets) {
    return Union(Collections.unmodifiableList(Arrays.asList(sets)));
  }

  /**
   * Returns the set of resources present in all sets provided.
   *
   * @param sets the sets to execute the intersection
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/intersection?lang=java">Intersection</a>
   * @see #Match(Expr)
   */
  public static Expr Intersection(List<? extends Expr> sets) {
    return Fn.apply("intersection", varargs(sets));
  }

  /**
   * Returns the set of resources present in all sets provided.
   *
   * @param sets the sets to execute the intersection. Type: Array of sets
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/intersection?lang=java">Intersection</a>
   * @see #Match(Expr)
   */
  public static Expr Intersection(Expr... sets) {
    return Intersection(Collections.unmodifiableList(Arrays.asList(sets)));
  }

  /**
   * Returns the set of resources present in the first set and not in any other set provided.
   *
   * @param sets the sets to take the difference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/difference?lang=java">Difference</a>
   * @see #Match(Expr)
   */
  public static Expr Difference(List<? extends Expr> sets) {
    return Fn.apply("difference", varargs(sets));
  }

  /**
   * Returns the set of resources present in the first set and not in any other set provided.
   *
   * @param sets the sets to take the difference. Type: Array of sets
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/difference?lang=java">Difference</a>
   * @see #Match(Expr)
   */
  public static Expr Difference(Expr... sets) {
    return Difference(Collections.unmodifiableList(Arrays.asList(sets)));
  }

  /**
   * Returns a new set after removing all duplicated values.
   *
   * @param set the set to remove duplicates. Type: Set
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/distinct?lang=java">Distinct</a>
   * @see #Match(Expr)
   */
  public static Expr Distinct(Expr set) {
    return Fn.apply("distinct", set);
  }

  /**
   * Derives a set of resources from applying each document of the source set to the target parameter.
   * The target parameter can assume two forms: a index reference, and a lambda function.
   * When the target is an index reference, Join will match each result of the source set with the target index's terms.
   * When target is a lambda function, each result from the source set will be passed as the lambda's arguments.
   *
   * @param source the source set. Type: Array or Set.
   * @param target the join target. Type: Index reference or Lambda function
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/join?lang=java">Join</a>
   * @see #Index(String)
   * @see #Lambda(Expr, Expr)
   */
  public static Expr Join(Expr source, Expr target) {
    return Fn.apply("join", source, "with", target);
  }

  /**
   * Derives a set of resources from applying each document of the
   * source set to the target parameter.  Each document from the
   * source set will be passed as the lambda argument.
   *
   * @param source the source set. Type: Array or Set.
   * @param lambda the join target.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/join?lang=java">Join</a>
   */
  public static Expr Join(Expr source, UnaryOperator<Expr> lambda) {
    String sym = genSym("join");
    return Join(source, Lambda(sym, lambda.apply(Var(sym))));
  }

  /**
   * Filter the set based on the lower/upper bounds (inclusive).
   *
   * @param set set to be filtered
   * @param from lower bound
   * @param to upper bound
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/range?lang=java">Range</a>
   */
  public static Expr Range(Expr set, Expr from, Expr to) {
    return Fn.apply("range", set, "from", from, "to", to);
  }

  /**
   * Creates a new authentication token for the provided reference.
   *
   * @param ref the token's owner reference. Type: Reference
   * @param params the token's configuration object. Type: Object
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/login?lang=java">Login</a>
   * @see #Ref(Expr, String)
   * @see #Obj(Map)
   */
  public static Expr Login(Expr ref, Expr params) {
    return Fn.apply("login", ref, "params", params);
  }

  /**
   * Delete authentication tokens.
   * If {@code invalidateAll} is true, deletes all tokens associated with the current session.
   * Otherwise, deletes only the token used for the current request.
   *
   * @param invalidateAll if the Logout function should delete all tokens
   *                      associated with the current session. Type: Boolean
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/logout?lang=java">Logout</a>
   * @see #Login(Expr, Expr)
   */
  public static Expr Logout(Expr invalidateAll) {
    return Fn.apply("logout", invalidateAll);
  }

  /**
   * Delete authentication tokens.
   * If {@code invalidateAll} is true, deletes all tokens associated with the current session.
   * Otherwise, deletes only the token used for the current request.
   *
   * @param invalidateAll if the Logout function should delete all tokens
   *                      associated with the current session.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/logout?lang=java">Logout</a>
   * @see #Login(Expr, Expr)
   */
  public static Expr Logout(boolean invalidateAll) {
    return Logout(BooleanV.valueOf(invalidateAll));
  }

  /**
   * Checks the given password against the reference's credentials, retuning true if valid, or false otherwise.
   *
   * @param ref the reference to authenticate. Type: Reference
   * @param password the authentication password. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/identify?lang=java">Identify</a>
   */
  public static Expr Identify(Expr ref, Expr password) {
    return Fn.apply("identify", ref, "password", password);
  }

  /**
   * Checks the given password against the reference's credentials, retuning true if valid, or false otherwise.
   *
   * @param ref the reference to authenticate. Type: Reference
   * @param password the authentication password.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/identify?lang=java">Identify</a>
   */
  public static Expr Identify(Expr ref, String password) {
    return Identify(ref, new StringV(password));
  }

  /**
   * Returns the reference associated with the authentication token used for the current request.
   *
   * @deprecated Use CurrentIdentity() instead.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/identity?lang=java">Identity</a>
   * @see #Login(Expr, Expr)
   */
  @Deprecated
  public static Expr Identity() {
    return Fn.apply("identity", Null());
  }

  /**
   * Returns the reference associated with the authentication token used for the current request.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/currentidentity?lang=java">CurrentIdentity</a>
   * @see #Login(Expr, Expr)
   */
  public static Expr CurrentIdentity() {
    return Fn.apply("current_identity", Null());
  }

  /**
   * Returns true if the authentication used for the request has an identity.
   *
   * @deprecated Use HasCurrentIdentity() instead.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/hasidentity?lang=java">HasIdentity</a>
   * @see #Identity()
   * @see #Identify(Expr, Expr)
   * @see #Login(Expr, Expr)
   */
  @Deprecated
  public static Expr HasIdentity() {
    return Fn.apply("has_identity", Null());
  }

  /**
   * Returns a reference to a set of all access providers in the database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @param scope a reference to a database. Type: Reference
   * @return a new {@link Expr} instance
   * @see #Database(String)
   * @see #Paginate(Expr)
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/accessproviders?lang=java">AccessProviders</a>
   */
  public static Expr AccessProviders(Expr scope) {
    return Fn.apply("access_providers", scope);
  }

  /**
   * Returns a reference to a set of all access providers in the database.
   * A reference set must be paginated in order to retrieve its values.
   *
   * @return a new {@link Expr} instance
   * @see #Paginate(Expr)
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/accessproviders?lang=java">AccessProviders</a>
   */
  public static Expr AccessProviders() {
    return AccessProviders(Null());
  }

  /**
   * Creates a reference for the access provider name.
   *
   * @param name the access provider name. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/accessprovider?lang=java">AccessProvider</a>
   */
  public static Expr AccessProvider(Expr name) {
    return Fn.apply("access_provider", name);
  }

  /**
   * Creates a reference for the access provider name.
   *
   * @param name the access provider name
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/accessprovider?lang=java">AccessProvider</a>
   */
  public static Expr AccessProvider(String name) {
    return AccessProvider(Value(name));
  }

  /**
   * Creates a reference for the given access provider name, scoped to the database provided.
   *
   * @param name the access provider name. Type: String
   * @param database the scope database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/accessprovider?lang=java">AccessProvider</a>
   * @see #Database(String)
   */
  public static Expr AccessProvider(Expr name, Expr database) {
    return Fn.apply("access_provider", name, "scope", database);
  }

  /**
   * Creates a reference for the given access provider name, scoped to the database provided.
   *
   * @param name the access provider name
   * @param database the scope database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/accessprovider?lang=java">AccessProvider</a>
   * @see #Database(String)
   */
  public static Expr AccessProvider(String name, Expr database) {
    return AccessProvider(Value(name), database);
  }

  /**
   * Creates a new access provider given the params.
   *
   * @param params  An object of parameters used to create a new access provider.
   *  - name: A valid schema name
   *  - issuer: A unique string
   *  - jwks_uri: A valid HTTPS URI
   *  - roles: An array of role/predicate pairs where the predicate returns a boolean.
   *                The array can also contain Role references.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/createaccessprovider?lang=java">CreateAccessProvider</a>
   */
  public static Expr CreateAccessProvider(Expr params) {
    return Fn.apply("create_access_provider", params);
  }
  
 /**
   * Returns current token used for the request.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/currenttoken?lang=java">CurrentToken</a>
   * @see #CurrentIdentity()
   * @see #Identify(Expr, Expr)
   * @see #Login(Expr, Expr)
   */
  public static Expr CurrentToken() {
    return Fn.apply("current_token", Null());
  }
  
  /**
   * Returns true if the authentication used for the request has a token.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/hascurrenttoken?lang=java">HasCurrentToken</a>
   * @see #Identity()
   * @see #Identify(Expr, Expr)
   * @see #Login(Expr, Expr)
   */
  public static Expr HasCurrentToken() {
    return Fn.apply("has_current_token", Null());
  }
  
  /**
   * Returns true if the authentication used for the request has an identity.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/hascurrentidentity?lang=java">HasCurrentIdentity</a>
   * @see #Identity()
   * @see #Identify(Expr, Expr)
   * @see #Login(Expr, Expr)
   */
  public static Expr HasCurrentIdentity() {
    return Fn.apply("has_current_identity", Null());
  }

  /**
   * Concatenates a list of strings into a single string value.
   *
   * @param terms a list of strings. Type: Array of strings
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/concat?lang=java">Concat</a>
   * @see #Arr(List)
   * @see #Value(String)
   */
  public static Expr Concat(Expr terms) {
    return Fn.apply("concat", terms);
  }

  /**
   * Concatenates a list of strings into a single string value using the separator provided.
   *
   * @param terms a list of strings. Type: Array of strings
   * @param separator a string to separate each element in the result. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/concat?lang=java">Concat</a>
   * @see #Arr(List)
   * @see #Value(String)
   */
  public static Expr Concat(Expr terms, Expr separator) {
    return Fn.apply("concat", terms, "separator", separator);
  }

  /**
   * Normalizes strings according to the Unicode Standard, section 5.18 "Case Mappings". By default, it uses
   * NKFCCaseFold as recommended by W3C. In order to use a specific string normalizer,
   * see {@link #Casefold(Expr, Normalizer)}.
   *
   * @param str the string to be normalized. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/casefold?lang=java">Casefold</a>
   * @see <a href="https://www.w3.org/TR/charmod-norm/">W3C Character Model for the World Wide Web: String Matching</a>
   * @see #Casefold(Expr, Normalizer)
   */
  public static Expr Casefold(Expr str) {
    return Fn.apply("casefold", str);
  }

  /**
   * Normalizes strings according to the Unicode Standard, section 5.18 "Case Mappings". By default, it uses
   * NKFCCaseFold as recommended by W3C. In order to use a specific string normalizer,
   * see {@link #Casefold(Expr, Normalizer)}.
   *
   * @param str the string to be normalized.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/casefold?lang=java">Casefold</a>
   * @see <a href="https://www.w3.org/TR/charmod-norm/">W3C Character Model for the World Wide Web: String Matching</a>
   * @see #Casefold(Expr, Normalizer)
   */
  public static Expr Casefold(String str) {
    return Casefold(new StringV(str));
  }

  /**
   * Normalizes strings according to the Unicode Standard, section 5.18 "Case Mappings", and the normalizer provided.
   * Pre-defined normalizers are available for the overload {@link #Casefold(Expr, Normalizer)}.
   *
   * @param str the string to be normalized. Type: String
   * @param normalizer the string normalizer to use. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/casefold?lang=java">Casefold</a>
   * @see <a href="https://www.w3.org/TR/charmod-norm/">W3C Character Model for the World Wide Web: String Matching</a>
   * @see #Casefold(Expr, Normalizer)
   */
  public static Expr Casefold(Expr str, Expr normalizer) {
    return Fn.apply("casefold", str, "normalizer", normalizer);
  }

  /**
   * Normalizes strings according to the Unicode Standard, section 5.18 "Case Mappings", and the normalizer provided.
   * Pre-defined normalizers are available for the overload {@link #Casefold(Expr, Normalizer)}.
   *
   * @param str the string to be normalized.
   * @param normalizer the string normalizer to use. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/casefold?lang=java">Casefold</a>
   * @see <a href="https://www.w3.org/TR/charmod-norm/">W3C Character Model for the World Wide Web: String Matching</a>
   * @see #Casefold(Expr, Normalizer)
   */
  public static Expr Casefold(String str, Expr normalizer) {
    return Casefold(new StringV(str), normalizer);
  }

  /**
   * Normalizes strings according to the Unicode Standard, section 5.18 "Case Mappings", and the normalizer provided.
   *
   * @param str the string to be normalized. Type: String
   * @param normalizer the string normalizer to use
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/casefold?lang=java">Casefold</a>
   * @see <a href="https://www.w3.org/TR/charmod-norm/">W3C Character Model for the World Wide Web: String Matching</a>
   */
  public static Expr Casefold(Expr str, Normalizer normalizer) {
    return Casefold(str, normalizer.value);
  }

  /**
   * Normalizes strings according to the Unicode Standard, section 5.18 "Case Mappings", and the normalizer provided.
   *
   * @param str the string to be normalized.
   * @param normalizer the string normalizer to use
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/casefold?lang=java">Casefold</a>
   * @see <a href="https://www.w3.org/TR/charmod-norm/">W3C Character Model for the World Wide Web: String Matching</a>
   * @see #Casefold(Expr, Normalizer)
   */
  public static Expr Casefold(String str, Normalizer normalizer) {
    return Casefold(new StringV(str), normalizer.value);
  }

  /**
   * Returns true if the string contains the given substring, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param search  the substring to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/containsstr?lang=java">ContainsStr</a>
   * @see #Value(String)
   */
  public static Expr ContainsStr(Expr value, Expr search) {
    return Fn.apply("containsstr", value, "search", search);
  }

  /**
   * Returns true if the string contains the given substring, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param search  the substring to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/containsstr?lang=java">ContainsStr</a>
   * @see #Value(String)
   */
  public static Expr ContainsStr(String value, Expr search) {
    return ContainsStr(new StringV(value), search);
  }

  /**
   * Returns true if the string contains the given substring, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param search  the substring to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/containsstr?lang=java">ContainsStr</a>
   * @see #Value(String)
   */
  public static Expr ContainsStr(Expr value, String search) {
    return ContainsStr(value, new StringV(search));
  }

  /**
   * Returns true if the string contains the given substring, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param search  the substring to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/containsstr?lang=java">ContainsStr</a>
   * @see #Value(String)
   */
  public static Expr ContainsStr(String value, String search) {
    return ContainsStr(new StringV(value), new StringV(search));
  }

  /**
   * Returns true if the string contains the given pattern, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param pattern the pattern to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/containsstrregex?lang=java">ContainsStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ContainsStrRegex(Expr value, Expr pattern) {
    return Fn.apply("containsstrregex", value, "pattern", pattern);
  }

  /**
   * Returns true if the string contains the given pattern, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param pattern the pattern to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/containsstrregex?lang=java">ContainsStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ContainsStrRegex(String value, Expr pattern) {
    return ContainsStrRegex(new StringV(value), pattern);
  }

  /**
   * Returns true if the string contains the given pattern, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param pattern the pattern to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/containsstrregex?lang=java">ContainsStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ContainsStrRegex(Expr value, String pattern) {
    return ContainsStrRegex(value, new StringV(pattern));
  }

  /**
   * Returns true if the string contains the given pattern, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param pattern the pattern to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/containsstrregex?lang=java">ContainsStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ContainsStrRegex(String value, String pattern) {
    return ContainsStrRegex(new StringV(value), new StringV(pattern));
  }

  /**
   * Returns true if the string ends with the given suffix value, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param search  the suffix to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/endswith?lang=java">EndsWith</a>
   * @see #Value(String)
   */
  public static Expr EndsWith(Expr value, Expr search) {
    return Fn.apply("endswith", value, "search", search);
  }

  /**
   * Returns true if the string ends with the given suffix value, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param search  the suffix to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/endswith?lang=java">EndsWith</a>
   * @see #Value(String)
   */
  public static Expr EndsWith(String value, Expr search) {
    return EndsWith(new StringV(value), search);
  }

  /**
   * Returns true if the string ends with the given suffix value, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param search  the suffix to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/endswith?lang=java">EndsWith</a>
   * @see #Value(String)
   */
  public static Expr EndsWith(Expr value, String search) {
    return EndsWith(value, new StringV(search));
  }

  /**
   * Returns true if the string ends with the given suffix value, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param search  the suffix to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/endswith?lang=java">EndsWith</a>
   * @see #Value(String)
   */
  public static Expr EndsWith(String value, String search) {
    return EndsWith(new StringV(value), new StringV(search));
  }

  /**
   * FindStr function searches a string for a substring and locates the location of the substring in the string
   *
   * @param value a strings
   * @param find  a substring to locate
   * @return      the offset of where the substring starts or -1 if not found
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstr?lang=java">FindStr</a>
   * @see #Value(String)
   */
  public static Expr FindStr(Expr value, Expr find) {
    return Fn.apply("findstr", value, "find", find);
  }

  /**
   * FindStr function searches a string for a substring and locates the location of the substring in the string
   *
   * @param value a strings
   * @param find  a substring to locate
   * @return      the offset of where the substring starts or -1 if not found
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstr?lang=java">FindStr</a>
   * @see #Value(String)
   */
  public static Expr FindStr(String value, Expr find) {
    return FindStr(new StringV(value), find);
  }

  /**
   * FindStr function searches a string for a substring and locates the location of the substring in the string
   *
   * @param value a strings
   * @param find  a substring to locate
   * @return      the offset of where the substring starts or -1 if not found
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstr?lang=java">FindStr</a>
   * @see #Value(String)
   */
  public static Expr FindStr(Expr value, String find) {
    return FindStr(value, new StringV(find));
  }

  /**
   * FindStr function searches a string for a substring and locates the location of the substring in the string
   *
   * @param value a strings
   * @param find  a substring to locate
   * @return      the offset of where the substring starts or -1 if not found
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstr?lang=java">FindStr</a>
   * @see #Value(String)
   */
  public static Expr FindStr(String value, String find) {
    return FindStr(new StringV(value), new StringV(find));
  }

  /**
   * FindStr function searches a string for a substring and locates the location of the substring in the string
   *
   * @param value a strings
   * @param find  a substring to locate
   * @param start a position to start the search
   * @return      the offset of where the substring starts or -1 if not found
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstr?lang=java">FindStr</a>
   * @see #Value(String)
   */
  public static Expr FindStr(Expr value, Expr find, Expr start) {
    return Fn.apply("findstr", value, "find", find, "start", start);
  }
  /**
   * FindStr function returns
   * FindStr function searches a string for a substring and locates the location of the substring in the string
   *
   * @param value a strings
   * @param find  a substring to locate
   * @param start a position to start the search
   * @return      the offset of where the substring starts or -1 if not found
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstr?lang=java">FindStr</a>
   * @see #Value(String)
   */
  public static Expr FindStr(Expr value, Expr find, long start) {
    return FindStr(value, find, new LongV(start));
  }

  /**
   * FindStr function used to searches a string for a substring and locates the location of the substring in the string
   *
   * @param value a strings
   * @param find  a substring to locate
   * @param start a position to start the search
   * @return      the offset of where the substring starts or -1 if not found
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstr?lang=java">FindStr</a>
   * @see #Value(String)
   */
  public static Expr FindStr(Expr value, String find, Expr start) {
    return FindStr(value, new StringV(find), start);
  }
  /**
   * FindStr function searches a string for a substring and locates the location of the substring in the string
   *
   * @param value a strings
   * @param find  a substring to locate
   * @param start a position to start the search
   * @return      the offset of where the substring starts or -1 if not found
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstr?lang=java">FindStr</a>
   * @see #Value(String)
   */
  public static Expr FindStr(String value, Expr find, long start) {
    return FindStr(new StringV(value), find, new LongV(start));
  }

  /**
   * FindStr function searches a string for a substring and locates the location of the substring in the string
   *
   * @param value a strings
   * @param find  a substring to locate
   * @param start a position to start the search
   * @return      the offset of where the substring starts or -1 if not found
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstr?lang=java">FindStr</a>
   * @see #Value(String)
   */
  public static Expr FindStr(String value, String find, Expr start) {
    return FindStr(new StringV(value), new StringV(find), start);
  }
  /**
   * FindStr function searches a string for a substring and locates the location of the substring in the string
   *
   * @param value a strings
   * @param find  a substring to locate
   * @param start a position to start the search
   * @return      the offset of where the substring starts or -1 if not found
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstr?lang=java">FindStr</a>
   * @see #Value(String)
   */
  public static Expr FindStr(String value, String find, long start) {
    return FindStr(new StringV(value), new StringV(find), new LongV(start));
  }


  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(Expr value, Expr pattern) {
    return Fn.apply("findstrregex", value, "pattern", pattern);
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(Expr value, String pattern) {
    return FindStrRegex(value, new StringV(pattern));
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(String value, Expr pattern) {
    return FindStrRegex(new StringV(value), pattern);
  }
  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(String value, String pattern) {
    return FindStrRegex(new String(value), new StringV(pattern));
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(Expr value, Expr pattern, Expr start) {
    return Fn.apply("findstrregex", value, "pattern", pattern, "start", start);
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(String value, Expr pattern, Expr start) {
    return FindStrRegex(new StringV(value), pattern, start);
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(Expr value, String pattern, Expr start) {
    return FindStrRegex(value, new StringV(pattern), start);
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(String value, String pattern, Expr start) {
    return FindStrRegex(new StringV(value), new StringV(pattern), start);
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(Expr value, Expr pattern, long start) {
    return FindStrRegex(value, pattern, new LongV(start));
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(String value, Expr pattern, long start) {
    return FindStrRegex(new StringV(value), pattern, new LongV(start));
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(Expr value, String pattern, long start) {
    return FindStrRegex(value, new StringV(pattern), new LongV(start));
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(String value, String pattern, long start) {
    return FindStrRegex(new StringV(value), new StringV(pattern), new LongV(start));
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(Expr value, Expr pattern, Expr start, Expr numResults) {
    return Fn.apply("findstrregex", value, "pattern", pattern, "start", start, "num_results", numResults);
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(String value, Expr pattern, Expr start, Expr numResults) {
    return FindStrRegex(new StringV(value), pattern, start, numResults);
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(Expr value, String pattern, Expr start, Expr numResults) {
    return FindStrRegex(value, new StringV(pattern), start, numResults);
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(String value, String pattern, Expr start, Expr numResults) {
    return FindStrRegex(new StringV(value), new StringV(pattern), start, numResults);
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(Expr value, Expr pattern, long start, Expr numResults) {
    return FindStrRegex(value, pattern, new LongV(start), numResults);
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(String value, Expr pattern, long start, Expr numResults) {
    return FindStrRegex(new StringV(value), pattern, new LongV(start), numResults);
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(Expr value, String pattern, long start, Expr numResults) {
    return FindStrRegex(value, new StringV(pattern), new LongV(start), numResults);
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(String value, String pattern, long start, Expr numResults) {
    return FindStrRegex(new StringV(value), new StringV(pattern), new LongV(start), numResults);
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(Expr value, Expr pattern, Expr start, long numResults) {
    return FindStrRegex(value, pattern, start, new LongV(numResults));
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(String value, Expr pattern, Expr start, long numResults) {
    return FindStrRegex(new StringV(value), pattern, start, new LongV(numResults));
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(Expr value, String pattern, Expr start, long numResults) {
    return FindStrRegex(value, new StringV(pattern), start, new LongV(numResults));
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(String value, String pattern, Expr start, long numResults) {
    return FindStrRegex(new StringV(value), new StringV(pattern), start, new LongV(numResults));
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(Expr value, Expr pattern, long start, long numResults) {
    return FindStrRegex(value, pattern, new LongV(start), new LongV(numResults));
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(String value, Expr pattern, long start, long numResults) {
    return FindStrRegex(new StringV(value), pattern, new LongV(start), new LongV(numResults));
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(Expr value, String pattern, long start, long numResults) {
    return FindStrRegex(value, new StringV(pattern), new LongV(start), new LongV(numResults));
  }

  /**
   * FindStrRegex function searches a string for a java pattern and locates all the locations of the pattern in the string
   *
   * @param value   a string to search
   * @param pattern a substring to locate
   * @param start the offset into the string
   * @param numResults the maximum number of results
   * @return        an array of objects contain the locations of the match [{ "start":s, "end":e, "data"d}]
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=java">FindStrRegex</a>
   * @see #Value(String)
   */
  public static Expr FindStrRegex(String value, String pattern, long start, long numResults) {
    return FindStrRegex(new StringV(value), new StringV(pattern), new LongV(start), new LongV(numResults));
  }

  /**
   * SplitStr function splits a string based on a provided token contained in the string
   *
   * @param value a string to search
   * @param token a substring to split on
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstr">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStr(Expr value, Expr token, Expr count) {
    return Fn.apply("split_str", value, "token", token, "count", count);
  }

  /**
   * SplitStr function splits a string based on a provided token contained in the string
   *
   * @param value a string to search
   * @param token a substring to split on
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstr">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStr(String value, String token, long count) {
    return SplitStr(new StringV(value), new StringV(token), new LongV(count));
  }

  /**
   * SplitStr function splits a string based on a provided token contained in the string
   *
   * @param value a string to search
   * @param token a substring to split on
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstr">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStr(Expr value, String token, long count) {
    return SplitStr(value, new StringV(token), new LongV(count));
  }

  /**
   * SplitStr function splits a string based on a provided token contained in the string
   *
   * @param value a string to search
   * @param token a substring to split on
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstr">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStr(Expr value, Expr token, long count) {
    return SplitStr(value, token, new LongV(count));
  }

  /**
   * SplitStr function splits a string based on a provided token contained in the string
   *
   * @param value a string to search
   * @param token a substring to split on
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstr">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStr(Expr value, String token, Expr count) {
    return SplitStr(value, new StringV(token), count);
  }

  /**
   * SplitStr function splits a string based on a provided token contained in the string
   *
   * @param value a string to search
   * @param token a substring to split on
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstr">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStr(String value, Expr token, Long count) {
    return SplitStr(new StringV(value), token, new LongV(count));
  }

  /**
   * SplitStr function splits a string based on a provided token contained in the string
   *
   * @param value a string to search
   * @param token a substring to split on
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstr">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStr(String value, Expr token, Expr count) {
    return SplitStr(new StringV(value), token, count);
  }

  /**
   * SplitStr function splits a string based on a provided token contained in the string
   *
   * @param value a string to search
   * @param token a substring to split on
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstr">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStr(Expr value, Expr token) {
    return Fn.apply("split_str", value, "token", token);
  }

  /**
   * SplitStr function splits a string based on a provided token contained in the string
   *
   * @param value a string to search
   * @param token a substring to split on
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstr">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStr(String value, Expr token) {
    return SplitStr(new StringV(value), token);
  }

  /**
   * SplitStr function splits a string based on a provided token contained in the string
   *
   * @param value a string to search
   * @param token a substring to split on
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstr">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStr(String value, String token) {
    return SplitStr(new StringV(value), new StringV(token));
  }

  /**
   * SplitStr function splits a string based on a provided token contained in the string
   *
   * @param value a string to search
   * @param token a substring to split on
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstr">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStr(Expr value, String token) {
    return SplitStr(value, new StringV(token));
  }

  /**
   * SplitStrRegex function splits a string based on for a java pattern
   *
   * @param value a string to search
   * @param pattern a java pattern
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstrregex">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStrRegex(Expr value, Expr pattern, Expr count) {
    return Fn.apply("split_str_regex", value, "pattern", pattern, "count", count);
  }

  /**
   * SplitStrRegex function splits a string based on for a java pattern
   *
   * @param value a string to search
   * @param pattern a java pattern
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstrregex">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStrRex(String value, String pattern, long count) {
    return SplitStrRegex(new StringV(value), new StringV(pattern), new LongV(count));
  }

  /**
   * SplitStrRegex function splits a string based on for a java pattern
   *
   * @param value a string to search
   * @param pattern a java pattern
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstrregex">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStrRegex(Expr value, String pattern, long count) {
    return SplitStrRegex(value, new StringV(pattern), new LongV(count));
  }

  /**
   * SplitStrRegex function splits a string based on for a java pattern
   *
   * @param value a string to search
   * @param pattern a java pattern
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstrregex">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStrRegex(Expr value, Expr pattern, long count) {
    return SplitStrRegex(value, pattern, new LongV(count));
  }

  /**
   * SplitStrRegex function splits a string based on for a java pattern
   *
   * @param value a string to search
   * @param pattern a java pattern
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstrregex">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStrRegex(Expr value, String pattern, Expr count) {
    return SplitStrRegex(value, new StringV(pattern), count);
  }

  /**
   * SplitStrRegex function splits a string based on for a java pattern
   *
   * @param value a string to search
   * @param pattern a java pattern
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstrregex">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStrRegex(String value, Expr pattern, Long count) {
    return SplitStrRegex(new StringV(value), pattern, new LongV(count));
  }

  /**
   * SplitStrRegex function splits a string based on for a java pattern
   *
   * @param value a string to search
   * @param pattern a java pattern
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstrregex">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStrRegex(String value, Expr pattern, Expr count) {
    return SplitStrRegex(new StringV(value), pattern, count);
  }

  /**
   * SplitStrRegex function splits a string based on for a java pattern
   *
   * @param value a string to search
   * @param pattern a java pattern
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstrregex">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStrRegex(String value, String pattern, Expr count) {
    return SplitStrRegex(new StringV(value), new StringV(pattern), count);
  }

  /**
   * SplitStrRegex function splits a string based on for a java pattern
   *
   * @param value a string to search
   * @param pattern a java pattern
   * @param count the maximal number of element returned
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstrregex">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStrRegex(String value, String pattern, long count) {
    return SplitStrRegex(new StringV(value), new StringV(pattern), new LongV(count));
  }

  /**
   * SplitStrRegex function splits a string based on for a java pattern
   *
   * @param value a string to search
   * @param pattern a java pattern
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstrregex">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStrRegex(Expr value, Expr pattern) {
    return Fn.apply("split_str_regex", value, "pattern", pattern);
  }

  /**
   * SplitStrRegex function splits a string based on for a java pattern
   *
   * @param value a string to search
   * @param pattern a java pattern
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstrregex">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStrRegex(String value, String pattern) {
    return SplitStrRegex(new StringV(value), new StringV(pattern));
  }

  /**
   * SplitStrRegex function splits a string based on for a java pattern
   *
   * @param value a string to search
   * @param pattern a java pattern
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstrregex">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStrRegex(Expr value, String pattern) {
    return SplitStrRegex(value, new StringV(pattern));
  }

  /**
   * SplitStrRegex function splits a string based on for a java pattern
   *
   * @param value a string to search
   * @param pattern a java pattern
   * @return        an array of strings
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/splitstrregex">FaunaDB String Functions</a>
   * @see #Value(String)
   */
  public static Expr SplitStrRegex(String value, Expr pattern) {
    return SplitStrRegex(new StringV(value), pattern);
  }

  /**
   * Length function returns the number of characters (codepoints) in the string
   *
   * @param value   a string to determine the length of
   * @return        the length of the string as a long
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/length?lang=java">Length</a>
   * @see #Value(String)
   */
  public static Expr Length(Expr value) {
    return Fn.apply("length", value);
  }

  /**
   * Length function returns the number of characters (codepoints) in the string
   *
   * @param value   a string to determine the length of
   * @return        the length of the string as a long
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/length?lang=java">Length</a>
   * @see #Value(String)
   */
  public static Expr Length(String value) {
    return Length(new StringV(value));
  }

  /**
   * Lower function returns all letters in the string in lowercase
   *
   * @param value a string to lowercase
   * @return      a string with all lowercase letters
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/lowercase?lang=java">LowerCase</a>
   * @see #Value(String)
   */
  public static Expr LowerCase(Expr value) {
    return Fn.apply("lowercase", value);
  }

  /**
   * Lower function returns all letters in the string in lowercase
   *
   * @param value a string to lowercase
   * @return      a string with all lowercase letters
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/lowercase?lang=java">LowerCase</a>
   * @see #Value(String)
   */
  public static Expr LowerCase(String value) {
    return LowerCase(new StringV(value));
  }

  /**
   * LTrim function returns a new string with leading white space removed
   *
   * @param value a string to trim leading white space.
   * @return      the string with leading white space removed
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/ltrim?lang=java">LTrim</a>
   * @see #Value(String)
   */
  public static Expr LTrim(Expr value) {
    return Fn.apply("ltrim", value);
  }

  /**
   * LTrim function returns a new string with leading white space removed
   *
   * @param value a string to trim leading white space.
   * @return      the string with leading white space removed
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/ltrim?lang=java">LTrim</a>
   * @see #Value(String)
   */
  public static Expr LTrim(String value) {
    return LTrim(new StringV(value));
  }

  /**
   * Tokenize the input into n-grams of the given sizes.
   *
   * @param terms the value to tokenize. Type: String
   * @param min the minimum size for the n-grams. Type: Number
   * @param max the maximum size for the n-grams. Type: Number
   * @return a new {@link Expr} instance
   * @see #Value(String)
   * @see #Value(long)
   */
  public static Expr NGram(Expr terms, Expr min, Expr max) {
    return Fn.apply("ngram", terms, "min", min, "max", max);
  }

  /**
   * Tokenize the input into n-grams of the given sizes.
   *
   * @param terms the value to tokenize.
   * @param min the minimum size for the n-grams. Type: Number
   * @param max the maximum size for the n-grams. Type: Number
   * @return a new {@link Expr} instance
   * @see #Value(long)
   */
  public static Expr NGram(String terms, Expr min, Expr max) {
    return NGram(new StringV(terms), min, max);
  }

  /**
   * Tokenize the input into n-grams of the given sizes.
   *
   * @param terms the value to tokenize. Type: String
   * @param min the minimum size for the n-grams.
   * @param max the maximum size for the n-grams. Type: Number
   * @return a new {@link Expr} instance
   * @see #Value(String)
   * @see #Value(long)
   */
  public static Expr NGram(Expr terms, long min, Expr max) {
    return NGram(terms, new LongV(min), max);
  }

  /**
   * Tokenize the input into n-grams of the given sizes.
   *
   * @param terms the value to tokenize.
   * @param min the minimum size for the n-grams.
   * @param max the maximum size for the n-grams. Type: Number
   * @return a new {@link Expr} instance
   * @see #Value(long)
   */
  public static Expr NGram(String terms, long min, Expr max) {
    return NGram(terms, new LongV(min), max);
  }

  /**
   * Tokenize the input into n-grams of the given sizes.
   *
   * @param terms the value to tokenize. Type: String
   * @param min the minimum size for the n-grams. Type: Number
   * @param max the maximum size for the n-grams.
   * @return a new {@link Expr} instance
   * @see #Value(String)
   * @see #Value(long)
   */
  public static Expr NGram(Expr terms, Expr min, long max) {
    return NGram(terms, min, new LongV(max));
  }

  /**
   * Tokenize the input into n-grams of the given sizes.
   *
   * @param terms the value to tokenize.
   * @param min the minimum size for the n-grams. Type: Number
   * @param max the maximum size for the n-grams.
   * @return a new {@link Expr} instance
   * @see #Value(String)
   * @see #Value(long)
   */
  public static Expr NGram(String terms, Expr min, long max) {
    return NGram(terms, min, new LongV(max));
  }

  /**
   * Tokenize the input into n-grams of the given sizes.
   *
   * @param terms the value to tokenize. Type: String
   * @param min the minimum size for the n-grams.
   * @param max the maximum size for the n-grams.
   * @return a new {@link Expr} instance
   * @see #Value(String)
   */
  public static Expr NGram(Expr terms, long min, long max) {
    return NGram(terms, new LongV(min), new LongV(max));
  }

  /**
   * Tokenize the input into n-grams of the given sizes.
   *
   * @param terms the value to tokenize.
   * @param min the minimum size for the n-grams.
   * @param max the maximum size for the n-grams.
   * @return a new {@link Expr} instance
   */
  public static Expr NGram(String terms, long min, long max) {
    return NGram(terms, new LongV(min), new LongV(max));
  }

  /**
   * Tokenize the input into n-grams of the given sizes.
   *
   * @param terms the list of values to tokenize. Type: Array of strings
   * @param min the minimum size for the n-grams. Type: Number
   * @param max the maximum size for the n-grams. Type: Number
   * @return a new {@link Expr} instance
   * @see #Arr(List)
   * @see #Value(String)
   * @see #Value(long)
   */
  public static Expr NGram(List<Expr> terms, Expr min, Expr max) {
    return NGram(varargs(terms), min, max);
  }


  /**
   * Tokenize the input into n-grams of the 1 and 2 elements in size.
   *
   * @param terms the list of values to tokenize. Type: Array of strings
   * @return a new {@link Expr} instance
   * @see #Arr(List)
   * @see #Value(String)
   */
  public static Expr NGram(List<Expr> terms) {
    return NGram(varargs(terms));
  }

  /**
   * Tokenize the input into n-grams of the 1 and 2 elements in size.
   *
   * @param term the term to tokenize. Type: String
   * @return a new {@link Expr} instance
   * @see #Arr(List)
   * @see #Value(String)
   */
  public static Expr NGram(Expr term) {
    return Fn.apply("ngram", term);
  }

  /**
   * Tokenize the input into n-grams of the 1 and 2 elements in size.
   *
   * @param term the value to tokenize.
   * @return a new {@link Expr} instance
   * @see #Arr(List)
   */
  public static Expr NGram(String term) {
    return NGram(new StringV(term));
  }

  /**
   * It takes a string and returns a regex which matches the input string verbatim.
   *
   * @param value the string to analyze
   * @return      a regex which matches the input string verbatim
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/regexescape?lang=java">RegexEscape</a>
   * @see #Value(String)
   */
  public static Expr RegexEscape(Expr value) {
    return Fn.apply("regexescape", value);
  }

  /**
   * It takes a string and returns a regex which matches the input string verbatim.
   *
   * @param value the string to analyze
   * @return      a regex which matches the input string verbatim
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/regexescape?lang=java">RegexEscape</a>
   * @see #Value(String)
   */
  public static Expr RegexEscape(String value) {
    return RegexEscape(new StringV(value));
  }

  /**
   * Repeat function returns a string the specified number of times
   *
   * @param value a strings
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/repeat?lang=java">Repeat</a>
   * @see #Value(String)
   */
  public static Expr Repeat(Expr value) {
    return Fn.apply("repeat", value);
  }

  /**
   * Repeat function returns a string the specified number of times
   *
   * @param value a strings
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/repeat?lang=java">Repeat</a>
   * @see #Value(String)
   */
  public static Expr Repeat(String value) {
    return Repeat(new StringV(value));
  }

  /**
   * Repeat function returns a string concatenanted the specified number of times
   *
   * @param value a strings
   * @param number an integer value indicate the number of times to repeat the string
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/repeat?lang=java">Repeat</a>
   * @see #Value(String)
   */
  public static Expr Repeat(Expr value, Expr number) {
    return Fn.apply("repeat", value, "number", number);
  }

  /**
   * Repeat function returns a string concatenanted the specified number of times
   *
   * @param value a strings
   * @param number an integer value indicate the number of times to repeat the string
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/repeat?lang=java">Repeat</a>
   * @see #Value(String)
   */
  public static Expr Repeat(String value, Expr number) {
    return Repeat(new StringV(value), number);
  }

  /**
   * Repeat function returns a string concatenanted the specified number of times
   *
   * @param value a strings
   * @param number an integer value indicate the number of times to repeat the string
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/repeat?lang=java">Repeat</a>
   * @see #Value(String)
   */
  public static Expr Repeat(Expr value, long number) {
    return Repeat(value, new LongV(number));
  }

  /**
   * Repeat function returns a string concatenanted the specified number of times
   *
   * @param value a strings
   * @param number an integer value indicate the number of times to repeat the string
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/repeat?lang=java">Repeat</a>
   * @see #Value(String)
   */
  public static Expr Repeat(String value, long number) {
    return Repeat(new StringV(value), new LongV(number));
  }

  /**
   * ReplaceStr returns a string with every occurence of the "find" string changed to "replace" string
   *
   * @param value   the source string
   * @param find    the substring to locate in in the source string
   * @param replace the string to replaice the "find" string when located
   * @return        the new string with every occurence of the "find" string changed to "replace" string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestr?lang=java">ReplaceStr</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStr(Expr value, Expr find, Expr replace) {
    return Fn.apply("replacestr", value, "find", find, "replace", replace);
  }

  /**
   * ReplaceStr returns a string with every occurence of the "find" string changed to "replace" string
   *
   * @param value   the source string
   * @param find    the substring to locate in in the source string
   * @param replace the string to replaice the "find" string when located
   * @return        the new string with every occurence of the "find" string changed to "replace" string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestr?lang=java">ReplaceStr</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStr(String value, Expr find, Expr replace) {
    return ReplaceStr(new StringV(value), find, replace);
  }

  /**
   * ReplaceStr returns a string with every occurence of the "find" string changed to "replace" string
   *
   * @param value   the source string
   * @param find    the substring to locate in in the source string
   * @param replace the string to replaice the "find" string when located
   * @return        the new string with every occurence of the "find" string changed to "replace" string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestr?lang=java">ReplaceStr</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStr(Expr value, String find, Expr replace) {
    return ReplaceStr(value, new StringV(find), replace);
  }

  /**
   * ReplaceStr returns a string with every occurence of the "find" string changed to "replace" string
   *
   * @param value   the source string
   * @param find    the substring to locate in in the source string
   * @param replace the string to replaice the "find" string when located
   * @return        the new string with every occurence of the "find" string changed to "replace" string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestr?lang=java">ReplaceStr</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStr(String value, String find, Expr replace) {
    return ReplaceStr(new StringV(value), new StringV(find), replace);
  }

  /**
   * ReplaceStr returns a string with every occurence of the "find" string changed to "replace" string
   *
   * @param value   the source string
   * @param find    the substring to locate in in the source string
   * @param replace the string to replaice the "find" string when located
   * @return        the new string with every occurence of the "find" string changed to "replace" string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestr?lang=java">ReplaceStr</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStr(Expr value, Expr find, String replace) {
    return ReplaceStr(value, find, new StringV(replace));
  }

  /**
   * ReplaceStr returns a string with every occurence of the "find" string changed to "replace" string
   *
   * @param value   the source string
   * @param find    the substring to locate in in the source string
   * @param replace the string to replaice the "find" string when located
   * @return        the new string with every occurence of the "find" string changed to "replace" string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestr?lang=java">ReplaceStr</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStr(String value, Expr find, String replace) {
    return ReplaceStr(new StringV(value), find, new StringV(replace));
  }

  /**
   * ReplaceStr returns a string with every occurence of the "find" string changed to "replace" string
   *
   * @param value   the source string
   * @param find    the substring to locate in in the source string
   * @param replace the string to replaice the "find" string when located
   * @return        the new string with every occurence of the "find" string changed to "replace" string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestr?lang=java">ReplaceStr</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStr(Expr value, String find, String replace) {
    return ReplaceStr(value, new StringV(find), new StringV(replace));
  }

  /**
   * ReplaceStr returns a string with every occurence of the "find" string changed to "replace" string
   *
   * @param value   the source string
   * @param find    the substring to locate in in the source string
   * @param replace the string to replaice the "find" string when located
   * @return        the new string with every occurence of the "find" string changed to "replace" string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestr?lang=java">ReplaceStr</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStr(String value, String find, String replace) {
    return ReplaceStr(new StringV(value), new StringV(find), new StringV(replace));
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(Expr value, Expr pattern, Expr replace) {
    return Fn.apply("replacestrregex", value, "pattern", pattern, "replace", replace);
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(String value, Expr pattern, Expr replace) {
    return ReplaceStrRegex(new StringV(value), pattern, replace);
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(Expr value, String pattern, Expr replace) {
    return ReplaceStrRegex(value, new StringV(pattern), replace);
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(String value, String pattern, Expr replace) {
    return ReplaceStrRegex(new StringV(value), new StringV(pattern), replace);
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(Expr value, Expr pattern, String replace) {
    return ReplaceStrRegex(value, pattern, new StringV(replace));
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(String value, Expr pattern, String replace) {
    return ReplaceStrRegex(new StringV(value), pattern, new StringV(replace));
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(Expr value, String pattern, String replace) {
    return ReplaceStrRegex(value, new StringV(pattern), replace);
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(String value, String pattern, String replace) {
    return ReplaceStrRegex(new StringV(value), new StringV(pattern), replace);
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(Expr value, Expr pattern, Expr replace, Expr first) {
    return Fn.apply("replacestrregex", value, "pattern", pattern, "replace", replace, "first", first);
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(String value, Expr pattern, Expr replace, Expr first) {
    return ReplaceStrRegex(new StringV(value), pattern, replace, first);
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(Expr value, String pattern, Expr replace, Expr first) {
    return ReplaceStrRegex(value, new StringV(pattern), replace, first);
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(String value, String pattern, Expr replace, Expr first) {
    return ReplaceStrRegex(new StringV(value), new StringV(pattern), replace, first);
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(Expr value, Expr pattern, String replace, Expr first) {
    return ReplaceStrRegex(value, pattern, new StringV(replace), first);
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(String value, Expr pattern, String replace, Expr first) {
    return ReplaceStrRegex(new StringV(value), pattern, new StringV(replace), first);
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(Expr value, String pattern, String replace, Expr first) {
    return ReplaceStrRegex(value, new StringV(pattern), new StringV(replace), first);
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(String value, String pattern, String replace, Expr first) {
    return ReplaceStrRegex(new StringV(value), new StringV(pattern), new StringV(replace), first);
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(Expr value, Expr pattern, Expr replace, Boolean first) {
    return ReplaceStrRegex(value, pattern, replace, BooleanV.valueOf(first));
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(String value, Expr pattern, Expr replace, boolean first) {
    return ReplaceStrRegex(new StringV(value), pattern, replace, BooleanV.valueOf(first));
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(Expr value, String pattern, Expr replace, boolean first) {
    return ReplaceStrRegex(value, new StringV(pattern), replace, BooleanV.valueOf(first));
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(String value, String pattern, Expr replace, boolean first) {
    return ReplaceStrRegex(new StringV(value), new StringV(pattern), replace, BooleanV.valueOf(first));
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(Expr value, Expr pattern, String replace, boolean first) {
    return ReplaceStrRegex(value, pattern, new StringV(replace), BooleanV.valueOf(first));
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(String value, Expr pattern, String replace, boolean first) {
    return ReplaceStrRegex(new StringV(value), pattern, new StringV(replace), BooleanV.valueOf(first));
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(Expr value, String pattern, String replace, boolean first) {
    return ReplaceStrRegex(value, new StringV(pattern), new StringV(replace), BooleanV.valueOf(first));
  }

  /**
   * ReplaceStrRegex returns a string with occurence(s) of the java regular expression "pattern" changed to "replace" string
   *
   * @param value the source string
   * @param pattern a java regular expression to locate
   * @param replace the string to replace the pattern when located
   * @param first only replace the first found pattern
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=java">ReplaceStrRegex</a>
   * @see #Value(String)
   */
  public static Expr ReplaceStrRegex(String value, String pattern, String replace, boolean first) {
    return ReplaceStrRegex(new StringV(value), new StringV(pattern), new StringV(replace), BooleanV.valueOf(first));
  }

  /**
   * RTrim function returns a new string with trailing whitespace removed
   *
   * @param value a string to trim whitespace.
   * @return      the string with trailing whitespace removed
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/rtrim?lang=java">RTrim</a>
   * @see #Value(String)
   */
  public static Expr RTrim(Expr value) {
    return Fn.apply("rtrim", value);
  }

  /**
   * RTrim function returns a new string with trailing whitespace removed
   *
   * @param value a string to trim whitespace.
   * @return      the string with trailing whitespace removed
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/rtrim?lang=java">RTrim</a>
   * @see #Value(String)
   */
  public static Expr RTrim(String value) {
    return RTrim(new StringV(value));
  }

  /**
   * Space function returns "N" number of spaces
   *
   * @param value the number of spaces
   * @return      a string with spaces
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/space?lang=java">Space</a>
   * @see #Value(String)
   */
  public static Expr Space(Expr value) {
    return Fn.apply("space", value);
  }

  /**
   * Space function returns "N" number of spaces
   *
   * @param value the number of spaces
   * @return      a string with spaces
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/space?lang=java">Space</a>
   * @see #Value(String)
   */
  public static Expr Space(Long value) {
    return Space(new LongV(value));
  }

  /**
   * Space function returns
   *
   * @param value the number of spaces
   * @return      a string with spaces
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/space?lang=java">Space</a>
   * @see #Value(String)
   */
  public static Expr Space(Integer value) {
    return Space(new LongV(value));
  }

  /**
   * SubString function returns a subset of the source string
   *
   * @param value the source string
   * @return      a new string contain a subset of the source string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=java">SubString</a>
   * @see #Value(String)
   */
  public static Expr SubString(Expr value) {
    return Fn.apply("substring", value);
  }

  /**
   * SubString function returns a subset of the source string
   *
   * @param value the source string
   * @return      a new string contain a subset of the source string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=java">SubString</a>
   * @see #Value(String)
   */
  public static Expr SubString(String value) {
    return SubString(new StringV(value));
  }

  /**
   * SubString function returns a subset of the source string
   *
   * @param value the source string
   * @param start the position in the source string where SubString starts extracting characters
   * @return      a new string contain a subset of the source string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=java">SubString</a>
   * @see #Value(String)
   */
  public static Expr SubString(Expr value, Expr start) {
    return Fn.apply("substring", value, "start", start);
  }

  /**
   * SubString function returns a subset of the source string
   *
   * @param value the source string
   * @param start the position in the source string where SubString starts extracting characters
   * @return      a new string contain a subset of the source string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=java">SubString</a>
   * @see #Value(String)
   */
  public static Expr SubString(String value, Expr start) {
    return SubString(new StringV(value), start);
  }

  /**
   * SubString function returns a subset of the source string
   *
   * @param value the source string
   * @param start the position in the source string where SubString starts extracting characters
   * @return      a new string contain a subset of the source string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=java">SubString</a>
   * @see #Value(String)
   */
  public static Expr SubString(Expr value, long start) {
    return SubString(value, new LongV(start));
  }

  /**
   * SubString function returns a subset of the source string
   *
   * @param value the source string
   * @param start the position in the source string where SubString starts extracting characters
   * @return      a new string contain a subset of the source string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=java">SubString</a>
   * @see #Value(String)
   */
  public static Expr SubString(String value, long start) {
    return SubString(new StringV(value), new LongV(start));
  }

  /**
   * SubString function returns a subset of the source string
   *
   * @param value the source string
   * @param start the position in the source string where SubString starts extracting characters
   * @param length the number of characters to be returned
   * @return       a new string contain a subset of the source string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=java">SubString</a>
   * @see #Value(String)
   */
  public static Expr SubString(Expr value, Expr start, Expr length) {
    return Fn.apply("substring", value, "start", start, "length", length);
  }

  /**
   * SubString function returns a subset of the source string
   *
   * @param value the source string
   * @param start the position in the source string where SubString starts extracting characters
   * @param length the number of characters to be returned
   * @return       a new string contain a subset of the source string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=java">SubString</a>
   * @see #Value(String)
   */
  public static Expr SubString(String value, Expr start, Expr length) {
    return SubString(new StringV(value), start, length);
  }

  /**
   * SubString function returns a subset of the source string
   *
   * @param value the source string
   * @param start the position in the source string where SubString starts extracting characters
   * @param length the number of characters to be returned
   * @return       a new string contain a subset of the source string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=java">SubString</a>
   * @see #Value(String)
   */
  public static Expr SubString(Expr value, long start, Expr length) {
    return SubString(value, new LongV(start), length);
  }

  /**
   * SubString function returns a subset of the source string
   *
   * @param value the source string
   * @param start the position in the source string where SubString starts extracting characters
   * @param length the number of characters to be returned
   * @return       a new string contain a subset of the source string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=java">SubString</a>
   * @see #Value(String)
   */
  public static Expr SubString(String value, long start, Expr length) {
    return SubString(new StringV(value), new LongV(start), length);
  }

  /**
   * SubString function returns a subset of the source string
   *
   * @param value the source string
   * @param start the position in the source string where SubString starts extracting characters
   * @param length the number of characters to be returned
   * @return       a new string contain a subset of the source string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=java">SubString</a>
   * @see #Value(String)
   */
  public static Expr SubString(Expr value, long start, long length) {
    return SubString(value, new LongV(start), new LongV(length));
  }

  /**
   * SubString function returns a subset of the source string
   *
   * @param value the source string
   * @param start the position in the source string where SubString starts extracting characters
   * @param length the number of characters to be returned
   * @return       a new string contain a subset of the source string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=java">SubString</a>
   * @see #Value(String)
   */
  public static Expr SubString(Expr value, Expr start, long length) {
    return SubString(value, start, new LongV(length));
  }

  /**
   * SubString function returns a subset of the source string
   *
   * @param value the source string
   * @param start the position in the source string where SubString starts extracting characters
   * @param length the number of characters to be returned
   * @return       a new string contain a subset of the source string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=java">SubString</a>
   * @see #Value(String)
   */
  public static Expr SubString(String value, Expr start, long length) {
    return SubString(new StringV(value), start, new LongV(length));
  }

  /**
   * SubString function returns a subset of the source string
   *
   * @param value the source string
   * @param start the position in the source string where SubString starts extracting characters
   * @param length the number of characters to be returned
   * @return       a new string contain a subset of the source string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=java">SubString</a>
   * @see #Value(String)
   */
  public static Expr SubString(String value, long start, long length) {
    return SubString(new StringV(value), new LongV(start), new LongV(length));
  }

  /**
   * TitleCase function returns a string with the first letter in each word capitalized
   *
   * @param value a strings to TitleCase
   * @return      a new string in TitleCase
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/titlecase?lang=java">TitleCase</a>
   * @see #Value(String)
   */
  public static Expr TitleCase(Expr value) {
    return Fn.apply("titlecase", value);
  }

  /**
   * TitleCase function returns a string with the first letter in each word capitalized
   *
   * @param value a strings to TitleCase
   * @return      a new string in TitleCase
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/titlecase?lang=java">TitleCase</a>
   * @see #Value(String)
   */
  public static Expr TitleCase(String value) {
    return TitleCase(new StringV(value));
  }

  /**
   * Trim function returns a new string with leading and trailing whitespace removed
   *
   * @param value a string to trim white space.
   * @return      the string with leading and trailing whitespace removed
   * Trim function returns
   *
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/trim?lang=java">Trim</a>
   * @see #Value(String)
   */
  public static Expr Trim(Expr value) {
    return Fn.apply("trim", value);
  }

  /**
   * Trim function returns a new string with leading and trailing whitespace removed
   *
   * @param value a string to trim white space.
   * @return      the string with leading and trailing whitespace removed
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/trim?lang=java">Trim</a>
   * @see #Value(String)
   */
  public static Expr Trim(String value) {
    return Trim(new StringV(value));
  }

  /**
   * UpperCase function returns all letters in the string in uppercase
   *
   * @param value a string to uppercase
   * @return      a string with all uppercase letters
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/uppercase?lang=java">UpperCase</a>
   * @see #Value(String)
   */
  public static Expr UpperCase(Expr value) {
    return Fn.apply("uppercase", value);
  }

  /**
   * UpperCase function returns all letters in the string in uppercase
   *
   * @param value a string to uppercase
   * @return      a string with all uppercase letters
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/uppercase?lang=java">UpperCase</a>
   * @see #Value(String)
   */
  public static Expr UpperCase(String value) {
    return UpperCase(new StringV(value));
  }

  /**
   * Format values into a string.
   *
   * @param format a string with format specifiers
   * @param values a list of values to format
   * @return      a string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/format?lang=java">Format</a>
   * @see #Value(String)
   */
  public static Expr Format(Expr format, Expr ...values) {
    return Fn.apply("format", format, "values", varargs(Arrays.asList(values)));
  }

  /**
   * Format values into a string.
   *
   * @param format a string with format specifiers
   * @param values a list of values to format
   * @return      a string
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/format?lang=java">Format</a>
   */
  public static Expr Format(String format, Expr ...values) {
    return Format(new StringV(format), values);
  }

  /**
   * Returns true if the string starts with the given prefix value, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param search  the prefix to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/startswith?lang=java">StartsWith</a>
   * @see #Value(String)
   */
  public static Expr StartsWith(Expr value, Expr search) {
    return Fn.apply("startswith", value, "search", search);
  }

  /**
   * Returns true if the string starts with the given prefix value, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param search  the prefix to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/startswith?lang=java">StartsWith</a>
   * @see #Value(String)
   */
  public static Expr StartsWith(String value, Expr search) {
    return StartsWith(new StringV(value), search);
  }

  /**
   * Returns true if the string starts with the given prefix value, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param search  the prefix to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/startswith?lang=java">StartsWith</a>
   * @see #Value(String)
   */
  public static Expr StartsWith(Expr value, String search) {
    return StartsWith(value, new StringV(search));
  }

  /**
   * Returns true if the string starts with the given prefix value, or false if otherwise
   *
   * @param value   the string to evaluate
   * @param search  the prefix to search for
   * @return        a {@link Expr} instance with the evaluation result
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/startswith?lang=java">StartsWith</a>
   * @see #Value(String)
   */
  public static Expr StartsWith(String value, String search) {
    return StartsWith(new StringV(value), new StringV(search));
  }

  /**
   * Creates a new timestamp from an ISO-8601 offset date/time string. A special string "now" can be used to get the
   * current transaction time. Multiple references to "now" within the same query will be equal.
   *
   * @param str an ISO-8601 formatted string or the string literal "now". Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/time?lang=java">Time</a>
   * @see #Value(String)
   */
  public static Expr Time(Expr str) {
    return Fn.apply("time", str);
  }

  /**
   * Creates a new timestamp from an ISO-8601 offset date/time string. A special string "now" can be used to get the
   * current transaction time. Multiple references to "now" within the same query will be equal.
   *
   * @param str an ISO-8601 formatted string or the string literal "now"
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/time?lang=java">Time</a>
   */
  public static Expr Time(String str) {
    return Time(new StringV(str));
  }

  /**
   * Constructs a timestamp relative to the epoch "1970-01-01T00:00:00Z" given a unit type and a number of units.
   *
   * @param num the number of units. Type: Number
   * @param unit the unit type
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/epoch?lang=java">Epoch</a>
   * @see TimeUnit
   * @see #Value(long)
   */
  public static Expr Epoch(Expr num, TimeUnit unit) {
    return Epoch(num, unit.value);
  }

  /**
   * Constructs a timestamp relative to the epoch "1970-01-01T00:00:00Z" given a unit type and a number of units.
   *
   * @param num the number of units.
   * @param unit the unit type
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/epoch?lang=java">Epoch</a>
   * @see TimeUnit
   */
  public static Expr Epoch(long num, TimeUnit unit) {
    return Epoch(new LongV(num), unit);
  }

  /**
   * Constructs a timestamp relative to the epoch "1970-01-01T00:00:00Z" given a unit type and a number of units.
   * Possible unit types are:
   * <ul>
   *   <li>day</li>
   *   <li>half day</li>
   *   <li>hour</li>
   *   <li>minute</li>
   *   <li>second</li>
   *   <li>millisecond</li>
   *   <li>microsecond</li>
   *   <li>nanosecond</li>
   * </ul>
   *
   * @param num the number of units. Type: Number
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/epoch?lang=java">Epoch</a>
   * @see #Value(long)
   */
  public static Expr Epoch(Expr num, Expr unit) {
    return Fn.apply("epoch", num, "unit", unit);
  }

  /**
   * Constructs a timestamp relative to the epoch "1970-01-01T00:00:00Z" given a unit type and a number of units.
   *
   * @param num the number of units. Type: Number
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/epoch?lang=java">Epoch</a>
   * @see #Value(long)
   */
  public static Expr Epoch(Expr num, String unit) {
    return Epoch(num, new StringV(unit));
  }

  /**
   * Constructs a timestamp relative to the epoch "1970-01-01T00:00:00Z" given a unit type and a number of units.
   *
   * @param num the number of units.
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/epoch?lang=java">Epoch</a>
   * @see #Value(String)
   */
  public static Expr Epoch(long num, Expr unit) {
    return Epoch(new LongV(num), unit);
  }

  /**
   * Constructs a timestamp relative to the epoch "1970-01-01T00:00:00Z" given a unit type and a number of units.
   *
   * @param num the number of units.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/epoch?lang=java">Epoch</a>
   */
  public static Expr Epoch(long num, String unit) {
    return Epoch(new LongV(num), new StringV(unit));
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data. Type: Time or Date
   * @param offset the number of units. Type: Number
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=java">TimeAdd</a>
   */
  public static Expr TimeAdd(Expr base, Expr offset, Expr unit) {
    return Fn.apply("time_add", base, "offset", offset, "unit", unit);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data. Type: Time or Date
   * @param offset the number of units. Type: Number
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=java">TimeAdd</a>
   */
  public static Expr TimeAdd(Expr base, Expr offset, TimeUnit unit) {
    return TimeAdd(base, offset, unit.value);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data. Type: Time or Date
   * @param offset the number of units. Type: Number
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=java">TimeAdd</a>
   */
  public static Expr TimeAdd(Expr base, Expr offset, String unit) {
    return TimeAdd(base, offset, Value(unit));
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data. Type: Time or Date
   * @param offset the number of units.
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=java">TimeAdd</a>
   */
  public static Expr TimeAdd(Expr base, long offset, Expr unit) {
    return TimeAdd(base, new LongV(offset), unit);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data. Type: Time or Date
   * @param offset the number of units.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=java">TimeAdd</a>
   */
  public static Expr TimeAdd(Expr base, long offset, TimeUnit unit) {
    return TimeAdd(base, new LongV(offset), unit.value);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data. Type: Time or Date
   * @param offset the number of units.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   */
  public static Expr TimeAdd(Expr base, long offset, String unit) {
    return TimeAdd(base, new LongV(offset), Value(unit));
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data.
   * @param offset the number of units. Type: Number
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=java">TimeAdd</a>
   */
  public static Expr TimeAdd(Instant base, Expr offset, Expr unit) {
    return TimeAdd(new TimeV(base), offset, unit);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data.
   * @param offset the number of units. Type: Number
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=java">TimeAdd</a>
   */
  public static Expr TimeAdd(Instant base, Expr offset, TimeUnit unit) {
    return TimeAdd(new TimeV(base), offset, unit.value);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data.
   * @param offset the number of units. Type: Number
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=java">TimeAdd</a>
   */
  public static Expr TimeAdd(Instant base, Expr offset, String unit) {
    return TimeAdd(new TimeV(base), offset, Value(unit));
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data.
   * @param offset the number of units.
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=java">TimeAdd</a>
   */
  public static Expr TimeAdd(Instant base, long offset, Expr unit) {
    return TimeAdd(new TimeV(base), new LongV(offset), unit);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data.
   * @param offset the number of units.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   */
  public static Expr TimeAdd(Instant base, long offset, TimeUnit unit) {
    return TimeAdd(new TimeV(base), new LongV(offset), unit.value);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data.
   * @param offset the number of units.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=java">TimeAdd</a>
   */
  public static Expr TimeAdd(Instant base, long offset, String unit) {
    return TimeAdd(new TimeV(base), new LongV(offset), Value(unit));
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data.
   * @param offset the number of units. Type: Number
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=java">TimeAdd</a>
   */
  public static Expr TimeAdd(LocalDate base, Expr offset, Expr unit) {
    return TimeAdd(new DateV(base), offset, unit);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data.
   * @param offset the number of units.
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=java">TimeAdd</a>
   */
  public static Expr TimeAdd(LocalDate base, long offset, Expr unit) {
     return TimeAdd(new DateV(base), new LongV(offset), unit);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data.
   * @param offset the number of units.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   */
  public static Expr TimeAdd(LocalDate base, long offset, TimeUnit unit) {
    return TimeAdd(new DateV(base), new LongV(offset), unit.value);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data.
   * @param offset the number of units.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=java">TimeAdd</a>
   */
  public static Expr TimeAdd(LocalDate base, long offset, String unit) {
    return TimeAdd(new DateV(base), new LongV(offset), Value(unit));
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data.
   * @param offset the number of units. Type: Number
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=java">TimeAdd</a>
   */
  public static Expr TimeAdd(LocalDate base, Expr offset, TimeUnit unit) {
    return TimeAdd(new DateV(base), offset, unit.value);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * added.
   *
   * @param base the base time or data.
   * @param offset the number of units. Type: Number
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=java">TimeAdd</a>
   */
  public static Expr TimeAdd(LocalDate base, Expr offset, String unit) {
    return TimeAdd(new DateV(base), offset, Value(unit));
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data. Type: Time or Date
   * @param offset the number of units. Type: Number
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(Expr base, Expr offset, Expr unit) {
    return Fn.apply("time_subtract", base, "offset", offset, "unit", unit);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data. Type: Time or Date
   * @param offset the number of units. Type: Number
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(Expr base, Expr offset, TimeUnit unit) {
    return TimeSubtract(base, offset, unit.value);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data. Type: Time or Date
   * @param offset the number of units. Type: Number
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(Expr base, Expr offset, String unit) {
    return TimeSubtract(base, offset, Value(unit));
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data. Type: Time or Date
   * @param offset the number of units.
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(Expr base, long offset, Expr unit) {
    return TimeSubtract(base, new LongV(offset), unit);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data. Type: Time or Date
   * @param offset the number of units.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(Expr base, long offset, TimeUnit unit) {
    return TimeSubtract(base, new LongV(offset), unit.value);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data. Type: Time or Date
   * @param offset the number of units.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(Expr base, long offset, String unit) {
    return TimeSubtract(base, new LongV(offset), Value(unit));
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data.
   * @param offset the number of units. Type: Number
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(Instant base, Expr offset, Expr unit) {
    return TimeSubtract(new TimeV(base), offset, unit);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data.
   * @param offset the number of units. Type: Number
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(Instant base, Expr offset, TimeUnit unit) {
    return TimeSubtract(new TimeV(base), offset, unit.value);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data.
   * @param offset the number of units. Type: Number
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(Instant base, Expr offset, String unit) {
    return TimeSubtract(new TimeV(base), offset, Value(unit));
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data.
   * @param offset the number of units.
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(Instant base, long offset, Expr unit) {
    return TimeSubtract(new TimeV(base), new LongV(offset), unit);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data.
   * @param offset the number of units.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(Instant base, long offset, TimeUnit unit) {
    return TimeSubtract(new TimeV(base), new LongV(offset), unit.value);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data.
   * @param offset the number of units.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(Instant base, long offset, String unit) {
    return TimeSubtract(new TimeV(base), new LongV(offset), Value(unit));
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data.
   * @param offset the number of units. Type: Number
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(LocalDate base, Expr offset, Expr unit) {
    return TimeSubtract(new DateV(base), offset, unit);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data.
   * @param offset the number of units. Type: Number
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(LocalDate base, Expr offset, TimeUnit unit) {
    return TimeSubtract(new DateV(base), offset, unit.value);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data.
   * @param offset the number of units. Type: Number
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(LocalDate base, Expr offset, String unit) {
    return TimeSubtract(new DateV(base), offset, Value(unit));
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data.
   * @param offset the number of units.
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(LocalDate base, long offset, Expr unit) {
     return TimeSubtract(new DateV(base), new LongV(offset), unit);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data.
   * @param offset the number of units.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(LocalDate base, long offset, TimeUnit unit) {
    return TimeSubtract(new DateV(base), new LongV(offset), unit.value);
  }

  /**
   * Returns a new time or date with the offset in terms of the unit
   * subtracted.
   *
   * @param base the base time or data.
   * @param offset the number of units.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=java">TimeSubtract</a>
   */
  public static Expr TimeSubtract(LocalDate base, long offset, String unit) {
    return TimeSubtract(new DateV(base), new LongV(offset), Value(unit));
  }

  /**
   * Returns the number of intervals in terms of the unit between
   * two times or dates. Both start and finish must be of the same
   * type.
   *
   * @param start the starting time or date, inclusive. Type: Time or Date
   * @param finish the ending time or date, exclusive. Type: Time or Date
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=java">TimeDiff</a>
   */
  public static Expr TimeDiff(Expr start, Expr finish, Expr unit) {
    return Fn.apply("time_diff", start, "other", finish, "unit", unit);
  }

  /**
   * Returns the number of intervals in terms of the unit between
   * two times or dates. Both start and finish must be of the same
   * type.
   *
   * @param start the starting time or date, inclusive. Type: Time or Date
   * @param finish the ending time or date, exclusive. Type: Time or Date
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=java">TimeDiff</a>
   */
  public static Expr TimeDiff(Expr start, Expr finish, TimeUnit unit) {
    return TimeDiff(start, finish, unit.value);
  }

  /**
   * Returns the number of intervals in terms of the unit between
   * two times or dates. Both start and finish must be of the same
   * type.
   *
   * @param start the starting time or date, inclusive. Type: Time or Date
   * @param finish the ending time or date, exclusive. Type: Time or Date
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=java">TimeDiff</a>
   */
  public static Expr TimeDiff(Expr start, Expr finish, String unit) {
    return TimeDiff(start, finish, Value(unit));
  }

  /**
   * Returns the number of intervals in terms of the unit between
   * two times.
   *
   * @param start the starting time, inclusive.
   * @param finish the ending time, exclusive. Type: Time
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=java">TimeDiff</a>
   */
  public static Expr TimeDiff(Instant start, Expr finish, Expr unit) {
    return TimeDiff(new TimeV(start), finish, unit);
  }

  /**
   * Returns the number of intervals in terms of the unit between
   * two times.
   *
   * @param start the starting time, inclusive.
   * @param finish the ending time, exclusive. Type: Time
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=java">TimeDiff</a>
   */
  public static Expr TimeDiff(Instant start, Expr finish, TimeUnit unit) {
    return TimeDiff(new TimeV(start), finish, unit.value);
  }

  /**
   * Returns the number of intervals in terms of the unit between
   * two times.
   *
   * @param start the starting time, inclusive.
   * @param finish the ending time, exclusive. Type: Time
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=java">TimeDiff</a>
   */
  public static Expr TimeDiff(Instant start, Expr finish, String unit) {
    return TimeDiff(new TimeV(start), finish, Value(unit));
  }

  /**
   * Returns the number of intervals in terms of the unit between
   * two times.
   *
   * @param start the starting time, inclusive.
   * @param finish the ending time, exclusive.
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=java">TimeDiff</a>
   */
  public static Expr TimeDiff(Instant start, Instant finish, Expr unit) {
    return TimeDiff(new TimeV(start), new TimeV(finish), unit);
  }

  /**
   * Returns the number of intervals in terms of the unit between
   * two times.
   *
   * @param start the starting time, inclusive.
   * @param finish the ending time, exclusive.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=java">TimeDiff</a>
   */
  public static Expr TimeDiff(Instant start, Instant finish, TimeUnit unit) {
    return TimeDiff(new TimeV(start), new TimeV(finish), unit.value);
  }

  /**
   * Returns the number of intervals in terms of the unit between
   * two times.
   *
   * @param start the starting time, inclusive.
   * @param finish the ending time, exclusive.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=java">TimeDiff</a>
   */
  public static Expr TimeDiff(Instant start, Instant finish, String unit) {
    return TimeDiff(new TimeV(start), new TimeV(finish), Value(unit));
  }

  /**
   * Returns the number of intervals in terms of the unit between
   * two dates.
   *
   * @param start the starting date, inclusive.
   * @param finish the ending date, exclusive. Type: Date
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=java">TimeDiff</a>
   */
  public static Expr TimeDiff(LocalDate start, Expr finish, Expr unit) {
    return TimeDiff(new DateV(start), finish, unit);
  }

  /**
   * Returns the number of intervals in terms of the unit between
   * two dates.
   *
   * @param start the starting date, inclusive.
   * @param finish the ending date, exclusive. Type: Date
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=java">TimeDiff</a>
   */
  public static Expr TimeDiff(LocalDate start, Expr finish, TimeUnit unit) {
    return TimeDiff(new DateV(start), finish, unit.value);
  }

  /**
   * Returns the number of intervals in terms of the unit between
   * two dates.
   *
   * @param start the starting date, inclusive.
   * @param finish the ending date, exclusive. Type: Date
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=java">TimeDiff</a>
   */
  public static Expr TimeDiff(LocalDate start, Expr finish, String unit) {
    return TimeDiff(new DateV(start), finish, Value(unit));
  }

  /**
   * Returns the number of intervals in terms of the unit between
   * two dates.
   *
   * @param start the starting date, inclusive.
   * @param finish the ending date, exclusive.
   * @param unit the unit type. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=java">TimeDiff</a>
   */
  public static Expr TimeDiff(LocalDate start, LocalDate finish, Expr unit) {
    return TimeDiff(new DateV(start), new DateV(finish), unit);
  }

  /**
   * Returns the number of intervals in terms of the unit between
   * two dates.
   *
   * @param start the starting date, inclusive.
   * @param finish the ending date, exclusive.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=java">TimeDiff</a>
   */
  public static Expr TimeDiff(LocalDate start, LocalDate finish, TimeUnit unit) {
    return TimeDiff(new DateV(start), new DateV(finish), unit.value);
  }

  /**
   * Returns the number of intervals in terms of the unit between
   * two dates.
   *
   * @param start the starting date, inclusive.
   * @param finish the ending date, exclusive.
   * @param unit the unit type.
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=java">TimeDiff</a>
   */
  public static Expr TimeDiff(LocalDate start, LocalDate finish, String unit) {
    return TimeDiff(new DateV(start), new DateV(finish), Value(unit));
  }

  /**
   * Creates a date from an ISO-8601 formatted date string.
   *
   * @param str an ISO-8601 formatted date string. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/date?lang=java">Date</a>
   * @see #Value(String)
   */
  public static Expr Date(Expr str) {
    return Fn.apply("date", str);
  }

  /**
   * Creates a date from an ISO-8601 formatted date string.
   *
   * @param str an ISO-8601 formatted date string
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/date?lang=java">Date</a>
   */
  public static Expr Date(String str) {
    return Date(new StringV(str));
  }

  /**
   * Returns the current snapshot time.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/now?lang=java">Now</a>
   */
  public static Expr Now() {
    return Fn.apply("now", Null());
  }

  /**
   * Returns a new string identifier suitable for use when constructing references.
   *
   * @deprecated Use NewId() instead.
   *
   * @return a new {@link Expr} instance
   * @see #Ref(Expr, Expr)
   */
  @Deprecated
  public static Expr NextId() {
    return Fn.apply("next_id", NullV.NULL);
  }

  /**
   * Returns a new string identifier suitable for use when constructing references.
   *
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/newid?lang=java">NewId</a>
   * @see #Ref(Expr, Expr)
   */
  public static Expr NewId() {
    return Fn.apply("new_id", NullV.NULL);
  }

  /**
   * Creates a new reference for the given class name.
   *
   * @param name the class name. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/class?lang=java">Class</a>
   * 
   * @deprecated use Collection instead
   */
  @Deprecated
  public static Expr Class(Expr name) {
    return Fn.apply("class", name);
  }

  /**
   * Creates a new reference for the given class name.
   *
   * @param name the class name
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/class?lang=java">Class</a>
   * 
   * @deprecated use Collection instead
   */
  @Deprecated
  public static Expr Class(String name) {
    return Class(Value(name));
  }

  /**
   * Creates a reference for the given class name, scoped to the database provided.
   *
   * @param name the class name. Type: String
   * @param database the scope database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/class?lang=java">Class</a>
   * @see #Database(String)
   * 
   * @deprecated use Collection instead
   */
  @Deprecated
  public static Expr Class(Expr name, Expr database) {
    return Fn.apply("class", name, "scope", database);
  }

  /**
   * Creates a new reference for the given collection name.
   *
   * @param name the collection name. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/collection?lang=java">Collection</a>
   */
  public static Expr Collection(Expr name) {
    return Fn.apply("collection", name);
  }

  /**
   * Creates a new reference for the given collection name.
   *
   * @param name the collection name
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/collection?lang=java">Collection</a>
   */
  public static Expr Collection(String name) {
    return Collection(Value(name));
  }

  /**
   * Creates a reference for the given collection name, scoped to the database provided.
   *
   * @param name the collection name. Type: String
   * @param database the scope database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/collection?lang=java">Collection</a>
   * @see #Database(String)
   */
  public static Expr Collection(Expr name, Expr database) {
    return Fn.apply("collection", name, "scope", database);
  }

  /**
   * Creates a reference for the given class name, scoped to the database provided.
   *
   * @param name the class name
   * @param database the scope database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/class?lang=java">Class</a>
   * @see #Database(String)
   *
   * @deprecated use Collection instead
   */
  @Deprecated
  public static Expr Class(String name, Expr database) {
    return Class(Value(name), database);
  }

  /**
   * Creates a reference for the given collection name, scoped to the database provided.
   *
   * @param name the collection name
   * @param database the scope database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/collection?lang=java">Collection</a>
   * @see #Database(String)
   */
  public static Expr Collection(String name, Expr database) {
    return Collection(Value(name), database);
  }

  /**
   * Creates a reference for the given database name.
   *
   * @param name the database name. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/database?lang=java">Database</a>
   */
  public static Expr Database(Expr name) {
    return Fn.apply("database", name);
  }

  /**
   * Creates a reference for the given database name.
   *
   * @param name the database name
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/database?lang=java">Database</a>
   */
  public static Expr Database(String name) {
    return Database(Value(name));
  }

  /**
   * Creates a reference for the given database name, scoped to the database provided.
   *
   * @param name the database name. Type: String
   * @param database the scope database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/database?lang=java">Database</a>
   */
  public static Expr Database(Expr name, Expr database) {
      return Fn.apply("database", name, "scope", database);
  }

  /**
   * Creates a reference for the given database name, scoped to the database provided.
   *
   * @param name the database name
   * @param database the scope database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/database?lang=java">Database</a>
   */
  public static Expr Database(String name, Expr database) {
    return Database(Value(name), database);
  }

  /**
   * Creates a reference for the given index name.
   *
   * @param name the index name. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/iindex?lang=java">Index</a>
   */
  public static Expr Index(Expr name) {
    return Fn.apply("index", name);
  }

  /**
   * Creates a reference for the given index name.
   *
   * @param name the index name
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/iindex?lang=java">Index</a>
   */
  public static Expr Index(String name) {
    return Index(Value(name));
  }

  /**
   * Creates a reference for the given index name, scoped to the database provided.
   *
   * @param name the index name. Type: String
   * @param database the scope database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/iindex?lang=java">Index</a>
   */
  public static Expr Index(Expr name, Expr database) {
    return Fn.apply("index", name, "scope", database);
  }

  /**
   * Creates a reference for the given index name, scoped to the database provided.
   *
   * @param name the index name
   * @param database the scope database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/iindex?lang=java">Index</a>
   */
  public static Expr Index(String name, Expr database) {
    return Index(Value(name), database);
  }

  /**
   * Creates a reference for the given user defined function name.
   *
   * @param name the user defined function name. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/function?lang=java">Function</a>
   */
  public static Expr Function(Expr name) {
    return Fn.apply("function", name);
  }

  /**
   * Creates a reference for the given user defined function name.
   *
   * @param name the user defined function name
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/function?lang=java">Function</a>
   */
  public static Expr Function(String name) {
    return Function(Value(name));
  }

  /**
   * Creates a reference for the given user defined function name, scoped to the database provided.
   *
   * @param name the user defined function name. Type: String
   * @param database the scope database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/function?lang=java">Function</a>
   */
  public static Expr Function(Expr name, Expr database) {
    return Fn.apply("function", name, "scope", database);
  }

  /**
   * Creates a reference for the given user defined function name, scoped to the database provided.
   *
   * @param name the user defined function name
   * @param database the scope database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/function?lang=java">Function</a>
   */
  public static Expr Function(String name, Expr database) {
    return Function(Value(name), database);
  }

  /**
   * Creates a reference for the given role name.
   *
   * @param name the role name. Type: String
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/role?lang=java">Role</a>
   */
  public static Expr Role(Expr name) {
    return Fn.apply("role", name);
  }

  /**
   * Creates a reference for the given role name.
   *
   * @param name the role name
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/role?lang=java">Role</a>
   */
  public static Expr Role(String name) {
    return Role(Value(name));
  }

  /**
   * Creates a reference for the given role name, scoped to the database provided.
   *
   * @param name the role name. Type: String
   * @param database the scope database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/role?lang=java">Role</a>
   */
  public static Expr Role(Expr name, Expr database) {
    return Fn.apply("role", name, "scope", database);
  }

  /**
   * Creates a reference for the given role name, scoped to the database provided.
   *
   * @param name the role name.
   * @param database the scope database. Type: Reference
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/role?lang=java">Role</a>
   */
  public static Expr Role(String name, Expr database) {
    return Role(Value(name), database);
  }

  /**
   * Tests equivalence between a list of values.
   *
   * @param values the values to test equivalence for
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/equals?lang=java">Equals</a>
   */
  public static Expr Equals(List<? extends Expr> values) {
    return Fn.apply("equals", varargs(values));
  }

  /**
   * Tests equivalence between a list of values.
   *
   * @param values the values to test equivalence for
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/equals?lang=java">Equals</a>
   */
  public static Expr Equals(Expr... values) {
    return Equals(Collections.unmodifiableList(Arrays.asList(values)));
  }

  /**
   * Returns true if the target value contains the given path, and false otherwise.
   * The path must be an array in which each element can be either a string, or a number.
   * If a string, the path segment refers to an object key. If a number, the path segment refers to an array index.
   *
   * For convenience, a path builder is available at the {@link #Contains(Path, Expr)} function.
   *
   * @param path the desired path to check for presence. Type: Array
   * @param in the target value. Type: Object or Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/contains?lang=java">Contains</a>
   * @see #Contains(Path, Expr)
   *
   * @deprecated use {@code ContainsPath} instead
   */
  @Deprecated
  public static Expr Contains(Expr path, Expr in) {
    return Fn.apply("contains", path, "in", in);
  }

  /**
   * Returns true if the target value contains the given path, and false otherwise.
   *
   * @param path the desired path to check for presence
   * @param in the target value. Type: Object or Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/contains?lang=java">Contains</a>
   *
   * @deprecated use {@code ContainsPath} instead
   */
  @Deprecated
  public static Expr Contains(Path path, Expr in) {
    return Contains(Arr(path.segments), in);
  }

  /**
   * Returns true if the target Expr contains the given field, and false otherwise.
   *
   * @param field the desired value to field for presence.
   * @param in the target value. Type: Object or Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/containsfield?lang=java">ContainsField</a>
   */
  public static Expr ContainsField(Expr field, Expr in) {
    return Fn.apply("contains_field", field, "in", in);
  }

  /**
   * Returns true if the target value contains the given path, and false otherwise.
   * The path must be an array in which each element can be either a string, or a number.
   * If a string, the path segment refers to an object key. If a number, the path segment refers to an array index.
   *
   * For convenience, a path builder is available at the {@link #ContainsPath(Path, Expr)} function.
   *
   * @param path the desired path to check for presence. Type: Array
   * @param in the target value. Type: Object or Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/containspath?lang=java">ContainsPath</a>
   * @see #ContainsPath(Path, Expr)
   */
  public static Expr ContainsPath(Expr path, Expr in) {
    return Fn.apply("contains_path", path, "in", in);
  }

  /**
   * Returns true if the target value contains the given path, and false otherwise.
   *
   * @param path the desired path to check for presence
   * @param in the target value. Type: Object or Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/containspath?lang=java">ContainsPath</a>
   */
  public static Expr ContainsPath(Path path, Expr in) {
    return ContainsPath(Arr(path.segments), in);
  }

  /**
   * Returns true if the target Expr contains the given value, and false otherwise.
   *
   * @param value the desired value to check for presence.
   * @param in the target value. Type: Ref, Object, Array or Set
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/containsvalue?lang=java">ContainsValue</a>
   */
  public static Expr ContainsValue(Expr value, Expr in) {
    return Fn.apply("contains_value", value, "in", in);
  }

  /**
   * Constructs a path matching object keys.
   *
   * @param segments the object keys
   * @return a new {@link Path} instance
   * @see Path
   * @see #Contains(Path, Expr)
   * @see #Select(Path, Expr)
   * @see #Select(Path, Expr, Expr)
   * @see #SelectAll(Path, Expr)
   */
  public static Path Path(String... segments) {
    return new Path().at(segments);
  }

  /**
   * Constructs a path matching array indexes.
   *
   * @param segments the array indexes
   * @return a new {@link Path} instance
   * @see Path
   * @see #Contains(Path, Expr)
   * @see #Select(Path, Expr)
   * @see #Select(Path, Expr, Expr)
   * @see #SelectAll(Path, Expr)
   */
  public static Path Path(int... segments) {
    return new Path().at(segments);
  }

  /**
   * Traverses target resource returning the value under the given path. Returns an error if the path doesn't exist.
   * The path must be an array in which each element can be either a string, or a number. If a string, the path segment
   * refers to an object key. If a number, the path segment refers to an array index.
   *
   * For convenience, a path builder is available at the {@link #Select(Path, Expr)} function.
   *
   * @param path the path to the desired value. Type: Array
   * @param from the target resource. Type: Object or Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/select?lang=java">Select</a>
   * @see #Select(Path, Expr)
   */
  public static Expr Select(Expr path, Expr from) {
    return Fn.apply("select", path, "from", from);
  }

  /**
   * Traverses target resource returning the value under the given path. Returns the default value provided if the path
   * doesn't exist. The path must be an array in which each element can be either a string, or a number. If a string,
   * the path segment refers to an object key. If a number, the path segment refers to an array index.
   *
   * For convenience, a path builder is available at the {@link #Select(Path, Expr, Expr)} function.
   *
   * @param path the path to the desired value. Type: Array
   * @param from the target resource. Type: Object or Array
   * @param defaultValue the default value to return if the desired path doesn't exist
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/select?lang=java">Select</a>
   * @see #Select(Path, Expr, Expr)
   */
  public static Expr Select(Expr path, Expr from, Expr defaultValue) {
    return Fn.apply("select", path, "from", from, "default", defaultValue);
  }

  /**
   * Traverses target resource returning the value under the given path. Returns an error if the path doesn't exist.
   *
   * @param path the path to the desired value
   * @param from the target resource. Type: Object or Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/select?lang=java">Select</a>
   */
  public static Expr Select(Path path, Expr from) {
    return Select(Arr(path.segments), from);
  }

  /**
   * Traverses target resource returning the value under the given path. Returns the default value provided if the path
   * doesn't exist.
   *
   * @param path the path to the desired value
   * @param from the target resource. Type: Object or Array
   * @param defaultValue the default value to return if the desired path doesn't exist
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/select?lang=java">Select</a>
   */
  public static Expr Select(Path path, Expr from, Expr defaultValue) {
    return Select(Arr(path.segments), from, defaultValue);
  }

  /**
   * Selects the desired path for each element in the given array. The path must be an array in which each element
   * can be either a string, or a number. If a string, the path segment refers to an object key. If a number, the
   * path segment refers to an array index.
   *
   * For convenience, a path builder is available at the {@link #SelectAll(Path, Expr)} function.
   *
   * @param path the path to the desired value. Type: Array
   * @param from the collection to traverse. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/selectall?lang=java">SelectAll</a>
   * @see #Select(Path, Expr)
   *
   * @deprecated use SelectAsIndex instead
   */
  @Deprecated
  public static Expr SelectAll(Expr path, Expr from) {
    return Fn.apply("select_all", path, "from", from);
  }

  /**
   * Selects the desired path for each element in the given array.
   *
   * @param path the path to the desired value
   * @param from the collection to traverse. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/selectall?lang=java">SelectAll</a>
   *
   * @deprecated use SelectAsIndex instead
   */
  @Deprecated
  public static Expr SelectAll(Path path, Expr from) {
    return SelectAll(Arr(path.segments), from);
  }

  /**
   * Selects the desired path for each element in the given array. The path must be an array in which each element
   * can be either a string, or a number. If a string, the path segment refers to an object key. If a number, the
   * path segment refers to an array index.
   *
   * For convenience, a path builder is available at the {@link #SelectAsIndex(Path, Expr)} function.
   *
   * @param path the path to the desired value. Type: Array
   * @param from the collection to traverse. Type: Array
   * @return a new {@link Expr} instance
   * @see #Select(Path, Expr)
   */
  public static Expr SelectAsIndex(Expr path, Expr from) {
    return Fn.apply("select_as_index", path, "from", from);
  }

  /**
   * Selects the desired path for each element in the given array.
   *
   * @param path the path to the desired value
   * @param from the collection to traverse. Type: Array
   * @return a new {@link Expr} instance
   */
  public static Expr SelectAsIndex(Path path, Expr from) {
    return SelectAsIndex(Arr(path.segments), from);
  }

  /**
   * Computes the abs of a number.
   *
   * @param value The operand to abs. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/abs?lang=java">Abs</a>
   */
  public static Expr Abs(Expr value) {
    return Fn.apply("abs", value);
  }

  /**
   * Computes the abs of a number.
   *
   * @param value The operand to abs. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/abs?lang=java">Abs</a>
   */
  public static Expr Abs(Long value) {
    return Fn.apply("abs", new LongV(value));
  }

  /**
   * Computes the abs of a number.
   *
   * @param value The operand to abs. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/abs?lang=java">Abs</a>
   */
  public static Expr Abs(Double value) {
    return Fn.apply("abs", new DoubleV(value));
  }

  /**
   * Computes the acos of a number.
   *
   * @param value The operand to acos. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/acos?lang=java">Acos</a>
   */
  public static Expr Acos(Expr value) {
    return Fn.apply("acos", value);
  }

  /**
   * Computes the acos of a number.
   *
   * @param value The operand to acos. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/acos?lang=java">Acos</a>
   */
    public static Expr Acos(Double value) {
      return Fn.apply("acos", new DoubleV(value));
    }


    /**
     * Computes the sum of a list of numbers.
     *
     * @param values the list of numbers. Type: Array
     * @return a {@link Expr} instance
     * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/add?lang=java">Add</a>
     */
  public static Expr Add(List<? extends Expr> values) {
    return Fn.apply("add", varargs(values));
  }

  /**
   * Computes the sum of a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
     * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/add?lang=java">Add</a>
   */
  public static Expr Add(Expr... values) {
    return Add(Collections.unmodifiableList(Arrays.asList(values)));
  }

  /**
   * Computes the asin of a number.
   *
   * @param value The operand to asin. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/asin?lang=java">Asin</a>
   */
  public static Expr Asin(Expr value) {
    return Fn.apply("asin", value);
  }

  /**
   * Computes the asin of a number.
   *
   * @param value The operand to asin. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/asin?lang=java">Asin</a>
   */
  public static Expr Asin(Double value) {
    return Fn.apply("asin", new DoubleV(value));
  }

  /**
   * Computes the atan of a number.
   *
   * @param value The operand to atan. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/atan?lang=java">Atan</a>
   */
  public static Expr Atan(Expr value) {
    return Fn.apply("atan", value);
  }

  /**
   * Computes the atan of a number.
   *
   * @param value The operand to atan. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/atan?lang=java">Atan</a>
   */
  public static Expr Atan(Double value) {
    return Fn.apply("atan", new DoubleV(value));
  }

  /**
   * Computes the bitwise and of a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/bitand?lang=java">BitAnd</a>
   */
  public static Expr BitAnd(List<? extends Expr> values) {
    return Fn.apply("bitand", varargs(values));
  }

  /**
   * Computes the bitwise and of a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/bitand?lang=java">BitAnd</a>
   */
  public static Expr BitAnd(Expr... values) {
    return BitAnd(Arrays.asList(values));
  }

  /**
   * Computes the bitwise NOT of a number.
   *
   * @param value The operand to atan. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/bitnot?lang=java">BitNot</a>
   */
  public static Expr BitNot(Expr value) {
    return Fn.apply("bitnot", value);
  }

  /**
   * Computes the bitwise NOT of a number.
   *
   * @param value The operand to atan. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/bitnot?lang=java">BitNot</a>
   */
  public static Expr BitNot(Long value) {
    return Fn.apply("bitnot", new LongV(value));
  }

  /**
   * Computes the bitwise OR of a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/bitor?lang=java">BitOr</a>
   */
  public static Expr BitOr(List<? extends Expr> values) {
    return Fn.apply("bitor", varargs(values));
  }

  /**
   * Computes the bitwise OR of a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/bitor?lang=java">BitOr</a>
   */
  public static Expr BitOr(Expr... values) {
    return BitOr(Arrays.asList(values));
  }

  /**
   * Computes the bitwise XOR of a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/bitxor?lang=java">BitXor</a>
   */
  public static Expr BitXor(List<? extends Expr> values) {
    return Fn.apply("bitxor", varargs(values));
  }

  /**
   * Computes the bitwise XOR of a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/bitxor?lang=java">BitXor</a>
   */
  public static Expr BitXor(Expr... values) {
    return BitXor(Arrays.asList(values));
  }

  /**
   * Computes the Ceil of a number.
   *
   * @param value The operand to ceil. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/ceil?lang=java">Ceil</a>
   */
  public static Expr Ceil(Expr value) {
    return Fn.apply("ceil", value);
  }

  /**
   * Computes the Ceil of a number.
   *
   * @param value The operand to ceil. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/ceil?lang=java">Ceil</a>
   */
  public static Expr Ceil(Long value) {
    return Fn.apply("ceil", new LongV(value));
  }

  /**
   * Computes the Ceil of a number.
   *
   * @param value The operand to ceil. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/ceil?lang=java">Ceil</a>
   */
  public static Expr Ceil(Double value) {
    return Fn.apply("ceil", new DoubleV(value));
  }

  /**
   * Computes the cosine of a number.
   *
   * @param value The operand to cos. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/cos?lang=java">Cos</a>
   */
  public static Expr Cos(Expr value) {
    return Fn.apply("cos", value);
  }

  /**
   * Computes the cosine of a number.
   *
   * @param value The operand to cos. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/cos?lang=java">Cos</a>
   */
  public static Expr Cos(Double value) {
    return Fn.apply("cos", new DoubleV(value));
  }

  /**
   * Computes the hyperbolic cosine of a number.
   *
   * @param value The operand to cosh. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/cosh?lang=java">Cosh</a>
   */
  public static Expr Cosh(Expr value) {
    return Fn.apply("cosh", value);
  }

  /**
   * Computes the hyperbolic cosine of a number.
   *
   * @param value The operand to cosh. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/cosh?lang=java">Cosh</a>
   */
  public static Expr Cosh(Double value) {
    return Fn.apply("cosh", new DoubleV(value));
  }

  /**
   * Computes the degrees of a number.
   *
   * @param value The operand to degrees. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/degrees?lang=java">Degrees</a>
   */
  public static Expr Degrees(Expr value) {
    return Fn.apply("degrees", value);
  }

  /**
   * Computes the degrees of a number.
   *
   * @param value The operand to degrees. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/degrees?lang=java">Degrees</a>
   */
  public static Expr Degrees(Double value) {
    return Fn.apply("degrees", new DoubleV(value));
  }

  /**
   * Computes the degrees of a number.
   *
   * @param value The operand to degrees. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/degrees?lang=java">Degrees</a>
   */
  public static Expr Degrees(Long value) {
    return Fn.apply("degrees", new LongV(value));
  }

  /**
   * Computes the quotient of a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/divide?lang=java">Divide</a>
   */
  public static Expr Divide(List<? extends Expr> values) {
    return Fn.apply("divide", varargs(values));
  }

  /**
   * Computes the quotient of a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/divide?lang=java">Divide</a>
   */
  public static Expr Divide(Expr... values) {
    return Divide(Collections.unmodifiableList(Arrays.asList(values)));
  }

  /**
   * Computes the exp of a number.
   *
   * @param value The operand to exp. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/exp?lang=java">Exp</a>
   */
  public static Expr Exp(Expr value) {
    return Fn.apply("exp", value);
  }

  /**
   * Computes the exp of a number.
   *
   * @param value The operand to exp. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/exp?lang=java">Exp</a>
   */
  public static Expr Exp(Long value) {
    return Fn.apply("exp", new LongV(value));
  }

  /**
   * Computes the exp of a number.
   *
   * @param value The operand to exp. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/exp?lang=java">Exp</a>
   */
  public static Expr Exp(Double value) {
    return Fn.apply("exp", new DoubleV(value));
  }

  /**
   * Computes the floor of a number.
   *
   * @param value The operand to floor Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/floor?lang=java">Floor</a>
   */
  public static Expr Floor(Expr value) {
    return Fn.apply("floor", value);
  }

  /**
   * Computes the floor of a number.
   *
   * @param value The operand to floor Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/floor?lang=java">Floor</a>
   */
  public static Expr Floor(Long value) {
    return Fn.apply("floor", new LongV(value));
  }

  /**
   * Computes the floor of a number.
   *
   * @param value The operand to floor Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/floor?lang=java">Floor</a>
   */
  public static Expr Floor(Double value) {
    return Fn.apply("floor", new DoubleV(value));
  }

  /**
   * Computes the ln of a number.
   *
   * @param value The operand to ln. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/ln?lang=java">Ln</a>
   */
  public static Expr Ln(Expr value) {
    return Fn.apply("ln", value);
  }

  /**
   * Computes the ln of a number.
   *
   * @param value The operand to ln. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/ln?lang=java">Ln</a>
   */
  public static Expr Ln(Double value) {
    return Fn.apply("ln", new DoubleV(value));
  }

  /**
   * Hypot to calculate a hypotenuse of a right triangle give the 2 sides
   *
   * @param num the base. Type: Number
   * @param exp the exponent, default 2
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/hypot?lang=java">Hypot</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Hypot(Expr num, Expr exp) {
    return Fn.apply("hypot",  num, "b", exp);
  }

  /**
   * Hypot to calculate a hypotenuse of a right triangle give the 2 sides
   *
   * @param num the base. Type: Number
   * @param exp the exponent, default 2
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/hypot?lang=java">Hypot</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Hypot(Double num, Expr exp) {
    return Fn.apply("hypot",  new DoubleV(num), "b", exp);
  }

  /**
   * Hypot to calculate a hypotenuse of a right triangle give the 2 sides
   *
   * @param num the base. Type: Number
   * @param exp the exponent, default 2
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/hypot?lang=java">Hypot</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Hypot(Expr num, Double exp) {
    return Fn.apply("hypot",  num, "b", new DoubleV(exp));
  }

  /**
   * Hypot to calculate a hypotenuse of a right triangle give the 2 sides
   *
   * @param num the base. Type: Number
   * @param exp the exponent, default 2
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/hypot?lang=java">Hypot</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Hypot(Double num, Double exp) {
    return Fn.apply("hypot",  new DoubleV(num), "b", new DoubleV(exp));
  }

  /**
   * Hypot to calculate a hypotenuse of a isosceles right triangle give side
   *
   * @param num the base. Type: Number
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/hypot?lang=java">Hypot</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Hypot(Expr num) {
    return Fn.apply("hypot",  num);
  }

  /**
   * Hypot to calculate a hypotenuse of a isosceles right triangle give side
   *
   * @param num the base. Type: Number
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/hypot?lang=java">Hypot</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Hypot(Double num) {
    return Fn.apply("hypot",  new DoubleV(num));
  }

  /**
   * Computes the log of a number.
   *
   * @param value The operand to log. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/log?lang=java">Log</a>
   */
  public static Expr Log(Expr value) {
    return Fn.apply("log", value);
  }

  /**
   * Computes the log of a number.
   *
   * @param value The operand to log. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/log?lang=java">Log</a>
   */
  public static Expr Log(Double value) {
    return Fn.apply("log", new DoubleV(value));
  }

  /**
   * Computes the max in a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/max?lang=java">Max</a>
   */
  public static Expr Max(List<? extends Expr> values) {
    return Fn.apply("max", varargs(values));
  }

  /**
   * Computes the max in a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/max?lang=java">Max</a>
   */
  public static Expr Max(Expr... values) {
    return Max(Arrays.asList(values));
  }

  /**
   * Computes the min in a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/min?lang=java">Min</a>
   */
  public static Expr Min(List<? extends Expr> values) {
    return Fn.apply("min", varargs(values));
  }

  /**
   * Computes the min in a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/min?lang=java">Min</a>
   */
  public static Expr Min(Expr... values) {
    return Min(Arrays.asList(values));
  }

  /**
   * Computes the remainder after division of a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/modulo?lang=java">Modulo</a>
   */
  public static Expr Modulo(List<? extends Expr> values) {
    return Fn.apply("modulo", varargs(values));
  }

  /**
   * Computes the remainder after division of a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/modulo?lang=java">Modulo</a>
   */
  public static Expr Modulo(Expr... values) {
    return Modulo(Collections.unmodifiableList(Arrays.asList(values)));
  }

  /**
   * Computes the product of a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/multiply?lang=java">Multiply</a>
   */
  public static Expr Multiply(List<? extends Expr> values) {
    return Fn.apply("multiply", varargs(values));
  }

  /**
   * Computes the product of a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/multiply?lang=java">Multiply</a>
   */
  public static Expr Multiply(Expr... values) {
    return Multiply(Arrays.asList(values));
  }

  /**
   * Pow to calculate a number raise to the power of some other number
   *
   * @param num the base. Type: Number
   * @param exp the exponent, default 2
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/pow?lang=java">Pow</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Pow(Expr num, Expr exp) {
      return Fn.apply("pow",  num, "exp", exp);
  }

  /**
   * Pow to calculate a number raise to the power of some other number
   *
   * @param num the base. Type: Number
   * @param exp the exponent, default 2
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/pow?lang=java">Pow</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Pow(Double num, Expr exp) {
    return Fn.apply("pow",  new DoubleV(num), "exp", exp);
  }

  /**
   * Pow to calculate a number raise to the power of some other number
   *
   * @param num the base. Type: Number
   * @param exp the exponent, default 2
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/pow?lang=java">Pow</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Pow(Expr num, Double exp) {
    return Fn.apply("pow",  num, "exp", new DoubleV(exp));
  }

  /**
   * Pow to calculate a number raise to the power of some other number
   *
   * @param num the base. Type: Number
   * @param exp the exponent, default 2
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/pow?lang=java">Pow</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Pow(Double num, Double exp) {
    return Fn.apply("pow",  new DoubleV(num), "exp", new DoubleV(exp));
  }

  /**
   * Pow to calculate a number raise to the power of some other number
   *
   * @param num the base. Type: Number
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/pow?lang=java">Pow</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Pow(Expr num) {
    return Fn.apply("pow",  num);
  }

  /**
   * Computes the radians of a number.
   *
   * @param value The operand to radians. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/radians?lang=java">Radians</a>
   */
  public static Expr Radians(Expr value) {
    return Fn.apply("radians", value);
  }

  /**
   * Computes the radians of a number.
   *
   * @param value The operand to radians. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/radians?lang=java">Radians</a>
   */
  public static Expr Radians(Double value) {
    return Fn.apply("radians", new DoubleV(value));
  }

  /**
   * Round to a given precision
   *
   * @param num the number to round. Type: Number
   * @param precision where to round
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/round?lang=java">Round</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Round(Expr num, Expr precision) {
    return Fn.apply("round",  num, "precision", precision);
  }

  /**
   * Round to a given precision
   *
   * @param num the number to round. Type: Number
   * @param precision where to round
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/round?lang=java">Round</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Round(Double num, Expr precision) {
    return Fn.apply("round",  new DoubleV(num), "precision", precision);
  }

  /**
   * Round to a given precision
   *
   * @param num the number to round. Type: Number
   * @param precision where to round
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/round?lang=java">Round</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Round(Long num, Expr precision) {
    return Fn.apply("round",  new LongV(num), "precision", precision);
  }

  /**
   * Round to a given precision
   *
   * @param num the number to round. Type: Number
   * @param precision where to round
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/round?lang=java">Round</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Round(Expr num, Long precision) {
    return Fn.apply("round",  num, "precision", new LongV(precision));
  }

  /**
   * Round to a given precision
   *
   * @param num the number to round. Type: Number
   * @param precision where to round
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/round?lang=java">Round</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Round(Double num, Long precision) {
    return Fn.apply("round",  new DoubleV(num), "precision", new LongV(precision));
  }

  /**
   * Round to a given precision
   *
   * @param num the number to round. Type: Number
   * @param precision where to round
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/round?lang=java">Round</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Round(Long num, Long precision) {
    return Fn.apply("round",  new LongV(num), "precision", new LongV(precision));
  }

  /**
   * Round to a given precision
   *
   * @param num the number to round to 2 decimal places. Type: Number
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/round?lang=java">Round</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Round(Expr num) {
    return Fn.apply("round",  num);
  }

  /**
   * Round to a given precision
   *
   * @param num the number to round to 2 decimal places. Type: Number
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/round?lang=java">Round</a>
   * @see #Value(long)
   * @see #Value(double)
   */
  public static Expr Round(Double num) {
    return Fn.apply("round",  new DoubleV(num));
  }

  /**
   * Computes the sign of a number.
   *
   * @param value The operand to log. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/sign?lang=java">Sign</a>
   */
  public static Expr Sign(Expr value) {
    return Fn.apply("sign", value);
  }

  /**
   * Computes the sign of a number.
   *
   * @param value The operand to log. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/sign?lang=java">Sign</a>
   */
  public static Expr Sign(Double value) {
    return Fn.apply("sign", new DoubleV(value));
  }

  /**
   * Computes the sign of a number.
   *
   * @param value The operand to log. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/sign?lang=java">Sign</a>
   */
  public static Expr Sign(Long value) {
    return Fn.apply("sign", new LongV(value));
  }

  /**
   * Computes the Sin of a number.
   *
   * @param value The operand to sin. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/sin?lang=java">Sin</a>
   */
  public static Expr Sin(Expr value) {
    return Fn.apply("sin", value);
  }

  /**
   * Computes the Sin of a number.
   *
   * @param value The operand to sin. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/sin?lang=java">Sin</a>
   */
  public static Expr Sin(Double value) {
    return Fn.apply("sin", new DoubleV(value));
  }

  /**
   * Computes the Sinh of a number.
   *
   * @param value The operand to hyperbolic sine. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/sinh?lang=java">Sinh</a>
   */
  public static Expr Sinh(Expr value) {
    return Fn.apply("sinh", value);
  }

  /**
   * Computes the Sinh of a number.
   *
   * @param value The operand to hyperbolic sine. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/sinh?lang=java">Sinh</a>
   */
  public static Expr Sinh(Double value) {
    return Fn.apply("sinh", new DoubleV(value));
  }

  /**
   * Computes the square root of a number.
   *
   * @param value The operand to log. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/sqrt?lang=java">Sqrt</a>
   */
  public static Expr Sqrt(Expr value) {
    return Fn.apply("sqrt", value);
  }

  /**
   * Computes the square root of a number.
   *
   * @param value The operand to log. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/sqrt?lang=java">Sqrt</a>
   */
  public static Expr Sqrt(Double value) {
    return Fn.apply("sqrt", new DoubleV(value));
  }

  /**
   * Computes the difference of a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/subtract?lang=java">Subtract</a>
   */
  public static Expr Subtract(List<? extends Expr> values) {
    return Fn.apply("subtract", varargs(values));
  }

  /**
   * Computes the difference of a list of numbers.
   *
   * @param values the list of numbers. Type: Array
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/subtract?lang=java">Subtract</a>
   */
  public static Expr Subtract(Expr... values) {
    return Subtract(Arrays.asList(values));
  }

  /**
   * Computes the tangent of a number.
   *
   * @param value The operand to tan. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/tan?lang=java">Tan</a>
   */
  public static Expr Tan(Expr value) {
    return Fn.apply("tan", value);
  }

  /**
   * Computes the tangent of a number.
   *
   * @param value The operand to tan. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/tan?lang=java">Tan</a>
   */
  public static Expr Tan(Double value) {
    return Fn.apply("tan", new DoubleV(value));
  }

  /**
   * Computes the hyperbolic tangent of a number.
   *
   * @param value The operand to tanh. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/tanh?lang=java">Tanh</a>
   */
  public static Expr Tanh(Expr value) {
    return Fn.apply("tanh", value);
  }

  /**
   * Computes the hyperbolic tangent of a number.
   *
   * @param value The operand to tanh. Type: Number
   * @return a {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/tanh?lang=java">Tanh</a>
   */
  public static Expr Tanh(Double value) {
    return Fn.apply("tanh", new DoubleV(value));
  }

  /**
   * Truncate to a given precision
   *
   * @param num the number to truncate. Type: Number
   * @param precision where to truncate, default 2
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/trunc?lang=java">Trunc</a>
   * @see #Value(long)
   */
  public static Expr Trunc(Expr num, Expr precision) {
    return Fn.apply("trunc",  num, "precision", precision);
  }

  /**
   * Truncate to a given precision
   *
   * @param num the number to truncate. Type: Number
   * @param precision where to truncate, default 2
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/trunc?lang=java">Trunc</a>
   * @see #Value(long)
   */
  public static Expr Trunc(Double num, Expr precision) {
    return Fn.apply("trunc",  new DoubleV(num), "precision", precision);
  }
  /**
   * Truncate to a given precision
   *
   * @param num the number to truncate. Type: Number
   * @param precision where to truncate, default 2
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/trunc?lang=java">Trunc</a>
   * @see #Value(long)
   */
  public static Expr Trunc(Long num, Expr precision) {
    return Fn.apply("trunc",  new LongV(num), "precision", precision);
  }

  /**
   * Truncate to a given precision
   *
   * @param num the number to truncate. Type: Number
   * @param precision where to truncate, default 2
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/trunc?lang=java">Trunc</a>
   * @see #Value(long)
   */
  public static Expr Trunc(Expr num, Long precision) {
    return Fn.apply("trunc",  num, "precision", new LongV(precision));
  }

  /**
   * Truncate to a given precision
   *
   * @param num the number to truncate. Type: Number
   * @param precision where to truncate, default 2
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/trunc?lang=java">Trunc</a>
   * @see #Value(long)
   */
  public static Expr Trunc(Double num, Long precision) {
    return Fn.apply("trunc",  new DoubleV(num), "precision", new LongV(precision));
  }
  /**
   * Truncate to a given precision
   *
   * @param num the number to truncate. Type: Number
   * @param precision where to truncate, default 2
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/trunc?lang=java">Trunc</a>
   * @see #Value(long)
   */
  public static Expr Trunc(Long num, Long precision) {
    return Fn.apply("trunc",  new LongV(num), "precision", new LongV(precision));
  }

  /**
   * Truncate to a given precision
   *
   * @param num the number to truncate to 2 decimal places. Type: Number
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/trunc?lang=java">Trunc</a>
   * @see #Value(long)
   */
  public static Expr Trunc(Expr num) {
    return Fn.apply("trunc",  num);
  }

  /**
   * Truncate to a given precision
   *
   * @param num the number to truncate to 2 decimal places. Type: Number
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/trunc?lang=java">Trunc</a>
   * @see #Value(long)
   */
  public static Expr Trunc(Double num) {
    return Fn.apply("trunc",  new DoubleV(num));
  }

  /**
   *
   * Count the number of elements in the collection.
   *
   * @param collection the collection
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/count?lang=java">Count</a>
   */
  public static Expr Count(Expr collection) {
    return Fn.apply("count", collection);
  }

  /**
   *
   * Sum the elements in the collection.
   *
   * @param collection the collection
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/sum?lang=java">Sum</a>
   */
  public static Expr Sum(Expr collection) {
    return Fn.apply("sum", collection);
  }

  /**
   *
   * Returns the mean of all elements in the collection.
   *
   * @param collection the collection
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/mean?lang=java">Mean</a>
   */
  public static Expr Mean(Expr collection) {
    return Fn.apply("mean", collection);
  }

  /**
   *
   * Evaluates to true if all elements of the collection is true.
   *
   * @param collection the collection
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/all?lang=java">All</a>
   */
  public static Expr All(Expr collection) {
    return Fn.apply("all", collection);
  }

  /**
   *
   * Evaluates to true if any element of the collection is true.
   *
   * @param collection the collection
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/any?lang=java">Any</a>
   */
  public static Expr Any(Expr collection) {
    return Fn.apply("any", collection);
  }

  /**
   * Returns true if the first element of the given collection is less than the ones following, and false otherwise.
   *
   * @param values the list of numbers to compare. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/lt?lang=java">LT</a>
   */
  public static Expr LT(List<? extends Expr> values) {
    return Fn.apply("lt", varargs(values));
  }

  /**
   * Returns true if the first element of the given collection is less than the ones following, and false otherwise.
   *
   * @param values the list of numbers to compare. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/lt?lang=java">LT</a>
   */
  public static Expr LT(Expr... values) {
    return LT(Collections.unmodifiableList(Arrays.asList(values)));
  }

  /**
   * Returns true if the first element of the given collection is less than or equal to the ones following,
   * and false otherwise.
   *
   * @param values the list of numbers to compare. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/lte?lang=java">LTE</a>
   */
  public static Expr LTE(List<? extends Expr> values) {
    return Fn.apply("lte", varargs(values));
  }

  /**
   * Returns true if the first element of the given collection is less than or equal to the ones following,
   * and false otherwise.
   *
   * @param values the list of numbers to compare. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/lte?lang=java">LTE</a>
   */
  public static Expr LTE(Expr... values) {
    return LTE(Collections.unmodifiableList(Arrays.asList(values)));
  }

  /**
   * Returns true if the first element of the given collection is greater than the ones following, and false otherwise.
   *
   * @param values the list of numbers to compare. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/gt?lang=java">GT</a>
   */
  public static Expr GT(List<? extends Expr> values) {
    return Fn.apply("gt", varargs(values));
  }

  /**
   * Returns true if the first element of the given collection is greater than the ones following, and false otherwise.
   *
   * @param values the list of numbers to compare. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/gt?lang=java">GT</a>
   */
  public static Expr GT(Expr... values) {
    return GT(Collections.unmodifiableList(Arrays.asList(values)));
  }

  /**
   * Returns true if the first element of the given collection is greater than or equal to the ones following,
   * and false otherwise.
   *
   * @param values the list of numbers to compare. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/gte?lang=java">GTE</a>
   */
  public static Expr GTE(List<? extends Expr> values) {
    return Fn.apply("gte", varargs(values));
  }

  /**
   * Returns true if the first element of the given collection is greater than or equal to the ones following,
   * and false otherwise.
   *
   * @param values the list of numbers to compare. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/gte?lang=java">GTE</a>
   */
  public static Expr GTE(Expr... values) {
    return GTE(Collections.unmodifiableList(Arrays.asList(values)));
  }

  /**
   * Returns true if all elements in the given collection are true, and false otherwise.
   *
   * @param values a collection of boolean values. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/and?lang=java">And</a>
   */
  public static Expr And(List<? extends Expr> values) {
    return Fn.apply("and", varargs(values));
  }

  /**
   * Returns true if all elements in the given collection are true, and false otherwise.
   *
   * @param values a collection of boolean values. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/and?lang=java">And</a>
   */
  public static Expr And(Expr... values) {
    return And(Collections.unmodifiableList(Arrays.asList(values)));
  }

  /**
   * Returns true if any element in the given collection is true, and false otherwise.
   *
   * @param values a collection of boolean values. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/or?lang=java">Or</a>
   */
  public static Expr Or(List<? extends Expr> values) {
    return Fn.apply("or", varargs(values));
  }

  /**
   * Returns true if any element in the given collection is true, and false otherwise.
   *
   * @param values a collection of boolean values. Type: Array
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/or?lang=java">Or</a>
   */
  public static Expr Or(Expr... values) {
    return Or(Collections.unmodifiableList(Arrays.asList(values)));
  }

  /**
   * Returns true if the given boolean parameter is false, or false if the boolean parameter is true.
   *
   * @param bool a boolean value. Type: Boolean
   * @return a new {@link Expr} instance
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/not?lang=java">Not</a>
   */
  public static Expr Not(Expr bool) {
    return Fn.apply("not", bool);
  }

  /**
   * Casts an expression to a string value, if possible.
   *
   * @param value an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/tostring?lang=java">ToString</a>
   */
  public static Expr ToString(Expr value) {
    return Fn.apply("to_string", value);
  }

  /**
   * Casts an expression to a numeric value, if possible.
   *
   * @param value an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/tonumber?lang=java">ToNumber</a>
   */
  public static Expr ToNumber(Expr value) {
    return Fn.apply("to_number", value);
  }

  /**
   * Casts an expression to a double value, if possible.
   *
   * @param value an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/todouble?lang=java">ToDouble</a>
   */
  public static Expr ToDouble(Expr value) {
    return Fn.apply("to_double", value);
  }

  /**
   * Casts an expression to a double value, if possible.
   *
   * @param value a string
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/todouble?lang=java">ToDouble</a>
   */
  public static Expr ToDouble(String value) {
    return ToDouble(Value(value));
  }

  /**
   * Casts an expression to a double value, if possible.
   *
   * @param value a double value.
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/todouble?lang=java">ToDouble</a>
   */
  public static Expr ToDouble(double value) {
    return ToDouble(Value(value));
  }

  /**
   * Casts an expression to a double value, if possible.
   *
   * @param value a long value
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/todouble?lang=java">ToDouble</a>
   */
  public static Expr ToDouble(long value) {
    return ToDouble(Value(value));
  }

  /**
   * Casts an expression to an integer value, if possible.
   *
   * @param value an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/tointeger?lang=java">ToInteger</a>
   */
  public static Expr ToInteger(Expr value) {
    return Fn.apply("to_integer", value);
  }

  /**
   * Casts an expression to an integer value, if possible.
   *
   * @param value a string.
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/tointeger?lang=java">ToInteger</a>
   */
  public static Expr ToInteger(String value) {
    return ToInteger(Value(value));
  }

  /**
   * Casts an expression to an integer value, if possible.
   *
   * @param value a double value
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/tointeger?lang=java">ToInteger</a>
   */
  public static Expr ToInteger(double value) {
    return ToInteger(Value(value));
  }

  /**
   * Casts an expression to an integer value, if possible.
   *
   * @param value a long value
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/tointeger?lang=java">ToInteger</a>
   */
  public static Expr ToInteger(long value) {
    return ToInteger(Value(value));
  }

  /**
   * Casts an expression to a time value, if possible.
   *
   * @param value an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/totime?lang=java">ToTime</a>
   */
  public static Expr ToTime(Expr value) {
    return Fn.apply("to_time", value);
  }

  /**
   * Converts a time expression to seconds since the UNIX epoch.
   *
   * @param value an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/toseconds?lang=java">ToSeconds</a>
   */
  public static Expr ToSeconds(Expr value) {
    return Fn.apply("to_seconds", value);
  }

  /**
   * Converts a time expression to milliseconds since the UNIX epoch.
   *
   * @param value an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/tomillis?lang=java">ToMillis</a>
   */
  public static Expr ToMillis(Expr value) {
    return Fn.apply("to_millis", value);
  }

  /**
   * Converts a time expression to microseconds since the UNIX epoch.
   *
   * @param value an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/tomicros?lang=java">ToMicros</a>
   */
  public static Expr ToMicros(Expr value) {
    return Fn.apply("to_micros", value);
  }

  /**
   * Returns a time expression's day of the month, from 1 to 31.
   *
   * @param expr an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/dayofmonth?lang=java">DayOfMonth</a>
   */
  public static Expr DayOfMonth(Expr expr) {
    return Fn.apply("day_of_month", expr);
  }

  /**
   * Returns a time expression's day of the week following ISO-8601 convention,
   * from 1 (Monday) to 7 (Sunday).
   *
   * @param expr an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/dayofweek?lang=java">DayOfWeek</a>
   */
  public static Expr DayOfWeek(Expr expr) {
    return Fn.apply("day_of_week", expr);
  }

  /**
   * Returns a time expression's day of the year, from 1 to 365, or 366 in a leap
   * year.
   *
   * @param expr an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/dayofyear?lang=java">DayOfYear</a>
   */
  public static Expr DayOfYear(Expr expr) {
    return Fn.apply("day_of_year", expr);
  }

  /**
   * Returns the time expression's year, following the ISO-8601 standard.
   *
   * @param expr an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/year?lang=java">Year</a>
   */
  public static Expr Year(Expr expr) {
    return Fn.apply("year", expr);
  }

  /**
   * Returns a time expression's month of the year, from 1 to 12.
   *
   * @param expr an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/month?lang=java">Month</a>
   */
  public static Expr Month(Expr expr) {
    return Fn.apply("month", expr);
  }

  /**
   * Returns a time expression's hour of the day, from 0 to 23.
   *
   * @param expr an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/hour?lang=java">Hour</a>
   */
  public static Expr Hour(Expr expr) {
    return Fn.apply("hour", expr);
  }

  /**
   * Returns a time expression's minute of the hour, from 0 to 59.
   *
   * @param expr an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/minute?lang=java">Minute</a>
   */
  public static Expr Minute(Expr expr) {
    return Fn.apply("minute", expr);
  }

  /**
   * Returns a time expression's second of the minute, from 0 to 59.
   *
   * @param expr an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/second?lang=java">Second</a>
   */
  public static Expr Second(Expr expr) {
    return Fn.apply("second", expr);
  }

  /**
   * Casts an expression to a date value, if possible.
   *
   * @param value an expression. Type: Any
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/todate?lang=java">ToDate</a>
   */
  public static Expr ToDate(Expr value) {
    return Fn.apply("to_date", value);
  }

  /**
   * Merge two or more objects.
   *
   * @param merge the first object. Type: Object
   * @param with the second object or a list of objects: Type: Object or Array of Objects
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/merge?lang=java">Merge</a>
   */
  public static Expr Merge(Expr merge, Expr with) {
    return Fn.apply("merge", merge, "with", with);
  }

  /**
   * Merge two or more objects.
   *
   * @param merge the first object. Type: Object
   * @param with the second object or a list of objects: Type: Object or Array of Objects
   * @param lambda a lambda to resolve possible conflicts: Type: A lambda function
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/merge?lang=java">Merge</a>
   */
  public static Expr Merge(Expr merge, Expr with, Expr lambda) {
    return Fn.apply("merge", merge, "with", with, "lambda", lambda);
  }

  /**
   * Try to convert an array of (field, value) into an object.
   *
   * @param fields an expr that result into an array of (field, value)
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/toobject?lang=java">ToObject</a>
   */
  public static Expr ToObject(Expr fields) {
    return Fn.apply("to_object", fields);
  }

  /**
   * Try to convert an object into an array of (field, value).
   *
   * @param object the object to convert.
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/toarray?lang=java">ToArray</a>
   */
  public static Expr ToArray(Expr object) {
    return Fn.apply("to_array", object);
  }

  /**
   * Check if the expression is a number.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isnumber?lang=java">IsNumber</a>
   */
  public static Expr IsNumber(Expr expr) {
    return Fn.apply("is_number", expr);
  }

  /**
   * Check if the expression is a double.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isdouble?lang=java">IsDouble</a>
   */
  public static Expr IsDouble(Expr expr) {
    return Fn.apply("is_double", expr);
  }

  /**
   * Check if the expression is an integer.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isinteger?lang=java">IsInteger</a>
   */
  public static Expr IsInteger(Expr expr) {
    return Fn.apply("is_integer", expr);
  }

  /**
   * Check if the expression is a boolean.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isboolean?lang=java">IsBoolean</a>
   */
  public static Expr IsBoolean(Expr expr) {
    return Fn.apply("is_boolean", expr);
  }

  /**
   * Check if the expression is null.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isnull?lang=java">IsNull</a>
   */
  public static Expr IsNull(Expr expr) {
    return Fn.apply("is_null", expr);
  }

  /**
   * Check if the expression is a byte array.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isbytes?lang=java">IsBytes</a>
   */
  public static Expr IsBytes(Expr expr) {
    return Fn.apply("is_bytes", expr);
  }

  /**
   * Check if the expression is a timestamp.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/istimestamp?lang=java">IsTimestamp</a>
   */
  public static Expr IsTimestamp(Expr expr) {
    return Fn.apply("is_timestamp", expr);
  }

  /**
   * Check if the expression is a date.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isdate?lang=java">IsDate</a>
   */
  public static Expr IsDate(Expr expr) {
    return Fn.apply("is_date", expr);
  }

  /**
   * Check if the expression is a string.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isstring?lang=java">IsString</a>
   */
  public static Expr IsString(Expr expr) {
    return Fn.apply("is_string", expr);
  }

  /**
   * Check if the expression is an array.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isarray?lang=java">IsArray</a>
   */
  public static Expr IsArray(Expr expr) {
    return Fn.apply("is_array", expr);
  }

  /**
   * Check if the expression is an object.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isobject?lang=java">IsObject</a>
   */
  public static Expr IsObject(Expr expr) {
    return Fn.apply("is_object", expr);
  }

  /**
   * Check if the expression is a reference.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isref?lang=java">IsRef</a>
   */
  public static Expr IsRef(Expr expr) {
    return Fn.apply("is_ref", expr);
  }

  /**
   * Check if the expression is a set.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isset?lang=java">IsSet</a>
   */
  public static Expr IsSet(Expr expr) {
    return Fn.apply("is_set", expr);
  }

  /**
   * Check if the expression is a document (either a reference or an instance).
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isdoc?lang=java">IsDoc</a>
   */
  public static Expr IsDoc(Expr expr) {
    return Fn.apply("is_doc", expr);
  }

  /**
   * Check if the expression is a lambda.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/islambda?lang=java">IsLambda</a>
   */
  public static Expr IsLambda(Expr expr) {
    return Fn.apply("is_lambda", expr);
  }

  /**
   * Check if the expression is a collection.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/iscollection?lang=java">IsCollection</a>
   */
  public static Expr IsCollection(Expr expr) {
    return Fn.apply("is_collection", expr);
  }

  /**
   * Check if the expression is a database.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isdatabase?lang=java">IsDatabase</a>
   */
  public static Expr IsDatabase(Expr expr) {
    return Fn.apply("is_database", expr);
  }

  /**
   * Check if the expression is an index.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isindex?lang=java">IsIndex</a>
   */
  public static Expr IsIndex(Expr expr) {
    return Fn.apply("is_index", expr);
  }

  /**
   * Check if the expression is a function.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isfunction?lang=java">IsFunction</a>
   */
  public static Expr IsFunction(Expr expr) {
    return Fn.apply("is_function", expr);
  }

  /**
   * Check if the expression is a key.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/iskey?lang=java">IsKey</a>
   */
  public static Expr IsKey(Expr expr) {
    return Fn.apply("is_key", expr);
  }

  /**
   * Check if the expression is a token.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/istoken?lang=java">IsToken</a>
   */
  public static Expr IsToken(Expr expr) {
    return Fn.apply("is_token", expr);
  }

  /**
   * Check if the expression is a credentials.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/iscredentials?lang=java">IsCredentials</a>
   */
  public static Expr IsCredentials(Expr expr) {
    return Fn.apply("is_credentials", expr);
  }

  /**
   * Check if the expression is a role.
   *
   * @param expr the expression to check
   * @return a new {@link Expr}
   * @see <a href="https://docs.fauna.com/fauna/current/api/fql/functions/isrole?lang=java">IsRole</a>
   */
  public static Expr IsRole(Expr expr) {
    return Fn.apply("is_role", expr);
  }
}
