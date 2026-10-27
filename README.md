# Crashfix

Crashfix is a small Fabric mod that fixes a vanilla crash: hovering the tooltip of a
bundle that contains an item stack with incompatible data kills the game with
`java.lang.IllegalStateException: Stack must be non-empty`
([MC-306072](https://bugs.mojang.com/browse/MC-306072)).

## The bug

The crash was introduced in 26.1-snapshot-3 and is still present in 26.2.

Code path:

```
BundleItem.toggleSelectedItem(ItemStack, int)      // called while the tooltip is built
  -> BundleContents$Mutable.toImmutable()
       -> ItemStackTemplate.fromNonEmptyStack(ItemStack)   // "Stack must be non-empty"
```

`ItemStackTemplate#validate` throws away item stacks whose data contradicts itself
(an example from the bug report):

- an item with `minecraft:max_stack_size` set to something other than `1` **and** a
  `minecraft:max_damage` component,
- a stack whose `count` is greater than its `minecraft:max_stack_size`.

Vanilla replaces such a stack with `ItemStack.EMPTY`. Inside a bundle that empty stack
is converted back with `ItemStackTemplate#fromNonEmptyStack`, which *requires* a
non-empty stack - so the tooltip crashes the game.

Reproduction (vanilla):

```
data modify block ~ ~-1 ~ Items[0].components merge value {"minecraft:max_stack_size":99}
data modify block ~ ~-1 ~ Items[0].components."minecraft:bundle_contents"[0].count set value <value>
```

then hover the bundle in the inventory.

## What the mod does

Two mixins (`src/main/resources/crashfix.mixins.json`):

1. `ItemStackTemplateMixin` - injected at the end of the private
   `ItemStackTemplate#validate`. When vanilla decided to drop a malformed stack, the
   mod rebuilds a usable one instead (`ItemStackRepair`): only the contradictory
   components (`max_stack_size`, `max_damage`, `damage`, `unbreakable`) are removed and
   **the original count is kept**. So a bundle that claims `count = 100` snowballs still
   shows 100 snowballs instead of crashing, and it weighs exactly as much as the
   equivalent bundle of legitimate, split stacks (`weight = count / maxStackSize`). The
   injection sits at the end of the method so the strict validation only runs again for
   actually broken data.
2. `ItemStackTemplateEmptyGuardMixin` - a last line of defence, injected at the head of
   `ItemStackTemplate#fromNonEmptyStack(ItemStack)`, the method whose
   `IllegalStateException: Stack must be non-empty` is the documented crash. Entries
   that cannot be repaired at all (for example an `air` entry injected with commands)
   are replaced with a barrier placeholder instead of crashing the game.

   This guard is written against `fromNonEmptyStack` on purpose: its bytecode is
   identical in 26.2 and 26.3, while everything around it was refactored in 26.3
   (`BundleContents$Mutable` grew a `GrowableMutableContainer` parent class, and
   `BundleContents#asMutable()` replaced the public `Mutable(BundleContents)`
   constructor). The main fix survived that refactor unchanged.

Both injections are declared with `require = 1` while the mixin config is
`"required": false`, so a Minecraft release that renames these internals logs a loud
warning at startup instead of silently disabling the fix or stopping the game.

## Requirements

- Minecraft 26.2 and 26.3 (declared range `>=26.2 <27.0.0`)
- Fabric Loader 0.19.3 or newer
- Fabric API
- Java 25

The mod is environment agnostic (`*`), so it also protects a dedicated server that
touches the same code path.

## Verifying the fix

In game (creative or with cheats enabled):

```
/crashfix selftest
```

This gives you a bundle holding both malformed stacks from the bug report. Hovering it
used to crash the game instantly; with the mod installed the tooltip renders and the
bundle still shows every item, including the oversized amount.

Behaviour of the repair, measured on dedicated 26.2 and 26.3 servers with the same jar:

| Bundle entry | Result |
| --- | --- |
| `minecraft:max_stack_size: 99` together with `minecraft:max_damage` | item kept, both bogus components removed, damage bar gone |
| `count` above `minecraft:max_stack_size` (for example 100 snowballs) | **count kept**: the bundle still shows 100 snowballs |
| a perfectly normal entry (64 cobblestone) | never touched by the mod |

Bundle weight stays consistent with the equivalent bundle of legitimate, split stacks
(`weight = count / maxStackSize`), so the fullness bar and the insert limit behave exactly
like they do for a bundle created by Minecraft itself.

## Project layout

```
src/main/java/de/badstarry/crashfix
├── Crashfix.java                             # common entrypoint
├── CrashfixClient.java                       # client entrypoint
├── client/SelfTestCommand.java               # /crashfix selftest
├── mixin/
│   ├── ItemStackTemplateMixin.java           # repairs malformed stacks
│   └── ItemStackTemplateEmptyGuardMixin.java # guards the crash site itself
├── repair/ItemStackRepair.java               # repair logic
└── util/OneShotLog.java                      # warn once, then debug
```

Minecraft 26.2 is consumed as a merged jar, so the mod uses a single source set
instead of Loom's `splitEnvironmentSourceSets()`.

## Development

Build with a JDK 25 (e.g. GraalVM 25):

```
gradlew.bat build --init-script init.gradle
```

The `init.gradle` script rewrites Maven Central URLs to Aliyun mirrors, which is
required in environments where `repo.maven.apache.org` returns 403. The result is
`build/libs/crashfix-1.0.0.jar`.

To try it in a development world:

```
gradlew.bat runClient --init-script init.gradle
gradlew.bat runServer --init-script init.gradle
```

## License

See [`LICENSE`](LICENSE).