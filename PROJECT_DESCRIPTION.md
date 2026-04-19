> **What if the trail itself could carry messages, warnings, and useful knowledge forward, even when nobody has signal?**

## Inspiration

TrailKarma starts from a very simple frustration. Hiking apps are built as if internet access is normal, but on real trails, that is exactly when it disappears.

One of our teammates, Aldan, was hiking the PCT from the Mexican border when things started to go wrong very fast. On day 3, he hiked around 40 km alone, almost stepped on a rattlesnake, and then his safety app locked him out because there was no signal. Later, the same app sent an emergency alert to his family right after he had told them he had seen a snake, which only made the situation more stressful.

He ended up making a 2 km detour just to try to get internet. There was still nothing. In the end, some hikers he had met on day 1 helped him with their satellite SMS.

What stayed with us was not only that one bad hike. It was the bigger problem behind it. Other hikers already had useful information, but there was no system to move that information forward on the trail. A bad campsite, a dry water source, a dangerous section, a wildlife sighting, all of that could already exist in somebody else’s phone and still be useless to the next person.

So we asked ourselves:

> **Why do trails still behave like disconnected individuals when, in practice, they already work like communities?**

That is the idea behind TrailKarma. We wanted to build something that treats the trail like a real social system, where people help each other, information moves forward, and those contributions actually matter.

## What TrailKarma Does

TrailKarma is an offline-first hiking app. Hikers can log hazards, water sources, wildlife sightings, biodiversity observations, and delayed relay messages even with no signal. Phones exchange that information over Bluetooth when hikers pass each other, and later, when somebody reaches connectivity again, the data syncs to the cloud.

On top of that, we add a public reward layer. Verified contributions can earn KARMA and badges on Solana, but we are careful about where the chain is used. We do not push normal hiking behavior on-chain. We use Solana for the part where it actually helps: uniqueness, ownership, and settlement.

So the app is not “blockchain for hiking.” It is a hiking app first, and the blockchain is there to support the trust layer behind the scenes.

## How TrailKarma Works

We designed TrailKarma as a stack of layers, and each layer has one job.

### 1. Offline-first Android app

The Android app is the source of truth for what happens in the field. Reports, biodiversity observations, relay jobs, location history, and wallet state all live locally on the phone in Room. If there is no signal, the app still works normally.

Hikers can log:

- hazards, like rockfall or damaged bridges
- water sources and trail conditions
- wildlife and biodiversity observations
- delayed relay messages for later delivery

This part matters a lot. Many apps say they “support offline mode,” but they are still designed around the internet. We wanted the opposite. Offline is the default state, and sync is what happens later.

### 2. BLE mesh relay

This is one of the parts we are most proud of. When two hikers with TrailKarma pass each other, their phones can exchange missing reports and relay packets over BLE. That means useful information does not have to wait for one specific person to reach signal. The community can physically carry it forward.

So, for example, if somebody reports a washed-out bridge a few miles back, that report can hop from phone to phone and reach another hiker before either of them gets internet.

This changes the model completely. Instead of “I only know what I personally saw,” the trail starts behaving like a delay-tolerant network.

### 3. Cloud sync and trail intelligence

When connectivity comes back, the app syncs reports, relay metadata, trails, biodiversity events, and location history to Databricks. We use idempotent `MERGE INTO` operations so repeated syncs do not create duplicate records.

Databricks also handles the heavier geospatial and analytics work. It computes H3 cells for reports and GPS points, stores shared trail data, mirrors biodiversity events, and serves cloud context back into the app. That gives us hazard clusters, water density, biodiversity hotspots, and seeded trail intelligence without forcing the phone to do expensive cloud-style processing.

In other words, the phone captures the field reality, and Databricks turns that into a usable shared data layer.

### 4. Rewards and settlement

Each user gets a local Solana wallet generated on-device. When a contribution is verified, the backend sponsors the transaction and settles the reward on Solana Devnet. Users do not need to buy SOL, manage gas, or understand crypto at all.

We use Solana for:

- KARMA issuance
- badge ownership
- contribution receipts
- relay-job uniqueness
- first-fulfiller settlement
- tipping

This is important because it gives us a public reward system without turning the whole app into a crypto-native experience. Solana is doing the trust and ownership work in the background, which is where it is actually useful.

