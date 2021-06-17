package faunadb

import com.fasterxml.jackson.annotation.JsonValue
import faunadb.values._
import scala.annotation.meta.getter
import scala.language.experimental.macros
import scala.language.implicitConversions

/**
  * Functions modeling the FaunaDB Query language.
  *
  * Documents of these Collections can be composed to model a query expression, which can then be passed to
  * [[FaunaClient!.query(expr:faunadb\.query\.Expr)*]] in order to execute the query.
  *
  * ===Examples===
  *
  * {{{
  * val query = Create(Collection("spells"), Obj("data" -> Obj("name" -> "Magic Missile")))
  * }}}
  *
  * {{{
  * val query = Map(Paginate(Match(Index("spells_by_name"), "Magic Missile")), Lambda(r => Get(r)))
  * }}}
  */
package query {

  /**
    * A query language expression.
    */
  case class Expr private[query] (@(JsonValue @getter) value: Value) extends AnyVal

  object Expr {
    implicit def encode[T: Encoder](obj: T): Expr = new Expr(wrapValue(obj))

    private def wrapValue(value: Value): Value = value match {
      case ObjectV(fields) => ObjectV("object" -> ObjectV(fields.map { case (k, v) => (k, wrapValue(v)) }))
      case ArrayV(values) => ArrayV(values.map(wrapValue))
      case _ => value
    }

    private[query] def apply(value: Value): Expr = new Expr(value)
  }

  /**
    * Enumeration for time units. Used by [[https://docs.fauna.com/fauna/current/api/fql/cheat_sheet#timedate]].
    */
  sealed abstract class TimeUnit(val expr: Expr)
  object TimeUnit {
    case object Day extends TimeUnit("day")
    case object HalfDay extends TimeUnit("half day")
    case object Hour extends TimeUnit("hour")
    case object Minute extends TimeUnit("minute")
    case object Second extends TimeUnit("second")
    case object Millisecond extends TimeUnit("millisecond")
    case object Microsecond extends TimeUnit("microsecond")
    case object Nanosecond extends TimeUnit("nanosecond")
  }

  /**
    * Enumeration for event action types.
    */
  sealed abstract class Action(val expr: Expr)
  object Action {
    case object Create extends Action("create")
    case object Delete extends Action("delete")
  }

  /**
    * Enumeration for casefold operation.
    */
  sealed abstract class Normalizer(val expr: Expr)
  object Normalizer {
    case object NFD extends Normalizer("NFD")
    case object NFC extends Normalizer("NFC")
    case object NFKD extends Normalizer("NFKD")
    case object NFKC extends Normalizer("NFKC")
    case object NFKCCaseFold extends Normalizer("NFKCCaseFold")
  }

  /**
    * Helper for path syntax
    */
  case class Path private (segments: Expr*) extends AnyVal {
    def /(sub: Path): Path = Path(segments ++ sub.segments: _*)
  }

  /**
    * Helper for pagination cursors
    */
  sealed trait Cursor
  object Cursor {
    implicit def exprToCursor(expr: Expr): Cursor =
      RawCursor(expr)
  }
  case class Before(expr: Expr) extends Cursor
  case class After(expr: Expr) extends Cursor
  case class RawCursor(expr: Expr) extends Cursor
  case object NoCursor extends Cursor
}

