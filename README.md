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

The plugin is tuned for Italian. Words are grouped by their Snowball Italian stem,
by a shared beginning (`bella` and `bellezza`), and, when you provide the free
Morph-it! lexicon, by their dictionary form (`strinse` and `stringeva`).

---

## Table of contents

- [Requirements](#requirements)
- [Building and installing](#building-and-installing)
- [How the analysis works](#how-the-analysis-works)
- [The editor](#the-editor)
- [The tool window](#the-tool-window)
- [Settings](#settings)
- [External YAML configuration](#external-yaml-configuration)
- [The Morph-it! lexicon](#the-morph-it-lexicon)
- [LaTeX handling](#latex-handling)
- [Troubleshooting](#troubleshooting)
- [Known limitations](#known-limitations)

---

## Requirements

- IntelliJ IDEA 2026.1 or newer. The plugin declares `sinceBuild = 261` and is built
  against IntelliJ IDEA 2026.1.4; it declares no Ultimate-only dependency.
- A JDK 21 as the Gradle JVM, which the IntelliJ Platform requires.
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
that *belongs with it* is already there, both occurrences are marked as an echo.

Three filters run before a word enters the window:

1. **LaTeX pre-processing.** Comments and command wrappers are blanked out so that
   only real prose is analyzed (see [LaTeX handling](#latex-handling)).
2. **Minimum length.** Words shorter than the configured minimum are skipped.
3. **Ignored words.** A list of function words (articles, prepositions, auxiliary
   verbs) is excluded. Each entry is a regular expression.

Words that survive are lower-cased. Two words belong together when **any** of these
holds:

1. **Same stem.** Both reduce to the same **Snowball Italian stem**. This is what makes
   `sollecitare` / `sollecitò` and `porta` / `portare` group together, which is the
   intended behaviour: *"portare alla porta"* is a repetition worth catching.
   This rule can be turned off (see [Settings](#settings)).
2. **Same lemma.** The [Morph-it! lexicon](#the-morph-it-lexicon), if configured, lists a
   dictionary form they have in common. This catches irregular verbs and forms the
   stemmer cannot relate: `strinse` / `stringeva`, `offerto` / `offriva`, `poco` / `pochi`.
3. **Common beginning.** They share at least the configured number of leading letters
   (4 by default). This catches derived words: `bella` / `bellezza`, `fede` / `fedele`,
   `gentile` / `gentilissimo`.

The rules are not transitive: `poco` matches `pochi` through the lexicon and `pochi`
matches `pochissimi` through the beginning, but `poco` and `pochissimi` do not match
each other. A **word group** is everything linked by a chain of echoes actually found in
the file, so in that example all three end up in one group.

The distance counter only advances on **accepted** words, so the numbers shown in
the table are directly comparable with the look-back window you configured.

## The editor

Highlighting is active **only while the tool window is visible**. Close or hide
the panel and both the colouring and the background analysis stop; reopen it and
everything comes back. This keeps large documents responsive when you are just
writing.

Two distinct layers of highlighting exist.

**Persistent echo colouring** paints every detected echo with a background colour.
The colour is derived from the word group via CRC32. A group made of a single stem
always gets the same hue, in this file and in every other one; a group joined by the
lexicon or by a common beginning takes the hue of its alphabetically first stem.
**Red is reserved** for an occurrence that has a twin *within the same sentence*,
whatever the variant — the most jarring kind of echo.

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
(`stella/stelle`, `bella/bellezza`). The word is painted in the same stable colour used in the
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

`Settings → Tools → Idea Echo`. Everything here is stored **per project**, except the
lexicon path.

**Minimum word length** and **Look-back window** mirror the toolbar controls.

**Group words with the same Snowball stem** turns the stem rule on or off; it is on by
default. With the lexicon configured you may want it off: the stemmer cuts unrelated
words down to the same root (`cosa` and `così`, `casa` and `caso`, `capo` and
`capito`), while the lexicon knows they have different lemmas. Its only gain then is on
forms the lexicon lacks, such as archaic or regional ones (`bevere`, `poté`).

**Group words sharing the first letters** is the number of leading letters two words
must have in common to be grouped even when their stems differ. The default is 4;
raise it if unrelated words get grouped, set it to 0 to turn the rule off.

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

**Morph-it! lexicon** is the path of the lexicon file (see
[The Morph-it! lexicon](#the-morph-it-lexicon)). Unlike everything else on this page it
is **shared by all projects**, and it is never written to the YAML file. Leave it blank
to group words by stem and common beginning only.

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
minCommonPrefix: 4
useStemmer: true
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

The thesaurus server choice and the lexicon path are deliberately *not* stored in the
YAML file: they are IDE preferences, not properties of the manuscript.

## The Morph-it! lexicon

[Morph-it!](https://docs.sslmit.unibo.it/doku.php?id=resources:morph-it) is a free
morphological lexicon of Italian by Marco Baroni and Eros Zanchetta: about half a
million inflected forms, each with its lemma. It is not bundled with the plugin: you download
it once and tell the plugin where it is.

### Installing it

1. Download the lexicon from the
   [Morph-it! page](https://docs.sslmit.unibo.it/doku.php?id=resources:morph-it). You
   need the plain-text word list, a file of about 19 MB with a name like
   `morph-it_048.txt` (the number is the version). If it comes as an archive, extract
   that file. The versions compiled for SFST or the Finite State Utilities are not
   usable here.
2. Move the file somewhere stable, outside your manuscripts: the setting is shared by
   all projects and the plugin reads the file from that path every time the IDE starts.
   For example `~/Documents/Lexicons/morph-it_048.txt`.
3. Open `Settings → Tools → Idea Echo`, click the folder button next to
   **Morph-it! lexicon**, select the file and press **Apply**. A path that does not
   point to a readable file is rejected.
4. Check that it was read: `Help → Show Log in Finder/Explorer/Files` opens the IDE
   log, which should contain a line like
   `Idea Echo: 404710 forms read from …/morph-it_048.txt`.

To stop using the lexicon, clear the field and press **Apply**. To update it, replace
the file or select the new one; a changed file is read again automatically.

### Format

The expected format is Morph-it!'s own: one entry per line, tab-separated,
`form⇥lemma⇥features`. Only the first two columns are used. The file may be in
ISO-8859-15, as distributed, or in UTF-8, so a converted copy works too.

### At run time

The file is read in the background the first time it is needed and kept in memory: it
takes under a second and about 50 MB of heap for version 0.48. While
it loads, words are grouped by stem and common beginning only; when it is ready, every
open project is re-analyzed. It is read again when its modification time changes. If it
cannot be read, the reason is written to the IDE log (`Help → Show Log`) and the analysis
goes on without it.

Morph-it! is dual-licensed under the Creative Commons Attribution-ShareAlike 2.0 License
and the GNU Lesser General Public License. The plugin itself remains under the MIT
License, since it neither contains nor redistributes the lexicon.

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

**The build downloads the whole IDE.** Without further settings the platform is
fetched as a remote artifact. To build against an IDE you already have installed, set
its path in `~/.gradle/gradle.properties` (not in the project's `gradle.properties`,
which is committed):

```properties
localIdePath=/Applications/IntelliJ IDEA.app
```

## Known limitations

- Stemming groups words by root, which is the point, but it also merges unrelated
  homographs that share a stem. This is a deliberate trade-off.
- The common-beginning rule is cruder still: with 4 letters, `stato` and `statua` are
  grouped. Raise the number if this gets in the way.
- With the lexicon, very common verbs become a single group: `sono`, `era`, `sarà` are
  all forms of *essere*. Add the forms you do not want to see to **Ignored words**.
- Groups are chains, so a long chain can join variants that look unrelated.
- Multi-argument commands keep only one argument; the others are dropped.
- The analyzer re-scans the whole file on every run. This is fine for normal
  chapters; very large single files may feel sluggish while typing.