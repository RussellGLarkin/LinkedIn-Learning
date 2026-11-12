import { test, expect } from '@playwright/test';

const FIRST = "Shall I compare thee to a summer's day?";
const SECOND = 'Thou art more lovely and more temperate:';
const THIRD = 'Rough winds do shake the darling buds of May,';
const LAST = 'So long lives this, and this gives life to thee.';
const SECOND_LAST = 'So long as men can breathe, or eyes can see,';
const TOTAL = 14;

/** Locators for the parts of the page the tests look at. */
function ui(page) {
  return {
    line: page.locator('#line'),
    prevLine: page.locator('#prev-line'),
    nextLine: page.locator('#next-line'),
    lineNumber: page.locator('#line-number'),
    counter: page.locator('#counter'),
    progress: page.getByRole('progressbar', { name: 'Song progress' }),
    prev: page.getByRole('button', { name: 'Previous line' }),
    next: page.getByRole('button', { name: 'Next line' }),
    play: page.locator('#play'),
    speed: seconds => page.getByRole('button', { name: new RegExp(`^${seconds} seconds? per line$`) }),
    keyHints: page.locator('.keys'),
  };
}

/** Serve custom lyrics instead of lyrics.txt. */
async function useLyrics(page, body, status = 200) {
  await page.route('**/lyrics.txt', route => route.fulfill({ status, body, contentType: 'text/plain' }));
}

async function expectPlaying(page) {
  const { play } = ui(page);
  await expect(play).toHaveAttribute('aria-label', 'Pause');
  await expect(page.locator('#pause-icon')).toBeVisible();
  await expect(page.locator('#play-icon')).toBeHidden();
}

async function expectPaused(page) {
  const { play } = ui(page);
  await expect(play).toHaveAttribute('aria-label', 'Play');
  await expect(page.locator('#play-icon')).toBeVisible();
  await expect(page.locator('#pause-icon')).toBeHidden();
}

/** Click Next `n` times. */
async function clickNext(page, n) {
  for (let i = 0; i < n; i++) await ui(page).next.click();
}

const START = new Date('2026-01-01T12:00:00Z');

test.beforeEach(async ({ page }) => {
  // Fake timers so 2-second playback steps run instantly and deterministically.
  await page.clock.install({ time: START });
});

/**
 * Load a page, then pause the fake clock so time only moves when a test calls
 * page.clock.runFor(). Without the pause, real time leaks in between steps.
 */
async function open(page, path = '/') {
  await page.goto(path);
  await page.clock.pauseAt(new Date(START.getTime() + 60_000));
}

test.describe('loading', () => {
  test('shows the first line with its position', async ({ page }) => {
    await open(page);
    const u = ui(page);

    await expect(page).toHaveTitle('Lyrics Display');
    await expect(u.line).toHaveText(FIRST);
    await expect(u.lineNumber).toHaveText(`LINE 01 / ${TOTAL}`);
    await expect(u.counter).toHaveText(`Line 1 of ${TOTAL}`);
    await expect(u.progress).toHaveAttribute('aria-valuenow', '7');
  });

  test('shows the next line below and nothing above on the first line', async ({ page }) => {
    await open(page);
    const u = ui(page);

    await expect(u.prevLine).toHaveText('');
    await expect(u.nextLine).toHaveText(SECOND);
  });

  test('enables the right controls on the first line', async ({ page }) => {
    await open(page);
    const u = ui(page);

    await expect(u.prev).toBeDisabled();
    await expect(u.next).toBeEnabled();
    await expect(u.play).toBeEnabled();
    await expectPaused(page);
  });

  test('explains how to fix it when lyrics.txt cannot be loaded', async ({ page }) => {
    await useLyrics(page, 'Not found', 404);
    await open(page);
    const u = ui(page);

    await expect(u.line).toContainText('Could not load lyrics.txt');
    await expect(u.line).toContainText('python3 -m http.server');
    await expect(u.play).toBeDisabled();
    await expect(u.prev).toBeDisabled();
    await expect(u.next).toBeDisabled();
  });

  test('says so when lyrics.txt is empty', async ({ page }) => {
    await useLyrics(page, '\n  \n\n');
    await open(page);
    const u = ui(page);

    await expect(u.line).toHaveText('lyrics.txt is empty.');
    await expect(u.play).toBeDisabled();
  });

  test('skips blank lines between verses', async ({ page }) => {
    await useLyrics(page, 'Verse one\n\n\nVerse two\r\n\r\n  Verse three  \n');
    await open(page);
    const u = ui(page);

    await expect(u.lineNumber).toHaveText('LINE 01 / 03');
    await u.next.click();
    await expect(u.line).toHaveText('Verse two');
    await u.next.click();
    await expect(u.line).toHaveText('Verse three');
    await expect(u.next).toBeDisabled();
  });

  test('handles a one-line song', async ({ page }) => {
    await useLyrics(page, 'Only line');
    await open(page);
    const u = ui(page);

    await expect(u.line).toHaveText('Only line');
    await expect(u.prev).toBeDisabled();
    await expect(u.next).toBeDisabled();
    await expect(u.progress).toHaveAttribute('aria-valuenow', '100');
  });

  test('shows lyrics as text, not HTML', async ({ page }) => {
    await useLyrics(page, '<b>bold</b> & <i>tags</i>');
    await open(page);

    await expect(ui(page).line).toHaveText('<b>bold</b> & <i>tags</i>');
    await expect(page.locator('#line b')).toHaveCount(0);
  });
});

