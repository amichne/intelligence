use std::error::Error;

use crossterm::event::{self, Event, KeyEventKind};
use intelligence_tui::{render, App, Config, RpcClient, UiEffect};

fn main() {
    if let Err(error) = run() {
        eprintln!("intelligence-tui: {error}");
        std::process::exit(1);
    }
}

fn run() -> Result<(), Box<dyn Error>> {
    let args = std::env::args().skip(1).collect::<Vec<_>>();
    if args.iter().any(|arg| arg == "-h" || arg == "--help") {
        println!("{}", Config::usage());
        return Ok(());
    }
    let config = Config::from_args(args.into_iter())?;
    let mut client = RpcClient::spawn(&config.intelligence_bin)?;
    let mut app = App::new(config);
    apply_effect(UiEffect::LoadCatalog, &mut app, &mut client, None)?;

    let mut terminal = ratatui::init();
    let result = run_terminal(&mut terminal, &mut app, &mut client);
    ratatui::restore();
    result
}

fn run_terminal(
    terminal: &mut ratatui::DefaultTerminal,
    app: &mut App,
    client: &mut RpcClient,
) -> Result<(), Box<dyn Error>> {
    loop {
        terminal.draw(|frame| render(frame, app))?;
        if app.should_quit {
            break Ok(());
        }
        if let Event::Key(key) = event::read()? {
            if key.kind == KeyEventKind::Press {
                let effect = app.handle_key(key);
                apply_effect(effect, app, client, Some(terminal))?;
            }
        }
    }
}

fn apply_effect(
    effect: UiEffect,
    app: &mut App,
    client: &mut RpcClient,
    mut terminal: Option<&mut ratatui::DefaultTerminal>,
) -> Result<(), Box<dyn Error>> {
    match effect {
        UiEffect::None => {}
        UiEffect::Quit => app.should_quit = true,
        UiEffect::LoadCatalog => load_catalog(app, client, terminal.as_deref_mut())?,
        UiEffect::Call {
            title,
            method,
            params,
            validate_after,
            reload_after,
        } => {
            draw_running_frame(app, terminal.as_deref_mut(), &title)?;
            match client.call_value(&method, params) {
                Ok(value) => app.set_rpc_result(&method, &value),
                Err(error) => app.set_error(error),
            }
            if validate_after {
                draw_running_frame(app, terminal.as_deref_mut(), "Validate target")?;
                match client.validate(&app.repo_root) {
                    Ok(value) => app.set_rpc_result("validation.run", &value),
                    Err(error) => app.set_error(error),
                }
            }
            if reload_after {
                load_catalog(app, client, terminal)?;
            }
        }
    }
    Ok(())
}

fn load_catalog(
    app: &mut App,
    client: &mut RpcClient,
    terminal: Option<&mut ratatui::DefaultTerminal>,
) -> Result<(), Box<dyn Error>> {
    draw_running_frame(app, terminal, "Load marketplace catalog")?;
    match client.catalog(&app.repo_root, &app.browse_repository) {
        Ok(catalog) => app.set_catalog(catalog),
        Err(error) => app.set_error(error),
    }
    Ok(())
}

fn draw_running_frame(
    app: &mut App,
    terminal: Option<&mut ratatui::DefaultTerminal>,
    title: &str,
) -> Result<(), Box<dyn Error>> {
    app.set_running(title);
    if let Some(terminal) = terminal {
        terminal.draw(|frame| render(frame, app))?;
    }
    Ok(())
}
