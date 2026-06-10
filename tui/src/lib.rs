use std::collections::{BTreeMap, BTreeSet};
use std::io::{BufRead, BufReader, Write};
use std::process::{Child, ChildStdin, Command, Stdio};

use crossterm::event::{KeyCode, KeyEvent};
use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Color, Modifier, Style, Stylize};
use ratatui::text::{Line, Span};
use ratatui::widgets::{
    Block, Borders, Clear, List, ListItem, ListState, Paragraph, Row, Table, TableState, Wrap,
};
use serde::Deserialize;
use serde_json::{json, Value};

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
}

impl OfferingKind {
    fn label(&self) -> &'static str {
        match self {
            Self::Plugin => "plugin",
            Self::Skill => "skill",
            Self::Agent => "agent",
            Self::Hook => "hook",
            Self::Instruction => "instruction",
        }
    }

    fn is_primitive(&self) -> bool {
        !matches!(self, Self::Plugin)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OfferingSource {
    Marketplace,
    Installed,
}

impl OfferingSource {
    fn label(self) -> &'static str {
        match self {
            Self::Marketplace => "catalog",
            Self::Installed => "local",
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

#[derive(Debug, Clone)]
pub struct PendingAction {
    pub title: String,
    pub method: String,
    pub params: Value,
    pub validate_after: bool,
    pub reload_after: bool,
}

#[derive(Debug, Clone)]
pub enum UiEffect {
    None,
    Quit,
    LoadCatalog,
    Call {
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
    pub status: String,
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
            status: "Loading marketplace catalog...".to_string(),
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
        self.status = format!(
            "Loaded {} offerings from {}",
            self.offerings.len(),
            self.browse_repository
        );
    }

    pub fn set_error(&mut self, error: String) {
        self.status = format!("Error: {error}");
    }

    pub fn set_rpc_result(&mut self, method: &str, value: &Value) {
        if let Some(messages) = value.get("messages").and_then(Value::as_array) {
            let text = messages
                .iter()
                .filter_map(Value::as_str)
                .collect::<Vec<_>>()
                .join("; ");
            if !text.is_empty() {
                self.status = text;
                return;
            }
        }
        if method == "validation.run" {
            let exit_code = value.get("exitCode").and_then(Value::as_i64).unwrap_or(1);
            self.status = if exit_code == 0 {
                "Validation passed".to_string()
            } else {
                "Validation failed".to_string()
            };
        } else {
            self.status = format!("{method} completed");
        }
    }

    pub fn handle_key(&mut self, key: KeyEvent) -> UiEffect {
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

    pub fn command_suggestions(&self) -> Vec<&'static str> {
        let commands = [
            "browse ",
            "preview ",
            "host ",
            "target ",
            "scope ",
            "search ",
            "stage",
            "stage all",
            "run staged",
            "clear staged",
            "import",
            "install all",
            "pin ",
            "unpin",
            "update",
            "update all",
            "validate",
            "remote list",
            "quit",
        ];
        commands
            .into_iter()
            .filter(|command| command.starts_with(self.command_input.trim()))
            .take(4)
            .collect()
    }

    fn handle_pending_key(&mut self, key: KeyEvent) -> UiEffect {
        match key.code {
            KeyCode::Esc => {
                self.pending = None;
                self.status = "Cancelled".to_string();
                UiEffect::None
            }
            KeyCode::Enter => {
                let pending = self.pending.take().expect("pending action exists");
                UiEffect::Call {
                    method: pending.method,
                    params: pending.params,
                    validate_after: pending.validate_after,
                    reload_after: pending.reload_after,
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
                } else {
                    self.status = selected_summary(self.selected_offering());
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
            KeyCode::Char('v') => UiEffect::Call {
                method: "validation.run".to_string(),
                params: json!({"repoRoot": self.repo_root, "portable": true}),
                validate_after: false,
                reload_after: false,
            },
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
            return UiEffect::Call {
                method: "validation.run".to_string(),
                params: json!({"repoRoot": self.repo_root, "portable": true}),
                validate_after: false,
                reload_after: false,
            };
        }
        if let Some(host) = command.strip_prefix("host ") {
            match GitHost::parse(host) {
                Ok(host) => {
                    self.default_git_host = host;
                    self.status =
                        format!("Default Git host set to {}", self.default_git_host.as_str());
                }
                Err(error) => self.status = error,
            }
            return UiEffect::None;
        }
        if let Some(target) = command.strip_prefix("target ") {
            let target = target.trim();
            if target.is_empty() {
                self.status = "target requires a repository path".to_string();
                return UiEffect::None;
            }
            self.repo_root = target.to_string();
            self.status = format!("Install target set to {target}");
            return UiEffect::LoadCatalog;
        }
        if let Some(scope) = command.strip_prefix("scope ") {
            match SearchScope::parse(scope) {
                Some(scope) => {
                    self.search_scope = scope;
                    self.apply_filter();
                    self.status = format!("Search scope set to {}", scope.label());
                }
                None => self.status = format!("Unknown search scope: {}", scope.trim()),
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
        if command == "remote list" {
            return UiEffect::Call {
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
            self.status = "Staging cleared".to_string();
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
        self.status = format!("Unknown command: {command}");
        UiEffect::None
    }

    fn pending_import_selected(&mut self) -> UiEffect {
        match self.selected_install_or_update_action() {
            Ok(action) => self.pending = Some(action),
            Err(error) => self.status = error,
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
                let target = format!(
                    "{}/{}",
                    self.browse_repository.trim_end_matches('/'),
                    offering.name
                );
                Ok(PendingAction {
                    title: format!("Install plugin {target}"),
                    method: "marketplace.import".to_string(),
                    params: json!({"repoRoot": self.repo_root, "target": target, "ref": self.ref_name}),
                    validate_after: true,
                    reload_after: true,
                })
            }
            _ => Ok(PendingAction {
                title: format!(
                    "Import {} primitive {}",
                    offering.kind.label(),
                    offering.name
                ),
                method: "marketplace.primitive.import".to_string(),
                params: json!({
                    "repoRoot": self.repo_root,
                    "repository": self.browse_repository,
                    "kind": offering.kind.label(),
                    "name": offering.name,
                    "ref": self.ref_name
                }),
                validate_after: true,
                reload_after: true,
            }),
        }
    }

    fn install_all_action(&self) -> PendingAction {
        PendingAction {
            title: format!("Install every plugin from {}", self.browse_repository),
            method: "marketplace.install".to_string(),
            params: json!({"repoRoot": self.repo_root, "repository": self.browse_repository, "ref": self.ref_name}),
            validate_after: true,
            reload_after: true,
        }
    }

    fn update_all_action(&self) -> PendingAction {
        PendingAction {
            title: "Update all imported plugins".to_string(),
            method: "marketplace.updateAll".to_string(),
            params: json!({"repoRoot": self.repo_root}),
            validate_after: true,
            reload_after: true,
        }
    }

    fn stage_selected_action(&mut self) -> UiEffect {
        match self.selected_install_or_update_action() {
            Ok(action) => {
                self.status = format!("Staged {}", action.title);
                self.staged.push(action);
                self.staged_selected = self.staged.len().saturating_sub(1);
                self.focus = FocusPane::Staging;
            }
            Err(error) => self.status = error,
        }
        UiEffect::None
    }

    fn stage_install_all(&mut self) -> UiEffect {
        let action = self.install_all_action();
        self.status = format!("Staged {}", action.title);
        self.staged.push(action);
        self.staged_selected = self.staged.len().saturating_sub(1);
        self.focus = FocusPane::Staging;
        UiEffect::None
    }

    fn confirm_staged_selected(&mut self) -> UiEffect {
        if self.staged.is_empty() {
            self.status = "No staged install action".to_string();
            return UiEffect::None;
        }
        let index = self.staged_selected.min(self.staged.len() - 1);
        self.pending = Some(self.staged.remove(index));
        if self.staged_selected >= self.staged.len() {
            self.staged_selected = self.staged.len().saturating_sub(1);
        }
        UiEffect::None
    }

    fn remove_staged_selected(&mut self) {
        if self.staged.is_empty() {
            self.status = "No staged install action".to_string();
            return;
        }
        let index = self.staged_selected.min(self.staged.len() - 1);
        let removed = self.staged.remove(index);
        if self.staged_selected >= self.staged.len() {
            self.staged_selected = self.staged.len().saturating_sub(1);
        }
        self.status = format!("Removed {}", removed.title);
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
            Err(error) => self.status = error,
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
        Ok(PendingAction {
            title: format!("{title}: {}", installed.name),
            method: method.to_string(),
            params,
            validate_after: true,
            reload_after: true,
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
                self.status = format!("Previewing {repository}");
                UiEffect::LoadCatalog
            }
            Err(error) => {
                self.status = error;
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
            let version = offering
                .installed
                .as_ref()
                .and_then(|installed| installed.version.as_deref())
                .unwrap_or("not installed");
            format!("{} ({}) - {version}", offering.name, offering.kind.label())
        })
        .unwrap_or_else(|| "No offering selected".to_string())
}

pub fn render(frame: &mut ratatui::Frame, app: &App) {
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

    render_title(frame, title_area, app);
    render_sources(frame, left_area, app);
    render_offerings(frame, center_area, app);
    render_details(frame, details_area, app);
    render_staging(frame, staging_area, app);
    render_command(frame, command_area, app);
    if app.show_help {
        render_shortcuts(frame);
    }
    if let Some(pending) = &app.pending {
        render_pending(frame, pending);
    }
}

fn render_title(frame: &mut ratatui::Frame, area: Rect, app: &App) {
    let title = app
        .marketplace
        .as_ref()
        .map(|marketplace| {
            format!(
                " intelligence | {} | provider {} | target {} | / search | tab panes | ? shortcuts ",
                marketplace.name, marketplace.provider, app.repo_root
            )
        })
        .unwrap_or_else(|| {
            format!(
                " intelligence | target {} | / search | tab panes | ? shortcuts ",
                app.repo_root
            )
        });
    frame.render_widget(Line::from(title).bold(), area);
}

fn render_sources(frame: &mut ratatui::Frame, area: Rect, app: &App) {
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
    let items = vec![
        ListItem::new(format!("Remote: {}", app.browse_repository)),
        ListItem::new(format!("Git host: {}", app.default_git_host.as_str())),
        ListItem::new(format!("Target: {}", app.repo_root)),
        ListItem::new(format!("Search: {}", app.search_scope.label())),
        ListItem::new(format!("Local: {installed} installed, {outdated} updates")),
        ListItem::new(""),
        ListItem::new("Operations"),
        ListItem::new("r preview remote"),
        ListItem::new("i stage selected"),
        ListItem::new("a stage install all"),
        ListItem::new("v validate target"),
        ListItem::new("/ search"),
        ListItem::new("tab focus"),
        ListItem::new(": palette"),
    ];
    let block = focused_block("Source / Operations", app.focus == FocusPane::Sources);
    frame.render_widget(List::new(items).block(block), area);
}

fn render_offerings(frame: &mut ratatui::Frame, area: Rect, app: &App) {
    let rows = app.filtered.iter().filter_map(|index| {
        let offering = app.offerings.get(*index)?;
        let state = offering
            .installed
            .as_ref()
            .map(|installed| {
                if installed.outdated {
                    "update"
                } else if installed.locked {
                    "pinned"
                } else {
                    "installed"
                }
            })
            .unwrap_or("available");
        let version = offering
            .installed
            .as_ref()
            .and_then(|installed| installed.version.clone())
            .unwrap_or_default();
        Some(Row::new(vec![
            offering.kind.label().to_string(),
            offering.name.clone(),
            offering.source.label().to_string(),
            state.to_string(),
            version,
        ]))
    });
    let table = Table::new(
        rows,
        [
            Constraint::Length(11),
            Constraint::Percentage(40),
            Constraint::Length(8),
            Constraint::Length(10),
            Constraint::Length(12),
        ],
    )
    .header(Row::new(vec!["Type", "Name", "Source", "State", "Version"]).style(Style::new().bold()))
    .block(focused_block(
        "Offerings",
        app.focus == FocusPane::Offerings,
    ))
    .row_highlight_style(Style::new().bg(Color::DarkGray))
    .highlight_symbol("> ");
    let mut state = TableState::default().with_selected(Some(app.selected));
    frame.render_stateful_widget(table, area, &mut state);
}

fn render_details(frame: &mut ratatui::Frame, area: Rect, app: &App) {
    let selected = app.selected_offering();
    let mut lines = Vec::new();
    if let Some(offering) = selected {
        lines.push(Line::from(vec![Span::styled(
            &offering.name,
            Style::new().add_modifier(Modifier::BOLD),
        )]));
        lines.push(Line::from(format!(
            "{} | {}",
            offering.kind.label(),
            offering.source.label()
        )));
        if let Some(repository) = &offering.repository {
            lines.push(Line::from(format!("repository: {repository}")));
        }
        if let Some(description) = &offering.description {
            lines.push(Line::from(""));
            lines.push(Line::from(description.clone()));
        }
        if !offering.tags.is_empty() {
            lines.push(Line::from(""));
            lines.push(Line::from(format!("tags: {}", offering.tags.join(", "))));
        }
        if let Some(installed) = &offering.installed {
            lines.push(Line::from(""));
            lines.push(Line::from(format!(
                "installed: {}",
                installed.version.as_deref().unwrap_or("unknown")
            )));
            if let Some(current) = &installed.current_version {
                lines.push(Line::from(format!("remote: {current}")));
            }
            lines.push(Line::from(format!("locked: {}", installed.locked)));
        }
        lines.push(Line::from(""));
        lines.push(Line::from(format!("install target: {}", app.repo_root)));
    } else {
        lines.push(Line::from("No offering selected"));
    }
    frame.render_widget(
        Paragraph::new(lines)
            .block(focused_block("Details", app.focus == FocusPane::Details))
            .wrap(Wrap { trim: true }),
        area,
    );
}

fn render_staging(frame: &mut ratatui::Frame, area: Rect, app: &App) {
    let items = if app.staged.is_empty() {
        vec![ListItem::new("No staged actions")]
    } else {
        app.staged
            .iter()
            .map(|action| ListItem::new(action.title.clone()))
            .collect()
    };
    let mut state =
        ListState::default().with_selected((!app.staged.is_empty()).then_some(app.staged_selected));
    frame.render_stateful_widget(
        List::new(items)
            .block(focused_block(
                "Staging / Install",
                app.focus == FocusPane::Staging,
            ))
            .highlight_symbol("> ")
            .highlight_style(Style::new().bg(Color::DarkGray)),
        area,
        &mut state,
    );
}

fn render_command(frame: &mut ratatui::Frame, area: Rect, app: &App) {
    let prompt = match app.input_mode {
        InputMode::Search => format!("/{} {}", app.search_scope.label(), app.search_query),
        InputMode::Command => format!(":{}", app.command_input),
        InputMode::Normal => app.status.clone(),
    };
    let suggestions = match app.input_mode {
        InputMode::Command => {
            let values = app.command_suggestions();
            if values.is_empty() {
                String::new()
            } else {
                format!("  {}", values.join("  "))
            }
        }
        InputMode::Search => {
            "  tab changes search type, enter previews repository scope".to_string()
        }
        InputMode::Normal => {
            "  ? shortcuts   / search   : palette   enter preview/staged confirm".to_string()
        }
    };
    frame.render_widget(
        Paragraph::new(vec![Line::from(prompt), Line::from(suggestions)])
            .block(Block::new().borders(Borders::ALL).title("Command")),
        area,
    );
}

fn render_shortcuts(frame: &mut ratatui::Frame) {
    let area = centered_rect(72, 58, frame.area());
    frame.render_widget(Clear, area);
    let body = vec![
        Line::from("Keyboard shortcuts").bold(),
        Line::from(""),
        Line::from("/ search current catalog and local installed plugins"),
        Line::from("tab / shift-tab move panes; in search, cycle all/repository/user/plugin/primitive/installed"),
        Line::from("r preview remote from repository search, using the configured default Git host"),
        Line::from("i stage selected install/update; a stage install all"),
        Line::from("enter opens details or confirms the selected staged action"),
        Line::from("v validate the selected install target"),
        Line::from(": palette for browse, preview, host, target, scope, staging, update, pin, and remotes"),
        Line::from("x removes a staged action when the staging pane is focused"),
        Line::from("esc or ? closes this panel"),
    ];
    frame.render_widget(
        Paragraph::new(body)
            .wrap(Wrap { trim: true })
            .block(Block::new().borders(Borders::ALL).title("Shortcuts")),
        area,
    );
}

fn render_pending(frame: &mut ratatui::Frame, pending: &PendingAction) {
    let area = centered_rect(70, 35, frame.area());
    frame.render_widget(Clear, area);
    let body = vec![
        Line::from(pending.title.clone()).bold(),
        Line::from(""),
        Line::from(format!("method: {}", pending.method)),
        Line::from(format!("params: {}", pending.params)),
        Line::from(""),
        Line::from("Enter confirms. Esc cancels."),
    ];
    frame.render_widget(
        Paragraph::new(body)
            .wrap(Wrap { trim: true })
            .block(Block::new().borders(Borders::ALL).title("Confirm")),
        area,
    );
}

fn focused_block(title: &'static str, focused: bool) -> Block<'static> {
    let style = if focused {
        Style::new().fg(Color::Cyan)
    } else {
        Style::new()
    };
    Block::new()
        .borders(Borders::ALL)
        .title(title)
        .border_style(style)
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

    fn test_config() -> Config {
        Config {
            repo_root: ".".to_string(),
            ref_name: None,
            intelligence_bin: "intelligence".to_string(),
            browse_repository: "amichne/slopsentral".to_string(),
            default_git_host: GitHost::github(),
        }
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

        assert_eq!(1, offerings.len());
        assert_eq!(OfferingSource::Installed, offerings[0].source);
        assert!(
            search_score_scoped("enterprise", SearchScope::Repository, &offerings[0]).is_some()
        );
        assert!(search_score_scoped("private", SearchScope::Installed, &offerings[0]).is_some());
    }
}
