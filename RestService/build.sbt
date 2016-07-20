name := "rest-service"

organization := "vanner"

version := "1.0.0-SNAPSHOT"

enablePlugins(_root_.sbtdocker.DockerPlugin)

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  "codecraft" %% "cloud" % "1.0.0-SNAPSHOT",
  "vanner" %% "user-model" % "1.0.0-SNAPSHOT",
  "vanner" %% "friend-model" % "1.0.0-SNAPSHOT"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

dockerfile in docker := {
	val jarFile: File = sbt.Keys.`package`.in(Compile, packageBin).value
  val classpath = (managedClasspath in Compile).value
  val mainclass = mainClass.in(Compile, packageBin).value.getOrElse(sys.error("Expected exactly one main class"))
  val jarTarget = s"/app/${jarFile.getName}"
  // Make a colon separated classpath with the JAR file
  val classpathString = classpath.files.map("/app/" + _.getName)
    .mkString(":") + ":" + jarTarget
  new Dockerfile {
    // Base image
    from("anapsix/alpine-java")
    // Add all files on the classpath
    add(classpath.files, "/app/")
    // Add the JAR file
    add(jarFile, jarTarget)
    // On launch run Java with the classpath and the main class
    entryPoint("java", "-Xms64m", "-Xmx512M", "-cp", classpathString, mainclass)
  }
}
