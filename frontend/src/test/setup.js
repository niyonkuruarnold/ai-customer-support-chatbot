/**
 * Vitest global setup — loaded before every test file.
 *
 * Prevents the Node 20 undici / CacheStorage global collision that causes:
 *   TypeError: webidl.util.markAsUncloneable is not a function
 *
 * Both jsdom and happy-dom can trigger this when they initialise their
 * internal fetch/Request/Response polyfills against an already-patched
 * globalThis.caches.  We guard with a typeof check so this is safe even
 * when the globals already exist (e.g. future Node versions that ship
 * a stable CacheStorage).
 */

/* eslint-disable prefer-const */

if (typeof globalThis.CacheStorage === 'undefined') {
  // Minimal stub so polyfills don't explode when they try to check
  // whether the native CacheStorage API is available.
  globalThis.CacheStorage = class CacheStorage {
    /* istanbul ignore next */
    open() {
      return Promise.resolve({
        match() { return Promise.resolve(undefined); },
        add() { return Promise.resolve(); },
        put() { return Promise.resolve(); },
        delete() { return Promise.resolve(true); },
        keys() { return Promise.resolve([]); },
      });
    }
  };
}

if (typeof globalThis.caches === 'undefined') {
  Object.defineProperty(globalThis, 'caches', {
    value: new globalThis.CacheStorage(),
    configurable: true,
    writable: true,
  });
}
