const scriptUrl = document.currentScript.src

const copyIcon = '<svg viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M19,21H8V7H19M19,5H8A2,2 0 0,0 6,7V21A2,2 0 0,0 8,23H19A2,2 0 0,0 21,21V7A2,2 0 0,0 19,5M16,1H4A2,2 0 0,0 2,3V17H4V3H16V1Z"></path></svg>'
const checkIcon = '<svg viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M21,7L9,19L3.5,13.5L4.91,12.09L9,16.17L19.59,5.59L21,7Z"></path></svg>'
const pageIcon = '<svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true"><path d="M17 6v12c0 .52-.2 1-1 1H4c-.7 0-1-.33-1-1V2c0-.55.42-1 1-1h8l5 5zM14 8h-3.13c-.51 0-.87-.34-.87-.87V4M6 12h8M6 15h8" stroke="currentColor" fill="none" fill-rule="evenodd" stroke-linejoin="round"></path></svg>'
const enterIcon = '<svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true"><g stroke="currentColor" fill="none" fill-rule="evenodd" stroke-linecap="round" stroke-linejoin="round"><path d="M18 3v4c0 2-2 4-4 4H2"></path><path d="M8 17l-6-6 6-6"></path></g></svg>'

function setUpCopyButtons() {
  document.querySelectorAll("main.content pre").forEach(pre => {
    const wrapper = document.createElement("div")
    wrapper.className = "code-block"
    pre.replaceWith(wrapper)
    wrapper.appendChild(pre)

    const button = document.createElement("button")
    button.type = "button"
    button.className = "copy-button"
    button.title = "Copy"
    button.setAttribute("aria-label", "Copy code to clipboard")
    button.innerHTML = copyIcon
    button.addEventListener("click", () => {
      navigator.clipboard.writeText(pre.innerText).then(() => {
        button.innerHTML = checkIcon
        setTimeout(() => (button.innerHTML = copyIcon), 1000)
      })
    })
    wrapper.appendChild(button)
  })
}

function escapeHtml(text) {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;")
}

function highlighted(text) {
  return escapeHtml(text || "").replace(/&lt;mark&gt;/g, "<mark>").replace(/&lt;\/mark&gt;/g, "</mark>")
}

function withoutTitle(body, title) {
  let i = 0
  let j = 0
  while (i < body.length && j < title.length) {
    if (body.startsWith("<mark>", i)) i += 6
    else if (body.startsWith("</mark>", i)) i += 7
    else if (body[i] === title[j]) (i++, j++)
    else return body
  }
  if (body.startsWith("</mark>", i)) i += 7
  return j === title.length ? body.slice(i).trimStart() : body
}

function setUpSearch() {
  const modal = document.getElementById("search-modal")
  const button = document.getElementById("search-button")
  if (!modal || !button) return

  const form = modal.querySelector(".search-form")
  const input = document.getElementById("search-input")
  const clear = modal.querySelector(".search-clear")
  const results = document.getElementById("search-results")
  const siteRoot = new URL("../", scriptUrl)
  let worker = null
  let hits = []
  let searched = ""
  let pending = ""
  let selected = 0

  const isOpen = () => !modal.hidden

  function open() {
    if (!worker) {
      worker = new Worker(new URL("../search/worker.js", scriptUrl))
      worker.onmessage = event => {
        hits = event.data
        searched = pending
        render()
      }
    }
    modal.hidden = false
    document.body.classList.add("search-open")
    input.focus()
    input.select()
  }

  function close() {
    modal.hidden = true
    document.body.classList.remove("search-open")
  }

  function select(index) {
    const items = results.querySelectorAll(".search-hit")
    if (!items.length) return
    selected = (index + items.length) % items.length
    items.forEach((item, i) => item.setAttribute("aria-selected", i === selected))
    items[selected].scrollIntoView({ block: "nearest" })
  }

  function render() {
    clear.hidden = input.value.trim() === ""

    if (searched === "") {
      results.innerHTML = '<p class="search-empty">Start typing to search the docs.</p>'
      return
    }

    if (!hits.length) {
      results.innerHTML = `<p class="search-empty">No results for "<strong>${escapeHtml(searched)}</strong>"</p>`
      return
    }

    const groups = new Map()
    hits.forEach(hit => {
      const path = hit.fields.path.replace(/^\//, "")
      const group = path.includes("/") ? path.split("/")[0].replace(/^./, c => c.toUpperCase()) : "Docs"
      if (!groups.has(group)) groups.set(group, [])
      groups.get(group).push({ hit, path })
    })

    results.innerHTML = [...groups].map(([group, items]) => `
      <section class="search-source">
        <h4 class="search-source-title">${escapeHtml(group)}</h4>
        <ul>${items.map(({ hit, path }) => `
          <li><a class="search-hit" role="option" href="${new URL(path + ".html", siteRoot)}">
            <span class="search-hit-icon">${pageIcon}</span>
            <span class="search-hit-content">
              <span class="search-hit-title">${highlighted(hit.highlights.title || hit.fields.title)}</span>
              ${hit.highlights.body ? `<span class="search-hit-text">${highlighted(withoutTitle(hit.highlights.body, hit.fields.title))}</span>` : ""}
            </span>
            <span class="search-hit-action">${enterIcon}</span>
          </a></li>`).join("")}
        </ul>
      </section>`).join("")

    results.querySelectorAll(".search-hit").forEach((item, i) => item.addEventListener("mousemove", () => select(i)))
    select(0)
  }

  input.addEventListener("input", () => {
    const query = input.value.replace(/[^\p{L}\p{N}]+/gu, " ").trim()
    if (query === "") {
      hits = []
      searched = ""
      render()
    } else {
      worker.postMessage({ query, size: 10, highlightFields: ["title", "body"] })
      pending = input.value.trim()
    }
  })

  form.addEventListener("submit", event => event.preventDefault())

  form.addEventListener("reset", () => {
    hits = []
    searched = ""
    setTimeout(() => {
      render()
      input.focus()
    })
  })

  input.addEventListener("keydown", event => {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault()
      select(selected + (event.key === "ArrowDown" ? 1 : -1))
    } else if (event.key === "Enter") {
      event.preventDefault()
      const item = results.querySelectorAll(".search-hit")[selected]
      if (item) window.location.href = item.href
    }
  })

  button.addEventListener("click", open)
  modal.querySelector(".search-close").addEventListener("click", close)
  modal.addEventListener("mousedown", event => {
    if (event.target === modal) close()
  })

  document.addEventListener("keydown", event => {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
      event.preventDefault()
      isOpen() ? close() : open()
    } else if (event.key === "Escape" && isOpen()) {
      event.preventDefault()
      close()
    } else if (event.key === "/" && !isOpen() && !/INPUT|TEXTAREA/.test(document.activeElement.tagName)) {
      event.preventDefault()
      open()
    }
  })

  const isMac = /Mac|iPhone|iPad/.test(navigator.platform)
  document.querySelectorAll(".search-keys .modifier").forEach(key => (key.textContent = isMac ? "⌘" : "Ctrl"))

  render()
}

document.addEventListener("DOMContentLoaded", () => {
  setUpCopyButtons()
  setUpSearch()
})
