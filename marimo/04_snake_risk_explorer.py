import marimo

__generated_with = "0.23.1"
app = marimo.App(width="wide", app_title="Snake Risk Explorer")


@app.cell
def _():
    import marimo as mo

    return (mo,)


@app.cell
def _():
    import json
    import os
    import pickle
    import pandas as pd
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import plotly.graph_objects as go

    return go, json, os, pd, pickle, plt


@app.cell
def _(os, pickle):
    _data_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "data")
    with open(os.path.join(_data_dir, "snake_model.pkl"), "rb") as _f:
        model = pickle.load(_f)
    data_dir = _data_dir
    return data_dir, model


@app.cell
def _(data_dir, json, os):
    with open(os.path.join(data_dir, "GeoJSON", "GeoJSON", "Southern_California.geojson"), encoding="utf-8") as _f:
        _geojson = json.load(_f)

    sections = {}
    for _feat in _geojson["features"]:
        _name = _feat["properties"]["Section_Name"]
        _coords = _feat["geometry"]["coordinates"]
        if _name not in sections:
            sections[_name] = []
        sections[_name].extend(_coords)

    centroids = {
        name: {
            "lat": round(coords[len(coords)//2][1], 4),
            "lng": round(coords[len(coords)//2][0], 4),
        }
        for name, coords in sections.items()
    }
    return centroids, sections


@app.cell
def _(mo):
    mo.md("""
    # 🐍 Snake Encounter Risk Explorer
    """)
    return


@app.cell
def _(mo):
    temperature = mo.ui.slider(0, 50, step=0.5, value=22.0, label="Temperature (°C)", show_value=True)
    windspeed   = mo.ui.slider(0, 50, step=0.5, value=8.0,  label="Wind Speed (m/s)", show_value=True)
    hour        = mo.ui.slider(0, 23, step=1,   value=14,   label="Hour of Day",       show_value=True)
    month       = mo.ui.slider(1, 12, step=1,   value=5,    label="Month",             show_value=True)
    return hour, month, temperature, windspeed


@app.cell
def _(hour, mo, month, temperature, windspeed):
    _month_names = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"]
    mo.vstack([
        mo.md("## ⚙️ Environmental Conditions"),
        mo.hstack([temperature, windspeed], gap=4),
        mo.hstack([hour, month], gap=4),
        mo.md(f"*Month: **{_month_names[month.value - 1]}** · Hour: **{hour.value:02d}:00***"),
    ])
    return


@app.cell
def _(centroids, hour, model, month, pd, temperature, windspeed):
    _features = ["latitude", "longitude", "temperature_c", "precipitation_mm",
                 "windspeed_ms", "weathercode", "observed_hour", "month", "dayofweek"]

    import datetime
    _dow = datetime.date(2026, month.value, 15).weekday()

    _rows = []
    for _name, _c in centroids.items():
        _rows.append({
            "section":          _name,
            "latitude":         _c["lat"],
            "longitude":        _c["lng"],
            "temperature_c":    temperature.value,
            "precipitation_mm": 0.0,
            "windspeed_ms":     windspeed.value,
            "weathercode":      0,
            "observed_hour":    hour.value,
            "month":            month.value,
            "dayofweek":        _dow,
        })

    _df = pd.DataFrame(_rows)
    _proba = model.predict_proba(_df[_features])[:, 1]
    _df["risk"] = [round(float(p), 4) for p in _proba]

    risk_df = _df[["section", "latitude", "longitude", "risk"]].sort_values("risk", ascending=False)
    return (risk_df,)


@app.cell
def _(go, risk_df, sections):
    def _risk_color(r):
        if r < 0.3:   return "#27ae60"
        elif r < 0.6: return "#f39c12"
        elif r < 0.8: return "#e67e22"
        else:         return "#e74c3c"

    _risk_lookup = dict(zip(risk_df["section"], risk_df["risk"]))
    _fig = go.Figure()

    # Trail lines colored by risk
    for _name, _coords in sections.items():
        _risk = _risk_lookup.get(_name, 0)
        _c = _coords[::20]
        _fig.add_trace(go.Scattermapbox(
            lat=[p[1] for p in _c],
            lon=[p[0] for p in _c],
            mode="lines",
            line=dict(color=_risk_color(_risk), width=5),
            name=_name,
            hovertemplate=f"<b>{_name}</b><br>Risk: {_risk:.1%}<extra></extra>",
            showlegend=False,
        ))

    # Centroid markers
    _fig.add_trace(go.Scattermapbox(
        lat=risk_df["latitude"],
        lon=risk_df["longitude"],
        mode="markers",
        marker=dict(
            size=16,
            color=[_risk_color(r) for r in risk_df["risk"]],
        ),
        text=risk_df["section"],
        customdata=risk_df["risk"],
        hovertemplate="<b>%{text}</b><br>Risk: %{customdata:.1%}<extra></extra>",
        name="Risk Score",
    ))

    _fig.update_layout(
        mapbox=dict(style="carto-positron", center=dict(lat=33.5, lon=-117.0), zoom=7),
        margin=dict(l=0, r=0, t=0, b=0),
        height=500,
        legend=dict(bgcolor="rgba(255,255,255,0.85)", bordercolor="#ccc", borderwidth=1),
    )

    risk_map = _fig
    return (risk_map,)


@app.cell
def _(plt, risk_df):
    _fig, _ax = plt.subplots(figsize=(5, 3.5))
    _colors = ["#27ae60" if r < 0.3 else "#f39c12" if r < 0.6 else "#e67e22" if r < 0.8 else "#e74c3c"
               for r in risk_df["risk"]]
    _bars = _ax.barh(risk_df["section"][::-1], risk_df["risk"][::-1] * 100, color=_colors[::-1])
    _ax.set_xlabel("Risk Score (%)")
    _ax.set_title("Snake Risk by PCT Section")
    _ax.set_xlim(0, 100)
    for _bar, _v in zip(_bars, risk_df["risk"][::-1] * 100):
        _ax.text(_v + 1, _bar.get_y() + _bar.get_height()/2, f"{_v:.1f}%", va="center", fontsize=9)
    plt.tight_layout()
    risk_chart = _fig
    return (risk_chart,)


@app.cell
def _(mo, risk_chart, risk_df, risk_map):
    _top = risk_df.iloc[0]
    _bot = risk_df.iloc[-1]

    mo.vstack([
        mo.hstack([
            mo.stat(label="Highest Risk Section", value=_top["section"],       caption=f"{_top['risk']:.1%} risk"),
            mo.stat(label="Lowest Risk Section",  value=_bot["section"],       caption=f"{_bot['risk']:.1%} risk"),
            mo.stat(label="Average Risk",          value=f"{risk_df['risk'].mean():.1%}", caption="All sections"),
        ], gap=2),
        mo.hstack([
            risk_map,
            mo.mpl.interactive(risk_chart),
        ], gap=2),
        mo.callout(
            mo.md("Risk scores are predictions from a Random Forest model trained on iNaturalist snake observations with weather features. Adjust the sliders above to see how conditions affect risk."),
            kind="warn",
        ),
    ])
    return


if __name__ == "__main__":
    app.run()
