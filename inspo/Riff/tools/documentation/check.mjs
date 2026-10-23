import { lint } from "markdownlint/promise";
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "../..");
const args = process.argv.slice(2);
const write = args.includes("--write");
const extra = args.filter((arg) => arg !== "--write");
// Immutable inputs, vendored sources and historical snapshots are not authored documentation.
const excluded = /^(references|LEGACY|compact_enc_det)\//;
const files = execFileSync("git", ["ls-files", "-z", "--cached", "--others", "--exclude-standard"], {
  cwd: root,
  encoding: "utf8",
}).split("\0").filter((path) => /\.md$/i.test(path) && !excluded.test(path));

function addDirectory(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) addDirectory(path);
    else if (/\.md$/i.test(entry.name)) files.push(relative(root, path));
  }
}
for (const input of extra) {
  const path = resolve(process.cwd(), input);
  if (statSync(path).isDirectory()) addDirectory(path);
  else files.push(relative(root, path));
}
const paths = [...new Set(files)].filter((path) => existsSync(resolve(root, path)) && statSync(resolve(root, path)).isFile()).sort();
if (!paths.length) throw new Error("No Markdown documents found");
console.log(`Checking ${paths.length} maintained Markdown documents`);
function run(executable, parameters) {
  const result = spawnSync(resolve(here, "node_modules/.bin", executable), parameters, {
    cwd: root,
    stdio: "inherit",
  });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}
run("prettier", ["--ignore-path", "/dev/null", write ? "--write" : "--check", ...paths]);
// Git already supplies exact filenames. Avoid a second glob/config layer that brings
// unrelated parsers and interprets literal filenames as patterns.
// AI-INTENT: Keep the existing rule configuration and failure exit status.
// https://wiki.271.pics/Riff/LATEST/dependency-security#inventory-and-remediation
const config = JSON.parse(readFileSync(resolve(root, ".markdownlint-cli2.jsonc"), "utf8")).config;
const results = await lint({ files: paths.map((path) => resolve(root, path)), config });
let failures = 0;
for (const [path, errors] of Object.entries(results)) {
  for (const error of errors) {
    failures++;
    console.error(`${relative(root, path)}:${error.lineNumber} ${error.ruleNames.join("/")} ${error.ruleDescription}: ${error.errorDetail ?? ""}`);
  }
}
if (failures) process.exit(1);
