package com.atlas.core

import cats.effect.*
import com.github.benmanes.caffeine.cache.Cache
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import io.circe.parser.*
import io.circe.syntax.*

import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.*

final class Atlas(dbPath: Path):

  import Atlas.Entry

  private case class Row(id: UUID, entry: Entry)

  private val cache: Cache[UUID, Row] =
    com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
      .maximumSize(100_000)
      .build[UUID, Row]()

  // ---- lifecycle ----

  def load: IO[Unit] =
    IO.interruptible:
      if Files.exists(dbPath) then
        Files.readAllLines(dbPath).asScala.foreach: line =>
          decode[LogLine](line).foreach:
            case LogLine("+", row) => cache.put(row.id, row)
            case LogLine("-", row) =>
              val e = row.entry
              cache.asMap().asScala.filter((_, r) =>
                r.entry.subject == e.subject && r.entry.predicate == e.predicate && r.entry.`object` == e.`object`
              ).foreach: (id, _) =>
                cache.invalidate(id)
            case _ => ()

  def compact: IO[Unit] = IO.interruptible:
    val rows  = cache.asMap().asScala.values.toList
    val tmp   = dbPath.resolveSibling("db.tmp.jsonl")
    val lines = rows.map(r => LogLine("+", r).asJson.noSpaces).mkString("\n")
    Files.writeString(tmp, lines + "\n")
    Files.move(tmp, dbPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE)

  // ---- write ----

  def insert(subject: String, predicate: String, `object`: String): IO[Entry] =
    IO.interruptible:
      val entry = Entry(subject, predicate, `object`)
      val row   = Row(UUID.randomUUID(), entry)
      cache.put(row.id, row)
      append(LogLine("+", row))
      entry

  def delete(subject: String, predicate: String, `object`: String): IO[Boolean] =
    IO.interruptible:
      val matches = cache.asMap().asScala.filter: (_, row) =>
        row.entry.subject == subject && row.entry.predicate == predicate && row.entry.`object` == `object`
      if matches.isEmpty then false
      else
        matches.foreach: (id, row) =>
          cache.invalidate(id)
          append(LogLine("-", row))
        true

  // ---- query ----

  def search(keywords: String): IO[List[Entry]] = IO.interruptible:
    val terms = keywords.split("\\s+").map(_.toLowerCase).toList
    val all   = cache.asMap().asScala.values.map(_.entry).toList
    all.filter: e =>
      val haystack = s"${e.subject} ${e.predicate} ${e.`object`}".toLowerCase
      terms.exists(haystack.contains)

  def retrieve(subject: String): IO[Option[Atlas.Retrieved]] = IO.interruptible:
    val entries = cache.asMap().asScala.values.map(_.entry).toList
    val mine    = entries.filter(_.subject == subject)
    if mine.isEmpty then None
    else
      val mineObjects = mine.map(_.`object`).toSet
      val direct   = entries.filter(e => e.subject != subject && mineObjects.contains(e.subject))
      val indirect = entries.filter(e =>
        e.subject != subject && !mineObjects.contains(e.subject) &&
        direct.exists(d => d.`object` == e.subject || e.subject == d.`object`)
      )
      Some(Atlas.Retrieved(mine, direct.take(10), indirect.take(20)))

  // ---- private ----

  private def append(line: LogLine): Unit =
    Files.writeString(dbPath, line.asJson.noSpaces + "\n",
      StandardOpenOption.CREATE, StandardOpenOption.APPEND)

  private given Encoder[Row] = deriveEncoder
  private given Decoder[Row] = deriveDecoder
  private case class LogLine(op: String, row: Row)
  private given Encoder[LogLine] = deriveEncoder
  private given Decoder[LogLine] = deriveDecoder

object Atlas:
  final case class Entry(
      subject: String,
      predicate: String,
      `object`: String,
      createdAt: Instant = Instant.now(),
  )
  object Entry:
    given Encoder[Entry] = deriveEncoder
    given Decoder[Entry] = deriveDecoder

  final case class Retrieved(subject: List[Entry], direct: List[Entry], indirect: List[Entry])
  object Retrieved:
    given Encoder[Retrieved] = deriveEncoder
    given Decoder[Retrieved] = deriveDecoder
