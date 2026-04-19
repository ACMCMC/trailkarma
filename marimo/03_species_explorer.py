import marimo

__generated_with = "0.23.1"
app = marimo.App(width="wide", app_title="Species Distribution Explorer")


@app.cell
def _():
    import marimo as mo
    return (mo,)


@app.cell
def _():
    import os
    import pandas as pd
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import matplotlib.cm as cm
    import plotly.graph_objects as go
    return cm, go, os, pd, plt


@app.cell
def _(os, pd):
    import json
    _data_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "data")

    with open(os.path.join(_data_dir, "GeoJSON", "GeoJSON", "Southern_California.geojson"), encoding="utf-8") as _f:
        _geojson = json.load(_f)
    _trail_coords = []
    for _feat in _geojson["features"]:
        _seg = _feat["geometry"]["coordinates"][::10]  # subsample per feature
        if not _seg:
            continue
        if _trail_coords:
            _trail_coords.append(None)  # break between features
        _trail_coords.extend(_seg)

    trail_coords = _trail_coords
    _df = pd.read_csv(os.path.join(_data_dir, "observations-712152.csv", "observations-712152.csv"), low_memory=False)

    _df = _df.dropna(subset=["latitude", "longitude"])
    _df["latitude"]  = pd.to_numeric(_df["latitude"],  errors="coerce")
    _df["longitude"] = pd.to_numeric(_df["longitude"], errors="coerce")
    _df = _df[
        (_df["latitude"]  >= 32.0) & (_df["latitude"]  <= 35.5) &
        (_df["longitude"] >= -118.5) & (_df["longitude"] <= -116.0)
    ].copy()

    _time_col = "time_observed_at" if "time_observed_at" in _df.columns else "observed_on"
    _df["observed_on"] = pd.to_datetime(_df[_time_col], errors="coerce", utc=True)
    _df["month"] = _df["observed_on"].dt.month.fillna(0).astype(int)
    _df["species_label"] = _df["common_name"].fillna(_df["species_guess"]).fillna("Unknown")

    species_df = _df.reset_index(drop=True)
    return species_df, trail_coords


@app.cell
def _(mo, species_df):
    _top = species_df["species_label"].value_counts().head(30).index.tolist()
    species_picker = mo.ui.dropdown(
        options=["All Species"] + _top,
        value="All Species",
        label="Species",
    )
    month_range = mo.ui.range_slider(
        start=1, stop=12, step=1, value=[1, 12],
        label="Month range",
        show_value=True,
    )
    return month_range, species_picker


@app.cell
def _(mo, species_picker, month_range):
    mo.hstack([species_picker, month_range], gap=4, justify="start")
    return


@app.cell
def _(species_df, species_picker, month_range):
    _m_min, _m_max = month_range.value
    _filtered = species_df[
        (species_df["month"] >= _m_min) & (species_df["month"] <= _m_max)
    ]
    if species_picker.value != "All Species":
        _filtered = _filtered[_filtered["species_label"] == species_picker.value]

    filtered_df = _filtered.reset_index(drop=True)
    return (filtered_df,)


@app.cell
def _(go, filtered_df, trail_coords, cm):
    _sample = filtered_df.head(500)
    _species_list = _sample["species_label"].unique().tolist()
    _colormap = cm.get_cmap("tab20", max(len(_species_list), 1))
    _color_map = {s: "#{:02x}{:02x}{:02x}".format(
        int(_colormap(i)[0]*255), int(_colormap(i)[1]*255), int(_colormap(i)[2]*255)
    ) for i, s in enumerate(_species_list)}

    _fig = go.Figure()

    # PCT trail line
    _fig.add_trace(go.Scattermapbox(
        lat=[c[1] if c is not None else None for c in trail_coords],
        lon=[c[0] if c is not None else None for c in trail_coords],
        mode="lines",
        line=dict(color="#2c3e50", width=2),
        name="PCT Trail",
        hovertemplate="PCT Trail<extra></extra>",
        showlegend=True,
    ))

    for _sp in _species_list:
        _sub = _sample[_sample["species_label"] == _sp]
        _fig.add_trace(go.Scattermapbox(
            lat=_sub["latitude"],
            lon=_sub["longitude"],
            mode="markers",
            marker=dict(size=7, color=_color_map[_sp], opacity=0.75),
            name=_sp,
            hovertemplate=f"<b>{_sp}</b><br>%{{lat:.4f}}, %{{lon:.4f}}<extra></extra>",
        ))

    _fig.update_layout(
        mapbox=dict(style="carto-positron", center=dict(lat=33.5, lon=-117.0), zoom=8),
        margin=dict(l=0, r=0, t=0, b=0),
        height=500,
        legend=dict(bgcolor="rgba(255,255,255,0.85)", bordercolor="#ccc", borderwidth=1),
    )

    species_map = _fig
    return (species_map,)


@app.cell
def _(filtered_df, plt):
    _fig, (_ax1, _ax2) = plt.subplots(1, 2, figsize=(9, 3.5))

    _top10 = filtered_df["species_label"].value_counts().head(10)
    _ax1.barh(_top10.index[::-1], _top10.values[::-1], color="#2980b9")
    _ax1.set_xlabel("Observations")
    _ax1.set_title("Top 10 Species")
    for _i, _v in enumerate(_top10.values[::-1]):
        _ax1.text(_v + 0.3, _i, str(_v), va="center", fontsize=8)

    _months = filtered_df["month"].value_counts().sort_index()
    _month_names = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
    _ax2.bar(
        [_month_names[m-1] for m in _months.index if 1 <= m <= 12],
        [_months[m] for m in _months.index if 1 <= m <= 12],
        color="#27ae60",
    )
    _ax2.set_xlabel("Month")
    _ax2.set_ylabel("Observations")
    _ax2.set_title("Observations by Month")
    plt.xticks(rotation=45)

    plt.tight_layout()
    species_chart = _fig
    return (species_chart,)


@app.cell
def _(mo, filtered_df, species_map, species_chart, species_df):
    mo.vstack([
        mo.md("# 🦎 Species Distribution Explorer — SoCal PCT Corridor"),
        mo.hstack([
            mo.stat(label="Total Observations", value=str(len(species_df)), caption="SoCal filtered"),
            mo.stat(label="Showing",            value=str(len(filtered_df)), caption="Current filter"),
            mo.stat(label="Unique Species",     value=str(filtered_df["species_label"].nunique()), caption="In selection"),
        ], gap=2),
        mo.hstack([
            species_map,
            mo.mpl.interactive(species_chart),
        ], gap=2),
        mo.callout(
            mo.md(f"Showing first 500 points on map for performance. Total matching: **{len(filtered_df)}**"),
            kind="info",
        ),
    ])
    return


if __name__ == "__main__":
    app.run()
