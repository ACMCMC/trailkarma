import marimo

__generated_with = "0.23.1"
app = marimo.App(width="wide", app_title="Water Source Validator")


@app.cell
def _():
    import marimo as mo
    return (mo,)


@app.cell
def _():
    import json
    import math
    import os
    import pandas as pd
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import plotly.graph_objects as go
    return go, json, math, os, pd, plt


@app.cell
def _(json, os):
    _data_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "data")

    with open(os.path.join(_data_dir, "GeoJSON", "GeoJSON", "Southern_California.geojson"), encoding="utf-8") as _f:
        _geojson = json.load(_f)

    _trail_coords = []
    for _feat in _geojson["features"]:
        _seg = _feat["geometry"]["coordinates"][::10]
        if not _seg:
            continue
        if _trail_coords:
            _trail_coords.append(None)
        _trail_coords.extend(_seg)

    trail_coords = _trail_coords
    data_dir = _data_dir
    return data_dir, trail_coords


@app.cell
def _(os, pd, data_dir):
    water_df = pd.read_csv(os.path.join(data_dir, "water_reports.csv"))
    return (water_df,)


@app.cell
def _(math, trail_coords, water_df):
    def _haversine_km(lat1, lng1, lat2, lng2):
        R = 6371.0
        dlat = math.radians(lat2 - lat1)
        dlng = math.radians(lng2 - lng1)
        a = math.sin(dlat/2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlng/2)**2
        return R * 2 * math.asin(math.sqrt(a))

    def _min_dist_to_trail(lat, lng):
        return min(
            _haversine_km(lat, lng, c[1], c[0])
            for c in trail_coords[::10] if c is not None
        )

    _distances = [_min_dist_to_trail(r["lat"], r["lng"]) for _, r in water_df.iterrows()]
    water_with_dist = water_df.copy()
    water_with_dist["dist_km"] = [round(d, 2) for d in _distances]
    water_with_dist["dist_mi"] = [round(d * 0.621371, 2) for d in _distances]
    return (water_with_dist,)


@app.cell
def _(mo, water_with_dist):
    max_dist = mo.ui.slider(
        start=0.5, stop=50.0, step=0.5, value=20.0,
        label="Max distance from PCT trail (km)",
        show_value=True,
    )
    return (max_dist,)


@app.cell
def _(mo, max_dist):
    mo.vstack([
        mo.md("## ⚙️ Filter Settings"),
        max_dist,
    ])
    return


@app.cell
def _(go, trail_coords, water_with_dist, max_dist):
    _threshold = max_dist.value
    _fig = go.Figure()

    # PCT trail line (None = segment break, keep as-is for plotly, filter for lat/lon)
    _fig.add_trace(go.Scattermapbox(
        lat=[c[1] if c is not None else None for c in trail_coords],
        lon=[c[0] if c is not None else None for c in trail_coords],
        mode="lines",
        line=dict(color="#2c3e50", width=2),
        name="PCT Trail",
        hovertemplate="PCT Trail<extra></extra>",
    ))

    # Accepted water sources
    _acc = water_with_dist[water_with_dist["dist_km"] <= _threshold]
    _rej = water_with_dist[water_with_dist["dist_km"] >  _threshold]

    _fig.add_trace(go.Scattermapbox(
        lat=_acc["lat"], lon=_acc["lng"],
        mode="markers",
        marker=dict(size=10, color="#27ae60"),
        name="Accepted",
        text=_acc["title"],
        customdata=_acc["dist_km"],
        hovertemplate="<b>%{text}</b><br>%{customdata} km from trail<extra></extra>",
    ))
    _fig.add_trace(go.Scattermapbox(
        lat=_rej["lat"], lon=_rej["lng"],
        mode="markers",
        marker=dict(size=10, color="#e74c3c"),
        name="Rejected",
        text=_rej["title"],
        customdata=_rej["dist_km"],
        hovertemplate="<b>%{text}</b><br>%{customdata} km from trail<extra></extra>",
    ))

    _fig.update_layout(
        mapbox=dict(style="carto-positron", center=dict(lat=33.2, lon=-116.8), zoom=8),
        margin=dict(l=0, r=0, t=0, b=0),
        height=500,
        legend=dict(bgcolor="rgba(255,255,255,0.85)", bordercolor="#ccc", borderwidth=1),
    )

    validator_map = _fig
    accepted_count = len(_acc)
    rejected_count = len(_rej)
    return accepted_count, rejected_count, validator_map


@app.cell
def _(water_with_dist, max_dist, plt):
    _threshold = max_dist.value
    _fig, (_ax1, _ax2) = plt.subplots(1, 2, figsize=(8, 3))

    _dists = water_with_dist["dist_km"].tolist()
    _ax1.hist(_dists, bins=15, color="#3498db", edgecolor="white")
    _ax1.axvline(_threshold, color="#e74c3c", linestyle="--", linewidth=2, label=f"Threshold: {_threshold} km")
    _ax1.set_xlabel("Distance to PCT (km)")
    _ax1.set_ylabel("Count")
    _ax1.set_title("Water Source Distance Distribution")
    _ax1.legend()

    _ok = (water_with_dist["dist_km"] <= _threshold).sum()
    _no = len(water_with_dist) - _ok
    _ax2.pie([_ok, _no], labels=["Accepted", "Rejected"],
             colors=["#27ae60", "#e74c3c"], autopct="%1.0f%%",
             startangle=90, wedgeprops={"edgecolor": "white"})
    _ax2.set_title("Validation Result")

    plt.tight_layout()
    dist_chart = _fig
    return (dist_chart,)


@app.cell
def _(mo, water_with_dist, max_dist):
    _threshold = max_dist.value
    _filtered = water_with_dist[water_with_dist["dist_km"] <= _threshold][
        ["title", "lat", "lng", "dist_km", "dist_mi"]
    ].sort_values("dist_km")
    water_table = mo.ui.table(_filtered, label="Accepted Water Sources")
    return (water_table,)


@app.cell
def _(mo, validator_map, dist_chart, accepted_count, rejected_count, water_table):
    mo.vstack([
        mo.md("# 💧 Water Source Geocoding Validator"),
        mo.md("*Validates that geocoded water sources fall within a reasonable distance of the PCT trail.*"),
        mo.hstack([
            mo.stat(label="Accepted", value=str(accepted_count), caption="Within threshold"),
            mo.stat(label="Rejected", value=str(rejected_count), caption="Too far from trail"),
            mo.stat(label="Total",    value=str(accepted_count + rejected_count), caption="Water sources"),
        ], gap=2),
        mo.hstack([
            validator_map,
            mo.mpl.interactive(dist_chart),
        ], gap=2),
        mo.md("### Accepted Sources"),
        water_table,
    ])
    return


if __name__ == "__main__":
    app.run()
