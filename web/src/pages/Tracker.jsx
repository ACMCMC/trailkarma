import { useState, useEffect } from 'react'
import { MapContainer, TileLayer, Polyline, Marker, Popup, CircleMarker, useMapEvents, useMap } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import L from 'leaflet'

delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
})

const REPORT_EMOJI = { hazard: '‼️', species: '🫎', water: '💧', info: '📖', food: '🍜' }

const MOCK_REPORTS = [
  { report_id: 'r1', type: 'hazard',  title: 'Fallen tree blocking path', lat: 32.8772, lng: -117.2408 },
  { report_id: 'r2', type: 'species', title: 'Mule deer spotted', lat: 32.8778, lng: -117.2402 },
  { report_id: 'r3', type: 'water',   title: 'Fresh water stream', lat: 32.8768, lng: -117.2415 },
  { report_id: 'r4', type: 'info',    title: 'Trail splits here', lat: 32.8783, lng: -117.2397 },
  { report_id: 'r5', type: 'food',    title: 'Picnic area', lat: 32.8763, lng: -117.2420 },
  { report_id: 'r6', type: 'hazard',  title: 'Steep drop-off', lat: 33.8763, lng: -116.5438 },
  { report_id: 'r7', type: 'species', title: 'Coyote tracks', lat: 33.8768, lng: -116.5432 },
  { report_id: 'r8', type: 'water',   title: 'Water tank refill', lat: 33.8773, lng: -116.5427 },
  { report_id: 'r9', type: 'info',    title: 'Viewpoint at summit', lat: 34.1015, lng: -118.2993 },
  { report_id: 'r10', type: 'food',   title: 'Ranger station snacks', lat: 34.1035, lng: -118.2982 },
]

function ReportMarkers({ reports }) {
  const map = useMap()
  useEffect(() => {
    console.log('[ReportMarkers] adding', reports.length, 'markers to map', map)
    const markers = reports.map(r => {
      const emoji = REPORT_EMOJI[r.type] ?? '📍'
      const icon = L.divIcon({
        html: `<div style="font-size:20px">${emoji}</div>`,
        className: 'leaflet-div-icon',
        iconSize: [30, 30],
        iconAnchor: [15, 15],
        popupAnchor: [0, -15],
      })
      const m = L.marker([r.lat, r.lng], { icon })
      m.addTo(map)
      m.bindPopup(`${emoji} ${r.title}`)
      console.log('[ReportMarkers] added', r.type, r.lat, r.lng)
      return m
    })
    return () => markers.forEach(m => m.remove())
  }, [map])
  return null
}

function BoundsTracker({ onBoundsChange }) {
  const map = useMapEvents({
    moveend: () => onBoundsChange(map.getBounds()),
    zoomend: () => onBoundsChange(map.getBounds()),
  })
  useEffect(() => { onBoundsChange(map.getBounds()) }, [])
  return null
}

const MOCK_HIKERS = [
  { user_id: 'user_001', display_name: 'Alice Trail' },
  { user_id: 'user_002', display_name: 'Bob Hiker' },
  { user_id: 'user_003', display_name: 'Carol PCT' },
]

