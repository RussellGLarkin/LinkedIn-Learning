import { describe as suite, test, beforeEach, afterEach, mock } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parseLyrics, keyAction, describe, createPlayer, MAX_DELAY_MS } from '../player.js';

const LINES = ['one', 'two', 'three', 'four'];

suite('parseLyrics', () => {
  test('splits text into lines', () => {
    assert.deepEqual(parseLyrics('a\nb\nc'), ['a', 'b', 'c']);
  });

  test('handles Windows line endings', () => {
    assert.deepEqual(parseLyrics('a\r\nb\r\n'), ['a', 'b']);
  });

  test('trims whitespace and drops blank lines between verses', () => {
    assert.deepEqual(parseLyrics('  verse one  \n\n   \nverse two\n\n'), ['verse one', 'verse two']);
  });

  test('returns an empty list for empty or blank text', () => {
    assert.deepEqual(parseLyrics(''), []);
    assert.deepEqual(parseLyrics('\n  \n'), []);
  });

  test('parses the bundled lyrics.txt', async () => {
    const text = await readFile(new URL('../lyrics.txt', import.meta.url), 'utf8');
    const lines = parseLyrics(text);
    assert.equal(lines.length, 14);
    assert.equal(lines[0], "Shall I compare thee to a summer's day?");
    assert.equal(lines[13], 'So long lives this, and this gives life to thee.');
  });
});

suite('keyAction', () => {
  test('Space toggles playback', () => {
    assert.equal(keyAction({ key: ' ' }), 'toggle');
  });

  test('Right and Down go to the next line', () => {
    assert.equal(keyAction({ key: 'ArrowRight' }), 'next');
    assert.equal(keyAction({ key: 'ArrowDown' }), 'next');
  });

  test('Left and Up go to the previous line', () => {
    assert.equal(keyAction({ key: 'ArrowLeft' }), 'prev');
    assert.equal(keyAction({ key: 'ArrowUp' }), 'prev');
  });

  test('ignores keys combined with Cmd, Ctrl or Alt', () => {
    assert.equal(keyAction({ key: 'ArrowLeft', metaKey: true }), null);
    assert.equal(keyAction({ key: 'ArrowRight', ctrlKey: true }), null);
    assert.equal(keyAction({ key: ' ', altKey: true }), null);
  });

  test('ignores other keys', () => {
    assert.equal(keyAction({ key: 'a' }), null);
    assert.equal(keyAction({ key: 'Enter' }), null);
    assert.equal(keyAction({ key: 'Tab' }), null);
  });
});

suite('describe', () => {
  test('a middle line shows its neighbours, number and progress', () => {
    const view = describe(LINES, 1);
    assert.equal(view.current, 'two');
    assert.equal(view.previous, 'one');
    assert.equal(view.next, 'three');
    assert.equal(view.lineNumber, 'LINE 02 / 04');
    assert.equal(view.counter, 'Line 2 of 4');
    assert.equal(view.percent, 50);
    assert.equal(view.atStart, false);
    assert.equal(view.atEnd, false);
  });

  test('the first line has no previous line and is at the start', () => {
    const view = describe(LINES, 0);
    assert.equal(view.previous, '');
    assert.equal(view.atStart, true);
    assert.equal(view.percent, 25);
  });

  test('the last line has no next line, is at the end and is 100%', () => {
    const view = describe(LINES, 3);
    assert.equal(view.next, '');
    assert.equal(view.atEnd, true);
    assert.equal(view.percent, 100);
  });

  test('a one-line song is both at the start and at the end', () => {
    const view = describe(['only'], 0);
    assert.equal(view.atStart, true);
    assert.equal(view.atEnd, true);
    assert.equal(view.lineNumber, 'LINE 01 / 01');
  });

  test('line numbers are zero-padded but not truncated', () => {
    const many = Array.from({ length: 120 }, (_, i) => `line ${i + 1}`);
    assert.equal(describe(many, 4).lineNumber, 'LINE 05 / 120');
    assert.equal(describe(many, 4).counter, 'Line 5 of 120');
  });

  test('passes the playing flag through', () => {
    assert.equal(describe(LINES, 0, true).playing, true);
    assert.equal(describe(LINES, 0).playing, false);
  });
});

