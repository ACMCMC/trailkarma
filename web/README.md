# Web Demo

The `web/` folder contains a lightweight React/Vite demo for TrailKarma. It is not the primary product surface and it is not currently wired to live backend data.

## Current Scope

The web app is a concept/demo layer that shows:

- a TrailKarma landing page
- a hiker-tracker view with mock hiker paths
- a trail-report map with mock hazard, species, water, info, and food markers
- a geocoded report-search experience using OpenStreetMap / Nominatim

Current entrypoints:

- [src/App.jsx](/Users/suraj/Desktop/dhacks/datahacks26/web/src/App.jsx)
- [src/pages/Home.jsx](/Users/suraj/Desktop/dhacks/datahacks26/web/src/pages/Home.jsx)
- [src/pages/Tracker.jsx](/Users/suraj/Desktop/dhacks/datahacks26/web/src/pages/Tracker.jsx)

## Important Limitation

The current web app uses hard-coded mock data.

That means:

- it does not read from Databricks
- it does not connect to the TypeScript rewards backend
- it does not display real Solana wallet state
- the Rewards card is present as a concept but not enabled

## Run Locally

```bash
cd /Users/suraj/Desktop/dhacks/datahacks26/web
npm install
npm run dev
```

Then open the local Vite URL shown in the terminal.

## Tech Stack

- React
- Vite
- React Leaflet
- OpenStreetMap tiles

## What To Build Next If The Web App Becomes Productized

- replace mock hikers and reports with live project data
- connect map state to Databricks-backed reports and trails
- expose real rewards, relay, and biodiversity views
- add authentication / user identity instead of local mock selection