test.describe('Previous and Next buttons', () => {
  test('Next moves forward and updates every readout', async ({ page }) => {
    await open(page);
    const u = ui(page);

    await u.next.click();

    await expect(u.line).toHaveText(SECOND);
    await expect(u.prevLine).toHaveText(FIRST);
    await expect(u.nextLine).toHaveText(THIRD);
    await expect(u.lineNumber).toHaveText(`LINE 02 / ${TOTAL}`);
    await expect(u.counter).toHaveText(`Line 2 of ${TOTAL}`);
    await expect(u.progress).toHaveAttribute('aria-valuenow', '14');
    await expect(u.prev).toBeEnabled();
  });

  test('Previous moves back', async ({ page }) => {
    await open(page);
    const u = ui(page);

    await clickNext(page, 2);
    await u.prev.click();

    await expect(u.line).toHaveText(SECOND);
    await expect(u.counter).toHaveText(`Line 2 of ${TOTAL}`);
  });

  test('Previous is disabled again after returning to the first line', async ({ page }) => {
    await open(page);
    const u = ui(page);

    await u.next.click();
    await u.prev.click();

    await expect(u.line).toHaveText(FIRST);
    await expect(u.prev).toBeDisabled();
  });

  test('the last line disables Next and fills the progress bar', async ({ page }) => {
    await open(page);
    const u = ui(page);

    await clickNext(page, TOTAL - 1);

    await expect(u.line).toHaveText(LAST);
    await expect(u.prevLine).toHaveText(SECOND_LAST);
    await expect(u.nextLine).toHaveText('');
    await expect(u.lineNumber).toHaveText(`LINE ${TOTAL} / ${TOTAL}`);
    await expect(u.next).toBeDisabled();
    await expect(u.progress).toHaveAttribute('aria-valuenow', '100');
    await expect(page.locator('#progress-fill')).toHaveAttribute('style', /width: 100%/);
  });
});

