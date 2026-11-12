// Pure lyrics-player logic with no DOM access, so it can be tested in Node.

/** Split lyrics text into non-empty, trimmed lines. */
export function parseLyrics(text) {
  return text.split(/\r?\n/).map(line => line.trim()).filter(Boolean);
}

/**
 * Map a keyboard event to a player action: 'toggle', 'next', 'prev' or null.
 * Modifier combos are ignored so browser shortcuts (e.g. Cmd+←) keep working.
 */
export function keyAction({ key, metaKey = false, ctrlKey = false, altKey = false }) {
  if (metaKey || ctrlKey || altKey) return null;
  switch (key) {
    case ' ':
      return 'toggle';
    case 'ArrowRight':
    case 'ArrowDown':
      return 'next';
    case 'ArrowLeft':
    case 'ArrowUp':
      return 'prev';
    default:
      return null;
  }
}

/** Everything the page needs to display for a given position. */
export function describe(lines, index, playing = false) {
  const total = lines.length;
  const pad = n => String(n).padStart(2, '0');
  const percent = ((index + 1) / total) * 100;
  return {
    current: lines[index],
    previous: index > 0 ? lines[index - 1] : '',
    next: index < total - 1 ? lines[index + 1] : '',
    lineNumber: `LINE ${pad(index + 1)} / ${pad(total)}`,
    counter: `Line ${index + 1} of ${total}`,
    percent,
    atStart: index === 0,
    atEnd: index === total - 1,
    playing,
  };
}

/**
 * Create a player over `lines`.
 *
 * - next/prev/goTo move one position and stop playback.
 * - play advances one line every `delayMs`, stopping on the last line;
 *   playing from the last line restarts at the first.
 * - setDelay applies immediately, even mid-playback.
 *
 * `onChange(state)` is called after every change of position or play state.
 */
export function createPlayer(lines, { delayMs = 2000, onChange = () => {} } = {}) {
  if (!Array.isArray(lines) || lines.length === 0) {
    throw new Error('createPlayer needs at least one line');
  }

  let index = 0;
  let delay = delayMs;
  let timer = null;
  const last = lines.length - 1;

  const state = () => ({ index, playing: timer !== null, delayMs: delay });
  const notify = () => onChange(state());

  function clearTimer() {
    clearInterval(timer);
    timer = null;
  }

  function startTimer() {
    clearInterval(timer);
    timer = setInterval(() => {
      if (index < last) index++;
      if (index === last) clearTimer();
      notify();
    }, delay);
  }

  function goTo(target) {
    if (!Number.isInteger(target) || target < 0 || target > last) return false;
    clearTimer();
    index = target;
    notify();
    return true;
  }

  function play() {
    if (timer !== null) return;
    if (index === last) index = 0;
    startTimer();
    notify();
  }

  function pause() {
    if (timer === null) return;
    clearTimer();
    notify();
  }

  return {
    get state() { return state(); },
    goTo,
    next: () => goTo(index + 1),
    prev: () => goTo(index - 1),
    play,
    pause,
    toggle: () => (timer !== null ? pause() : play()),
    setDelay(ms) {
      if (!(ms > 0)) throw new RangeError('delay must be a positive number of milliseconds');
      delay = ms;
      if (timer !== null) startTimer();
      notify();
    },
    destroy: clearTimer,
  };
}
