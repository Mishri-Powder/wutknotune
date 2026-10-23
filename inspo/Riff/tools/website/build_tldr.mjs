#!/usr/bin/env node
/** Render a bilingual Markdown topic into an existing, staged Riff website. */
import assert from "node:assert/strict";
import { publicMarkdownTokens } from "./public_links.mjs";
import { createHash } from "node:crypto";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, resolve } from "node:path";
import { parseArgs } from "node:util";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const repository = resolve(here, "../..");
const requireDocumentation = createRequire(resolve(here, "../documentation/package.json"));
const MarkdownIt = requireDocumentation("markdown-it");
const { values } = parseArgs({
  options: {
    site: { type: "string" },
    topic: { type: "string" },
  },
});
assert(values.site && values.topic, "Provide --site <staged-site> and --topic <topic.json>.");
const site = resolve(values.site);
const topic = JSON.parse(readFileSync(resolve(values.topic), "utf8"));
assert(/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(topic.slug), "Invalid topic slug.");

const escapeHtml = (text) =>
  text
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
const escapePattern = (text) => text.replace(/[.*+?^$()|[\]\\]/g, "\\$&");
const route = (language) => "/tldr/" + (language === "ru" ? "ru/" : "") + topic.slug + "/";
const changed = [];

function write(relative, content) {
  const path = resolve(site, relative);
  assert(path.startsWith(site + "/"));
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, content);
  changed.push(relative);
}

function replaceOnce(text, pattern, replacement) {
  const matches = [
    ...text.matchAll(new RegExp(pattern.source, pattern.flags.replaceAll("g", "") + "g")),
  ];
  assert.equal(matches.length, 1, "Expected one match: " + pattern);
  return text.replace(pattern, typeof replacement === "function" ? replacement : () => replacement);
}

// Topic-owned assets are copied before their immutable URLs enter generated pages.
for (const asset of topic.assets ?? []) {
  const source = resolve(repository, asset.source);
  assert(source.startsWith(repository + "/"), "Asset must belong to the repository.");
  assert(/^assets\/[a-z0-9.-]+$/.test(asset.target), "Invalid asset target.");
  write(asset.target, readFileSync(source, "utf8"));
}
for (const stylesheet of topic.stylesheets ?? []) {
  assert((topic.assets ?? []).some((asset) => "/" + asset.target === stylesheet));
}