test.describe('playback', () => {
  test('Play switches to Pause', async ({ page }) => {
    await open(page);
    await ui(page).play.click();
    await expectPlaying(page);
  });

  test('advances one line every 2 seconds', async ({ page }) => {
    await open(page);
    const u = ui(page);
    await expect(u.line).toHaveText(FIRST);

    await u.play.click();
    await page.clock.runFor(1999);
    await expect(u.line).toHaveText(FIRST);

    await page.clock.runFor(1);
    await expect(u.line).toHaveText(SECOND);

    await page.clock.runFor(2000);
    await expect(u.line).toHaveText(THIRD);
    await expectPlaying(page);
  });

  test('stops on the last line and switches back to Play', async ({ page }) => {
    await open(page);
    const u = ui(page);
    await expect(u.line).toHaveText(FIRST);

    await u.play.click();
    await page.clock.runFor(2000 * (TOTAL - 1));

    await expect(u.line).toHaveText(LAST);
    await expectPaused(page);

    await page.clock.runFor(10_000);
    await expect(u.line).toHaveText(LAST);
  });

  test('Play on the last line starts again from the top', async ({ page }) => {
    await open(page);
    const u = ui(page);

    await clickNext(page, TOTAL - 1);
    await u.play.click();

    await expect(u.line).toHaveText(FIRST);
    await expectPlaying(page);
    await page.clock.runFor(2000);
    await expect(u.line).toHaveText(SECOND);
  });

  test('Pause stops advancing', async ({ page }) => {
    await open(page);
    const u = ui(page);
    await expect(u.line).toHaveText(FIRST);

    await u.play.click();
    await page.clock.runFor(2000);
    await expect(u.line).toHaveText(SECOND);

    await u.play.click();
    await expectPaused(page);
    await page.clock.runFor(10_000);
    await expect(u.line).toHaveText(SECOND);
  });

  test('clicking Next during playback stops playback', async ({ page }) => {
    await open(page);
    const u = ui(page);
    await expect(u.line).toHaveText(FIRST);

    await u.play.click();
    await u.next.click();

    await expect(u.line).toHaveText(SECOND);
    await expectPaused(page);
    await page.clock.runFor(10_000);
    await expect(u.line).toHaveText(SECOND);
  });

  test('clicking Previous during playback stops playback', async ({ page }) => {
    await open(page);
    const u = ui(page);
    await expect(u.line).toHaveText(FIRST);

    await u.play.click();
    await page.clock.runFor(4000);
    await expect(u.line).toHaveText(THIRD);

    await u.prev.click();
    await expect(u.line).toHaveText(SECOND);
    await expectPaused(page);
  });
});

test.describe('speed', () => {
  test('2 seconds is selected by default', async ({ page }) => {
    await open(page);
    const u = ui(page);

    await expect(u.speed(2)).toHaveAttribute('aria-pressed', 'true');
    for (const s of [1, 3, 4]) {
      await expect(u.speed(s)).toHaveAttribute('aria-pressed', 'false');
    }
  });

  test('choosing a speed selects only that button', async ({ page }) => {
    await open(page);
    const u = ui(page);

    await u.speed(4).click();

    await expect(u.speed(4)).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator('.segmented button[aria-pressed="true"]')).toHaveCount(1);
  });

  for (const seconds of [1, 3, 4]) {
    test(`${seconds}s plays one line every ${seconds} seconds`, async ({ page }) => {
      await open(page);
      const u = ui(page);
      await expect(u.line).toHaveText(FIRST);

      await u.speed(seconds).click();
      await u.play.click();

      await page.clock.runFor(seconds * 1000 - 1);
      await expect(u.line).toHaveText(FIRST);
      await page.clock.runFor(1);
      await expect(u.line).toHaveText(SECOND);
    });
  }

  test('changing speed during playback takes effect immediately', async ({ page }) => {
    await open(page);
    const u = ui(page);
    await expect(u.line).toHaveText(FIRST);

    await u.play.click();
    await page.clock.runFor(1500);
    await u.speed(4).click();

    // The old 2-second step would have fired here; the new 4-second one has not yet.
    await page.clock.runFor(600);
    await expect(u.line).toHaveText(FIRST);
    await expectPlaying(page);

    await page.clock.runFor(3400);
    await expect(u.line).toHaveText(SECOND);
  });
});

