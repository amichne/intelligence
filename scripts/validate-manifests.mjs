import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const coreSchemaDir = path.join(repoRoot, "schemas", "core");
const adapterSchemaDir = path.join(repoRoot, "schemas", "adapters");
const marketplaceSchemaDir = path.join(repoRoot, "schemas", "marketplace");
const hookSchemaDir = path.join(repoRoot, "schemas", "hooks");
const adaptableMarketplace = path.join(repoRoot, "adaptable.marketplace.json");
const codexMarketplace = path.join(repoRoot, "codex", "marketplace.json");
const codexMarketplaceLock = path.join(repoRoot, "codex", "marketplace-lock.json");
const codexBranchMarketplace = path.join(repoRoot, ".agents", "plugins", "marketplace.json");
const codexBranchMarketplaceLock = path.join(repoRoot, "marketplace-lock.json");
const githubPluginMarketplace = path.join(repoRoot, ".github", "plugin", "marketplace.json");
const codexMarketplaceFixture = path.join(
  repoRoot,
  "schemas",
  "marketplace",
  "fixtures",
  "codex.marketplace.json"
);
const generatedJsonRoots = [
  path.join(repoRoot, "codex"),
  path.join(repoRoot, ".github", "plugin")
].map(normalizePath);
const options = parseArguments(process.argv.slice(2));

const requireFromRepo = createRequire(import.meta.url);
const Ajv2020Module = loadDependency("ajv/dist/2020.js");
const AjvDraft7Module = loadDependency("ajv");
const addFormatsModule = loadDependency("ajv-formats");
const Ajv2020 = Ajv2020Module.default ?? Ajv2020Module;
const AjvDraft7 = AjvDraft7Module.default ?? AjvDraft7Module;
const addFormats = addFormatsModule.default ?? addFormatsModule;

const ajv = new Ajv2020({
  allErrors: true,
  strict: false,
  validateSchema: true
});
addFormats(ajv);

const draft7Ajv = new AjvDraft7({
  allErrors: true,
  strict: false,
  validateSchema: true
});
addFormats(draft7Ajv);

const adapterHookSchemas = [
  { adapter: "claude", schemaId: "claude-hooks.schema.json", validator: ajv },
  { adapter: "codex", schemaId: "https://json.schemastore.org/codex-hooks.json", validator: draft7Ajv },
  { adapter: "github", schemaId: "github-hooks.schema.json", validator: ajv }
];

const validatedJsonFiles = new Set();

for (const schemaPath of listJsonFiles(coreSchemaDir)) {
  ajv.addSchema(readJson(schemaPath));
}

for (const schemaPath of [
  ...listSchemaFilesRecursive(adapterSchemaDir),
  ...listSchemaFilesRecursive(marketplaceSchemaDir),
  ...listJsonFiles(hookSchemaDir)
]) {
  const schema = readJson(schemaPath);
  if (schema.$schema === "http://json-schema.org/draft-07/schema#") {
    draft7Ajv.addSchema(schema);
  } else {
    ajv.addSchema(schema);
  }
}

const checks = [
  ["adaptable-marketplace.schema.json", adaptableMarketplace],
  ["codex-marketplace.schema.json", codexMarketplaceFixture],
  ...optionalChecks("codex-marketplace.schema.json", codexMarketplace),
  ...optionalChecks("codex-marketplace-lock.schema.json", codexMarketplaceLock),
  ...optionalChecks("codex-marketplace.schema.json", codexBranchMarketplace),
  ...optionalChecks("codex-marketplace-lock.schema.json", codexBranchMarketplaceLock),
  ...optionalChecks("github-marketplace.schema.json", githubPluginMarketplace),
  ...listJsonFiles(path.join(repoRoot, "profiles")).map((file) => ["workflow-profile.schema.json", file]),
  ...listPluginManifests(path.join(repoRoot, "plugins")).map((file) => ["plugin.schema.json", file]),
  ...listCodexPluginManifests(path.join(repoRoot, "plugins")).map((file) => ["codex-plugin.schema.json", file]),
  ...listCodexPluginManifests(path.join(repoRoot, "codex", "plugins")).map((file) => ["codex-plugin.schema.json", file]),
  ...hydratedChecks(options.hydrated),
  ...listFiles(path.join(repoRoot, "hooks"))
    .filter((file) => file.endsWith(".hook.json"))
    .map((file) => ["hook.schema.json", file]),
  ...listFiles(path.join(repoRoot, "hooks"))
    .filter((file) => file.endsWith(".requirements.json"))
    .map((file) => ["hook-skill-requirements.schema.json", file])
];

