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

import laika.ast.LengthUnit.px
import laika.ast.Path
import laika.ast.Path.Root
import laika.helium.Helium
import laika.helium.config._
import laika.io.model.InputTree
import laika.theme.Theme
import laika.theme.ThemeBuilder
import laika.theme.ThemeProvider
import laika.theme.config.Color
import pink.cozydev.protosearch.ui.SearchUI

/** Builds the Helium theme used by [[LaikaDocumentationPlugin]]: Helium configured to look like a Docusaurus site,
  * protosearch for search, and the templates, stylesheet and script shipped in this plugin's resources.
  */
private[documentation] object DocumentationTheme {

  private val resources = "com/alejandrohdezma/sbt/laika/documentation"

  private val files: List[(String, Path)] = List(
    "default.template.html" -> Root / "default.template.html",
    "landing.template.html" -> Root / "landing.template.html",
    "head.template.html"    -> Root / "helium" / "templates" / "head.template.html",
    "topNav.template.html"  -> Root / "helium" / "templates" / "topNav.template.html",
    "documentation.css"     -> Root / "laika-documentation" / "documentation.css",
    "documentation.js"      -> Root / "laika-documentation" / "documentation.js"
  )

  private val templates: ThemeProvider = new ThemeProvider {

    def build[F[_]: Async]: Resource[F, Theme[F]] = {
      val inputs = files.foldLeft(InputTree[F]) { case (tree, (name, path)) =>
        tree.addClassLoaderResource(s"$resources/$name", path)
      }

      ThemeBuilder[F]("sbt-laika-documentation").addInputs(inputs).build
    }

  }

  def apply(
      title: String,
      description: Option[String],
      repository: Option[String],
      editUrl: Option[String],
      primaryColor: String,
      primaryColorDark: String,
      favicon: Option[String]
  ): ThemeProvider = {
    val primary     = Color.hex(primaryColor.stripPrefix("#"))
    val primaryDark = Color.hex(primaryColorDark.stripPrefix("#"))

    Helium.defaults.site
      .metadata(title = Some(title), description = description, language = Some("en"))
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
      .favIcons(favicon.map(path => Favicon.internal(Path.parse(s"/$path"), "32x32")).toList: _*)
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
      .landingPage(title = Some(title), subtitle = description)
      .extendWith(SearchUI)
      .extendWith(templates)
      .build
  }

}
