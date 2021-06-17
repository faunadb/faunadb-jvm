package faunadb

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate}
import java.util
import java.util.concurrent.Flow

import faunadb.FaunaClient._
import faunadb.errors._
import faunadb.query.{TimeUnit, _}
import faunadb.values._
import monix.execution.Scheduler
import monix.reactive.Observable
import org.reactivestreams.{FlowAdapters, Publisher}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Random

class ClientSpec
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures
    with IntegrationPatience {

  val config = {
    val rootKey = Option(System.getenv("FAUNA_ROOT_KEY")) getOrElse {
      throw new RuntimeException("FAUNA_ROOT_KEY must defined to run tests")
    }
    val domain = Option(System.getenv("FAUNA_DOMAIN")) getOrElse { "db.fauna.com" }
    val scheme = Option(System.getenv("FAUNA_SCHEME")) getOrElse { "https" }
    val port = Option(System.getenv("FAUNA_PORT")) getOrElse { "443" }

    collection.Map("root_token" -> rootKey, "root_url" -> s"$scheme://$domain:$port")
  }

  val rootClient = FaunaClient(endpoint = config("root_url"), secret = config("root_token"))

  val testDbName = {
    val now = Instant.now()
    val timeSuffix = now.truncatedTo(ChronoUnit.SECONDS).toString.replace(":", ".")
    val randomSuffix = aRandomString(10)
    s"faunadb-scala-test-$timeSuffix-$randomSuffix"
  }
  var client: FaunaClient = null
  var adminClient: FaunaClient = null

  // Helper fields

  val RefField = Field("ref").to[RefV]
  val TsField = Field("ts").to[Long]
  val SecretField = Field("secret").to[String]
  val DataField = Field("data")

  // Page helpers
  case class Ev(ref: RefV, ts: Long, action: String)

  val EventField = Field.zip(
    Field("document").to[RefV],
    Field("ts").to[Long],
    Field("action").to[String]
  ) map { case (r, ts, a) => Ev(r, ts, a) }

  val PageEvents = DataField.collect(EventField)
  val PageRefs = DataField.to[Seq[RefV]]

  def aRandomString: String = aRandomString(size = 8)
  def aRandomString(size: Int): String = Random.alphanumeric.take(size).mkString

  def dropDB(): Unit =
    rootClient.query(Delete(Database(testDbName))).futureValue

  // tests

  override protected def beforeAll(): Unit = {
    val db = rootClient.query(CreateDatabase(Obj("name" -> testDbName))).futureValue
    val dbRef = db(RefField).get
    val serverKey = rootClient.query(CreateKey(Obj("database" -> dbRef, "role" -> "server"))).futureValue
    val adminKey = rootClient.query(CreateKey(Obj("database" -> dbRef, "role" -> "admin"))).futureValue

    client = FaunaClient(endpoint = config("root_url"), secret = serverKey(SecretField).get)
    adminClient = FaunaClient(endpoint = config("root_url"), secret = adminKey(SecretField).get)

    client.query(CreateCollection(Obj("name" -> "spells"))).futureValue

    client.query(CreateIndex(Obj(
      "name" -> "spells_by_element",
      "source" -> Collection("spells"),
      "terms" -> Arr(Obj("field" -> Arr("data", "element"))),
      "active" -> true))).futureValue
  }

  override protected def afterAll(): Unit = {
    dropDB()
  }

  it should "parse nested sets" in {
    val collName = aRandomString
    val indexName = aRandomString
    client.query(CreateCollection(Obj("name" -> collName))).futureValue
    client.query(CreateIndex(Obj("name" -> indexName, "source" -> Collection(collName), "active" -> true))).futureValue

    val result = client.query(
      Map(
        Arr(
          Match(Index(indexName))
        ),
        Lambda(
          "x",
          Obj( "value" -> Var("x"), "IsSet" -> IsSet(Var("x")))
        )
      )
    ).futureValue

    result shouldBe ArrayV(ObjectV("value" -> SetRefV(ObjectV("match" -> RefV(indexName, Native.Indexes))), "IsSet" -> TrueV))
  }

  "Fauna Client" should "should not find an instance" in {
    client.query(Get(RefV("1234", RefV("spells", Native.Collections)))).failed.futureValue shouldBe a[NotFoundException]
  }

  "Fauna Client" should "should not find an instance for query with metrics" in {
    client.queryWithMetrics(Get(RefV("1234", RefV("spells", Native.Collections))), None).failed.futureValue shouldBe a[NotFoundException]
  }

  it should "abort the execution" in {
    client.query(Abort("a message")).failed.futureValue shouldBe a[BadRequestException]
  }

  it should "echo values" in {
    client.query(ObjectV("foo" -> StringV("bar"))).futureValue should equal (ObjectV("foo" -> StringV("bar")))
    client.query("qux").futureValue should equal (StringV("qux"))
  }

  it should "echo a query value" in {
    // Run
    val result = client.query(
      QueryV(ObjectV("lambda" -> "_", "expr" -> true))
    ).futureValue

    // Verify
    result shouldBe a[QueryV]
    val query = result.asInstanceOf[QueryV]
    query.lambda("lambda").to[String].get shouldBe "_"
    query.lambda("expr").to[Boolean].get shouldBe true
    query.lambda("api_version").toOpt shouldBe defined
  }

  it should "fail with timeout error" in {
    val timeout = 1 nano // the jdk client does not accept Duration.Zero as timeout
    val thrown = client.query("echo", timeout).failed.futureValue
    thrown shouldBe an[java.util.concurrent.CompletionException]
    thrown.getCause shouldBe an[java.net.http.HttpTimeoutException]
  }

  it should "fail if timeout is zero" in {
    val timeout = Duration.Zero
    val thrown = client.query("echo", timeout).failed.futureValue
    thrown shouldBe an[IllegalArgumentException]
    thrown.getMessage should include ("Invalid duration")
  }

  it should "fail if timeout is zero for query with metrics" in {
    val timeout = Duration.Zero
    val thrown = client.queryWithMetrics("echo", Some(timeout)).failed.futureValue
    thrown shouldBe an[IllegalArgumentException]
    thrown.getMessage should include ("Invalid duration")
  }

  it should "fail with permission denied" in {
    val key = rootClient.query(CreateKey(Obj("database" -> Database(testDbName), "role" -> "client"))).futureValue
    val client = FaunaClient(endpoint = config("root_url"), secret = key(SecretField).get)

    client.query(Paginate(Native.Databases)).failed.futureValue shouldBe a[PermissionDeniedException]
  }

  it should "fail with unauthorized" in {
    val badClient = FaunaClient(endpoint = config("root_url"), secret = "notavalidsecret")
    badClient.query(Get(RefV("12345", RefV("spells", Native.Collections)))).failed.futureValue shouldBe an[UnauthorizedException]
  }

  it should "receive a Null value properly" in {
    client.query(NullV).futureValue shouldBe NullV
  }

  it should "receive a Null value properly for query with metrics" in {
    client.queryWithMetrics(NullV, None).futureValue.value shouldBe NullV
  }

  it should "create a new instance" in {
    val inst = client.query(
      Create(Collection("spells"),
        Obj("data" -> Obj("testField" -> "testValue")))).futureValue

    inst(RefField).get.collection should equal (Some(RefV("spells", Native.Collections)))
    inst(RefField).get.database should be (None)
    inst("data", "testField").to[String].get should equal ("testValue")

    client.query(Exists(inst(RefField))).futureValue should equal (TrueV)

    val inst2 = client.query(Create(Collection("spells"),
      Obj("data" -> Obj(
        "testData" -> Obj(
          "array" -> Arr(1, "2", 3.4),
          "bool" -> true,
          "num" -> 1234,
          "string" -> "sup",
          "float" -> 1.234,
          "null" -> NullV))))).futureValue

    val testData = inst2("data", "testData")

    testData.isDefined shouldBe true
    testData("array", 0).to[Long].get shouldBe 1
    testData("array", 1).to[String].get shouldBe "2"
    testData("array", 2).to[Double].get shouldBe 3.4
    testData("string").to[String].get shouldBe "sup"
    testData( "num").to[Long].get shouldBe 1234
  }

  it should "issue a batched query" in {
    val randomText1 = aRandomString
    val randomText2 = aRandomString
    val collectionRef = Collection("spells")
    val expr1 = Create(collectionRef, Obj("data" -> Obj("queryTest1" -> randomText1)))
    val expr2 = Create(collectionRef, Obj("data" -> Obj("queryTest1" -> randomText2)))

    val results = client.query(Seq(expr1, expr2)).futureValue

    results.length shouldBe 2
    results(0)("data", "queryTest1").to[String].get shouldBe randomText1
    results(1)("data", "queryTest1").to[String].get shouldBe randomText2
  }

  it should "get at timestamp" in {
    val randomCollectionName = aRandomString
    val randomCollection = client.query(CreateCollection(Obj("name" -> randomCollectionName))).futureValue

    val data = client.query(Create(randomCollection(RefField).get, Obj("data" -> Obj("x" -> 1)))).futureValue
    val dataRef = data(RefField).get

    val ts1 = data(TsField).get
    val ts2 = (client.query(Update(dataRef, Obj("data" -> Obj("x" -> 2)))).futureValue).apply(TsField).get
    val ts3 = (client.query(Update(dataRef, Obj("data" -> Obj("x" -> 3)))).futureValue).apply(TsField).get

    val xField = Field("data", "x").to[Long]

    (client.query(At(ts1, Get(dataRef))).futureValue).apply(xField).get should equal(1)
    (client.query(At(ts2, Get(dataRef))).futureValue).apply(xField).get should equal(2)
    (client.query(At(ts3, Get(dataRef))).futureValue).apply(xField).get should equal(3)
  }

  it should "issue a paginated query" in {
    val randomCollectionName = aRandomString
    val randomCollection = client.query(CreateCollection(Obj("name" -> randomCollectionName))).futureValue
    val collectionRef = randomCollection(RefField)

    val randomCollectionIndexResult = client.query(CreateIndex(Obj(
      "name" -> (randomCollectionName + "_collection_index"),
      "source" -> collectionRef,
      "active" -> true,
      "unique" -> false
    ))).futureValue

    val indexCreate = client.query(CreateIndex(Obj(
      "name" -> (randomCollectionName + "_test_index"),
      "source" -> collectionRef,
      "terms" -> Arr(Obj("field" -> Arr("data", "queryTest1"))),
      "active" -> true,
      "unique" -> false
    ))).futureValue

    val randomCollectionIndex = randomCollectionIndexResult(RefField).get
    val testIndex = indexCreate(RefField).get

    val randomText1 = aRandomString
    val randomText2 = aRandomString
    val randomText3 = aRandomString

    val create1 = client.query(Create(collectionRef, Obj("data" -> Obj("queryTest1" -> randomText1)))).futureValue
    val create2 = client.query(Create(collectionRef, Obj("data" -> Obj("queryTest1" -> randomText2)))).futureValue
    val create3 = client.query(Create(collectionRef, Obj("data" -> Obj("queryTest1" -> randomText3)))).futureValue

    val queryMatch = client.query(Paginate(Match(testIndex, randomText1))).futureValue

    queryMatch(PageRefs).get shouldBe Seq(create1(RefField).get)

    val resp = client.query(Paginate(Match(randomCollectionIndex), size = 1)).futureValue

    resp("data").to[ArrayV].get.elems.size shouldBe 1
    resp("after").isDefined should equal (true)
    resp("before").isDefined should equal (false)

    val resp2 = client.query(Paginate(Match(randomCollectionIndex), After(resp("after")), size = 1)).futureValue

    resp2("data").to[Seq[Value]].get.size shouldBe 1
    resp2("after").isDefined should equal (true)
    resp2("before").isDefined should equal (true)
  }

  it should "return single instance from index for query metrics" in {
    val randomCollectionName = aRandomString
    client.query(CreateCollection(Obj("name" -> randomCollectionName))).futureValue

    client.query(CreateIndex(Obj(
      "name" -> (randomCollectionName + "_by_element"),
      "source" -> Collection(randomCollectionName),
      "terms" -> Arr(Obj("field" -> Arr("data", "element"))),
      "active" -> true))).futureValue

    client.query(Create(Collection(randomCollectionName), Obj("data" -> Obj("name" -> "Magic Missile", "element" -> "arcane", "cost" -> 10L)))).futureValue
    client.query(Create(Collection(randomCollectionName), Obj("data" -> Obj("name" -> "Fire Bolt", "element" -> "fire", "cost" -> 15L)))).futureValue

    val valueResponse = client.queryWithMetrics(
      Map(
        Paginate(Match(Index(randomCollectionName + "_by_element"), "fire")),
        Lambda(nextRef => Select("data", Get(nextRef)))
      ),
      None
    ).futureValue.value

    valueResponse("data")(0).get("name").to[String].get shouldBe "Fire Bolt"
    valueResponse("data")(0).get("element").to[String].get shouldBe "fire"
    valueResponse("data")(0).get("cost").to[Long].get shouldBe 15L
  }

  it should "list all items in collection for query with metrics" in {
    val randomCollectionName = aRandomString
    client.query(CreateCollection(Obj("name" -> randomCollectionName))).futureValue

    client.query(Create(Collection(randomCollectionName), Obj("data" -> Obj("name" -> "Magic Missile", "element" -> "arcane", "cost" -> 10L)))).futureValue
    client.query(Create(Collection(randomCollectionName), Obj("data" -> Obj("name" -> "Fire Bolt", "element" -> "fire", "cost" -> 15L)))).futureValue

    val valueResponse = client.queryWithMetrics(
      Map(
        Paginate(Documents(Collection(randomCollectionName))),
        Lambda(nextRef => Select("data", Get(nextRef)))
      ),
      None
    ).futureValue.value

    valueResponse("data").get.asInstanceOf[ArrayV].elems.length shouldBe 2
  }

  it should "return metrics data" in {
    val metricsResponse = client.queryWithMetrics(
      Paginate(Match(Index("spells_by_element"), Value("fire"))),
      None
    ).futureValue

    val byteReadOps = metricsResponse.getMetric(Metrics.ByteReadOps)
    val byteWriteOps = metricsResponse.getMetric(Metrics.ByteWriteOps)
    val computeOps = metricsResponse.getMetric(Metrics.ComputeOps)
    val faunaDbBuild = metricsResponse.getMetric(Metrics.FaunaDbBuild)
    val queryBytesIn = metricsResponse.getMetric(Metrics.QueryBytesIn)
    val queryBytesOut = metricsResponse.getMetric(Metrics.QueryBytesOut)
    val queryTime = metricsResponse.getMetric(Metrics.QueryTime)
    val readOps = metricsResponse.getMetric(Metrics.ReadOps)
    val storageBytesRead = metricsResponse.getMetric(Metrics.StorageBytesRead)
    val storageBytesWrite = metricsResponse.getMetric(Metrics.StorageBytesWrite)
    val txnRetries = metricsResponse.getMetric(Metrics.TxnRetries)
    val txnTime = metricsResponse.getMetric(Metrics.TxnTime)
    val writeOps = metricsResponse.getMetric(Metrics.WriteOps)

    byteReadOps.isDefined should equal (true)
    byteWriteOps.isDefined should equal (true)
    computeOps.isDefined should equal (true)
    faunaDbBuild.isDefined should equal (true)
    queryBytesIn.isDefined should equal (true)
    queryBytesOut.isDefined should equal (true)
    queryTime.isDefined should equal (true)
    readOps.isDefined should equal (true)
    storageBytesRead.isDefined should equal (true)
    storageBytesWrite.isDefined should equal (true)
    txnRetries.isDefined should equal (true)
    txnTime.isDefined should equal (true)
    writeOps.isDefined should equal (true)
  }

  it should "paginate with cursor object" in {
    val collection =
      client.query(
        CreateCollection(Obj(
          "name" -> aRandomString
        ))).futureValue

    val index =
      client.query(
        CreateIndex(Obj(
          "name" -> Concat(collection("name"), "_collection_index"),
          "source" -> collection("ref"),
          "active" -> true
        ))).futureValue

    client.query(
      Arr(0 until 10 map { _ =>
        Create(collection("ref"), Obj(
          "name" -> aRandomString
        ))
      }: _*)
    ).futureValue

    def page(cursor: Expr): Value =
      client.query(
        Paginate(
          Match(index("ref")),
          cursor = cursor,
          size = 4
        )).futureValue

    val first = page(Null())
    val second = page(Obj("after" -> first("after")))
    val third = page(Obj("before" -> second("before")))

    first("data").to[Seq[Value]].get should have size 4
    first("data") shouldNot equal(second("data"))
    first("data") shouldBe third("data")
  }

  it should "handle a constraint violation" in {
    val randomCollectionName = aRandomString
    val randomCollection = client.query(CreateCollection(Obj("name" -> randomCollectionName))).futureValue
    val collectionRef = randomCollection(RefField).get

    client.query(CreateIndex(Obj(
      "name" -> (randomCollectionName+"_by_unique_test"),
      "source" -> collectionRef,
      "terms" -> Arr(Obj("field" -> Arr("data", "uniqueTest1"))),
      "unique" -> true, "active" -> true))).futureValue

    val randomText = aRandomString
    client.query(Create(collectionRef, Obj("data" -> Obj("uniqueTest1" -> randomText)))).futureValue

    val result = client.query(Create(collectionRef, Obj("data" -> Obj("uniqueTest1" -> randomText)))).failed.futureValue

    result shouldBe a[BadRequestException]
    result.getMessage() should include("instance not unique")
  }

  it should "test types" in {
    val set = client.query(Match(Index("spells_by_element"), "arcane")).futureValue.to[SetRefV].get
    set.parameters("match").to[RefV].get shouldBe RefV("spells_by_element", Native.Indexes)
    set.parameters("terms").to[String].get shouldBe "arcane"

    client.query(Array[Byte](0x1, 0x2, 0x3, 0x4)).futureValue should equal (BytesV(0x1, 0x2, 0x3, 0x4))
  }

  it should "test basic forms" in {
    val letR = client.query(Let { val x = 1; val y = Add(x, 2); y }).futureValue
    letR.to[Long].get shouldBe 3

    val ifR = client.query(If(true, "was true", "was false")).futureValue
    ifR.to[String].get shouldBe "was true"

    val randomNum = Math.abs(Random.nextLong() % 250000L) + 250000L
    val randomRef = RefV(randomNum.toString, RefV("spells", Native.Collections))
    val doR = client.query(Do(
      Create(randomRef, Obj("data" -> Obj("name" -> "Magic Missile"))),
      Get(randomRef)
    )).futureValue
    doR(RefField).get shouldBe randomRef

    val objectR = client.query(Obj("name" -> "Hen Wen", "age" -> 123)).futureValue
    objectR("name").to[String].get shouldBe "Hen Wen"
    objectR("age").to[Long].get shouldBe 123

  }

  it should "test collections" in {
    val mapR = client.query(Map(Arr(1L, 2L, 3L), Lambda(i => Add(i, 1L)))).futureValue
    mapR.to[Seq[Long]].get shouldBe Seq(2, 3, 4)

    val foreachR = client.query(Foreach(Arr("Fireball Level 1", "Fireball Level 2"), Lambda(spell => Create(Collection("spells"), Obj("data" -> Obj("name" -> spell)))))).futureValue
    foreachR.to[Seq[String]].get shouldBe Seq("Fireball Level 1", "Fireball Level 2")

    val filterR = client.query(Filter(Arr(1, 2, 3, 4), Lambda(i => If(Equals(Modulo(i, 2), 0), true, false)))).futureValue
    filterR.to[Seq[Long]].get shouldBe Seq(2, 4)

    val takeR = client.query(Take(2, Arr(1, 2, 3, 4))).futureValue
    takeR.to[Seq[Long]].get shouldBe Seq(1, 2)

    val dropR = client.query(Drop(2, Arr(1, 2, 3, 4))).futureValue
    dropR.to[Seq[Long]].get shouldBe Seq(3, 4)

    val prependR = client.query(Prepend(Arr(1, 2, 3), Arr(4, 5, 6))).futureValue
    prependR.to[Seq[Long]].get shouldBe Seq(1, 2, 3, 4, 5, 6)

    val appendR = client.query(Append(Arr(1, 2, 3), Arr(4, 5, 6))).futureValue
    appendR.to[Seq[Long]].get shouldBe Seq(4, 5, 6, 1, 2, 3)

    val randomElement = aRandomString
    client.query(Create(Collection("spells"), Obj("data" -> Obj("name" -> "predicate test", "element" -> randomElement)))).futureValue

    //arrays
    client.query(IsEmpty(Arr(1, 2, 3))).futureValue.to[Boolean].get shouldBe false
    client.query(IsEmpty(Arr())).futureValue.to[Boolean].get shouldBe true

    client.query(IsNonEmpty(Arr(1, 2, 3))).futureValue.to[Boolean].get shouldBe true
    client.query(IsNonEmpty(Arr())).futureValue.to[Boolean].get shouldBe false

    //pages
    client.query(IsEmpty(Paginate(Match(Index("spells_by_element"), randomElement)))).futureValue.to[Boolean].get shouldBe false
    client.query(IsEmpty(Paginate(Match(Index("spells_by_element"), "an invalid element")))).futureValue.to[Boolean].get shouldBe true

    client.query(IsNonEmpty(Paginate(Match(Index("spells_by_element"), randomElement)))).futureValue.to[Boolean].get shouldBe true
    client.query(IsNonEmpty(Paginate(Match(Index("spells_by_element"), "an invalid element")))).futureValue.to[Boolean].get shouldBe false
  }

  it should "test resource modification" in {
    val createR = client.query(Create(Collection("spells"), Obj("data" -> Obj("name" -> "Magic Missile", "element" -> "arcane", "cost" -> 10L)))).futureValue
    createR(RefField).get.collection shouldBe Some(RefV("spells", Native.Collections))
    createR(RefField).get.database shouldBe None
    createR("data", "name").to[String].get shouldBe "Magic Missile"
    createR("data", "element").to[String].get shouldBe "arcane"
    createR("data", "cost").to[Long].get shouldBe 10L

    val updateR = client.query(Update(createR(RefField), Obj("data" -> Obj("name" -> "Faerie Fire", "cost" -> NullV)))).futureValue
    updateR(RefField).get shouldBe createR(RefField).get
    updateR("data", "name").to[String].get shouldBe "Faerie Fire"
    updateR("data", "element").to[String].get shouldBe "arcane"
    updateR("data", "cost").isDefined should equal (false)

    val replaceR = client.query(Replace(createR("ref"), Obj("data" -> Obj("name" -> "Volcano", "element" -> Arr("fire", "earth"), "cost" -> 10L)))).futureValue
    replaceR("ref").get shouldBe createR("ref").get
    replaceR("data", "name").to[String].get shouldBe "Volcano"
    replaceR("data", "element").to[Seq[String]].get shouldBe Seq("fire", "earth")
    replaceR("data", "cost").to[Long].get shouldBe 10L

    val insertR = client.query(Insert(createR("ref"), 1L, Action.Create, Obj("data" -> Obj("cooldown" -> 5L)))).futureValue
    insertR("document").get shouldBe createR("ref").get

    val removeR = client.query(Remove(createR("ref"), 2L, Action.Delete)).futureValue
    removeR shouldBe NullV

    client.query(Delete(createR("ref"))).futureValue
    val getR = client.query(Get(createR("ref"))).failed.futureValue
    getR shouldBe a[NotFoundException]
  }

  it should "test sets" in {
    val create1R = client.query(Create(Collection("spells"),
      Obj("data" -> Obj("name" -> "Magic Missile", "element" -> "arcane", "cost" -> 10L)))).futureValue
    val create2R = client.query(Create(Collection("spells"),
      Obj("data" -> Obj("name" -> "Fireball", "element" -> "fire", "cost" -> 10L)))).futureValue
    val create3R = client.query(Create(Collection("spells"),
      Obj("data" -> Obj("name" -> "Faerie Fire", "element" -> Arr("arcane", "nature"), "cost" -> 10L)))).futureValue
    val create4R = client.query(Create(Collection("spells"),
      Obj("data" -> Obj("name" -> "Summon Animal Companion", "element" -> "nature", "cost" -> 10L)))).futureValue

    val matchR = client.query(Paginate(Match(Index("spells_by_element"), "arcane"))).futureValue
    matchR("data").to[Seq[RefV]].get should contain (create1R("ref").get)

    val matchEventsR = client.query(Paginate(Match(Index("spells_by_element"), "arcane"), events = true)).futureValue
    matchEventsR(PageEvents).get map { _.ref } should contain (create1R("ref").to[RefV].get)

    val unionR = client.query(Paginate(Union(
      Match(Index("spells_by_element"), "arcane"),
      Match(Index("spells_by_element"), "fire")))).futureValue
    unionR(PageRefs).get should (contain (create1R(RefField).get) and contain (create2R(RefField).get))

    val unionEventsR = client.query(Paginate(Union(
      Match(Index("spells_by_element"), "arcane"),
      Match(Index("spells_by_element"), "fire")), events = true)).futureValue

    unionEventsR(PageEvents).get collect { case e if e.action == "add" => e.ref } should (
      contain (create1R(RefField).get) and contain (create2R(RefField).get))

    val intersectionR = client.query(Paginate(Intersection(
      Match(Index("spells_by_element"), "arcane"),
      Match(Index("spells_by_element"), "nature")))).futureValue
    intersectionR(PageRefs).get should contain (create3R(RefField).get)

    val differenceR = client.query(Paginate(Difference(
      Match(Index("spells_by_element"), "nature"),
      Match(Index("spells_by_element"), "arcane")))).futureValue

    differenceR(PageRefs).get should contain (create4R(RefField).get)
  }

  it should "test events api" in {
    val randomCollectionName = aRandomString
    val randomCollection = client.query(CreateCollection(Obj("name" -> randomCollectionName))).futureValue

    val data = client.query(Create(randomCollection(RefField).get, Obj("data" -> Obj("x" -> 1)))).futureValue
    val dataRef = data(RefField).get

    client.query(Update(dataRef, Obj("data" -> Obj("x" -> 2)))).futureValue
    client.query(Delete(dataRef)).futureValue

    case class Event(action: String, document: RefV)

    implicit val eventCodec = Codec.Record[Event]

    // Events
    val events = (client.query(Paginate(Events(dataRef))).futureValue).apply(DataField.to[List[Event]]).get

    events.length shouldBe 3
    events(0).action shouldBe "create"
    events(0).document shouldBe dataRef

    events(1).action shouldBe "update"
    events(1).document shouldBe dataRef

    events(2).action shouldBe "delete"
    events(2).document shouldBe dataRef

    // Singleton
    val singletonsR = client.query(Paginate(Events(Singleton(dataRef)))).futureValue
    val singletons = singletonsR(DataField.to[List[Event]]).get

    singletons.length shouldBe 2
    singletons(0).action shouldBe "add"
    singletons(0).document shouldBe dataRef

    singletons(1).action shouldBe "remove"
    singletons(1).document shouldBe dataRef
  }

  it should "test string functions" in {
    (client.query(ContainsStr("ABCDEF","CDE")).futureValue).to[Boolean].get shouldBe true
    (client.query(ContainsStr("ABCDEF","GHI")).futureValue).to[Boolean].get shouldBe false

    (client.query(ContainsStrRegex("ABCDEF","[A-Z]")).futureValue).to[Boolean].get shouldBe true
    (client.query(ContainsStrRegex("123456","[A-Z]")).futureValue).to[Boolean].get shouldBe false

    (client.query(EndsWith("ABCDEF","DEF")).futureValue).to[Boolean].get shouldBe true
    (client.query(EndsWith("ABCDEF","ABC")).futureValue).to[Boolean].get shouldBe false

    (client.query(FindStr("heLLo world","world")).futureValue).to[Long].get shouldBe 6L
    (client.query(Length("heLLo world")).futureValue).to[Long].get shouldBe 11L
    (client.query(LowerCase("hEllO wORLd")).futureValue).to[String].get shouldBe "hello world"
    (client.query(LTrim("   hello world")).futureValue).to[String].get shouldBe "hello world"
    (client.query(RegexEscape("ABCDEF")).futureValue).to[String].get shouldBe """\QABCDEF\E"""
    (client.query(ReplaceStrRegex("hello world","hello","bye")).futureValue).to[String].get shouldBe "bye world"
    (client.query(Repeat("bye ")).futureValue).to[String].get shouldBe "bye bye "
    (client.query(Repeat("bye ",3)).futureValue).to[String].get shouldBe "bye bye bye "
    (client.query(ReplaceStr("hello world","hello","bye")).futureValue).to[String].get shouldBe "bye world"
    (client.query(RTrim("hello world    ")).futureValue).to[String].get shouldBe "hello world"
    (client.query(Space(4)).futureValue).to[String].get shouldBe "    "

    (client.query(StartsWith("ABCDEF","ABC")).futureValue).to[Boolean].get shouldBe true
    (client.query(StartsWith("ABCDEF","DEF")).futureValue).to[Boolean].get shouldBe false

    (client.query(SubString("heLLo world", 6)).futureValue).to[String].get shouldBe "world"
    (client.query(Trim("    hello world    ")).futureValue).to[String].get shouldBe "hello world"
    (client.query(TitleCase("heLLo worlD")).futureValue).to[String].get shouldBe "Hello World"
    (client.query(UpperCase("hello world")).futureValue).to[String].get shouldBe "HELLO WORLD"

    (client.query(Casefold("Hen Wen")).futureValue).to[String].get shouldBe "hen wen"

    // https://unicode.org/reports/tr15/
    (client.query(Casefold("\u212B", Normalizer.NFD)).futureValue).to[String].get shouldBe "A\u030A"
    (client.query(Casefold("\u212B", Normalizer.NFC)).futureValue).to[String].get shouldBe "\u00C5"
    (client.query(Casefold("\u1E9B\u0323", Normalizer.NFKD)).futureValue).to[String].get shouldBe "\u0073\u0323\u0307"
    (client.query(Casefold("\u1E9B\u0323", Normalizer.NFKC)).futureValue).to[String].get shouldBe "\u1E69"
    (client.query(Casefold("\u212B", Normalizer.NFKCCaseFold)).futureValue).to[String].get shouldBe "\u00E5"

    (client.query(NGram("what")).futureValue).to[Seq[String]].get shouldBe Seq("w", "wh", "h", "ha", "a", "at", "t")
    (client.query(NGram("what", 2, 3)).futureValue).to[Seq[String]].get shouldBe Seq("wh", "wha", "ha", "hat", "at")

    (client.query(NGram(Arr("john", "doe"))).futureValue).to[Seq[String]].get shouldBe Seq("j", "jo", "o", "oh", "h", "hn", "n", "d", "do", "o", "oe", "e")
    (client.query(NGram(Arr("john", "doe"), 3, 4)).futureValue).to[Seq[String]].get shouldBe Seq("joh", "john", "ohn", "doe")

    (client.query(Format("%3$s%1$s %2$s", "DB", "rocks", "Fauna")).futureValue).to[String].get shouldBe "FaunaDB rocks"

    val zeroSplitErr = client.query(SplitStr("hello world", " ", 0)).failed.futureValue
    zeroSplitErr shouldBe a[BadRequestException]
    zeroSplitErr.getMessage should include ("invalid argument: count must be count > 0")
    val maxSplitErr = client.query(SplitStr("hello world", " ", 1025)).failed.futureValue
    maxSplitErr shouldBe a[BadRequestException]
    maxSplitErr.getMessage should include ("invalid argument: count must be count <= 1024")
    client.query(SplitStr("hello world", " ", 1)).futureValue.to[Seq[String]].get shouldBe Seq("hello world")
    client.query(SplitStr("hello world", " ", 2)).futureValue.to[Seq[String]].get shouldBe Seq("hello", "world")
    // count is optional
    client.query(SplitStr("hello world", " ")).futureValue.to[Seq[String]].get shouldBe Seq("hello", "world")
    client.query(SplitStr("hello better world", " ", 2)).futureValue.to[Seq[String]].get shouldBe Seq("hello", "better world")
    client.query(SplitStr("hello better world", " ", 3)).futureValue.to[Seq[String]].get shouldBe Seq("hello", "better", "world")
    client.query(SplitStr("hello better world", "better", 2)).futureValue.to[Seq[String]].get shouldBe Seq("hello ", " world")

    val zeroSplitRegexErr = client.query(SplitStrRegex("hello world", ".",0)).failed.futureValue
    zeroSplitRegexErr shouldBe a[BadRequestException]
    zeroSplitRegexErr.getMessage should include ("invalid argument: count must be count > 0")
    val maxSplitRegexErr = client.query(SplitStrRegex("hello world",".", 1025)).failed.futureValue
    maxSplitRegexErr shouldBe a[BadRequestException]
    maxSplitRegexErr.getMessage should include ("invalid argument: count must be count <= 1024")
    val badSplitRegexErr = client.query(SplitStrRegex("hello world", "{", 1)).failed.futureValue
    badSplitRegexErr shouldBe a[BadRequestException]
    badSplitRegexErr.getMessage should include ("invalid argument: Search pattern /{/ is not a valid regular expression.")
    client.query(SplitStrRegex("hello world", ".*", 3)).futureValue.to[Seq[String]].get shouldBe Seq("","","")
    // count is optional
    client.query(SplitStrRegex("hello world", ".*")).futureValue.to[Seq[String]].get shouldBe Seq("","","")
    client.query(SplitStrRegex("hello world", "\\W", 2)).futureValue.to[Seq[String]].get shouldBe Seq("hello", "world")
  }

  it should "test math functions" in {

    val absR = client.query(Abs(-100L)).futureValue
    absR.to[Long].get shouldBe 100L

    val acosR = client.query(Trunc(Acos(0.5D), 2)).futureValue
    acosR.to[Double].get shouldBe 1.04D

    val addR = client.query(Add(100L, 10L)).futureValue
    addR.to[Long].get shouldBe 110L

    val asinR = client.query(Trunc(Asin(0.5D), 2)).futureValue
    asinR.to[Double].get shouldBe 0.52D

    val atanR = client.query(Trunc(Atan(0.5D), 2)).futureValue
    atanR.to[Double].get shouldBe 0.46D

    val bitandR = client.query(BitAnd(15L, 7L, 3L)).futureValue
    bitandR.to[Long].get shouldBe 3L

    val bitnotR = client.query(BitNot(3L)).futureValue
    bitnotR.to[Long].get shouldBe -4L

    val bitorR = client.query(BitOr(15L, 7L, 3L)).futureValue
    bitorR.to[Long].get shouldBe 15L

    val bitxorR = client.query(BitXor(2L, 1L)).futureValue
    bitxorR.to[Long].get shouldBe 3L

    val ceilR = client.query(Ceil(1.01D)).futureValue
    ceilR.to[Double].get shouldBe 2.0D

    val cosR = client.query(Trunc(Cos(0.5D), 2)).futureValue
    cosR.to[Double].get shouldBe 0.87D

    val coshR = client.query(Trunc(Cosh(2L),2)).futureValue
    coshR.to[Double].get shouldBe 3.76D

    val degreesR = client.query(Trunc(Degrees(2.0D),2)).futureValue
    degreesR.to[Double].get shouldBe 114.59D

    val divideR = client.query(Divide(100L, 10L)).futureValue
    divideR.to[Long].get shouldBe 10L

    val expR = client.query(Trunc(Exp(2L), 2)).futureValue
    expR.to[Double].get shouldBe 7.38D

    val floorR = client.query(Floor(1.91D)).futureValue
    floorR.to[Double].get shouldBe 1.0D

    val hypotR = client.query(Hypot(3D, 4D)).futureValue
    hypotR.to[Double].get shouldBe 5.0D

    val lnR = client.query(Trunc(Ln(2L),2)).futureValue
    lnR.to[Double].get shouldBe 0.69D

    val logR = client.query(Trunc(Log(2L),2)).futureValue
    logR.to[Double].get shouldBe 0.30D

    val maxR = client.query(Max(101L, 10L, 1L)).futureValue
    maxR.to[Long].get shouldBe 101L

    val minR = client.query(Min(101L, 10L)).futureValue
    minR.to[Long].get shouldBe 10L

    val moduloR = client.query(Modulo(101L, 10L)).futureValue
    moduloR.to[Long].get shouldBe 1L

    val multiplyR = client.query(Multiply(100L, 10L)).futureValue
    multiplyR.to[Long].get shouldBe 1000L

    val radiansR = client.query(Trunc(Radians(500), 2)).futureValue
    radiansR.to[Double].get shouldBe 8.72D

    val roundR = client.query(Round(12345.6789)).futureValue
    roundR.to[Double].get shouldBe 12345.68D

    val signR = client.query(Sign(3L)).futureValue
    signR.to[Long].get shouldBe 1L

    val sinR = client.query(Trunc(Sin(0.5D), 2)).futureValue
    sinR.to[Double].get shouldBe 0.47D

    val sinhR = client.query(Trunc(Sinh(0.5D), 2)).futureValue
    sinhR.to[Double].get shouldBe 0.52D

    val sqrtR = client.query(Sqrt(16L)).futureValue
    sqrtR.to[Double].get shouldBe 4L

    val subtractR = client.query(Subtract(100L, 10L)).futureValue
    subtractR.to[Long].get shouldBe 90L

    val tanR = client.query(Trunc(Tan(0.5D), 2)).futureValue
    tanR.to[Double].get shouldBe 0.54D

    val tanhR = client.query(Trunc(Tanh(0.5D), 2)).futureValue
    tanhR.to[Double].get shouldBe 0.46D

    val truncR = client.query(Trunc(123.456D, 2L)).futureValue
    truncR.to[Double].get shouldBe 123.45D

  }

  it should "test miscellaneous functions" in {
    // NewId
    val newIdR = client.query(NewId()).futureValue
    newIdR.to[String].get should not be null

    // Equals
    val equalsR = client.query(Equals("fire", "fire")).futureValue
    equalsR.to[Boolean].get shouldBe true

    // Concat
    val concatR = client.query(Concat(Arr("Magic", "Missile"))).futureValue
    concatR.to[String].get shouldBe "MagicMissile"

    val concat2R = client.query(Concat(Arr("Magic", "Missile"), " ")).futureValue
    concat2R.to[String].get shouldBe "Magic Missile"

    // ContainsField
    client.query(ContainsField("foo", Obj("foo" -> "bar"))).futureValue shouldBe TrueV
    client.query(ContainsField("foo", Obj())).futureValue shouldBe FalseV

    // ContainsPath
    val containsPath = client.query(ContainsPath("favorites" / "foods", Obj("favorites" -> Obj("foods" -> Arr("crunchings", "munchings"))))).futureValue
    containsPath.to[Boolean].get shouldBe true

    client.query(ContainsPath("field", Obj("field" -> "value"))).futureValue shouldBe TrueV
    client.query(ContainsPath(1, Arr("value0", "value1", "value2"))).futureValue shouldBe TrueV

    client.query(ContainsPath("a" / "nested" / 0 / "path", Obj("a" -> Obj("nested" -> Arr(Obj("path" -> "value")))))).futureValue shouldBe TrueV

    // ContainsValue
    val collectionName = aRandomString
    client.query(CreateCollection(Obj("name" -> collectionName))).futureValue

    val document = client.query(Create(Collection(collectionName), Obj())).futureValue
    val ref = document("ref").to[RefV].get
    client.query(ContainsValue(ref.id, ref)).futureValue shouldBe TrueV

    client.query(ContainsValue("1", Arr("1", "2", "3"))).futureValue shouldBe TrueV

    client.query(ContainsValue("bar", Obj("foo" -> "bar"))).futureValue shouldBe TrueV

    val indexName = aRandomString
    client.query(
      CreateIndex(Obj(
        "name" -> indexName,
          "source" -> Collection(collectionName),
          "active" -> true,
          "terms" -> Arr(Obj("field" -> Arr("data", "value"))),
          "values" -> Arr(Obj("field" -> Arr("data", "value")))
        ))).futureValue

    client.query(Create(Collection(collectionName), Obj("data" -> Obj("value" -> "foo")))).futureValue
    client.query(ContainsValue("foo", Match(Index(indexName), "foo"))).futureValue shouldBe TrueV

    // Select
    val selectR = client.query(Select("favorites" / "foods" / 1, Obj("favorites" -> Obj("foods" -> Arr("crunchings", "munchings", "lunchings"))))).futureValue
    selectR.to[String].get shouldBe "munchings"

    client.query(Select("field", Obj("field" -> "value"))).futureValue shouldBe StringV("value")
    client.query(Select("non-existent-field", Obj("field" -> "value"), "a default value")).futureValue shouldBe StringV("a default value")

    client.query(Select(1, Arr("value0", "value1", "value2"))).futureValue shouldBe StringV("value1")
    client.query(Select(100, Arr("value0", "value1", "value2"), "a default value")).futureValue shouldBe StringV("a default value")

    client.query(Select("a" / "nested" / 0 / "path", Obj("a" -> Obj("nested" -> Arr(Obj("path" -> "value")))))).futureValue shouldBe StringV("value")

    // SelectAll
    client.query(SelectAll("foo", Arr(Obj("foo" -> "bar"), Obj("foo" -> "baz"), Obj("a" -> "b")))).futureValue shouldBe ArrayV("bar", "baz")
    client.query(SelectAll("foo" / "bar", Arr(Obj("foo" -> Obj("bar" -> 1)), Obj("foo" -> Obj("bar" -> 2))))).futureValue shouldBe ArrayV(1, 2)
    client.query(SelectAll("foo" / 0, Arr(Obj("foo" -> Arr(0, 1)), Obj("foo" -> Arr(2, 3))))).futureValue shouldBe ArrayV(0, 2)

    // SelectAsIndex
    client.query(SelectAsIndex("foo", Arr(Obj("foo" -> "bar"), Obj("foo" -> "baz"), Obj("a" -> "b")))).futureValue shouldBe ArrayV("bar", "baz")
    client.query(SelectAsIndex("foo" / "bar", Arr(Obj("foo" -> Obj("bar" -> 1)), Obj("foo" -> Obj("bar" -> 2))))).futureValue shouldBe ArrayV(1, 2)
    client.query(SelectAsIndex("foo" / 0, Arr(Obj("foo" -> Arr(0, 1)), Obj("foo" -> Arr(2, 3))))).futureValue shouldBe ArrayV(0, 2)

    // And
    val andR = client.query(And(true, false)).futureValue
    andR.to[Boolean].get shouldBe false

    // Or
    val orR = client.query(Or(true, false)).futureValue
    orR.to[Boolean].get shouldBe true

    // Not
    val notR = client.query(Not(false)).futureValue
    notR.to[Boolean].get shouldBe true
  }

  it should "test Contains function" in {
    val containsR = client.query(Contains("favorites" / "foods", Obj("favorites" -> Obj("foods" -> Arr("crunchings", "munchings"))))).futureValue
    containsR.to[Boolean].get shouldBe true

    client.query(Contains("field", Obj("field" -> "value"))).futureValue shouldBe TrueV
    client.query(Contains(1, Arr("value0", "value1", "value2"))).futureValue shouldBe TrueV

    client.query(Contains("a" / "nested" / 0 / "path", Obj("a" -> Obj("nested" -> Arr(Obj("path" -> "value")))))).futureValue shouldBe TrueV
  }

  it should "test conversion functions" in {
    val strR = client.query(ToString(100L)).futureValue
    strR.to[String].get should equal ("100")

    val numR = client.query(ToNumber("100")).futureValue
    numR.to[Long].get should equal (100L)

    (client.query(
      Arr(ToDouble(3.14d), ToDouble(10L), ToDouble("3.14"))
    ).futureValue).to[List[Double]].get shouldBe List(3.14D, 10D, 3.14D)

    val doubleErr = client.query(ToDouble(Now())).failed.futureValue
    doubleErr shouldBe a[BadRequestException]
    doubleErr.getMessage should include ("invalid argument: Cannot cast Time to Double.")

    (client.query(
      Arr(ToInteger(10D), ToInteger(10L), ToInteger("10"))
    ).futureValue).to[List[Long]].get shouldBe List(10L, 10L, 10L)

    val integerErr = client.query(ToInteger(Now())).failed.futureValue
    integerErr shouldBe a[BadRequestException]
    integerErr.getMessage should include ("invalid argument: Cannot cast Time to Integer.")
  }

  it should "test time functions" in {
    import java.util.Calendar._

    val timeR = client.query(ToTime("1970-01-01T00:00:00Z")).futureValue
    timeR.to[TimeV].get.toInstant should equal (Instant.ofEpochMilli(0))

    val dateR = client.query(ToDate("1970-01-01")).futureValue
    dateR.to[DateV].get.localDate should equal (LocalDate.ofEpochDay(0))

    val cal = java.util.Calendar.getInstance()
    val nowStr = Time(cal.toInstant.toString)

    val secondsR = client.query(ToSeconds(Epoch(0, TimeUnit.Second))).futureValue
    secondsR.to[Long].get should equal (0)

    val toMillisR = client.query(ToMillis(nowStr)).futureValue
    toMillisR.to[Long].get should equal (cal.getTimeInMillis)

    val toMicrosR = client.query(ToMicros(Epoch(1552733214259L, TimeUnit.Second))).futureValue
    toMicrosR.to[Long].get should equal (1552733214259000000L)

    val dayOfYearR = client.query(DayOfYear(nowStr)).futureValue
    dayOfYearR.to[Long].get should equal (cal.get(DAY_OF_YEAR))

    val dayOfMonthR = client.query(DayOfMonth(nowStr)).futureValue
    dayOfMonthR.to[Long].get should equal (cal.get(DAY_OF_MONTH))

    val dayOfWeekR = client.query(DayOfWeek(nowStr)).futureValue
    dayOfWeekR.to[Long].get should equal (cal.get(DAY_OF_WEEK)-1)

    val yearR = client.query(Year(nowStr)).futureValue
    yearR.to[Long].get should equal (cal.get(YEAR))

    val monthR = client.query(Month(nowStr)).futureValue
    monthR.to[Long].get should equal (cal.get(MONTH)+1)

    val hourR = client.query(Hour(Epoch(0, TimeUnit.Second))).futureValue
    hourR.to[Long].get should equal (0)

    val minuteR = client.query(Minute(nowStr)).futureValue
    minuteR.to[Long].get should equal (cal.get(MINUTE))

    val secondR = client.query(query.Second(nowStr)).futureValue
    secondR.to[Long].get should equal (cal.get(SECOND))
  }

  it should "test date and time functions" in {
    val timeR = client.query(Time("1970-01-01T00:00:00-04:00")).futureValue
    timeR.to[TimeV].get.toInstant shouldBe Instant.ofEpochMilli(0).plus(4, ChronoUnit.HOURS)
    timeR.to[Instant].get shouldBe Instant.ofEpochMilli(0).plus(4, ChronoUnit.HOURS)

    val epochR = client.query(Arr(
      Epoch(2, TimeUnit.Day),
      Epoch(1, TimeUnit.HalfDay),
      Epoch(12, TimeUnit.Hour),
      Epoch(30, TimeUnit.Minute),
      Epoch(30, TimeUnit.Second),
      Epoch(10, TimeUnit.Millisecond),
      Epoch(42, TimeUnit.Nanosecond),
      Epoch(40, TimeUnit.Microsecond)
    )).futureValue

    epochR.collect(Field.to[Instant]).get.sorted shouldBe Seq(
      Instant.ofEpochMilli(0).plus(42, ChronoUnit.NANOS),
      Instant.ofEpochMilli(0).plus(40, ChronoUnit.MICROS),
      Instant.ofEpochMilli(0).plus(10, ChronoUnit.MILLIS),
      Instant.ofEpochMilli(0).plus(30, ChronoUnit.SECONDS),
      Instant.ofEpochMilli(0).plus(30, ChronoUnit.MINUTES),
      Instant.ofEpochMilli(0).plus(12, ChronoUnit.HOURS),
      Instant.ofEpochMilli(0).plus(1, ChronoUnit.HALF_DAYS),
      Instant.ofEpochMilli(0).plus(2, ChronoUnit.DAYS)
    )

    epochR(0).to[TimeV].get.toInstant shouldBe Instant.ofEpochMilli(0).plus(2, ChronoUnit.DAYS)
    epochR(0).to[Instant].get shouldBe Instant.ofEpochMilli(0).plus(2, ChronoUnit.DAYS)

    val dateR = client.query(query.Date("1970-01-02")).futureValue
    dateR.to[DateV].get.localDate shouldBe LocalDate.ofEpochDay(1)
    dateR.to[LocalDate].get shouldBe LocalDate.ofEpochDay(1)
  }

  it should "test date and time match functions" in {
    val addTimeR = client.query(TimeAdd(Epoch(0, TimeUnit.Second), 1, TimeUnit.Day)).futureValue
    addTimeR.to[Instant].get shouldBe Instant.ofEpochMilli(0).plus(1, ChronoUnit.DAYS)

    val addDateR = client.query(TimeAdd(Date("1970-01-01"), 1, TimeUnit.Day)).futureValue
    addDateR.to[LocalDate].get shouldBe LocalDate.ofEpochDay(1)

    val subTimeR = client.query(TimeSubtract(Epoch(0, TimeUnit.Second), 1, TimeUnit.Day)).futureValue
    subTimeR.to[Instant].get shouldBe Instant.ofEpochMilli(0).minus(1, ChronoUnit.DAYS)

    val subDateR = client.query(TimeSubtract(Date("1970-01-01"), 1, TimeUnit.Day)).futureValue
    subDateR.to[LocalDate].get shouldBe LocalDate.ofEpochDay(-1)

    val diffTimeR = client.query(TimeDiff(Epoch(0, TimeUnit.Second), Epoch(1, TimeUnit.Second), TimeUnit.Second)).futureValue
    diffTimeR.to[Long].get should equal (1)

    val diffDateR = client.query(TimeDiff(Date("1970-01-01"), Date("1970-01-02"), TimeUnit.Day)).futureValue
    diffDateR.to[Long].get should equal (1)
  }

  it should "test now function" in {
    client.query(Equals(Now(), Time("now"))).futureValue shouldBe TrueV

    val t1 = client.query(Now()).futureValue
    val t2 = client.query(Now()).futureValue
    client.query(LTE(t1, t2, Now())).futureValue shouldBe TrueV
  }

   it should "test authentication functions" in {
      val createR = client.query(Create(Collection("spells"), Obj("credentials" -> Obj("password" -> "abcdefg")))).futureValue

      // Login
      val loginR = client.query(Login(createR(RefField), Obj("password" -> "abcdefg"))).futureValue
      val secret = loginR(SecretField).get

      // HasIdentity
      val hasIdentity = client.sessionWith(secret)(_.query(HasIdentity())).futureValue
      hasIdentity.to[Boolean].get shouldBe true

     // HasCurrentIdentity
     val hasCurrentIdentity = client.sessionWith(secret)(_.query(HasCurrentIdentity())).futureValue
     hasCurrentIdentity.to[Boolean].get shouldBe true

      // Identity
      val identity = client.sessionWith(secret)(_.query(Identity())).futureValue
      identity.to[RefV].get shouldBe createR(RefField).get

     // CurrentIdentity
     val currentIdentity = client.sessionWith(secret)(_.query(CurrentIdentity())).futureValue
     currentIdentity.to[RefV].get shouldBe createR(RefField).get

      // Logout
      val loggedOut = client.sessionWith(secret)(_.query(Logout(false))).futureValue
      loggedOut.to[Boolean].get shouldBe true

      // Identify
      val identifyR = client.query(Identify(createR(RefField), "abcdefg")).futureValue
      identifyR.to[Boolean].get shouldBe true
   }

  it should "create_access_provider successful creation" in {
    val roleName = "my_role" + aRandomString
    val providerName = "my_provider" + aRandomString
    val issuerName = "my_issuer" + aRandomString
    val fullUri = s"https://${aRandomString}.auth0.com"

    val roleV = adminClient.query(CreateRole(Obj(
      "name" -> roleName,
      "privileges" -> Obj(
        "resource" -> Databases(),
        "actions" -> Obj("read" -> true)
      )
    ))).futureValue

    val providerV = adminClient.query(CreateAccessProvider(Obj(
      "name" -> providerName,
      "issuer" -> issuerName,
      "jwks_uri" -> fullUri,
      "roles" -> Arr(Role(roleName))))
    ).futureValue

    providerV("roles").to[Seq[RefV]].get should contain theSameElementsAs(Vector(roleV(RefField).get))
    providerV("audience").toOpt shouldBe defined
    providerV("name").to[String].get shouldBe(providerName)
    providerV("issuer").to[String].get shouldBe(issuerName)
    providerV("jwks_uri").to[String].get shouldBe(fullUri)

    // Cleanup
    adminClient.query(Delete(AccessProvider(providerName))).futureValue
  }

  it should "create_access_provider fails with non-unique issuer" in {
    val roleName = "my_role" + aRandomString
    val providerName = "my_provider" + aRandomString
    val issuerName = "my_issuer" + aRandomString
    val fullUri = s"https://${aRandomString}.auth0.com"
    adminClient.query(CreateRole(Obj(
      "name" -> roleName,
      "privileges" -> Obj(
        "resource" -> Databases(),
        "actions" -> Obj("read" -> true)
      )
    ))).futureValue

    val providerV = adminClient.query(CreateAccessProvider(Obj(
      "name" -> providerName,
      "issuer" -> issuerName,
      "jwks_uri" -> fullUri,
      "roles" -> Arr(Role(roleName))))
    ).futureValue

    providerV("roles").to[Seq[RefV]].get.size shouldBe 1

    // Create provider with duplicate issuer value
    val error = adminClient.query(CreateAccessProvider(Obj(
      "name" -> "duplicate_provider",
      "issuer" -> issuerName,
      "jwks_uri" -> "https://db.fauna.com",
      "roles" -> Arr(Role(roleName))))
    ).failed.futureValue

    error shouldBe a[BadRequestException]

    // Cleanup
    adminClient.query(Delete(AccessProvider(providerName))).futureValue
  }

  it should "create_access_provider fails without issuer" in {
    val roleName = "my_role" + aRandomString
    val providerName = "my_provider" + aRandomString
    val fullUri = s"https://${aRandomString}.auth0.com"
    adminClient.query(CreateRole(Obj(
      "name" -> roleName,
      "privileges" -> Obj(
        "resource" -> Databases(),
        "actions" -> Obj("read" -> true)
      )
    ))).futureValue

    val error = adminClient.query(CreateAccessProvider(Obj(
      "name" -> providerName,
      "jwks_uri" -> fullUri,
      "roles" -> Arr(Role(roleName))))
    ).failed.futureValue

    error shouldBe a[BadRequestException]
  }

  it should "create_access_provider fails without name" in {
    val roleName = "my_role" + aRandomString
    val issuerName = "my_issuer" + aRandomString
    val fullUri = s"https://${aRandomString}.auth0.com"

    val roleV = adminClient.query(CreateRole(Obj(
      "name" -> roleName,
      "privileges" -> Obj(
        "resource" -> Databases(),
        "actions" -> Obj("read" -> true)
      )
    ))).futureValue

    val error = adminClient.query(CreateAccessProvider(Obj(
      "issuer" -> issuerName,
      "jwks_uri" -> fullUri,
      "roles" -> Arr(Role(roleName))))
    ).failed.futureValue

    error shouldBe a[BadRequestException]
  }

  it should "create_access_provider fails with invalid URI" in {
    val roleName = "my_role" + aRandomString
    val providerName = "my_provider" + aRandomString
    val issuerName = "my_issuer" + aRandomString
    val fullUri = aRandomString

    adminClient.query(CreateRole(Obj(
      "name" -> roleName,
      "privileges" -> Obj(
        "resource" -> Databases(),
        "actions" -> Obj("read" -> true)
      )
    ))).futureValue

    val error = adminClient.query(CreateAccessProvider(Obj(
      "name" -> providerName,
      "issuer" -> issuerName,
      "jwks_uri" -> fullUri, // not a valid URI
      "roles" -> Arr(Role(roleName))))
    ).failed.futureValue

    error shouldBe a[BadRequestException]
  }

  it should "retrieve an existing access provider" in {
    // Set up
    val providerName = aRandomString
    val issuer = aRandomString
    val jwksUri = "https://xxxx.auth0.com/"

    adminClient.query(
      CreateAccessProvider(
        Obj(
          "name" -> providerName,
          "issuer" -> issuer,
          "jwks_uri" -> jwksUri
        )
      )
    ).futureValue

    // Run
    val accessProvider = adminClient.query(Get(AccessProvider(providerName))).futureValue

    // Verify
    accessProvider("ref").toOpt shouldBe defined
    accessProvider("ts").toOpt shouldBe defined
    accessProvider("name").to[String].get shouldBe providerName
    accessProvider("issuer").to[String].get shouldBe issuer
    accessProvider("jwks_uri").to[String].get shouldBe jwksUri

    // Cleanup
    adminClient.query(Delete(AccessProvider(providerName))).futureValue
  }

  it should "retrieve all existing access providers" in {
    // Set up
    val jwksUri = "https://xxxx.auth0.com/"
    val providerName1 = aRandomString
    val providerName2 = aRandomString

    val accessProvider1 =
      adminClient.query(
        CreateAccessProvider(
          Obj(
            "name" -> providerName1,
            "issuer" -> aRandomString,
            "jwks_uri" -> jwksUri
          )
        )
      ).futureValue

    val accessProvider2 =
      adminClient.query(
        CreateAccessProvider(
          Obj(
            "name" -> providerName2,
            "issuer" -> aRandomString,
            "jwks_uri" -> jwksUri
          )
        )
      ).futureValue

    // Run
    val accessProviders = adminClient.query(Paginate(AccessProviders())).futureValue

    // Verify
    val expectedAccessProvidersRefs = Seq(accessProvider1(RefField).get, accessProvider2(RefField).get)
    accessProviders("data").to[ArrayV].get.elems should contain theSameElementsAs expectedAccessProvidersRefs

    // Cleanup
    adminClient.query(Delete(AccessProvider(providerName1))).futureValue
    adminClient.query(Delete(AccessProvider(providerName2))).futureValue
  }

  it should "return true when querying HasCurrentToken when authenticated with an internal token" in {
    // Setup
    val collName = aRandomString
    client.query(CreateCollection(Obj("name" -> collName))).futureValue
    val createR = client.query(Create(Collection(collName), Obj("credentials" -> Obj("password" -> "abcdefg")))).futureValue
    val loginR = client.query(Login(createR(RefField), Obj("password" -> "abcdefg"))).futureValue
    val secret = loginR(SecretField).get

    // Run
    val hasCurrentToken = client.sessionWith(secret)(_.query(HasCurrentToken())).futureValue

    // Verify
    hasCurrentToken.to[Boolean].get shouldBe true
  }

  it should "return false when querying HasCurrentToken when not authenticated with an internal key" in {
    // Setup
    val collName = aRandomString
    client.query(CreateCollection(Obj("name" -> collName))).futureValue
    val createR = client.query(Create(Collection(collName), Obj("credentials" -> Obj("password" -> "abcdefg")))).futureValue
    val loginR = client.query(Login(createR(RefField), Obj("password" -> "abcdefg"))).futureValue
    val secret = loginR(SecretField).get

    // Run
    val hasNotCurrentToken = client.query(HasCurrentToken()).futureValue

    // Verify
    hasNotCurrentToken.to[Boolean].get shouldBe false
  }

  it should "test CurrentToken with internal token" in {
    // Setup
    val collName = aRandomString
    client.query(CreateCollection(Obj("name" -> collName))).futureValue
    val createR = client.query(Create(Collection(collName), Obj("credentials" -> Obj("password" -> "abcdefg")))).futureValue
    val loginR = client.query(Login(createR(RefField), Obj("password" -> "abcdefg"))).futureValue
    val secret = loginR(SecretField).get
    val tokenRef = loginR(RefField).get

    // Run
    val currentToken = client.sessionWith(secret)(_.query(CurrentToken())).futureValue

    // Verify
    currentToken.to[RefV].get shouldBe tokenRef
  }

  it should "test CurrentToken with internal key" in {
    val clientKey = adminClient.query(CreateKey(Obj("role" -> "client"))).futureValue
    val clientKeyClient = FaunaClient(secret = clientKey(SecretField).get, endpoint = config("root_url"))
    val currentToken = clientKeyClient.query(CurrentToken()).futureValue
    currentToken.to[RefV].get shouldBe clientKey(RefField).get

  }

  it should "create session client" in {
    val otherClient = client.sessionClient(config("root_token"))

    otherClient.query("echo string").futureValue.to[String].get shouldBe "echo string"
  }

  it should "find key by secret" in {
    val key = rootClient.query(CreateKey(Obj("database" -> Database(testDbName), "role" -> "admin"))).futureValue

    rootClient.query(KeyFromSecret(key(SecretField).get)).futureValue should equal (rootClient.query(Get(key(RefField).get)).futureValue)
  }

  it should "create a function" in {
    val query = Query((a, b) => Concat(Arr(a, b), "/"))

    client.query(CreateFunction(Obj("name" -> "a_function", "body" -> query))).futureValue

    (client.query(Exists(Function("a_function"))).futureValue).to[Boolean].get shouldBe true
  }

  it should "call a function" in  {
    val query = Query((a, b) => Concat(Arr(a, b), "/"))

    client.query(CreateFunction(Obj("name" -> "concat_with_slash", "body" -> query))).futureValue

    (client.query(Call(Function("concat_with_slash"), "a", "b")).futureValue).to[String].get shouldBe "a/b"
  }

  it should "parse function errors" in {
    client.query(
      CreateFunction(Obj(
        "name" -> "function_with_abort",
        "body" -> Query(Lambda("_", Abort("this function failed")))
      ))
    ).futureValue

    val err = client.query(Call(Function("function_with_abort"))).failed.futureValue

    err shouldBe a[BadRequestException]
    err.getMessage should include(
      "call error: Calling the function resulted in an error.")

    val cause = err.asInstanceOf[BadRequestException].errors.head.cause.head
    cause.code shouldEqual "transaction aborted"
    cause.description shouldEqual "this function failed"
  }

  it should "create a role" in {
    val name = s"a_role_$aRandomString"

    rootClient.query(CreateRole(Obj(
      "name" -> name,
      "privileges" -> Obj(
        "resource" -> Collections(),
        "actions" -> Obj("read" -> true)
      )
    ))).futureValue

    (rootClient.query(Exists(Role(name))).futureValue).to[Boolean].get shouldBe true
  }

  it should "read a role from a nested database" in {
    val childCli = createNewDatabase(adminClient, "db-for-roles")

    childCli.query(CreateRole(Obj(
      "name" -> "a_role",
      "privileges" -> Obj(
        "resource" -> Collections(),
        "actions" -> Obj("read" -> true)
      )
    ))).futureValue

    (childCli.query(Paginate(Roles())).futureValue).apply(PageRefs).get shouldBe Seq(RefV("a_role", Native.Roles))
    (adminClient.query(Paginate(Roles(Database("db-for-roles")))).futureValue).apply(PageRefs).get shouldBe
      Seq(RefV("a_role", Native.Roles, RefV("db-for-roles", Native.Databases)))
  }

  it should "move database" in {
    val db1Name = aRandomString(size = 10)
    val db2Name = aRandomString(size = 10)
    val clsName = aRandomString(size = 10)

    val db1 = createNewDatabase(adminClient, db1Name)
    val db2 = createNewDatabase(adminClient, db2Name)
    db2.query(CreateCollection(Obj("name" -> clsName))).futureValue

    (db1.query(Paginate(Databases())).futureValue).apply(PageRefs).get shouldBe empty

    adminClient.query(MoveDatabase(Database(db2Name), Database(db1Name))).futureValue

    (db1.query(Paginate(Databases())).futureValue).apply(PageRefs).get shouldBe List(RefV(db2Name, Native.Databases))
    (db1.query(Paginate(Collections(Database(db2Name)))).futureValue).apply(PageRefs).get shouldBe List(RefV(clsName, Native.Collections, RefV(db2Name, Native.Databases)))
  }

  case class Spell(name: String, element: Either[String, Seq[String]], cost: Option[Long])

  implicit val spellCodec: Codec[Spell] = Codec.caseClass[Spell]

  it should "encode/decode user classes" in {
    val masterSummon = Spell("Master Summon", Left("wind"), None)
    val magicMissile = Spell("Magic Missile", Left("arcane"), Some(10))
    val faerieFire = Spell("Faerie Fire", Right(Seq("arcane", "nature")), Some(10))

    val masterSummonCreated = client.query(Create(Collection("spells"), Obj("data" -> masterSummon))).futureValue
    masterSummonCreated("data").to[Spell].get shouldBe masterSummon

    val spells = client.query(Map(Paginate(Match(Index("spells_by_element"), "arcane")), Lambda(x => Select("data", Get(x))))).futureValue
    spells("data").get.to[Set[Spell]].get shouldBe Set(magicMissile, faerieFire)
  }

  it should "create collection in a nested database" in {
    val client1 = createNewDatabase(adminClient, "parent-database")
    createNewDatabase(client1, "child-database")

    val key = client1.query(CreateKey(Obj("database" -> Database("child-database"), "role" -> "server"))).futureValue

    val client2 = FaunaClient(secret = key(SecretField).get, endpoint = config("root_url"))

    client2.query(CreateCollection(Obj("name" -> "a_collection"))).futureValue

    val nestedDatabase = Database("child-database", Database("parent-database"))

    (client.query(Exists(Collection("a_collection", nestedDatabase))).futureValue).to[Boolean].get shouldBe true

    val allCollections = client.query(Paginate(Collections(nestedDatabase))).futureValue
    allCollections("data").to[List[RefV]].get shouldBe List(RefV("a_collection", Native.Collections, RefV("child-database", Native.Databases, RefV("parent-database", Native.Databases))))
  }

  it should "retrieve keys created for a child database" in {
    // Set up
    val parentDatabaseName = aRandomString
    val client = createNewDatabase(adminClient, parentDatabaseName)

    val childDatabaseName = aRandomString
    client.query(CreateDatabase(Obj("name" -> childDatabaseName))).futureValue

    val serverKey = (client.query(CreateKey(Obj("database" -> Database(childDatabaseName), "role" -> "server"))).futureValue).apply(RefField).get
    val adminKey = (client.query(CreateKey(Obj("database" -> Database(childDatabaseName), "role" -> "admin"))).futureValue).apply(RefField).get

    // Run
    val keys = client.query(Paginate(Keys())).futureValue

    // Verify
    val expectedKeys = Seq(serverKey, adminKey)
    keys("data").to[List[Value]].get should contain theSameElementsAs expectedKeys
  }

  it should "retrieve keys created for a child database from a given database defined by the scope param" in {
    // Set up
    val parentDatabaseName = aRandomString
    val client = createNewDatabase(adminClient, parentDatabaseName)

    val childDatabaseName = aRandomString
    client.query(CreateDatabase(Obj("name" -> childDatabaseName))).futureValue

    val serverKey = (client.query(CreateKey(Obj("database" -> Database(childDatabaseName), "role" -> "server"))).futureValue).apply(RefField).get
    val adminKey = (client.query(CreateKey(Obj("database" -> Database(childDatabaseName), "role" -> "admin"))).futureValue).apply(RefField).get

    // Run
    val keys = adminClient.query(Paginate(Keys(Database(parentDatabaseName)))).futureValue

    // Verify
    val expectedKeys =
      Seq(serverKey, adminKey).map { key =>
        def addDatabaseScope(ref: RefV, database: RefV): RefV = ref.copy(database = Some(database))
        key.copy(collection = key.collection.map(collection => addDatabaseScope(collection, RefV(parentDatabaseName, Native.Databases))))
      }

    keys("data").to[List[Value]].get shouldBe expectedKeys.toList
  }

  it should "create recursive refs from string" in {
    client.query(Ref("collections/widget/123")).futureValue shouldBe RefV("123", RefV("widget", Native.Collections))
  }

  it should "not break do with one element" in {
    (client.query(Do(1)).futureValue).to[Long].get shouldBe 1
    (client.query(Do(1, 2)).futureValue).to[Long].get shouldBe 2
    (client.query(Do(Arr(1, 2))).futureValue).to[Array[Long]].get shouldBe Array(1L, 2L)
  }

  it should "parse complex index" in {
    client.query(CreateCollection(Obj("name" -> "reservations"))).futureValue

    val index = client.query(CreateIndex(Obj(
      "name" -> "reservations_by_lastName",
      "source" -> Obj(
        "class" -> Class("reservations"),
        "fields" -> Obj(
          "cfLastName" -> Query(Lambda(x => Casefold(Select(Path("data", "guestInfo", "lastName"), x)))),
          "fActive" -> Query(Lambda(x => Select(Path("data", "active"), x)))
        )
      ),
      "terms" -> Arr(Obj("binding" -> "cfLastName"), Obj("binding" -> "fActive")),
      "values" -> Arr(
        Obj("field" -> Arr("data", "checkIn")),
        Obj("field" -> Arr("data", "checkOut")),
        Obj("field" -> Arr("ref"))
      ),
      "active" -> true
    ))).futureValue

    index("name").get shouldBe StringV("reservations_by_lastName")
  }

  it should "merge objects" in {
    //empty object
    client.query(
      Merge(
        Obj(),
        Obj("x" -> 10, "y" -> 20)
      )
    ).futureValue shouldBe ObjectV("x" -> 10, "y" -> 20)

    //adds field
    client.query(
      Merge(
        Obj("x" -> 10, "y" -> 20),
        Obj("z" -> 30)
      )
    ).futureValue shouldBe ObjectV("x" -> 10, "y" -> 20, "z" -> 30)

    //replace field
    client.query(
      Merge(
        Obj("x" -> 10, "y" -> 20, "z" -> -1),
        Obj("z" -> 30)
      )
    ).futureValue shouldBe ObjectV("x" -> 10, "y" -> 20, "z" -> 30)

    //remove field
    client.query(
      Merge(
        Obj("x" -> 10, "y" -> 20, "z" -> -1),
        Obj("z" -> Null())
      )
    ).futureValue shouldBe ObjectV("x" -> 10, "y" -> 20)

    //merge multiple objects
    client.query(
      Merge(
        Obj(),
        Arr(
          Obj("x" -> 10),
          Obj("y" -> 20),
          Obj("z" -> 30)
        )
      )
    ).futureValue shouldBe ObjectV("x" -> 10, "y" -> 20, "z" -> 30)

    //ignore left value
    client.query(
      Merge(
        Obj("x" -> 10, "y" -> 20),
        Obj("x" -> 100, "y" -> 200),
        Lambda((key, left, right) => right)
      )
    ).futureValue shouldBe ObjectV("x" -> 100, "y" -> 200)

    //ignore right value
    client.query(
      Merge(
        Obj("x" -> 10, "y" -> 20),
        Obj("x" -> 100, "y" -> 200),
        Lambda((key, left, right) => left)
      )
    ).futureValue shouldBe ObjectV("x" -> 10, "y" -> 20)

    //lambda 1-arity -> return [key, leftValue, rightValue]
    client.query(
      Merge(
        Obj("x" -> 10, "y" -> 20),
        Obj("x" -> 100, "y" -> 200),
        Lambda(value => value)
      )
    ).futureValue shouldBe ObjectV("x" -> ArrayV("x", 10, 100), "y" -> ArrayV("y", 20, 200))
  }

  it should "to_object" in {
    client.query(ToObject(Seq(("k0", 10), ("k1", 20)))).futureValue shouldBe ObjectV("k0" -> 10, "k1" -> 20)

    val collName = aRandomString
    val indexName = aRandomString
    client.query(CreateCollection(Obj("name" -> collName))).futureValue
    client.query(CreateIndex(Obj(
      "name" -> indexName,
      "source" -> Collection(collName),
      "active" -> true,
      "values" -> Arr(
        Obj("field" -> Arr("data", "key")),
        Obj("field" -> Arr("data", "value"))
      )
    ))).futureValue

    client.query(Do(
      Create(Collection(collName), Obj("data" -> Obj("key" -> "k0", "value" -> 10))),
      Create(Collection(collName), Obj("data" -> Obj("key" -> "k1", "value" -> 20)))
    )).futureValue

    client.query(ToObject(Select("data", Paginate(Match(Index(indexName)))))).futureValue shouldBe ObjectV("k0" -> 10, "k1" -> 20)
  }

  it should "to_array" in {
    client.query(ToArray(Obj("k0" -> 10, "k1" -> 20))).futureValue shouldBe ArrayV(("k0", 10), ("k1", 20))
  }

  it should "to_object/to_array" in {
    val obj = ObjectV("k0" -> 10, "k1" -> 20)

    client.query(ToObject(ToArray(obj))).futureValue shouldBe obj
  }

  it should "reduce collections" in {
    val collName = aRandomString
    val indexName = aRandomString

    val values = Arr((1 to 100).map(i => i: Expr): _*)

    client.query(CreateCollection(Obj("name" -> collName))).futureValue
    client.query(CreateIndex(Obj(
      "name" -> indexName,
      "source" -> Collection(collName),
      "active" -> true,
       "values" -> Arr(
         Obj("field" -> Arr("data", "value")),
         Obj("field" -> Arr("data", "foo"))
       )
    ))).futureValue

    client.query(
      Foreach(
        values,
        Lambda(i => Create(Collection(collName), Obj(
          "data" -> Obj("value" -> i, "foo" -> "bar")
        )))
      )
    ).futureValue

    //array
    (client.query(
      Reduce(
        Lambda((acc, i) => Add(acc, i)),
        10,
        values
      )
    ).futureValue).to[Long].get shouldBe 5060L

    //page
    (client.query(
      Reduce(
        Lambda((acc, i) => Add(acc, Select(0, i))),
        10,
        Paginate(Match(Index(indexName)), size = 100)
      )
    ).futureValue).apply("data").to[Seq[Long]].get shouldBe Seq(5060L)

    //set
    (client.query(
      Reduce(
        Lambda((acc, i) => Add(acc, Select(0, i))),
        10,
        Match(Index(indexName))
      )
    ).futureValue).to[Long].get shouldBe 5060L
  }

  it should "count/sum/mean collections" in {
    val collName = aRandomString
    val indexName = aRandomString

    val values = Arr((1 to 100).map(i => i: Expr): _*)

    client.query(CreateCollection(Obj("name" -> collName))).futureValue
    client.query(CreateIndex(Obj(
      "name" -> indexName,
      "source" -> Collection(collName),
      "active" -> true,
      "values" -> Arr(
        Obj("field" -> Arr("data", "value"))
      )
    ))).futureValue

    client.query(
      Foreach(
        values,
        Lambda(i => Create(Collection(collName), Obj(
          "data" -> Obj("value" -> i)
        )))
      )
    ).futureValue

    //array
    client.query(
      Arr(
        Sum(values),
        Count(values),
        Mean(values)
      )
    ).futureValue shouldBe ArrayV(5050L, 100L, 50.5d)

    //sets
    client.query(
      Arr(
        Sum(Match(Index(indexName))),
        Count(Match(Index(indexName))),
        Mean(Match(Index(indexName)))
      )
    ).futureValue shouldBe ArrayV(5050L, 100L, 50.5d)

    //pages
    client.query(
      Arr(
        Select(Path("data", 0), Sum(Paginate(Match(Index(indexName)), size = 100))),
        Select(Path("data", 0), Count(Paginate(Match(Index(indexName)), size = 100))),
        Select(Path("data", 0), Mean(Paginate(Match(Index(indexName)), size = 100)))
      )
    ).futureValue shouldBe ArrayV(5050L, 100L, 50.5d)
  }

  it should "all/any" in {
    val coll = client.query(CreateCollection(Obj("name" -> aRandomString))).futureValue
    val index = client.query(CreateIndex(Obj(
      "name" -> aRandomString,
      "source" -> coll("ref"),
      "active" -> true,
      "terms" -> Arr(Obj("field" -> Arr("data", "foo"))),
      "values" -> Arr(Obj("field" -> Arr("data", "value")))
    ))).futureValue

    client.query(Do(
      Create(coll("ref"), Obj("data" -> Obj("foo" -> "true", "value" -> true))),
      Create(coll("ref"), Obj("data" -> Obj("foo" -> "true", "value" -> true))),
      Create(coll("ref"), Obj("data" -> Obj("foo" -> "true", "value" -> true))),

      Create(coll("ref"), Obj("data" -> Obj("foo" -> "false", "value" -> false))),
      Create(coll("ref"), Obj("data" -> Obj("foo" -> "false", "value" -> false))),
      Create(coll("ref"), Obj("data" -> Obj("foo" -> "false", "value" -> false))),

      Create(coll("ref"), Obj("data" -> Obj("foo" -> "mixed", "value" -> true))),
      Create(coll("ref"), Obj("data" -> Obj("foo" -> "mixed", "value" -> false))),
      Create(coll("ref"), Obj("data" -> Obj("foo" -> "mixed", "value" -> true))),
      Create(coll("ref"), Obj("data" -> Obj("foo" -> "mixed", "value" -> false)))
    )).futureValue

    //all: array
    client.query(All(Arr(true, true, true))).futureValue shouldBe TrueV
    client.query(All(Arr(true, false, true))).futureValue shouldBe FalseV

    //all: page
    client.query(All(Paginate(Match(index("ref"), "true")))).futureValue shouldBe ObjectV("data" -> ArrayV(TrueV))
    client.query(All(Paginate(Match(index("ref"), "false")))).futureValue shouldBe ObjectV("data" -> ArrayV(FalseV))
    client.query(All(Paginate(Match(index("ref"), "mixed")))).futureValue shouldBe ObjectV("data" -> ArrayV(FalseV))

    //all: set
    client.query(All(Match(index("ref"), "true"))).futureValue shouldBe TrueV
    client.query(All(Match(index("ref"), "false"))).futureValue shouldBe FalseV
    client.query(All(Match(index("ref"), "mixed"))).futureValue shouldBe FalseV

    //any: array
    client.query(Any(Arr(false, false, false))).futureValue shouldBe FalseV
    client.query(Any(Arr(true, false, true))).futureValue shouldBe TrueV

    //any: page
    client.query(Any(Paginate(Match(index("ref"), "true")))).futureValue shouldBe ObjectV("data" -> ArrayV(TrueV))
    client.query(Any(Paginate(Match(index("ref"), "false")))).futureValue shouldBe ObjectV("data" -> ArrayV(FalseV))
    client.query(Any(Paginate(Match(index("ref"), "mixed")))).futureValue shouldBe ObjectV("data" -> ArrayV(TrueV))

    //any: set
    client.query(Any(Match(index("ref"), "true"))).futureValue shouldBe TrueV
    client.query(Any(Match(index("ref"), "false"))).futureValue shouldBe FalseV
    client.query(Any(Match(index("ref"), "mixed"))).futureValue shouldBe TrueV
  }

  it should "range" in {
    val col = client.query(CreateCollection(Obj("name" -> aRandomString(size = 10)))).futureValue

    val index = client.query(CreateIndex(Obj(
      "name" -> aRandomString(size = 10),
      "source" -> col("ref").get,
      "values" -> Arr(
        Obj("field" -> Arr("data", "value")),
        Obj("field" -> "ref")
      ),
      "active" -> true
    ))).futureValue

    client.query(Foreach(
      (1 to 20).toList,
      Lambda(i =>
        Create(
          col("ref").get,
          Obj("data" -> Obj("value" -> i))
        )
      )
    )).futureValue

    def query(set: Expr) =
      (client.query(Select("data",
        Map(Paginate(set), Lambda((value, ref) => value))
      )).futureValue).to[List[Int]].get

    val m = Match(index("ref").get)
    query(Range(m, 3, 7)) shouldBe (3 to 7).toList
    query(Union(Range(m, 1, 10), Range(m, 11, 20))) shouldBe (1 to 20).toList
    query(Difference(Range(m, 1, 20), Range(m, 11, 20))) shouldBe (1 to 10).toList
    query(Intersection(Range(m, 1, 20), Range(m, 5, 15))) shouldBe (5 to 15).toList
  }

  it should "type check" in {
    val coll = (client.query(CreateCollection(Obj("name" -> aRandomString))).futureValue).apply("ref").get
    val index = (client.query(CreateIndex(Obj("name" -> aRandomString, "source" -> coll, "active" -> true))).futureValue).apply("ref").get
    val doc = (client.query(Create(coll, Obj("credentials" -> Obj("password" -> "sekret")))).futureValue).apply("ref").get
    val db = (adminClient.query(CreateDatabase(Obj("name" -> aRandomString))).futureValue).apply("ref").get
    val fn = (client.query(CreateFunction(Obj("name" -> aRandomString, "body" -> Query(x => x)))).futureValue).apply("ref").get
    val key = (adminClient.query(CreateKey(Obj("database" -> db, "role" -> "admin"))).futureValue).apply("ref").get
    val tok = client.query(Login(doc, Obj("password" -> "sekret"))).futureValue
    val role = (adminClient.query(CreateRole(Obj("name" -> aRandomString, "membership" -> Arr(), "privileges" -> Arr()))).futureValue).apply("ref").get
    val cred = (client.sessionWith(tok(SecretField).get) { c => c.query(Get(Ref("credentials/self"))) }.futureValue).apply("ref").get
    val token = tok("ref").get

    val trueExprs = Seq(
      IsNumber(3.14),
      IsNumber(10L),
      IsDouble(3.14),
      IsInteger(10L),
      IsBoolean(true),
      IsBoolean(false),
      IsNull(Null()),
      IsBytes(BytesV(0x1, 0x2, 0x3, 0x4)),
      IsTimestamp(Now()),
      IsTimestamp(Epoch(1, TimeUnit.Second)),
      IsTimestamp(Time("1970-01-01T00:00:00Z")),
      IsDate(ToDate(Now())),
      IsDate(Date("1970-01-01")),
      IsString("string"),
      IsArray(Seq(10)),
      IsObject(Obj("x" -> 10)),
      IsObject(Paginate(Collections())),
      IsObject(Get(doc)),
      IsRef(coll),
      IsSet(Collections()),
      IsSet(Match(index)),
      IsSet(Union(Match(index))),
      IsDoc(doc),
      IsDoc(Get(doc)),
      IsLambda(Query(x => x)),
      IsCollection(coll),
      IsCollection(Get(coll)),
      IsDatabase(db),
      IsDatabase(Get(db)),
      IsIndex(index),
      IsIndex(Get(index)),
      IsFunction(fn),
      IsFunction(Get(fn)),
      IsKey(key),
      IsKey(Get(key)),
      IsToken(token),
      IsToken(Get(token)),
      IsCredentials(cred),
      IsCredentials(Get(cred)),
      IsRole(role),
      IsRole(Get(role))
    )

    adminClient.query(trueExprs).futureValue shouldBe Seq.fill(trueExprs.size)(TrueV)

    val falseExprs = Seq(
      IsNumber("string"),
      IsNumber(Arr()),
      IsDouble(10L),
      IsInteger(3.14),
      IsBoolean("string"),
      IsBoolean(10),
      IsNull("string"),
      IsBytes(Arr(0x1, 0x2, 0x3, 0x4)),
      IsTimestamp(ToDate(Now())),
      IsTimestamp(10),
      IsDate(Now()),
      IsString(Arr()),
      IsString(10),
      IsArray(Obj("x" -> 10)),
      IsObject(Arr(10)),
      IsRef(Match(index)),
      IsRef(10),
      IsSet("string"),
      IsDoc(Obj()),
      IsLambda(fn),
      IsLambda(Get(fn)),
      IsCollection(db),
      IsCollection(Get(db)),
      IsDatabase(coll),
      IsDatabase(Get(coll)),
      IsIndex(coll),
      IsIndex(Get(db)),
      IsFunction(index),
      IsFunction(Get(coll)),
      IsKey(db),
      IsKey(Get(index)),
      IsToken(index),
      IsToken(Get(cred)),
      IsCredentials(token),
      IsCredentials(Get(role)),
      IsRole(coll),
      IsRole(Get(index))
    )

    adminClient.query(falseExprs).futureValue shouldBe Seq.fill(falseExprs.size)(FalseV)
  }

  it should "retrieve documents from a collection" in {
    val coll = (client.query(CreateCollection(Obj("name" -> aRandomString))).futureValue).apply("ref").get
    for (i <- 1 to 10) client.query(Create(coll, Obj())).futureValue
    val docs = client.query(Paginate(Documents(coll))).futureValue
    docs("data").to[Seq[Value]].get should have size 10
  }

  it should "reverse an array" in {
    // Run
    val values = (1 to 10).toArray
    val result = client.query(Reverse(values)).futureValue

    // Verify
    val expectedValues = values.map(LongV(_)).reverse
    result.to[ArrayV].get.elems should contain theSameElementsInOrderAs expectedValues
  }

  it should "reverse a set" in {
    // Set up
    val collectionName = aRandomString
    client.query(CreateCollection(Obj("name" -> collectionName))).futureValue

    val indexName = aRandomString
    client.query(
      CreateIndex(Obj(
        "name" -> indexName,
        "source" -> Collection(collectionName),
        "active" -> true
      ))
    ).futureValue

    client.query(Create(Collection(collectionName), Obj())).futureValue
    client.query(Create(Collection(collectionName), Obj())).futureValue

    // Run
    val result = client.query(Paginate(Reverse(Match(Index(indexName))))).futureValue

    // Verify
    val expected = {
      val result = client.query(Paginate(Match(Index(indexName)))).futureValue
      result("data").to[Seq[Value]].get.reverse
    }

    result("data").to[Seq[Value]].get should contain theSameElementsInOrderAs expected
  }

  it should "reverse a page" in {
    // Set up
    val collectionName = aRandomString
    client.query(CreateCollection(Obj("name" -> collectionName))).futureValue

    val indexName = aRandomString
    client.query(
      CreateIndex(Obj(
        "name" -> indexName,
        "source" -> Collection(collectionName),
        "active" -> true
      ))
    ).futureValue

    client.query(Create(Collection(collectionName), Obj())).futureValue
    client.query(Create(Collection(collectionName), Obj())).futureValue

    // Run
    val result = client.query(Reverse(Paginate(Match(Index(indexName))))).futureValue

    // Verify
    val expected = {
      val result = client.query(Paginate(Match(Index(indexName)))).futureValue
      result("data").to[Seq[Value]].get.reverse
    }

    result("data").to[Seq[Value]].get should contain theSameElementsInOrderAs expected
  }

  it should "stream return error if it cannot find an instance" in {
    val err = client.stream(Get(RefV("1234", RefV("spells", Native.Collections)))).failed.futureValue
    err shouldBe a[NotFoundException]
  }

  it should "stream return error if the query is not readonly" in {
    val err = client.stream(Create(Collection("spells"), Obj("data" -> Obj("testField" -> "testValue0")))).failed.futureValue
    err shouldBe a[BadRequestException]
    err.getMessage should include("Write effect in read-only query expression.")
  }

  it should "stream on document reference contains `document` field by default" in {
    val createdDoc = client.query(Create(Collection("spells"), Obj("credentials" -> Obj("password" -> "abcdefg")))).futureValue
    val docRef = createdDoc(RefField)
    val publisherValue = client.stream(docRef).futureValue
    val events = testSubscriber(4, publisherValue)

    // push 3 updates
    client.query(Update(docRef, Obj("data" -> Obj("testField" -> "testValue1")))).futureValue
    client.query(Update(docRef, Obj("data" -> Obj("testField" -> "testValue2")))).futureValue
    client.query(Update(docRef, Obj("data" -> Obj("testField" -> "testValue3")))).futureValue

    // assertion 4 events (start + 3 updates)
    events.futureValue match {
      case startEvent :: t1 :: t2 :: t3 :: Nil =>
        startEvent("type").get shouldBe StringV("start")
        startEvent("event").toOpt.isDefined shouldBe true

        t1("type").get shouldBe StringV("version")
        t1("event", "action").get shouldBe StringV("update")
        t1("event", "document", "data").get shouldBe ObjectV("testField" -> StringV("testValue1"))
        t1("event", "prev").toOpt.isEmpty shouldBe true
        t1("event", "diff").toOpt.isEmpty shouldBe true

        t2("type").get shouldBe StringV("version")
        t2("event", "action").get shouldBe StringV("update")
        t2("event", "document", "data").get shouldBe ObjectV("testField" -> StringV("testValue2"))
        t2("event", "prev").toOpt.isEmpty shouldBe true
        t2("event", "diff").toOpt.isEmpty shouldBe true

        t3("type").get shouldBe StringV("version")
        t3("event", "action").get shouldBe StringV("update")
        t3("event", "document", "data").get shouldBe ObjectV("testField" -> StringV("testValue3"))
        t3("event", "prev").toOpt.isEmpty shouldBe true
        t3("event", "diff").toOpt.isEmpty shouldBe true
      case _ =>
        fail("expected 4 events")
    }
  }

  it should "stream on document reference with opt-in fields" in {
    val createdDoc = client.query(Create(Collection("spells"), Obj("data" -> Obj("testField" -> "testValue0")))).futureValue
    val docRef = createdDoc(RefField)
    val publisherValue = client.stream(docRef, List(DocumentField, PrevField, DiffField)).futureValue
    val events = testSubscriber(4, publisherValue)

    // push 3 updates
    client.query(Update(docRef, Obj("data" -> Obj("testField" -> "testValue1")))).futureValue
    client.query(Update(docRef, Obj("data" -> Obj("testField" -> "testValue2")))).futureValue
    client.query(Update(docRef, Obj("data" -> Obj("testField" -> "testValue3")))).futureValue

    // assertion 4 events (start + 3 updates)
    events.futureValue match {
      case startEvent :: t1 :: t2 :: t3 :: Nil =>
        startEvent("type").get shouldBe StringV("start")
        startEvent("event").toOpt.isDefined shouldBe true

        t1("type").get shouldBe StringV("version")
        t1("event", "document", "data").get shouldBe ObjectV("testField" -> StringV("testValue1"))
        t1("event", "prev", "data").get shouldBe ObjectV("testField" -> StringV("testValue0"))
        t1("event", "diff", "data").get shouldBe ObjectV("testField" -> StringV("testValue1"))

        t2("type").get shouldBe StringV("version")
        t2("event", "document", "data").get shouldBe ObjectV("testField" -> StringV("testValue2"))
        t2("event", "prev", "data").get shouldBe ObjectV("testField" -> StringV("testValue1"))
        t2("event", "diff", "data").get shouldBe ObjectV("testField" -> StringV("testValue2"))

        t3("type").get shouldBe StringV("version")
        t3("event", "document", "data").get shouldBe ObjectV("testField" -> StringV("testValue3"))
        t3("event", "prev", "data").get shouldBe ObjectV("testField" -> StringV("testValue2"))
        t3("event", "diff", "data").get shouldBe ObjectV("testField" -> StringV("testValue3"))
      case _ =>
        fail("expected 4 events")
    }
  }

  it should "stream updates last seen transaction time" in {
    val createdDoc = client.query(Create(Collection("spells"), Obj("credentials" -> Obj("password" -> "abcdefg")))).futureValue
    val docRef = createdDoc(RefField)
    val publisherValue = client.stream(docRef).futureValue

    val capturedEventsP = Promise[List[Value]]

    val valueSubscriber = new Flow.Subscriber[Value] {
      var subscription: Flow.Subscription = null
      val captured = new util.ArrayList[Value]

      override def onSubscribe(s: Flow.Subscription): Unit = {
        subscription = s
        subscription.request(1)
      }

      override def onNext(v: Value): Unit = {
        if (v("txn").to[Long].get <= client.lastTxnTime) {
          captured.add(v)
          if (captured.size() == 4) {
            capturedEventsP.success(captured.iterator().asScala.toList)
            subscription.cancel()
          } else
            subscription.request(1)
        } else
          capturedEventsP.failure(new IllegalStateException("event's txnTS did not update client's value"))
      }

      override def onError(t: Throwable): Unit = capturedEventsP.failure(t)

      override def onComplete(): Unit = capturedEventsP.failure(new IllegalStateException("not expecting the stream to complete"))
    }

    // subscribe to publisher
    publisherValue.subscribe(valueSubscriber)

    // push 3 updates
    client.query(Update(docRef, Obj("data" -> Obj("testField" -> "testValue1")))).futureValue
    client.query(Update(docRef, Obj("data" -> Obj("testField" -> "testValue2")))).futureValue
    client.query(Update(docRef, Obj("data" -> Obj("testField" -> "testValue3")))).futureValue

    // blocking
    capturedEventsP.future.futureValue
  }

  it should "stream handles authorization lost during stream evaluation." in {
    // create collection
    val collectionName = "stream-things"
    adminClient.query(CreateCollection(Obj("name" -> collectionName))).futureValue

    // create doc
    val createdDoc = adminClient.query(Create(Collection(collectionName), Obj("credentials" -> Obj("password" -> "abcdefg")))).futureValue
    val docRef = createdDoc(RefField)

    // create new key + client
    val newKey = adminClient.query(CreateKey(Obj("role" -> "server-readonly"))).futureValue
    val streamingClient = client.sessionClient(newKey(SecretField).get)

    val publisherValue = streamingClient.stream(docRef).futureValue

    val subscriberDone = Promise[Unit]

    val valueSubscriber = new Flow.Subscriber[Value] {
      var subscription: Flow.Subscription = null
      val captured = new util.ArrayList[Value]
      override def onSubscribe(s: Flow.Subscription): Unit = {
        subscription = s
        subscription.request(1)
      }
      override def onNext(v: Value): Unit = {
        if (captured.isEmpty) {
          // update doc on `start` event
          adminClient.query(Update(docRef, Obj("data" -> Obj("testField" -> "afterStart")))).futureValue

          // delete key
          adminClient.query(Delete(newKey(RefField))).futureValue

          // push an update to force auth revalidation.
          adminClient.query(Update(docRef, Obj("data" -> Obj("testField" -> "afterKeyDelete")))).futureValue
        }
        captured.add(v)          // capture element
        subscription.request(1)  // ask for more elements
      }
      override def onError(t: Throwable): Unit = subscriberDone.failure(t)
      override def onComplete(): Unit = subscriberDone.success(())
    }

    // subscribe to publisher
    publisherValue.subscribe(valueSubscriber)

    // blocking - we expect the Promise to fail because the stream's key has been deleted
    val subscriberError = subscriberDone.future.failed.futureValue
    subscriberError shouldBe a[StreamingException]
    subscriberError.getMessage should include("permission denied: Authorization lost during stream evaluation.")
  }

  it should "stream on document reference using reactive-streams & Monix" in {
    val createdDoc = client.query(Create(Collection("spells"), Obj("credentials" -> Obj("password" -> "abcdefg")))).futureValue
    val docRef = createdDoc(RefField)

    val publisherValue: Flow.Publisher[Value] = client.stream(docRef).futureValue
    val reactiveStreamsPublisher: Publisher[Value] = FlowAdapters.toPublisher(publisherValue)

    // push 3 updates
    client.query(Update(docRef, Obj("data" -> Obj("testField" -> "testValue1")))).futureValue
    client.query(Update(docRef, Obj("data" -> Obj("testField" -> "testValue2")))).futureValue
    client.query(Update(docRef, Obj("data" -> Obj("testField" -> "testValue3")))).futureValue

    // blocking
    val events = Observable.fromReactivePublisher(reactiveStreamsPublisher)
      .take(4) // 4 events (start + 3 updates)
      .toListL
      .runToFuture(Scheduler.Implicits.global)
      .futureValue

    // assertion
    events match {
      case startEvent :: t1 :: t2 :: t3 :: Nil =>
        startEvent("type").get shouldBe StringV("start")
        t1("type").get shouldBe StringV("version")
        t2("type").get shouldBe StringV("version")
        t3("type").get shouldBe StringV("version")
      case _ =>
        fail("expected 4 events")
    }
  }

  it should "stream with snapshot=true on document contains a document snapshot event" in {
    val createdDoc = client.query(Create(Collection("spells"), Obj("data" -> Obj("testField" -> "testValue0")))).futureValue
    val docRef = createdDoc(RefField)
    val publisherValue = client.stream(docRef, snapshot = true).futureValue
    val events = testSubscriber(5, publisherValue)

    // push 3 updates
    client.query(Update(docRef, Obj("data" -> Obj("testField" -> "testValue1")))).futureValue
    client.query(Update(docRef, Obj("data" -> Obj("testField" -> "testValue2")))).futureValue
    client.query(Update(docRef, Obj("data" -> Obj("testField" -> "testValue3")))).futureValue

    // assertion
    events.futureValue match {
      case startEvent :: snapshot :: t1 :: t2 :: t3 :: Nil =>
        startEvent("type").get shouldBe StringV("start")
        startEvent("event").toOpt.isDefined shouldBe true

        snapshot("type").get shouldBe StringV("snapshot")
        snapshot("txn").get shouldBe snapshot("event", "ts").get
        snapshot("event", "ref").get shouldBe docRef.get
        snapshot("event", "data").get shouldBe ObjectV("testField" -> StringV("testValue0"))

        t1("type").get shouldBe StringV("version")
        t1("event", "document", "data").get shouldBe ObjectV("testField" -> StringV("testValue1"))

        t2("type").get shouldBe StringV("version")
        t2("event", "document", "data").get shouldBe ObjectV("testField" -> StringV("testValue2"))

        t3("type").get shouldBe StringV("version")
        t3("event", "document", "data").get shouldBe ObjectV("testField" -> StringV("testValue3"))
      case _ =>
        fail("expected 4 events")
    }
  }

  it should "throw no exceptions when running 500 queries in parallel" in {
    val key = rootClient.query(CreateKey(Obj("database" -> Database(testDbName), "role" -> "admin"))).futureValue
    val clientPool = List.tabulate(10)(n => FaunaClient(endpoint = config("root_url"), secret = key(SecretField).get))

    val random = scala.util.Random
    val COLLECTION_NAME = "ParallelTestCollection"
    //create sample collection with 10 documents
    client.query(CreateCollection(Obj("name" -> COLLECTION_NAME))).futureValue
    (1 to 3).foreach(_ =>
      client.query(
          Create(Collection(COLLECTION_NAME),
            Obj("data" -> Obj("testField" -> "testValue")))).futureValue
    )

    val counter = 100
    def metricsQuery: Future[MetricsResponse] = {
      val taskClient = clientPool(random.nextInt(9))
      val result = taskClient.queryWithMetrics(
        Map(
          Paginate(Documents(Collection(COLLECTION_NAME))),
          Lambda(nextRef => Select("data", Get(nextRef)))
        ),
        None
      )
      result
    }
    def sumQuery: Future[Value] = {
      val taskClient = clientPool(random.nextInt(9))
      val values = Arr((1 to 10).map(i => i: Expr): _*)
      taskClient.query(Sum(values))
    }

    (Seq.fill(counter)(metricsQuery))
      .par
      .foreach((result: Future[MetricsResponse]) => noException should be thrownBy result.futureValue)

    (Seq.fill(counter)(sumQuery))
      .par
      .foreach((result: Future[Value]) => result.futureValue shouldBe(LongV(55)) )
  }

  def createNewDatabase(client: FaunaClient, name: String): FaunaClient = {
    client.query(CreateDatabase(Obj("name" -> name))).futureValue
    val key = client.query(CreateKey(Obj("database" -> Database(name), "role" -> "admin"))).futureValue
    client.sessionClient(key(SecretField).get)
  }

  def testSubscriber(messageCount: Int, publisher: Flow.Publisher[Value]): Future[List[Value]] = {
    val capturedEventsP = Promise[List[Value]]

    val valueSubscriber = new Flow.Subscriber[Value] {
      var subscription: Flow.Subscription = null
      val captured = new util.ArrayList[Value]

      override def onSubscribe(s: Flow.Subscription): Unit = {
        subscription = s
        subscription.request(1)
      }
      override def onNext(v: Value): Unit = {
        captured.add(v)
        if (captured.size() == messageCount) {
          capturedEventsP.success(captured.iterator().asScala.toList)
          subscription.cancel()
        } else {
          subscription.request(1)
        }
      }
      override def onError(t: Throwable): Unit = capturedEventsP.failure(t)
      override def onComplete(): Unit = capturedEventsP.failure(new IllegalStateException("not expecting the stream to complete"))
    }
    // subscribe to publisher
    publisher.subscribe(valueSubscriber)
    capturedEventsP.future
  }
}
