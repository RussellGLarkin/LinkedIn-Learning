# Lyrics Display

A small web app that shows song lyrics one line at a time, karaoke style, on a dark screen. You can step through the lines by hand or let it play automatically, advancing at a speed you choose.

It's plain HTML, CSS and JavaScript, with no build step. The only external resource is the Inter and Space Grotesk fonts from Google Fonts; without a connection it falls back to system fonts.

## Features

- Shows the current line large in the center, with the previous and next lines dimmed above and below it
- A line number in the header ("LINE 05 / 14") and a "Line 5 of 14" counter
- A progress bar showing how far through the song you are
- **Previous** and **Next** buttons, greyed out at the first and last line
- A round **Play** button that advances automatically and switches to a **Pause** icon while playing
- **Speed** buttons for 1, 2, 3 or 4 seconds per line, 2 seconds by default. Changes apply immediately, even mid-playback
- Keyboard shortcuts:

  | Key | Action |
  | --- | --- |
  | <kbd>Space</kbd> | Play / pause |
  | <kbd>→</kbd> or <kbd>↓</kbd> | Next line |
  | <kbd>←</kbd> or <kbd>↑</kbd> | Previous line |

- Dark theme, and works on phone-sized screens

## Running it

The page loads `lyrics.txt` with `fetch`, and browsers block that when you open the page straight from disk (`file://`). Serve the folder over HTTP instead:

```bash
python3 -m http.server 8000
```

Then open <http://localhost:8000>.

Any other static file server works too, for example `npx serve`.

## Running the tests

There are two test suites:

| Command | What it runs |
| --- | --- |
| `npm test` | Unit tests for the player logic, using Node's built-in test runner (Node 20.4+) |
| `npm run test:ui` | Playwright UI tests in Chrome, Firefox, Android and iOS |
| `npm run test:all` | Both |
| `npm run test:ui:report` | Opens the HTML report from the last UI test run |

### Unit tests (`test/`)

They cover the player logic in `player.js`: parsing `lyrics.txt`, the keyboard shortcut mapping, the line number, counter and progress values, and playback (moving between lines, auto-advance, stopping on the last line, restarting from the top, pausing, and changing speed mid-playback). Timers are mocked, so the suite runs in well under a second.

### UI tests (`e2e/`)

The Playwright tests drive the real page in four browsers:

| Project | Browser | Screen |
| --- | --- | --- |
| `chrome` | Chromium | Desktop |
| `firefox` | Firefox | Desktop |
| `android` | Chromium, emulating a Pixel 7 | Phone, touch |
| `ios` | WebKit (Safari's engine), emulating an iPhone 15 | Phone, touch |

The phones are emulated in desktop browsers, which is close to the real devices but not identical.

The tests cover loading (including a missing file, an empty file, blank lines, a one-line song and HTML in the lyrics), the Previous/Next buttons, playback, the speed buttons, keyboard shortcuts and layout (no sideways scrolling, controls on screen, tap-target size). Playwright's fake clock replaces real waiting, so playback tests check exact timings without slowing the suite down.

First-time setup:

```bash
npm install
npx playwright install chromium firefox webkit
```

`npm run test:ui` starts its own server on port 4173, so it won't clash with one you already have running. Each run writes an HTML report to `playwright-report/`; failed tests also keep a screenshot and a trace in `test-results/`.

## Using your own lyrics

Replace the contents of `lyrics.txt` with your song, one line per line of the file. Blank lines are ignored, so you can leave gaps between verses without getting empty screens.

## Project structure

```
.
├── index.html            # Page markup
├── styles.css            # Styling
├── app.js                # Loads the lyrics and connects the player to the page
├── player.js             # Player logic with no DOM access: parsing, playback, shortcuts
├── lyrics.txt            # The lyrics to display
├── package.json          # Declares ES modules, Playwright and the test scripts
├── playwright.config.js  # Playwright browsers, test server and HTML report
├── test/
│   └── player.test.js    # Unit tests for player.js
└── e2e/
    └── lyrics.spec.js    # Playwright UI tests
```

## Behavior notes

- When playback reaches the last line, it stops. Pressing Play again starts over from the first line.
- Pressing Previous or Next (or an arrow key) during playback pauses it, so the timer doesn't skip past the line you chose.
- To change the speed choices, edit the buttons in the `.segmented` group in `index.html` (each has a `data-seconds` value). To change the default, edit `DEFAULT_SECONDS` at the top of `app.js`; it should match one of the buttons.
- `app.js` is loaded as an ES module (`<script type="module">`), which is another reason the page needs to be served over HTTP.
