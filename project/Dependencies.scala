import sbt.*

object Dependencies {

  object V {
    val scala = "3.9.0"

    /**
     * The ankka this release builds against. `-Dankka.version=<v>` overrides it, for a locally
     * published one.
     */
    val ankka = sys.props.getOrElse("ankka.version", "0.16.3")

    val logback = "1.6.3"
    val munit   = "1.3.6"
  }

  val ankkaSdk     = "com.thinkmorestupidless" %% "ankka-sdk"     % V.ankka
  val ankkaRuntime = "com.thinkmorestupidless" %% "ankka-runtime" % V.ankka
  val ankkaHttp    = "com.thinkmorestupidless" %% "ankka-http"    % V.ankka
  val ankkaTestkit = "com.thinkmorestupidless" %% "ankka-testkit" % V.ankka

  val logback = "ch.qos.logback" % "logback-classic" % V.logback
  val munit   = "org.scalameta" %% "munit"           % V.munit
}
