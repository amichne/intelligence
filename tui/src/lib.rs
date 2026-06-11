use std::collections::{BTreeMap, BTreeSet};
use std::env;
use std::io::{BufRead, BufReader, Write};
use std::process::{Child, ChildStdin, Command, Stdio};

use crossterm::event::{KeyCode, KeyEvent};
use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::symbols;
use ratatui::text::{Line, Span};
use ratatui::widgets::{
    Block, Borders, Cell, Clear, List, ListItem, ListState, Paragraph, Row, Table, TableState, Wrap,
};
use serde::Deserialize;
use serde_json::{json, Value};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ColorSupport {
    Monochrome,
    Ansi16,
    Ansi256,
    TrueColor,
}

impl ColorSupport {
    fn from_env() -> Self {
        Self::detect(
            env::var_os("NO_COLOR").is_some(),
            env::var("COLORTERM").ok().as_deref(),
            env::var("TERM").ok().as_deref(),
        )
    }

    fn detect(no_color: bool, colorterm: Option<&str>, term: Option<&str>) -> Self {
        if no_color {
            return Self::Monochrome;
        }
        if colorterm.is_some_and(|value| {
            let normalized = value.to_ascii_lowercase();
            normalized == "truecolor" || normalized == "24bit"
        }) {
            return Self::TrueColor;
        }
        if term.is_some_and(|value| value.contains("256color")) {
            return Self::Ansi256;
        }
        Self::Ansi16
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ColorSlot {
    Default,
    Muted,
    Emphasis,
    Surface,
    Overlay,
    Selection,
    AccentPrimary,
    AccentSecondary,
    Plugin,
    Skill,
    Agent,
    Hook,
    Instruction,
    Catalog,
    Local,
    Info,
    Success,
    Warning,
    Error,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
struct UiTheme {
    color_support: ColorSupport,
}

impl UiTheme {
    fn current() -> Self {
        Self::with_color_support(ColorSupport::from_env())
    }

    fn with_color_support(color_support: ColorSupport) -> Self {
        Self { color_support }
    }

    fn fg(self, slot: ColorSlot) -> Style {
        apply_fg(Style::new(), self.color(slot))
    }

    fn bg(self, slot: ColorSlot) -> Style {
        apply_bg(Style::new(), self.color(slot))
    }

    fn muted(self) -> Style {
        self.fg(ColorSlot::Muted).add_modifier(Modifier::DIM)
    }

    fn emphasis(self) -> Style {
        self.fg(ColorSlot::Emphasis).add_modifier(Modifier::BOLD)
    }

    fn panel_border(self, focused: bool) -> Style {
        if focused {
            self.fg(ColorSlot::AccentPrimary)
                .add_modifier(Modifier::BOLD)
        } else {
            self.fg(ColorSlot::Muted)
        }
    }

    fn panel_title(self, focused: bool) -> Style {
        if focused {
            self.fg(ColorSlot::AccentPrimary)
                .add_modifier(Modifier::BOLD)
        } else {
            self.fg(ColorSlot::Emphasis)
        }
    }

    fn selected(self) -> Style {
        let style = if self.color_support == ColorSupport::Monochrome {
            Style::new().add_modifier(Modifier::REVERSED)
        } else {
            apply_fg(
                self.bg(ColorSlot::Selection),
                self.color(ColorSlot::Emphasis),
            )
        };
        style.add_modifier(Modifier::BOLD)
    }

    fn status(self, severity: StatusSeverity) -> Style {
        let slot = match severity {
            StatusSeverity::Info => ColorSlot::Info,
            StatusSeverity::Success => ColorSlot::Success,
            StatusSeverity::Warning => ColorSlot::Warning,
            StatusSeverity::Error => ColorSlot::Error,
        };
        self.fg(slot).add_modifier(Modifier::BOLD)
    }

    fn color(self, slot: ColorSlot) -> Option<Color> {
        match self.color_support {
            ColorSupport::Monochrome => None,
            ColorSupport::Ansi16 => Some(match slot {
                ColorSlot::Default => Color::Gray,
                ColorSlot::Muted | ColorSlot::Surface | ColorSlot::Overlay => Color::DarkGray,
                ColorSlot::Emphasis | ColorSlot::Selection => Color::White,
                ColorSlot::AccentPrimary | ColorSlot::Plugin | ColorSlot::Catalog => Color::Cyan,
                ColorSlot::AccentSecondary | ColorSlot::Agent | ColorSlot::Local => Color::Magenta,
                ColorSlot::Skill | ColorSlot::Instruction | ColorSlot::Warning => Color::Yellow,
                ColorSlot::Hook | ColorSlot::Success => Color::Green,
                ColorSlot::Info => Color::Cyan,
                ColorSlot::Error => Color::Red,
            }),
            ColorSupport::Ansi256 => Some(Color::Indexed(match slot {
                ColorSlot::Default => 252,
                ColorSlot::Muted => 244,
                ColorSlot::Emphasis => 15,
                ColorSlot::Surface => 236,
                ColorSlot::Overlay => 240,
                ColorSlot::Selection => 24,
                ColorSlot::AccentPrimary | ColorSlot::Plugin | ColorSlot::Catalog => 75,
                ColorSlot::AccentSecondary | ColorSlot::Agent | ColorSlot::Local => 141,
                ColorSlot::Skill | ColorSlot::Instruction | ColorSlot::Warning => 179,
                ColorSlot::Hook | ColorSlot::Success => 114,
                ColorSlot::Info => 81,
                ColorSlot::Error => 203,
            })),
            ColorSupport::TrueColor => Some(match slot {
                ColorSlot::Default => Color::Rgb(192, 202, 245),
                ColorSlot::Muted => Color::Rgb(86, 95, 137),
                ColorSlot::Emphasis => Color::Rgb(224, 224, 224),
                ColorSlot::Surface => Color::Rgb(36, 40, 59),
                ColorSlot::Overlay => Color::Rgb(65, 72, 104),
                ColorSlot::Selection => Color::Rgb(54, 74, 130),
                ColorSlot::AccentPrimary | ColorSlot::Plugin | ColorSlot::Catalog => {
                    Color::Rgb(122, 162, 247)
                }
                ColorSlot::AccentSecondary | ColorSlot::Agent | ColorSlot::Local => {
                    Color::Rgb(187, 154, 247)
                }
                ColorSlot::Skill | ColorSlot::Instruction | ColorSlot::Warning => {
                    Color::Rgb(224, 175, 104)
                }
                ColorSlot::Hook | ColorSlot::Success => Color::Rgb(158, 206, 106),
                ColorSlot::Info => Color::Rgb(125, 207, 255),
                ColorSlot::Error => Color::Rgb(247, 118, 142),
            }),
        }
    }
}

fn apply_fg(style: Style, color: Option<Color>) -> Style {
    color.map_or(style, |color| style.fg(color))
}

fn apply_bg(style: Style, color: Option<Color>) -> Style {
    color.map_or(style, |color| style.bg(color))
}

#[derive(Debug, Clone)]
pub struct Config {
    pub repo_root: String,
    pub ref_name: Option<String>,
    pub intelligence_bin: String,
    pub browse_repository: String,
    pub default_git_host: GitHost,
}

impl Config {
    pub fn usage() -> &'static str {
        "Usage: intelligence-tui [--repo PATH] [--ref REF] [--intelligence-bin PATH] [--browse REPOSITORY] [--git-host HOST]"
    }

    pub fn from_args(args: impl Iterator<Item = String>) -> Result<Self, String> {
        let mut repo_root = ".".to_string();
        let mut ref_name = None;
        let mut intelligence_bin = "intelligence".to_string();
        let mut browse_repository = "amichne/slopsentral".to_string();
        let mut default_git_host = GitHost::github();
        let mut args = args.peekable();
        while let Some(arg) = args.next() {
            match arg.as_str() {
                "--repo" => repo_root = required_value("--repo", &mut args)?,
                "--ref" => ref_name = Some(required_value("--ref", &mut args)?),
                "--intelligence-bin" => {
                    intelligence_bin = required_value("--intelligence-bin", &mut args)?
                }
                "--browse" => browse_repository = required_value("--browse", &mut args)?,
                "--git-host" => {
                    default_git_host = GitHost::parse(&required_value("--git-host", &mut args)?)?
                }
                "-h" | "--help" => return Err(Self::usage().to_string()),
                _ => return Err(format!("unknown argument: {arg}")),
            }
        }
        Ok(Self {
            repo_root,
            ref_name,
            intelligence_bin,
            browse_repository,
            default_git_host,
        })
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct GitHost(String);

impl GitHost {
    pub fn github() -> Self {
        Self("github.com".to_string())
    }

    pub fn parse(value: &str) -> Result<Self, String> {
        let trimmed = value.trim();
        let without_scheme = trimmed
            .strip_prefix("https://")
            .or_else(|| trimmed.strip_prefix("http://"))
            .unwrap_or(trimmed);
        let host = without_scheme.trim_end_matches('/');
        if host.is_empty() {
            return Err("git host must not be empty".to_string());
        }
        if host.contains(char::is_whitespace) || host.contains('/') {
            return Err(format!("git host must be a host name, got `{value}`"));
        }
        Ok(Self(host.to_string()))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }

    pub fn repository_ref(&self, input: &str) -> Result<String, String> {
        let trimmed = input.trim().trim_start_matches('@');
        if trimmed.is_empty() {
            return Err(
                "repository search requires owner/repo, a Git URL, or a local path".to_string(),
            );
        }
        if looks_like_local_path(trimmed) || looks_like_git_url(trimmed) {
            return Ok(trimmed.to_string());
        }
        if is_owner_repo(trimmed) {
            return if self.0 == "github.com" {
                Ok(trimmed.to_string())
            } else {
                Ok(format!("https://{}/{trimmed}.git", self.0))
            };
        }
        Err(format!(
            "repository search expects owner/repo, Git URL, or local path, got `{input}`"
        ))
    }
}

fn required_value(
    option: &str,
    args: &mut std::iter::Peekable<impl Iterator<Item = String>>,
) -> Result<String, String> {
    args.next()
        .filter(|value| !value.trim().is_empty())
        .ok_or_else(|| format!("{option} requires a value"))
}

pub struct RpcClient {
    child: Child,
    stdin: ChildStdin,
    stdout: BufReader<std::process::ChildStdout>,
    next_id: u64,
}

impl RpcClient {
    pub fn spawn(intelligence_bin: &str) -> Result<Self, String> {
        let mut child = Command::new(intelligence_bin)
            .arg("rpc")
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::inherit())
            .spawn()
            .map_err(|error| format!("failed to start `{intelligence_bin} rpc`: {error}"))?;
        let stdin = child
            .stdin
            .take()
            .ok_or_else(|| "failed to open rpc stdin".to_string())?;
        let stdout = child
            .stdout
            .take()
            .ok_or_else(|| "failed to open rpc stdout".to_string())?;
        Ok(Self {
            child,
            stdin,
            stdout: BufReader::new(stdout),
            next_id: 1,
        })
    }

    pub fn catalog(&mut self, repo_root: &str, repository: &str) -> Result<Catalog, String> {
        self.call(
            "marketplace.catalog",
            json!({
                "repoRoot": repo_root,
                "repository": repository,
                "provider": "auto",
                "checkUpdates": false
            }),
        )
    }

    pub fn validate(&mut self, repo_root: &str) -> Result<Value, String> {
        self.call_value(
            "validation.run",
            json!({
                "repoRoot": repo_root,
                "portable": true
            }),
        )
    }

    pub fn call<T: for<'de> Deserialize<'de>>(
        &mut self,
        method: &str,
        params: Value,
    ) -> Result<T, String> {
        let value = self.call_value(method, params)?;
        serde_json::from_value(value).map_err(|error| format!("invalid {method} response: {error}"))
    }

    pub fn call_value(&mut self, method: &str, params: Value) -> Result<Value, String> {
        let id = self.next_id;
        self.next_id += 1;
        let request = json!({
            "jsonrpc": "2.0",
            "id": id,
            "method": method,
            "params": params
        });
        writeln!(self.stdin, "{request}")
            .map_err(|error| format!("failed to send rpc request: {error}"))?;
        self.stdin
            .flush()
            .map_err(|error| format!("failed to flush rpc request: {error}"))?;

        let mut line = String::new();
        self.stdout
            .read_line(&mut line)
            .map_err(|error| format!("failed to read rpc response: {error}"))?;
        if line.trim().is_empty() {
            return Err("rpc server closed without a response".to_string());
        }
        let response: Value = serde_json::from_str(&line)
            .map_err(|error| format!("invalid rpc response JSON: {error}"))?;
        if let Some(error) = response.get("error") {
            let message = error
                .get("message")
                .and_then(Value::as_str)
                .unwrap_or("rpc error");
            return Err(message.to_string());
        }
        response
            .get("result")
            .cloned()
            .ok_or_else(|| "rpc response did not contain result".to_string())
    }
}

impl Drop for RpcClient {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

#[derive(Debug, Clone, Deserialize)]
pub struct Catalog {
    pub marketplace: MarketplaceSummary,
    #[serde(default)]
    pub plugins: Vec<PluginOffering>,
    #[serde(default, rename = "standalonePrimitives")]
    pub standalone_primitives: StandalonePrimitives,
    #[serde(default)]
    pub installed: Vec<InstalledPlugin>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct MarketplaceSummary {
    pub name: String,
    pub provider: String,
    pub repository: String,
    pub entrypoint: String,
    #[serde(default)]
    pub description: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct PluginOffering {
    pub name: String,
    #[serde(default)]
    pub description: Option<String>,
    #[serde(default)]
    pub category: Option<String>,
    #[serde(default)]
    pub tags: Vec<String>,
}

#[derive(Debug, Clone, Default, Deserialize)]
pub struct StandalonePrimitives {
    #[serde(default)]
    pub skills: Vec<PrimitiveOffering>,
    #[serde(default)]
    pub agents: Vec<PrimitiveOffering>,
    #[serde(default)]
    pub hooks: Vec<PrimitiveOffering>,
    #[serde(default)]
    pub instructions: Vec<PrimitiveOffering>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct PrimitiveOffering {
    pub name: String,
    #[serde(default)]
    pub description: Option<String>,
    #[serde(rename = "type")]
    pub primitive_type: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct InstalledPlugin {
    pub name: String,
    #[serde(default)]
    pub version: Option<String>,
    #[serde(default, rename = "currentVersion")]
    pub current_version: Option<String>,
    #[serde(default)]
    pub imported: bool,
    #[serde(default)]
    pub locked: bool,
    #[serde(default)]
    pub outdated: bool,
    #[serde(default)]
    pub marketplace: Option<String>,
    #[serde(default)]
    pub description: Option<String>,
    #[serde(default)]
    pub tags: Vec<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FocusPane {
    Sources,
    Offerings,
    Details,
    Staging,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum InputMode {
    Normal,
    Search,
    Command,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StatusSeverity {
    Info,
    Success,
    Warning,
    Error,
}

impl StatusSeverity {
    fn label(self) -> &'static str {
        match self {
            Self::Info => "INFO",
            Self::Success => "SUCCESS",
            Self::Warning => "WARNING",
            Self::Error => "ERROR",
        }
    }

    fn style(self, theme: UiTheme) -> Style {
        theme.status(self)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StatusMessage {
    pub severity: StatusSeverity,
    pub text: String,
}

impl StatusMessage {
    fn new(severity: StatusSeverity, text: impl Into<String>) -> Self {
        Self {
            severity,
            text: text.into(),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SearchScope {
    All,
    Repository,
    User,
    Plugin,
    Primitive,
    Installed,
}

impl SearchScope {
    fn label(self) -> &'static str {
        match self {
            Self::All => "all",
            Self::Repository => "repository",
            Self::User => "user",
            Self::Plugin => "plugin",
            Self::Primitive => "primitive",
            Self::Installed => "installed",
        }
    }

    fn next(self) -> Self {
        match self {
            Self::All => Self::Repository,
            Self::Repository => Self::User,
            Self::User => Self::Plugin,
            Self::Plugin => Self::Primitive,
            Self::Primitive => Self::Installed,
            Self::Installed => Self::All,
        }
    }

    fn previous(self) -> Self {
        match self {
            Self::All => Self::Installed,
            Self::Repository => Self::All,
            Self::User => Self::Repository,
            Self::Plugin => Self::User,
            Self::Primitive => Self::Plugin,
            Self::Installed => Self::Primitive,
        }
    }

    fn parse(value: &str) -> Option<Self> {
        match value.trim().to_lowercase().as_str() {
            "all" => Some(Self::All),
            "repo" | "repos" | "repository" | "repositories" => Some(Self::Repository),
            "user" | "users" | "owner" | "owners" => Some(Self::User),
            "plugin" | "plugins" => Some(Self::Plugin),
            "primitive" | "primitives" | "skill" | "skills" | "agent" | "agents" | "hook"
            | "hooks" | "instruction" | "instructions" => Some(Self::Primitive),
            "installed" | "local" => Some(Self::Installed),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum OfferingKind {
    Plugin,
    Skill,
    Agent,
    Hook,
    Instruction,
    Guided(GuidedActionKind),
}

impl OfferingKind {
    fn label(&self) -> &'static str {
        match self {
            Self::Plugin => "plugin",
            Self::Skill => "skill",
            Self::Agent => "agent",
            Self::Hook => "hook",
            Self::Instruction => "instruction",
            Self::Guided(kind) => kind.label(),
        }
    }

    fn marker(&self) -> &'static str {
        match self {
            Self::Plugin => "●",
            Self::Skill => "◆",
            Self::Agent => "▲",
            Self::Hook => "◇",
            Self::Instruction => "■",
            Self::Guided(kind) => kind.marker(),
        }
    }

    fn table_label(&self) -> &'static str {
        match self {
            Self::Plugin => "plugin",
            Self::Skill => "skill",
            Self::Agent => "agent",
            Self::Hook => "hook",
            Self::Instruction => "instr",
            Self::Guided(kind) => kind.table_label(),
        }
    }

    fn color_slot(&self) -> ColorSlot {
        match self {
            Self::Plugin => ColorSlot::Plugin,
            Self::Skill => ColorSlot::Skill,
            Self::Agent => ColorSlot::Agent,
            Self::Hook => ColorSlot::Hook,
            Self::Instruction => ColorSlot::Instruction,
            Self::Guided(kind) => kind.color_slot(),
        }
    }

    fn style(&self, theme: UiTheme) -> Style {
        let style = theme.fg(self.color_slot());
        if matches!(self, Self::Plugin) {
            style.add_modifier(Modifier::BOLD)
        } else {
            style
        }
    }

    fn is_primitive(&self) -> bool {
        matches!(
            self,
            Self::Skill | Self::Agent | Self::Hook | Self::Instruction
        )
    }

    fn guided_action(&self) -> Option<GuidedActionKind> {
        match self {
            Self::Guided(kind) => Some(*kind),
            _ => None,
        }
    }

    fn is_guided(&self) -> bool {
        self.guided_action().is_some()
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum GuidedActionKind {
    DiscoverSource,
    AuthorResource,
    EditResource,
    ProjectOutputs,
}

impl GuidedActionKind {
    fn label(self) -> &'static str {
        match self {
            Self::DiscoverSource => "discover",
            Self::AuthorResource => "author",
            Self::EditResource => "edit",
            Self::ProjectOutputs => "outputs",
        }
    }

    fn marker(self) -> &'static str {
        match self {
            Self::DiscoverSource => "?",
            Self::AuthorResource => "+",
            Self::EditResource => "~",
            Self::ProjectOutputs => "▣",
        }
    }

    fn table_label(self) -> &'static str {
        match self {
            Self::DiscoverSource => "find",
            Self::AuthorResource => "author",
            Self::EditResource => "edit",
            Self::ProjectOutputs => "output",
        }
    }

    fn color_slot(self) -> ColorSlot {
        match self {
            Self::DiscoverSource => ColorSlot::Info,
            Self::AuthorResource => ColorSlot::Success,
            Self::EditResource => ColorSlot::AccentSecondary,
            Self::ProjectOutputs => ColorSlot::Warning,
        }
    }

    fn flow_kind(self) -> FlowKind {
        match self {
            Self::DiscoverSource => FlowKind::Discover,
            Self::AuthorResource => FlowKind::Author,
            Self::EditResource => FlowKind::Edit,
            Self::ProjectOutputs => FlowKind::Outputs,
        }
    }

    fn source_scope(self) -> SourceScope {
        match self {
            Self::DiscoverSource => SourceScope::RemoteSource,
            Self::AuthorResource | Self::EditResource => SourceScope::PersonalSource,
            Self::ProjectOutputs => SourceScope::CurrentRepository,
        }
    }

    fn target_scope(self) -> TargetScope {
        match self {
            Self::DiscoverSource => TargetScope::Repository,
            Self::AuthorResource | Self::EditResource => TargetScope::PersonalSource,
            Self::ProjectOutputs => TargetScope::ProviderOutput,
        }
    }

    fn operation_kind(self) -> OperationKind {
        match self {
            Self::DiscoverSource => OperationKind::Read,
            Self::AuthorResource => OperationKind::Create,
            Self::EditResource => OperationKind::Edit,
            Self::ProjectOutputs => OperationKind::Project,
        }
    }

    fn guidance(self) -> &'static str {
        match self {
            Self::DiscoverSource => {
                "Use :browse owner/repo, :preview /path/to/source, or repository search to read another marketplace without writing state."
            }
            Self::AuthorResource => {
                "Create reusable skills, plugins, hooks, and agents in the owning marketplace source, usually /Users/amichne/code/slopsentral; this TUI does not author files yet."
            }
            Self::EditResource => {
                "Edit resources where they are authored: repository source for repo-owned content, or the personal marketplace source for reusable content."
            }
            Self::ProjectOutputs => {
                "Provider output is generated from source and install state. Use marketplace materialize or marketplace publish outside the TUI for now."
            }
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FlowKind {
    Discover,
    Installed,
    AddToRepository,
    Author,
    Edit,
    Outputs,
}

impl FlowKind {
    fn label(self) -> &'static str {
        match self {
            Self::Discover => "discover",
            Self::Installed => "installed",
            Self::AddToRepository => "add to repo",
            Self::Author => "author",
            Self::Edit => "edit",
            Self::Outputs => "outputs",
        }
    }

    fn table_label(self) -> &'static str {
        match self {
            Self::Discover => "find",
            Self::Installed => "local",
            Self::AddToRepository => "add",
            Self::Author => "author",
            Self::Edit => "edit",
            Self::Outputs => "output",
        }
    }

    fn rank(self) -> u8 {
        match self {
            Self::AddToRepository => 0,
            Self::Installed => 1,
            Self::Discover => 2,
            Self::Author => 3,
            Self::Edit => 4,
            Self::Outputs => 5,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TargetScope {
    Repository,
    PersonalSource,
    ProviderOutput,
}

impl TargetScope {
    fn label(self) -> &'static str {
        match self {
            Self::Repository => "repository",
            Self::PersonalSource => "personal source",
            Self::ProviderOutput => "provider output",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SourceScope {
    CurrentRepository,
    PersonalSource,
    RemoteSource,
    ResolvedCache,
    InstalledState,
}

impl SourceScope {
    fn label(self) -> &'static str {
        match self {
            Self::CurrentRepository => "current repository",
            Self::PersonalSource => "personal source",
            Self::RemoteSource => "remote source",
            Self::ResolvedCache => "resolved cache",
            Self::InstalledState => "installed state",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OperationKind {
    Read,
    Import,
    Install,
    Create,
    Edit,
    Update,
    Validate,
    Project,
    Publish,
}

impl OperationKind {
    fn label(self) -> &'static str {
        match self {
            Self::Read => "read",
            Self::Import => "import",
            Self::Install => "install",
            Self::Create => "create",
            Self::Edit => "edit",
            Self::Update => "update",
            Self::Validate => "validate",
            Self::Project => "project",
            Self::Publish => "publish",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OfferingSource {
    Marketplace,
    Installed,
    Guidance,
}

impl OfferingSource {
    fn label(self) -> &'static str {
        match self {
            Self::Marketplace => "catalog",
            Self::Installed => "local",
            Self::Guidance => "guided path",
        }
    }

    fn marker(self) -> &'static str {
        match self {
            Self::Marketplace => "◉",
            Self::Installed => "○",
            Self::Guidance => "◇",
        }
    }

    fn style(self, theme: UiTheme) -> Style {
        match self {
            Self::Marketplace => theme.fg(ColorSlot::Catalog),
            Self::Installed => theme.fg(ColorSlot::Local),
            Self::Guidance => theme.fg(ColorSlot::Info),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum OfferingInstallState {
    Available,
    Installed,
    Pinned,
    UpdateAvailable,
    Guided,
}

impl OfferingInstallState {
    fn from_offering(offering: &Offering) -> Self {
        if offering.kind.is_guided() {
            return Self::Guided;
        }
        offering
            .installed
            .as_ref()
            .map(|installed| {
                if installed.outdated {
                    Self::UpdateAvailable
                } else if installed.locked {
                    Self::Pinned
                } else {
                    Self::Installed
                }
            })
            .unwrap_or(Self::Available)
    }

    fn label(self) -> &'static str {
        match self {
            Self::Available => "available",
            Self::Installed => "installed",
            Self::Pinned => "pinned",
            Self::UpdateAvailable => "update",
            Self::Guided => "guided",
        }
    }

    fn marker(self) -> &'static str {
        match self {
            Self::Available => "□",
            Self::Installed => "✓",
            Self::Pinned => "◆",
            Self::UpdateAvailable => "▲",
            Self::Guided => "?",
        }
    }

    fn table_label(self) -> &'static str {
        match self {
            Self::Available => "avail",
            Self::Installed => "inst",
            Self::Pinned => "pin",
            Self::UpdateAvailable => "update",
            Self::Guided => "guide",
        }
    }

    fn style(self, theme: UiTheme) -> Style {
        match self {
            Self::Available => theme.muted(),
            Self::Installed => theme.fg(ColorSlot::Success),
            Self::Pinned => theme.fg(ColorSlot::AccentSecondary),
            Self::UpdateAvailable => theme.fg(ColorSlot::Warning).add_modifier(Modifier::BOLD),
            Self::Guided => theme.fg(ColorSlot::Info),
        }
    }
}

#[derive(Debug, Clone)]
pub struct Offering {
    pub kind: OfferingKind,
    pub name: String,
    pub description: Option<String>,
    pub tags: Vec<String>,
    pub installed: Option<InstalledPlugin>,
    pub repository: Option<String>,
    pub source: OfferingSource,
}

impl Offering {
    fn flow_kind(&self) -> FlowKind {
        if let Some(guided) = self.kind.guided_action() {
            guided.flow_kind()
        } else if self.installed.is_some() || self.source == OfferingSource::Installed {
            FlowKind::Installed
        } else {
            FlowKind::AddToRepository
        }
    }

    fn source_scope(&self) -> SourceScope {
        if let Some(guided) = self.kind.guided_action() {
            return guided.source_scope();
        }
        if self.source == OfferingSource::Installed || self.installed.is_some() {
            return SourceScope::InstalledState;
        }
        self.repository
            .as_deref()
            .filter(|repository| looks_like_local_path(repository))
            .map(|_| SourceScope::PersonalSource)
            .unwrap_or(SourceScope::RemoteSource)
    }

    fn target_scope(&self) -> TargetScope {
        self.kind
            .guided_action()
            .map(GuidedActionKind::target_scope)
            .unwrap_or(TargetScope::Repository)
    }

    fn operation_kind(&self) -> OperationKind {
        if let Some(guided) = self.kind.guided_action() {
            return guided.operation_kind();
        }
        if self.installed.is_some() || self.source == OfferingSource::Installed {
            OperationKind::Update
        } else {
            OperationKind::Import
        }
    }

    fn guidance_message(&self) -> Option<&'static str> {
        self.kind.guided_action().map(GuidedActionKind::guidance)
    }
}

#[derive(Debug, Clone)]
pub struct PendingAction {
    pub title: String,
    pub method: String,
    pub params: Value,
    pub validate_after: bool,
    pub reload_after: bool,
    pub confirmation: ConfirmationKind,
    pub provenance: ActionProvenance,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ConfirmationKind {
    Inline,
    Modal,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ActionProvenance {
    PluginImport {
        plugin: String,
        repository: String,
        target: String,
        ref_name: Option<String>,
    },
    PrimitiveImport {
        kind: String,
        name: String,
        repository: String,
        target: String,
        ref_name: Option<String>,
    },
    MarketplaceInstall {
        repository: String,
        target: String,
        ref_name: Option<String>,
    },
    InstalledUpdate {
        plugin: String,
        source: Option<String>,
        target: String,
    },
    InstalledPin {
        plugin: String,
        version: String,
        target: String,
    },
    InstalledUnpin {
        plugin: String,
        target: String,
    },
    UpdateAll {
        target: String,
    },
    Validate {
        target: String,
    },
    RemoteList {
        target: String,
    },
    Other {
        action: String,
        target: String,
    },
}

impl ActionProvenance {
    fn summary(&self) -> String {
        match self {
            Self::PluginImport {
                plugin,
                repository,
                target,
                ref_name,
            } => format!(
                "install plugin {plugin} from {} → {target}",
                repository_with_ref(repository, ref_name.as_deref())
            ),
            Self::PrimitiveImport {
                kind,
                name,
                repository,
                target,
                ref_name,
            } => format!(
                "import {kind} {name} from {} → {target}",
                repository_with_ref(repository, ref_name.as_deref())
            ),
            Self::MarketplaceInstall {
                repository,
                target,
                ref_name,
            } => format!(
                "install all plugins from {} → {target}",
                repository_with_ref(repository, ref_name.as_deref())
            ),
            Self::InstalledUpdate {
                plugin,
                source,
                target,
            } => {
                if let Some(source) = source {
                    format!("update plugin {plugin} from {source} → {target}")
                } else {
                    format!("update plugin {plugin} → {target}")
                }
            }
            Self::InstalledPin {
                plugin,
                version,
                target,
            } => format!("pin plugin {plugin} to {version} in {target}"),
            Self::InstalledUnpin { plugin, target } => {
                format!("unpin plugin {plugin} in {target}")
            }
            Self::UpdateAll { target } => format!("update all imported plugins in {target}"),
            Self::Validate { target } => format!("validate {target}"),
            Self::RemoteList { target } => format!("list remotes for {target}"),
            Self::Other { action, target } => format!("{action} → {target}"),
        }
    }
}

fn repository_with_ref(repository: &str, ref_name: Option<&str>) -> String {
    match ref_name.filter(|value| !value.trim().is_empty()) {
        Some(ref_name) => format!("{repository}@{ref_name}"),
        None => repository.to_string(),
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct CommandSuggestion {
    pub command: &'static str,
    pub applicable: bool,
    pub flow: FlowKind,
}

#[derive(Debug, Clone)]
pub enum UiEffect {
    None,
    Quit,
    LoadCatalog,
    Call {
        title: String,
        method: String,
        params: Value,
        validate_after: bool,
        reload_after: bool,
    },
}

pub struct App {
    pub repo_root: String,
    pub browse_repository: String,
    pub ref_name: Option<String>,
    pub default_git_host: GitHost,
    pub marketplace: Option<MarketplaceSummary>,
    pub offerings: Vec<Offering>,
    pub installed: Vec<InstalledPlugin>,
    pub filtered: Vec<usize>,
    pub selected: usize,
    pub staged: Vec<PendingAction>,
    pub staged_selected: usize,
    pub focus: FocusPane,
    pub input_mode: InputMode,
    pub search_scope: SearchScope,
    pub search_query: String,
    pub command_input: String,
    pub status: Option<StatusMessage>,
    pub pending: Option<PendingAction>,
    pub show_help: bool,
    pub should_quit: bool,
}

impl App {
    pub fn new(config: Config) -> Self {
        Self {
            repo_root: config.repo_root,
            browse_repository: config.browse_repository,
            ref_name: config.ref_name,
            default_git_host: config.default_git_host,
            marketplace: None,
            offerings: Vec::new(),
            installed: Vec::new(),
            filtered: Vec::new(),
            selected: 0,
            staged: Vec::new(),
            staged_selected: 0,
            focus: FocusPane::Offerings,
            input_mode: InputMode::Normal,
            search_scope: SearchScope::All,
            search_query: String::new(),
            command_input: String::new(),
            status: Some(StatusMessage::new(
                StatusSeverity::Info,
                "Loading marketplace catalog...",
            )),
            pending: None,
            show_help: false,
            should_quit: false,
        }
    }

    pub fn set_catalog(&mut self, catalog: Catalog) {
        let repository = catalog.marketplace.repository.clone();
        self.browse_repository = repository.clone();
        self.marketplace = Some(catalog.marketplace);
        self.installed = catalog.installed;
        self.offerings = offerings_from_catalog(
            catalog.plugins,
            catalog.standalone_primitives,
            &self.installed,
            &repository,
        );
        self.apply_filter();
        let marketplace_resources = self
            .offerings
            .iter()
            .filter(|offering| !offering.kind.is_guided())
            .count();
        let guided_paths = self
            .offerings
            .iter()
            .filter(|offering| offering.kind.is_guided())
            .count();
        self.set_status(
            StatusSeverity::Success,
            format!(
                "Loaded {marketplace_resources} marketplace resources and {guided_paths} guided paths from {}",
                self.browse_repository
            ),
        );
    }

    pub fn set_error(&mut self, error: String) {
        self.set_status(StatusSeverity::Error, error);
    }

    pub fn set_rpc_result(&mut self, method: &str, value: &Value) {
        if let Some(messages) = value.get("messages").and_then(Value::as_array) {
            let text = messages
                .iter()
                .filter_map(Value::as_str)
                .collect::<Vec<_>>()
                .join("; ");
            if !text.is_empty() {
                self.set_status(StatusSeverity::Success, text);
                return;
            }
        }
        if method == "validation.run" {
            let exit_code = value.get("exitCode").and_then(Value::as_i64).unwrap_or(1);
            if exit_code == 0 {
                self.set_status(StatusSeverity::Success, "Validation passed");
            } else {
                self.set_status(StatusSeverity::Error, "Validation failed");
            }
        } else {
            self.set_status(StatusSeverity::Success, format!("{method} completed"));
        }
    }

    pub fn set_running(&mut self, title: &str) {
        self.set_status(StatusSeverity::Info, format!("Running {title}…"));
    }

    fn set_status(&mut self, severity: StatusSeverity, text: impl Into<String>) {
        self.status = Some(StatusMessage::new(severity, text));
    }

    fn validate_effect(&self) -> UiEffect {
        UiEffect::Call {
            title: "Validate target".to_string(),
            method: "validation.run".to_string(),
            params: json!({"repoRoot": self.repo_root, "portable": true}),
            validate_after: false,
            reload_after: false,
        }
    }

    pub fn handle_key(&mut self, key: KeyEvent) -> UiEffect {
        self.status = None;
        if self.pending.is_some() {
            return self.handle_pending_key(key);
        }

        if self.show_help {
            return self.handle_help_key(key);
        }

        match self.input_mode {
            InputMode::Search => self.handle_search_key(key),
            InputMode::Command => self.handle_command_key(key),
            InputMode::Normal => self.handle_normal_key(key),
        }
    }

    pub fn selected_offering(&self) -> Option<&Offering> {
        self.filtered
            .get(self.selected)
            .and_then(|index| self.offerings.get(*index))
    }

    pub fn command_suggestions(&self) -> Vec<CommandSuggestion> {
        const COMMANDS: [(&str, FlowKind); 27] = [
            ("browse ", FlowKind::Discover),
            ("preview ", FlowKind::Discover),
            ("search ", FlowKind::Discover),
            ("scope ", FlowKind::Discover),
            ("host ", FlowKind::Discover),
            ("import", FlowKind::AddToRepository),
            ("stage", FlowKind::AddToRepository),
            ("run staged", FlowKind::AddToRepository),
            ("clear staged", FlowKind::AddToRepository),
            ("install all", FlowKind::AddToRepository),
            ("stage all", FlowKind::AddToRepository),
            ("update", FlowKind::Installed),
            ("update all", FlowKind::Installed),
            ("pin ", FlowKind::Installed),
            ("unpin", FlowKind::Installed),
            ("remote list", FlowKind::Installed),
            ("target ", FlowKind::Installed),
            ("validate", FlowKind::Installed),
            ("author", FlowKind::Author),
            ("create skill", FlowKind::Author),
            ("edit", FlowKind::Edit),
            ("open source", FlowKind::Edit),
            ("outputs", FlowKind::Outputs),
            ("materialize", FlowKind::Outputs),
            ("publish", FlowKind::Outputs),
            ("validate hydrated", FlowKind::Outputs),
            ("quit", FlowKind::Discover),
        ];
        let typed = self.command_input.trim();
        let selected = self.selected_offering();
        let selected_installed = selected.and_then(|offering| offering.installed.as_ref());
        let selected_outdated = selected_installed.is_some_and(|installed| installed.outdated);
        let staged_available = !self.staged.is_empty();

        let mut suggestions = COMMANDS
            .into_iter()
            .map(|(command, flow)| {
                let applicable = match command {
                    "import" | "stage" => selected.is_some(),
                    "update" => selected_installed.is_some(),
                    "pin " | "unpin" => selected_installed.is_some(),
                    "run staged" | "clear staged" => staged_available,
                    "update all" => self.installed.iter().any(|plugin| plugin.outdated),
                    _ => true,
                };
                let context_rank = match command {
                    "update" if selected_outdated => 0,
                    "import" | "stage" if selected.is_some() => 1,
                    "run staged" | "clear staged" if staged_available => 2,
                    "pin " | "unpin" if selected_installed.is_some() => 3,
                    "install all" | "update all" | "stage all" => 4,
                    "validate" => 5,
                    _ => 10,
                };
                let prefix_rank = if typed.is_empty() || command.starts_with(typed) {
                    0
                } else {
                    1
                };
                let applicability_rank = if applicable { 0 } else { 1 };
                (
                    prefix_rank,
                    applicability_rank,
                    context_rank,
                    CommandSuggestion {
                        command,
                        applicable,
                        flow,
                    },
                )
            })
            .collect::<Vec<_>>();
        suggestions.sort_by_key(
            |(prefix_rank, applicability_rank, context_rank, suggestion)| {
                (
                    *prefix_rank,
                    *applicability_rank,
                    *context_rank,
                    suggestion.flow.rank(),
                    suggestion.command,
                )
            },
        );
        suggestions
            .into_iter()
            .map(|(_, _, _, suggestion)| suggestion)
            .collect()
    }

    fn handle_pending_key(&mut self, key: KeyEvent) -> UiEffect {
        match key.code {
            KeyCode::Esc | KeyCode::Char('n') => {
                self.pending = None;
                self.set_status(StatusSeverity::Warning, "Cancelled");
                UiEffect::None
            }
            KeyCode::Enter | KeyCode::Char('y') => {
                if let Some(pending) = self.pending.take() {
                    UiEffect::Call {
                        title: pending.title,
                        method: pending.method,
                        params: pending.params,
                        validate_after: pending.validate_after,
                        reload_after: pending.reload_after,
                    }
                } else {
                    UiEffect::None
                }
            }
            _ => UiEffect::None,
        }
    }

    fn handle_help_key(&mut self, key: KeyEvent) -> UiEffect {
        match key.code {
            KeyCode::Esc | KeyCode::Char('?') | KeyCode::Char('q') => {
                self.show_help = false;
                UiEffect::None
            }
            _ => UiEffect::None,
        }
    }

    fn handle_normal_key(&mut self, key: KeyEvent) -> UiEffect {
        match key.code {
            KeyCode::Char('q') => UiEffect::Quit,
            KeyCode::Char('?') => {
                self.show_help = true;
                UiEffect::None
            }
            KeyCode::Char('/') => {
                self.input_mode = InputMode::Search;
                UiEffect::None
            }
            KeyCode::Char(':') => {
                self.input_mode = InputMode::Command;
                self.command_input.clear();
                UiEffect::None
            }
            KeyCode::Tab => {
                self.cycle_focus(1);
                UiEffect::None
            }
            KeyCode::BackTab => {
                self.cycle_focus(-1);
                UiEffect::None
            }
            KeyCode::Down | KeyCode::Char('j') => {
                if self.focus == FocusPane::Staging {
                    self.move_staged_selection(1);
                } else {
                    self.move_selection(1);
                }
                UiEffect::None
            }
            KeyCode::Up | KeyCode::Char('k') => {
                if self.focus == FocusPane::Staging {
                    self.move_staged_selection(-1);
                } else {
                    self.move_selection(-1);
                }
                UiEffect::None
            }
            KeyCode::Enter => {
                if self.focus == FocusPane::Staging {
                    self.confirm_staged_selected()
                } else if self.focus == FocusPane::Sources
                    && self.search_scope == SearchScope::Repository
                {
                    self.preview_search_repository()
                } else if let Some(message) = self.selected_guidance_message() {
                    self.set_status(StatusSeverity::Info, message);
                    UiEffect::None
                } else {
                    self.set_status(
                        StatusSeverity::Info,
                        selected_summary(self.selected_offering()),
                    );
                    UiEffect::None
                }
            }
            KeyCode::Char('r') => self.preview_search_repository(),
            KeyCode::Char('i') => self.stage_selected_action(),
            KeyCode::Char('a') => self.stage_install_all(),
            KeyCode::Char('u') => {
                self.pending_installed_action("marketplace.update", "Update selected plugin", None)
            }
            KeyCode::Char('U') => {
                self.pending = Some(self.update_all_action());
                UiEffect::None
            }
            KeyCode::Char('v') => self.validate_effect(),
            KeyCode::Char('x') | KeyCode::Delete => {
                if self.focus == FocusPane::Staging {
                    self.remove_staged_selected();
                }
                UiEffect::None
            }
            _ => UiEffect::None,
        }
    }

    fn handle_search_key(&mut self, key: KeyEvent) -> UiEffect {
        match key.code {
            KeyCode::Esc => {
                self.input_mode = InputMode::Normal;
                UiEffect::None
            }
            KeyCode::Enter => {
                self.input_mode = InputMode::Normal;
                if self.search_scope == SearchScope::Repository
                    && !self.search_query.trim().is_empty()
                {
                    self.preview_search_repository()
                } else {
                    UiEffect::None
                }
            }
            KeyCode::Tab => {
                self.search_scope = self.search_scope.next();
                self.apply_filter();
                UiEffect::None
            }
            KeyCode::BackTab => {
                self.search_scope = self.search_scope.previous();
                self.apply_filter();
                UiEffect::None
            }
            KeyCode::Backspace => {
                self.search_query.pop();
                self.apply_filter();
                UiEffect::None
            }
            KeyCode::Char(ch) => {
                self.search_query.push(ch);
                self.apply_filter();
                UiEffect::None
            }
            _ => UiEffect::None,
        }
    }

    fn handle_command_key(&mut self, key: KeyEvent) -> UiEffect {
        match key.code {
            KeyCode::Esc => {
                self.input_mode = InputMode::Normal;
                UiEffect::None
            }
            KeyCode::Backspace => {
                self.command_input.pop();
                UiEffect::None
            }
            KeyCode::Enter => {
                self.input_mode = InputMode::Normal;
                self.parse_command()
            }
            KeyCode::Char(ch) => {
                self.command_input.push(ch);
                UiEffect::None
            }
            _ => UiEffect::None,
        }
    }

    fn parse_command(&mut self) -> UiEffect {
        let command = self.command_input.trim().to_string();
        if command.is_empty() {
            return UiEffect::None;
        }
        if command == "quit" || command == "q" {
            return UiEffect::Quit;
        }
        if command == "validate" {
            return self.validate_effect();
        }
        if let Some(host) = command.strip_prefix("host ") {
            match GitHost::parse(host) {
                Ok(host) => {
                    self.default_git_host = host;
                    self.set_status(
                        StatusSeverity::Success,
                        format!("Default Git host set to {}", self.default_git_host.as_str()),
                    );
                }
                Err(error) => self.set_status(StatusSeverity::Error, error),
            }
            return UiEffect::None;
        }
        if let Some(target) = command.strip_prefix("target ") {
            let target = target.trim();
            if target.is_empty() {
                self.set_status(StatusSeverity::Error, "target requires a repository path");
                return UiEffect::None;
            }
            self.repo_root = target.to_string();
            self.set_status(
                StatusSeverity::Success,
                format!("Install target set to {target}"),
            );
            return UiEffect::LoadCatalog;
        }
        if let Some(scope) = command.strip_prefix("scope ") {
            match SearchScope::parse(scope) {
                Some(scope) => {
                    self.search_scope = scope;
                    self.apply_filter();
                    self.set_status(
                        StatusSeverity::Success,
                        format!("Search scope set to {}", scope.label()),
                    );
                }
                None => self.set_status(
                    StatusSeverity::Error,
                    format!("Unknown search scope: {}", scope.trim()),
                ),
            }
            return UiEffect::None;
        }
        if let Some(search) = command.strip_prefix("search ") {
            let mut parts = search.trim().splitn(2, char::is_whitespace);
            let first = parts.next().unwrap_or_default();
            if let Some(scope) = SearchScope::parse(first) {
                self.search_scope = scope;
                self.search_query = parts.next().unwrap_or_default().trim().to_string();
            } else {
                self.search_query = search.trim().to_string();
            }
            self.apply_filter();
            return UiEffect::None;
        }
        if command == "author" || command == "create skill" {
            return self.show_guided_action(GuidedActionKind::AuthorResource);
        }
        if command == "edit" || command == "open source" {
            return self.show_guided_action(GuidedActionKind::EditResource);
        }
        if matches!(
            command.as_str(),
            "outputs" | "materialize" | "publish" | "validate hydrated"
        ) {
            return self.show_guided_action(GuidedActionKind::ProjectOutputs);
        }
        if command == "remote list" {
            return UiEffect::Call {
                title: "List marketplace remotes".to_string(),
                method: "marketplace.remotes.list".to_string(),
                params: json!({"repoRoot": self.repo_root}),
                validate_after: false,
                reload_after: false,
            };
        }
        if let Some(repository) = command
            .strip_prefix("browse ")
            .or_else(|| command.strip_prefix("preview "))
        {
            let repository = repository.trim();
            return self.preview_repository(repository);
        }
        if command == "stage" {
            return self.stage_selected_action();
        }
        if command == "stage all" {
            return self.stage_install_all();
        }
        if command == "run staged" {
            return self.confirm_staged_selected();
        }
        if command == "clear staged" {
            self.staged.clear();
            self.staged_selected = 0;
            self.set_status(StatusSeverity::Success, "Staging cleared");
            return UiEffect::None;
        }
        if command == "install all" {
            self.pending = Some(self.install_all_action());
            return UiEffect::None;
        }
        if command == "import" || command == "install" {
            return self.pending_import_selected();
        }
        if command == "update all" {
            self.pending = Some(self.update_all_action());
            return UiEffect::None;
        }
        if command == "update" {
            return self.pending_installed_action(
                "marketplace.update",
                "Update selected plugin",
                None,
            );
        }
        if command == "unpin" {
            return self.pending_installed_action(
                "marketplace.unpin",
                "Unpin selected plugin",
                None,
            );
        }
        if let Some(version) = command.strip_prefix("pin ") {
            return self.pending_installed_action(
                "marketplace.pin",
                "Pin selected plugin",
                Some(version.trim()),
            );
        }
        self.set_status(StatusSeverity::Error, format!("Unknown command: {command}"));
        UiEffect::None
    }

    fn pending_import_selected(&mut self) -> UiEffect {
        if let Some(message) = self.selected_guidance_message() {
            self.set_status(StatusSeverity::Info, message);
            return UiEffect::None;
        }
        match self.selected_install_or_update_action() {
            Ok(action) => self.pending = Some(action),
            Err(error) => self.set_status(StatusSeverity::Error, error),
        }
        UiEffect::None
    }

    fn selected_install_or_update_action(&self) -> Result<PendingAction, String> {
        let offering = self
            .selected_offering()
            .ok_or_else(|| "No offering selected".to_string())?;
        if offering.installed.is_some() {
            return self.installed_action_for(
                offering,
                "marketplace.update",
                "Update selected plugin",
                None,
            );
        }
        match offering.kind {
            OfferingKind::Plugin => {
                let plugin = offering.name.clone();
                let import_target = format!(
                    "{}/{}",
                    self.browse_repository.trim_end_matches('/'),
                    plugin
                );
                Ok(PendingAction {
                    title: format!("Install plugin {import_target}"),
                    method: "marketplace.import".to_string(),
                    params: json!({"repoRoot": self.repo_root, "target": import_target, "ref": self.ref_name}),
                    validate_after: true,
                    reload_after: true,
                    confirmation: ConfirmationKind::Inline,
                    provenance: ActionProvenance::PluginImport {
                        plugin,
                        repository: self.browse_repository.clone(),
                        target: self.repo_root.clone(),
                        ref_name: self.ref_name.clone(),
                    },
                })
            }
            OfferingKind::Skill
            | OfferingKind::Agent
            | OfferingKind::Hook
            | OfferingKind::Instruction => {
                let kind = offering.kind.label().to_string();
                let name = offering.name.clone();
                Ok(PendingAction {
                    title: format!("Import {kind} primitive {name}"),
                    method: "marketplace.primitive.import".to_string(),
                    params: json!({
                        "repoRoot": self.repo_root,
                        "repository": self.browse_repository,
                        "kind": kind,
                        "name": name,
                        "ref": self.ref_name
                    }),
                    validate_after: true,
                    reload_after: true,
                    confirmation: ConfirmationKind::Inline,
                    provenance: ActionProvenance::PrimitiveImport {
                        kind,
                        name,
                        repository: self.browse_repository.clone(),
                        target: self.repo_root.clone(),
                        ref_name: self.ref_name.clone(),
                    },
                })
            }
            OfferingKind::Guided(_) => Err(offering
                .guidance_message()
                .unwrap_or("This guided path is not a repository mutation.")
                .to_string()),
        }
    }

    fn install_all_action(&self) -> PendingAction {
        PendingAction {
            title: format!("Install every plugin from {}", self.browse_repository),
            method: "marketplace.install".to_string(),
            params: json!({"repoRoot": self.repo_root, "repository": self.browse_repository, "ref": self.ref_name}),
            validate_after: true,
            reload_after: true,
            confirmation: ConfirmationKind::Modal,
            provenance: ActionProvenance::MarketplaceInstall {
                repository: self.browse_repository.clone(),
                target: self.repo_root.clone(),
                ref_name: self.ref_name.clone(),
            },
        }
    }

    fn update_all_action(&self) -> PendingAction {
        PendingAction {
            title: "Update all imported plugins".to_string(),
            method: "marketplace.updateAll".to_string(),
            params: json!({"repoRoot": self.repo_root}),
            validate_after: true,
            reload_after: true,
            confirmation: ConfirmationKind::Modal,
            provenance: ActionProvenance::UpdateAll {
                target: self.repo_root.clone(),
            },
        }
    }

    fn stage_selected_action(&mut self) -> UiEffect {
        if let Some(message) = self.selected_guidance_message() {
            self.set_status(StatusSeverity::Info, message);
            self.focus = FocusPane::Details;
            return UiEffect::None;
        }
        match self.selected_install_or_update_action() {
            Ok(action) => {
                self.set_status(StatusSeverity::Success, format!("Staged {}", action.title));
                self.staged.push(action);
                self.staged_selected = self.staged.len().saturating_sub(1);
                self.focus = FocusPane::Staging;
            }
            Err(error) => self.set_status(StatusSeverity::Error, error),
        }
        UiEffect::None
    }

    fn selected_guidance_message(&self) -> Option<&'static str> {
        self.selected_offering()
            .and_then(Offering::guidance_message)
    }

    fn show_guided_action(&mut self, kind: GuidedActionKind) -> UiEffect {
        if let Some(index) = self
            .offerings
            .iter()
            .position(|offering| offering.kind == OfferingKind::Guided(kind))
        {
            self.apply_filter();
            if let Some(filtered_index) = self.filtered.iter().position(|value| *value == index) {
                self.selected = filtered_index;
            }
        }
        self.focus = FocusPane::Details;
        self.set_status(StatusSeverity::Info, kind.guidance());
        UiEffect::None
    }

    fn stage_install_all(&mut self) -> UiEffect {
        let action = self.install_all_action();
        self.set_status(StatusSeverity::Success, format!("Staged {}", action.title));
        self.staged.push(action);
        self.staged_selected = self.staged.len().saturating_sub(1);
        self.focus = FocusPane::Staging;
        UiEffect::None
    }

    fn confirm_staged_selected(&mut self) -> UiEffect {
        if self.staged.is_empty() {
            self.set_status(StatusSeverity::Warning, "No staged install action");
            return UiEffect::None;
        }
        let modal_confirmation = self.staged.len() > 1;
        let index = self.staged_selected.min(self.staged.len() - 1);
        let mut action = self.staged.remove(index);
        if modal_confirmation {
            action.confirmation = ConfirmationKind::Modal;
        }
        self.pending = Some(action);
        if self.staged_selected >= self.staged.len() {
            self.staged_selected = self.staged.len().saturating_sub(1);
        }
        UiEffect::None
    }

    fn remove_staged_selected(&mut self) {
        if self.staged.is_empty() {
            self.set_status(StatusSeverity::Warning, "No staged install action");
            return;
        }
        let index = self.staged_selected.min(self.staged.len() - 1);
        let removed = self.staged.remove(index);
        if self.staged_selected >= self.staged.len() {
            self.staged_selected = self.staged.len().saturating_sub(1);
        }
        self.set_status(
            StatusSeverity::Warning,
            format!("Removed {}", removed.title),
        );
    }

    fn pending_installed_action(
        &mut self,
        method: &str,
        title: &str,
        version: Option<&str>,
    ) -> UiEffect {
        match self
            .selected_offering()
            .ok_or_else(|| "No offering selected".to_string())
            .and_then(|offering| self.installed_action_for(offering, method, title, version))
        {
            Ok(action) => self.pending = Some(action),
            Err(error) => self.set_status(StatusSeverity::Error, error),
        }
        UiEffect::None
    }

    fn installed_action_for(
        &self,
        offering: &Offering,
        method: &str,
        title: &str,
        version: Option<&str>,
    ) -> Result<PendingAction, String> {
        let installed = offering
            .installed
            .as_ref()
            .ok_or_else(|| "Selected offering is not installed".to_string())?;
        if version.is_some_and(str::is_empty) {
            return Err("pin requires a version".to_string());
        }
        let mut params = json!({"repoRoot": self.repo_root, "plugin": installed.name});
        if let Some(version) = version {
            params["version"] = json!(version);
        }
        let provenance = match method {
            "marketplace.update" => ActionProvenance::InstalledUpdate {
                plugin: installed.name.clone(),
                source: offering
                    .repository
                    .clone()
                    .or_else(|| installed.marketplace.clone()),
                target: self.repo_root.clone(),
            },
            "marketplace.pin" => ActionProvenance::InstalledPin {
                plugin: installed.name.clone(),
                version: version.unwrap_or_default().to_string(),
                target: self.repo_root.clone(),
            },
            "marketplace.unpin" => ActionProvenance::InstalledUnpin {
                plugin: installed.name.clone(),
                target: self.repo_root.clone(),
            },
            _ => ActionProvenance::Other {
                action: title.to_string(),
                target: self.repo_root.clone(),
            },
        };
        Ok(PendingAction {
            title: format!("{title}: {}", installed.name),
            method: method.to_string(),
            params,
            validate_after: true,
            reload_after: true,
            confirmation: ConfirmationKind::Inline,
            provenance,
        })
    }

    fn preview_search_repository(&mut self) -> UiEffect {
        let query = self.search_query.trim().to_string();
        let repository = if query.is_empty() {
            self.browse_repository.clone()
        } else {
            query
        };
        self.preview_repository(&repository)
    }

    fn preview_repository(&mut self, repository: &str) -> UiEffect {
        match self.default_git_host.repository_ref(repository) {
            Ok(repository) => {
                self.browse_repository = repository.clone();
                self.set_status(StatusSeverity::Info, format!("Previewing {repository}"));
                UiEffect::LoadCatalog
            }
            Err(error) => {
                self.set_status(StatusSeverity::Error, error);
                UiEffect::None
            }
        }
    }

    fn apply_filter(&mut self) {
        let query = self.search_query.trim();
        let mut ranked = self
            .offerings
            .iter()
            .enumerate()
            .filter_map(|(index, offering)| {
                search_score_scoped(query, self.search_scope, offering).map(|score| (index, score))
            })
            .collect::<Vec<_>>();
        ranked.sort_by(|left, right| right.1.cmp(&left.1).then_with(|| left.0.cmp(&right.0)));
        self.filtered = ranked.iter().map(|(index, _)| *index).collect();
        if self.selected >= self.filtered.len() {
            self.selected = self.filtered.len().saturating_sub(1);
        }
    }

    fn move_selection(&mut self, delta: isize) {
        if self.filtered.is_empty() {
            self.selected = 0;
            return;
        }
        let next = self.selected as isize + delta;
        self.selected = next.clamp(0, self.filtered.len() as isize - 1) as usize;
    }

    fn move_staged_selection(&mut self, delta: isize) {
        if self.staged.is_empty() {
            self.staged_selected = 0;
            return;
        }
        let next = self.staged_selected as isize + delta;
        self.staged_selected = next.clamp(0, self.staged.len() as isize - 1) as usize;
    }

    fn cycle_focus(&mut self, delta: isize) {
        let panes = [
            FocusPane::Sources,
            FocusPane::Offerings,
            FocusPane::Details,
            FocusPane::Staging,
        ];
        let current = panes
            .iter()
            .position(|pane| *pane == self.focus)
            .unwrap_or(0) as isize;
        let next = (current + delta).rem_euclid(panes.len() as isize) as usize;
        self.focus = panes[next];
    }
}

fn offerings_from_catalog(
    plugins: Vec<PluginOffering>,
    primitives: StandalonePrimitives,
    installed: &[InstalledPlugin],
    repository: &str,
) -> Vec<Offering> {
    let installed_by_name = installed
        .iter()
        .cloned()
        .map(|plugin| (plugin.name.clone(), plugin))
        .collect::<BTreeMap<_, _>>();
    let mut seen_plugins = BTreeSet::new();
    let mut offerings = Vec::new();
    for plugin in plugins {
        seen_plugins.insert(plugin.name.clone());
        offerings.push(Offering {
            kind: OfferingKind::Plugin,
            installed: installed_by_name.get(&plugin.name).cloned(),
            repository: Some(repository.to_string()),
            source: OfferingSource::Marketplace,
            name: plugin.name,
            description: plugin.description,
            tags: plugin.tags,
        });
    }
    for primitive in primitives.skills {
        offerings.push(primitive_offering(OfferingKind::Skill, primitive));
    }
    for primitive in primitives.agents {
        offerings.push(primitive_offering(OfferingKind::Agent, primitive));
    }
    for primitive in primitives.hooks {
        offerings.push(primitive_offering(OfferingKind::Hook, primitive));
    }
    for primitive in primitives.instructions {
        offerings.push(primitive_offering(OfferingKind::Instruction, primitive));
    }
    for plugin in installed {
        if seen_plugins.contains(&plugin.name) {
            continue;
        }
        offerings.push(Offering {
            kind: OfferingKind::Plugin,
            name: plugin.name.clone(),
            description: plugin.description.clone(),
            tags: plugin.tags.clone(),
            installed: Some(plugin.clone()),
            repository: plugin.marketplace.clone(),
            source: OfferingSource::Installed,
        });
    }
    offerings.extend(guided_offerings());
    offerings
}

fn primitive_offering(kind: OfferingKind, primitive: PrimitiveOffering) -> Offering {
    Offering {
        kind,
        name: primitive.name,
        description: primitive.description,
        tags: Vec::new(),
        installed: None,
        repository: None,
        source: OfferingSource::Marketplace,
    }
}

fn guided_offerings() -> Vec<Offering> {
    vec![
        guided_offering(
            GuidedActionKind::DiscoverSource,
            "Browse marketplace sources",
            "Read repository, local, or remote marketplaces without changing target state.",
            &["guided", "read", "discover", "browse"],
        ),
        guided_offering(
            GuidedActionKind::AuthorResource,
            "Create reusable resource",
            "Author reusable skills, plugins, hooks, agents, and instructions in the owning marketplace source.",
            &["guided", "author", "create", "personal-source"],
        ),
        guided_offering(
            GuidedActionKind::EditResource,
            "Edit owned resource",
            "Edit resources where they are authored instead of changing generated provider output.",
            &["guided", "edit", "source"],
        ),
        guided_offering(
            GuidedActionKind::ProjectOutputs,
            "Generate provider output",
            "Materialize or publish Codex and GitHub provider payloads from source and install state.",
            &["guided", "outputs", "materialize", "publish"],
        ),
    ]
}

fn guided_offering(
    kind: GuidedActionKind,
    name: &'static str,
    description: &'static str,
    tags: &[&'static str],
) -> Offering {
    Offering {
        kind: OfferingKind::Guided(kind),
        name: name.to_string(),
        description: Some(description.to_string()),
        tags: tags.iter().map(|tag| (*tag).to_string()).collect(),
        installed: None,
        repository: Some(kind.source_scope().label().to_string()),
        source: OfferingSource::Guidance,
    }
}

pub fn search_score(query: &str, offering: &Offering) -> Option<i64> {
    search_score_scoped(query, SearchScope::All, offering)
}

pub fn search_score_scoped(query: &str, scope: SearchScope, offering: &Offering) -> Option<i64> {
    if query.is_empty() {
        return scope_accepts_empty(scope, offering).then_some(0);
    }
    let haystack = searchable_text(scope, offering);
    if haystack.is_empty() {
        return None;
    }
    let mut score = 0;
    for term in query.to_lowercase().split_whitespace() {
        if haystack.contains(term) {
            score += 100 - term.len() as i64;
        } else if haystack
            .split_whitespace()
            .any(|token| is_subsequence(term, token))
        {
            score += 20;
        } else {
            return None;
        }
    }
    Some(score)
}

fn scope_accepts_empty(scope: SearchScope, offering: &Offering) -> bool {
    match scope {
        SearchScope::All | SearchScope::Repository | SearchScope::User => true,
        SearchScope::Plugin => offering.kind == OfferingKind::Plugin,
        SearchScope::Primitive => offering.kind.is_primitive(),
        SearchScope::Installed => {
            offering.installed.is_some() || offering.source == OfferingSource::Installed
        }
    }
}

fn searchable_text(scope: SearchScope, offering: &Offering) -> String {
    let repository = offering.repository.clone().unwrap_or_default();
    match scope {
        SearchScope::All => all_searchable_text(offering, &repository),
        SearchScope::Repository => repository.to_lowercase(),
        SearchScope::User => repository_owner(&repository)
            .unwrap_or_default()
            .to_lowercase(),
        SearchScope::Plugin => {
            if offering.kind == OfferingKind::Plugin {
                all_searchable_text(offering, &repository)
            } else {
                String::new()
            }
        }
        SearchScope::Primitive => {
            if offering.kind.is_primitive() {
                all_searchable_text(offering, &repository)
            } else {
                String::new()
            }
        }
        SearchScope::Installed => {
            if offering.installed.is_some() || offering.source == OfferingSource::Installed {
                all_searchable_text(offering, &repository)
            } else {
                String::new()
            }
        }
    }
}

fn all_searchable_text(offering: &Offering, repository: &str) -> String {
    let mut values = vec![
        offering.name.clone(),
        offering.kind.label().to_string(),
        offering.flow_kind().label().to_string(),
        offering.source_scope().label().to_string(),
        offering.target_scope().label().to_string(),
        offering.operation_kind().label().to_string(),
        offering.description.clone().unwrap_or_default(),
        offering.tags.join(" "),
        repository.to_string(),
        repository_owner(repository).unwrap_or_default(),
        offering.source.label().to_string(),
    ];
    if let Some(installed) = &offering.installed {
        values.push("installed".to_string());
        values.push(installed.marketplace.clone().unwrap_or_default());
        if installed.outdated {
            values.push("outdated update".to_string());
        }
        if installed.locked {
            values.push("locked pinned".to_string());
        }
        if let Some(version) = &installed.version {
            values.push(version.clone());
        }
    }
    values.join(" ").to_lowercase()
}

fn repository_owner(repository: &str) -> Option<String> {
    let trimmed = repository
        .trim()
        .trim_end_matches('/')
        .trim_end_matches(".git");
    if trimmed.is_empty() || looks_like_local_path(trimmed) {
        return None;
    }
    let path = if is_owner_repo(trimmed) {
        trimmed.to_string()
    } else if trimmed.starts_with("git@") {
        trimmed
            .split_once(':')
            .map(|(_, path)| path.to_string())
            .unwrap_or_default()
    } else if trimmed.starts_with("https://") || trimmed.starts_with("http://") {
        trimmed
            .split_once("://")
            .and_then(|(_, rest)| rest.split_once('/').map(|(_, path)| path.to_string()))
            .unwrap_or_default()
    } else {
        let parts = trimmed.split('/').collect::<Vec<_>>();
        if parts.len() >= 3 && parts[0].contains('.') {
            parts[1..].join("/")
        } else {
            trimmed.to_string()
        }
    };
    path.split('/').next().map(str::to_string)
}

fn looks_like_local_path(value: &str) -> bool {
    value.starts_with('.') || value.starts_with('~') || value.starts_with('/')
}

fn looks_like_git_url(value: &str) -> bool {
    value.starts_with("https://")
        || value.starts_with("http://")
        || value.starts_with("ssh://")
        || value.starts_with("git@")
        || value.ends_with(".git")
}

fn is_owner_repo(value: &str) -> bool {
    let mut parts = value.split('/');
    let owner = parts.next();
    let repo = parts.next();
    if parts.next().is_some() {
        return false;
    }
    owner.is_some_and(is_repository_segment) && repo.is_some_and(is_repository_segment)
}

fn is_repository_segment(value: &str) -> bool {
    !value.is_empty()
        && value
            .chars()
            .all(|ch| ch.is_ascii_alphanumeric() || matches!(ch, '_' | '.' | '-'))
}

fn is_subsequence(needle: &str, haystack: &str) -> bool {
    let mut chars = needle.chars();
    let mut current = chars.next();
    for ch in haystack.chars() {
        if Some(ch) == current {
            current = chars.next();
            if current.is_none() {
                return true;
            }
        }
    }
    false
}

fn selected_summary(selected: Option<&Offering>) -> String {
    selected
        .map(|offering| {
            if let Some(message) = offering.guidance_message() {
                return message.to_string();
            }
            let version = offering
                .installed
                .as_ref()
                .and_then(|installed| installed.version.as_deref())
                .unwrap_or("not installed");
            format!("{} ({}) - {version}", offering.name, offering.kind.label())
        })
        .unwrap_or_else(|| "No offering selected".to_string())
}

fn provenance_path(offering: &Offering, app: &App) -> String {
    let source = match offering.source_scope() {
        SourceScope::CurrentRepository => "current repository".to_string(),
        SourceScope::PersonalSource => offering
            .repository
            .clone()
            .filter(|value| looks_like_local_path(value))
            .unwrap_or_else(|| "/Users/amichne/code/slopsentral".to_string()),
        SourceScope::RemoteSource => offering
            .repository
            .clone()
            .unwrap_or_else(|| app.browse_repository.clone()),
        SourceScope::ResolvedCache => "~/.local/share/intelligence/marketplace-assets".to_string(),
        SourceScope::InstalledState => ".intelligence/adaptable.marketplace.json".to_string(),
    };
    format!(
        "{} -> {} -> {} {}",
        source,
        offering.operation_kind().label(),
        offering.target_scope().label(),
        match offering.target_scope() {
            TargetScope::Repository => app.repo_root.as_str(),
            TargetScope::PersonalSource => "/Users/amichne/code/slopsentral",
            TargetScope::ProviderOutput => ".agents/plugins or .github/plugin",
        }
    )
}

#[derive(Debug, Clone, PartialEq, Eq)]
enum ModeBarMode {
    Normal,
    Search(SearchScope),
    Command,
    Confirm,
    Help,
}

impl ModeBarMode {
    fn from_app(app: &App) -> Self {
        if app.pending.is_some() {
            Self::Confirm
        } else if app.show_help {
            Self::Help
        } else {
            match app.input_mode {
                InputMode::Normal => Self::Normal,
                InputMode::Search => Self::Search(app.search_scope),
                InputMode::Command => Self::Command,
            }
        }
    }

    fn label(&self) -> String {
        match self {
            Self::Normal => "NORMAL".to_string(),
            Self::Search(scope) => format!("SEARCH {}", scope.label()),
            Self::Command => "COMMAND".to_string(),
            Self::Confirm => "CONFIRM".to_string(),
            Self::Help => "HELP".to_string(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct ModeBarState {
    mode: ModeBarMode,
    severity: Option<StatusSeverity>,
    row1: String,
    row2: String,
    row3: String,
    suggestions: Vec<CommandSuggestion>,
}

impl ModeBarState {
    fn from_app(app: &App) -> Self {
        let mode = ModeBarMode::from_app(app);
        let mode_label = mode.label();
        match mode.clone() {
            ModeBarMode::Normal => {
                let status = app
                    .status
                    .as_ref()
                    .map(|status| format!("{}: {}", status.severity.label(), status.text))
                    .unwrap_or_else(|| "ready".to_string());
                let suggestions = app.command_suggestions();
                Self {
                    mode,
                    severity: app.status.as_ref().map(|status| status.severity),
                    row1: format!("{mode_label} {status}"),
                    row2: suggestions_plain(&suggestions),
                    row3: normal_footer(app.focus),
                    suggestions,
                }
            }
            ModeBarMode::Search(scope) => {
                let prompt = format!("/{} {}", scope.label(), app.search_query);
                Self {
                    mode,
                    severity: None,
                    row1: format!("{mode_label} {prompt}"),
                    row2: search_detail(app, scope),
                    row3: "tab scope  shift-tab previous  enter apply  esc normal".to_string(),
                    suggestions: Vec::new(),
                }
            }
            ModeBarMode::Command => {
                let suggestions = app.command_suggestions();
                Self {
                    mode,
                    severity: None,
                    row1: format!("{mode_label} :{}", app.command_input),
                    row2: suggestions_plain(&suggestions),
                    row3: "enter run  esc normal  ? help".to_string(),
                    suggestions,
                }
            }
            ModeBarMode::Confirm => {
                let pending = app.pending.as_ref();
                Self {
                    mode,
                    severity: None,
                    row1: format!(
                        "{mode_label} {}",
                        pending
                            .map(|action| action.title.as_str())
                            .unwrap_or("pending action")
                    ),
                    row2: pending
                        .map(|action| action.provenance.summary())
                        .unwrap_or_else(|| "pending action".to_string()),
                    row3: "y confirm  enter confirm  esc cancel  n cancel".to_string(),
                    suggestions: Vec::new(),
                }
            }
            ModeBarMode::Help => Self {
                mode,
                severity: None,
                row1: format!("{mode_label} shortcuts"),
                row2: "shortcut reference is open".to_string(),
                row3: "? close  esc close  q close".to_string(),
                suggestions: Vec::new(),
            },
        }
    }
}

fn normal_footer(focus: FocusPane) -> String {
    match focus {
        FocusPane::Sources => "tab resources  r preview source  / search  : palette  ? help",
        FocusPane::Offerings => "tab details  enter explain  i stage/add  a install all  / search",
        FocusPane::Details => "tab actions  enter explain  / search  : palette  ? help",
        FocusPane::Staging => "tab context  enter confirm  x remove  : palette  ? help",
    }
    .to_string()
}

fn search_detail(app: &App, scope: SearchScope) -> String {
    let mut detail = format!(
        "SEARCH {} · {}/{}",
        scope.label(),
        app.filtered.len(),
        app.offerings.len()
    );
    if scope == SearchScope::Repository {
        let repository = app.search_query.trim();
        let preview = if repository.is_empty() {
            app.browse_repository.as_str()
        } else {
            repository
        };
        detail.push_str(&format!(" · enter previews {preview}"));
    }
    detail
}

fn suggestions_plain(suggestions: &[CommandSuggestion]) -> String {
    format!("commands: {}", grouped_suggestions_plain(suggestions))
}

fn ellipsize(value: &str, width: u16) -> String {
    let width = usize::from(width);
    if value.chars().count() <= width {
        return value.to_string();
    }
    if width == 0 {
        return String::new();
    }
    if width == 1 {
        return "…".to_string();
    }
    let mut truncated = value.chars().take(width - 1).collect::<String>();
    truncated.push('…');
    truncated
}

fn fit_suggestions_line(
    prefix: &str,
    suggestions: &[CommandSuggestion],
    width: u16,
    theme: UiTheme,
) -> Line<'static> {
    if suggestions.is_empty() {
        return Line::from(ellipsize(prefix, width)).style(theme.muted());
    }
    let width = usize::from(width);
    let mut spans = vec![Span::styled(prefix.to_string(), theme.muted())];
    let mut used = prefix.chars().count();
    for group in grouped_suggestions(suggestions) {
        let group_label = format!("{}:", group.flow.table_label());
        let group_separator = if used == prefix.chars().count() {
            " "
        } else {
            " | "
        };
        let group_needed = group_separator.chars().count() + group_label.chars().count();
        if used + group_needed > width {
            break;
        }
        spans.push(Span::styled(group_separator.to_string(), theme.muted()));
        spans.push(Span::styled(group_label, theme.muted()));
        used += group_needed;
        for suggestion in group.suggestions {
            let label = suggestion_label(suggestion);
            let needed = 1 + label.chars().count();
            if used + needed > width {
                return Line::from(spans);
            }
            spans.push(Span::styled(" ".to_string(), theme.muted()));
            let style = if suggestion.applicable {
                theme.fg(ColorSlot::AccentPrimary)
            } else {
                theme.muted()
            };
            spans.push(Span::styled(label, style));
            used += needed;
        }
    }
    Line::from(spans)
}

struct SuggestionGroup<'a> {
    flow: FlowKind,
    suggestions: Vec<&'a CommandSuggestion>,
}

fn grouped_suggestions(suggestions: &[CommandSuggestion]) -> Vec<SuggestionGroup<'_>> {
    let mut groups = Vec::<SuggestionGroup<'_>>::new();
    for suggestion in suggestions {
        if let Some(group) = groups
            .iter_mut()
            .find(|group| group.flow == suggestion.flow)
        {
            group.suggestions.push(suggestion);
        } else {
            groups.push(SuggestionGroup {
                flow: suggestion.flow,
                suggestions: vec![suggestion],
            });
        }
    }
    groups
}

fn grouped_suggestions_plain(suggestions: &[CommandSuggestion]) -> String {
    grouped_suggestions(suggestions)
        .into_iter()
        .map(|group| {
            let values = group
                .suggestions
                .into_iter()
                .map(suggestion_label)
                .collect::<Vec<_>>()
                .join(" ");
            format!("{}: {values}", group.flow.table_label())
        })
        .collect::<Vec<_>>()
        .join(" | ")
}

fn suggestion_label(suggestion: &CommandSuggestion) -> String {
    if suggestion.applicable {
        suggestion.command.to_string()
    } else {
        format!("({})", suggestion.command.trim_end())
    }
}

pub fn render(frame: &mut ratatui::Frame, app: &App) {
    let theme = UiTheme::current();
    let [title_area, main_area, command_area] = Layout::vertical([
        Constraint::Length(1),
        Constraint::Min(10),
        Constraint::Length(3),
    ])
    .areas(frame.area());
    let [left_area, center_area, right_area] = Layout::horizontal([
        Constraint::Length(30),
        Constraint::Percentage(48),
        Constraint::Percentage(34),
    ])
    .areas(main_area);
    let [details_area, staging_area] =
        Layout::vertical([Constraint::Percentage(62), Constraint::Percentage(38)])
            .areas(right_area);

    render_title(frame, title_area, app, theme);
    render_sources(frame, left_area, app, theme);
    render_offerings(frame, center_area, app, theme);
    render_details(frame, details_area, app, theme);
    render_staging(frame, staging_area, app, theme);
    render_mode_bar(frame, command_area, app, theme);
    if app.pending.is_none() && app.show_help {
        render_shortcuts(frame, theme);
    }
    if let Some(pending) = app
        .pending
        .as_ref()
        .filter(|action| action.confirmation == ConfirmationKind::Modal)
    {
        render_pending(frame, pending, theme);
    }
}

fn render_title(frame: &mut ratatui::Frame, area: Rect, app: &App, theme: UiTheme) {
    let mut spans = vec![Span::styled(" intelligence ", theme.emphasis())];
    if let Some(marketplace) = &app.marketplace {
        spans.push(Span::styled("│ ", theme.muted()));
        spans.push(Span::styled(
            marketplace.name.clone(),
            theme.fg(ColorSlot::Plugin).add_modifier(Modifier::BOLD),
        ));
        spans.push(Span::styled(" │ provider ", theme.muted()));
        spans.push(Span::styled(
            marketplace.provider.clone(),
            theme.fg(ColorSlot::Catalog),
        ));
    }
    spans.push(Span::styled(" │ target ", theme.muted()));
    spans.push(Span::styled(
        app.repo_root.clone(),
        theme.fg(ColorSlot::Default),
    ));
    spans.push(Span::styled(
        " │ / search │ tab panes │ ? shortcuts ",
        theme.muted(),
    ));
    frame.render_widget(Line::from(spans), area);
}

fn render_sources(frame: &mut ratatui::Frame, area: Rect, app: &App, theme: UiTheme) {
    let installed = app
        .installed
        .iter()
        .filter(|plugin| plugin.imported)
        .count();
    let outdated = app
        .installed
        .iter()
        .filter(|plugin| plugin.outdated)
        .count();
    let plugin_count = app
        .offerings
        .iter()
        .filter(|offering| offering.kind == OfferingKind::Plugin)
        .count();
    let primitive_count = app
        .offerings
        .iter()
        .filter(|offering| offering.kind.is_primitive())
        .count();
    let guided_count = app
        .offerings
        .iter()
        .filter(|offering| offering.kind.is_guided())
        .count();
    let active_flow = app
        .selected_offering()
        .map(Offering::flow_kind)
        .unwrap_or(FlowKind::Discover);
    let source_kind = app
        .selected_offering()
        .map(|offering| offering.source_scope().label())
        .unwrap_or(SourceScope::RemoteSource.label());
    let entrypoint = app
        .marketplace
        .as_ref()
        .map(|marketplace| marketplace.entrypoint.as_str())
        .unwrap_or("not loaded");
    let items = vec![
        field_item("Target", app.repo_root.as_str(), theme),
        field_item("Intent", ".intelligence/adaptable.marketplace.json", theme),
        field_item("Lock", ".intelligence/marketplace-lock.json", theme),
        field_item("Browse", app.browse_repository.as_str(), theme),
        field_item("Entrypoint", entrypoint, theme),
        field_item("Source", source_kind, theme),
        field_item("Flow", active_flow.label(), theme),
        field_item("Search", app.search_scope.label(), theme),
        ListItem::new(Line::from(vec![
            Span::styled("Local", theme.muted()),
            Span::from(": "),
            Span::styled(
                format!("{installed} installed"),
                theme.fg(ColorSlot::Success),
            ),
            Span::styled(", ", theme.muted()),
            Span::styled(format!("{outdated} updates"), theme.fg(ColorSlot::Warning)),
        ])),
        ListItem::new(""),
        ListItem::new(Line::from(vec![
            Span::styled("Plugins", theme.emphasis()),
            Span::from(": "),
            Span::styled("●", OfferingKind::Plugin.style(theme)),
            Span::from(format!(" {plugin_count}")),
        ])),
        ListItem::new(Line::from(vec![
            Span::styled("Primitives", theme.emphasis()),
            Span::from(": "),
            Span::styled("◆", theme.fg(ColorSlot::Skill)),
            Span::from(format!(" {primitive_count}")),
        ])),
        ListItem::new(Line::from(vec![
            Span::styled("Guides", theme.emphasis()),
            Span::from(": "),
            Span::styled("?", theme.fg(ColorSlot::Info)),
            Span::from(format!(" {guided_count}")),
        ])),
        ListItem::new(""),
        field_item(
            "Cache",
            "~/.local/share/intelligence/marketplace-assets",
            theme,
        ),
        field_item("Personal", "/Users/amichne/code/slopsentral", theme),
    ];
    let block = focused_block("Context", app.focus == FocusPane::Sources, theme);
    frame.render_widget(List::new(items).block(block), area);
}

fn render_offerings(frame: &mut ratatui::Frame, area: Rect, app: &App, theme: UiTheme) {
    let rows = app.filtered.iter().filter_map(|index| {
        let offering = app.offerings.get(*index)?;
        let state = OfferingInstallState::from_offering(offering);
        let version = offering
            .installed
            .as_ref()
            .and_then(|installed| installed.version.clone())
            .unwrap_or_default();
        Some(Row::new(vec![
            Cell::from(Line::from(vec![
                Span::styled(offering.kind.marker(), offering.kind.style(theme)),
                Span::styled(offering.kind.table_label(), offering.kind.style(theme)),
            ])),
            Cell::from(Span::styled(
                offering.name.clone(),
                theme.fg(ColorSlot::Default),
            )),
            Cell::from(Span::styled(
                offering.flow_kind().table_label(),
                theme.fg(ColorSlot::Info),
            )),
            Cell::from(Line::from(vec![
                Span::styled(state.marker(), state.style(theme)),
                Span::styled(state.table_label(), state.style(theme)),
            ])),
            Cell::from(Span::styled(version, theme.muted())),
        ]))
    });
    let table = Table::new(
        rows,
        [
            Constraint::Length(8),
            Constraint::Min(10),
            Constraint::Length(7),
            Constraint::Length(8),
            Constraint::Length(8),
        ],
    )
    .header(Row::new(vec!["Type", "Name", "Flow", "State", "Version"]).style(theme.emphasis()))
    .block(focused_block(
        "Resources",
        app.focus == FocusPane::Offerings,
        theme,
    ))
    .row_highlight_style(theme.selected())
    .highlight_symbol("▸ ");
    let mut state = TableState::default().with_selected(Some(app.selected));
    frame.render_stateful_widget(table, area, &mut state);
}

fn render_details(frame: &mut ratatui::Frame, area: Rect, app: &App, theme: UiTheme) {
    let selected = app.selected_offering();
    let mut lines = Vec::new();
    if let Some(offering) = selected {
        let state = OfferingInstallState::from_offering(offering);
        lines.push(Line::from(vec![
            Span::styled(offering.kind.marker(), offering.kind.style(theme)),
            Span::from(" "),
            Span::styled(&offering.name, theme.emphasis()),
        ]));
        lines.push(Line::from(vec![
            Span::styled(offering.kind.label(), offering.kind.style(theme)),
            Span::styled("  │  ", theme.muted()),
            Span::styled(offering.source.marker(), offering.source.style(theme)),
            Span::from(" "),
            Span::styled(offering.source.label(), offering.source.style(theme)),
            Span::styled("  │  ", theme.muted()),
            Span::styled(state.marker(), state.style(theme)),
            Span::from(" "),
            Span::styled(state.label(), state.style(theme)),
        ]));
        lines.push(field_line("flow", offering.flow_kind().label(), theme));
        lines.push(field_line(
            "source scope",
            offering.source_scope().label(),
            theme,
        ));
        lines.push(field_line(
            "operation",
            offering.operation_kind().label(),
            theme,
        ));
        lines.push(field_line(
            "target scope",
            offering.target_scope().label(),
            theme,
        ));
        lines.push(field_line("path", provenance_path(offering, app), theme));
        if let Some(repository) = &offering.repository {
            lines.push(field_line("source", repository.as_str(), theme));
        }
        if let Some(description) = &offering.description {
            lines.push(Line::from(""));
            lines.push(Line::from(description.clone()).style(theme.fg(ColorSlot::Default)));
        }
        if let Some(guidance) = offering.guidance_message() {
            lines.push(Line::from(""));
            lines.push(Line::from(guidance).style(theme.fg(ColorSlot::Info)));
        }
        if !offering.tags.is_empty() {
            lines.push(Line::from(""));
            lines.push(tag_line(&offering.tags, theme));
        }
        if let Some(installed) = &offering.installed {
            lines.push(Line::from(""));
            lines.push(field_line(
                "installed",
                installed.version.as_deref().unwrap_or("unknown"),
                theme,
            ));
            if let Some(current) = &installed.current_version {
                lines.push(field_line("remote", current.as_str(), theme));
            }
            lines.push(field_line("locked", installed.locked.to_string(), theme));
        }
        lines.push(Line::from(""));
        lines.push(field_line("target", app.repo_root.as_str(), theme));
    } else {
        lines.push(Line::from("No offering selected").style(theme.muted()));
    }
    frame.render_widget(
        Paragraph::new(lines)
            .block(focused_block(
                "Details",
                app.focus == FocusPane::Details,
                theme,
            ))
            .wrap(Wrap { trim: true }),
        area,
    );
}

fn render_staging(frame: &mut ratatui::Frame, area: Rect, app: &App, theme: UiTheme) {
    let items = if app.staged.is_empty() {
        vec![ListItem::new(
            Line::from("No staged actions").style(theme.muted()),
        )]
    } else {
        app.staged
            .iter()
            .map(|action| {
                ListItem::new(Line::from(vec![
                    Span::styled("▶", theme.fg(ColorSlot::AccentPrimary)),
                    Span::from(" "),
                    Span::styled(action.provenance.summary(), theme.fg(ColorSlot::Default)),
                ]))
            })
            .collect()
    };
    let mut state =
        ListState::default().with_selected((!app.staged.is_empty()).then_some(app.staged_selected));
    frame.render_stateful_widget(
        List::new(items)
            .block(focused_block(
                "Actions",
                app.focus == FocusPane::Staging,
                theme,
            ))
            .highlight_symbol("▸ ")
            .highlight_style(theme.selected()),
        area,
        &mut state,
    );
}

fn render_mode_bar(frame: &mut ratatui::Frame, area: Rect, app: &App, theme: UiTheme) {
    let mode_bar = ModeBarState::from_app(app);
    let row1_style = mode_bar
        .severity
        .map(|severity| severity.style(theme))
        .unwrap_or_else(|| theme.emphasis());
    let row1 = Line::styled(ellipsize(&mode_bar.row1, area.width), row1_style);
    let row2 = if mode_bar.suggestions.is_empty() {
        Line::from(ellipsize(&mode_bar.row2, area.width)).style(theme.fg(ColorSlot::Default))
    } else {
        fit_suggestions_line("commands:", &mode_bar.suggestions, area.width, theme)
    };
    let row3 = shortcut_line(ellipsize(&mode_bar.row3, area.width), theme);
    frame.render_widget(Paragraph::new(vec![row1, row2, row3]), area);
}

fn render_shortcuts(frame: &mut ratatui::Frame, theme: UiTheme) {
    let area = centered_rect(72, 58, frame.area());
    frame.render_widget(Clear, area);
    let body = vec![
        Line::from("Keyboard shortcuts").style(theme.emphasis()),
        Line::from(""),
        Line::from("Scopes and flows").style(theme.emphasis()),
        Line::from("target = repository state; source = marketplace, personal source, installed state, cache, or provider output"),
        Line::from("flows: discover, add to repo, installed, author, edit, outputs"),
        Line::from(""),
        shortcut_line("/ search current catalog and local installed plugins", theme),
        shortcut_line("tab / shift-tab move panes; in search, cycle all/repository/user/plugin/primitive/installed", theme),
        shortcut_line("r preview remote from repository search, using the configured default Git host", theme),
        shortcut_line("i stage selected install/update; a stage install all", theme),
        shortcut_line("enter opens details or confirms the selected staged action", theme),
        shortcut_line("v validate the selected install target", theme),
        shortcut_line(": palette groups discover, add, installed, author, edit, and outputs commands", theme),
        shortcut_line("x removes a staged action when the staging pane is focused", theme),
        shortcut_line("esc or ? closes this panel", theme),
    ];
    frame.render_widget(
        Paragraph::new(body)
            .wrap(Wrap { trim: true })
            .block(overlay_block("Shortcuts", theme)),
        area,
    );
}

fn render_pending(frame: &mut ratatui::Frame, pending: &PendingAction, theme: UiTheme) {
    let area = centered_rect(70, 35, frame.area());
    frame.render_widget(Clear, area);
    let inner_width = area.width.saturating_sub(2);
    let body = vec![
        Line::from(pending.title.clone()).style(theme.emphasis()),
        Line::from(""),
        field_line(
            "method",
            ellipsize(&pending.method, inner_width.saturating_sub(8)),
            theme,
        ),
        field_line(
            "params",
            ellipsize(&pending.params.to_string(), inner_width.saturating_sub(8)),
            theme,
        ),
        Line::from(""),
        shortcut_line("Y or Enter confirms. Esc or N cancels.", theme),
    ];
    frame.render_widget(
        Paragraph::new(body).block(overlay_block("Confirm", theme)),
        area,
    );
}

fn field_item<'a>(
    label: &'static str,
    value: impl Into<std::borrow::Cow<'a, str>>,
    theme: UiTheme,
) -> ListItem<'a> {
    ListItem::new(field_line(label, value, theme))
}

fn field_line<'a>(
    label: &'static str,
    value: impl Into<std::borrow::Cow<'a, str>>,
    theme: UiTheme,
) -> Line<'a> {
    Line::from(vec![
        Span::styled(label, theme.muted()),
        Span::from(": "),
        Span::styled(value.into(), theme.fg(ColorSlot::Default)),
    ])
}

fn tag_line<'a>(tags: &'a [String], theme: UiTheme) -> Line<'a> {
    let mut spans = vec![Span::styled("tags", theme.muted()), Span::from(": ")];
    for (index, tag) in tags.iter().enumerate() {
        if index > 0 {
            spans.push(Span::styled(", ", theme.muted()));
        }
        spans.push(Span::styled(
            tag.as_str(),
            theme.fg(ColorSlot::AccentSecondary),
        ));
    }
    Line::from(spans)
}

fn shortcut_line<'a>(value: impl Into<std::borrow::Cow<'a, str>>, theme: UiTheme) -> Line<'a> {
    let value = value.into();
    let mut spans = Vec::new();
    let mut chars = value.chars().peekable();
    while let Some(ch) = chars.next() {
        if ch.is_ascii_alphanumeric() || matches!(ch, '/' | '?' | ':' | '-') {
            let mut token = String::from(ch);
            while let Some(next) = chars.peek().copied() {
                if !(next.is_ascii_alphanumeric() || matches!(next, '/' | '?' | ':' | '-')) {
                    break;
                }
                token.push(next);
                chars.next();
            }
            let style = if is_shortcut_token(&token) {
                theme
                    .fg(ColorSlot::AccentPrimary)
                    .add_modifier(Modifier::BOLD)
            } else {
                theme.fg(ColorSlot::Default)
            };
            spans.push(Span::styled(token, style));
        } else {
            spans.push(Span::from(ch.to_string()));
        }
    }
    Line::from(spans)
}

fn is_shortcut_token(token: &str) -> bool {
    matches!(
        token,
        "/" | "?"
            | ":"
            | "tab"
            | "shift-tab"
            | "enter"
            | "esc"
            | "q"
            | "r"
            | "i"
            | "a"
            | "v"
            | "x"
            | "y"
            | "n"
            | "Y"
            | "Enter"
            | "Esc"
            | "N"
    )
}

fn focused_block(title: &'static str, focused: bool, theme: UiTheme) -> Block<'static> {
    let title = if focused {
        format!("▸ {title}")
    } else {
        format!("  {title}")
    };
    Block::new()
        .borders(Borders::ALL)
        .border_set(if focused {
            symbols::border::THICK
        } else {
            symbols::border::PLAIN
        })
        .title(title)
        .title_style(theme.panel_title(focused))
        .style(theme.bg(ColorSlot::Surface))
        .border_style(theme.panel_border(focused))
}

fn overlay_block(title: &'static str, theme: UiTheme) -> Block<'static> {
    Block::new()
        .borders(Borders::ALL)
        .border_set(symbols::border::DOUBLE)
        .title(format!(" ◆ {title} "))
        .title_style(
            theme
                .fg(ColorSlot::AccentSecondary)
                .add_modifier(Modifier::BOLD),
        )
        .style(theme.bg(ColorSlot::Overlay))
        .border_style(theme.fg(ColorSlot::AccentSecondary))
}

fn centered_rect(percent_x: u16, percent_y: u16, area: Rect) -> Rect {
    let vertical = Layout::vertical([
        Constraint::Percentage((100 - percent_y) / 2),
        Constraint::Percentage(percent_y),
        Constraint::Percentage((100 - percent_y) / 2),
    ]);
    let [_, middle, _] = vertical.areas(area);
    let horizontal = Layout::horizontal([
        Constraint::Percentage((100 - percent_x) / 2),
        Constraint::Percentage(percent_x),
        Constraint::Percentage((100 - percent_x) / 2),
    ]);
    let [_, center, _] = horizontal.areas(middle);
    center
}

pub fn unique_tags(offerings: &[Offering]) -> Vec<String> {
    offerings
        .iter()
        .flat_map(|offering| offering.tags.iter().cloned())
        .collect::<BTreeSet<_>>()
        .into_iter()
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crossterm::event::{KeyEvent, KeyModifiers};
    use ratatui::{backend::TestBackend, Terminal};

    fn test_config() -> Config {
        Config {
            repo_root: ".".to_string(),
            ref_name: None,
            intelligence_bin: "intelligence".to_string(),
            browse_repository: "amichne/slopsentral".to_string(),
            default_git_host: GitHost::github(),
        }
    }

    fn installed_plugin(name: &str) -> InstalledPlugin {
        InstalledPlugin {
            name: name.to_string(),
            version: Some("1.0.0".to_string()),
            current_version: Some("1.1.0".to_string()),
            imported: true,
            locked: false,
            outdated: true,
            marketplace: Some("amichne/slopsentral".to_string()),
            description: Some("Installed workflow".to_string()),
            tags: vec!["installed".to_string()],
        }
    }

    fn plugin_offering(name: &str, installed: Option<InstalledPlugin>) -> Offering {
        Offering {
            kind: OfferingKind::Plugin,
            name: name.to_string(),
            description: Some(format!("{name} description")),
            tags: vec!["workflow".to_string()],
            installed,
            repository: Some("amichne/slopsentral".to_string()),
            source: OfferingSource::Marketplace,
        }
    }

    fn skill_offering(name: &str) -> Offering {
        Offering {
            kind: OfferingKind::Skill,
            name: name.to_string(),
            description: Some(format!("{name} skill")),
            tags: Vec::new(),
            installed: None,
            repository: None,
            source: OfferingSource::Marketplace,
        }
    }

    fn select_offerings(app: &mut App, offerings: Vec<Offering>) {
        app.offerings = offerings;
        app.apply_filter();
        app.selected = 0;
    }

    #[test]
    fn color_support_detection_respects_no_color_and_terminal_capability() {
        assert_eq!(
            ColorSupport::Monochrome,
            ColorSupport::detect(true, Some("truecolor"), Some("xterm-256color"))
        );
        assert_eq!(
            ColorSupport::TrueColor,
            ColorSupport::detect(false, Some("24bit"), Some("xterm-256color"))
        );
        assert_eq!(
            ColorSupport::Ansi256,
            ColorSupport::detect(false, None, Some("screen-256color"))
        );
        assert_eq!(
            ColorSupport::Ansi16,
            ColorSupport::detect(false, None, None)
        );
    }

    #[test]
    fn render_uses_non_color_markers_for_focus_kind_source_and_state() {
        let mut app = App::new(test_config());
        select_offerings(
            &mut app,
            vec![
                plugin_offering("review-stack", None),
                skill_offering("tui-design"),
                plugin_offering(
                    "kotlin-engineering",
                    Some(installed_plugin("kotlin-engineering")),
                ),
            ],
        );
        app.selected = 0;

        let lines = rendered_lines(&app, 120, 30);
        let screen = screen_text(&lines);

        assert!(screen.contains("▸ Resources"));
        assert!(screen.contains("●plugin"));
        assert!(screen.contains("◆skill"));
        assert!(screen.contains("add"));
        assert!(screen.contains("□avail"));
        assert!(screen.contains("▲update"));
    }

    #[test]
    fn mode_bar_derives_confirm_before_help_and_input_mode() {
        let mut app = App::new(test_config());
        app.input_mode = InputMode::Search;
        app.show_help = true;
        app.pending = Some(app.install_all_action());

        let mode_bar = ModeBarState::from_app(&app);

        assert_eq!(ModeBarMode::Confirm, mode_bar.mode);
        assert!(mode_bar.row1.starts_with("CONFIRM "));
    }

    #[test]
    fn mode_bar_labels_cover_normal_search_command_and_help() {
        let mut app = App::new(test_config());
        assert!(ModeBarState::from_app(&app).row1.starts_with("NORMAL "));

        app.input_mode = InputMode::Search;
        app.search_scope = SearchScope::Repository;
        assert!(ModeBarState::from_app(&app)
            .row1
            .starts_with("SEARCH repository "));

        app.input_mode = InputMode::Command;
        assert!(ModeBarState::from_app(&app).row1.starts_with("COMMAND "));

        app.show_help = true;
        assert!(ModeBarState::from_app(&app).row1.starts_with("HELP "));
    }

    #[test]
    fn pending_confirmation_keys_are_explicit_and_other_keys_are_inert() {
        let mut app = App::new(test_config());
        app.pending = Some(app.install_all_action());

        let inert = app.handle_key(KeyEvent::new(KeyCode::Char('x'), KeyModifiers::NONE));
        assert!(matches!(inert, UiEffect::None));
        assert!(app.pending.is_some());

        let confirmed = app.handle_key(KeyEvent::new(KeyCode::Char('y'), KeyModifiers::NONE));
        assert!(matches!(
            confirmed,
            UiEffect::Call { method, .. } if method == "marketplace.install"
        ));
        assert!(app.pending.is_none());

        app.pending = Some(app.install_all_action());
        let cancelled = app.handle_key(KeyEvent::new(KeyCode::Char('n'), KeyModifiers::NONE));
        assert!(matches!(cancelled, UiEffect::None));
        assert!(app.pending.is_none());
        assert_eq!(
            Some(StatusSeverity::Warning),
            app.status.as_ref().map(|status| status.severity)
        );
    }

    #[test]
    fn multi_action_staged_confirmation_uses_modal() {
        let mut app = App::new(test_config());
        select_offerings(&mut app, vec![plugin_offering("review-stack", None)]);
        let first = app
            .selected_install_or_update_action()
            .expect("first staged action");
        select_offerings(&mut app, vec![plugin_offering("release-stack", None)]);
        let second = app
            .selected_install_or_update_action()
            .expect("second staged action");
        app.staged = vec![first, second];

        let effect = app.confirm_staged_selected();

        assert!(matches!(effect, UiEffect::None));
        assert_eq!(
            Some(ConfirmationKind::Modal),
            app.pending.as_ref().map(|action| action.confirmation)
        );
    }

    #[test]
    fn confirmation_provenance_describes_plugin_primitive_and_update_targets() {
        let mut app = App::new(test_config());
        select_offerings(&mut app, vec![plugin_offering("review-stack", None)]);
        let plugin = app
            .selected_install_or_update_action()
            .expect("plugin action");
        assert_eq!(ConfirmationKind::Inline, plugin.confirmation);
        assert_eq!(
            "install plugin review-stack from amichne/slopsentral → .",
            plugin.provenance.summary()
        );

        app.ref_name = Some("main".to_string());
        select_offerings(&mut app, vec![skill_offering("tui-design")]);
        let primitive = app
            .selected_install_or_update_action()
            .expect("primitive action");
        assert_eq!(
            "import skill tui-design from amichne/slopsentral@main → .",
            primitive.provenance.summary()
        );

        app.repo_root = "/tmp/target".to_string();
        select_offerings(
            &mut app,
            vec![plugin_offering(
                "kotlin-engineering",
                Some(installed_plugin("kotlin-engineering")),
            )],
        );
        let update = app
            .selected_install_or_update_action()
            .expect("update action");
        assert_eq!(
            "update plugin kotlin-engineering from amichne/slopsentral → /tmp/target",
            update.provenance.summary()
        );
    }

    #[test]
    fn search_detail_reports_match_count_scope_and_repository_enter_effect() {
        let mut app = App::new(test_config());
        select_offerings(
            &mut app,
            vec![
                Offering {
                    repository: Some("amichne/tools".to_string()),
                    ..plugin_offering("one", None)
                },
                Offering {
                    repository: Some("acme/internal".to_string()),
                    ..plugin_offering("two", None)
                },
                Offering {
                    repository: Some("amichne/tools-extra".to_string()),
                    ..plugin_offering("three", None)
                },
            ],
        );
        app.input_mode = InputMode::Search;
        app.search_scope = SearchScope::Repository;
        app.search_query = "amichne/tools".to_string();
        app.apply_filter();

        let mode_bar = ModeBarState::from_app(&app);

        assert_eq!(
            "SEARCH repository · 2/3 · enter previews amichne/tools",
            mode_bar.row2
        );
    }

    #[test]
    fn status_messages_are_typed_and_clear_on_next_keypress() {
        let mut app = App::new(test_config());
        app.set_error("network failed".to_string());

        assert_eq!(
            Some(StatusSeverity::Error),
            app.status.as_ref().map(|status| status.severity)
        );
        assert_eq!(
            Some("network failed"),
            app.status.as_ref().map(|status| status.text.as_str())
        );

        let effect = app.handle_key(KeyEvent::new(KeyCode::Char('z'), KeyModifiers::NONE));

        assert!(matches!(effect, UiEffect::None));
        assert!(app.status.is_none());
    }

    #[test]
    fn command_suggestions_rank_context_and_keep_unavailable_commands() {
        let mut app = App::new(test_config());
        let suggestions = app.command_suggestions();
        assert!(suggestions
            .iter()
            .any(|suggestion| suggestion.command == "import" && !suggestion.applicable));
        assert!(suggestions.iter().any(
            |suggestion| suggestion.command == "author" && suggestion.flow == FlowKind::Author
        ));
        assert!(suggestions
            .iter()
            .any(|suggestion| suggestion.command == "materialize"
                && suggestion.flow == FlowKind::Outputs));
        assert!(
            position_of_command(&suggestions, "validate")
                < position_of_command(&suggestions, "import")
        );

        select_offerings(&mut app, vec![plugin_offering("review-stack", None)]);
        let suggestions = app.command_suggestions();
        assert_eq!("import", suggestions[0].command);
        assert!(suggestions[0].applicable);

        select_offerings(
            &mut app,
            vec![plugin_offering(
                "kotlin-engineering",
                Some(installed_plugin("kotlin-engineering")),
            )],
        );
        let suggestions = app.command_suggestions();
        assert_eq!("update", suggestions[0].command);
    }

    #[test]
    fn guided_resources_classify_flow_scope_and_operation_without_rpc_actions() {
        let mut app = App::new(test_config());
        let offerings = offerings_from_catalog(
            Vec::new(),
            StandalonePrimitives::default(),
            &[],
            "amichne/slopsentral",
        );
        select_offerings(&mut app, offerings);
        let author = app
            .offerings
            .iter()
            .find(|offering| {
                offering.kind == OfferingKind::Guided(GuidedActionKind::AuthorResource)
            })
            .expect("author guide");
        assert_eq!(FlowKind::Author, author.flow_kind());
        assert_eq!(SourceScope::PersonalSource, author.source_scope());
        assert_eq!(TargetScope::PersonalSource, author.target_scope());
        assert_eq!(OperationKind::Create, author.operation_kind());

        app.command_input = "author".to_string();
        app.input_mode = InputMode::Command;
        let effect = app.handle_key(KeyEvent::new(KeyCode::Enter, KeyModifiers::NONE));

        assert!(matches!(effect, UiEffect::None));
        assert_eq!(FocusPane::Details, app.focus);
        assert_eq!(
            Some(StatusSeverity::Info),
            app.status.as_ref().map(|status| status.severity)
        );
        assert!(app
            .status
            .as_ref()
            .is_some_and(|status| status.text.contains("/Users/amichne/code/slopsentral")));
    }

    fn position_of_command(suggestions: &[CommandSuggestion], command: &str) -> usize {
        suggestions
            .iter()
            .position(|suggestion| suggestion.command == command)
            .unwrap_or(usize::MAX)
    }

    fn rendered_lines(app: &App, width: u16, height: u16) -> Vec<String> {
        let mut terminal = Terminal::new(TestBackend::new(width, height)).expect("test terminal");
        terminal
            .draw(|frame| render(frame, app))
            .expect("render frame");
        let buffer = terminal.backend().buffer();
        (0..buffer.area.height)
            .map(|y| {
                let mut line = String::new();
                for x in 0..buffer.area.width {
                    if let Some(cell) = buffer.cell((x, y)) {
                        line.push_str(cell.symbol());
                    }
                }
                line
            })
            .collect()
    }

    fn screen_text(lines: &[String]) -> String {
        lines.join("\n")
    }

    #[test]
    fn render_mode_bar_normal_mode_is_three_borderless_rows() {
        let app = App::new(test_config());

        let lines = rendered_lines(&app, 80, 24);

        assert!(lines[21].starts_with("NORMAL INFO: Loading marketplace catalog..."));
        assert!(lines[22].starts_with("commands:"));
        assert!(lines[23].contains("/ search"));
        assert!(!lines[21..24].iter().any(|line| line.contains("Command")));
        assert!(!lines[21..24].iter().any(|line| line.contains('┌')));
        assert!(!lines[21..24].iter().any(|line| line.contains('│')));
    }

    #[test]
    fn render_context_resources_and_actions_use_flow_language() {
        let mut app = App::new(test_config());
        let catalog = Catalog {
            marketplace: MarketplaceSummary {
                name: "slopsentral".to_string(),
                provider: "source".to_string(),
                repository: "amichne/slopsentral".to_string(),
                entrypoint: "source/adaptable.marketplace.json".to_string(),
                description: None,
            },
            plugins: vec![PluginOffering {
                name: "kotlin-engineering".to_string(),
                description: Some("Typed Kotlin workflow".to_string()),
                category: None,
                tags: vec!["kotlin".to_string()],
            }],
            standalone_primitives: StandalonePrimitives::default(),
            installed: Vec::new(),
        };
        app.set_catalog(catalog);
        app.staged = vec![app.install_all_action()];

        let lines = rendered_lines(&app, 80, 24);
        let screen = screen_text(&lines);

        assert!(screen.contains("Context"));
        assert!(screen.contains("Resources"));
        assert!(screen.contains("Actions"));
        assert!(screen.contains("Flow"));
        assert!(screen.contains("add"));
        assert!(screen.contains("install all"));
    }

    #[test]
    fn render_guided_output_details_explain_source_operation_and_target() {
        let mut app = App::new(test_config());
        select_offerings(
            &mut app,
            vec![guided_offering(
                GuidedActionKind::ProjectOutputs,
                "Generate provider output",
                "Materialize or publish provider payloads.",
                &["outputs"],
            )],
        );
        app.focus = FocusPane::Details;

        let lines = rendered_lines(&app, 100, 30);
        let screen = screen_text(&lines);

        assert!(screen.contains("flow: outputs"));
        assert!(screen.contains("source scope: current repository"));
        assert!(screen.contains("operation: project"));
        assert!(screen.contains("target scope: provider output"));
        assert!(app.selected_offering().is_some_and(|offering| offering
            .guidance_message()
            .is_some_and(|message| message.contains("marketplace materialize"))));
    }

    #[test]
    fn render_mode_bar_search_mode_shows_match_count() {
        let mut app = App::new(test_config());
        select_offerings(
            &mut app,
            vec![
                Offering {
                    repository: Some("amichne/tooling".to_string()),
                    ..plugin_offering("one", None)
                },
                Offering {
                    repository: Some("acme/internal".to_string()),
                    ..plugin_offering("two", None)
                },
            ],
        );
        app.input_mode = InputMode::Search;
        app.search_scope = SearchScope::Repository;
        app.search_query = "amichne/tooling".to_string();
        app.apply_filter();

        let lines = rendered_lines(&app, 80, 24);

        assert!(lines[21].starts_with("SEARCH repository /repository amichne/tooling"));
        assert!(lines[22].contains("SEARCH repository · 1/2"));
        assert!(lines[22].contains("enter previews amichne/tooling"));
        assert!(lines[23].contains("tab scope"));
    }

    #[test]
    fn render_mode_bar_command_mode_shows_ranked_suggestions() {
        let mut app = App::new(test_config());
        app.input_mode = InputMode::Command;
        app.command_input = "up".to_string();
        select_offerings(
            &mut app,
            vec![plugin_offering(
                "kotlin-engineering",
                Some(installed_plugin("kotlin-engineering")),
            )],
        );

        let lines = rendered_lines(&app, 80, 24);

        assert!(lines[21].starts_with("COMMAND :up"));
        assert!(lines[22].contains("update"));
        assert!(lines[23].contains("enter run"));
    }

    #[test]
    fn render_inline_confirm_uses_mode_bar_without_raw_rpc_modal() {
        let mut app = App::new(test_config());
        select_offerings(&mut app, vec![plugin_offering("review-stack", None)]);
        app.pending = Some(
            app.selected_install_or_update_action()
                .expect("pending action"),
        );
        app.show_help = true;

        let lines = rendered_lines(&app, 80, 24);
        let screen = screen_text(&lines);

        assert!(lines[21].starts_with("CONFIRM Install plugin"));
        assert!(lines[22].contains("install plugin review-stack from amichne/slopsentral → ."));
        assert_eq!(
            "y confirm  enter confirm  esc cancel  n cancel",
            lines[23].trim_end()
        );
        assert!(!screen.contains("Keyboard shortcuts"));
        assert!(!screen.contains("method:"));
        assert!(!screen.contains("params:"));
    }

    #[test]
    fn render_batch_confirm_retains_modal_with_raw_rpc_details() {
        let mut app = App::new(test_config());
        app.pending = Some(app.install_all_action());

        let lines = rendered_lines(&app, 80, 24);
        let screen = screen_text(&lines);

        assert!(lines[21].starts_with("CONFIRM Install every plugin"));
        assert!(screen.contains("method: marketplace.install"));
        assert!(screen.contains("params:"));
        assert!(screen.contains("Y or Enter confirms. Esc or N cancels."));
    }

    #[test]
    fn render_long_status_keeps_mode_label_and_ellipsizes_message() {
        let mut app = App::new(test_config());
        app.set_status(
            StatusSeverity::Error,
            "this status message is intentionally much longer than a narrow terminal can show",
        );

        let lines = rendered_lines(&app, 40, 24);

        assert!(lines[21].starts_with("NORMAL "));
        assert!(lines[21].contains('…'));
        assert!(lines[22].starts_with("commands:"));
    }

    #[test]
    fn render_severity_labels_remain_legible_without_color() {
        let mut app = App::new(test_config());
        app.set_error("network failed".to_string());

        let lines = rendered_lines(&app, 80, 24);

        assert!(lines[21].starts_with("NORMAL ERROR: network failed"));
    }

    #[test]
    fn search_matches_name_tags_and_installed_state() {
        let offering = Offering {
            kind: OfferingKind::Plugin,
            name: "kotlin-engineering".to_string(),
            description: Some("Typed Kotlin workflow".to_string()),
            tags: vec!["gradle".to_string(), "review".to_string()],
            repository: Some("amichne/slopsentral".to_string()),
            source: OfferingSource::Marketplace,
            installed: Some(InstalledPlugin {
                name: "kotlin-engineering".to_string(),
                version: Some("0.1.0".to_string()),
                current_version: Some("0.2.0".to_string()),
                imported: true,
                locked: true,
                outdated: true,
                marketplace: Some("slopsentral".to_string()),
                description: None,
                tags: Vec::new(),
            }),
        };

        assert!(search_score("kotlin", &offering).is_some());
        assert!(search_score("gradle", &offering).is_some());
        assert!(search_score("outdated", &offering).is_some());
        assert!(search_score_scoped("amichne", SearchScope::User, &offering).is_some());
        assert!(search_score_scoped("slopsentral", SearchScope::Repository, &offering).is_some());
        assert!(search_score("missing", &offering).is_none());
    }

    #[test]
    fn tab_cycles_focus_without_rpc_effects() {
        let mut app = App::new(test_config());

        assert_eq!(FocusPane::Offerings, app.focus);
        let effect = app.handle_key(KeyEvent::new(KeyCode::Tab, KeyModifiers::NONE));

        assert!(matches!(effect, UiEffect::None));
        assert_eq!(FocusPane::Details, app.focus);
    }

    #[test]
    fn command_palette_builds_update_all_confirmation() {
        let mut app = App::new(Config {
            repo_root: "/tmp/repo".to_string(),
            ..test_config()
        });
        app.input_mode = InputMode::Command;
        app.command_input = "update all".to_string();

        let effect = app.handle_key(KeyEvent::new(KeyCode::Enter, KeyModifiers::NONE));

        assert!(matches!(effect, UiEffect::None));
        let pending = app.pending.expect("pending action");
        assert_eq!("marketplace.updateAll", pending.method);
        assert!(pending.validate_after);
    }

    #[test]
    fn search_tab_cycles_scope_without_leaving_search() {
        let mut app = App::new(test_config());
        app.input_mode = InputMode::Search;

        let effect = app.handle_key(KeyEvent::new(KeyCode::Tab, KeyModifiers::NONE));

        assert!(matches!(effect, UiEffect::None));
        assert_eq!(InputMode::Search, app.input_mode);
        assert_eq!(SearchScope::Repository, app.search_scope);
    }

    #[test]
    fn repository_search_enter_previews_with_enterprise_host() {
        let mut app = App::new(Config {
            default_git_host: GitHost::parse("github.enterprise.example").expect("host"),
            ..test_config()
        });
        app.input_mode = InputMode::Search;
        app.search_scope = SearchScope::Repository;
        app.search_query = "acme/tools".to_string();

        let effect = app.handle_key(KeyEvent::new(KeyCode::Enter, KeyModifiers::NONE));

        assert!(matches!(effect, UiEffect::LoadCatalog));
        assert_eq!(
            "https://github.enterprise.example/acme/tools.git",
            app.browse_repository
        );
    }

    #[test]
    fn question_mark_opens_shortcuts_overlay() {
        let mut app = App::new(test_config());

        let effect = app.handle_key(KeyEvent::new(KeyCode::Char('?'), KeyModifiers::NONE));

        assert!(matches!(effect, UiEffect::None));
        assert!(app.show_help);
    }

    #[test]
    fn selected_install_can_be_staged_and_confirmed() {
        let mut app = App::new(test_config());
        app.offerings = vec![Offering {
            kind: OfferingKind::Plugin,
            name: "review-stack".to_string(),
            description: None,
            tags: Vec::new(),
            installed: None,
            repository: Some("amichne/slopsentral".to_string()),
            source: OfferingSource::Marketplace,
        }];
        app.filtered = vec![0];

        let stage = app.handle_key(KeyEvent::new(KeyCode::Char('i'), KeyModifiers::NONE));
        let confirm = app.handle_key(KeyEvent::new(KeyCode::Enter, KeyModifiers::NONE));

        assert!(matches!(stage, UiEffect::None));
        assert!(matches!(confirm, UiEffect::None));
        assert_eq!(FocusPane::Staging, app.focus);
        assert!(app.staged.is_empty());
        let pending = app.pending.expect("pending action");
        assert_eq!("marketplace.import", pending.method);
    }

    #[test]
    fn installed_only_plugins_are_searchable_locally() {
        let installed = InstalledPlugin {
            name: "private-tools".to_string(),
            version: Some("1.0.0".to_string()),
            current_version: None,
            imported: true,
            locked: false,
            outdated: false,
            marketplace: Some("enterprise-tools".to_string()),
            description: Some("Internal automation".to_string()),
            tags: vec!["internal".to_string()],
        };

        let offerings = offerings_from_catalog(
            Vec::new(),
            StandalonePrimitives::default(),
            &[installed],
            "amichne/slopsentral",
        );

        assert_eq!(5, offerings.len());
        assert_eq!(OfferingSource::Installed, offerings[0].source);
        assert_eq!(
            OfferingKind::Guided(GuidedActionKind::DiscoverSource),
            offerings[1].kind
        );
        assert!(
            search_score_scoped("enterprise", SearchScope::Repository, &offerings[0]).is_some()
        );
        assert!(search_score_scoped("private", SearchScope::Installed, &offerings[0]).is_some());
    }
}
