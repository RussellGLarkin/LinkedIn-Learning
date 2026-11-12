# Lyrics Display

A small web app that shows song lyrics one line at a time. You can step through the lines by hand or let it play automatically, advancing every 2 seconds.

It's plain HTML, CSS and JavaScript, with no build step and no dependencies.

## Features

- Shows one line at a time, with its line number and a "Line 5 of 14" counter
- A progress bar showing how far through the song you are
- **Previous** and **Next** buttons, greyed out at the first and last line
- A **Play** button that advances every 2 seconds and switches to **Pause** while playing
- Keyboard shortcuts:

  | Key | Action |
  | --- | --- |
  | <kbd>Space</kbd> | Play / pause |
  | <kbd>→</kbd> or <kbd>↓</kbd> | Next line |
  | <kbd>←</kbd> or <kbd>↑</kbd> | Previous line |

- Follows your system's light or dark setting, and works on phone-sized screens

## Running it

The page loads `lyrics.txt` with `fetch`, and browsers block that when you open the page straight from disk (`file://`). Serve the folder over HTTP instead:

```bash
python3 -m http.server 8000
```

Then open <http://localhost:8000>.

Any other static file server works too, for example `npx serve`.

## Using your own lyrics

Replace the contents of `lyrics.txt` with your song, one line per line of the file. Blank lines are ignored, so you can leave gaps between verses without getting empty screens.

## Project structure

```
.
├── index.html   # Page markup
├── styles.css   # Styling, including the light/dark themes
├── app.js       # Loads the lyrics, handles playback, buttons and keyboard shortcuts
└── lyrics.txt   # The lyrics to display
```

## Behavior notes

- When playback reaches the last line, it stops. Pressing Play again starts over from the first line.
- Pressing Previous or Next (or an arrow key) during playback pauses it, so the timer doesn't skip past the line you chose.
- To change the playback speed, edit `PLAY_DELAY_MS` at the top of `app.js`.
