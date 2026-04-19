import marimo

__generated_with = "0.10.14"
app = marimo.App()


@app.cell
def __():
    import requests
    import pandas as pd
    import os
    from pathlib import Path
    from typing import List
    import json

    return requests, pd, os, Path, List, json


@app.cell
def __(md):
    md("""
    # TrailKarma PCT Species Dataset Pipeline

    This notebook pulls wildlife species observations from iNaturalist API,
    filtering for the Pacific Crest Trail (PCT) region, and prepares them
    for training an edge AI model on Impulse AI Platform.

    **Challenge coverage:** Best Use of Marimo/Sphinx, Best Models Trained on Impulse AI Platform
    """)
    return


@app.cell
def __(md):
    md("""
    ## 1. Define PCT Bounding Box & Target Species
    """)
    return


@app.cell
def __():
    # Pacific Crest Trail approximate bounding box
    # Runs ~2,130 miles from Mexico (CA) to Canada (WA)
    PCT_BOUNDS = {
        "north": 49.0,    # Canadian border
        "south": 32.6,    # Mexican border (Campo, CA)
        "west": -124.8,   # Pacific coast
        "east": -115.5    # Eastern edge (near NV/CA border)
    }

    # High-priority PCT species for safety & sightings
    TARGET_SPECIES = [
        "Crotalus",           # Rattlesnakes
        "Ursus americanus",   # Black bear
        "Puma concolor",      # Mountain lion
        "Canis lupus",        # Gray wolf (rare but notable)
        "Vulpes vulpes",      # Red fox
        "Ovis canadensis",    # Bighorn sheep
        "Cervus canadensis",  # Elk
        "Odocoileus",         # Mule/white-tailed deer
        "Antilocapra americana", # Pronghorn
        "Gymnorhinus cyanocephalus", # Pinyon jay
        "Aquila chrysaetos",  # Golden eagle
        "Pica nuttallii",     # Yellow-billed magpie
    ]

    return PCT_BOUNDS, TARGET_SPECIES


@app.cell
def __(md):
    md("""
    ## 2. Pull iNaturalist Observations
    """)
    return


@app.cell
def __(requests, PCT_BOUNDS, TARGET_SPECIES, pd):
    """
    Query iNaturalist API for observations matching target species in PCT bounds.
    API docs: https://www.inaturalist.org/pages/developers/index
    """

    def fetch_inaturalist_observations(species_list: list, bounds: dict, limit: int = 5000) -> pd.DataFrame:
        """
        Fetch iNaturalist observations for given species in geographic bounds.
        """
        base_url = "https://api.inaturalist.org/v1/observations"

        all_obs = []

        for species in species_list:
            params = {
                "q": species,
                "place_id": None,  # We'll use bounds instead
                "nelat": bounds["north"],
                "nelng": bounds["east"],
                "swlat": bounds["south"],
                "swlng": bounds["west"],
                "has": ["photos"],  # Only observations with photos
                "quality_grade": "research",  # Only research-grade (verified)
                "per_page": 200,
                "order": "desc",
                "order_by": "updated_at"
            }

            try:
                print(f"Fetching {species}...")
                page = 1
                species_count = 0

                while species_count < limit:
                    params["page"] = page
                    resp = requests.get(base_url, params=params, timeout=10)
                    resp.raise_for_status()

                    data = resp.json()
                    results = data.get("results", [])

                    if not results:
                        break

                    for obs in results:
                        if species_count >= limit:
                            break

                        # Extract relevant fields
                        photos = obs.get("photos", [])
                        if photos:
                            all_obs.append({
                                "observation_id": obs["id"],
                                "species": species,
                                "scientific_name": obs.get("species_guess", ""),
                                "lat": obs["geom"]["coordinates"][1],
                                "lng": obs["geom"]["coordinates"][0],
                                "photo_url": photos[0]["url"],  # First photo
                                "date_observed": obs.get("observed_on", ""),
                                "quality_grade": obs.get("quality_grade", "")
                            })
                            species_count += 1

                    page += 1

                print(f"  → {species_count} observations")

            except Exception as e:
                print(f"  Error fetching {species}: {e}")

        return pd.DataFrame(all_obs)

    print("Fetching iNaturalist observations for PCT species...")
    observations_df = fetch_inaturalist_observations(TARGET_SPECIES, PCT_BOUNDS, limit=100)
    print(f"\nTotal observations: {len(observations_df)}")
    observations_df.head()

    return fetch_inaturalist_observations, observations_df


