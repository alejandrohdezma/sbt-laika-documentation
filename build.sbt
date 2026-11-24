ThisBuild / scalaVersion           := _root_.scalafix.sbt.BuildInfo.scala212
ThisBuild / organization           := "com.alejandrohdezma"
ThisBuild / versionPolicyIntention := Compatibility.BinaryAndSourceCompatible

addCommandAlias("ci-test", "fix --check; versionPolicyCheck; mdoc; test; publishLocal; scripted")
addCommandAlias("ci-docs", "github; mdoc; headerCreateAll")
addCommandAlias("ci-publish", "versionCheck; github; ci-release")

lazy val documentation = project.enablePlugins(MdocPlugin)

lazy val `sbt-laika-documentation` = module
  .enablePlugins(SbtPlugin)
  .settings(scriptedLaunchOpts += s"-Dplugin.version=${version.value}")
  .settings(scriptedLaunchOpts += s"-Dsbt.boot.directory=${sys.props("user.home")}/.sbt/boot")
