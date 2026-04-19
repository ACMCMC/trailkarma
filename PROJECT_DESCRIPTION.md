> **The trail is full of people who know exactly what you need to know. You just have no way to reach them.**

## What is TrailKarma

TrailKarma is a community hiking app where you log hazards, water sources, and wildlife sightings without signal, share them phone-to-phone over Bluetooth with hikers you pass, and earn KARMA tokens and badges on Solana once you're back online.

## Inspiration

Our teammate Aldan was hiking the PCT from the Mexican border when everything went wrong on day 3. He hiked 40km solo, nearly stepped on a rattlesnake, and his safety app locked him out because there was no signal - and then sent an emergency alert to his family right when he'd just told them he'd seen a snake. To fix it, he detoured 2km looking for internet. No luck. He ended up running into some hikers from day 1 who lent him their emergency satellite SMS.

He gave up the PCT after that. But the thing that stuck with him wasn't the distance or the snake. It was that those hikers from day 1 had information he needed, and there was simply no way to get it to him. The campground that wasn't where the map said. The hazards ahead. All of that was sitting in other people's phones with no way to share it. And nobody had any reason to log it either, because nobody gets anything for it.

## How TrailKarma Works

**Offline-first:**
Everything works without signal. You log hazards (rockfall, washed-out bridges), water sources, and wildlife sightings to a local database on your phone. Your GPS location is sampled continuously and snapped to trail segments. Nothing goes to the cloud yet.

**BLE Mesh:**
This is the part we're most proud of. When you pass another hiker with TrailKarma installed, your phones discover each other over Bluetooth Low Energy and exchange missing reports automatically, you don't do anything. So if someone logged a washed-out bridge 5 miles back, you'll have that info beofre you even get signal, because they passed three other hikers who relayed it forward. That's the mesh.

**Cloud Sync:**
When you reach a trailhead and connect to the internet, the sync worker pushes everything to Databricks using idempotent `MERGE INTO` operations, so duplicates are handled automatically. On the backend, H3 hexagonal cells (Resolution 9) are computed for every report and GPS ping, which lets us run spatial queries in constant time. Hazard heatmaps, water density maps, species clusters, all from indexed H3 cells, not full table scans.

**Rewards:**
The app generates a Solana wallet on your phone and keeps it local. When you come back online and the backend verifies your contributions, it sponsors the Solana transaction on your behalf, you don't need any SOL and you never see a private key or a gas fee. KARMA tokens land in your wallet, and achievement badges get minted as non-transferable Token-2022 assets.  We use the blockchain for uniqueness and ownership only, not for storing hiking data. The real-world events stay off-chain.

**Species Detection:**
The app can run on-device audio classification to detect likely species from sounds, paired with your location. You can also attach photos to the same observation for later review and verification. Verified contributions earn a KARMA bonus and feed a dataset that conservation researchers can actually access.

## The Full Stack

* **Android:** Kotlin, Jetpack Compose, Room DB, WorkManager, CameraX, Bluetooth Low Energy.
* **BLE Mesh:** Persistent foreground service with GATT server/client, exponential backoff, and graceful peer drop handling.
* **Backend:** TypeScript rewards/relay service plus a Python biodiversity API.
* **Solana Program:** Anchor. User profiles, contribution receipts, relay jobs, badge mints, and the KARMA fungible token.
* **Databricks:** Delta Lake with `MERGE INTO` for idempotent sync, native H3 spatial indexing, and Z-ordered tables for fast spatial queries.

## Challenges We Ran Into

* **BLE Reliability:** Getting two phones to discover each other and exchange data without draining the battery was harder than expected. We went through a lot of iterations of the GATT protocol, backoff timings, and connection pooling before it felt stable.
* **H3 at Scale:** Computing H3 cells on-device for every GPS ping is too slow. We push that to Databricks and use Z-ordering so range queries stay fast.
* **Android 15:** The 16KB memory page alignment requirement broke our early builds. We had to upgrade the Android camera stack and rethink how we bundle native libraries.
* **Offline-first correctness:** It's easy to make an app that works online and "also supports offline mode." It's much harder to design one where offline is the real state and sync is the exception. The idempotent schema took a few redesigns to get right.
* **Reward deduplication:** Without on-chain uniqueness, you could claim the same report twice. Solana's PDAs solve this: one PDA per contribution, one per badge. The backend verifies before submitting, and the chain rejects duplicates automatically.

## Accomplishments We're Proud Of

* **The BLE mesh actually works:** We can put two phones on separate networks, walk them past each other, and watch the reports sync without touching either screen. This is the core of the app and it works.
* **Integrated core stack:** The main Android, Databricks, Solana Devnet, and backend flows are connected end-to-end. The separate web demo still uses mock data, but the mobile demo path is real.
* **Smart use of Solana:** We didn't try to put hiking on-chain. We used the blockchain for the one thing it's actually better at than a database: proving that an event happened exactly once, and nobody can change that. KARMA tokens and badges are on Devnet and verifiable right now.
* **H3 in production:** We have real trail data indexed with H3, and heatmaps update in real time as new reports come in. You can see clusters of hazards and water sources with no query taking more than a fraction of a second.

## What We Learned

The hardest design decision was figuring out what goes where. At first we wanted to log everything on-chain, then we wanted to sync everything to Databricks immediately. Both were wrong. The right answer was: local first, BLE second, cloud third, chain last, each layer doing only what it's actually good at.

We were also surprised by how much the visibility of a reward matters. A private point score nobody can see is basically worthless. A public badge that proves you documented a species nobody had reported on that trail section, that's something people actually care about.

## What's Next for TrailKarma

The first thing is to expand beyond the PCT and see if the KARMA incentive actually changes behavior. Does it make people log more reports? Do they explore harder terrain to find new species? We genuinely don't know yet.

Beyond that, the biggest remaining implementation steps are:
- **Biodiversity verification:** turn the current audio + photo capture flow into a stronger review, verification, and reward pipeline.
- **Researcher export:** get the biodiversity dataset into a form that conservation partners can actually consume.
- **Field validation:** harden the multi-phone relay flow with more real-world BLE and carrier testing.
- **Wearables:** integrate heart rate and SpO2 to predict altitude sickness before it hits, based on actual physiology instead of just elevation.
- **iOS:** the architecture is portable. If there's enough interest, a native iOS version makes sense.

> **The trail is a community. TrailKarma is what happens when you give that community the tools to help itself, and make sure people actually get credit for it.**

---

## Research & Inspiration

Some numbers that motivated this project:

- About 3,000 people die on US trails every year from preventable causes, many from hazards that other hikers had already seen but had no reason to formally report.
- Citizen science participation drops sharply when there's no feedback loop. Badges and KARMA create that loop.
- Most blockchain projects fail because they try to on-board non-crypto users into a crypto-native experience. We didn't. Users never see a private key or a gas fee.
