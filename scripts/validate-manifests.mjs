import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const concordancePackage = path.join(repoRoot, "concordance", "package.json");

if (!fs.existsSync(concordancePackage)) {
  console.error("Missing concordance schema reference at ./concordance");
  process.exit(1);
}

const requireFromConcordance = createRequire(concordancePackage);
const Ajv2020Module = requireFromConcordance("ajv/dist/2020.js");
const addFormatsModule = requireFromConcordance("ajv-formats");
const Ajv2020 = Ajv2020Module.default ?? Ajv2020Module;
const addFormats = addFormatsModule.default ?? addFormatsModule;

const ajv = new Ajv2020({
  allErrors: true,
  strict: false,
  validateSchema: true
});
addFormats(ajv);

const schemaDir = path.join(repoRoot, "concordance", "schemas", "core");
for (const schemaPath of listJsonFiles(schemaDir)) {
  ajv.addSchema(readJson(schemaPath));
}

const checks = [
  ["marketplace.schema.json", path.join(repoRoot, "marketplace.json")],
  ...listPluginManifests(path.join(repoRoot, "plugins")).map((file) => ["plugin.schema.json", file]),
  ...listFiles(path.join(repoRoot, "hooks"))
    .filter((file) => file.endsWith(".hook.json"))
    .map((file) => ["hook.schema.json", file])
];

let failures = 0;
for (const [schemaId, filePath] of checks) {
  const validate = ajv.getSchema(schemaId);
  if (!validate) {
    throw new Error(`Schema was not registered: ${schemaId}`);
  }

  const data = readJson(filePath);
  if (validate(data)) {
    console.log(`OK ${path.relative(repoRoot, filePath)}`);
    failures += validateLocalReferences(filePath, data);
    continue;
  }

  failures += 1;
  console.error(`FAIL ${path.relative(repoRoot, filePath)}`);
  console.error(JSON.stringify(validate.errors, null, 2));
}

for (const manifest of listJsonFiles(path.join(repoRoot, "manifests"))) {
  const data = readJson(manifest);
  failures += validateManifestReferences(manifest, data);
  console.log(`OK ${path.relative(repoRoot, manifest)}`);
}

if (failures > 0) {
  process.exitCode = 1;
}

function listPluginManifests(directory) {
  if (!fs.existsSync(directory)) {
    return [];
  }
  return fs.readdirSync(directory, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => path.join(directory, entry.name, "plugin.json"))
    .filter((file) => fs.existsSync(file))
    .sort();
}

function listFiles(directory) {
  if (!fs.existsSync(directory)) {
    return [];
  }
  return fs.readdirSync(directory)
    .sort()
    .map((entry) => path.join(directory, entry))
    .filter((file) => fs.statSync(file).isFile());
}

function listJsonFiles(directory) {
  if (!fs.existsSync(directory)) {
    return [];
  }
  return fs.readdirSync(directory)
    .filter((entry) => entry.endsWith(".json"))
    .sort()
    .map((entry) => path.join(directory, entry));
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function validateLocalReferences(filePath, data) {
  let failures = 0;
  if (data.type === "MARKETPLACE") {
    for (const entry of data.plugins ?? []) {
      failures += requireLocalPath(filePath, entry.plugin?.source?.path, `plugin ${entry.name}`);
    }
  }
  for (const primitive of [
    ...(data.skills ?? []),
    ...(data.agents ?? []),
    ...(data.instructions ?? []),
    ...(data.hooks ?? [])
  ]) {
    failures += validatePrimitiveReference(filePath, primitive);
  }
  if (isPrimitiveReference(data)) {
    failures += validatePrimitiveReference(filePath, data);
  }
  return failures;
}

function validateManifestReferences(filePath, data) {
  if (!filePath.endsWith(path.join("manifests", "promotions.json"))) {
    return 0;
  }

  let failures = 0;
  for (const entry of data.entries ?? []) {
    failures += requireLocalPath(filePath, entry.canonicalPath, `promotion ${entry.name} canonicalPath`);
    if (entry.sourceResolvedPath && !fs.existsSync(entry.sourceResolvedPath)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: missing promotion sourceResolvedPath for ${entry.name}: ${entry.sourceResolvedPath}`
      );
      failures += 1;
    }
    for (const source of entry.supportingSources ?? []) {
      if (source.sourceResolvedPath && !fs.existsSync(source.sourceResolvedPath)) {
        console.error(
          `FAIL ${path.relative(repoRoot, filePath)}: missing promotion supporting sourceResolvedPath for ${entry.name}: ${source.sourceResolvedPath}`
        );
        failures += 1;
      }
    }
  }
  return failures;
}

function validatePrimitiveReference(filePath, primitive) {
  let failures = requirePrimitivePath(filePath, primitive);
  for (const dependency of primitive?.dependsOn ?? []) {
    failures += requirePrimitivePath(filePath, dependency);
  }
  return failures;
}

function requirePrimitivePath(filePath, primitive) {
  if (primitive?.source?.type !== "LOCAL_SOURCE") {
    return 0;
  }
  return requireLocalPath(filePath, primitive.path, `${primitive.type} ${primitive.name}`);
}

function isPrimitiveReference(data) {
  return ["SKILL", "AGENT", "INSTRUCTION", "HOOK"].includes(data?.type);
}

function requireLocalPath(filePath, localPath, label) {
  if (!localPath) {
    return 0;
  }
  const target = path.resolve(repoRoot, localPath);
  if (fs.existsSync(target)) {
    return 0;
  }
  console.error(`FAIL ${path.relative(repoRoot, filePath)}: missing ${label} path: ${localPath}`);
  return 1;
}
