# Idea Echo

An IntelliJ IDEA plugin that finds **nearby word repetitions** ("echoes") in LaTeX
manuscripts and makes them impossible to miss.

When you write long prose, the same word tends to resurface a few lines after its
first appearance. The reader hears the echo even when the writer does not. Idea
Echo scans the `.tex` file you are editing, groups words that share a root, and
highlights every occurrence that falls within a configurable look-back window.
A side panel lists the offending words with their frequency and how close they
came to each other, and renders a synonyms/antonyms page so you can fix the
repetition on the spot.

The plugin is tuned for Italian: word grouping uses the Snowball Italian stemmer,
so `portare` and `porta`, or `stella` and `stelle`, count as the same word.

---

## Table of contents

- [Requirements](#requirements)
- [Building and installing](#building-and-installing)
- [How the analysis works](#how-the-analysis-works)
- [The editor](#the-editor)
- [The tool window](#the-tool-window)
- [Settings](#settings)
- [External YAML configuration](#external-yaml-configuration)
- [LaTeX handling](#latex-handling)
- [Troubleshooting](#troubleshooting)
- [Known limitations](#known-limitations)

---

## Requirements

- IntelliJ IDEA 2024.3 or newer (Community or Ultimate). The plugin declares
  `sinceBuild = 243`.
- A JDK 21 as the Gradle JVM, which is what the 2024.3 platform expects.
- **TeXiFy IDEA** is optional but recommended. With it installed, `.tex` files use
  the `Latex` language; without it they are plain text. The plugin registers its
  annotator for **both**, so it works either way.

## Building and installing

Build the distributable ZIP with the `buildPlugin` Gradle task. From the IDE, open
the Gradle tool window and run `Tasks → intellij platform → buildPlugin`. From a
terminal (generate the wrapper once with `gradle wrapper` if it is missing):

```bash
./gradlew buildPlugin
```

The archive appears in `build/distributions/idea-echo-plugin-<version>.zip`.

To install it: `Settings → Plugins → ⚙ → Install Plugin from Disk…`, select the
**ZIP** (not the inner `.jar`), then restart the IDE.

To try changes without installing, run the `runIde` task: it launches a sandbox
IDE with the plugin loaded.

## How the analysis works

The analyzer walks the document and keeps a **sliding window** of the last *N*
accepted words. For every new word it looks back through that window; if a word
with the same stem is already there, both occurrences are marked as an echo.

Three filters run before a word enters the window:

1. **LaTeX pre-processing.** Comments and command wrappers are blanked out so that
   only real prose is analyzed (see [LaTeX handling](#latex-handling)).
2. **Minimum length.** Words shorter than the configured minimum are skipped.
3. **Ignored words.** A list of function words (articles, prepositions, auxiliary
   verbs) is excluded. Each entry is a regular expression.

Words that survive are lower-cased and reduced to their **Snowball Italian stem**.
Two words are considered the same when their stems match. This is what makes
`sollecitare` / `sollecitò` and `porta` / `portare` group together, which is the
intended behaviour: *"portare alla porta"* is a repetition worth catching.

The distance counter only advances on **accepted** words, so the numbers shown in
the table are directly comparable with the look-back window you configured.

## The editor

Highlighting is active **only while the tool window is visible**. Close or hide
the panel and both the colouring and the background analysis stop; reopen it and
everything comes back. This keeps large documents responsive when you are just
writing.

Two distinct layers of highlighting exist.

**Persistent echo colouring** paints every detected echo with a background colour.
The colour is derived from the word's stem via CRC32, so a given root always gets
the same hue, in this file and in every other one. **Red is reserved** for words
repeated *within the same sentence* — the most jarring kind of echo.

**On-demand marking** appears when you select a row in the table, or when the
caret enters a highlighted word. Every occurrence of that word group gets a red
underline plus a red marker on the right-hand error stripe, so you can see at a
glance where else the word appears in the document.

Both layers coexist: the background tells you *this is an echo*, the underline
tells you *this is the one you are looking at*.

## The tool window

Open it from the right edge of the IDE, or via `View → Tool Windows → Idea Echo`.

### Toolbar

| Control    | Meaning                                                                                                                                                                |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Update** | Full reset: clears the current marking, re-reads the settings (including the external YAML file, if enabled), re-analyzes the document, and repaints the editor.       |
| **Min**    | Minimum word length, 2 to 4.                                                                                                                                           |
| **Window** | Look-back window in words, 20 to 500 in steps of 20.                                                                                                                   |

Changing `Min` or `Window` persists the value and resets everything immediately.

### Table

One row per word group, with all its variants joined alphabetically
(`stella/stelle`). The word is painted in the same stable colour used in the
editor.

| Column    | Meaning                                                                    |
|-----------|----------------------------------------------------------------------------|
| **Word**  | The variants sharing one stem.                                             |
| **#**     | Total occurrences marked as echoes across all variants.                    |
| **∂w**    | Minimum distance, in accepted words, between two occurrences.              |
| **∂s**    | Minimum distance in sentences. `0` means two occurrences share a sentence. |

The two minima are computed independently, so they may come from different pairs
of occurrences.

Click a column header to sort. Sorting by **∂w** ascending puts the most
irritating echoes — the closest ones — at the top.

**Single click** on a row loads the synonyms page and marks all occurrences in the
editor. **Double click** jumps the caret to the first occurrence.

Note that `#` counts occurrences flagged as echoes, not every appearance of the
word in the file: an occurrence too far from its neighbours to fall inside the
window is not an echo and is not counted.

### Thesaurus

Below the table, a dropdown selects the synonyms/antonyms server, and the page for
the currently selected word is rendered underneath. The choice is remembered per
project.

## Settings

`Settings → Tools → Idea Echo`. Everything here is stored **per project**.

**Minimum word length** and **Look-back window** mirror the toolbar controls.

**Ignored LaTeX commands (with content)** is a list with `+` / `-` buttons. These
commands are removed together with their arguments, so nothing inside them is ever
analyzed. Enter the command name **without** the backslash.

**Text argument (command → 1-based index)** is a two-column table, editable in
place. It declares which argument of a command holds the real prose. Commands not
listed default to their first argument. For example, `chapterwithsummary → 3`
means that in `\chapterwithsummary{label}{summary}{prose}` only the third argument
is analyzed.

**Ignored words** is a free-form text area, one entry per line, where each entry is
a **regular expression matched against the whole word**. A plain word works as
itself, and character classes let you collapse inflections:

```
dopo
quell[aeio]
stat[aeio]
per[oò]
```

`quell[aeio]` covers *quella, quelle, quelli, quello* in one line. Because the
match must span the entire word, `quell[aeio]` will never swallow `quellissimo`.
An invalid pattern is skipped rather than breaking the analysis.

The defaults for ignored commands and ignored words live in
`src/main/resources/messages/EchoBundle.properties`, which means they follow the
IDE language and can be localized.

## External YAML configuration

Tick **Store configuration in an external YAML file** to move the settings out of
the IDE project store and into a file you can commit alongside your manuscript.

The file **must live inside the project directory**; the picker is rooted there and
a path outside it is rejected when you press Apply.

```yaml
minWordLength: 4
windowSize: 160
ignoredCommands:
  - comment
  - note
  - scene
ignoredWords:
  - all[aeo]
  - quell[aeio]
  - dopo
textArgument:
  chapterwithsummary: 3
```

### Behaviour

- **Enabling the option** creates the file from the values currently in the form,
  if it does not already exist.
- **While enabled**, every change — including the toolbar spinners — is written to
  the YAML file. The internal project settings are left untouched.
- **Disabling the option** restores the internal values exactly as they were before
  you switched. The file stays on disk.

Two buttons, enabled **only in internal mode**, bridge the two:

- **Write file from current values** exports the form to the file without switching
  to external mode.
- **Import values from file** loads the file into the form; pressing Apply then
  stores those values internally.

### Editing the file by hand

The file is re-read when its modification time changes. Editing it while the IDE is
closed is safe: everything is loaded at the next start. Editing it while the IDE is
running requires two things — **save the file** (an unsaved editor buffer has not
changed on disk yet), then press **Update** in the tool window.

The thesaurus server choice is deliberately *not* stored in the YAML file: it is an
IDE preference, not a property of the manuscript.

## LaTeX handling

Before analysis the document is rewritten so that only prose remains. Characters
are blanked, never moved, so every highlight lands on the right offset.

**Comments** are removed from `%` to the end of the line. An escaped `\%` is a
literal percent sign and is left alone.

**Ignored commands** disappear with all their arguments, whether they use braces or
brackets.

**Commands with arguments** are unwrapped to the argument that holds the prose —
the first one by default, or the one declared in the settings table. The content is
scanned recursively, so `\sjm{\french{Paperino}}` resolves correctly, and any
trailing arguments or `[...]` options are discarded. So `\sjm{Pippo}` and
`\french{Paperino}` are analyzed as *Pippo* and *Paperino*.

**Commands without arguments** are kept **literally**, backslash and capitalization
included: `\Jacques` remains the token `\Jacques`. Such tokens are never stemmed
and only ever match another identical token, so a character macro echoes against
itself but never against an ordinary word.

## Troubleshooting

**The table works but the editor is not coloured.** The annotator is registered per
language, and the language ID is case-sensitive: with TeXiFy installed, `.tex` is
`Latex`, not `LATEX`. Both `Latex` and `TEXT` are registered in `plugin.xml`, so
check that the file really is one of the two.

**Nothing is detected in a chapter file.** Your prose is probably inside a command
argument that is not the first one. Declare it in the **Text argument** table.

**The YAML file changes are ignored.** Save the file, then press **Update**. The
cache is invalidated by the file's modification time, and an unsaved buffer has not
touched the disk yet.

**`NoClassDefFoundError: org/yaml/snakeyaml/Yaml`.** The plugin compiles against the
SnakeYAML copy shipped with the IntelliJ platform, declared `compileOnly` so it is
not bundled twice. If your IDE build does not expose it, change the dependency in
`build.gradle.kts` to `implementation`.

**Gradle fails at configuration time.** The IntelliJ Platform Gradle plugin is
applied with its version in `settings.gradle.kts` only, and repositories are
declared there too (`FAIL_ON_PROJECT_REPOS`). Do not add a `repositories { }` block
or a plugin version in `build.gradle.kts`.

## Known limitations

- Stemming groups words by root, which is the point, but it also merges unrelated
  homographs that share a stem. This is a deliberate trade-off.
- Multi-argument commands keep only one argument; the others are dropped.
- The analyzer re-scans the whole file on every run. This is fine for normal
  chapters; very large single files may feel sluggish while typing.