let failures = 0;
for (const [schemaId, filePath] of checks) {
  failures += validateDataFile(ajv, schemaId, filePath);
}

for (const [validator, schemaId, filePath] of listAdapterHookChecks()) {
  failures += validateDataFile(validator, schemaId, filePath);
}

if (options.portable) {
  console.log("OK portable mode: no host-local checks required");
}

failures += validateNodeDependencyManifests();
failures += validateSchemaDocuments();
failures += validateHydratedInstructionAdapters(repoRoot);
failures += validateHydratedInstructionAdapters(options.hydrated);
failures += validateJsonCoverage();

if (failures > 0) {
  process.exitCode = 1;
}

function validateDataFile(validator, schemaId, filePath) {
  const validate = validator.getSchema(schemaId);
  if (!validate) {
    throw new Error(`Schema was not registered: ${schemaId}`);
  }

  const data = readJson(filePath);
  if (validate(data)) {
    markValidated(filePath);
    console.log(`OK ${path.relative(repoRoot, filePath)}`);
    return validateLocalReferences(filePath, data);
  }

  console.error(`FAIL ${path.relative(repoRoot, filePath)}`);
  console.error(JSON.stringify(validate.errors, null, 2));
  return 1;
}

function parseArguments(args) {
  let hydrated = null;
  let portable = false;
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--portable") {
      portable = true;
      continue;
    }
    if (arg === "--hydrated") {
      const value = args[index + 1];
      if (!value) {
        printUsageAndExit();
      }
      hydrated = path.resolve(repoRoot, value);
      index += 1;
      continue;
    }
    console.error(`unknown argument: ${arg}`);
    printUsageAndExit();
  }
  return { hydrated, portable };
}

function printUsageAndExit() {
  console.error("usage: node scripts/validate-manifests.mjs [--portable] [--hydrated <dir>]");
  process.exit(2);
}

function hydratedChecks(directory) {
  if (!directory) {
    return [];
  }
  return [
    ...optionalChecks("codex-marketplace.schema.json", path.join(directory, "codex", "marketplace.json")),
    ...optionalChecks("codex-marketplace-lock.schema.json", path.join(directory, "codex", "marketplace-lock.json")),
    ...optionalChecks("codex-marketplace.schema.json", path.join(directory, ".agents", "plugins", "marketplace.json")),
    ...optionalChecks("codex-marketplace-lock.schema.json", path.join(directory, "marketplace-lock.json")),
    ...optionalChecks("github-marketplace.schema.json", path.join(directory, ".github", "plugin", "marketplace.json")),
    ...listCodexPluginManifests(path.join(directory, "codex", "plugins")).map((file) => [
      "codex-plugin.schema.json",
      file
    ]),
    ...listCodexPluginManifests(path.join(directory, "plugins")).map((file) => [
      "codex-plugin.schema.json",
      file
    ])
  ];
}

function listAdapterHookChecks() {
  return adapterHookSchemas.flatMap(({ adapter, schemaId, validator }) =>
    listJsonFiles(path.join(repoRoot, "hooks", adapter)).map((file) => [
      validator,
      schemaId,
      file
    ])
  );
}

function validateLocalReferences(filePath, data) {
  let failures = 0;
  if (data.type === "MARKETPLACE") {
    for (const entry of data.plugins ?? []) {
      failures += requireLocalPath(filePath, entry.plugin?.source?.path, `plugin ${entry.name}`);
    }
  }
  if (data.type === "MARKETPLACE" || data.type === "PLUGIN") {
    for (const collectionName of ["skills", "agents", "instructions", "hooks"]) {
      for (const primitive of asArray(data[collectionName])) {
        failures += validatePrimitiveReference(filePath, primitive);
      }
    }
  }
  if (isPrimitiveReference(data)) {
    failures += validatePrimitiveReference(filePath, data);
  }
  if (data.type === "HOOK_SKILL_REQUIREMENTS") {
    for (const skill of data.skills ?? []) {
      failures += requireLocalPath(filePath, skill.skillPath, `required skill ${skill.id}`);
    }
  }
  if (data.type === "WORKFLOW_PROFILE") {
    failures += validateWorkflowProfileReferences(filePath, data);
  }
  return failures;
}

