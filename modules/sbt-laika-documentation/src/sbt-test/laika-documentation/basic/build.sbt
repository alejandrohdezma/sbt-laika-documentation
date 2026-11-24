ThisBuild / scalaVersion := "2.13.18"

lazy val documentation = project
  .enablePlugins(LaikaDocumentationPlugin)
  .settings(name := "my-library")
  .settings(mdocIn := file(".github") / "docs", mdocOut := file("."), mdocVariables += "NAME" -> name.value)
  .settings(description := "A library that does things")
  .settings(scmInfo := Some(ScmInfo(url("https://github.com/example/my-library"), "scm:git@github.com:example/my-library.git")))
  .settings(documentationLogo := Some("img/fast.svg"))
  .settings(documentationPrimaryColorDark := "#f7d0e2")
  .settings(documentationHelium := (_.site.metadata(language = Some("es"))))

val checkSite = taskKey[Unit]("Checks the generated site")

checkSite := {
  val site = (ThisBuild / baseDirectory).value / "public"

  def contains(file: String, expected: String*): Unit = {
    val content = IO.read(site / file)
    expected.foreach { text =>
      assert(content.contains(text), s"$file does not contain: $text")
    }
  }

  contains("index.html", "<title>my-library</title>", "A library that does things", "It does things fast", "img/fast.svg", "No image here")
  contains("index.html", "id=\"search-button\"", "laika-documentation/documentation.css", "laika-documentation/documentation.js")
  assert(!IO.read(site / "index.html").contains("search-top-bar"), "index.html uses protosearch's top bar instead of ours")
  contains(
    "getting-started.html",
    "<title>Getting Started | my-library</title>",
    "https://github.com/example/my-library/edit/main/documentation/docs/getting-started.md",
    "https://github.com/example/my-library\"",
    "guides/configuration.html"
  )
  contains("guides/configuration.html", "<title>Configuration | my-library</title>", "../getting-started.html")

  val link   = "<a class=\"nav-item\" href=\"https://example.com\" target=\"_blank\" rel=\"noopener noreferrer\">Example"
  val footer = "<div class=\"footer__copyright\">Built by <a href=\"https://example.com\">Example</a></div>"
  val logo   = "<a class=\"footer__logo\" href=\"https://example.com\""
  contains("index.html", link, footer, logo)
  contains("getting-started.html", link, footer, logo, "<img src=\"img/fast.svg\" alt=\"my-library logo\"></a>")
  contains("guides/configuration.html", link, footer, logo, "<img src=\"../img/fast.svg\" alt=\"my-library logo\"></a>")
  contains("helium/site/laika-helium.css", "#25c2a0", "#f7d0e2")
  contains("index.html", "<html lang=\"es\">")
}

val checkReadme = taskKey[Unit]("Checks the README written by the project's own mdoc run")

checkReadme := {
  val readme = IO.read((ThisBuild / baseDirectory).value / "README.md")
  assert(readme.contains("# my-library"), s"README.md was not generated from .github/docs: $readme")
}
