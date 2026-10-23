/** Keep internal documentation references out of public page navigation. */
export function isInternalReference(value) {
  if (/^\/(?:en\/)?(?:Riff|Cuetree|Cuegrain)(?:\/|$)/.test(value)) return true;
  try {
    const url = new URL(value, "https://roadio.link");
    const host = url.hostname.toLowerCase();
    return host === "271.pics" || host.endsWith(".271.pics") || host === "freki" ||
      (host === "localhost" && url.port === "8080");
  } catch {
    return false;
  }
}

/** Preserve link labels and inline markup while discarding internal link wrappers. */
export function publicMarkdownTokens(tokens) {
  const links = [];
  return tokens.flatMap((token) => {
    if (token.type === "link_open") {
      const internal = isInternalReference(token.attrGet("href") ?? "");
      links.push(internal);
      return internal ? [] : [token];
    }
    if (token.type === "link_close") return links.pop() ? [] : [token];
    if (token.children) token.children = publicMarkdownTokens(token.children);
    return [token];
  });
}

/** Existing static pages use the same boundary as newly generated articles. */
export function publicHtmlLinks(html) {
  return html.replace(/<a\b[^>]*\bhref\s*=\s*(["'])(.*?)\1[^>]*>([\s\S]*?)<\/a\s*>/gi,
    (anchor, _quote, href, label) => isInternalReference(href) ? label : anchor);
}