const MOCK_LOCATIONS = {
  user_001: [
    { lat: 32.8766, lng: -117.2413, timestamp: '2026-04-18T23:30:00Z' },
    { lat: 32.8770, lng: -117.2410, timestamp: '2026-04-18T23:40:00Z' },
    { lat: 32.8775, lng: -117.2405, timestamp: '2026-04-18T23:50:00Z' },
    { lat: 32.8780, lng: -117.2400, timestamp: '2026-04-19T00:00:00Z' },
    { lat: 32.8785, lng: -117.2395, timestamp: '2026-04-19T00:10:00Z' },
  ],
  user_002: [
    { lat: 33.8760, lng: -116.5440, timestamp: '2026-04-18T20:00:00Z' },
    { lat: 33.8765, lng: -116.5435, timestamp: '2026-04-18T20:15:00Z' },
    { lat: 33.8770, lng: -116.5430, timestamp: '2026-04-18T20:30:00Z' },
    { lat: 33.8775, lng: -116.5425, timestamp: '2026-04-18T20:45:00Z' },
    { lat: 33.8780, lng: -116.5420, timestamp: '2026-04-18T21:00:00Z' },
  ],
  user_003: [
    { lat: 34.1000, lng: -118.3000, timestamp: '2026-04-18T18:00:00Z' },
    { lat: 34.1010, lng: -118.2995, timestamp: '2026-04-18T18:20:00Z' },
    { lat: 34.1020, lng: -118.2990, timestamp: '2026-04-18T18:40:00Z' },
    { lat: 34.1030, lng: -118.2985, timestamp: '2026-04-18T19:00:00Z' },
    { lat: 34.1040, lng: -118.2980, timestamp: '2026-04-18T19:20:00Z' },
  ],
}