@app.cell
def __(md):
    md("""
    ## 3. Download Images & Create Training Dataset
    """)
    return


@app.cell
def __(requests, os, Path):
    def download_images(df: pd.DataFrame, output_dir: str = "./pct_species_dataset") -> Path:
        """
        Download images from iNaturalist URLs and organize by species.
        Returns path to dataset directory.
        """
        dataset_path = Path(output_dir)
        dataset_path.mkdir(exist_ok=True)

        # Create class directories
        for species in df["species"].unique():
            (dataset_path / species).mkdir(exist_ok=True)

        downloaded = []

        for idx, row in df.iterrows():
            species = row["species"]
            obs_id = row["observation_id"]
            photo_url = row["photo_url"]

            # Build filename: species_obsid.jpg
            filename = f"{species}_{obs_id}.jpg"
            filepath = dataset_path / species / filename

            if filepath.exists():
                downloaded.append(filepath)
                continue

            try:
                resp = requests.get(photo_url, timeout=10)
                resp.raise_for_status()

                with open(filepath, "wb") as f:
                    f.write(resp.content)

                downloaded.append(filepath)

                if (idx + 1) % 10 == 0:
                    print(f"Downloaded {idx + 1} images...")

            except Exception as e:
                print(f"Failed to download {photo_url}: {e}")

        print(f"\nDownloaded {len(downloaded)} images to {dataset_path}")
        return dataset_path

    return download_images


@app.cell
def __(observations_df, download_images):
    # Download images
    dataset_path = download_images(observations_df, "./pct_species_dataset")


@app.cell
def __(md):
    md("""
    ## 4. Create CSV Manifest for Impulse AI Upload
    """)
    return


@app.cell
def __(observations_df, Path):
    """
    Create a CSV file that Impulse AI can ingest for dataset upload.
    Impulse expects: filepath, label
    """

    manifest_rows = []
    dataset_path = Path("./pct_species_dataset")

    for idx, row in observations_df.iterrows():
        species = row["species"]
        obs_id = row["observation_id"]
        filename = f"{species}_{obs_id}.jpg"
        filepath = dataset_path / species / filename

        # Check if file exists
        if filepath.exists():
            manifest_rows.append({
                "filepath": str(filepath),
                "label": species,
                "scientific_name": row["scientific_name"],
                "latitude": row["lat"],
                "longitude": row["lng"]
            })

    manifest_df = pd.DataFrame(manifest_rows)

    # Save manifest CSV
    manifest_df.to_csv("pct_species_manifest.csv", index=False)

    print(f"Created manifest: pct_species_manifest.csv")
    print(f"Total images: {len(manifest_df)}")
    print(f"Species classes: {manifest_df['label'].nunique()}")
    print(f"\nClasses distribution:")
    print(manifest_df['label'].value_counts())


@app.cell
def __(md):
    md("""
    ## 5. Split Train/Test and Upload Instructions

    Next steps:
    1. Upload `pct_species_manifest.csv` and `./pct_species_dataset/` to Impulse AI
    2. Create a classification impulse with image input (640x480 or 224x224)
    3. Use Impulse's AutoML to train the model
    4. Export as TensorFlow Lite (.tflite) for Android
    5. Add the .tflite file to Android app assets/

    **Impulse AI Benefits:**
    - Automated data augmentation
    - On-device optimization (small model size)
    - Easy TFLite export
    - Edge deployment ready
    """)
    return


if __name__ == "__main__":
    app.run()
