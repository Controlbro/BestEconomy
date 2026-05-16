# BestEconomy

## Internal placeholders

BestEconomy does not require PlaceholderAPI for its built-in placeholders. Player-facing messages, the scoreboard, and the tab list can use these internal placeholders:

- `{player}` / `{Player}` - the receiving player's name.
- `{money}` / `{Money}` - the receiving player's Money balance.
- `{shards}` / `{Shards}` - the receiving player's Shards balance.
- `{playtime}` / `{Playtime}` - the receiving player's total playtime, formatted as days/hours/minutes/seconds.
- `{ping}` / `{Ping}` - the receiving player's ping in milliseconds.
- `{tps}` / `{TPS}` - the current server TPS.
- `{players_online}` / `{online}` - the current online player count.
- `{max_players}` - the server max player count.
- `{tab_prefix}` - tab-list only; selected from `tab.yml` prefix rules.

## Scoreboard and tab list

- `scoreboard.yml` controls the sidebar scoreboard title, update interval, and lines.
- `tab.yml` controls the tab header, footer, player-name format, online-player display, and permission-based tab prefixes.
