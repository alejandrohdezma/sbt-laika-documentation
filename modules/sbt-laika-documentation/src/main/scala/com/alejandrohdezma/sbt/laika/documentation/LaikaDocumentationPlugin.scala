/*
 * Copyright 2026 Alejandro Hernández <https://github.com/alejandrohdezma>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alejandrohdezma.sbt.laika.documentation

import sbt.Keys._
import sbt._

import laika.config.SyntaxHighlighting
import laika.format.Markdown
import laika.sbt.LaikaPlugin
import laika.sbt.LaikaPlugin.autoImport._
import mdoc.MdocPlugin
import mdoc.MdocPlugin.autoImport._
import pink.cozydev.protosearch.laika.IndexConfig

/** SBT AutoPlugin that builds a documentation website with Laika, looking like a Docusaurus site.
  *
  * Enable it on the project that holds the documentation (usually `documentation`):
  *
  * {{{
  * lazy val documentation = project.enablePlugins(MdocPlugin, LaikaDocumentationPlugin)
  * }}}
  *
  * The Markdown pages in `documentation/docs` go through mdoc first, then Laika renders them with the Helium theme, a
  * landing page, client-side search (protosearch) and the static files in `documentation/static`. `laikaSite` writes
  * the site to `docs` at the root of the build, so GitHub Pages can serve it from the `main` branch.
  *
  * Title, description and repository links come from `name`, `description`, `scmInfo` and `homepage`.
  */
object LaikaDocumentationPlugin extends AutoPlugin {

  override def requires: Plugins = LaikaPlugin && MdocPlugin

  object autoImport {

    val documentationTitle = settingKey[String] {
      "Title shown in the top bar, the landing page and the browser tab (default: the repository name, then `name`)"
    }

    val documentationDescription = settingKey[Option[String]] {
      "Tagline shown on the landing page and in the page metadata (default: `description`, when it differs from `name`)"
    }

    val documentationRepository = settingKey[Option[URL]] {
      "Repository linked from the top bar and used for \"Edit this page\" links (default: `scmInfo`, then `homepage`)"
    }

    val documentationBranch = settingKey[String] {
      "Branch that \"Edit this page\" links point to (default: main)"
    }

    val documentationPrimaryColor = settingKey[String] {
      "Primary color, as a CSS hex value (default: #25c2a0)"
    }

    val documentationPrimaryColorDark = settingKey[String] {
      "Primary color in dark mode, as a CSS hex value (default: documentationPrimaryColor)"
    }

    val documentationStartPage = settingKey[String] {
      "Page, relative to the source directory, linked from \"Docs\" and \"Get Started\" (default: getting-started.md)"
    }

    val documentationLogo = settingKey[Option[String]] {
      "Logo shown in the top bar, relative to the static directory (default: img/logo.svg, if it exists)"
    }

    val documentationFavicon = settingKey[Option[String]] {
      "Favicon, relative to the static directory (default: img/favicon.ico, if it exists)"
    }

    val documentationSourceDirectory = settingKey[File] {
      "Directory with the Markdown pages (default: `docs` inside the project)"
    }

    val documentationStaticDirectory = settingKey[File] {
      "Directory with images and other static files (default: `static` inside the project)"
    }

    val documentationOutputDirectory = settingKey[File] {
      "Directory the site is written to (default: `docs` at the root of the build)"
    }

  }

  import autoImport._

  private val mdocDocumentation = Def.taskDyn {
    mdoc.toTask(s" --in ${documentationSourceDirectory.value} --out ${target.value / "mdoc"}")
  }

  override def projectSettings: Seq[Setting[_]] = Seq(
    documentationTitle := documentationRepository.value
      .flatMap(_.getPath.split("/").filter(_.nonEmpty).lastOption)
      .getOrElse(name.value),
    documentationDescription      := Option(description.value).filter(_.nonEmpty).filterNot(_ == name.value),
    documentationRepository       := scmInfo.value.map(_.browseUrl).orElse(homepage.value),
    documentationBranch           := "main",
    documentationPrimaryColor     := "#25c2a0",
    documentationPrimaryColorDark := documentationPrimaryColor.value,
    documentationStartPage        := "getting-started.md",
    documentationSourceDirectory  := baseDirectory.value / "docs",
    documentationStaticDirectory  := baseDirectory.value / "static",
    documentationOutputDirectory  := (ThisBuild / baseDirectory).value / "docs",
    documentationLogo             := Some("img/logo.svg").filter(documentationStaticDirectory.value./(_).exists),
    documentationFavicon          := Some("img/favicon.ico").filter(documentationStaticDirectory.value./(_).exists),
    Laika / sourceDirectories     := (target.value / "mdoc") +: Seq(documentationStaticDirectory.value).filter(_.exists),
    laikaSite / target            := documentationOutputDirectory.value,
    laikaSite                     := laikaSite.dependsOn(mdocDocumentation).value,
    laikaPreview                  := laikaPreview.dependsOn(mdocDocumentation).value,
    laikaExtensions               := Seq(Markdown.GitHubFlavor, SyntaxHighlighting),
    laikaRenderers                += IndexConfig.default.config,
    laikaConfig                   := {
      val base = laikaConfig.value
        .withConfigValue("documentation.title", documentationTitle.value)
        .withConfigValue("documentation.startPage", s"/${documentationStartPage.value}")

      val withDescription =
        documentationDescription.value.fold(base)(base.withConfigValue("documentation.description", _))

      documentationLogo.value.fold(withDescription)(logo =>
        withDescription.withConfigValue("documentation.logo", s"/$logo")
      )
    },
    laikaTheme := {
      val repository = documentationRepository.value.map(_.toString.stripSuffix("/"))

      val editUrl = for {
        repo <- repository
        path <- IO.relativize((ThisBuild / baseDirectory).value, documentationSourceDirectory.value)
      } yield s"$repo/edit/${documentationBranch.value}/$path"

      DocumentationTheme(
        title = documentationTitle.value, description = documentationDescription.value, repository = repository,
        editUrl = editUrl, primaryColor = documentationPrimaryColor.value,
        primaryColorDark = documentationPrimaryColorDark.value, favicon = documentationFavicon.value
      )
    }
  )

}