for (const language of ["en", "ru"]) {
  const markdown = readFileSync(resolve(repository, topic.sources[language]), "utf8");
  const parser = new MarkdownIt({ html: false, linkify: false });
  const tokens = publicMarkdownTokens(parser.parse(markdown, {}));
  const headings = [];
  let title;
  for (let index = 0; index < tokens.length; index += 1) {
    if (tokens[index].type !== "heading_open") continue;
    if (tokens[index].tag === "h1") {
      assert.equal(title, undefined, "A topic must have one title.");
      title = tokens[index + 1].content;
    } else if (tokens[index].tag === "h2") {
      const anchor = "question-" + (headings.length + 1);
      tokens[index].attrSet("id", anchor);
      headings.push({ anchor, title: tokens[index + 1].content });
    }
  }
  assert(title && headings.length > 0, "A topic must have a title and sections.");
  if (topic.homepageTerms) assert.equal(headings.length, topic.homepageTerms[language].length);
  let body = parser.renderer.render(tokens, parser.options, {});
  assert(!/(?:271\.pics|freki:8080)/i.test(body), "Internal URL remains in public article.");
  for (const asset of topic.assets ?? []) {
    body = body.replaceAll("https://roadio.link/" + asset.target, "/" + asset.target);
  }
  const contents =
    '<nav class="toc" aria-label="' +
    (language === "ru" ? "Содержание" : "Contents") +
    '"><ol>' +
    headings
      .map(
        (heading) =>
          '<li><a href="#' + heading.anchor + '">' + escapeHtml(heading.title) + "</a></li>",
      )
      .join("") +
    "</ol></nav>";
  const firstParagraphEnd = body.indexOf("</p>");
  assert(firstParagraphEnd >= 0, "A topic must have an introduction.");
  body = body.slice(0, firstParagraphEnd + 4) + contents + body.slice(firstParagraphEnd + 4);

  const directory = language === "ru" ? "tldr/ru" : "tldr";
  let template = readFileSync(resolve(site, directory + "/player-indexer/index.html"), "utf8");
  template = template.replaceAll("/player-indexer/", "/" + topic.slug + "/");
  template = replaceOnce(
    template,
    /<title>.*?<\/title>/s,
    "<title>" + escapeHtml(title) + " — Riff</title>",
  );
  template = replaceOnce(
    template,
    /<meta name="description" content="[^"]*">/,
    '<meta name="description" content="' + escapeHtml(topic.summaries[language]) + '">',
  );
  template = replaceOnce(
    template,
    /(<main id="content" class="reading">).*?(<\/main>)/s,
    (_match, opening, closing) => opening + body + closing,
  );
  for (const stylesheet of topic.stylesheets ?? []) {
    template = replaceOnce(
      template,
      /<\/head>/,
      '<link rel="stylesheet" href="' + escapeHtml(stylesheet) + '"></head>',
    );
  }
  write(directory + "/" + topic.slug + "/index.html", template);

  const indexPath = directory + "/index.html";
  let index = readFileSync(resolve(site, indexPath), "utf8");
  const existingCard = new RegExp(
    '<a class="topic" href="' + escapePattern(route(language)) + '"[^>]*>.*?</a>',
    "s",
  );
  index = index.replace(existingCard, "");
  const card =
    '<a class="topic" href="' +
    route(language) +
    '"><h2>' +
    escapeHtml(title) +
    "</h2><p>" +
    escapeHtml(topic.summaries[language]) +
    "</p></a>";
  assert(index.includes('<a class="topic"'), "The existing TL;DR index is required.");
  index = index.replace('<a class="topic"', card + '<a class="topic"');
  write(indexPath, index);
}

const script = readFileSync(resolve(site, "assets/site.js"), "utf8");
const header = script.match(/^const content=(.*?);\n/s);
assert(header, "Expected the existing homepage translation table.");
const content = JSON.parse(header[1]);
const translationKey = topic.homepageTranslationKey;
if (translationKey) {
  for (const language of ["en", "ru"]) {
    for (const [index, term] of topic.homepageTerms[language].entries()) {
      const link =
        '<a href="' +
        route(language) +
        "#question-" +
        (index + 1) +
        '">' +
        escapeHtml(term) +
        "</a>";
      const previous = content.translations[translationKey][language];
      if (previous.includes(link)) continue;
      assert(previous.includes(term), "Missing homepage term: " + term);
      content.translations[translationKey][language] = previous.replace(term, link);
    }
  }
}
if (topic.homepageTopicSummary) {
  content.translations.tldrTopic = topic.homepageTopicSummary;
}
const nextScript =
  "const content=" + JSON.stringify(content) + ";\n" + script.slice(header[0].length);
const scriptName =
  "site." + createHash("sha256").update(nextScript).digest("hex").slice(0, 12) + ".js";
write("assets/site.js", nextScript);
write("assets/" + scriptName, nextScript);
let homepage = readFileSync(resolve(site, "index.html"), "utf8");
homepage = replaceOnce(homepage, /\/assets\/site\.[a-f0-9]+\.js/, "/assets/" + scriptName);
for (const key of [translationKey, topic.homepageTopicSummary ? "tldrTopic" : undefined].filter(
  Boolean,
)) {
  const pattern = new RegExp('(<p data-i18n="' + escapePattern(key) + '">).*?(</p>)', "s");
  homepage = replaceOnce(
    homepage,
    pattern,
    (_match, opening, closing) => opening + content.translations[key].en + closing,
  );
}
write("index.html", homepage);
console.log(JSON.stringify({ topic: topic.slug, files: changed, script: scriptName }, null, 2));