function validateWorkflowProfileReferences(filePath, data) {
  const marketplace = readJson(adaptableMarketplace);
  const pluginNames = new Set((marketplace.plugins ?? []).map((entry) => entry.name));
  const hookNames = new Set((marketplace.hooks ?? []).map((entry) => entry.name));
  let failures = 0;

  for (const pluginName of data.plugins ?? []) {
    if (!pluginNames.has(pluginName)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: workflow profile references unknown plugin ${pluginName}`
      );
      failures += 1;
    }
  }

  for (const hook of data.hooks ?? []) {
    if (!hookNames.has(hook.name)) {
      console.error(
        `FAIL ${path.relative(repoRoot, filePath)}: workflow profile references unknown hook ${hook.name}`
      );
      failures += 1;
      continue;
    }
    failures += requireLocalPath(
      filePath,
      `hooks/${hook.adapter}/${hook.name}.hooks.json`,
      `workflow profile hook adapter ${hook.name}@${hook.adapter}`
    );
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

function validateNodeDependencyManifests() {
  const packagePath = path.join(repoRoot, "package.json");
  const lockPath = path.join(repoRoot, "package-lock.json");
  let failures = 0;

  if (!fs.existsSync(packagePath)) {
    console.error("FAIL package.json: missing Node dependency manifest");
    failures += 1;
  } else {
    const packageJson = readJson(packagePath);
    markValidated(packagePath);
    console.log("OK package.json");
    failures += requireJsonValue(packagePath, "name", packageJson.name, "intelligence");
    failures += requireJsonValue(packagePath, "private", packageJson.private, true);
    failures += requireJsonValue(
      packagePath,
      "scripts.validate:manifests",
      packageJson.scripts?.["validate:manifests"],
      "node scripts/validate-manifests.mjs"
    );
    failures += requireStringField(packagePath, "devDependencies.ajv", packageJson.devDependencies?.ajv);
    failures += requireStringField(packagePath, "devDependencies.ajv-formats", packageJson.devDependencies?.["ajv-formats"]);
  }

  if (!fs.existsSync(lockPath)) {
    console.error("FAIL package-lock.json: missing npm lockfile");
    failures += 1;
  } else {
    const lock = readJson(lockPath);
    markValidated(lockPath);
    console.log("OK package-lock.json");
    failures += requireJsonValue(lockPath, "name", lock.name, "intelligence");
    failures += requireJsonValue(lockPath, "lockfileVersion", lock.lockfileVersion, 3);
    failures += requireJsonValue(lockPath, "requires", lock.requires, true);
    failures += requireStringField(lockPath, "packages.\"\".devDependencies.ajv", lock.packages?.[""]?.devDependencies?.ajv);
    failures += requireStringField(
      lockPath,
      "packages.\"\".devDependencies.ajv-formats",
      lock.packages?.[""]?.devDependencies?.["ajv-formats"]
    );
    failures += requireStringField(lockPath, "packages.node_modules/ajv.integrity", lock.packages?.["node_modules/ajv"]?.integrity);
    failures += requireStringField(
      lockPath,
      "packages.node_modules/ajv-formats.integrity",
      lock.packages?.["node_modules/ajv-formats"]?.integrity
    );
  }

  return failures;
}

function validateSchemaDocuments() {
  let failures = 0;
  const profileSchemaPath = path.join(
    repoRoot,
    "skills",
    "manage-json-schemas",
    "references",
    "schema-profile",
    "schema-profile.schema.json"
  );
  const profileSchema = readJson(profileSchemaPath);
  const validateProfile = ajv.compile(profileSchema);

  for (const schemaPath of listJsonFilesRecursive(repoRoot).filter((file) => file.endsWith(".schema.json"))) {
    const schema = readJson(schemaPath);
    let ok = true;
    if (schema.$schema === "../../schema-profile/schema-profile.schema.json") {
      ok = validateProfile(schema);
      if (!ok) {
        console.error(`FAIL ${path.relative(repoRoot, schemaPath)}`);
        console.error(JSON.stringify(validateProfile.errors, null, 2));
      }
    } else {
      const validator = schema.$schema === "http://json-schema.org/draft-07/schema#" ? draft7Ajv : ajv;
      ok = validator.validateSchema(schema);
      if (!ok) {
        console.error(`FAIL ${path.relative(repoRoot, schemaPath)}`);
        console.error(JSON.stringify(validator.errors, null, 2));
      }
    }

    if (ok) {
      markValidated(schemaPath);
      console.log(`OK ${path.relative(repoRoot, schemaPath)}`);
    } else {
      failures += 1;
    }
  }
  return failures;
}

function validateHydratedInstructionAdapters(directory) {
  if (!directory) {
    return 0;
  }

  let failures = 0;
  for (const pluginDir of hydratedPluginDirs(directory)) {
    const hasAgentProfiles = hasFilesRecursive(path.join(pluginDir, "agents"));
    const hasInstructions = hasFilesRecursive(path.join(pluginDir, "instructions"));
    if (!hasAgentProfiles && !hasInstructions) {
      continue;
    }

    const agentsPath = path.join(pluginDir, "AGENTS.md");
    if (!fs.existsSync(agentsPath)) {
      console.error(
        `FAIL ${path.relative(repoRoot, pluginDir)}: missing AGENTS.md adapter for hydrated agents/instructions`
      );
      failures += 1;
      continue;
    }

    const content = fs.readFileSync(agentsPath, "utf8");
    if (!content.includes("generated adapter") || !content.includes("Runtime Boundary")) {
      console.error(
        `FAIL ${path.relative(repoRoot, agentsPath)}: AGENTS.md adapter must identify its generated runtime boundary`
      );
      failures += 1;
      continue;
    }

    console.log(`OK ${path.relative(repoRoot, agentsPath)}`);
  }
  return failures;
}

function hydratedPluginDirs(directory) {
  return [
    path.join(directory, "codex", "plugins"),
    path.join(directory, ".github", "plugin", "plugins"),
    path.join(directory, "plugins")
  ]
    .filter((pluginRoot) => fs.existsSync(pluginRoot))
    .flatMap((pluginRoot) =>
      fs.readdirSync(pluginRoot, { withFileTypes: true })
        .filter((entry) => entry.isDirectory())
        .map((entry) => path.join(pluginRoot, entry.name))
    )
    .sort();
}

function hasFilesRecursive(directory) {
  if (!fs.existsSync(directory)) {
    return false;
  }

  const stack = [directory];
  while (stack.length > 0) {
    const current = stack.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const entryPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(entryPath);
        continue;
      }
      if (entry.isFile()) {
        return true;
      }
    }
  }
  return false;
}

function validateJsonCoverage() {
  let failures = 0;
  for (const filePath of listJsonFilesRecursive(repoRoot)) {
    if (validatedJsonFiles.has(normalizePath(filePath))) {
      continue;
    }
    console.error(
      `FAIL ${path.relative(repoRoot, filePath)}: JSON file is not covered by a schema validation path`
    );
    failures += 1;
  }
  return failures;
}

function optionalChecks(schemaId, filePath) {
  return fs.existsSync(filePath) ? [[schemaId, filePath]] : [];
}

function requireJsonValue(filePath, field, actual, expected) {
  if (actual === expected) {
    return 0;
  }
  console.error(
    `FAIL ${path.relative(repoRoot, filePath)}: ${field} must be ${JSON.stringify(expected)}, found ${JSON.stringify(actual)}`
  );
  return 1;
}

function requireStringField(filePath, field, value) {
  if (typeof value === "string" && value.length > 0) {
    return 0;
  }
  console.error(`FAIL ${path.relative(repoRoot, filePath)}: ${field} must be a non-empty string`);
  return 1;
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

function listCodexPluginManifests(directory) {
  if (!fs.existsSync(directory)) {
    return [];
  }
  return fs.readdirSync(directory, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => path.join(directory, entry.name, ".codex-plugin", "plugin.json"))
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

function listJsonFilesRecursive(directory) {
  const results = [];
  const skippedDirectories = new Set([
    ".git",
    ".idea",
    ".agent-turn",
    ".venv",
    ".venv-docs",
    "dist",
    "node_modules",
    "site"
  ]);

  function visit(current) {
    if (!fs.existsSync(current)) {
      return;
    }
    const stat = fs.lstatSync(current);
    if (stat.isSymbolicLink()) {
      return;
    }
    if (stat.isDirectory()) {
      const name = path.basename(current);
      if (skippedDirectories.has(name)) {
        return;
      }
      if (generatedJsonRoots.includes(normalizePath(current))) {
        return;
      }
      for (const entry of fs.readdirSync(current).sort()) {
        visit(path.join(current, entry));
      }
      return;
    }
    if (stat.isFile() && current.endsWith(".json")) {
      results.push(current);
    }
  }

  visit(directory);
  return results.sort();
}

function listSchemaFilesRecursive(directory) {
  return listJsonFilesRecursive(directory).filter((file) => file.endsWith(".schema.json"));
}

function loadDependency(specifier) {
  try {
    return requireFromRepo(specifier);
  } catch (error) {
    if (error?.code === "MODULE_NOT_FOUND") {
      console.error(`Missing Node validation dependency: ${specifier}. Run npm ci from the repository root.`);
      process.exit(1);
    }
    throw error;
  }
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function markValidated(filePath) {
  validatedJsonFiles.add(normalizePath(filePath));
}

function normalizePath(filePath) {
  return path.resolve(filePath);
}