test.describe('keyboard shortcuts', () => {
  test('Space plays and pauses', async ({ page }) => {
    await open(page);
    await expect(ui(page).line).toHaveText(FIRST);

    await page.keyboard.press('Space');
    await expectPlaying(page);
    await page.keyboard.press('Space');
    await expectPaused(page);
  });

  test('Right and Down arrows go to the next line', async ({ page }) => {
    await open(page);
    const u = ui(page);
    await expect(u.line).toHaveText(FIRST);

    await page.keyboard.press('ArrowRight');
    await expect(u.line).toHaveText(SECOND);
    await page.keyboard.press('ArrowDown');
    await expect(u.line).toHaveText(THIRD);
  });

  test('Left and Up arrows go to the previous line', async ({ page }) => {
    await open(page);
    const u = ui(page);
    await clickNext(page, 2);

    await page.keyboard.press('ArrowLeft');
    await expect(u.line).toHaveText(SECOND);
    await page.keyboard.press('ArrowUp');
    await expect(u.line).toHaveText(FIRST);
  });

  test('arrows stop at the first and last lines', async ({ page }) => {
    await open(page);
    const u = ui(page);
    await expect(u.line).toHaveText(FIRST);

    await page.keyboard.press('ArrowLeft');
    await expect(u.line).toHaveText(FIRST);

    for (let i = 0; i < TOTAL + 3; i++) await page.keyboard.press('ArrowRight');
    await expect(u.line).toHaveText(LAST);
  });

  test('an arrow key during playback stops playback', async ({ page }) => {
    await open(page);
    const u = ui(page);
    await expect(u.line).toHaveText(FIRST);

    await page.keyboard.press('Space');
    await page.keyboard.press('ArrowRight');

    await expect(u.line).toHaveText(SECOND);
    await expectPaused(page);
  });

  test('Space toggles only once when the Play button has focus', async ({ page }) => {
    await open(page);
    const u = ui(page);
    await expect(u.line).toHaveText(FIRST);

    await u.play.focus();
    await page.keyboard.press('Space');
    await expectPlaying(page);
  });

  test('Space on a focused Next button plays instead of moving', async ({ page }) => {
    await open(page);
    const u = ui(page);
    await expect(u.line).toHaveText(FIRST);

    await u.next.focus();
    await page.keyboard.press('Space');

    await expectPlaying(page);
    await expect(u.line).toHaveText(FIRST);
  });

  test('shortcuts with modifier keys are left to the browser', async ({ page }) => {
    await open(page);
    const u = ui(page);
    await expect(u.line).toHaveText(FIRST);

    await page.keyboard.press('Alt+ArrowRight');
    await page.keyboard.press('Control+ArrowRight');
    await expect(u.line).toHaveText(FIRST);
  });

  test('Tab reaches the controls in order', async ({ page, isMobile }) => {
    // Phones have no Tab key, and Safari/WebKit skips buttons on Tab by default.
    test.skip(isMobile, 'desktop keyboards only');
    await open(page);
    const u = ui(page);
    await u.next.click(); // enable Previous so it is focusable
    await page.locator('body').click({ position: { x: 1, y: 1 } });

    const expected = ['Previous line', 'Pause|Play', 'Next line', '1 second per line', '2 seconds per line'];
    for (const name of expected) {
      await page.keyboard.press('Tab');
      await expect(page.locator(':focus')).toHaveAttribute('aria-label', new RegExp(`^(${name})$`));
    }
  });
});

test.describe('layout', () => {
  test('has no horizontal scrolling', async ({ page }) => {
    await open(page);
    await expect(ui(page).line).toHaveText(FIRST);

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow).toBeLessThanOrEqual(0);
  });

  test('a very long line wraps instead of overflowing', async ({ page }) => {
    await useLyrics(page, 'word '.repeat(80).trim());
    await open(page);
    await expect(ui(page).line).toContainText('word word');

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow).toBeLessThanOrEqual(0);
  });

  test('all controls are visible without scrolling', async ({ page }) => {
    await open(page);
    const u = ui(page);
    await expect(u.line).toHaveText(FIRST);

    for (const control of [u.prev, u.play, u.next, u.speed(1), u.speed(4)]) {
      await expect(control).toBeInViewport();
    }
  });

  test('keyboard hints show on desktop and hide on phones', async ({ page, isMobile }) => {
    await open(page);
    if (isMobile) {
      await expect(ui(page).keyHints).toBeHidden();
    } else {
      await expect(ui(page).keyHints).toBeVisible();
    }
  });

  test('buttons are large enough to tap', async ({ page }) => {
    await open(page);
    const u = ui(page);
    await expect(u.line).toHaveText(FIRST);

    for (const control of [u.prev, u.play, u.next, u.speed(1)]) {
      const box = await control.boundingBox();
      expect(box.width).toBeGreaterThanOrEqual(36);
      expect(box.height).toBeGreaterThanOrEqual(36);
    }
  });
});
