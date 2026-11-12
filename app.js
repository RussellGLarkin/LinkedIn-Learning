import { parseLyrics, keyAction, describe, createPlayer } from './player.js';

const DEFAULT_SECONDS = 2;

const lineEl = document.getElementById('line');
const prevLineEl = document.getElementById('prev-line');
const nextLineEl = document.getElementById('next-line');
const lineNumberEl = document.getElementById('line-number');
const counterEl = document.getElementById('counter');
const progressEl = document.querySelector('.progress');
const progressFill = document.getElementById('progress-fill');
const prevBtn = document.getElementById('prev');
const nextBtn = document.getElementById('next');
const playBtn = document.getElementById('play');
const playIcon = document.getElementById('play-icon');
const pauseIcon = document.getElementById('pause-icon');
const speedButtons = document.querySelectorAll('.segmented button');

function showSpeed(seconds) {
  speedButtons.forEach(btn => {
    btn.setAttribute('aria-pressed', String(Number(btn.dataset.seconds) === seconds));
  });
}

function start(lines) {
  let shownIndex = -1;

  function render({ index, playing }) {
    const view = describe(lines, index, playing);

    // Only re-run the fade when the line actually changes (not on play/pause).
    if (index !== shownIndex) {
      lineEl.textContent = view.current;
      lineEl.classList.remove('fade-in');
      void lineEl.offsetWidth; // restart the fade animation
      lineEl.classList.add('fade-in');
      shownIndex = index;
    }

    prevLineEl.textContent = view.previous;
    nextLineEl.textContent = view.next;
    lineNumberEl.textContent = view.lineNumber;
    counterEl.textContent = view.counter;
    progressFill.style.width = `${view.percent}%`;
    progressEl.setAttribute('aria-valuenow', Math.round(view.percent));

    prevBtn.disabled = view.atStart;
    nextBtn.disabled = view.atEnd;
    playBtn.setAttribute('aria-label', playing ? 'Pause' : 'Play');
    // SVG elements have no `hidden` property, so toggle the attribute (styled in styles.css).
    playIcon.toggleAttribute('hidden', playing);
    pauseIcon.toggleAttribute('hidden', !playing);
  }

  const player = createPlayer(lines, { delayMs: DEFAULT_SECONDS * 1000, onChange: render });

  prevBtn.addEventListener('click', player.prev);
  nextBtn.addEventListener('click', player.next);
  playBtn.addEventListener('click', player.toggle);
  speedButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const seconds = Number(btn.dataset.seconds);
      player.setDelay(seconds * 1000);
      showSpeed(seconds);
    });
  });

  document.addEventListener('keydown', e => {
    const action = keyAction(e);
    if (!action) return;
    // preventDefault stops page scroll and stops a focused button from also firing.
    e.preventDefault();
    if (action === 'toggle' && !e.repeat) player.toggle();
    if (action === 'next') player.next();
    if (action === 'prev') player.prev();
  });
  // Some browsers activate a focused button on Space keyup; block that so Space toggles only once.
  document.addEventListener('keyup', e => {
    if (e.key === ' ') e.preventDefault();
  });

  playBtn.disabled = false;
  render(player.state);
}

showSpeed(DEFAULT_SECONDS);

fetch('lyrics.txt')
  .then(res => {
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return res.text();
  })
  .then(text => {
    const lines = parseLyrics(text);
    if (lines.length === 0) {
      lineEl.textContent = 'lyrics.txt is empty.';
      return;
    }
    start(lines);
  })
  .catch(err => {
    lineEl.textContent = 'Could not load lyrics.txt — serve this folder over HTTP (e.g. python3 -m http.server).';
    console.error(err);
  });
