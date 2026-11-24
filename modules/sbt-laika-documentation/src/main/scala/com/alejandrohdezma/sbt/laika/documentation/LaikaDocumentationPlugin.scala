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

import cats.effect.Async
import cats.effect.Resource

import sbt.Keys._
import sbt._

import laika.api.config.ConfigValue
import laika.ast.LengthUnit.px
import laika.ast.Path
import laika.ast.Path.Root
import laika.config.SyntaxHighlighting
import laika.format.Markdown
import laika.helium.Helium
import laika.helium.config._
import laika.io.model.InputTree
import laika.sbt.LaikaPlugin
import laika.sbt.LaikaPlugin.autoImport._
import laika.theme.Theme
import laika.theme.ThemeBuilder
import laika.theme.ThemeProvider
import laika.theme.config.Color
import mdoc.MdocPlugin
import mdoc.MdocPlugin.autoImport._
import pink.cozydev.protosearch.laika.IndexConfig
import pink.cozydev.protosearch.ui.SearchUI

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
  * the site to `public` at the root of the build, where sbt-ci's release workflow deploys it to GitHub Pages.
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
      "Tagline shown on the landing page and in the page metadata (default: `description`, when it differs from `name` ignoring case)"
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
      "Directory the site is written to (default: `public` at the root of the build)"
    }

    val documentationHelium = settingKey[Helium => Helium] {
      "Customizes the Helium theme after the plugin has configured it (default: no changes)"
    }

  }

  import autoImport._

  override def projectSettings: Seq[Setting[_]] = Seq(
    documentationTitle := documentationRepository.value
      .flatMap(_.getPath.split("/").filter(_.nonEmpty).lastOption)
      .getOrElse(name.value),
    documentationDescription      := Option(description.value).filter(_.nonEmpty).filterNot(_.equalsIgnoreCase(name.value)),
    documentationRepository       := scmInfo.value.map(_.browseUrl).orElse(homepage.value),
    documentationBranch           := "main",
    documentationPrimaryColor     := "#25c2a0",
    documentationPrimaryColorDark := documentationPrimaryColor.value,
    documentationStartPage        := "getting-started.md",
    documentationSourceDirectory  := baseDirectory.value / "docs",
    documentationStaticDirectory  := baseDirectory.value / "static",
    documentationOutputDirectory  := (ThisBuild / baseDirectory).value / "public",
    documentationLogo             := Some("img/logo.svg").filter(documentationStaticDirectory.value./(_).exists),
    documentationFavicon          := Some("img/favicon.ico").filter(documentationStaticDirectory.value./(_).exists),
    documentationHelium           := identity,
    Laika / sourceDirectories     := (target.value / "mdoc") +: Seq(documentationStaticDirectory.value).filter(_.exists),
    laikaSite / target            := documentationOutputDirectory.value,
    laikaSite                     := laikaSite.dependsOn(mdocDocumentation).value,
    laikaPreview                  := laikaPreview.dependsOn(mdocDocumentation).value,
    laikaExtensions               := Seq(Markdown.GitHubFlavor, SyntaxHighlighting),
    laikaRenderers                += IndexConfig.default.config,
    laikaConfig                   := laikaConfig.value
      .withConfigValue("documentation.title", documentationTitle.value)
      .withConfigValue("documentation.startPage", s"/${documentationStartPage.value}")
      .withConfigValue("documentation.description", optional(documentationDescription.value))
      .withConfigValue("documentation.logo", optional(documentationLogo.value.map(logo => s"/$logo"))),
    laikaTheme := theme.value
  )

  /** Builds the Helium theme: Helium configured to look like a Docusaurus site, protosearch for search, and the
    * templates, stylesheet and script shipped in this plugin's resources.
    */
  private val theme = Def.setting {
    val primary     = Color.hex(documentationPrimaryColor.value.stripPrefix("#"))
    val primaryDark = Color.hex(documentationPrimaryColorDark.value.stripPrefix("#"))

    val repository = documentationRepository.value.map(_.toString.stripSuffix("/"))

    val editUrl = for {
      repo <- repository
      path <- IO.relativize((ThisBuild / baseDirectory).value, documentationSourceDirectory.value)
    } yield s"$repo/edit/${documentationBranch.value}/$path"

    val helium = Helium.defaults.site
      .metadata(
        title = Some(documentationTitle.value),
        description = documentationDescription.value,
        language = Some("en")
      )
      .site
      .clearFontResources
      .site
      .fontSizes(
        body = px(16), code = px(15.2), title = px(48), header2 = px(32), header3 = px(24), header4 = px(20),
        small = px(12.8)
      )
      .site
      .layout(
        contentWidth = px(1140), navigationWidth = px(300), topBarHeight = px(60), defaultBlockSpacing = px(20),
        defaultLineHeight = 1.65, anchorPlacement = AnchorPlacement.Right
      )
      .site
      .themeColors(
        primary = primary,
        primaryMedium = Color.hex("dadde1"),
        primaryLight = Color.hex("f6f8fa"),
        secondary = Color.hex("1c1e21"),
        text = Color.hex("1c1e21"),
        background = Color.hex("ffffff"),
        bgGradient = (primary, primary)
      )
      .site
      .syntaxHighlightingColors(
        base = ColorQuintet(
          Color.hex("f6f8fa"), Color.hex("999988"), Color.hex("393a34"), Color.hex("393a34"), Color.hex("393a34")
        ),
        wheel = ColorQuintet(
          Color.hex("00a4db"), Color.hex("00a4db"), Color.hex("d73a49"), Color.hex("e3116c"), Color.hex("6f42c1")
        )
      )
      .site
      .darkMode
      .themeColors(
        primary = primaryDark,
        primaryMedium = Color.hex("444950"),
        primaryLight = Color.hex("242526"),
        secondary = Color.hex("e3e3e3"),
        text = Color.hex("e3e3e3"),
        background = Color.hex("1b1b1d"),
        bgGradient = (Color.hex("1c1c1d"), Color.hex("1c1c1d"))
      )
      .site
      .darkMode
      .syntaxHighlightingColors(
        base = ColorQuintet(
          Color.hex("282a36"), Color.hex("6272a4"), Color.hex("f8f8f2"), Color.hex("f8f8f2"), Color.hex("f8f8f2")
        ),
        wheel = ColorQuintet(
          Color.hex("50fa7b"), Color.hex("bd93f9"), Color.hex("50fa7b"), Color.hex("ff79c6"), Color.hex("ffb86c")
        )
      )
      .site
      .favIcons(documentationFavicon.value.map(path => Favicon.internal(Path.parse(s"/$path"), "32x32")).toList: _*)
      .site
      .topNavigationBar(navLinks = repository.map(IconLink.external(_, HeliumIcon.github)).toList)
      .site
      .mainNavigation(depth = 2)
      .site
      .pageNavigation(depth = 3, sourceBaseURL = editUrl, sourceLinkText = "Edit this page")
      .site
      .internalCSS(Root / "laika-documentation" / "documentation.css")
      .site
      .internalJS(Root / "laika-documentation" / "documentation.js")
      .site
      .landingPage(title = Some(documentationTitle.value), subtitle = documentationDescription.value)
      .extendWith(SearchUI)
      .extendWith {
        new ThemeProvider {

          def build[F[_]: Async]: Resource[F, Theme[F]] = {

            val inputs = InputTree[F]
              .addClassLoaderResource(
                "com/alejandrohdezma/sbt/laika/documentation/default.template.html",
                Root / "default.template.html"
              )
              .addClassLoaderResource(
                "com/alejandrohdezma/sbt/laika/documentation/landing.template.html",
                Root / "landing.template.html"
              )
              .addClassLoaderResource(
                "com/alejandrohdezma/sbt/laika/documentation/head.template.html",
                Root / "helium" / "templates" / "head.template.html"
              )
              .addClassLoaderResource(
                "com/alejandrohdezma/sbt/laika/documentation/topNav.template.html",
                Root / "helium" / "templates" / "topNav.template.html"
              )
              .addClassLoaderResource(
                "com/alejandrohdezma/sbt/laika/documentation/documentation.css",
                Root / "laika-documentation" / "documentation.css"
              )
              .addClassLoaderResource(
                "com/alejandrohdezma/sbt/laika/documentation/documentation.js",
                Root / "laika-documentation" / "documentation.js"
              )

            ThemeBuilder[F]("sbt-laika-documentation").addInputs(inputs).build
          }
        }
      }

    documentationHelium.value(helium).build
  }

  private def optional(value: Option[String]): ConfigValue =
    value.fold[ConfigValue](ConfigValue.NullValue)(ConfigValue.StringValue(_))

  private val mdocDocumentation = Def.taskDyn {
    mdoc.toTask(s" --in ${documentationSourceDirectory.value} --out ${target.value / "mdoc"}")
  }

}
