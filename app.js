const PLAY_DELAY_MS = 2000;

const lineEl = document.getElementById('line');
const lineNumberEl = document.getElementById('line-number');
const counterEl = document.getElementById('counter');
const progressEl = document.querySelector('.progress');
const progressFill = document.getElementById('progress-fill');
const prevBtn = document.getElementById('prev');
const nextBtn = document.getElementById('next');
const playBtn = document.getElementById('play');

let lines = [];
let index = 0;
let timer = null;

function render() {
  const total = lines.length;
  const pct = ((index + 1) / total) * 100;

  lineEl.textContent = lines[index];
  lineEl.classList.remove('fade-in');
  void lineEl.offsetWidth; // restart the fade animation
  lineEl.classList.add('fade-in');

  lineNumberEl.textContent = `LINE ${String(index + 1).padStart(2, '0')}`;
  counterEl.textContent = `Line ${index + 1} of ${total}`;
  progressFill.style.width = `${pct}%`;
  progressEl.setAttribute('aria-valuenow', Math.round(pct));

  prevBtn.disabled = index === 0;
  nextBtn.disabled = index === total - 1;
  playBtn.textContent = timer ? '❚❚ Pause' : '▶ Play';
}

function stop() {
  clearInterval(timer);
  timer = null;
}

function play() {
  // Restart from the top if we're already on the last line.
  if (index === lines.length - 1) index = 0;
  timer = setInterval(() => {
    if (index < lines.length - 1) index++;
    if (index === lines.length - 1) stop();
    render();
  }, PLAY_DELAY_MS);
}

function togglePlay() {
  timer ? stop() : play();
  render();
}

function go(delta) {
  const target = index + delta;
  if (target < 0 || target >= lines.length) return;
  stop();
  index = target;
  render();
}

prevBtn.addEventListener('click', () => go(-1));
nextBtn.addEventListener('click', () => go(1));
playBtn.addEventListener('click', togglePlay);

document.addEventListener('keydown', e => {
  if (!lines.length || e.metaKey || e.ctrlKey || e.altKey) return;
  switch (e.key) {
    case ' ':
      // preventDefault stops page scroll and stops a focused button from also firing.
      e.preventDefault();
      if (!e.repeat) togglePlay();
      break;
    case 'ArrowRight':
    case 'ArrowDown':
      e.preventDefault();
      go(1);
      break;
    case 'ArrowLeft':
    case 'ArrowUp':
      e.preventDefault();
      go(-1);
      break;
  }
});
// Some browsers activate a focused button on Space keyup; block that so Space toggles only once.
document.addEventListener('keyup', e => {
  if (e.key === ' ') e.preventDefault();
});

fetch('lyrics.txt')
  .then(res => {
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return res.text();
  })
  .then(text => {
    lines = text.split(/\r?\n/).map(l => l.trim()).filter(Boolean);
    if (lines.length === 0) {
      lineEl.textContent = 'lyrics.txt is empty.';
      return;
    }
    playBtn.disabled = false;
    render();
  })
  .catch(err => {
    lineEl.textContent = 'Could not load lyrics.txt — serve this folder over HTTP (e.g. python3 -m http.server).';
    console.error(err);
  });