### 5. Biodiversity intelligence

TrailKarma also has a biodiversity side because trails are not only transportation routes, they are ecosystems.

The app can record a short environmental audio clip and run on-device inference to suggest likely species. A user can also attach a photo and type the species they believe is present. Then Gemini checks whether that visual claim looks correct. If the observation is verified, it can create a report, contribute to rewards, and be mirrored to Databricks for later analysis.

So this becomes both a safety layer and a citizen-science layer. A wildlife report can help the next hiker, and it can also become useful ecological data later.

## Sponsors and How We Used Them

One thing we cared about in this project is that the sponsor technologies are not random decorations. Each one is tied to a real part of the system.

### Databricks

Databricks is one of the core pillars of TrailKarma. We use it as the cloud backbone for syncing reports, locations, relay packets, biodiversity events, and trail metadata. The Android app pushes unsynced field data into Databricks SQL, and Databricks handles the heavier cloud-side work that would be inefficient on-device.

That includes H3-based spatial indexing, biodiversity mirroring, seeded environmental data, and warehouse-backed retrieval of shared trail context. In practice, Databricks is what lets TrailKarma move from isolated offline events on a phone to a real community intelligence system across the trail.

### Solana

Solana powers the public trust layer behind TrailKarma’s reward system. We use it for KARMA issuance, badge ownership, contribution receipts, relay-job uniqueness, first-fulfiller settlement, and tipping. Every user gets a local wallet, but the backend sponsors the transactions so hikers never need to acquire SOL or think about gas.

This makes the reward system feel seamless in the app while still giving us the benefits of verifiable ownership and anti-duplication. Solana is not there as a buzzword, it is the reason the reward layer is public, portable, and hard to game.

### ElevenLabs

ElevenLabs powers our delayed voice relay system, which is probably the most emotionally intuitive part of the app. A hiker can create a signed relay intent offline, that intent can travel phone-to-phone over BLE, and once any carrier device reaches connectivity, the backend can trigger an outbound voice call to deliver the message and capture a short reply.

That means ElevenLabs is not being used for novelty narration. It is part of a real communication workflow for trails, where messages, callback context, and replies all move through a low-connectivity system. It turns a delayed offline intent into something that actually feels human and usable.

### Google AI / Gemini

We use Gemini as the verification layer for species-photo submissions. A user can take or upload a trail photo, type the species they think is present, and Gemini checks whether the image actually supports that claim. The result then feeds into biodiversity reporting, rewards, and collectible logic.

This gives the app a practical reasoning layer inside the biodiversity pipeline. Instead of treating every photo equally, we can use Gemini to improve data quality and make the reward system smarter.

### Qualcomm Edge AI

Our biodiversity audio classification runs directly on the Android device through a bundled TFLite model pack. The phone records a short environmental clip, runs inference locally, stores the result with location context, and syncs later when connectivity returns.

That is exactly why the edge-AI track fits us so well. The intelligence lives where the user actually needs it, out on trail, often with weak or no signal. This is not cloud inference hidden behind a mobile UI, it is real local inference in the field.

### Marimo and Sphinx

Marimo is part of the ML workflow behind TrailKarma. The repo includes a real Marimo notebook, `data_pipeline.py`, which prepares biodiversity data, queries observations, downloads labeled images, and builds an Impulse-ready training manifest. We also used Marimo notebooks for exploratory analysis, snake-risk modeling, and risk-zone visualization.

Sphinx fits the documentation side of that same workflow. We built the project with a strong docs layer around the training path, the biodiversity pipeline, the rewards architecture, and the Databricks sync flow. So Marimo and Sphinx are part of how we make the project reproducible, explainable, and easier to share beyond the demo itself.

### Impulse AI

Impulse AI is part of our model-training and deployment path. We prepared the biodiversity dataset in an Impulse-ready format, with manifests and labeled assets that can go through an Impulse training workflow and come back as compact artifacts suitable for Android deployment.

That makes Impulse part of the path from ecological observations to on-device intelligence. It helps connect the training pipeline to what the hiker actually sees in the app.

### NVIDIA Brev.dev

