package com.atlas

import cats.effect.{ExitCode, IO, IOApp, Resource}
import ch.linkyard.mcp.jsonrpc2.transport.StdioJsonRpcConnection
import ch.linkyard.mcp.server.McpServer

import com.atlas.api.AtlasServer
import com.atlas.core.Atlas

import java.nio.file.{Files, Path}

object Main extends IOApp:

  private val dataDir: Path  = Path.of(sys.props("user.home"), ".atlas")
  private val dataFile: Path = dataDir.resolve("db.jsonl")

  private def log(msg: String): IO[Unit] = IO.blocking(System.err.println(msg))

  override def run(args: List[String]): IO[ExitCode] =
    IO.interruptible(Files.createDirectories(dataDir)) >>
      Resource
        .make(IO.interruptible(Atlas(dataFile)))(a => a.compact)
        .use: atlas =>
          for
            _     <- atlas.load
            _     <- log(s"atlas ready, data=$dataFile")
            server = AtlasServer(atlas).server
            conn   = StdioJsonRpcConnection.create[IO]
            _     <- McpServer.start[IO](server)(conn, (_: Exception) => IO.unit).useForever
          yield ExitCode.Success
