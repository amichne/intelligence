use std::collections::BTreeSet;
use std::io::{BufRead, BufReader, Write};
use std::process::{Child, ChildStdin, Command, Stdio};

use crossterm::event::{KeyCode, KeyEvent};
use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Color, Modifier, Style, Stylize};
use ratatui::text::{Line, Span};
use ratatui::widgets::{
    Block, Borders, Clear, List, ListItem, Paragraph, Row, Table, TableState, Wrap,
};
use serde::Deserialize;
use serde_json::{json, Value};

#[derive(Debug, Clone)]
pub struct Config {
    pub repo_root: String,
    pub ref_name: Option<String>,
    pub intelligence_bin: String,
    pub browse_repository: String,
}

impl Config {
    pub fn usage() -> &'static str {
        "Usage: intelligence-tui [--repo PATH] [--ref REF] [--intelligence-bin PATH] [--browse REPOSITORY]"
    }

    pub fn from_args(args: impl Iterator<Item = String>) -> Result<Self, String> {
        let mut repo_root = ".".to_string();
        let mut ref_name = None;
        let mut intelligence_bin = "intelligence".to_string();
        let mut browse_repository = "amichne/slopsentral".to_string();
        let mut args = args.peekable();
        while let Some(arg) = args.next() {
            match arg.as_str() {
                "--repo" => repo_root = required_value("--repo", &mut args)?,
                "--ref" => ref_name = Some(required_value("--ref", &mut args)?),
                "--intelligence-bin" => {
                    intelligence_bin = required_value("--intelligence-bin", &mut args)?
                }
                "--browse" => browse_repository = required_value("--browse", &mut args)?,
                "-h" | "--help" => return Err(Self::usage().to_string()),
                _ => return Err(format!("unknown argument: {arg}")),
            }
        }
        Ok(Self {
            repo_root,
            ref_name,
            intelligence_bin,
            browse_repository,
        })
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
    Marketplaces,
    Offerings,
    Details,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum InputMode {
    Normal,
    Search,
    Command,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum OfferingKind {
    Plugin,
    Skill,
    Agent,
    Hook,
    Instruction,
}

#[derive(Debug, Clone)]
pub struct Offering {
    pub kind: OfferingKind,
    pub name: String,
    pub description: Option<String>,
    pub tags: Vec<String>,
    pub installed: Option<InstalledPlugin>,
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
    pub marketplace: Option<MarketplaceSummary>,
    pub offerings: Vec<Offering>,
    pub installed: Vec<InstalledPlugin>,
    pub filtered: Vec<usize>,
    pub selected: usize,
    pub focus: FocusPane,
    pub input_mode: InputMode,
    pub search_query: String,
    pub command_input: String,
    pub status: String,
    pub pending: Option<PendingAction>,
    pub should_quit: bool,
}

impl App {
    pub fn new(config: Config) -> Self {
        Self {
            repo_root: config.repo_root,
            browse_repository: config.browse_repository,
            ref_name: config.ref_name,
            marketplace: None,
            offerings: Vec::new(),
            installed: Vec::new(),
            filtered: Vec::new(),
            selected: 0,
            focus: FocusPane::Offerings,
            input_mode: InputMode::Normal,
            search_query: String::new(),
            command_input: String::new(),
            status: "Loading marketplace catalog...".to_string(),
            pending: None,
            should_quit: false,
        }
    }

    pub fn set_catalog(&mut self, catalog: Catalog) {
        self.browse_repository = catalog.marketplace.repository.clone();
        self.marketplace = Some(catalog.marketplace);
        self.installed = catalog.installed;
        self.offerings = offerings_from_catalog(
            catalog.plugins,
            catalog.standalone_primitives,
            &self.installed,
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

    fn handle_normal_key(&mut self, key: KeyEvent) -> UiEffect {
        match key.code {
            KeyCode::Char('q') => UiEffect::Quit,
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
                self.focus = match self.focus {
                    FocusPane::Marketplaces => FocusPane::Offerings,
                    FocusPane::Offerings => FocusPane::Details,
                    FocusPane::Details => FocusPane::Marketplaces,
                };
                UiEffect::None
            }
            KeyCode::Down | KeyCode::Char('j') => {
                self.move_selection(1);
                UiEffect::None
            }
            KeyCode::Up | KeyCode::Char('k') => {
                self.move_selection(-1);
                UiEffect::None
            }
            KeyCode::Enter => {
                self.status = selected_summary(self.selected_offering());
                UiEffect::None
            }
            _ => UiEffect::None,
        }
    }

    fn handle_search_key(&mut self, key: KeyEvent) -> UiEffect {
        match key.code {
            KeyCode::Esc | KeyCode::Enter => {
                self.input_mode = InputMode::Normal;
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
        if command == "remote list" {
            return UiEffect::Call {
                method: "marketplace.remotes.list".to_string(),
                params: json!({"repoRoot": self.repo_root}),
                validate_after: false,
                reload_after: false,
            };
        }
        if let Some(repository) = command.strip_prefix("browse ") {
            let repository = repository.trim();
            if repository.is_empty() {
                self.status = "browse requires a repository".to_string();
                return UiEffect::None;
            }
            self.browse_repository = repository.to_string();
            return UiEffect::LoadCatalog;
        }
        if command == "install all" {
            self.pending = Some(PendingAction {
                title: format!("Install every plugin from {}", self.browse_repository),
                method: "marketplace.install".to_string(),
                params: json!({"repoRoot": self.repo_root, "repository": self.browse_repository}),
                validate_after: true,
                reload_after: true,
            });
            return UiEffect::None;
        }
        if command == "import" || command == "install" {
            return self.pending_import_selected();
        }
        if command == "update all" {
            self.pending = Some(PendingAction {
                title: "Update all imported plugins".to_string(),
                method: "marketplace.updateAll".to_string(),
                params: json!({"repoRoot": self.repo_root}),
                validate_after: true,
                reload_after: true,
            });
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
        let Some(offering) = self.selected_offering() else {
            self.status = "No offering selected".to_string();
            return UiEffect::None;
        };
        match offering.kind {
            OfferingKind::Plugin => {
                let target = format!(
                    "{}/{}",
                    self.browse_repository.trim_end_matches('/'),
                    offering.name
                );
                self.pending = Some(PendingAction {
                    title: format!("Import plugin {target}"),
                    method: "marketplace.import".to_string(),
                    params: json!({"repoRoot": self.repo_root, "target": target, "ref": self.ref_name}),
                    validate_after: true,
                    reload_after: true,
                });
            }
            _ => {
                self.pending = Some(PendingAction {
                    title: format!("Import {:?} primitive {}", offering.kind, offering.name),
                    method: "marketplace.primitive.import".to_string(),
                    params: json!({
                        "repoRoot": self.repo_root,
                        "repository": self.browse_repository,
                        "kind": format!("{:?}", offering.kind).to_lowercase(),
                        "name": offering.name,
                        "ref": self.ref_name
                    }),
                    validate_after: true,
                    reload_after: true,
                });
            }
        }
        UiEffect::None
    }

    fn pending_installed_action(
        &mut self,
        method: &str,
        title: &str,
        version: Option<&str>,
    ) -> UiEffect {
        let Some(offering) = self.selected_offering() else {
            self.status = "No offering selected".to_string();
            return UiEffect::None;
        };
        let Some(installed) = &offering.installed else {
            self.status = "Selected offering is not installed".to_string();
            return UiEffect::None;
        };
        if version.is_some_and(str::is_empty) {
            self.status = "pin requires a version".to_string();
            return UiEffect::None;
        }
        let mut params = json!({"repoRoot": self.repo_root, "plugin": installed.name});
        if let Some(version) = version {
            params["version"] = json!(version);
        }
        self.pending = Some(PendingAction {
            title: format!("{title}: {}", installed.name),
            method: method.to_string(),
            params,
            validate_after: true,
            reload_after: true,
        });
        UiEffect::None
    }

    fn apply_filter(&mut self) {
        let query = self.search_query.trim();
        let mut ranked = self
            .offerings
            .iter()
            .enumerate()
            .filter_map(|(index, offering)| {
                search_score(query, offering).map(|score| (index, score))
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
}

fn offerings_from_catalog(
    plugins: Vec<PluginOffering>,
    primitives: StandalonePrimitives,
    installed: &[InstalledPlugin],
) -> Vec<Offering> {
    let installed_by_name = installed
        .iter()
        .cloned()
        .map(|plugin| (plugin.name.clone(), plugin))
        .collect::<std::collections::BTreeMap<_, _>>();
    let mut offerings = Vec::new();
    for plugin in plugins {
        offerings.push(Offering {
            kind: OfferingKind::Plugin,
            installed: installed_by_name.get(&plugin.name).cloned(),
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
    offerings
}

fn primitive_offering(kind: OfferingKind, primitive: PrimitiveOffering) -> Offering {
    Offering {
        kind,
        name: primitive.name,
        description: primitive.description,
        tags: Vec::new(),
        installed: None,
    }
}

pub fn search_score(query: &str, offering: &Offering) -> Option<i64> {
    if query.is_empty() {
        return Some(0);
    }
    let haystack = searchable_text(offering);
    let mut score = 0;
    for term in query.to_lowercase().split_whitespace() {
        if haystack.contains(term) {
            score += 100 - term.len() as i64;
        } else if is_subsequence(term, &haystack) {
            score += 20;
        } else {
            return None;
        }
    }
    Some(score)
}

fn searchable_text(offering: &Offering) -> String {
    let mut values = vec![
        offering.name.clone(),
        format!("{:?}", offering.kind),
        offering.description.clone().unwrap_or_default(),
        offering.tags.join(" "),
    ];
    if let Some(installed) = &offering.installed {
        values.push("installed".to_string());
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
            format!("{} ({:?}) - {version}", offering.name, offering.kind)
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
        Constraint::Length(26),
        Constraint::Percentage(48),
        Constraint::Percentage(32),
    ])
    .areas(main_area);

    render_title(frame, title_area, app);
    render_left(frame, left_area, app);
    render_offerings(frame, center_area, app);
    render_details(frame, right_area, app);
    render_command(frame, command_area, app);
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
                " intelligence | {} | provider {} | repo {} ",
                marketplace.name, marketplace.provider, app.repo_root
            )
        })
        .unwrap_or_else(|| format!(" intelligence | repo {} ", app.repo_root));
    frame.render_widget(Line::from(title).bold(), area);
}

fn render_left(frame: &mut ratatui::Frame, area: Rect, app: &App) {
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
        ListItem::new(format!("Browse: {}", app.browse_repository)),
        ListItem::new(format!("Installed: {installed}")),
        ListItem::new(format!("Updates: {outdated}")),
        ListItem::new(""),
        ListItem::new("Commands"),
        ListItem::new("/ search"),
        ListItem::new(": palette"),
        ListItem::new("Enter details"),
        ListItem::new("q quit"),
    ];
    let block = focused_block("Marketplaces", app.focus == FocusPane::Marketplaces);
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
            format!("{:?}", offering.kind),
            offering.name.clone(),
            version,
            state.to_string(),
        ]))
    });
    let table = Table::new(
        rows,
        [
            Constraint::Length(12),
            Constraint::Percentage(44),
            Constraint::Length(12),
            Constraint::Length(10),
        ],
    )
    .header(Row::new(vec!["Type", "Name", "Version", "State"]).style(Style::new().bold()))
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
        lines.push(Line::from(format!("{:?}", offering.kind)));
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

fn render_command(frame: &mut ratatui::Frame, area: Rect, app: &App) {
    let prompt = match app.input_mode {
        InputMode::Search => format!("/{}", app.search_query),
        InputMode::Command => format!(":{}", app.command_input),
        InputMode::Normal => app.status.clone(),
    };
    let suggestions = if app.input_mode == InputMode::Command {
        let values = app.command_suggestions();
        if values.is_empty() {
            String::new()
        } else {
            format!("  {}", values.join("  "))
        }
    } else {
        String::new()
    };
    frame.render_widget(
        Paragraph::new(vec![Line::from(prompt), Line::from(suggestions)])
            .block(Block::new().borders(Borders::ALL).title("Command")),
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

    #[test]
    fn search_matches_name_tags_and_installed_state() {
        let offering = Offering {
            kind: OfferingKind::Plugin,
            name: "kotlin-engineering".to_string(),
            description: Some("Typed Kotlin workflow".to_string()),
            tags: vec!["gradle".to_string(), "review".to_string()],
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
        assert!(search_score("missing", &offering).is_none());
    }

    #[test]
    fn tab_cycles_focus_without_rpc_effects() {
        let mut app = App::new(Config {
            repo_root: ".".to_string(),
            ref_name: None,
            intelligence_bin: "intelligence".to_string(),
            browse_repository: "amichne/slopsentral".to_string(),
        });

        assert_eq!(FocusPane::Offerings, app.focus);
        let effect = app.handle_key(KeyEvent::new(KeyCode::Tab, KeyModifiers::NONE));

        assert!(matches!(effect, UiEffect::None));
        assert_eq!(FocusPane::Details, app.focus);
    }

    #[test]
    fn command_palette_builds_update_all_confirmation() {
        let mut app = App::new(Config {
            repo_root: "/tmp/repo".to_string(),
            ref_name: None,
            intelligence_bin: "intelligence".to_string(),
            browse_repository: "amichne/slopsentral".to_string(),
        });
        app.input_mode = InputMode::Command;
        app.command_input = "update all".to_string();

        let effect = app.handle_key(KeyEvent::new(KeyCode::Enter, KeyModifiers::NONE));

        assert!(matches!(effect, UiEffect::None));
        let pending = app.pending.expect("pending action");
        assert_eq!("marketplace.updateAll", pending.method);
        assert!(pending.validate_after);
    }
}
