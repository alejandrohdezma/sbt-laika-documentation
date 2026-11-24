@DESCRIPTION@

`sbt-laika-documentation` builds a documentation website for your project with [Laika](https://typelevel.org/Laika/), using a Docusaurus-like look. It needs no Node.js toolchain: the site builds with sbt alone, and search runs in the browser with [protosearch](https://github.com/cozydev-pink/protosearch).

You get:

- a landing page with a title, a tagline, a "Get Started" button and optional feature cards;
- a sidebar, breadcrumbs, a table of contents, "Edit this page" links and previous/next links on every page;
- search, opened with <kbd>⌘</kbd> <kbd>K</kbd> (or <kbd>Ctrl</kbd> <kbd>K</kbd>);
- light and dark mode, following the reader's system setting;
- [mdoc](https://scalameta.org/mdoc/) support, so code samples are type-checked and `@VERSION@`-style variables work.

## Installation

Add the plugin to your `project/plugins.sbt`:

```sbt
addSbtPlugin("@ORGANIZATION@" % "@NAME@" % "@VERSION@")
```

The plugin is published for **sbt 1.x** only, because Laika has no stable sbt 2 release yet.

## Usage

Enable the plugin on the project that holds your documentation. If you already have an mdoc project that builds your README, like the `documentation` project that [sbt-ci](https://github.com/alejandrohdezma/sbt-ci) expects, use that one:

```sbt
lazy val documentation = project.enablePlugins(MdocPlugin, LaikaDocumentationPlugin)
```

The plugin runs mdoc on its own pages with its own input and output folders, so the project's `mdoc` task keeps building your README as before.

Then add your pages:

```
documentation/
├── docs/                    # Markdown pages, processed by mdoc
│   ├── getting-started.md   # linked from "Docs" and "Get Started"
│   ├── directory.conf       # optional: sidebar order and section titles
│   └── guides/
│       └── configuration.md
└── static/                  # optional: images and other files, copied as they are
    └── img/
        ├── logo.svg         # optional: shown in the top bar
        └── favicon.ico      # optional
```

Run `documentation/laikaSite` to build the site into `docs/` at the root of the build. To publish it with GitHub Pages, commit `docs/` and set the Pages source to the `main` branch and the `/docs` folder.

The title comes from the repository name, and the tagline and repository links from `description`, `scmInfo` and `homepage`. If you use [sbt-github](https://github.com/alejandrohdezma/sbt-github), run `github` before `documentation/laikaSite` so those settings are filled from GitHub:

```
sbt "github; documentation/laikaSite"
```

### Sidebar order

By default the sidebar lists pages in alphabetical order. To change it, add a `directory.conf` file to `docs` (or to any subdirectory):

```hocon
laika.navigationOrder = [
  getting-started.md
  guides
  faq.md
]
```

A subdirectory becomes a sidebar section. Set its title in its own `directory.conf`:

```hocon
laika.title = Guides
```

### Landing page features

Add `documentation.features` to `docs/directory.conf` to show cards on the landing page, below the title:

```hocon
documentation.features = [
  {
    title = "Fast"
    description = "It does things fast"
    image = "img/fast.png"
  }
  {
    title = "Simple"
    description = "It has sensible defaults"
  }
]
```

`image` is optional, and relative to the static directory. A path that doesn't exist fails the build.

### Top bar links

Add `documentation.links` to `docs/directory.conf` to show more links next to "Docs" in the top bar. Each link opens in a new tab:

```hocon
documentation.links = [
  { title = "Company", url = "https://example.com" }
]
```

### Footer

Add `documentation.footer` to `docs/directory.conf` to show a footer on every page:

```hocon
documentation.footer {
  link = "https://example.com"
  text = "My Library is an <a href=\"https://example.com\">Example</a> project distributed under the Apache-2.0 license."
}
```

The footer shows the logo, if there is one, and the text. `link` is optional: when it is set, the logo links to it. `text` can contain HTML.

## Settings

| Setting | Description | Default |
| :--- | :--- | :--- |
| `documentationTitle` | Title shown in the top bar, the landing page and the browser tab | the repository name, then `name` |
| `documentationDescription` | Tagline shown on the landing page and in the page metadata | `description`, when it differs from `name` |
| `documentationRepository` | Repository linked from the top bar and used for "Edit this page" links | `scmInfo`, then `homepage` |
| `documentationBranch` | Branch that "Edit this page" links point to | `main` |
| `documentationPrimaryColor` | Primary color, as a CSS hex value | `#25c2a0` |
| `documentationPrimaryColorDark` | Primary color in dark mode, as a CSS hex value | `documentationPrimaryColor` |
| `documentationStartPage` | Page linked from "Docs" and "Get Started" | `getting-started.md` |
| `documentationLogo` | Logo shown in the top bar, relative to the static directory | `img/logo.svg`, if it exists |
| `documentationFavicon` | Favicon, relative to the static directory | `img/favicon.ico`, if it exists |
| `documentationSourceDirectory` | Directory with the Markdown pages | `docs` inside the project |
| `documentationStaticDirectory` | Directory with images and other static files | `static` inside the project |
| `documentationOutputDirectory` | Directory the site is written to | `docs` at the root of the build |

## Customizing the theme

The plugin configures Laika's Helium theme through `laikaTheme`, and ships its templates, stylesheet and script as theme resources. A file with the same path in your static directory replaces the shipped one, for example:

| File in `documentation/static` | Replaces |
| :--- | :--- |
| `default.template.html` | the template of every page |
| `landing.template.html` | the landing page template |
| `helium/templates/topNav.template.html` | the top bar, including the search box |
| `laika-documentation/documentation.css` | the stylesheet |

## Contributors to this project

@CONTRIBUTORS_TABLE@
