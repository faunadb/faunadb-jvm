package com.faunadb.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faunadb.client.query.Expr;
import com.faunadb.client.types.Value;
import com.faunadb.client.types.Value.*;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.faunadb.client.query.Language.*;
import static com.faunadb.client.query.Language.Collection;
import static com.faunadb.client.query.Language.TimeUnit.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class SerializationSpec {

  private ObjectMapper json;

  @Before
  public void setUp() {
    json = new ObjectMapper();
  }

  @Test
  public void shouldSerializeLiteralValues() throws Exception {
    assertJson(Value(Long.MAX_VALUE), String.valueOf(Long.MAX_VALUE));
    assertJson(Value("a string"), "\"a string\"");
    assertJson(Value(10), "10");
    assertJson(Value(1.0), "1.0");
    assertJson(Value(true), "true");
    assertJson(Value(false), "false");
    assertJson(Null(), "null");
  }

  @Test
  public void shouldSerializeAnArray() throws Exception {
    assertJson(
      Arr(
        Value("a string"),
        Value(10),
        Obj("data", Value("str"))
      ), "[\"a string\",10,{\"object\":{\"data\":\"str\"}}]");

    Map<String, Value> obj = new LinkedHashMap<>();
    obj.put("value", new StringV("test"));

    assertJson(
        Arr(new ObjectV(obj)),
        "[{\"object\":{\"value\":\"test\"}}]");

    assertJson(
      Arr(Arrays.asList(
        Value("other string"),
        Value(42)
      )), "[\"other string\",42]");
  }

  @Test
  public void shouldSerializeAnObject() throws Exception {
    assertJson(Obj(), "{\"object\":{}}");

    assertJson(
      Obj("k1", Value("v1")),
      "{\"object\":{\"k1\":\"v1\"}}");

    Map<String, Value> v1 = new LinkedHashMap<>();
    Map<String, Value> v2 = new LinkedHashMap<>();

    v2.put("v2", new StringV("test"));
    v1.put("v1", new ObjectV(v2));

    assertJson(
      Obj("k1", new ObjectV(v1)),
      "{\"object\":{\"k1\":{\"object\":{\"v1\":{\"object\":{\"v2\":\"test\"}}}}}}");

    assertJson(
      Obj("k1", Obj("v1", Obj("v2", Value("test")))),
      "{\"object\":{\"k1\":{\"object\":{\"v1\":{\"object\":{\"v2\":\"test\"}}}}}}");

    assertJson(Obj("k1", Value("v1"), "k2", Value("v2")),
      "{\"object\":{\"k1\":\"v1\",\"k2\":\"v2\"}}");

    assertJson(
      Obj(
        "k1", Value("v1"),
        "k2", Value("v2"),
        "k3", Value("v3")
      ),
      "{\"object\":{\"k1\":\"v1\",\"k2\":\"v2\",\"k3\":\"v3\"}}");

    assertJson(
      Obj(
        "k1", Value("v1"),
        "k2", Value("v2"),
        "k3", Value("v3"),
        "k4", Value("v4")
      ),
      "{\"object\":{\"k1\":\"v1\",\"k2\":\"v2\",\"k3\":\"v3\",\"k4\":\"v4\"}}");

    assertJson(
      Obj(
        "k1", Value("v1"),
        "k2", Value("v2"),
        "k3", Value("v3"),
        "k4", Value("v4"),
        "k5", Value("v5")
      ),
      "{\"object\":{\"k1\":\"v1\",\"k2\":\"v2\",\"k3\":\"v3\",\"k4\":\"v4\",\"k5\":\"v5\"}}");

    Map<String, Expr> kvs = new LinkedHashMap<>();

    kvs.put("k1", Value("v1"));
    kvs.put("k2", Value("v2"));

    assertJson(
      Obj(kvs),
      "{\"object\":{\"k1\":\"v1\",\"k2\":\"v2\"}}");
  }

  @Test
  public void shouldSerializeRef() throws Exception {
    assertJson(Native.COLLECTIONS, "{\"@ref\":{\"id\":\"collections\"}}");
    assertJson(new RefV("id1", new RefV("people", Native.COLLECTIONS)), "{\"@ref\":{\"id\":\"id1\",\"collection\":{\"@ref\":{\"id\":\"people\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}}");
  }

  @Test
  public void shouldSerializeCollection() throws Exception {
    assertJson(Collection(Value("spells")), "{\"collection\":\"spells\"}");
  }

  @Test
  public void shouldSerializeDatabase() throws Exception {
    assertJson(Database(Value("test-db")), "{\"database\":\"test-db\"}");
  }

  @Test
  public void shouldSerializeIndex() throws Exception {
    assertJson(Index(Value("spells_by_name")), "{\"index\":\"spells_by_name\"}");
  }

  @Test
  public void shouldSerializeInstantValue() throws Exception {
    assertJson(Value(Instant.ofEpochMilli(0)), "{\"@ts\":\"1970-01-01T00:00:00Z\"}");
  }

  @Test
  public void shouldSerializeDateValue() throws Exception {
    assertJson(Value(LocalDate.of(2015, 1, 15)), "{\"@date\":\"2015-01-15\"}");
  }

  @Test
  public void shouldSerializeBytesValue() throws Exception {
    assertJson(Value(new byte[] {0x1, 0x2, 0x3, 0x4}),
      "{\"@bytes\":\"AQIDBA==\"}");
  }

  @Test
  public void shouldSerializeBytesValueUrlSafe() throws Exception {
    assertJson(Value(new byte[] {(byte)0xf8}), "{\"@bytes\":\"-A==\"}");
    assertJson(Value(new byte[] {(byte)0xf9}), "{\"@bytes\":\"-Q==\"}");
    assertJson(Value(new byte[] {(byte)0xfa}), "{\"@bytes\":\"-g==\"}");
    assertJson(Value(new byte[] {(byte)0xfb}), "{\"@bytes\":\"-w==\"}");
    assertJson(Value(new byte[] {(byte)0xfc}), "{\"@bytes\":\"_A==\"}");
    assertJson(Value(new byte[] {(byte)0xfd}), "{\"@bytes\":\"_Q==\"}");
    assertJson(Value(new byte[] {(byte)0xfe}), "{\"@bytes\":\"_g==\"}");
    assertJson(Value(new byte[] {(byte)0xff}), "{\"@bytes\":\"_w==\"}");
  }

  @Test
  public void shouldSerializeAbort() throws Exception {
    assertJson(Abort(Value("a message")), "{\"abort\":\"a message\"}");
    assertJson(Abort("a message"), "{\"abort\":\"a message\"}");
  }

  @Test
  public void shouldSerializeLet() throws Exception {
    assertJson(
      Let(
        "v1", Obj("x1", Value("y1"))
      ).in(
        Value("x")
      ), "{\"let\":[{\"v1\":{\"object\":{\"x1\":\"y1\"}}}],\"in\":\"x\"}");

    assertJson(
      Let(
        "v1", Value("x1")
      ).in(
        Value("x")
      ), "{\"let\":[{\"v1\":\"x1\"}],\"in\":\"x\"}");

    assertJson(
      Let(
        "v1", Value("x1"),
        "v2", Value("x2")
      ).in(
        Value("x")
      ), "{\"let\":[{\"v1\":\"x1\"},{\"v2\":\"x2\"}],\"in\":\"x\"}");

    assertJson(
      Let(
        "v1", Value("x1"),
        "v2", Value("x2"),
        "v3", Value("x3")
      ).in(
        Value("x")
      ), "{\"let\":[{\"v1\":\"x1\"},{\"v2\":\"x2\"},{\"v3\":\"x3\"}],\"in\":\"x\"}");

    assertJson(
      Let(
        "v1", Value("x1"),
        "v2", Value("x2"),
        "v3", Value("x3"),
        "v4", Value("x4")
      ).in(
        Value("x")
      ), "{\"let\":[{\"v1\":\"x1\"},{\"v2\":\"x2\"},{\"v3\":\"x3\"},{\"v4\":\"x4\"}],\"in\":\"x\"}");

    assertJson(
      Let(
        "v1", Value("x1"),
        "v2", Value("x2"),
        "v3", Value("x3"),
        "v4", Value("x4"),
        "v5", Value("x5")
      ).in(
        Value("x")
      ), "{\"let\":[{\"v1\":\"x1\"},{\"v2\":\"x2\"},{\"v3\":\"x3\"},{\"v4\":\"x4\"},{\"v5\":\"x5\"}],\"in\":\"x\"}");

    Map<String, Expr> vs = new LinkedHashMap<>();

    vs.put("v1", Value("x1"));
    vs.put("v2", Value("x2"));

    assertJson(
        Let(vs).in(
        Value("x")
      ), "{\"let\":[{\"v1\":\"x1\"},{\"v2\":\"x2\"}],\"in\":\"x\"}");
  }

  @Test
  public void shouldSerializeVar() throws Exception {
    assertJson(Var("x"), "{\"var\":\"x\"}");
  }

  @Test
  public void shouldSerializeIf() throws Exception {
    assertJson(
      If(Value(true), Value(true), Value(false)),
      "{\"if\":true,\"then\":true,\"else\":false}");
  }

  @Test
  public void shouldSerializeDo() throws Exception {
    assertJson(
      Do(
        Value("x")
      ), "{\"do\":[\"x\"]}");

    assertJson(
      Do(
        If(Value(true), Value("x"), Value("y")),
        Value(42)
      ), "{\"do\":[{\"if\":true,\"then\":\"x\",\"else\":\"y\"},42]}");

    assertJson(
      Do(Arrays.asList(
        If(Value(true), Value("xx"), Value("yy")),
        Value(45)
      )), "{\"do\":[{\"if\":true,\"then\":\"xx\",\"else\":\"yy\"},45]}");

    assertJson(
      Do(Arr(
        If(Value(true), Value("xx"), Value("yy")),
        Value(45)
      )), "{\"do\":[[{\"if\":true,\"then\":\"xx\",\"else\":\"yy\"},45]]}");
  }

  @Test
  public void shouldSerializeLambda() throws Exception {
    assertJson(
      Lambda(Value("x"),
        If(Var("x"), Value(42), Value(45))
      ), "{\"lambda\":\"x\",\"expr\":{\"if\":{\"var\":\"x\"},\"then\":42,\"else\":45}}");

    assertJson(
      Lambda(Arr(Value("x"), Value("_")),
        If(Var("x"), Value(42), Value(45))
      ), "{\"lambda\":[\"x\",\"_\"],\"expr\":{\"if\":{\"var\":\"x\"},\"then\":42,\"else\":45}}");

    assertJson(
      Lambda("x",
        If(Var("x"), Value(42), Value(45))
      ), "{\"lambda\":\"x\",\"expr\":{\"if\":{\"var\":\"x\"},\"then\":42,\"else\":45}}");

  }

  @Test
  public void shouldSerializeMap() throws Exception {
    assertJson(
      Map(
        Arr(Value(1), Value(2), Value(3)),
        Lambda(Value("x"), Var("x"))
      ), "{\"map\":{\"lambda\":\"x\",\"expr\":{\"var\":\"x\"}},\"collection\":[1,2,3]}");
  }

  @Test
  public void shouldSerializeForeach() throws Exception {
    assertJson(
      Foreach(
        Arr(Value(1), Value(2), Value(3)),
        Lambda(Value("x"), Var("x"))
      ), "{\"foreach\":{\"lambda\":\"x\",\"expr\":{\"var\":\"x\"}},\"collection\":[1,2,3]}");
  }

  @Test
  public void shouldSerializeFilter() throws Exception {
    assertJson(
      Filter(
        Arr(Value(true), Value(false)),
        Lambda(Value("x"), Var("x"))
      ), "{\"filter\":{\"lambda\":\"x\",\"expr\":{\"var\":\"x\"}},\"collection\":[true,false]}");
  }

  @Test
  public void shouldSerializeTake() throws Exception {
    assertJson(
      Take(
        Value(2),
        Arr(Value(1), Value(2), Value(3))
      ), "{\"take\":2,\"collection\":[1,2,3]}"
    );

    assertJson(
      Take(
        2L,
        Arr(Value(1), Value(2), Value(3))
      ), "{\"take\":2,\"collection\":[1,2,3]}"
    );
  }

  @Test
  public void shouldSerializeDrop() throws Exception {
    assertJson(
      Drop(
        Value(2),
        Arr(Value(1), Value(2), Value(3))
      ), "{\"drop\":2,\"collection\":[1,2,3]}"
    );

    assertJson(
      Drop(
        2L,
        Arr(Value(1), Value(2), Value(3))
      ), "{\"drop\":2,\"collection\":[1,2,3]}"
    );

  }

  @Test
  public void shouldSerializePrepend() throws Exception {
    assertJson(
      Prepend(
        Arr(Value(1), Value(2), Value(3)),
        Arr(Value(4), Value(5), Value(6))
      ), "{\"prepend\":[1,2,3],\"collection\":[4,5,6]}"
    );
  }

  @Test
  public void shouldSerializeAppend() throws Exception {
    assertJson(
      Append(
        Arr(Value(4), Value(5), Value(6)),
        Arr(Value(1), Value(2), Value(3))
      ), "{\"append\":[4,5,6],\"collection\":[1,2,3]}"
    );
  }

  @Test
  public void shouldSerializeIsEmpty() throws Exception {
    assertJson(
      IsEmpty(
        Arr(Value(1), Value(2), Value(3))
      ), "{\"is_empty\":[1,2,3]}"
    );
  }

  @Test
  public void shouldSerializeIsNonEmpty() throws Exception {
    assertJson(
      IsNonEmpty(
        Arr(Value(1), Value(2), Value(3))
      ), "{\"is_nonempty\":[1,2,3]}"
    );
  }

  @Test
  public void shouldSerializeGet() throws Exception {
    assertJson(
      Get(new RefV("104979509692858368", new RefV("spells", Native.COLLECTIONS))),
      "{\"get\":{\"@ref\":{\"id\":\"104979509692858368\",\"collection\":{\"@ref\":{\"id\":\"spells\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}}}"
    );

    assertJson(
      Get(new RefV("104979509692858368", new RefV("spells", Native.COLLECTIONS)), Value(Instant.ofEpochMilli(0))),
      "{\"get\":{\"@ref\":{\"id\":\"104979509692858368\",\"collection\":{\"@ref\":{\"id\":\"spells\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}},\"ts\":{\"@ts\":\"1970-01-01T00:00:00Z\"}}"
    );

    assertJson(
      Get(new RefV("104979509692858368", new RefV("spells", Native.COLLECTIONS)), Instant.ofEpochMilli(0)),
      "{\"get\":{\"@ref\":{\"id\":\"104979509692858368\",\"collection\":{\"@ref\":{\"id\":\"spells\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}},\"ts\":{\"@ts\":\"1970-01-01T00:00:00Z\"}}"
    );
  }

  @Test
  public void shouldSerializeKeyFromSecret() throws Exception {
    assertJson(KeyFromSecret(Value("s3cr3t")), "{\"key_from_secret\":\"s3cr3t\"}");
    assertJson(KeyFromSecret("s3cr3t"), "{\"key_from_secret\":\"s3cr3t\"}");
  }

  @Test
  public void shouldSerializePaginate() throws Exception {
    assertJson(
      Paginate(Native.DATABASES),
      "{\"paginate\":{\"@ref\":{\"id\":\"databases\"}}}"
    );

    assertJson(
      Paginate(Native.DATABASES)
        .after(new RefV("test", Native.DATABASES))
        .events(true)
        .sources(true)
        .ts(10L)
        .size(2),
      "{\"paginate\":{\"@ref\":{\"id\":\"databases\"}},\"after\":{\"@ref\":{\"id\":\"test\",\"collection\":{\"@ref\":{\"id\":\"databases\"}}}}," +
        "\"events\":true,\"sources\":true,\"ts\":10,\"size\":2}"
    );

    assertJson(
      Paginate(Native.DATABASES)
        .after(new RefV("test", Native.DATABASES))
        .events(Value(true))
        .sources(Value(true))
        .ts(Value(10L))
        .size(Value(2)),
      "{\"paginate\":{\"@ref\":{\"id\":\"databases\"}},\"after\":{\"@ref\":{\"id\":\"test\",\"collection\":{\"@ref\":{\"id\":\"databases\"}}}}," +
        "\"events\":true,\"sources\":true,\"ts\":10,\"size\":2}"
    );

    assertJson(
      Paginate(Native.DATABASES)
        .before(new RefV("test", Native.DATABASES))
        .events(false)
        .sources(false)
        .ts(10L)
        .size(2),
      "{\"paginate\":{\"@ref\":{\"id\":\"databases\"}},\"before\":{\"@ref\":{\"id\":\"test\",\"collection\":{\"@ref\":{\"id\":\"databases\"}}}}," +
        "\"ts\":10,\"size\":2}"
    );
  }

  @Test
  public void shouldSerializeExists() throws Exception {
    assertJson(
      Exists(new RefV("104979509692858368", new RefV("spells", Native.COLLECTIONS))),
      "{\"exists\":{\"@ref\":{\"id\":\"104979509692858368\",\"collection\":{\"@ref\":{\"id\":\"spells\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}}}"
    );

    assertJson(
      Exists(new RefV("104979509692858368", new RefV("spells", Native.COLLECTIONS)), Value(Instant.ofEpochMilli(0))),
      "{\"exists\":{\"@ref\":{\"id\":\"104979509692858368\",\"collection\":{\"@ref\":{\"id\":\"spells\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}},\"ts\":{\"@ts\":\"1970-01-01T00:00:00Z\"}}"
    );
  }

  @Test
  public void shouldSerializeCreate() throws Exception {
    assertJson(
      Create(
        Native.DATABASES,
        Obj("name", Value("annuvin"))
      ), "{\"create\":{\"@ref\":{\"id\":\"databases\"}},\"params\":{\"object\":{\"name\":\"annuvin\"}}}");

  }

  @Test
  public void shouldSerializeUpdate() throws Exception {
    assertJson(
      Update(
        new RefV("annuvin", Native.DATABASES),
        Obj("name", Value("llyr"))
      ), "{\"update\":{\"@ref\":{\"id\":\"annuvin\",\"collection\":{\"@ref\":{\"id\":\"databases\"}}}},\"params\":{\"object\":{\"name\":\"llyr\"}}}");

  }

  @Test
  public void shouldSerializeReplace() throws Exception {
    assertJson(
      Replace(
        new RefV("104979509696660483", new RefV("spells", Native.COLLECTIONS)),
        Obj("data",
          Obj("name", Value("Mountain's Thunder")))
      ), "{\"replace\":{\"@ref\":{\"id\":\"104979509696660483\",\"collection\":{\"@ref\":{\"id\":\"spells\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}}," +
        "\"params\":{\"object\":{\"data\":{\"object\":{\"name\":\"Mountain's Thunder\"}}}}}");
  }

  @Test
  public void shouldSerializeDelete() throws Exception {
    assertJson(
      Delete(new RefV("104979509696660483", new RefV("spells", Native.COLLECTIONS))),
      "{\"delete\":{\"@ref\":{\"id\":\"104979509696660483\",\"collection\":{\"@ref\":{\"id\":\"spells\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}}}"
    );
  }

  @Test
  public void shouldSerializeInsert() throws Exception {
    assertJson(
      Insert(
        new RefV("104979509696660483", new RefV("spells", Native.COLLECTIONS)),
        Value(Instant.ofEpochMilli(0)),
        Action.CREATE,
        Obj("data", Obj("name", Value("test")))
      ),
      "{\"insert\":{\"@ref\":{\"id\":\"104979509696660483\",\"collection\":{\"@ref\":{\"id\":\"spells\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}},\"ts\":{\"@ts\":\"1970-01-01T00:00:00Z\"}," +
        "\"action\":\"create\",\"params\":{\"object\":{\"data\":{\"object\":{\"name\":\"test\"}}}}}"
    );

    assertJson(
      Insert(
        new RefV("104979509696660483", new RefV("spells", Native.COLLECTIONS)),
        Value(Instant.ofEpochMilli(0)),
        Value("create"),
        Obj("data", Obj("name", Value("test")))
      ),
      "{\"insert\":{\"@ref\":{\"id\":\"104979509696660483\",\"collection\":{\"@ref\":{\"id\":\"spells\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}},\"ts\":{\"@ts\":\"1970-01-01T00:00:00Z\"}," +
        "\"action\":\"create\",\"params\":{\"object\":{\"data\":{\"object\":{\"name\":\"test\"}}}}}"
    );
  }

  @Test
  public void shouldSerializeRemove() throws Exception {
    assertJson(
      Remove(
        new RefV("104979509696660483", new RefV("spells", Native.COLLECTIONS)),
        Value(Instant.ofEpochMilli(0)),
        Action.DELETE
      ),
      "{\"remove\":{\"@ref\":{\"id\":\"104979509696660483\",\"collection\":{\"@ref\":{\"id\":\"spells\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}}," +
        "\"ts\":{\"@ts\":\"1970-01-01T00:00:00Z\"},\"action\":\"delete\"}"
    );

    assertJson(
      Remove(
        new RefV("104979509696660483", new RefV("spells", Native.COLLECTIONS)),
        Value(Instant.ofEpochMilli(0)),
        Value("delete")
      ),
      "{\"remove\":{\"@ref\":{\"id\":\"104979509696660483\",\"collection\":{\"@ref\":{\"id\":\"spells\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}}," +
        "\"ts\":{\"@ts\":\"1970-01-01T00:00:00Z\"},\"action\":\"delete\"}"
    );
  }

  @Test
  public void shouldSerializeCreateClass() throws Exception {
    assertJson(
      CreateCollection(Obj(
        "name", Value("spells")
      )),
      "{\"create_collection\":{\"object\":{\"name\":\"spells\"}}}"
    );
  }

  @Test
  public void shouldSerializeCreateDatabase() throws Exception {
    assertJson(
      CreateDatabase(Obj(
        "name", Value("db-test")
      )),
      "{\"create_database\":{\"object\":{\"name\":\"db-test\"}}}"
    );
  }

  @Test
  public void shouldSerializeCreateKey() throws Exception {
    assertJson(
      CreateKey(Obj(
        "database", Database(Value("db-test")),
        "role", Value("server")
      )),
      "{\"create_key\":{\"object\":{\"database\":{\"database\":\"db-test\"},\"role\":\"server\"}}}"
    );
  }

  @Test
  public void shouldSerializeCreateIndex() throws Exception {
    assertJson(
      CreateIndex(Obj(
        "name", Value("all_spells"),
        "source", Collection("spells")
      )),
      "{\"create_index\":{\"object\":{\"name\":\"all_spells\",\"source\":{\"collection\":\"spells\"}}}}"
    );
  }

  @Test
  public void shouldSerializeCreateRole() throws Exception {
    assertJson(
      CreateRole(Obj(
          "name", Value("a_role"),
          "privileges", Arr(Obj(
              "resource", Databases(),
              "actions", Obj("read", Value(true))
          ))
      )),
      "{\"create_role\":{\"object\":{\"name\":\"a_role\",\"privileges\":[{\"object\":{" +
          "\"resource\":{\"databases\":null},\"actions\":{\"object\":{\"read\":true}}}}]}}}"
    );

    assertJson(
      CreateRole(Obj(
          "name", Value("a_role"),
          "privileges", Obj(
              "resource", Databases(),
              "actions", Obj("read", Value(true))
          )
      )),
      "{\"create_role\":{\"object\":{\"name\":\"a_role\",\"privileges\":{\"object\":{" +
          "\"resource\":{\"databases\":null},\"actions\":{\"object\":{\"read\":true}}}}}}}"
    );
  }

  @Test
  public void shouldSerializeSingletonFunction() throws Exception {
    assertJson(
      Singleton(Ref("classes/widget/1")),
      "{\"singleton\":{\"@ref\":\"classes/widget/1\"}}"
    );
  }

  @Test
  public void shouldSerializeEventsFunction() throws Exception {
    assertJson(
      Events(Ref("classes/widget/1")),
      "{\"events\":{\"@ref\":\"classes/widget/1\"}}"
    );
  }

  @Test
  public void shouldSerializeMatchFunction() throws Exception {
    assertJson(
      Match(new RefV("all_users", Native.INDEXES)),
      "{\"match\":{\"@ref\":{\"id\":\"all_users\",\"collection\":{\"@ref\":{\"id\":\"indexes\"}}}}}"
    );

    assertJson(
      Match(new RefV("spells_by_element", Native.INDEXES), Value("fire")),
      "{\"match\":{\"@ref\":{\"id\":\"spells_by_element\",\"collection\":{\"@ref\":{\"id\":\"indexes\"}}}},\"terms\":\"fire\"}"
    );
  }

  @Test
  public void shouldSerializeUnion() throws Exception {
    assertJson(
      Union(Native.DATABASES, Native.KEYS),
      "{\"union\":[{\"@ref\":{\"id\":\"databases\"}},{\"@ref\":{\"id\":\"keys\"}}]}"
    );

    assertJson(
      Union(Arrays.asList(Native.DATABASES, Native.KEYS)),
      "{\"union\":[{\"@ref\":{\"id\":\"databases\"}},{\"@ref\":{\"id\":\"keys\"}}]}"
    );

    assertJson(
      Union(Arr(Native.DATABASES, Native.KEYS)),
      "{\"union\":[{\"@ref\":{\"id\":\"databases\"}},{\"@ref\":{\"id\":\"keys\"}}]}"
    );
  }

  @Test
  public void shouldSerializeIntersection() throws Exception {
    assertJson(
      Intersection(Native.DATABASES, Native.KEYS),
      "{\"intersection\":[{\"@ref\":{\"id\":\"databases\"}},{\"@ref\":{\"id\":\"keys\"}}]}"
    );

    assertJson(
      Intersection(Arrays.asList(Native.DATABASES, Native.KEYS)),
      "{\"intersection\":[{\"@ref\":{\"id\":\"databases\"}},{\"@ref\":{\"id\":\"keys\"}}]}"
    );

    assertJson(
      Intersection(Arr(Native.DATABASES, Native.KEYS)),
      "{\"intersection\":[{\"@ref\":{\"id\":\"databases\"}},{\"@ref\":{\"id\":\"keys\"}}]}"
    );
  }

  @Test
  public void shouldSerializeDifference() throws Exception {
    assertJson(
      Difference(Native.DATABASES, Native.KEYS),
      "{\"difference\":[{\"@ref\":{\"id\":\"databases\"}},{\"@ref\":{\"id\":\"keys\"}}]}"
    );

    assertJson(
      Difference(Arrays.asList(Native.DATABASES, Native.KEYS)),
      "{\"difference\":[{\"@ref\":{\"id\":\"databases\"}},{\"@ref\":{\"id\":\"keys\"}}]}"
    );

    assertJson(
      Difference(Arr(Native.DATABASES, Native.KEYS)),
      "{\"difference\":[{\"@ref\":{\"id\":\"databases\"}},{\"@ref\":{\"id\":\"keys\"}}]}"
    );
  }

  @Test
  public void shouldSerializeDistinct() throws Exception {
    assertJson(
      Distinct(Match(new RefV("some_set", Native.INDEXES))),
      "{\"distinct\":{\"match\":{\"@ref\":{\"id\":\"some_set\",\"collection\":{\"@ref\":{\"id\":\"indexes\"}}}}}}"
    );
  }

  @Test
  public void shouldSerializeReverse() throws Exception {
    assertJson(
      Reverse(
        Arr(Value("1"), Value("2"), Value("3"))
      ),
      "{\"reverse\":[\"1\",\"2\",\"3\"]}"
    );
  }

  @Test
  public void shouldSerializeJoin() throws Exception {
    assertJson(
      Join(
        Match(new RefV("spellbooks_by_owner", Native.INDEXES), new RefV("104979509695139637", new RefV("characters", Native.COLLECTIONS))),
        new RefV("spells_by_spellbook", Native.INDEXES)
      ),
      "{\"join\":{\"match\":{\"@ref\":{\"id\":\"spellbooks_by_owner\",\"collection\":{\"@ref\":{\"id\":\"indexes\"}}}}," +
        "\"terms\":{\"@ref\":{\"id\":\"104979509695139637\",\"collection\":{\"@ref\":{\"id\":\"characters\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}}}," +
        "\"with\":{\"@ref\":{\"id\":\"spells_by_spellbook\",\"collection\":{\"@ref\":{\"id\":\"indexes\"}}}}}"
    );
  }

  @Test
  public void shouldSerializeLogin() throws Exception {
    assertJson(
      Login(
        new RefV("104979509695139637", new RefV("characters", Native.COLLECTIONS)),
        Obj("password", Value("abracadabra"))
      ),
      "{\"login\":{\"@ref\":{\"id\":\"104979509695139637\",\"collection\":{\"@ref\":{\"id\":\"characters\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}}," +
        "\"params\":{\"object\":{\"password\":\"abracadabra\"}}}"
    );
  }

  @Test
  public void shouldSerializeLogout() throws Exception {
    assertJson(Logout(Value(true)), "{\"logout\":true}");
    assertJson(Logout(true), "{\"logout\":true}");
  }

  @Test
  public void shouldSerializeIdentify() throws Exception {
    assertJson(
      Identify(new RefV("104979509695139637", new RefV("characters", Native.COLLECTIONS)), Value("abracadabra")),
      "{\"identify\":{\"@ref\":{\"id\":\"104979509695139637\",\"collection\":{\"@ref\":{\"id\":\"characters\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}},\"password\":\"abracadabra\"}"
    );

    assertJson(
      Identify(new RefV("104979509695139637", new RefV("characters", Native.COLLECTIONS)), "abracadabra"),
      "{\"identify\":{\"@ref\":{\"id\":\"104979509695139637\",\"collection\":{\"@ref\":{\"id\":\"characters\",\"collection\":{\"@ref\":{\"id\":\"collections\"}}}}}},\"password\":\"abracadabra\"}"
    );
  }

  @Test
  public void shouldSerializeIdentity() throws Exception {
    assertJson(Identity(), "{\"identity\":null}");
  }

  @Test
  public void shouldSerializeCurrentIdentity() throws Exception {
    assertJson(CurrentIdentity(), "{\"current_identity\":null}");
  }

  @Test
  public void shouldSerializeHasIdentity() throws Exception {
    assertJson(HasIdentity(), "{\"has_identity\":null}");
  }

  @Test
  public void shouldSerializeCreateAccessProvider() throws Exception {
    assertJson(
      CreateAccessProvider(
        Obj(
        "name", Value("role_name"),
        "issuer", Value("issuer_name"),
        "jwks_uri", Value("https://auth0.com"),
        "membership", Arr()
        )),
      "{\"create_access_provider\":{\"object\":{\"name\":\"role_name\",\"issuer\":\"issuer_name\",\"jwks_uri\":\"https://auth0.com\",\"membership\":[]}}}");
  }

  @Test
  public void shouldSerializeAccessProvider() throws Exception {
    assertJson(AccessProvider(Value("access-provider")), "{\"access_provider\":\"access-provider\"}");
  }

  @Test
  public void shouldSerializeCurrentToken() throws Exception {
    assertJson(CurrentToken(), "{\"current_token\":null}");
  }

  @Test
  public void shouldSerializeHasCurrentToken() throws Exception {
    assertJson(HasCurrentToken(), "{\"has_current_token\":null}");
  }

  @Test  
  public void shouldSerializeHasCurrentIdentity() throws Exception {
    assertJson(HasCurrentIdentity(), "{\"has_current_identity\":null}");
  }

  @Test
  public void shouldSerializeConcat() throws Exception {
    assertJson(
      Concat(
        Arr(
          Value("Hen"),
          Value("Wen")
        )
      ), "{\"concat\":[\"Hen\",\"Wen\"]}"
    );

    assertJson(
      Concat(
        Arr(
          Value("Hen"),
          Value("Wen")
        ),
        Value(" ")
      ), "{\"concat\":[\"Hen\",\"Wen\"],\"separator\":\" \"}"
    );
  }

  @Test
  public void shouldSerializeCasefold() throws Exception {
    assertJson(Casefold(Value("Hen Wen")), "{\"casefold\":\"Hen Wen\"}");
    assertJson(Casefold("Hen Wen"), "{\"casefold\":\"Hen Wen\"}");

    assertJson(Casefold(Value("Hen Wen"), Normalizer.NFD), "{\"casefold\":\"Hen Wen\",\"normalizer\":\"NFD\"}");
    assertJson(Casefold("Hen Wen", Normalizer.NFD), "{\"casefold\":\"Hen Wen\",\"normalizer\":\"NFD\"}");
    assertJson(Casefold(Value("Hen Wen"), Value("NFD")), "{\"casefold\":\"Hen Wen\",\"normalizer\":\"NFD\"}");
    assertJson(Casefold("Hen Wen", Value("NFD")), "{\"casefold\":\"Hen Wen\",\"normalizer\":\"NFD\"}");
  }

  @Test
  public void shouldSerializeContainsStr() throws Exception {
    assertJson(ContainsStr("ABCDEF", "CDE"), "{\"containsstr\":\"ABCDEF\",\"search\":\"CDE\"}");
    assertJson(ContainsStr(Value("ABCDEF"), "CDE"), "{\"containsstr\":\"ABCDEF\",\"search\":\"CDE\"}");
    assertJson(ContainsStr("ABCDEF", Value("CDE")), "{\"containsstr\":\"ABCDEF\",\"search\":\"CDE\"}");
    assertJson(ContainsStr(Value("ABCDEF"), Value("CDE")), "{\"containsstr\":\"ABCDEF\",\"search\":\"CDE\"}");
  }

  @Test
  public void shouldSerializeContainsStrRegex() throws Exception {
    assertJson(ContainsStrRegex("ABCDEF", "BCD"), "{\"containsstrregex\":\"ABCDEF\",\"pattern\":\"BCD\"}");
    assertJson(ContainsStrRegex(Value("ABCDEF"), "BCD"), "{\"containsstrregex\":\"ABCDEF\",\"pattern\":\"BCD\"}");
    assertJson(ContainsStrRegex("ABCDEF", Value("BCD")), "{\"containsstrregex\":\"ABCDEF\",\"pattern\":\"BCD\"}");
    assertJson(ContainsStrRegex(Value("ABCDEF"), Value("BCD")), "{\"containsstrregex\":\"ABCDEF\",\"pattern\":\"BCD\"}");
  }

  @Test
  public void shouldSerializeEndsWith() throws Exception {
    assertJson(EndsWith("ABCDEF", "DEF"), "{\"endswith\":\"ABCDEF\",\"search\":\"DEF\"}");
    assertJson(EndsWith(Value("ABCDEF"), "DEF"), "{\"endswith\":\"ABCDEF\",\"search\":\"DEF\"}");
    assertJson(EndsWith("ABCDEF", Value("DEF")), "{\"endswith\":\"ABCDEF\",\"search\":\"DEF\"}");
    assertJson(EndsWith(Value("ABCDEF"), Value("DEF")), "{\"endswith\":\"ABCDEF\",\"search\":\"DEF\"}");
  }

  @Test
  public void shouldSerializeFindStr() throws Exception {
    assertJson(FindStr("ABCDEF", "ABC"), "{\"findstr\":\"ABCDEF\",\"find\":\"ABC\"}");
    assertJson(FindStr(Value("AABCDEFF"), Value("AABC")), "{\"findstr\":\"AABCDEFF\",\"find\":\"AABC\"}");
    assertJson(FindStr("ABCDEF","ABC", 1), "{\"findstr\":\"ABCDEF\",\"find\":\"ABC\",\"start\":1}");
    assertJson(FindStr(Value("AABCDEFF"), Value("AABC"), Value(1)), "{\"findstr\":\"AABCDEFF\",\"find\":\"AABC\",\"start\":1}");
  }

  @Test
  public void shouldSerializeFindStrRegex() throws Exception {
    assertJson(FindStrRegex("ABCDEF", "BCD"), "{\"findstrregex\":\"ABCDEF\",\"pattern\":\"BCD\"}");
    assertJson(FindStrRegex(Value("abcdef"), Value("bcd")), "{\"findstrregex\":\"abcdef\",\"pattern\":\"bcd\"}");
    assertJson(FindStrRegex("ABCDEF", "BCD", 1L), "{\"findstrregex\":\"ABCDEF\",\"pattern\":\"BCD\",\"start\":1}");
    assertJson(FindStrRegex(Value("abcdef"), Value("bcd"), Value(1)), "{\"findstrregex\":\"abcdef\",\"pattern\":\"bcd\",\"start\":1}");
    assertJson(FindStrRegex("ABCDEF", "BCD", 1L, 3L), "{\"findstrregex\":\"ABCDEF\",\"pattern\":\"BCD\",\"start\":1,\"num_results\":3}");
    assertJson(FindStrRegex(Value("abcdef"), Value("bcd"), Value(1), Value(4)), "{\"findstrregex\":\"abcdef\",\"pattern\":\"bcd\",\"start\":1,\"num_results\":4}");
  }

  @Test
  public void shouldSerializeSplitStr() throws Exception {
    assertJson(SplitStr("ABCDEF", "ABC", 1L), "{\"split_str\":\"ABCDEF\",\"token\":\"ABC\",\"count\":1}");
    assertJson(SplitStr("ABCDEF", Value("ABC"), 1L), "{\"split_str\":\"ABCDEF\",\"token\":\"ABC\",\"count\":1}");
    assertJson(SplitStr("ABCDEF", Value("ABC"), Value(1)), "{\"split_str\":\"ABCDEF\",\"token\":\"ABC\",\"count\":1}");
  }

  @Test
  public void shouldSerializeSplitStrNoCount() throws Exception {
    assertJson(SplitStr("ABCDEF", "ABC"), "{\"split_str\":\"ABCDEF\",\"token\":\"ABC\"}");
    assertJson(SplitStr("ABCDEF", Value("ABC")), "{\"split_str\":\"ABCDEF\",\"token\":\"ABC\"}");
    assertJson(SplitStr("ABCDEF", Value("ABC")), "{\"split_str\":\"ABCDEF\",\"token\":\"ABC\"}");
  }

  @Test
  public void shouldSerializeSplitStrRegex() throws Exception {
    assertJson(SplitStrRegex("ABCDEF", ".*", 1L), "{\"split_str_regex\":\"ABCDEF\",\"pattern\":\".*\",\"count\":1}");
    assertJson(SplitStrRegex("ABCDEF", Value(".*"), 1L), "{\"split_str_regex\":\"ABCDEF\",\"pattern\":\".*\",\"count\":1}");
    assertJson(SplitStrRegex("ABCDEF", Value(".*"), Value(1)), "{\"split_str_regex\":\"ABCDEF\",\"pattern\":\".*\",\"count\":1}");
  }

  @Test
  public void shouldSerializeSplitStrRegexNoCount() throws Exception {
    assertJson(SplitStrRegex("ABCDEF", ".*"), "{\"split_str_regex\":\"ABCDEF\",\"pattern\":\".*\"}");
    assertJson(SplitStrRegex("ABCDEF", Value(".*")), "{\"split_str_regex\":\"ABCDEF\",\"pattern\":\".*\"}");
    assertJson(SplitStrRegex("ABCDEF", Value(".*")), "{\"split_str_regex\":\"ABCDEF\",\"pattern\":\".*\"}");
  }

  @Test
  public void shouldSerializeLength() throws Exception {
    assertJson(Length("ABC"), "{\"length\":\"ABC\"}");
    assertJson(Length(Value("DEF")), "{\"length\":\"DEF\"}");
  }

  @Test
  public void shouldSerializeLower() throws Exception {
    assertJson(LowerCase("ABC"), "{\"lowercase\":\"ABC\"}");
    assertJson(LowerCase(Value("DEF")), "{\"lowercase\":\"DEF\"}");
  }

  @Test
  public void shouldSerializeLTrim() throws Exception {
    assertJson(LTrim("ABC"), "{\"ltrim\":\"ABC\"}");
    assertJson(LTrim(Value("DEF")), "{\"ltrim\":\"DEF\"}");
  }

  @Test
  public void shouldSerializeNGram() throws Exception {
    assertJson(NGram(Value("str")), "{\"ngram\":\"str\"}");
    assertJson(NGram("str"), "{\"ngram\":\"str\"}");
    assertJson(NGram(Arr(Value("str0"), Value("str1"))), "{\"ngram\":[\"str0\",\"str1\"]}");
    assertJson(NGram(Arrays.asList(Value("str0"), Value("str1"))), "{\"ngram\":[\"str0\",\"str1\"]}");

    assertJson(NGram(Value("str"), Value(2), Value(4)), "{\"ngram\":\"str\",\"min\":2,\"max\":4}");
    assertJson(NGram("str", Value(2), Value(4)), "{\"ngram\":\"str\",\"min\":2,\"max\":4}");
    assertJson(NGram(Value("str"), 2L, Value(4)), "{\"ngram\":\"str\",\"min\":2,\"max\":4}");
    assertJson(NGram("str", 2L, Value(4)), "{\"ngram\":\"str\",\"min\":2,\"max\":4}");
    assertJson(NGram(Value("str"), Value(2), 4L), "{\"ngram\":\"str\",\"min\":2,\"max\":4}");
    assertJson(NGram("str", Value(2), 4L), "{\"ngram\":\"str\",\"min\":2,\"max\":4}");
    assertJson(NGram(Value("str"), 2L, 4L), "{\"ngram\":\"str\",\"min\":2,\"max\":4}");
    assertJson(NGram("str", 2L, 4L), "{\"ngram\":\"str\",\"min\":2,\"max\":4}");
    assertJson(NGram(Arr(Value("str0"), Value("str1")), Value(2), Value(4)), "{\"ngram\":[\"str0\",\"str1\"],\"min\":2,\"max\":4}");
    assertJson(NGram(Arrays.asList(Value("str0"), Value("str1")), Value(2), Value(4)), "{\"ngram\":[\"str0\",\"str1\"],\"min\":2,\"max\":4}");
  }

  @Test
  public void shouldSerializeRegexEscape() throws Exception {
    assertJson(RegexEscape("ABC"), "{\"regexescape\":\"ABC\"}");
    assertJson(RegexEscape(Value("ABC")), "{\"regexescape\":\"ABC\"}");
  }

  @Test
  public void shouldSerializeRepeat() throws Exception {
    assertJson(Repeat("ABC"), "{\"repeat\":\"ABC\"}");
    assertJson(Repeat(Value("abc")), "{\"repeat\":\"abc\"}");
    assertJson(Repeat("ABC", 2L), "{\"repeat\":\"ABC\",\"number\":2}");
    assertJson(Repeat(Value("abc"), Value(2)), "{\"repeat\":\"abc\",\"number\":2}");
  }

  @Test
  public void shouldSerializeReplaceStr() throws Exception {
    assertJson(ReplaceStr("ABCDEF", "BCD","CAR"), "{\"replacestr\":\"ABCDEF\",\"find\":\"BCD\",\"replace\":\"CAR\"}");
    assertJson(ReplaceStr(Value("abcdef"), Value("bcd"), Value("car")), "{\"replacestr\":\"abcdef\",\"find\":\"bcd\",\"replace\":\"car\"}");
  }

  @Test
  public void shouldSerializeReplaceStrRegex() throws Exception {
    assertJson(ReplaceStrRegex("ABCDEF", "BCD","CAR"), "{\"replacestrregex\":\"ABCDEF\",\"pattern\":\"BCD\",\"replace\":\"CAR\"}");
    assertJson(ReplaceStrRegex(Value("abcdef"), Value("bcd"), Value("car")), "{\"replacestrregex\":\"abcdef\",\"pattern\":\"bcd\",\"replace\":\"car\"}");
    assertJson(ReplaceStrRegex("ABCDEF", "BCD","CAR",true), "{\"replacestrregex\":\"ABCDEF\",\"pattern\":\"BCD\",\"replace\":\"CAR\",\"first\":true}");
    assertJson(ReplaceStrRegex(Value("abcdef"), Value("bcd"), Value("car"), Value(true)), "{\"replacestrregex\":\"abcdef\",\"pattern\":\"bcd\",\"replace\":\"car\",\"first\":true}");
  }

  @Test
  public void shouldSerializeRTrim() throws Exception {
    assertJson(RTrim("ABC"), "{\"rtrim\":\"ABC\"}");
    assertJson(RTrim(Value("DEF")), "{\"rtrim\":\"DEF\"}");
  }

  @Test
  public void shouldSerializeSpace() throws Exception {
    assertJson(Space(2), "{\"space\":2}");
    assertJson(Space(2L), "{\"space\":2}");
    assertJson(Space(Value(2)), "{\"space\":2}");
  }

  @Test
  public void shouldSerializeStartsWith() throws Exception {
    assertJson(StartsWith("ABCDEF", "ABC"), "{\"startswith\":\"ABCDEF\",\"search\":\"ABC\"}");
    assertJson(StartsWith(Value("ABCDEF"), "ABC"), "{\"startswith\":\"ABCDEF\",\"search\":\"ABC\"}");
    assertJson(StartsWith("ABCDEF", Value("ABC")), "{\"startswith\":\"ABCDEF\",\"search\":\"ABC\"}");
    assertJson(StartsWith(Value("ABCDEF"), Value("ABC")), "{\"startswith\":\"ABCDEF\",\"search\":\"ABC\"}");
  }

  @Test
  public void shouldSerializeSubString() throws Exception {
    assertJson(SubString("ABC"), "{\"substring\":\"ABC\"}");
    assertJson(SubString(Value("abc")), "{\"substring\":\"abc\"}");
    assertJson(SubString("ABC", 2L), "{\"substring\":\"ABC\",\"start\":2}");
    assertJson(SubString(Value("abc"), Value(2)), "{\"substring\":\"abc\",\"start\":2}");
    assertJson(SubString("ABC", 2, 3), "{\"substring\":\"ABC\",\"start\":2,\"length\":3}");
    assertJson(SubString(Value("abc"), Value(2), Value(3)), "{\"substring\":\"abc\",\"start\":2,\"length\":3}");
  }

  @Test
  public void shouldSerializeTrim() throws Exception {
    assertJson(Trim("ABC"), "{\"trim\":\"ABC\"}");
    assertJson(Trim(Value("DEF")), "{\"trim\":\"DEF\"}");
  }

  @Test
  public void shouldSerializeUpper() throws Exception {
    assertJson(UpperCase("ABC"), "{\"uppercase\":\"ABC\"}");
    assertJson(UpperCase(Value("DEF")), "{\"uppercase\":\"DEF\"}");
  }

  @Test
  public void shouldSerializeTitleCase() throws Exception {
    assertJson(TitleCase("ABC"), "{\"titlecase\":\"ABC\"}");
    assertJson(TitleCase(Value("DEF")), "{\"titlecase\":\"DEF\"}");
  }

  @Test
  public void shouldSerializeFormat() throws Exception {
    assertJson(Format(Value("%f %d"), Value(3.14), Value(10)), "{\"format\":\"%f %d\",\"values\":[3.14,10]}");
    assertJson(Format("%f %d", Value(3.14), Value(10)), "{\"format\":\"%f %d\",\"values\":[3.14,10]}");
  }

  @Test
  public void shouldSerializeTime() throws Exception {
    assertJson(
      Time(Value("1970-01-01T00:00:00+00:00")),
      "{\"time\":\"1970-01-01T00:00:00+00:00\"}"
    );

    assertJson(
      Time("1970-01-01T00:00:00+00:00"),
      "{\"time\":\"1970-01-01T00:00:00+00:00\"}"
    );
  }

  @Test
  public void shouldSerializeEpoch() throws Exception {
    assertJson(Epoch(Value(0), SECOND), "{\"epoch\":0,\"unit\":\"second\"}");
    assertJson(Epoch(0L, SECOND), "{\"epoch\":0,\"unit\":\"second\"}");
    assertJson(Epoch(Value(0), MILLISECOND), "{\"epoch\":0,\"unit\":\"millisecond\"}");
    assertJson(Epoch(0L, MILLISECOND), "{\"epoch\":0,\"unit\":\"millisecond\"}");
    assertJson(Epoch(Value(0), MICROSECOND), "{\"epoch\":0,\"unit\":\"microsecond\"}");
    assertJson(Epoch(0L, MICROSECOND), "{\"epoch\":0,\"unit\":\"microsecond\"}");
    assertJson(Epoch(Value(0), NANOSECOND), "{\"epoch\":0,\"unit\":\"nanosecond\"}");
    assertJson(Epoch(0L, NANOSECOND), "{\"epoch\":0,\"unit\":\"nanosecond\"}");

    assertJson(Epoch(Value(0), Value("second")), "{\"epoch\":0,\"unit\":\"second\"}");
    assertJson(Epoch(Value(0), "second"), "{\"epoch\":0,\"unit\":\"second\"}");
    assertJson(Epoch(0L, Value("second")), "{\"epoch\":0,\"unit\":\"second\"}");
    assertJson(Epoch(0L, "second"), "{\"epoch\":0,\"unit\":\"second\"}");
    assertJson(Epoch(Value(0), Value("millisecond")), "{\"epoch\":0,\"unit\":\"millisecond\"}");
    assertJson(Epoch(Value(0), "millisecond"), "{\"epoch\":0,\"unit\":\"millisecond\"}");
    assertJson(Epoch(0L, Value("millisecond")), "{\"epoch\":0,\"unit\":\"millisecond\"}");
    assertJson(Epoch(0L, "millisecond"), "{\"epoch\":0,\"unit\":\"millisecond\"}");
    assertJson(Epoch(Value(0), Value("microsecond")), "{\"epoch\":0,\"unit\":\"microsecond\"}");
    assertJson(Epoch(Value(0), "microsecond"), "{\"epoch\":0,\"unit\":\"microsecond\"}");
    assertJson(Epoch(0L, Value("microsecond")), "{\"epoch\":0,\"unit\":\"microsecond\"}");
    assertJson(Epoch(0L, "microsecond"), "{\"epoch\":0,\"unit\":\"microsecond\"}");
    assertJson(Epoch(Value(0), Value("nanosecond")), "{\"epoch\":0,\"unit\":\"nanosecond\"}");
    assertJson(Epoch(Value(0), "nanosecond"), "{\"epoch\":0,\"unit\":\"nanosecond\"}");
    assertJson(Epoch(0L, Value("nanosecond")), "{\"epoch\":0,\"unit\":\"nanosecond\"}");
    assertJson(Epoch(0L, "nanosecond"), "{\"epoch\":0,\"unit\":\"nanosecond\"}");
  }

  @Test
  public void shouldSerializeDate() throws Exception {
    assertJson(Date(Value("1970-01-01")), "{\"date\":\"1970-01-01\"}");
    assertJson(Date("1970-01-01"), "{\"date\":\"1970-01-01\"}");
  }

  @Test
  public void shouldSerializeNewId() throws Exception {
    assertJson(NewId(), "{\"new_id\":null}");
  }

  @Test
  public void shouldSerializeEquals() throws Exception {
    assertJson(Equals(Value("fire"), Value("fire")), "{\"equals\":[\"fire\",\"fire\"]}");
    assertJson(Equals(Arrays.asList(Value("fire"), Value("fire"))), "{\"equals\":[\"fire\",\"fire\"]}");
    assertJson(Equals(Arr(Value("fire"), Value("fire"))), "{\"equals\":[\"fire\",\"fire\"]}");
  }

  @Test
  public void shouldSerializeContains() throws Exception {
    assertJson(
      Contains(
        Path("favorites", "foods"),
        Obj("favorites",
          Obj("foods", Arr(
            Value("crunchings"),
            Value("munchings"),
            Value("lunchings")
          ))
        )
      ),
      "{\"contains\":[\"favorites\",\"foods\"],\"in\":" +
        "{\"object\":{\"favorites\":{\"object\":{\"foods\":[\"crunchings\",\"munchings\",\"lunchings\"]}}}}}");

    assertJson(
      Contains(
        Arr(Value("favorites"), Value("foods")),
        Obj("favorites",
          Obj("foods", Arr(
            Value("crunchings"),
            Value("munchings"),
            Value("lunchings")
          ))
        )
      ),
      "{\"contains\":[\"favorites\",\"foods\"],\"in\":" +
        "{\"object\":{\"favorites\":{\"object\":{\"foods\":[\"crunchings\",\"munchings\",\"lunchings\"]}}}}}");
  }
  @Test
  public void shouldSerializeContainsField() throws Exception {
    assertJson(
      ContainsField(
        Value("foo"),
        Obj("foo", Value("bar"))
      ),
      "{\"contains_field\":\"foo\",\"in\":" +
        "{\"object\":{\"foo\":\"bar\"}}}");
  }

  @Test
  public void shouldSerializeContainsPath() throws Exception {
    assertJson(
      ContainsPath(
        Path("favorites", "foods"),
        Obj("favorites",
          Obj("foods", Arr(
            Value("crunchings"),
            Value("munchings"),
            Value("lunchings")
          ))
        )
      ),
      "{\"contains_path\":[\"favorites\",\"foods\"],\"in\":" +
        "{\"object\":{\"favorites\":{\"object\":{\"foods\":[\"crunchings\",\"munchings\",\"lunchings\"]}}}}}");

    assertJson(
      ContainsPath(
        Arr(Value("favorites"), Value("foods")),
        Obj("favorites",
          Obj("foods", Arr(
            Value("crunchings"),
            Value("munchings"),
            Value("lunchings")
          ))
        )
      ),
      "{\"contains_path\":[\"favorites\",\"foods\"],\"in\":" +
        "{\"object\":{\"favorites\":{\"object\":{\"foods\":[\"crunchings\",\"munchings\",\"lunchings\"]}}}}}");
  }

  @Test
  public void shouldSerializeContainsValue() throws Exception {
    assertJson(
      ContainsValue(
        Value("bar"),
        Obj("foo", Value("bar"))
      ),
      "{\"contains_value\":\"bar\",\"in\":" +
        "{\"object\":{\"foo\":\"bar\"}}}");
  }

  @Test
  public void shouldSerializeSelect() throws Exception {
    assertJson(
      Select(
        Path("favorites", "foods").at(1),
        Obj("favorites",
          Obj("foods", Arr(
            Value("crunchings"),
            Value("munchings"),
            Value("lunchings")
          ))
        )
      ),
      "{\"select\":[\"favorites\",\"foods\",1],\"from\":" +
        "{\"object\":{\"favorites\":{\"object\":{\"foods\":[\"crunchings\",\"munchings\",\"lunchings\"]}}}}}");

    assertJson(
      Select(
        Path(0).at("name"),
        Arr(Obj("name", Value("someone")))
      ),
      "{\"select\":[0,\"name\"],\"from\":" +
        "[{\"object\":{\"name\":\"someone\"}}]}");

    assertJson(
      Select(
        Arr(Value("favorites"), Value("foods"), Value(1)),
        Obj("favorites",
          Obj("foods", Arr(
            Value("crunchings"),
            Value("munchings"),
            Value("lunchings")
          ))
        )
      ),
      "{\"select\":[\"favorites\",\"foods\",1],\"from\":" +
        "{\"object\":{\"favorites\":{\"object\":{\"foods\":[\"crunchings\",\"munchings\",\"lunchings\"]}}}}}");

    assertJson(
      Select(
        Arr(Value("favorites"), Value("foods"), Value(1)),
        Obj("favorites", Obj("foods", Arr())),
        Value("munchings")
      ),
      "{\"select\":[\"favorites\",\"foods\",1]," +
        "\"from\":{\"object\":{\"favorites\":{\"object\":{\"foods\":[]}}}}," +
        "\"default\":\"munchings\"}");
  }

  @Test
  public void shouldSerializeSelectAllFunction() throws Exception {
    assertJson(
      SelectAll(
        Path("foo").at(1),
        Arr(
          Obj("foo", Arr(Value(0), Value(1))),
          Obj("foo", Arr(Value(2), Value(3)))
        )
      ),
      "{\"select_all\":[\"foo\",1],\"from\":[{\"object\":{\"foo\":[0,1]}},{\"object\":{\"foo\":[2,3]}}]}"
    );

    assertJson(
      SelectAll(
        Path(0).at("name"),
        Arr(Obj("name", Value("someone")))
      ),
      "{\"select_all\":[0,\"name\"],\"from\":[{\"object\":{\"name\":\"someone\"}}]}"
    );

    assertJson(
      SelectAll(
        Arr(Value("foo"), Value("bar")),
        Obj("foo", Obj("bar", Arr(Value(1), Value(2))))
      ),
      "{\"select_all\":[\"foo\",\"bar\"],\"from\":{\"object\":{\"foo\":{\"object\":{\"bar\":[1,2]}}}}}");
  }

  @Test
  public void shouldSerializeSelectAsIndexFunction() throws Exception {
    assertJson(
      SelectAsIndex(
        Path("foo").at(1),
        Arr(
          Obj("foo", Arr(Value(0), Value(1))),
          Obj("foo", Arr(Value(2), Value(3)))
        )
      ),
      "{\"select_as_index\":[\"foo\",1],\"from\":[{\"object\":{\"foo\":[0,1]}},{\"object\":{\"foo\":[2,3]}}]}"
    );

    assertJson(
      SelectAsIndex(
        Path(0).at("name"),
        Arr(Obj("name", Value("someone")))
      ),
      "{\"select_as_index\":[0,\"name\"],\"from\":[{\"object\":{\"name\":\"someone\"}}]}"
    );

    assertJson(
      SelectAsIndex(
        Arr(Value("foo"), Value("bar")),
        Obj("foo", Obj("bar", Arr(Value(1), Value(2))))
      ),
      "{\"select_as_index\":[\"foo\",\"bar\"],\"from\":{\"object\":{\"foo\":{\"object\":{\"bar\":[1,2]}}}}}");
  }


  @Test
  public void shouldSerializeAbs() throws Exception {
    assertJson(Abs(Value(-100)), "{\"abs\":-100}");
    assertJson(Abs(-100L), "{\"abs\":-100}");
    assertJson(Abs(-100.0), "{\"abs\":-100.0}");
  }

  @Test
  public void shouldSerializeAcos() throws Exception {
    assertJson(Acos(Value(0)), "{\"acos\":0}");
    assertJson(Acos(0.0), "{\"acos\":0.0}");
  }

  @Test
  public void shouldSerializeAdd() throws Exception {
    assertJson(Add(Value(100), Value(10)), "{\"add\":[100,10]}");
    assertJson(Add(Arrays.asList(Value(100), Value(10))), "{\"add\":[100,10]}");
    assertJson(Add(Arr(Value(100), Value(10))), "{\"add\":[100,10]}");
  }

  @Test
  public void shouldSerializeAsin() throws Exception {
    assertJson(Asin(Value(0)), "{\"asin\":0}");
    assertJson(Asin(0.0), "{\"asin\":0.0}");
  }

  @Test
  public void shouldSerializeAtan() throws Exception {
    assertJson(Atan(Value(0)), "{\"atan\":0}");
  }

  @Test
  public void shouldSerializeBitAnd() throws Exception {
    assertJson(BitAnd(Value(7), Value(3)), "{\"bitand\":[7,3]}");
  }

  @Test
  public void shouldSerializeBitNot() throws Exception {
    assertJson(BitNot(Value(-1)), "{\"bitnot\":-1}");
  }

  @Test
  public void shouldSerializeBitOr() throws Exception {
    assertJson(BitOr(Value(7), Value(3)), "{\"bitor\":[7,3]}");
  }

  @Test
  public void shouldSerializeBitXor() throws Exception {
    assertJson(BitXor(Value(7), Value(3)), "{\"bitxor\":[7,3]}");
  }

  @Test
  public void shouldSerializeCeil() throws Exception {
    assertJson(Ceil(Value(123.456)), "{\"ceil\":123.456}");
  }

  @Test
  public void shouldSerializeCos() throws Exception {
    assertJson(Cos(Value(1)), "{\"cos\":1}");
  }

  @Test
  public void shouldSerializeCosh() throws Exception {
    assertJson(Cosh(Value(1)), "{\"cosh\":1}");
  }

  @Test
  public void shouldSerializeDegrees() throws Exception {
    assertJson(Degrees(Value(1)), "{\"degrees\":1}");
  }

  @Test
  public void shouldSerializeDivide() throws Exception {
    assertJson(Divide(Value(100), Value(10)), "{\"divide\":[100,10]}");
    assertJson(Divide(Arrays.asList(Value(100), Value(10))), "{\"divide\":[100,10]}");
    assertJson(Divide(Arr(Value(100), Value(10))), "{\"divide\":[100,10]}");
  }

  @Test
  public void shouldSerializeExp() throws Exception {
    assertJson(Exp(Value(1)), "{\"exp\":1}");
  }

  @Test
  public void shouldSerializeFloor() throws Exception {
    assertJson(Floor(Value(1)), "{\"floor\":1}");
  }

  @Test
  public void shouldSerializeHypot() throws Exception {
    assertJson(Hypot(Value(3), Value(4)), "{\"hypot\":3,\"b\":4}");
  }

  @Test
  public void shouldSerializeLn() throws Exception {
    assertJson(Ln(Value(1)), "{\"ln\":1}");
  }

  @Test
  public void shouldSerializeLog() throws Exception {
    assertJson(Log(Value(1)), "{\"log\":1}");
  }

  @Test
  public void shouldSerializeMax() throws Exception {
    assertJson(Max(Value(100), Value(10)), "{\"max\":[100,10]}");
    assertJson(Max(Arrays.asList(Value(100), Value(10))), "{\"max\":[100,10]}");
    assertJson(Max(Arr(Value(100), Value(10))), "{\"max\":[100,10]}");
  }

  @Test
  public void shouldSerializeMin() throws Exception {
    assertJson(Min(Value(100), Value(10)), "{\"min\":[100,10]}");
    assertJson(Min(Arrays.asList(Value(100), Value(10))), "{\"min\":[100,10]}");
    assertJson(Min(Arr(Value(100), Value(10))), "{\"min\":[100,10]}");
  }

  @Test
  public void shouldSerializeModulo() throws Exception {
    assertJson(Modulo(Value(100), Value(10)), "{\"modulo\":[100,10]}");
    assertJson(Modulo(Arrays.asList(Value(100), Value(10))), "{\"modulo\":[100,10]}");
    assertJson(Modulo(Arr(Value(100), Value(10))), "{\"modulo\":[100,10]}");
  }

  @Test
  public void shouldSerializeMultiply() throws Exception {
    assertJson(Multiply(Value(100), Value(10)), "{\"multiply\":[100,10]}");
    assertJson(Multiply(Arrays.asList(Value(100), Value(10))), "{\"multiply\":[100,10]}");
    assertJson(Multiply(Arr(Value(100), Value(10))), "{\"multiply\":[100,10]}");
  }

  @Test
  public void shouldSerializePow() throws Exception {
    assertJson(Pow(Value(4)), "{\"pow\":4}");
    assertJson(Pow(Value(8), Value(3)), "{\"pow\":8,\"exp\":3}");
  }

  @Test
  public void shouldSerializeRadians() throws Exception {
    assertJson(Radians(Value(1)), "{\"radians\":1}");
  }

  @Test
  public void shouldSerializeRound() throws Exception {
    assertJson(Round(Value(123.456)), "{\"round\":123.456}");
    assertJson(Round(Value(555.666), Value(2)), "{\"round\":555.666,\"precision\":2}");
  }

  @Test
  public void shouldSerializeSign() throws Exception {
    assertJson(Sign(Value(1)), "{\"sign\":1}");
  }

  @Test
  public void shouldSerializeSin() throws Exception {
    assertJson(Sin(Value(1)), "{\"sin\":1}");
  }

  @Test
  public void shouldSerializeSinh() throws Exception {
    assertJson(Sinh(Value(1)), "{\"sinh\":1}");
  }

  @Test
  public void shouldSerializeSqrt() throws Exception {
    assertJson(Sqrt(Value(1)), "{\"sqrt\":1}");
  }

  @Test
  public void shouldSerializeSubtract() throws Exception {
    assertJson(Subtract(Value(100), Value(10)), "{\"subtract\":[100,10]}");
    assertJson(Subtract(Arrays.asList(Value(100), Value(10))), "{\"subtract\":[100,10]}");
    assertJson(Subtract(Arr(Value(100), Value(10))), "{\"subtract\":[100,10]}");
  }

  @Test
  public void shouldSerializeTan() throws Exception {
    assertJson(Tan(Value(1)), "{\"tan\":1}");
  }

  @Test
  public void shouldSerializeTanh() throws Exception {
    assertJson(Tanh(Value(1)), "{\"tanh\":1}");
  }

  @Test
  public void shouldSerializeTrunc() throws Exception {
    assertJson(Trunc(Value(1)), "{\"trunc\":1}");
    assertJson(Trunc(Value(123.456), Value(2)), "{\"trunc\":123.456,\"precision\":2}");
  }

  @Test
  public void shouldSerializeLT() throws Exception {
    assertJson(LT(Value(1), Value(2), Value(3)), "{\"lt\":[1,2,3]}");
    assertJson(LT(Arrays.asList(Value(1), Value(2), Value(3))), "{\"lt\":[1,2,3]}");
    assertJson(LT(Arr(Value(1), Value(2), Value(3))), "{\"lt\":[1,2,3]}");
  }

  @Test
  public void shouldSerializeLTE() throws Exception {
    assertJson(LTE(Value(1), Value(2), Value(2)), "{\"lte\":[1,2,2]}");
    assertJson(LTE(Arrays.asList(Value(1), Value(2), Value(2))), "{\"lte\":[1,2,2]}");
    assertJson(LTE(Arr(Value(1), Value(2), Value(2))), "{\"lte\":[1,2,2]}");
  }

  @Test
  public void shouldSerializeGT() throws Exception {
    assertJson(GT(Value(3), Value(2), Value(1)), "{\"gt\":[3,2,1]}");
    assertJson(GT(Arrays.asList(Value(3), Value(2), Value(1))), "{\"gt\":[3,2,1]}");
    assertJson(GT(Arr(Value(3), Value(2), Value(1))), "{\"gt\":[3,2,1]}");
  }

  @Test
  public void shouldSerializeGTE() throws Exception {
    assertJson(GTE(Value(3), Value(2), Value(2)), "{\"gte\":[3,2,2]}");
    assertJson(GTE(Arrays.asList(Value(3), Value(2), Value(2))), "{\"gte\":[3,2,2]}");
    assertJson(GTE(Arr(Value(3), Value(2), Value(2))), "{\"gte\":[3,2,2]}");
  }

  @Test
  public void shouldSerializeAnd() throws Exception {
    assertJson(And(Value(true), Value(true), Value(false)), "{\"and\":[true,true,false]}");
    assertJson(And(Arrays.asList(Value(true), Value(true), Value(false))), "{\"and\":[true,true,false]}");
    assertJson(And(Arr(Value(true), Value(true), Value(false))), "{\"and\":[true,true,false]}");
  }

  @Test
  public void shouldSerializeOr() throws Exception {
    assertJson(Or(Value(true), Value(true), Value(false)), "{\"or\":[true,true,false]}");
    assertJson(Or(Arrays.asList(Value(true), Value(true), Value(false))), "{\"or\":[true,true,false]}");
    assertJson(Or(Arr(Value(true), Value(true), Value(false))), "{\"or\":[true,true,false]}");
  }

  @Test
  public void shouldSerializeNot() throws Exception {
    assertJson(Not(Value(true)), "{\"not\":true}");
  }

  @Test
  public void shouldSerializeAt() throws Exception {
    assertJson(At(Value(1L), Get(Native.COLLECTIONS)), "{\"at\":1,\"expr\":{\"get\":{\"@ref\":{\"id\":\"collections\"}}}}");
    assertJson(At(Time(Value("1970-01-01T00:00:00+00:00")), Get(Native.COLLECTIONS)),
      "{\"at\":{\"time\":\"1970-01-01T00:00:00+00:00\"},\"expr\":{\"get\":{\"@ref\":{\"id\":\"collections\"}}}}");
    assertJson(At(Instant.ofEpochMilli(0), Get(Native.COLLECTIONS)),
      "{\"at\":{\"@ts\":\"1970-01-01T00:00:00Z\"},\"expr\":{\"get\":{\"@ref\":{\"id\":\"collections\"}}}}");
  }

  @Test
  public void shouldSerializeToString() throws Exception {
    assertJson(ToString(Value(100)), "{\"to_string\":100}");
  }

  @Test
  public void shouldSerializeToNumber() throws Exception {
    assertJson(ToString(Value("100")), "{\"to_string\":\"100\"}");
  }

  @Test
  public void shouldSerializeToTime() throws Exception {
    assertJson(ToTime(Value("1970-01-01T00:00:00Z")), "{\"to_time\":\"1970-01-01T00:00:00Z\"}");
  }

  @Test
  public void shouldSerializeToDate() throws Exception {
    assertJson(ToDate(Value("1970-01-01")), "{\"to_date\":\"1970-01-01\"}");
  }

  @Test
  public void shouldSerializeMerge() throws Exception {
    assertJson(Merge(Obj("x", Value(10)), Obj("y", Value(20))),
      "{\"merge\":{\"object\":{\"x\":10}},\"with\":{\"object\":{\"y\":20}}}");
    assertJson(Merge(Obj("x", Value(10)), Obj("y", Value(20)), Lambda("x", Var("x"))),
      "{\"merge\":{\"object\":{\"x\":10}},\"with\":{\"object\":{\"y\":20}},\"lambda\":{\"lambda\":\"x\",\"expr\":{\"var\":\"x\"}}}");
  }

  @Test
  public void shouldPrintUsefully() throws Exception {
    Map<String, Value> k1 = new java.util.HashMap<>();
    k1.put("k1", new StringV("v1"));

    assertThat(new ObjectV(k1).toString(), equalTo("{k1: \"v1\"}"));
    assertThat(new ArrayV(Arrays.asList(new StringV("v1"), new StringV("v2"))).toString(),
               equalTo("[\"v1\", \"v2\"]"));
    assertThat(BooleanV.valueOf(true).toString(), equalTo("true"));
    assertThat(new DoubleV(3.14).toString(), equalTo("3.14"));
    assertThat(new LongV(42).toString(), equalTo("42"));
    assertThat(new StringV("string").toString(), equalTo("\"string\""));
    assertThat(NullV.NULL.toString(), equalTo("null"));
    assertThat(new TimeV(Instant.ofEpochMilli(0)).toString(), equalTo("1970-01-01T00:00:00Z"));
    assertThat(new DateV(LocalDate.ofEpochDay(0)).toString(), equalTo("1970-01-01"));
    assertThat(new BytesV("DEADBEEF").toString(), equalTo("[0x0c 0x40 0x03 0x04 0x41 0x05]"));
    assertThat(new SetRefV(k1).toString(), equalTo("{@set = {k1: \"v1\"}}"));
    assertThat(Native.COLLECTIONS.toString(), equalTo("ref(id = \"collections\")"));
    assertThat(new RefV("42",
                        new RefV("people", Native.COLLECTIONS),
                        new RefV("db", Native.DATABASES)).toString(),
               equalTo("ref(id = \"42\", collection = ref(id = \"people\", collection = ref(id = \"collections\")), database = ref(id = \"db\", collection = ref(id = \"databases\")))"));
  }

  @Test
  public void shouldSerializeTheDocumentsFunction() throws Exception {
    assertJson(Documents(Collection("foo")), "{\"documents\":{\"collection\":\"foo\"}}");
  }

  private void assertJson(Expr expr, String jsonString) throws JsonProcessingException {
    assertThat(json.writeValueAsString(expr),
      equalTo(jsonString));
  }
}
