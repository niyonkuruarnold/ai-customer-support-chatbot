import MarkdownIt from 'markdown-it'

/**
 * Shared markdown-it instance for rendering AI response content.
 *
 * - `html: false` — raw HTML in AI output is escaped, never rendered
 *   (the only HTML in the feed comes from this renderer).
 * - `linkify: true` — bare URLs become clickable links.
 * - `breaks: true` — single newlines become <br>, matching chat-style text.
 */
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

/**
 * Render markdown text to safe HTML. Always returns a string; null/undefined
 * input renders as an empty paragraph-less string.
 *
 * @param {string|null|undefined} text
 * @returns {string}
 */
export function markdownToHtml(text) {
  return md.render(text ?? '')
}
