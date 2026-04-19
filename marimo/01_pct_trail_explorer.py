import marimo

__generated_with = "0.23.1"
app = marimo.App(width="wide", app_title="PCT Trail Explorer")


@app.cell
def _():
    import marimo as mo
    return (mo,)


@app.cell
def _():
    import json
    import os
    import pandas as pd
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import plotly.graph_objects as go
    return go, json, os, pd, plt


@app.cell
def _(json, os):
    _data_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "data")

    with open(os.path.join(_data_dir, "GeoJSON", "GeoJSON", "Southern_California.geojson"), encoding="utf-8") as _f:
        _geojson = json.load(_f)

    sections = {}
    for _feat in _geojson["features"]:
        _name = _feat["properties"]["Section_Name"]
        _coords = _feat["geometry"]["coordinates"]
        if _name not in sections:
            sections[_name] = {"coords": [], "region": _feat["properties"].get("Region", "SoCal")}
        sections[_name]["coords"].extend(_coords)

    data_dir = _data_dir
    return data_dir, sections


@app.cell
def _(data_dir, os, pd):
    water_df = pd.read_csv(os.path.join(data_dir, "water_reports.csv"))
    food_df  = pd.read_csv(os.path.join(data_dir, "food_reports.csv"))
    return food_df, water_df


@app.cell
def _(mo, sections):
    section_picker = mo.ui.dropdown(
        options=["All Sections"] + sorted(sections.keys()),
        value="All Sections",
        label="PCT Section",
    )
    show_water = mo.ui.switch(value=True, label="💧 Water Sources")
    show_food  = mo.ui.switch(value=True, label="🍎 Food Stops")
    basemap = mo.ui.dropdown(
        options={
            "Light":  "carto-positron",
            "Dark":   "carto-darkmatter",
            "Street": "open-street-map",
        },
        value="Light",
        label="Basemap",
    )
    return basemap, section_picker, show_food, show_water


@app.cell
def _(mo, section_picker, show_water, show_food, basemap):
    mo.hstack([section_picker, show_water, show_food, basemap], gap=4, justify="start")
    return


@app.cell
def _(go, food_df, section_picker, sections, show_food, show_water, water_df, basemap):
    _COLORS = {
        "CA Section A": "#e74c3c",
        "CA Section B": "#e67e22",
        "CA Section C": "#f39c12",
        "CA Section D": "#27ae60",
        "CA Section E": "#2980b9",
        "CA Section F": "#8e44ad",
    }
    _selected = section_picker.value

    _all_coords = []
    for _s in sections.values():
        _all_coords.extend(_s["coords"])
    _focus = sections[_selected]["coords"] if _selected != "All Sections" else _all_coords
    _mid = _focus[len(_focus) // 2]

    _fig = go.Figure()

    for _name, _data in sections.items():
        _active = (_selected == "All Sections" or _selected == _name)
        _c = _data["coords"][::20]
        _fig.add_trace(go.Scattermapbox(
            lat=[p[1] for p in _c],
            lon=[p[0] for p in _c],
            mode="lines",
            name=_name,
            line=dict(color=_COLORS.get(_name, "#888"), width=5 if _active else 2),
            opacity=1.0 if _active else 0.25,
            hovertemplate=f"<b>{_name}</b><extra></extra>",
        ))

    if show_water.value:
        _fig.add_trace(go.Scattermapbox(
            lat=water_df["lat"],
            lon=water_df["lng"],
            mode="markers",
            name="Water Sources",
            marker=dict(size=10, color="#3498db", opacity=0.85),
            text=water_df["title"],
            hovertemplate="<b>%{text}</b><br>%{lat:.4f}, %{lon:.4f}<extra></extra>",
        ))

    if show_food.value:
        _fig.add_trace(go.Scattermapbox(
            lat=food_df["lat"],
            lon=food_df["lng"],
            mode="markers",
            name="Food Stops",
            marker=dict(size=10, color="#e74c3c", opacity=0.85),
            text=food_df["title"],
            hovertemplate="<b>%{text}</b><br>%{lat:.4f}, %{lon:.4f}<extra></extra>",
        ))

    _fig.update_layout(
        mapbox=dict(
            style=basemap.value,
            center=dict(lat=_mid[1], lon=_mid[0]),
            zoom=7 if _selected == "All Sections" else 9,
        ),
        margin=dict(l=0, r=0, t=0, b=0),
        height=560,
        legend=dict(bgcolor="rgba(255,255,255,0.85)", bordercolor="#ccc", borderwidth=1, x=0.01, y=0.99),
    )

    trail_map = _fig
    return (trail_map,)


@app.cell
def _(plt, sections):
    def _haversine(c1, c2):
        R = 3958.8
        lat1, lon1 = map(float, [c1[1], c1[0]])
        lat2, lon2 = map(float, [c2[1], c2[0]])
        dlat = (lat2 - lat1) * 3.14159 / 180
        dlon = (lon2 - lon1) * 3.14159 / 180
        a = (dlat/2)**2 + (dlon/2)**2
        return R * 2 * (a**0.5)

    _lengths = {}
    for _n in sorted(sections.keys()):
        _c = sections[_n]["coords"]
        _lengths[_n] = round(sum(_haversine(_c[i], _c[i+1]) for i in range(len(_c)-1)), 1)

    _fig, _ax = plt.subplots(figsize=(5, 3))
    _bars = _ax.barh(list(_lengths.keys()), list(_lengths.values()),
                     color=["#e74c3c","#e67e22","#f39c12","#27ae60","#2980b9","#8e44ad"])
    _ax.set_xlabel("Miles")
    _ax.set_title("Section Lengths")
    for _bar, _v in zip(_bars, _lengths.values()):
        _ax.text(_v + 1, _bar.get_y() + _bar.get_height()/2, f"{_v} mi", va="center", fontsize=8)
    _ax.set_xlim(0, max(_lengths.values()) * 1.15)
    plt.tight_layout()
    length_chart = _fig
    return (length_chart,)


@app.cell
def _(food_df, length_chart, mo, trail_map, water_df):
    _stats = mo.hstack([
        mo.stat(label="Trail Sections", value="6",              caption="A through F"),
        mo.stat(label="Water Sources",  value=str(len(water_df)), caption="Geocoded"),
        mo.stat(label="Food Stops",     value=str(len(food_df)),  caption="Resupply points"),
    ], gap=2)

    mo.vstack([
        mo.md("# 🏔️ Pacific Crest Trail — Southern California Explorer"),
        _stats,
        mo.hstack([
            trail_map,
            mo.mpl.interactive(length_chart),
        ], gap=2),
    ])
    return


if __name__ == "__main__":
    app.run()
