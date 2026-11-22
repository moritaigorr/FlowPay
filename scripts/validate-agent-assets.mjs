import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Intentionally validates the restricted formats authored in this repository.
// Reject unsupported syntax rather than claiming general TOML/YAML support.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const errors = [];
const documents = [];
const check = (condition, message) => { if (!condition) errors.push(message); };
const read = relative => {
  const text = fs.readFileSync(path.join(root, relative), 'utf8');
  check(!text.includes('\uFFFD'), `${relative}: invalid text encoding`);
  check(!/^\s*\[TODO:.*\]\s*$/m.test(text), `${relative}: unfinished scaffold`);
  documents.push([relative, text]);
  return text;
};
const exists = relative => fs.existsSync(path.join(root, relative));
const skillsRoot = '.agents/skills';
const agentsRoot = '.codex/agents';
const skills = new Set();
const agents = new Set();

try {
  read('AGENTS.md');
  for (const entry of fs.readdirSync(path.join(root, 'docs'), { withFileTypes: true })) {
    if (entry.isFile() && entry.name.endsWith('.md')) read(`docs/${entry.name}`);
  }
  for (const entry of fs.readdirSync(path.join(root, skillsRoot), { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const file = `${skillsRoot}/${entry.name}/SKILL.md`;
    const text = read(file);
    const match = text.match(/^---\r?\nname: ([a-z0-9]+(?:-[a-z0-9]+)*)\r?\ndescription: ([^\r\n]+)\r?\n---\r?\n/);
    check(Boolean(match), `${file}: unsupported or invalid YAML frontmatter`);
    if (!match) continue;
    check(match[1] === entry.name && match[1].length <= 64, `${file}: invalid skill name`);
    check(!skills.has(match[1]), `${file}: duplicate skill name`);
    check(match[2].length <= 1024 && !/[<>]|:\s|\s#/.test(match[2]), `${file}: unsupported description scalar`);
    skills.add(match[1]);
  }
  for (const entry of fs.readdirSync(path.join(root, agentsRoot), { withFileTypes: true })) {
    if (!entry.isFile() || !entry.name.endsWith('.toml')) continue;
    const file = `${agentsRoot}/${entry.name}`;
    const text = read(file);
    const match = text.match(/^name = "([a-z0-9]+(?:-[a-z0-9]+)*)"\r?\ndescription = "([^"\\\r\n]+)"\r?\ndeveloper_instructions = """\r?\n([\s\S]+?)\r?\n"""\s*$/);
    check(Boolean(match), `${file}: unsupported or invalid TOML profile`);
    if (!match) continue;
    check(entry.name === `${match[1]}.toml`, `${file}: filename/name mismatch`);
    check(!agents.has(match[1]), `${file}: duplicate agent name`);
    check(!/\\|"""/.test(match[3]), `${file}: unsupported multiline escape/delimiter`);
    agents.add(match[1]);
    for (const target of text.match(/(?:\.agents\/skills\/[a-z0-9-]+\/SKILL\.md|docs\/[A-Za-z0-9_-]+\.md)/g) ?? []) {
      check(exists(target), `${file}: missing file ${target}`);
    }
  }
  let links = 0;
  for (const [file, text] of documents) {
    for (const [, raw] of text.matchAll(/\]\(([^)]+)\)/g)) {
      if (/^(?:https?:|mailto:|#)/.test(raw)) continue;
      const target = raw.split('#')[0];
      const resolved = path.resolve(root, path.dirname(file), target);
      check(fs.existsSync(resolved), `${file}: broken link ${raw}`);
      links++;
    }
  }
  check(skills.size > 0 && agents.size > 0, 'No skills or agents discovered');
  if (!errors.length) console.log(`PASS: ${agents.size} agent profiles, ${skills.size} skills, ${links} local links. Restricted syntax and references validated; runtime activation not tested.`);
} catch (error) {
  errors.push(error.message);
}
if (errors.length) {
  for (const error of errors) console.error(error);
  process.exitCode = 1;
}
