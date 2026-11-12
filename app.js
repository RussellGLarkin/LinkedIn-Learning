const speedInput = document.getElementById('speed');
const speedValue = document.getElementById('speed-value');

// The slider runs Slow → Fast, but we store a delay, so invert it:
// with min 0.5 and max 5, slider value v gives a delay of (5.5 - v) seconds.
function delayFromSlider() {
  const min = Number(speedInput.min);
  const max = Number(speedInput.max);
  return (min + max - Number(speedInput.value)) * 1000;
}

let delayMs = delayFromSlider();

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
  }, delayMs);
}

function updateSpeed() {
  delayMs = delayFromSlider();
  const seconds = (delayMs / 1000).toFixed(1);
  speedValue.textContent = `${seconds} s / line`;
  speedInput.setAttribute('aria-valuetext', `${seconds} seconds per line`);
  // Apply the new speed immediately if we're playing.
  if (timer) {
    stop();
    play();
  }
}

speedInput.addEventListener('input', updateSpeed);
updateSpeed();

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
  // Let the speed slider keep its own arrow-key handling when it has focus.
  if (e.target === speedInput && e.key.startsWith('Arrow')) return;
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