package object query {

  // implicit conversions

  implicit def strToPath(str: String): Path = Path(Expr(StringV(str)))
  implicit def intToPath(int: Int): Path = Path(Expr(LongV(int.toLong)))
  implicit def pathToExpr(path: Path): Expr = Expr(varargs(path.segments))

  // Helpers

  private def varargs(exprs: Seq[Expr]) =
    exprs match {
      case Seq(e) => e.value
      case es     => ArrayV(es map (_.value): _*)
    }

  private def unwrap(exprs: Seq[Expr]) =
    exprs map { _.value }

  private def unwrapPairs(exprs: Seq[(String, Expr)]) =
    exprs map { t => (t._1, t._2.value) }

  // Values

  /**
    * Creates a RefV value. The string "collections/widget/123" will be equivalent to:
    * {{{
    * RefV("123", RefV("widget", Native.Collections))
    * }}}
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/types?lang=scala#ref]]
    */
  def Ref(value: String): Expr =
    Expr(ObjectV("@ref" -> StringV(value)))

  def Ref(collectionRef: Expr, id: Expr): Expr =
    Expr(ObjectV("ref" -> collectionRef.value, "id" -> id.value))

  /** Native reference to all classes */
  @deprecated("use Collections instead", "2.7.0")
  def Classes(scope: Expr = NullV): Expr =
    Expr(ObjectV("classes" -> scope.value))

    /** Native reference to all classes */
  def Collections(scope: Expr = NullV): Expr =
    Expr(ObjectV("collections" -> scope.value))

  /** Native reference to all databases */
  def Databases(scope: Expr = NullV): Expr =
    Expr(ObjectV("databases" -> scope.value))

  /** Native reference to all indexes */
  def Indexes(scope: Expr = NullV): Expr =
    Expr(ObjectV("indexes" -> scope.value))

  /** Native reference to all functions */
  def Functions(scope: Expr = NullV): Expr =
    Expr(ObjectV("functions" -> scope.value))

  /** Native reference to all roles */
  def Roles(scope: Expr = NullV): Expr =
    Expr(ObjectV("roles" -> scope.value))

  /** Native reference to all keys */
  def Keys(scope: Expr = NullV): Expr =
    Expr(ObjectV("keys" -> scope.value))

  /** Native reference to all tokens */
  def Tokens(scope: Expr = NullV): Expr =
    Expr(ObjectV("tokens" -> scope.value))

  /** Native reference to all credentials */
  def Credentials(scope: Expr = NullV): Expr =
    Expr(ObjectV("credentials" -> scope.value))

  /** Returns a set of all documents in the given collection */
  def Documents(collection: Expr): Expr =
    Expr(ObjectV("documents" -> collection.value))

  /**
    * An Array value.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/types?lang=scala#array]]
    */
  def Arr(elems: Expr*): Expr =
    Expr(ArrayV(unwrap(elems): _*))

  /**
    * An Object value.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/types?lang=scala#object]]
    */
  def Obj(pairs: (String, Expr)*): Expr =
    Expr(ObjectV("object" -> ObjectV(unwrapPairs(pairs): _*)))

  private val NullExpr = Expr(NullV)

  /**
    * A Null value.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/types?lang=scala#null]]
    */
  def Null(): Expr =
    NullExpr

  // Basic Forms

  /**
    * A Abort expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/abort?lang=scala]]
    */
  def Abort(msg: Expr): Expr =
    Expr(ObjectV("abort" -> msg.value))

  /**
    * A Call expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/call?lang=scala]]
    */
  def Call(ref: Expr, arguments: Expr*): Expr =
    Expr(ObjectV("call" -> ref.value, "arguments" -> varargs(arguments)))

  /**
    * A Query expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/query?lang=scala]]
    */
  def Query(fn: Expr => Expr): Expr = macro QueryMacros.query
  def Query(fn: (Expr, Expr) => Expr): Expr = macro QueryMacros.query
  def Query(fn: (Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.query
  def Query(fn: (Expr, Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.query
  def Query(fn: (Expr, Expr, Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.query
  def Query(fn: (Expr, Expr, Expr, Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.query
  def Query(fn: (Expr, Expr, Expr, Expr, Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.query
  def Query(fn: (Expr, Expr, Expr, Expr, Expr, Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.query
  def Query(fn: (Expr, Expr, Expr, Expr, Expr, Expr, Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.query
  def Query(fn: (Expr, Expr, Expr, Expr, Expr, Expr, Expr, Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.query

  def Query(lambda: Expr): Expr =
    Expr(ObjectV("query" -> lambda.value))

  /**
    * A At expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/at?lang=scala]]
    */
  def At(timestamp: Expr, expr: Expr): Expr =
    Expr(ObjectV("at" -> timestamp.value, "expr" -> expr.value))

  /**
    * A Let expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/let?lang=scala]]
    */

  def Let(block: => Any): Expr = macro QueryMacros.let

  def Let(bindings: Seq[(String, Expr)], in: Expr): Expr =
    Expr(ObjectV("let" -> ArrayV(unwrapPairs(bindings) map { ObjectV(_) }: _*), "in" -> in.value))

  /**
    * A Var expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/var?lang=scala]]
    */
  def Var(name: String): Expr =
    Expr(ObjectV("var" -> StringV(name)))

  /**
   * An If expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/if?lang=scala]]
   */
  def If(pred: Expr, `then`: Expr, `else`: Expr): Expr =
    Expr(ObjectV("if" -> pred.value, "then" -> `then`.value, "else" -> `else`.value))

  /**
   * A Do expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/do?lang=scala]]
   */
  def Do(exprs: Expr*): Expr =
    Expr(ObjectV("do" -> ArrayV(unwrap(exprs): _*)))

  /**
   * A Lambda expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/lambda?lang=scala]]
   */
  def Lambda(fn: Expr => Expr): Expr = macro QueryMacros.lambda
  def Lambda(fn: (Expr, Expr) => Expr): Expr = macro QueryMacros.lambda
  def Lambda(fn: (Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.lambda
  def Lambda(fn: (Expr, Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.lambda
  def Lambda(fn: (Expr, Expr, Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.lambda
  def Lambda(fn: (Expr, Expr, Expr, Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.lambda
  def Lambda(fn: (Expr, Expr, Expr, Expr, Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.lambda
  def Lambda(fn: (Expr, Expr, Expr, Expr, Expr, Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.lambda
  def Lambda(fn: (Expr, Expr, Expr, Expr, Expr, Expr, Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.lambda
  def Lambda(fn: (Expr, Expr, Expr, Expr, Expr, Expr, Expr, Expr, Expr, Expr) => Expr): Expr = macro QueryMacros.lambda

  def Lambda(lambda: Expr, expr: Expr): Expr =
    Expr(ObjectV("lambda" -> lambda.value, "expr" -> expr.value))

  // Collection Functions

  /**
   * A Map expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/map?lang=scala]]
   */
  def Map(collection: Expr, lambda: Expr): Expr =
    Expr(ObjectV("map" -> lambda.value, "collection" -> collection.value))

  /**
   * A Foreach expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/foreach?lang=scala]]
   */
  def Foreach(collection: Expr, lambda: Expr): Expr =
    Expr(ObjectV("foreach" -> lambda.value, "collection" -> collection.value))

  /**
    * A Filter expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/filter?lang=scala]]
    */
  def Filter(collection: Expr, lambda: Expr): Expr =
    Expr(ObjectV("filter" -> lambda.value, "collection" -> collection.value))

  /**
    * A Prepend expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/prepend?lang=scala]]
    */
  def Prepend(elems: Expr, collection: Expr): Expr =
    Expr(ObjectV("prepend" -> elems.value, "collection" -> collection.value))

  /**
    * An Append expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/append?lang=scala]]
    */
  def Append(elems: Expr, collection: Expr): Expr =
    Expr(ObjectV("append" -> elems.value, "collection" -> collection.value))

  /**
    * A Take expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/take?lang=scala]]
    */
  def Take(num: Expr, collection: Expr): Expr =
    Expr(ObjectV("take" -> num.value, "collection" -> collection.value))

  /**
    * A Drop expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/drop?lang=scala]]
    */
  def Drop(num: Expr, collection: Expr): Expr =
    Expr(ObjectV("drop" -> num.value, "collection" -> collection.value))

  /**
    * A IsEmpty expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isempty?lang=scala]]
    */
  def IsEmpty(collection: Expr): Expr =
    Expr(ObjectV("is_empty" -> collection.value))

  /**
    * A IsNonEmpty expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isnonempty?lang=scala]]
    */
  def IsNonEmpty(collection: Expr): Expr =
    Expr(ObjectV("is_nonempty" -> collection.value))

  // Read Functions

  /**
   * A Get expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/get?lang=scala]]
   */
  def Get(resource: Expr): Expr =
    Expr(ObjectV("get" -> resource.value))

  def Get(resource: Expr, ts: Expr): Expr =
    Expr(ObjectV("get" -> resource.value, "ts" -> ts.value))

  /**
    * A KeyFromSecret expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/keyfromsecret?lang=scala]]
    */
  def KeyFromSecret(secret: Expr): Expr =
    Expr(ObjectV("key_from_secret" -> secret.value))

  /**
    * A Reduce expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/reduce?lang=scala]]
    */
  def Reduce(lambda: Expr, initial: Expr, collection: Expr): Expr =
    Expr(ObjectV("reduce" -> lambda.value, "initial" -> initial.value, "collection" -> collection.value))

  /**
    * A Reverse expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/reverse?lang=scala]]
    */
  def Reverse(source: Expr): Expr =
    Expr(ObjectV("reverse" -> source.value))

  /**
   * A Paginate expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/paginate?lang=scala]]
   */
  def Paginate(
    resource: Expr,
    cursor: Cursor = NoCursor,
    ts: Expr = Null(),
    size: Expr = Null(),
    sources: Expr = Null(),
    events: Expr = Null()): Expr = {

    val call = List.newBuilder[(String, Value)]
    call += "paginate" -> resource.value

    cursor match {
      case b: Before    => call += "before" -> b.expr.value
      case a: After     => call += "after" -> a.expr.value
      case r: RawCursor => call += "cursor" -> r.expr.value
      case NoCursor     => ()
    }

    if (ts != Null()) call += "ts" -> ts.value
    if (size != Null()) call += "size" -> size.value
    if (events != Null()) call += "events" -> events.value
    if (sources != Null()) call += "sources" -> sources.value

    Expr(ObjectV(call.result: _*))
  }

  /**
   * An Exists expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/exists?lang=scala]]
   */
  def Exists(ref: Expr): Expr =
    Expr(ObjectV("exists" -> ref.value))

  def Exists(ref: Expr, ts: Expr): Expr =
    Expr(ObjectV("exists" -> ref.value, "ts" -> ts.value))

  // Write Functions

  /**
   * A Create expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/create?lang=scala]]
   */
  def Create(ref: Expr, params: Expr): Expr =
    Expr(ObjectV("create" -> ref.value, "params" -> params.value))

  /**
   * An Update expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/update?lang=scala]]
   */
  def Update(ref: Expr, params: Expr): Expr =
    Expr(ObjectV("update" -> ref.value, "params" -> params.value))

  /**
   * A Replace expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/replace?lang=scala]]
   */
  def Replace(ref: Expr, params: Expr): Expr =
    Expr(ObjectV("replace" -> ref.value, "params" -> params.value))

  /**
   * A Delete expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/delete?lang=scala]]
   */
  def Delete(ref: Expr): Expr =
    Expr(ObjectV("delete" -> ref.value))

  /**
    * An Insert expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/insert?lang=scala]]
    */
  def Insert(ref: Expr, ts: Expr, action: Action, params: Expr): Expr =
    Insert(ref, ts, action.expr, params)

  def Insert(ref: Expr, ts: Expr, action: Expr, params: Expr): Expr =
    Expr(ObjectV("insert" -> ref.value, "ts" -> ts.value, "action" -> action.value, "params" -> params.value))

  /**
    * A Remove expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/remove?lang=scala]]
    */
  def Remove(ref: Expr, ts: Expr, action: Action): Expr =
    Remove(ref, ts, action.expr)

  def Remove(ref: Expr, ts: Expr, action: Expr): Expr =
    Expr(ObjectV("remove" -> ref.value, "ts" -> ts.value, "action" -> action.value))

  /**
    * A Create Class expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/createclass?lang=scala]]
    */
  @deprecated("use CreateCollection instead", "2.7.0")
  def CreateClass(params: Expr): Expr =
    Expr(ObjectV("create_class" -> params.value))

  /**
    * A Create Collection expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/createcollection?lang=scala]]
    */
  def CreateCollection(params: Expr): Expr =
    Expr(ObjectV("create_collection" -> params.value))

  /**
    * A Create Database expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/createdatabase?lang=scala]]
    */
  def CreateDatabase(params: Expr): Expr =
    Expr(ObjectV("create_database" -> params.value))

  /**
    * A Create Key expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/createkey?lang=scala]]
    */
  def CreateKey(params: Expr): Expr =
    Expr(ObjectV("create_key" -> params.value))

  /**
    * A Create Index expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/createindex?lang=scala]]
    */
  def CreateIndex(params: Expr): Expr =
    Expr(ObjectV("create_index" -> params.value))

  /**
    * A Create Function expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/createfunction?lang=scala]]
    */
  def CreateFunction(params: Expr): Expr =
    Expr(ObjectV("create_function" -> params.value))

  /**
    * A Create Role expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/createrole?lang=scala]]
    */
  def CreateRole(params: Expr): Expr =
    Expr(ObjectV("create_role" -> params.value))

  /**
    * A Move Database expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/movedatabase?lang=scala]]
    */
  def MoveDatabase(from: Expr, to: Expr): Expr =
    Expr(ObjectV("move_database" -> from.value, "to" -> to.value))

  // Set Constructors

  /**
    * A Singleton set.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/singleton?lang=scala]]
    */
  def Singleton(ref: Expr): Expr =
    Expr(ObjectV("singleton" -> ref.value))

  /**
    * A Events set.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/events?lang=scala]]
    */
  def Events(refSet: Expr): Expr =
    Expr(ObjectV("events" -> refSet.value))

  /**
   * A Match set.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/match?lang=scala]]
   */
  def Match(index: Expr, terms: Expr*): Expr =
    Expr(ObjectV("match" -> varargs(terms), "index" -> index.value))

  /**
   * A Union set.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/union?lang=scala]]
   */
  def Union(sets: Expr*): Expr =
    Expr(ObjectV("union" -> varargs(sets)))

  /**
   * An Intersection set.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/intersection?lang=scala]]
   */
  def Intersection(sets: Expr*): Expr =
    Expr(ObjectV("intersection" -> varargs(sets)))

  /**
   * A Difference set.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/difference?lang=scala]]
   */
  def Difference(sets: Expr*): Expr =
    Expr(ObjectV("difference" -> varargs(sets)))

  /**
   * A Distinct set.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/distinct?lang=scala]]
   */
  def Distinct(set: Expr): Expr =
    Expr(ObjectV("distinct" -> set.value))

  /**
   * A Join set.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/join?lang=scala]]
   */
  def Join(source: Expr, `with`: Expr): Expr =
    Expr(ObjectV("join" -> source.value, "with" -> `with`.value))

  /**
   * Filter the set based on the lower/upper bounds (inclusive).
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/range?lang=scala]]
   *
   * @param set set to be filtered
   * @param from lower bound
   * @param to upper bound
   */
  def Range(set: Expr, from: Expr, to: Expr): Expr =
    Expr(ObjectV("range" -> set.value, "from" -> from.value, "to" -> to.value))

  // Authentication Functions

  /**
    * A Login expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/login?lang=scala]]
    */
  def Login(ref: Expr, params: Expr): Expr =
    Expr(ObjectV("login" -> ref.value, "params" -> params.value))

  /**
    * A Logout expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/logout?lang=scala]]
    */
  def Logout(invalidateAll: Expr): Expr =
    Expr(ObjectV("logout" -> invalidateAll.value))

  /**
    * An Identify expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/identify?lang=scala]]
    */
  def Identify(ref: Expr, password: Expr): Expr =
    Expr(ObjectV("identify" -> ref.value, "password" -> password.value))

  /**
    * An Identity expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/identity?lang=scala]]
    */
  @deprecated("use CurrentIdentity instead", "4.0.0")
  def Identity(): Expr =
    Expr(ObjectV("identity" -> NullV))

  /**
    * A CurrentIdentity expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/currentidentity?lang=scala]]
    */
  def CurrentIdentity(): Expr =
    Expr(ObjectV("current_identity" -> NullV))

  /**
    * A HasIdentity expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/hasidentity?lang=scala]]
    */
  @deprecated("use HasCurrentIdentity instead", "4.0.0")
  def HasIdentity(): Expr =
    Expr(ObjectV("has_identity" -> NullV))

  /**
    * An CreateAccessProvider expression.
    *
    * @param params An object of parameters used to create a new access provider.
    *  - name: A valid schema name
    *  - issuer: A unique string
    *  - jwks_uri: A valid HTTPS URI
    *  - roles: An array of role/predicate pairs where the predicate returns a boolean.
    *                The array can also contain Role references.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/createaccessprovider?lang=scala]]
    */
  def CreateAccessProvider(params: Expr): Expr =
    Expr(ObjectV("create_access_provider" -> params.value))

  /** Native reference to all access providers
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/accessproviders?lang=scala]]
  */
  def AccessProviders(scope: Expr = NullV): Expr =
    Expr(ObjectV("access_providers" -> scope.value))

  /**
    * An Access Provider expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/accessprovider?lang=scala]]
    */
  def AccessProvider(name: Expr): Expr =
     Expr(ObjectV("access_provider" -> name.value))

  /**
    * An Access Provider expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/accessprovider?lang=scala]]
    */
  def AccessProvider(name: Expr, scope: Expr): Expr =
    Expr(ObjectV("access_provider" -> name.value, "scope" -> scope.value))


  /**
    * A HasCurrentToken expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/hascurrenttoken?lang=scala]]
    */
  def HasCurrentToken(): Expr =
    Expr(ObjectV("has_current_token" -> NullV))

  /**
    * A CurrentToken expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/currenttoken?lang=scala]]
    */
  def CurrentToken(): Expr =
    Expr(ObjectV("current_token" -> NullV))

  /**
    * A HasCurrentIdentity expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/hascurrentidentity?lang=scala]]
    */
  def HasCurrentIdentity(): Expr =
    Expr(ObjectV("has_current_identity" -> NullV))


  // String Functions

  /**
   * A Concat expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/concat?lang=scala]]
   */
  def Concat(term: Expr): Expr =
    Expr(ObjectV("concat" -> term.value))

  def Concat(term: Expr, separator: Expr): Expr =
    Expr(ObjectV("concat" -> term.value, "separator" -> separator.value))

  /**
   * A Casefold expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/casefold?lang=scala]]
   */
  def Casefold(term: Expr): Expr =
    Expr(ObjectV("casefold" -> term.value))

  /**
   * A Casefold expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/casefold?lang=scala]]
   */
  def Casefold(term: Expr, normalizer: Normalizer): Expr =
    Casefold(term, normalizer.expr)

  /**
   * A Casefold expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/casefold?lang=scala]]
   */
  def Casefold(term: Expr, normalizer: Expr): Expr =
    Expr(ObjectV("casefold" -> term.value, "normalizer" -> normalizer.value))

  /**
    * A ContainsStr expression
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/containsstr?lang=scala]]
    */
  def ContainsStr(value: Expr, search: Expr): Expr =
    Expr(ObjectV("containsstr" -> value.value, "search" -> search.value))

  /**
    * A ContainsStrRegex expression
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/containsstrregex?lang=scala]]
    */
  def ContainsStrRegex(value: Expr, pattern: Expr): Expr =
    Expr(ObjectV("containsstrregex" -> value.value, "pattern" -> pattern.value))

  /**
    * An EndsWith expression
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/endswith?lang=scala]]
    */
  def EndsWith(value: Expr, search: Expr): Expr =
    Expr(ObjectV("endswith" -> value.value, "search" -> search.value))

  /**
   * A FindStr expression
   *
   *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/findstr?lang=scala]]
   */
  def FindStr(value: Expr, find: Expr): Expr =
    Expr(ObjectV("findstr" -> value.value, "find" -> find.value))

  /**
   * A FindStr expression
   *
   *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/findstr?lang=scala]]
   */
  def FindStr(value: Expr, find: Expr, start: Expr): Expr =
    Expr(ObjectV("findstr" -> value.value, "find" -> find.value, "start" -> start.value))

  /**
    * A FindStrRegex expression
    *
    *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=scala]]
    */
  def FindStrRegex(value: Expr, pattern: Expr, start: Expr): Expr =
    Expr(ObjectV("findstrregex" -> value.value, "pattern" -> pattern.value, "start" -> start.value))

  /**
    * A FindStrRegex expression
    *
    *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/findstrregex?lang=scala]]
    */
  def FindStrRegex(value: Expr, pattern: Expr, start: Expr, num_results: Expr): Expr =
    Expr(ObjectV("findstrregex" -> value.value, "pattern" -> pattern.value, "start" -> start.value, "num_results" -> num_results.value))

  /**
   * A Length expression
   *
   *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/length?lang=scala]]
   */
  def Length(value: Expr): Expr =
    Expr(ObjectV("length" -> value.value ))

  /**
   * A LowerCase expression
   *
   *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/lowercase?lang=scala]]
   */
  def LowerCase(value: Expr): Expr =
    Expr(ObjectV("lowercase" -> value.value))

  /**
   * A LTrim expression
   *
   *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/ltrim?lang=scala]]
   */
  def LTrim(value: Expr): Expr =
    Expr(ObjectV("ltrim" -> value.value ))

  /**
    * A NGram expression.
    */
  def NGram(terms: Expr, min: Expr = NullV, max: Expr = NullV): Expr = {
    val b = Seq.newBuilder[(String, Value)]

    b += "ngram" -> terms.value
    if (min != Null()) b += "min" -> min.value
    if (max != Null()) b += "max" -> max.value
    Expr(ObjectV(b.result(): _*))
  }

  /**
    * A SplitStr expression
    *
    *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/splitstr]]
    */
  def SplitStr(value: Expr, token: Expr, count: Expr): Expr =
    Expr(ObjectV("split_str" -> value.value, "token" -> token.value, "count" -> count.value))

  /**
    * A SplitStr expression
    *
    *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/splitstr]]
    */
  def SplitStr(value: Expr, token: Expr): Expr =
    Expr(ObjectV("split_str" -> value.value, "token" -> token.value))

  /**
    * A SplitStrRegex expression
    *
    *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/splitstrregex]]
    */
  def SplitStrRegex(value: Expr, pattern: Expr, count: Expr): Expr =
    Expr(ObjectV("split_str_regex" -> value.value, "pattern" -> pattern.value, "count" -> count.value))

  /**
    * A SplitStrRegex expression
    *
    *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/splitstrregex]]
    */
  def SplitStrRegex(value: Expr, pattern: Expr): Expr =
    Expr(ObjectV("split_str_regex" -> value.value, "pattern" -> pattern.value))

  /**
    * A RegexEscape expression
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/regexescape?lang=scala]]
    */
  def RegexEscape(value: Expr): Expr =
    Expr(ObjectV("regexescape" -> value.value))

  /**
   * A Repeat expression
   *
   *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/repeat?lang=scala]]
   */
  def Repeat(value: Expr): Expr =
    Expr(ObjectV("repeat" -> value.value ))

  /**
    * A Repeat expression
    *
    *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/repeat?lang=scala]]
    */
  def Repeat(value: Expr, number: Expr): Expr =
    Expr(ObjectV("repeat" -> value.value, "number" -> number.value))

  /**
   * A ReplaceStr expression
   *
   *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/replacestr?lang=scala]]
   */
  def ReplaceStr(value: Expr, find: Expr, replace: Expr): Expr =
    Expr(ObjectV("replacestr" -> value.value, "find" -> find.value, "replace" -> replace.value))

  /**
    * A ReplaceStrRegex expression
    *
    *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=scala]]
    */
  def ReplaceStrRegex(value: Expr, pattern: Expr, replace: Expr): Expr =
    Expr(ObjectV("replacestrregex" -> value.value, "pattern" -> pattern.value, "replace" -> replace.value))

  /**
    * A ReplaceStrRegex expression
    *
    *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/replacestrregex?lang=scala]]
    */
  def ReplaceStrRegex(value: Expr, pattern: Expr, replace: Expr, first: Expr): Expr =
    Expr(ObjectV("replacestrregex" -> value.value, "pattern" -> pattern.value, "replace" -> replace.value, "first" -> first.value))

  /**
   * A RTrim expression
   *
   *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/rtrim?lang=scala]]
   */
  def RTrim(value: Expr): Expr =
    Expr(ObjectV("rtrim" -> value.value))

  /**
   * A Space expression
   *
   *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/space?lang=scala]]
   */
  def Space(value: Expr): Expr =
    Expr(ObjectV("space" -> value.value ))

  /**
    * A StartsWith expression
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/startswith?lang=scala]]
    */
  def StartsWith(value: Expr, search: Expr): Expr =
    Expr(ObjectV("startswith" -> value.value, "search" -> search.value))

  /**
    * A SubString expression
    *
    *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=scala]]
    */
  def SubString(value: Expr): Expr =
    Expr(ObjectV("substring" -> value.value))

  /**
   * A SubString expression
   *
   *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=scala]]
   */
  def SubString(value: Expr, start: Expr): Expr =
    Expr(ObjectV("substring" -> value.value, "start" -> start.value))

  /**
   * A SubString expression
   *
   *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/substring?lang=scala]]
   */
  def SubString(value: Expr, start: Expr, length: Expr): Expr =
    Expr(ObjectV("substring" -> value.value, "start" -> start.value, "length" -> length.value))

  /**
    * A TitleCase expression
    *
    *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/titlecase?lang=scala]]
    */
  def TitleCase(value: Expr): Expr =
    Expr(ObjectV("titlecase" -> value.value ))

  /**
   * A Trim expression
   *
   *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/trim?lang=scala]]
   */
  def Trim(term: Expr): Expr =
    Expr(ObjectV("trim" -> term.value ))

  /**
   * A UpperCase expression
   *
   *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/uppercase?lang=scala]]
   */
  def UpperCase(term: Expr): Expr =
    Expr(ObjectV("uppercase" -> term.value ))

  /**
    * A Format expression
    *
    *'''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/format?lang=scala]]
    */
  def Format(format: Expr, values: Expr*): Expr =
    Expr(ObjectV("format" -> format.value, "values" -> varargs(values)))

  // Time Functions

  /**
    * A Time expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/time?lang=scala]]
    */
  def Time(str: Expr): Expr =
    Expr(ObjectV("time" -> str.value))

  /**
    * An Epoch expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/epoch?lang=scala]]
    */
  def Epoch(num: Expr, unit: TimeUnit): Expr =
    Epoch(num, unit.expr)

  /**
    * An Epoch expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/epoch?lang=scala]]
    */
  def Epoch(num: Expr, unit: Expr): Expr =
    Expr(ObjectV("epoch" -> num.value, "unit" -> unit.value))

  /**
    * A TimeAdd expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=scala]]
    */
  def TimeAdd(base: Expr, offset: Expr, unit: Expr): Expr =
    Expr(ObjectV("time_add" -> base.value, "offset" -> offset.value, "unit" -> unit.value))

  /**
    * A TimeAdd expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/timeadd?lang=scala]]
    */
  def TimeAdd(base: Expr, offset: Expr, unit: TimeUnit): Expr =
    TimeAdd(base, offset, unit.expr)

  /**
    * A TimeSubtract expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=scala]]
    */
  def TimeSubtract(base: Expr, offset: Expr, unit: Expr): Expr =
    Expr(ObjectV("time_subtract" -> base.value, "offset" -> offset.value, "unit" -> unit.value))

  /**
    * A TimeSubtract expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/timesubtract?lang=scala]]
    */
  def TimeSubtract(base: Expr, offset: Expr, unit: TimeUnit): Expr =
    TimeSubtract(base, offset, unit.expr)

  /**
    * A TimeDiff expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=scala]]
    */
  def TimeDiff(start: Expr, finish: Expr, unit: Expr): Expr =
    Expr(ObjectV("time_diff" -> start.value, "other" -> finish.value, "unit" -> unit.value))

  /**
    * A TimeDiff expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/timediff?lang=scala]]
    */
  def TimeDiff(start: Expr, finish: Expr, unit: TimeUnit): Expr =
    TimeDiff(start, finish, unit.expr)

  /**
    * A Date expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/date?lang=scala]]
    */
  def Date(str: Expr): Expr =
    Expr(ObjectV("date" -> str.value))

  /**
    * Returns the current snapshot time.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/now?lang=scala]]
   */
  def Now(): Expr =
    Expr(ObjectV("now" -> NullV))

  // Misc Functions

  /**
    * A Next Id expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/newid?lang=scala]]
    */
  @deprecated("use NewId instead", "2.7.0")
  def NextId(): Expr =
    Expr(ObjectV("next_id" -> NullV))

  /**
    * A New Id expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/newid?lang=scala]]
    */
  def NewId(): Expr =
    Expr(ObjectV("new_id" -> NullV))

  /**
    * A Class expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/class?lang=scala]]
    */
  @deprecated("use Collection instead", "2.7.0")
  def Class(name: Expr): Expr =
    Expr(ObjectV("class" -> name.value))

  /**
    * A Class expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/class?lang=scala]]
    */
  @deprecated("use Collection instead", "2.7.0")
  def Class(name: Expr, scope: Expr): Expr =
    Expr(ObjectV("class" -> name.value, "scope" -> scope.value))
  
  /**
    * A Collection expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/collection?lang=scala]]
    */
  def Collection(name: Expr): Expr =
    Expr(ObjectV("collection" -> name.value))

  /**
    * A Collection expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/collection?lang=scala]]
    */
  def Collection(name: Expr, scope: Expr): Expr =
    Expr(ObjectV("collection" -> name.value, "scope" -> scope.value))  

  /**
    * A Database expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/database?lang=scala]]
    */
  def Database(name: Expr): Expr =
    Expr(ObjectV("database" -> name.value))

  /**
    * A Database expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/database?lang=scala]]
    */
  def Database(name: Expr, scope: Expr): Expr =
    Expr(ObjectV("database" -> name.value, "scope" -> scope.value))

  /**
    * An Index expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/iindex?lang=scala]]
    */
  def Index(name: Expr): Expr =
    Expr(ObjectV("index" -> name.value))

  /**
    * An Index expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/iindex?lang=scala]]
    */
  def Index(name: Expr, scope: Expr): Expr =
    Expr(ObjectV("index" -> name.value, "scope" -> scope.value))

  /**
    * A Function expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/function?lang=scala]]
    */
  def Function(name: Expr): Expr =
    Expr(ObjectV("function" -> name.value))

  /**
    * A Function expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/function?lang=scala]]
    */
  def Function(name: Expr, scope: Expr): Expr =
    Expr(ObjectV("function" -> name.value, "scope" -> scope.value))

  /**
    * A Role expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/role?lang=scala]]
    */
  def Role(name: Expr): Expr =
    Expr(ObjectV("role" -> name.value))

  /**
    * A Role expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/role?lang=scala]]
    */
  def Role(name: Expr, scope: Expr): Expr =
    Expr(ObjectV("role" -> name.value, "scope" -> scope.value))

  /**
   * An Equals expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/equals?lang=scala]]
   */
  def Equals(terms: Expr*): Expr =
    Expr(ObjectV("equals" -> varargs(terms)))

  /**
   * A Contains expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/contains?lang=scala]]
   */
  @deprecated("use ContainsPath instead", "3.0.0")
  def Contains(path: Expr, in: Expr): Expr =
    Expr(ObjectV("contains" -> path.value, "in" -> in.value))

  /**
   * A ContainsField expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/containsfield?lang=scala]]
   */
  def ContainsField(field: Expr, in: Expr): Expr =
    Expr(ObjectV("contains_field" -> field.value, "in" -> in.value))

  /**
   * A ContainsPath expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/containspath?lang=scala]]
   */
  def ContainsPath(path: Expr, in: Expr): Expr =
    Expr(ObjectV("contains_path" -> path.value, "in" -> in.value))

  /**
   * A ContainsValue expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/containsvalue?lang=scala]]
   */
  def ContainsValue(value: Expr, in: Expr): Expr =
    Expr(ObjectV("contains_value" -> value.value, "in" -> in.value))

  /**
   * A Select expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/select?lang=scala]]
   */
  def Select(path: Expr, from: Expr): Expr =
    Expr(ObjectV("select" -> path.value, "from" -> from.value))

  /**
   * A Select expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/select?lang=scala]]
   */
  def Select(path: Expr, from: Expr, default: Expr): Expr =
    Expr(ObjectV("select" -> path.value, "from" -> from.value, "default" -> default.value))

  /**
    * A SelectAll expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/selectall?lang=scala]]
    */
  @deprecated("use SelectAsIndex instead", "2.10.0")
  def SelectAll(path: Expr, from: Expr): Expr =
    Expr(ObjectV("select_all" -> path.value, "from" -> from.value))

    /**
    * A SelectAsIndex expression.
    */
  def SelectAsIndex(path: Expr, from: Expr): Expr =
    Expr(ObjectV("select_as_index" -> path.value, "from" -> from.value))

  /**
   * An Abs expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/abs?lang=scala]]
   */
  def Abs(num: Expr): Expr =
    Expr(ObjectV("abs" -> num.value))

  /**
    * An Acos expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/acos?lang=scala]]
    */
  def Acos(num: Expr): Expr =
    Expr(ObjectV("acos" -> num.value))

  /**
   * An Add expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/add?lang=scala]]
   */
  def Add(terms: Expr*): Expr =
    Expr(ObjectV("add" -> varargs(terms)))

  /**
    * An Asin expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/asin?lang=scalas]]
    */
  def Asin(num: Expr): Expr =
    Expr(ObjectV("asin" -> num.value))

  /**
    * An Atan expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/atan?lang=scala]]
    */
  def Atan(num: Expr): Expr =
    Expr(ObjectV("atan" -> num.value))

  /**
    * An BitAnd expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/bitand?lang=scala]]
    */
  def BitAnd(terms: Expr*): Expr =
    Expr(ObjectV("bitand" -> varargs(terms)))

  /**
    * A BitNot expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/bitnot?lang=scala]]
    */
  def BitNot(num: Expr): Expr =
    Expr(ObjectV("bitnot" -> num.value))

  /**
    * An BitOr expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/bitor?lang=scala]]
    */
  def BitOr(terms: Expr*): Expr =
    Expr(ObjectV("bitor" -> varargs(terms)))

  /**
    * An BitXor expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/bitxor?lang=scala]]
    */
  def BitXor(terms: Expr*): Expr =
    Expr(ObjectV("bitxor" -> varargs(terms)))

  /**
    * A Ceil expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/ceil?lang=scala]]
    */
  def Ceil(num: Expr): Expr =
    Expr(ObjectV("ceil" -> num.value))

  /**
    * A Cos expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/cos?lang=scala]]
    */
  def Cos(num: Expr): Expr =
    Expr(ObjectV("cos" -> num.value))

  /**
    * A Cosh expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/cosh?lang=scala]]
    */
  def Cosh(num: Expr): Expr =
    Expr(ObjectV("cosh" -> num.value))

  /**
    * A Degrees expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/degrees?lang=scala]]
    */
  def Degrees(num: Expr): Expr =
    Expr(ObjectV("degrees" -> num.value))

  /**
    * A Divide expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/divide?lang=scala]]
    */
  def Divide(terms: Expr*): Expr =
    Expr(ObjectV("divide" -> varargs(terms)))

  /**
    * An Exp expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/exp?lang=scala]]
    */
  def Exp(num: Expr): Expr =
    Expr(ObjectV("exp" -> num.value))

  /**
    * A Floor expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/floor?lang=scala]]
    */
  def Floor(num: Expr): Expr =
    Expr(ObjectV("floor" -> num.value))

  /**
    * A Hypot expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/hypot?lang=scala]]
    */
  def Hypot(num: Expr): Expr =
    Expr(ObjectV("hypot" -> num.value))

  /**
    * A Hypot expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/hypot?lang=scala]]
    */
  def Hypot(num: Expr, exp: Expr): Expr =
    Expr(ObjectV("hypot" -> num.value, "b" -> exp.value))

  /**
    * A ln expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/ln?lang=scala]]
    */
  def Ln(num: Expr): Expr =
    Expr(ObjectV("ln" -> num.value))

  /**
    * A Log expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/log?lang=scala]]
    */
  def Log(num: Expr): Expr =
    Expr(ObjectV("log" -> num.value))

  /**
    * A Max expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/max?lang=scala]]
    */
  def Max(terms: Expr*): Expr =
    Expr(ObjectV("max" -> varargs(terms)))

  /**
    * A Min expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/min?lang=scala]]
    */
  def Min(terms: Expr*): Expr =
    Expr(ObjectV("min" -> varargs(terms)))

  /**
    * A Modulo expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/modulo?lang=scala]]
    */
  def Modulo(terms: Expr*): Expr =
    Expr(ObjectV("modulo" -> varargs(terms)))

  /**
   * A Multiply expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/multiply?lang=scala]]
   */
  def Multiply(terms: Expr*): Expr =
    Expr(ObjectV("multiply" -> varargs(terms)))

  /**
    * A Pow expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/pow?lang=scala]]
    */
  def Pow(num: Expr): Expr =
    Expr(ObjectV("pow" -> num.value))

  /**
    * A Pow expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/pow?lang=scala]]
    */
  def Pow(num: Expr, exp: Expr): Expr =
    Expr(ObjectV("pow" -> num.value, "exp" -> exp.value))

  /**
    * A Radians expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/radians?lang=scala]]
    */
  def Radians(num: Expr): Expr =
    Expr(ObjectV("radians" -> num.value))

  /**
    * A Round expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/round?lang=scala]]
    */
  def Round(num: Expr): Expr =
    Expr(ObjectV("round" -> num.value))

  /**
    * A Round expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/round?lang=scala]]
    */
  def Round(num: Expr, precision: Expr): Expr =
    Expr(ObjectV("round" -> num.value, "precision" -> precision.value))

  /**
    * A sign expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/sign?lang=scala]]
    */
  def Sign(num: Expr): Expr =
    Expr(ObjectV("sign" -> num.value))

  /**
    * A sin expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/sin?lang=scala]]
    */
  def Sin(num: Expr): Expr =
    Expr(ObjectV("sin" -> num.value))

  /**
    * A sinh expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/sinh?lang=scala]]
    */
  def Sinh(num: Expr): Expr =
    Expr(ObjectV("sinh" -> num.value))

  /**
    * A sqrt expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/sqrt?lang=scala]]
    */
  def Sqrt(num: Expr): Expr =
    Expr(ObjectV("sqrt" -> num.value))

  /**
   * A Subtract expression.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/subtract?lang=scala]]
   */
  def Subtract(terms: Expr*): Expr =
    Expr(ObjectV("subtract" -> varargs(terms)))

  /**
    * A Tan expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/tan?lang=scala]]
    */
  def Tan(num: Expr): Expr =
    Expr(ObjectV("tan" -> num.value))

  /**
    * A Tanh expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/tanh?lang=scala]]
    */
  def Tanh(num: Expr): Expr =
    Expr(ObjectV("tanh" -> num.value))

  /**
    * A Trunc expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/trunc?lang=scala]]
    */
  def Trunc(num: Expr): Expr =
    Expr(ObjectV("trunc" -> num.value))

  /**
    * A Trunc expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/trunc?lang=scala]]
    */
  def Trunc(num: Expr, precision: Expr): Expr =
    Expr(ObjectV("trunc" -> num.value, "precision" -> precision.value))

  /**
    * Count the number of elements in the collection.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/count?lang=scala]]
    */
  def Count(collection: Expr): Expr =
    Expr(ObjectV("count" -> collection.value))

  /**
    * Sum the elements in the collection.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/sum?lang=scala]]
    */
  def Sum(collection: Expr): Expr =
    Expr(ObjectV("sum" -> collection.value))

  /**
    * Returns the mean of all elements in the collection.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/mean?lang=scala]]
    */
  def Mean(collection: Expr): Expr =
    Expr(ObjectV("mean" -> collection.value))

  /**
    * Evaluates to true if all elements of the collection is true.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/all?lang=scala]]
    */
  def All(collection: Expr): Expr =
    Expr(ObjectV("all" -> collection.value))

  /**
    * Evaluates to true if any element of the collection is true.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/any?lang=scala]]
    */
  def Any(collection: Expr): Expr =
    Expr(ObjectV("any" -> collection.value))

  /**
    * A LT expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/lt?lang=scala]]
    */
  def LT(terms: Expr*): Expr =
    Expr(ObjectV("lt" -> varargs(terms)))

  /**
    * A LTE expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/lte?lang=scala]]
    */
  def LTE(terms: Expr*): Expr =
    Expr(ObjectV("lte" -> varargs(terms)))

  /**
    * A GT expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/gt?lang=scala]]
    */
  def GT(terms: Expr*): Expr =
    Expr(ObjectV("gt" -> varargs(terms)))

  /**
    * A GTE expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/gte?lang=scala]]
    */
  def GTE(terms: Expr*): Expr =
    Expr(ObjectV("gte" -> varargs(terms)))

  /**
    * An And expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/and?lang=scala]]
    */
  def And(terms: Expr*): Expr =
    Expr(ObjectV("and" -> varargs(terms)))

  /**
    * An Or expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/or?lang=scala]]
    */
  def Or(terms: Expr*): Expr =
    Expr(ObjectV("or" -> varargs(terms)))

  /**
    * A Not expression.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/not?lang=scala]]
    */
  def Not(term: Expr): Expr =
    Expr(ObjectV("not" -> term.value))

  /**
    * Casts an expression to a string value, if possible.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/tostring?lang=scala]]
    */
  def ToString(term: Expr): Expr =
    Expr(ObjectV("to_string" -> term.value))

  /**
    * Casts an expression to a numeric value, if possible.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/tonumber?lang=scala]]
    */
  def ToNumber(term: Expr): Expr =
    Expr(ObjectV("to_number" -> term.value))

  /**
    * Casts an expression to a double value, if possible.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/todouble?lang=scala]]
    */
  def ToDouble(term: Expr): Expr =
    Expr(ObjectV("to_double" -> term.value))

  /**
    * Casts an expression to an integer value, if possible.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/tointeger?lang=scala]]
    */
  def ToInteger(term: Expr): Expr =
    Expr(ObjectV("to_integer" -> term.value))

  /**
    * Casts an expression to a time value, if possible.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/totime?lang=scala]]
    */
  def ToTime(term: Expr): Expr =
    Expr(ObjectV("to_time" -> term.value))

  /**
   * Converts a time expression to seconds since the UNIX epoch.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/toseconds?lang=scala]]
   */ 
  def ToSeconds(term: Expr): Expr =
    Expr(ObjectV("to_seconds" -> term.value))

  /**
   * Converts a time expression to milliseconds since the UNIX epoch.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/tomillis?lang=scala]]
   */
  def ToMillis(term: Expr): Expr =
    Expr(ObjectV("to_millis" -> term.value))

  /**
   * Converts a time expression to microseconds since the UNIX epoch.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/tomicros?lang=scala]]
   */ 
  def ToMicros(term: Expr): Expr =
    Expr(ObjectV("to_micros" -> term.value))

  /**
   * Returns a time expression's day of the month, from 1 to 31.
   *
   * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/dayofmonth?lang=scala]]
   */ 
  def DayOfMonth(term: Expr): Expr =
    Expr(ObjectV("day_of_month" -> term.value))

  /**
    * Returns a time expression's day of the week following ISO-8601 convention, from 1 (Monday) to 7 (Sunday).
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/dayofweek?lang=scala]]
    */ 
  def DayOfWeek(term: Expr): Expr =
    Expr(ObjectV("day_of_week" -> term.value))

  /**
    * Returns a time expression's day of the year, from 1 to 365, or 366 in a leap year.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/dayofyear?lang=scala]]
    */
  def DayOfYear(term: Expr): Expr =
    Expr(ObjectV("day_of_year" -> term.value))

  /**
    * Returns the time expression's year, following the ISO-8601 standard.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/year?lang=scala]]
    */ 
  def Year(term: Expr): Expr =
    Expr(ObjectV("year" -> term.value))

  /**
    * Returns a time expression's month of the year, from 1 to 12.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/month?lang=scala]]
    */ 
  def Month(term: Expr): Expr =
    Expr(ObjectV("month" -> term.value))
  
  /**
    * Returns a time expression's hour of the day, from 0 to 23.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/hour?lang=scala]]
    */   
  def Hour(term: Expr): Expr =
    Expr(ObjectV("hour" -> term.value))

  /**
    * Returns a time expression's minute of the hour, from 0 to 59.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/minute?lang=scala]]
    */   
  def Minute(term: Expr): Expr =
    Expr(ObjectV("minute" -> term.value))

  /**
    * Returns a time expression's second of the minute, from 0 to 59.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/second?lang=scala]]
    */ 
  def Second(term: Expr): Expr =
    Expr(ObjectV("second" -> term.value))

  /**
    * Casts an expression to a data value, if possible.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/todate?lang=scala]]
    */
  def ToDate(term: Expr): Expr =
    Expr(ObjectV("to_date" -> term.value))

  /**
    * Merge two or more objects into a single one.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/merge?lang=scala]]
    */
  def Merge(merge: Expr, `with`: Expr): Expr =
    Expr(ObjectV("merge" -> merge.value, "with" -> `with`.value))

  /**
    * Merge two or more objects into a single one. A lambda can be specified to resolve conflicts.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/merge?lang=scala]]
    */
  def Merge(merge: Expr, `with`: Expr, lambda: Expr): Expr =
    Expr(ObjectV("merge" -> merge.value, "with" -> `with`.value, "lambda" -> lambda.value))

  /**
    * Try to convert an array of (field, value) into an object.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/toobject?lang=scala]]
    */
  def ToObject(fields: Expr): Expr =
    Expr(ObjectV("to_object" -> fields.value))

  /**
    * Try to convert an object into an array of (field, value).
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/toarray?lang=scala]]
    */
  def ToArray(obj: Expr): Expr =
    Expr(ObjectV("to_array" -> obj.value))

  /**
    * Check if the expression is a number.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isnumber?lang=scala]]
    */
  def IsNumber(expr: Expr): Expr =
    Expr(ObjectV("is_number" -> expr.value))

  /**
    * Check if the expression is a double.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isdouble?lang=scala]]
    */
  def IsDouble(expr: Expr): Expr =
    Expr(ObjectV("is_double" -> expr.value))

  /**
    * Check if the expression is an integer.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isinteger?lang=scala]]
    */
  def IsInteger(expr: Expr): Expr =
    Expr(ObjectV("is_integer" -> expr.value))

  /**
    * Check if the expression is a boolean.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isboolean?lang=scala]]
    */
  def IsBoolean(expr: Expr): Expr =
    Expr(ObjectV("is_boolean" -> expr.value))

  /**
    * Check if the expression is null.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isnull?lang=scala]]
    */
  def IsNull(expr: Expr): Expr =
    Expr(ObjectV("is_null" -> expr.value))

  /**
    * Check if the expression is a byte array.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isbytes?lang=scala]]
    */
  def IsBytes(expr: Expr): Expr =
    Expr(ObjectV("is_bytes" -> expr.value))

  /**
    * Check if the expression is a timestamp.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/istimestamp?lang=scala]]
    */
  def IsTimestamp(expr: Expr): Expr =
    Expr(ObjectV("is_timestamp" -> expr.value))

  /**
    * Check if the expression is a date.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isdate?lang=scala]]
    */
  def IsDate(expr: Expr): Expr =
    Expr(ObjectV("is_date" -> expr.value))

  /**
    * Check if the expression is a string.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isstring?lang=scala]]
    */
  def IsString(expr: Expr): Expr =
    Expr(ObjectV("is_string" -> expr.value))

  /**
    * Check if the expression is an array.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isarray?lang=scala]]
    */
  def IsArray(expr: Expr): Expr =
    Expr(ObjectV("is_array" -> expr.value))

  /**
    * Check if the expression is an object.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isobject?lang=scala]]
    */
  def IsObject(expr: Expr): Expr =
    Expr(ObjectV("is_object" -> expr.value))

  /**
    * Check if the expression is a reference.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isref?lang=scala]]
    */
  def IsRef(expr: Expr): Expr =
    Expr(ObjectV("is_ref" -> expr.value))

  /**
    * Check if the expression is a set.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isset?lang=scala]]
    */
  def IsSet(expr: Expr): Expr =
    Expr(ObjectV("is_set" -> expr.value))

  /**
    * Check if the expression is a document (either a reference or an instance).
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isdoc?lang=scala]]
    */
  def IsDoc(expr: Expr): Expr =
    Expr(ObjectV("is_doc" -> expr.value))

  /**
    * Check if the expression is a lambda.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/islambda?lang=scala]]
    */
  def IsLambda(expr: Expr): Expr =
    Expr(ObjectV("is_lambda" -> expr.value))

  /**
    * Check if the expression is a collection.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/iscollection?lang=scala]]
    */
  def IsCollection(expr: Expr): Expr =
    Expr(ObjectV("is_collection" -> expr.value))

  /**
    * Check if the expression is a database.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isdatabase?lang=scala]]
    */
  def IsDatabase(expr: Expr): Expr =
    Expr(ObjectV("is_database" -> expr.value))

  /**
    * Check if the expression is an index.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isindex?lang=scala]]
    */
  def IsIndex(expr: Expr): Expr =
    Expr(ObjectV("is_index" -> expr.value))

  /**
    * Check if the expression is a function.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isfunction?lang=scala]]
    */
  def IsFunction(expr: Expr): Expr =
    Expr(ObjectV("is_function" -> expr.value))

  /**
    * Check if the expression is a key.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/iskey?lang=scala]]
    */
  def IsKey(expr: Expr): Expr =
    Expr(ObjectV("is_key" -> expr.value))

  /**
    * Check if the expression is a token.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/istoken?lang=scala]]
    */
  def IsToken(expr: Expr): Expr =
    Expr(ObjectV("is_token" -> expr.value))

  /**
    * Check if the expression is a credentials.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/iscredentials?lang=scala]]
    */
  def IsCredentials(expr: Expr): Expr =
    Expr(ObjectV("is_credentials" -> expr.value))

  /**
    * Check if the expression is a role.
    *
    * '''Reference''': [[https://docs.fauna.com/fauna/current/api/fql/functions/isrole?lang=scala]]
    */
  def IsRole(expr: Expr): Expr =
    Expr(ObjectV("is_role" -> expr.value))
}
