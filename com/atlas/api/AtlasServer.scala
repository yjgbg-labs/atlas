package com.atlas.api

import cats.effect.{IO, Resource}
import ch.linkyard.mcp.protocol.{Content, Initialize, Meta}
import ch.linkyard.mcp.protocol.Tool.CallTool
import ch.linkyard.mcp.server.*
import com.atlas.core.Atlas
import io.circe.{Json, JsonObject}
import io.circe.syntax.*

final class AtlasServer(atlas: Atlas):

  private def toolInfo(name: String, desc: String): ToolFunction.Info =
    ToolFunction.Info(name, None, Some(desc), ToolFunction.Effect.Additive(false), false)

  private def textResult(json: Json): CallTool.Response =
    CallTool.Response.Success(
      List(Content.Text(json.noSpaces, None, Meta.empty)),
      None,
      Meta.empty,
    )

  private val insertTool: ToolFunction[IO] =
    ToolFunction.native[IO](
      toolInfo("insert", "Insert a triple. Always creates a new entry (no dedup)."),
      JsonObject(
        "type"       -> Json.fromString("object"),
        "properties" -> Json.obj(
          "subject"   -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("The subject")),
          "predicate" -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("The predicate")),
          "object"    -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("The object")),
        ),
        "required" -> Json.arr(Json.fromString("subject"), Json.fromString("predicate"), Json.fromString("object")),
      ),
      (args, _) =>
        for
          s <- IO.fromEither(args.toJson.hcursor.get[String]("subject"))
          p <- IO.fromEither(args.toJson.hcursor.get[String]("predicate"))
          o <- IO.fromEither(args.toJson.hcursor.get[String]("object"))
          e <- atlas.insert(s, p, o)
        yield textResult(e.asJson),
    )

  private val searchTool: ToolFunction[IO] =
    ToolFunction.native[IO](
      toolInfo("search", "Full-text search across all triples. Space-separated keywords, OR matching."),
      JsonObject(
        "type"       -> Json.fromString("object"),
        "properties" -> Json.obj(
          "keywords" -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("Search keywords"))
        ),
        "required" -> Json.arr(Json.fromString("keywords")),
      ),
      (args, _) =>
        for
          kw   <- IO.fromEither(args.toJson.hcursor.get[String]("keywords"))
          hits <- atlas.search(kw)
        yield textResult(hits.asJson),
    )

  private val retrieveTool: ToolFunction[IO] =
    ToolFunction.native[IO](
      toolInfo("retrieve", "Retrieve all triples of a subject, with their relations to other subjects."),
      JsonObject(
        "type"       -> Json.fromString("object"),
        "properties" -> Json.obj(
          "subject" -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("Subject name"))
        ),
        "required" -> Json.arr(Json.fromString("subject")),
      ),
      (args, _) =>
        for
          s   <- IO.fromEither(args.toJson.hcursor.get[String]("subject"))
          ret <- atlas.retrieve(s)
        yield textResult(ret.fold(Json.obj("error" -> Json.fromString("not found")))(_.asJson)),
    )

  private val deleteTool: ToolFunction[IO] =
    ToolFunction.native[IO](
      toolInfo("delete", "Delete a triple by its subject, predicate, and object."),
      JsonObject(
        "type"       -> Json.fromString("object"),
        "properties" -> Json.obj(
          "subject"   -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("Subject")),
          "predicate" -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("Predicate")),
          "object"    -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("Object")),
        ),
        "required" -> Json.arr(Json.fromString("subject"), Json.fromString("predicate"), Json.fromString("object")),
      ),
      (args, _) =>
        for
          s       <- IO.fromEither(args.toJson.hcursor.get[String]("subject"))
          p       <- IO.fromEither(args.toJson.hcursor.get[String]("predicate"))
          o       <- IO.fromEither(args.toJson.hcursor.get[String]("object"))
          deleted <- atlas.delete(s, p, o)
        yield textResult(Json.obj("deleted" -> Json.fromBoolean(deleted))),
    )

  val server: McpServer[IO] =
    (_, _) =>
      Resource.pure(new McpServer.Session[IO] with McpServer.ToolProvider[IO]:
        override val serverInfo: Initialize.PartyInfo =
          Initialize.PartyInfo("atlas", "0.1.0")
        override val instructions: IO[Option[String]] =
          IO.pure(Some(
            "A triple store. insert(subject, predicate, object) to add a triple. " +
            "search(keywords) to find triples. " +
            "retrieve(subject) to get all triples of a subject with relations. " +
            "delete(subject, predicate, object) to remove a triple."
          ))
        override val tools: IO[List[ToolFunction[IO]]] =
          IO.pure(List(insertTool, searchTool, retrieveTool, deleteTool))
      )
