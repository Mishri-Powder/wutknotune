#!/usr/bin/env node
/** Apply public-copy corrections to an existing staged website. */
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync, readdirSync, writeFileSync } from "node:fs";
import { resolve, relative } from "node:path";
import { parseArgs } from "node:util";
import { publicHtmlLinks } from "./public_links.mjs";

const { values } = parseArgs({ options: { site: { type: "string" } } });
assert(values.site, "Provide --site <staged-site>.");
const site = resolve(values.site);
const changed = [];
function write(path, text) {
  if (readFileSync(path, "utf8") === text) return;
  writeFileSync(path, text);
  changed.push(relative(site, path));
}
function visit(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name);
    if (relative(site, path) === "source/files" || entry.isSymbolicLink()) continue;
    if (entry.isDirectory()) visit(path);
    else if (entry.name.endsWith(".html")) {
      const text = publicHtmlLinks(readFileSync(path, "utf8"))
        .replaceAll("Look under the bonnet", "Under the hood");
      assert(!/(?:271\.pics|freki:8080)/i.test(text), "Internal reference remains: " + path);
      write(path, text);
    }
  }
}
visit(site);
const script = resolve(site, "assets/site.js");
const content = readFileSync(script, "utf8").replaceAll("Look under the bonnet", "Under the hood");
assert(!/(?:271\.pics|freki:8080)/i.test(content));
write(script, content);
const name = "site." + createHash("sha256").update(content).digest("hex").slice(0, 12) + ".js";
const immutable = resolve(site, "assets", name);
try {
  assert.equal(readFileSync(immutable, "utf8"), content, "Immutable asset collision.");
} catch (error) {
  if (error.code !== "ENOENT") throw error;
  writeFileSync(immutable, content);
  changed.push("assets/" + name);
}
const homepage = resolve(site, "index.html");
write(homepage, readFileSync(homepage, "utf8").replace(/\/assets\/site\.[a-f0-9]+\.js/, "/assets/" + name));
console.log(JSON.stringify({ changed: [...new Set(changed)], script: name }, null, 2));
