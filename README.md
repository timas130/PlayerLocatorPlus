# Player Locator Plus

Track other players' locations on your experience bar like a compass!

<img alt="Requires Cloth Config API" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/requires/cloth-config-api_vector.svg"> <img alt="Requires Fabric API" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/requires/fabric-api_vector.svg"> <img alt="Requires Fabric Langauge Kotlin" height="56" src="requires_kotlin.svg">

![Screenshot of the player locator](screenshot1.png)

## Marker colors

Each player's color is determined randomly by their UUID.
You cannot change your UUID (unless you're playing in offline mode); therefore,
it's impossible to change your marker color.

## Distance fade

When a player is *very* far away, their marker starts to fade:

![The player locator with one marker slightly faded](screenshot2.png)

## Disappearing

There are three ways to hide from the player locator:
1. Crouching
2. Wearing a carved pumpkin
3. Having the invisibility effect

![A player wearing a pumpkin is not shown on the player locator](screenshot3.png)

## Configuration

The config is available at `config/player-locator-plus.toml`.
You can also access the configuration screen with Mod Menu.

Server parameters:

- `enabled`: Is the mod enabled server-side at all. Note that the bar will still be visible if `visible` is true. Default: `true`
- `sendServerConfig`: Whether the server will try to force its config on the client. This will only have effect if the clients have `acceptServerConfig` set to true. Default: `true`
- `sendDistance`: Whether to send distance information along with the direction information.
  There's basically no point in settings this to `false`, as players can still easily
  triangulate the exact location of others even without knowing the distance.
  However, it is harder with this option on, especially if you consider that other players
  move.
  Default: `true`
- `maxDistance`: The maximum distance at which other players are visible on the compass.
  Default: `0` (unlimited)
- `directionPrecision`: The amount of segments the direction vector is split into. Decreasing this value significantly will make tracking way more inaccurate but be helpful in preventing triangulation. Default: `300`
- `ticksBetweenUpdates`: How many ticks apart are the compass updates.
  The less, the smoother the movements of faraway players are.
  Close players (inside the entity render distance) do not depend on this parameter as much.
  Default: `5` (four times per second)
- `sneakingHides`: Whether sneaking hides players from the locator. Default: `true`
- `pumpkinHides`: Whether wearing a pumpkin (or any other `gaze_disguise_equipment`) hides
  players from the locator.
  Default: `true`
- `mobHeadsHide`: Whether wearing a mob/player head hides players from the locator. The exact list can be edited with a datapack by changing `data/player-locator-plus/tags/item/hiding_equipment.json` (see default [here](https://github.com/timas130/PlayerLocatorPlus/blob/main/src/main/resources/data/player-locator-plus/tags/item/hiding_equipment.json)). Default: `true`
- `invisibilityHides`: Whether being invisible hides players from the locator. Default: `true`

Client parameters:

- `visible`: Show the player locator. Default: `true`
- `visibleEmpty`: Show the player locator even there are no players online and no markers in sight. Default: `false`
- `acceptServerConfig`: Whether to override the client-side config with the server-side one. The server config will also be able set client parameters. Default: `true`
- `fadeMarkers`: Fade markers of faraway players. Default: `true`
- `fadeStart`: At what distance the markers start to fade. Default: `100`
- `fadeEnd`: At what distance the markers stop fading and settle at `fadeEndOpacity`. Default: `1000`
- `fadeEndOpacity`: The final opacity when/after `fadeEnd` is reached. Default: `0.3`
- `showHeight`: Show little arrows above/below a marker if the height difference is significant.
  Default: `true`

## Acknowledgements

Originally introduced in Minecraft: Bedrock Edition Preview
[here](https://www.minecraft.net/en-us/article/test-the-new-player-locator-bar),
covered by Phoenix SC.

## License

Player Locator Plus is licensed under the GNU General Public License Version 3 or later.
