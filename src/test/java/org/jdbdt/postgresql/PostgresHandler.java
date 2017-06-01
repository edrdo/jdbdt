package org.jdbdt.postgresql;

import java.nio.file.FileSystems;
import java.nio.file.Path;

import org.jdbdt.BuildEnvironment;

import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres;
import ru.yandex.qatools.embed.postgresql.distribution.Version;

@SuppressWarnings("javadoc")
public interface PostgresHandler {
  String start();
  void stop();

  PostgresHandler AppVeyorHandler = new PostgresHandler() {
    @Override
    public String start() {
      return "jdbc:postgresql://localhost:5432/postgres?user=postgres&password=Password12!";
    }
    @Override
    public void stop() { }

  };
  PostgresHandler EmbeddedHandler = new PostgresHandler() {
    EmbeddedPostgres postgres;

    @Override
    public String start() {
      postgres = new EmbeddedPostgres(Version.V9_6_2);
      Path cachePath = FileSystems.getDefault().getPath(System.getProperty("user.home") + "/.embedpostgresql/");
      try {
        return postgres.start(EmbeddedPostgres.cachedRuntimeConfig(cachePath));
      } catch (Exception e) {
        throw new InternalError(e);
      }
    }

    @Override
    public void stop() {
      postgres.stop();
    }
  };

  static PostgresHandler get() {
    PostgresHandler h;
    switch (BuildEnvironment.get()) {
      case AppVeyor:
        h = AppVeyorHandler;
        break;
      default:
        h = EmbeddedHandler;
    }
    return h;
  }

}