suite('createPlayer', () => {
  let player;

  beforeEach(() => {
    mock.timers.enable({ apis: ['setTimeout'] });
  });

  afterEach(() => {
    player?.destroy();
    player = undefined;
    mock.timers.reset();
  });

  test('rejects an empty song', () => {
    assert.throws(() => createPlayer([]), /at least one line/);
  });

  test('rejects lyrics that are not a list', () => {
    for (const bad of ['abc', null, undefined, { length: 2 }]) {
      assert.throws(() => createPlayer(bad), /at least one line/, `createPlayer(${JSON.stringify(bad)})`);
    }
  });

  test('rejects an invalid starting delay', () => {
    for (const bad of [0, -1, 1.5, NaN, Infinity, MAX_DELAY_MS + 1, '2000']) {
      assert.throws(() => createPlayer(LINES, { delayMs: bad }), RangeError, `delayMs: ${bad}`);
    }
  });

  test('starts on the first line, paused, at 2 seconds per line', () => {
    player = createPlayer(LINES);
    assert.deepEqual(player.state, { index: 0, playing: false, delayMs: 2000 });
  });

  suite('manual navigation', () => {
    test('next and prev move one line', () => {
      player = createPlayer(LINES);
      player.next();
      player.next();
      assert.equal(player.state.index, 2);
      player.prev();
      assert.equal(player.state.index, 1);
    });

    test('prev does nothing on the first line', () => {
      const onChange = mock.fn();
      player = createPlayer(LINES, { onChange });
      assert.equal(player.prev(), false);
      assert.equal(player.state.index, 0);
      assert.equal(onChange.mock.callCount(), 0);
    });

    test('next does nothing on the last line', () => {
      player = createPlayer(LINES);
      player.goTo(3);
      assert.equal(player.next(), false);
      assert.equal(player.state.index, 3);
    });

    test('goTo jumps to a valid line and ignores invalid ones', () => {
      player = createPlayer(LINES);
      assert.equal(player.goTo(2), true);
      assert.equal(player.state.index, 2);
      for (const bad of [-1, 4, 1.5, NaN, '1']) {
        assert.equal(player.goTo(bad), false, `goTo(${bad})`);
      }
      assert.equal(player.state.index, 2);
    });

    test('reports each change to onChange', () => {
      const onChange = mock.fn();
      player = createPlayer(LINES, { onChange });
      player.next();
      assert.equal(onChange.mock.callCount(), 1);
      assert.deepEqual(onChange.mock.calls[0].arguments[0], { index: 1, playing: false, delayMs: 2000 });
    });
  });

  suite('playback', () => {
    test('advances one line every 2 seconds', () => {
      player = createPlayer(LINES);
      player.play();
      assert.equal(player.state.playing, true);

      mock.timers.tick(1999);
      assert.equal(player.state.index, 0, 'not before the delay');
      mock.timers.tick(1);
      assert.equal(player.state.index, 1);
      mock.timers.tick(2000);
      assert.equal(player.state.index, 2);
    });

    test('stops on the last line', () => {
      player = createPlayer(LINES);
      player.play();
      // One step per tick: Node 20 mock timers skip timeouts scheduled during a tick.
      for (let step = 0; step < 3; step++) mock.timers.tick(2000);
      assert.deepEqual(player.state, { index: 3, playing: false, delayMs: 2000 });

      mock.timers.tick(10_000);
      assert.equal(player.state.index, 3, 'stays on the last line');
    });

    test('playing from the last line restarts at the first', () => {
      player = createPlayer(LINES);
      player.goTo(3);
      player.play();
      assert.equal(player.state.index, 0);
      assert.equal(player.state.playing, true);
    });

    test('pause stops advancing', () => {
      player = createPlayer(LINES);
      player.play();
      mock.timers.tick(2000);
      player.pause();
      mock.timers.tick(10_000);
      assert.deepEqual(player.state, { index: 1, playing: false, delayMs: 2000 });
    });

    test('toggle switches between play and pause', () => {
      player = createPlayer(LINES);
      player.toggle();
      assert.equal(player.state.playing, true);
      player.toggle();
      assert.equal(player.state.playing, false);
    });

    test('pressing play twice does not double the speed', () => {
      player = createPlayer(LINES);
      player.play();
      player.play();
      mock.timers.tick(2000);
      assert.equal(player.state.index, 1);
    });

    test('moving manually during playback stops playback', () => {
      player = createPlayer(LINES);
      player.play();
      player.next();
      assert.deepEqual(player.state, { index: 1, playing: false, delayMs: 2000 });
      mock.timers.tick(10_000);
      assert.equal(player.state.index, 1, 'the timer no longer advances');
    });

    test('a one-line song stops immediately after the first tick', () => {
      player = createPlayer(['only']);
      player.play();
      mock.timers.tick(2000);
      assert.deepEqual(player.state, { index: 0, playing: false, delayMs: 2000 });
    });
  });

  suite('speed', () => {
    test('uses the delay given when created', () => {
      player = createPlayer(LINES, { delayMs: 1000 });
      player.play();
      mock.timers.tick(1000);
      assert.equal(player.state.index, 1);
    });

    test('setDelay changes the speed for the next play', () => {
      player = createPlayer(LINES);
      player.setDelay(4000);
      player.play();
      mock.timers.tick(3999);
      assert.equal(player.state.index, 0);
      mock.timers.tick(1);
      assert.equal(player.state.index, 1);
    });

    test('setDelay applies immediately during playback', () => {
      player = createPlayer(LINES);
      player.play();
      mock.timers.tick(1500);
      player.setDelay(4000);
      // The old 2 s timer would have fired at t=2000; it must not.
      mock.timers.tick(600);
      assert.equal(player.state.index, 0, 'old 2 s timer was replaced');
      // The new 4 s timer fires 4 s after the change, at t=5500.
      mock.timers.tick(3400);
      assert.equal(player.state.index, 1, 'advanced on the new 4 s timer');
      assert.equal(player.state.playing, true);
    });

    test('speeding up during playback takes effect on the next line', () => {
      player = createPlayer(LINES);
      player.play();
      mock.timers.tick(1500);
      player.setDelay(1000);
      mock.timers.tick(1000);
      assert.equal(player.state.index, 1);
      mock.timers.tick(1000);
      assert.equal(player.state.index, 2, 'keeps the 1 s pace');
    });

    test('setDelay rejects zero, negative, fractional, non-numeric and too-large delays', () => {
      player = createPlayer(LINES);
      for (const bad of [0, -1000, 1.5, NaN, 'fast', '2000', Infinity, MAX_DELAY_MS + 1]) {
        assert.throws(() => player.setDelay(bad), RangeError, `setDelay(${bad})`);
      }
      assert.equal(player.state.delayMs, 2000);
    });

    test('setDelay accepts the smallest and largest valid delays', () => {
      player = createPlayer(LINES);
      player.setDelay(1);
      assert.equal(player.state.delayMs, 1);
      player.setDelay(MAX_DELAY_MS);
      assert.equal(player.state.delayMs, MAX_DELAY_MS);
    });

    test('a rejected delay leaves playback running at the old speed', () => {
      player = createPlayer(LINES);
      player.play();
      assert.throws(() => player.setDelay(Infinity), RangeError);
      mock.timers.tick(2000);
      assert.deepEqual(player.state, { index: 1, playing: true, delayMs: 2000 });
    });
  });

  suite('change notifications', () => {
    // The page redraws only when onChange fires, so each change must notify exactly once.
    function setup() {
      const onChange = mock.fn();
      player = createPlayer(LINES, { onChange });
      const calls = () => onChange.mock.calls.map(c => c.arguments[0]);
      return { onChange, calls };
    }

    test('nothing is reported on creation', () => {
      const { onChange } = setup();
      assert.equal(onChange.mock.callCount(), 0);
    });

    test('play reports that playback started', () => {
      const { calls } = setup();
      player.play();
      assert.deepEqual(calls(), [{ index: 0, playing: true, delayMs: 2000 }]);
    });

    test('play from the last line reports the restart at the first line', () => {
      const { onChange, calls } = setup();
      player.goTo(3);
      onChange.mock.resetCalls();
      player.play();
      assert.deepEqual(calls(), [{ index: 0, playing: true, delayMs: 2000 }]);
    });

    test('pause reports that playback stopped', () => {
      const { onChange, calls } = setup();
      player.play();
      onChange.mock.resetCalls();
      player.pause();
      assert.deepEqual(calls(), [{ index: 0, playing: false, delayMs: 2000 }]);
    });

    test('pause while already paused reports nothing', () => {
      const { onChange } = setup();
      player.pause();
      assert.equal(onChange.mock.callCount(), 0);
    });

    test('play while already playing reports nothing', () => {
      const { onChange } = setup();
      player.play();
      onChange.mock.resetCalls();
      player.play();
      assert.equal(onChange.mock.callCount(), 0);
    });

    test('each playback step reports the new line', () => {
      const { onChange, calls } = setup();
      player.play();
      onChange.mock.resetCalls();
      mock.timers.tick(2000);
      mock.timers.tick(2000);
      assert.deepEqual(calls(), [
        { index: 1, playing: true, delayMs: 2000 },
        { index: 2, playing: true, delayMs: 2000 },
      ]);
    });

    test('the final step reports the last line and that playback stopped, once', () => {
      const { onChange, calls } = setup();
      player.goTo(2);
      player.play();
      onChange.mock.resetCalls();
      mock.timers.tick(10_000);
      assert.deepEqual(calls(), [{ index: 3, playing: false, delayMs: 2000 }]);
    });

    test('setDelay reports the new delay, paused or playing', () => {
      const { onChange, calls } = setup();
      player.setDelay(3000);
      assert.deepEqual(calls(), [{ index: 0, playing: false, delayMs: 3000 }]);

      player.play();
      onChange.mock.resetCalls();
      player.setDelay(1000);
      assert.deepEqual(calls(), [{ index: 0, playing: true, delayMs: 1000 }]);
    });

    test('a rejected delay reports nothing', () => {
      const { onChange } = setup();
      assert.throws(() => player.setDelay(0), RangeError);
      assert.equal(onChange.mock.callCount(), 0);
    });

    test('moving during playback reports the new line and the stop together', () => {
      const { onChange, calls } = setup();
      player.play();
      onChange.mock.resetCalls();
      player.next();
      assert.deepEqual(calls(), [{ index: 1, playing: false, delayMs: 2000 }]);
    });
  });

  test('destroy stops the timer', () => {
    player = createPlayer(LINES);
    player.play();
    player.destroy();
    mock.timers.tick(10_000);
    assert.equal(player.state.index, 0);
    assert.equal(player.state.playing, false);
  });
});