We used NVIDIA Brev.dev in the heavier training and export workflow for the biodiversity models. That includes environment setup, training-time work, embedding and classifier export, and packaging model artifacts for Android deployment.

So Brev is part of the production path for the mobile model pack. It supports the step where raw training assets become something we can actually ship to the phone.

### [X] Data / Scripps-style data use

We use the provided species and observation data as part of the biodiversity and trail-safety layer. It helps seed cloud data, enrich species reporting, support model preparation, and ground the system in real ecological context instead of synthetic examples.

More concretely, this data contributes to biodiversity analytics, report seeding, and safety-aware species context, including snakes and other relevant wildlife that hikers may actually encounter.

### The Basement

This is one of our strongest overall tracks because the idea itself is unusual but very coherent. TrailKarma combines offline trail reporting, BLE-based phone-to-phone data carriage, delayed voice relay, biodiversity logging, on-device intelligence, and public social rewards in one system.

The result is not just a hiking app with extra features. It is closer to a community infrastructure layer for trails. Helpful behavior happens offline, the community carries information forward, and useful contributions become visible and rewardable later.

### DigitalOcean

DigitalOcean fits the deployment story of TrailKarma. The release build already expects stable public API endpoints, and the system splits cleanly into a TypeScript rewards backend, a Python biodiversity backend, and optional web-facing surfaces.

That gives us a clean hosted architecture story and a project structure that can scale beyond a local hackathon demo.

## The Technical Stack

- **Android app:** Kotlin, Jetpack Compose, Room, WorkManager, CameraX, and BLE
- **Backend:** TypeScript rewards and relay service, plus Python biodiversity API
- **On-device ML:** TFLite biodiversity inference on Android
- **Cloud and analytics:** Databricks with H3 indexing and cloud sync
- **On-chain layer:** Anchor-based Solana program for KARMA, badges, and relay settlement

## Challenges We Ran Into

- **BLE reliability:** Making two phones discover each other, connect, and exchange useful data without draining battery took a lot of iteration.
- **Offline-first correctness:** It is easy to say an app supports offline mode. It is much harder to make offline the real default state and sync the secondary step.
- **Spatial indexing at the right layer:** We did not want to do expensive geospatial work on-device, so we pushed H3 computation into Databricks.
- **Reward deduplication:** Without a real uniqueness layer, the same contribution could be claimed more than once. That is one of the reasons the Solana layer matters so much.
- **Android build issues:** The Android 15 memory-page changes broke some early assumptions, so we had to revisit parts of the mobile stack.

## Accomplishments We’re Proud Of

- **The BLE mesh actually works:** We can move reports and relay packets between phones without internet, which is the heart of the whole project.
- **The stack is integrated end to end:** The Android app, the backend, Databricks, the biodiversity pipeline, and the Solana reward flow all connect in one real system.
- **The voice relay is not just flashy, it is useful:** It solves an actual trail communication problem in a way that feels natural.
- **The biodiversity layer does double duty:** It helps hikers in the moment, and it also creates structured ecological data for later analysis.
- **The architecture is disciplined:** Each layer does one job, and because of that, the whole thing feels more real and less like a hackathon collage.

## What We Learned

The hardest part was not adding more technology. It was deciding where each technology should stop.

At first, it was tempting to push too much onto Solana. Then it was tempting to make the cloud handle everything. Both ideas looked good on paper, but they were wrong for the real use case. The better answer was much simpler: phone first, nearby hikers second, cloud third, chain last.

We also learned that incentives matter more than we expected. A hidden point counter is not very interesting. But a visible reward, a real badge, or proof that you helped another hiker, that changes how people think about contributing.

## What’s Next

The next steps are pretty clear:

- strengthen biodiversity verification and review
- improve researcher-facing data export
- test BLE relay in more realistic multi-phone field conditions
- expand beyond the current trail scope
- explore wearables for altitude and safety signals
- eventually add iOS if the project keeps growing

## Final Positioning

If we had to explain TrailKarma in one line, it would be this:

> **We turned the trail into a delay-tolerant social network, where useful acts, like reporting hazards, carrying messages, and contributing biodiversity data, can still travel forward and still get rewarded even when signal does not.**

That is the whole point of the project. The trail already works like a community. We just wanted to finally give that community the technology to act like one.