function formatTime(iso) {
  return new Date(iso).toLocaleString('en-US', {
    month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

export default function Tracker() {
  const [query, setQuery] = useState('')
  const [selectedHiker, setSelectedHiker] = useState(null)
  const [showDropdown, setShowDropdown] = useState(false)
  const [mapBounds, setMapBounds] = useState(null)

  const visibleReports = MOCK_REPORTS.filter(r =>
    !mapBounds || (
      r.lat >= mapBounds.getSouth() && r.lat <= mapBounds.getNorth() &&
      r.lng >= mapBounds.getWest()  && r.lng <= mapBounds.getEast()
    )
  )

  const filtered = MOCK_HIKERS.filter(h =>
    h.display_name.toLowerCase().includes(query.toLowerCase())
  )

  const locations = selectedHiker ? MOCK_LOCATIONS[selectedHiker.user_id] || [] : []
  const positions = locations.map(l => [l.lat, l.lng])
  const latest = locations[locations.length - 1]

  function handleSelect(hiker) {
    setSelectedHiker(hiker)
    setQuery(hiker.display_name)
    setShowDropdown(false)
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: '#f7faf8', fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif' }}>

      {/* Top bar */}
      <div style={{ background: 'white', borderBottom: '1px solid #e0ece6', padding: '14px 24px', display: 'flex', alignItems: 'center', gap: 16, zIndex: 1000 }}>
        <span style={{ fontSize: 18, fontWeight: 700, color: '#2d6a4f', whiteSpace: 'nowrap' }}>
          TrailKarma
        </span>
        <div style={{ position: 'relative', flex: 1, maxWidth: 420 }}>
          <input
            type="text"
            placeholder="Search hiker name..."
            value={query}
            onChange={e => { setQuery(e.target.value); setShowDropdown(true) }}
            onFocus={() => setShowDropdown(true)}
            style={{
              width: '100%', padding: '10px 14px', fontSize: 15,
              border: '1px solid #c8dfd4', borderRadius: 8,
              outline: 'none', boxSizing: 'border-box',
              background: '#f4faf7',
            }}
          />
          {showDropdown && query && filtered.length > 0 && (
            <ul style={{
              position: 'absolute', top: '110%', left: 0, right: 0,
              background: 'white', border: '1px solid #ddd', borderRadius: 8,
              listStyle: 'none', margin: 0, padding: 4,
              boxShadow: '0 4px 12px rgba(0,0,0,0.08)', zIndex: 2000,
            }}>
              {filtered.map(h => (
                <li
                  key={h.user_id}
                  onMouseDown={() => handleSelect(h)}
                  style={{
                    padding: '10px 14px', cursor: 'pointer', borderRadius: 6,
                    fontSize: 14, color: '#1a1a1a',
                  }}
                  onMouseEnter={e => e.currentTarget.style.background = '#f0faf4'}
                  onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                >
                  {h.display_name}
                  <span style={{ color: '#aaa', marginLeft: 8, fontSize: 12 }}>{h.user_id}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
        {selectedHiker && (
          <span style={{ fontSize: 13, color: '#888' }}>
            Last sync: {latest ? formatTime(latest.timestamp) : '—'}
          </span>
        )}
      </div>

      {/* Location history table */}
      {selectedHiker && locations.length > 0 && (
        <div style={{ background: 'white', borderBottom: '1px solid #e0ece6', padding: '12px 24px' }}>
          <p style={{ fontSize: 12, fontWeight: 600, color: '#40916c', letterSpacing: '0.05em', textTransform: 'uppercase', margin: '0 0 8px' }}>
            Location History — {selectedHiker.display_name}
          </p>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead>
              <tr style={{ color: '#888', textAlign: 'left' }}>
                <th style={{ padding: '6px 10px', fontWeight: 500, width: 30 }}>#</th>
                <th style={{ padding: '6px 10px', fontWeight: 500 }}>Time</th>
                <th style={{ padding: '6px 10px', fontWeight: 500 }}>Latitude</th>
                <th style={{ padding: '6px 10px', fontWeight: 500 }}>Longitude</th>
              </tr>
            </thead>
            <tbody>
              {[...locations].reverse().map((loc, i) => (
                <tr
                  key={i}
                  style={{ borderTop: '1px solid #f0f0f0', background: i === 0 ? '#f6fcf8' : 'transparent' }}
                >
                  <td style={{ padding: '7px 10px', color: '#bbb' }}>{i === 0 ? '●' : i + 1}</td>
                  <td style={{ padding: '7px 10px', color: i === 0 ? '#2d6a4f' : '#333', fontWeight: i === 0 ? 600 : 400 }}>
                    {formatTime(loc.timestamp)}
                  </td>
                  <td style={{ padding: '7px 10px', color: '#555', fontFamily: 'monospace' }}>{loc.lat.toFixed(5)}</td>
                  <td style={{ padding: '7px 10px', color: '#555', fontFamily: 'monospace' }}>{loc.lng.toFixed(5)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Map */}
      <div style={{ flex: 1, position: 'relative' }}>
        {selectedHiker && positions.length > 0 ? (
          <MapContainer
            key={selectedHiker.user_id}
            center={[latest.lat, latest.lng]}
            zoom={15}
            style={{ height: '100%', width: '100%' }}
          >
            <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
            <BoundsTracker onBoundsChange={setMapBounds} />
            <Polyline positions={positions} color="#2d6a4f" weight={4} opacity={0.85} />
            {locations.map((loc, i) => {
              const isLatest = i === locations.length - 1
              return isLatest ? (
                <Marker key={i} position={[loc.lat, loc.lng]}>
                  <Popup>
                    <strong>{selectedHiker.display_name} (latest)</strong><br />
                    {formatTime(loc.timestamp)}<br />
                    <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{loc.lat.toFixed(5)}, {loc.lng.toFixed(5)}</span>
                  </Popup>
                </Marker>
              ) : (
                <CircleMarker key={i} center={[loc.lat, loc.lng]} radius={6} pathOptions={{ color: '#c0392b', fillColor: '#e74c3c', fillOpacity: 1, weight: 1.5 }}>
                  <Popup>
                    <strong>Stop {locations.length - i - 1}</strong><br />
                    {formatTime(loc.timestamp)}<br />
                    <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{loc.lat.toFixed(5)}, {loc.lng.toFixed(5)}</span>
                  </Popup>
                </CircleMarker>
              )
            })}
            <ReportMarkers reports={visibleReports} />
          </MapContainer>
        ) : (
          <div style={{
            height: '100%', display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center', color: '#aaa',
          }}>
            <div style={{ fontSize: 48, marginBottom: 16 }}>🗺️</div>
            <p style={{ fontSize: 16 }}>Search for a hiker to see their trail</p>
          </div>
        )}
      </div>
    </div>
  )
}
