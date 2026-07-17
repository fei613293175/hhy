/// <reference types="vite/client" />

import { describe, expect, it } from 'vitest';
import html from '../index.html?raw';

describe('admin HTML entry', () => {
  it('declares UTF-8 before rendering Chinese UI content', () => {
    expect(html).toMatch(/<meta charset="UTF-8"\s*\/>/);
    expect(html.indexOf('charset="UTF-8"')).toBeLessThan(html.indexOf('<title>'));
  });

  it('uses the physical-device viewport for responsive layouts', () => {
    expect(html).toContain('name="viewport" content="width=device-width, initial-scale=1.0"');
  });
});
