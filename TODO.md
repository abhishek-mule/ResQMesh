# TODO

- [ ] Add an SSR-safe guard for Leaflet/DOM usage in `dashboard/src/components/GpsMap.tsx` to prevent `ReferenceError: window is not defined`.
- [ ] Ensure dynamic import uses `ssr:false` (already present in `dashboard/src/app/page.tsx`).
- [ ] Re-run dashboard build/start to confirm the runtime error is gone.

