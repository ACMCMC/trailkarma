import { useState, useEffect } from 'react'
import { MapContainer, TileLayer, Polyline, Marker, Popup, CircleMarker, useMap, useMapEvents } from 'react-leaflet'
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
  { report_id: 'r2', type: 'species', title: 'Mule deer spotted',          lat: 32.8778, lng: -117.2402 },
  { report_id: 'r3', type: 'water',   title: 'Fresh water stream',          lat: 32.8768, lng: -117.2415 },
  { report_id: 'r4', type: 'info',    title: 'Trail splits here',           lat: 32.8783, lng: -117.2397 },
  { report_id: 'r5', type: 'food',    title: 'Picnic area',                 lat: 32.8763, lng: -117.2420 },
  { report_id: 'r6', type: 'hazard',  title: 'Steep drop-off',              lat: 33.8763, lng: -116.5438 },
  { report_id: 'r7', type: 'species', title: 'Coyote tracks',               lat: 33.8768, lng: -116.5432 },
  { report_id: 'r8', type: 'water',   title: 'Water tank refill',           lat: 33.8773, lng: -116.5427 },
]

function BoundsTracker({ onBoundsChange }) {
  const map = useMapEvents({
    moveend: () => onBoundsChange(map.getBounds()),
    zoomend: () => onBoundsChange(map.getBounds()),
  })
  useEffect(() => { onBoundsChange(map.getBounds()) }, [])
  return null
}

function GeocodedPin({ pin }) {
  const map = useMap()
  useEffect(() => {
    if (!pin) return
    map.setView([pin.lat, pin.lon], 17)
    const icon = L.divIcon({
      html: `<div style="font-size:30px;line-height:1;filter:drop-shadow(0 3px 6px rgba(0,0,0,0.4))">📍</div>`,
      className: 'emoji-icon',
      iconSize: [30, 38],
      iconAnchor: [15, 38],
      popupAnchor: [0, -38],
    })
    const m = L.marker([pin.lat, pin.lon], { icon }).addTo(map)
    m.bindPopup(`<strong style="font-size:13px">${pin.label}</strong>`, { maxWidth: 260 }).openPopup()
    return () => m.remove()
  }, [pin, map])
  return null
}

function ReportMarkers({ reports }) {
  const map = useMap()
  useEffect(() => {
    const markers = reports.map(r => {
      const emoji = REPORT_EMOJI[r.type] ?? '📍'
      const icon = L.divIcon({
        html: `<div style="font-size:20px">${emoji}</div>`,
        className: 'emoji-icon',
        iconSize: [30, 30],
        iconAnchor: [15, 15],
        popupAnchor: [0, -15],
      })
      return L.marker([r.lat, r.lng], { icon })
        .addTo(map)
        .bindPopup(`${emoji} ${r.title}`)
    })
    return () => markers.forEach(m => m.remove())
  }, [map])
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

const FEATURES = [
  {
    id: 'tracker',
    icon: '🧭',
    title: 'Hiker Tracker',
    desc: 'Follow a hiker\'s live trail',
    available: true,
  },
  {
    id: 'reports',
    icon: '⚠️',
    title: 'Trail Reports',
    desc: 'Hazards, water & species along a route',
    available: true,
  },
  {
    id: 'rewards',
    icon: '🏅',
    title: 'Rewards',
    desc: 'Karma points & collectibles earned on trail',
    available: false,
  },
]

export default function Home() {
  const [activeFeature, setActiveFeature] = useState('tracker')
  const [query, setQuery] = useState('')
  const [selectedHiker, setSelectedHiker] = useState(null)
  const [showDropdown, setShowDropdown] = useState(false)

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

  const [reportAddress, setReportAddress] = useState('')
  const [geocodedPin, setGeocodedPin] = useState(null)
  const [reportMapBounds, setReportMapBounds] = useState(null)
  const [geocoding, setGeocoding] = useState(false)
  const [suggestions, setSuggestions] = useState([])
  const [showSuggestions, setShowSuggestions] = useState(false)

  const visibleReports = MOCK_REPORTS.filter(r =>
    !reportMapBounds || (
      r.lat >= reportMapBounds.getSouth() && r.lat <= reportMapBounds.getNorth() &&
      r.lng >= reportMapBounds.getWest()  && r.lng <= reportMapBounds.getEast()
    )
  )

  async function nominatimSearch(q, limit = 5) {
    const usRes = await fetch(
      `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(q)}&format=json&limit=${limit}&countrycodes=us`,
      { headers: { 'Accept-Language': 'en' } }
    )
    const usData = await usRes.json()
    if (usData.length > 0) return usData
    const globalRes = await fetch(
      `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(q)}&format=json&limit=${limit}`,
      { headers: { 'Accept-Language': 'en' } }
    )
    return globalRes.json()
  }

  useEffect(() => {
    if (!reportAddress.trim() || reportAddress.length < 2) {
      setSuggestions([])
      return
    }
    const timer = setTimeout(async () => {
      setGeocoding(true)
      try {
        const data = await nominatimSearch(reportAddress)
        setSuggestions(data)
        setShowSuggestions(true)
      } finally {
        setGeocoding(false)
      }
    }, 400)
    return () => clearTimeout(timer)
  }, [reportAddress])

  function handleSuggestionSelect(s) {
    setReportAddress(s.display_name)
    setGeocodedPin({ lat: parseFloat(s.lat), lon: parseFloat(s.lon), label: s.display_name })
    setSuggestions([])
    setShowSuggestions(false)
  }

  async function handleGeocode(e) {
    e.preventDefault()
    if (suggestions.length > 0) {
      handleSuggestionSelect(suggestions[0])
      return
    }
    if (!reportAddress.trim()) return
    setGeocoding(true)
    try {
      const data = await nominatimSearch(reportAddress, 1)
      if (data.length > 0) handleSuggestionSelect(data[0])
    } finally {
      setGeocoding(false)
    }
  }

  function handleFeatureClick(id) {
    if (id === 'tracker' || id === 'reports') setActiveFeature(id)
  }

  return (
    <div style={{ minHeight: '100vh', background: '#f2f5f0', fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif' }}>

      {/* ── Hero ── */}
      <div style={{
        background: 'linear-gradient(160deg, #0d2818 0%, #1b4332 55%, #2d6a4f 100%)',
        padding: '60px 24px 52px',
        textAlign: 'center',
        position: 'relative',
        overflow: 'hidden',
      }}>
        {/* decorative mountain silhouette */}
        <svg
          viewBox="0 0 1440 120" preserveAspectRatio="none"
          style={{ position: 'absolute', bottom: 0, left: 0, width: '100%', height: 80, opacity: 0.15 }}
        >
          <polygon points="0,120 200,40 400,90 600,20 800,75 1000,10 1200,60 1440,30 1440,120" fill="white" />
        </svg>

        <div style={{ position: 'relative', zIndex: 1 }}>
          <div style={{ fontSize: 13, letterSpacing: '0.2em', color: '#74c69d', textTransform: 'uppercase', marginBottom: 12, fontWeight: 500 }}>
            Offline-first hiking platform
          </div>
          <h1 style={{
            fontSize: 'clamp(36px, 6vw, 64px)',
            fontWeight: 800,
            color: 'white',
            letterSpacing: '-0.02em',
            margin: 0,
          }}>
            Trail<span style={{ color: '#74c69d' }}>Karma</span>
          </h1>
          <p style={{ color: '#b7e4c7', fontSize: 16, marginTop: 12, fontWeight: 400 }}>
            Bringing hikers together beyond the signal
          </p>
        </div>
      </div>

      {/* ── Feature tabs ── */}
      <div style={{ display: 'flex', justifyContent: 'center', gap: 16, padding: '32px 24px 0', flexWrap: 'wrap' }}>
        {FEATURES.map(f => {
          const isActive = activeFeature === f.id
          return (
            <div
              key={f.id}
              onClick={() => handleFeatureClick(f.id)}
              style={{
                width: 220,
                background: isActive ? 'white' : f.available ? 'white' : '#f5f5f0',
                border: isActive ? '2px solid #2d6a4f' : '2px solid transparent',
                borderRadius: 14,
                padding: '20px 22px',
                cursor: f.available ? 'pointer' : 'default',
                boxShadow: isActive ? '0 4px 16px rgba(45,106,79,0.18)' : '0 2px 8px rgba(0,0,0,0.06)',
                transition: 'all 0.18s ease',
                opacity: f.available ? 1 : 0.55,
                position: 'relative',
              }}
            >
              {!f.available && (
                <span style={{
                  position: 'absolute', top: 10, right: 12,
                  fontSize: 10, fontWeight: 600, color: '#999',
                  textTransform: 'uppercase', letterSpacing: '0.08em',
                }}>
                  Soon
                </span>
              )}
              <div style={{ fontSize: 28, marginBottom: 8 }}>{f.icon}</div>
              <div style={{ fontWeight: 700, fontSize: 15, color: isActive ? '#1b4332' : '#333', marginBottom: 4 }}>
                {f.title}
              </div>
              <div style={{ fontSize: 12, color: '#888', lineHeight: 1.4 }}>{f.desc}</div>
              {isActive && (
                <div style={{ position: 'absolute', bottom: -2, left: '50%', transform: 'translateX(-50%)', width: 32, height: 3, background: '#2d6a4f', borderRadius: 2 }} />
              )}
            </div>
          )
        })}
      </div>

      {/* ── Feature content ── */}
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '28px 24px 60px' }}>

        {activeFeature === 'tracker' && (
          <div style={{ width: '100%', maxWidth: 960 }}>

            {/* Search input */}
            <div style={{ position: 'relative', marginBottom: 24 }}>
              <div style={{
                position: 'absolute', left: 16, top: '50%', transform: 'translateY(-50%)',
                fontSize: 18, pointerEvents: 'none',
              }}>🔍</div>
              <input
                type="text"
                placeholder="Search hiker by name..."
                value={query}
                onChange={e => { setQuery(e.target.value); setShowDropdown(true) }}
                onFocus={() => setShowDropdown(true)}
                style={{
                  width: '100%', padding: '14px 16px 14px 46px',
                  fontSize: 16, border: '2px solid #d0e8da',
                  borderRadius: 12, outline: 'none',
                  background: 'white',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.05)',
                  transition: 'border-color 0.15s',
                }}
                onFocusCapture={e => e.target.style.borderColor = '#2d6a4f'}
                onBlurCapture={e => e.target.style.borderColor = '#d0e8da'}
              />
              {showDropdown && query && filtered.length > 0 && (
                <ul style={{
                  position: 'absolute', top: '110%', left: 0, right: 0,
                  background: 'white', border: '1px solid #dde', borderRadius: 10,
                  listStyle: 'none', margin: 0, padding: 6,
                  boxShadow: '0 8px 24px rgba(0,0,0,0.1)', zIndex: 2000,
                }}>
                  {filtered.map(h => (
                    <li
                      key={h.user_id}
                      onMouseDown={() => handleSelect(h)}
                      style={{ padding: '11px 14px', cursor: 'pointer', borderRadius: 7, fontSize: 14, color: '#1a1a1a', display: 'flex', alignItems: 'center', gap: 10 }}
                      onMouseEnter={e => e.currentTarget.style.background = '#f0faf4'}
                      onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                    >
                      <span style={{ fontSize: 20 }}>🥾</span>
                      <span>
                        <strong>{h.display_name}</strong>
                        <span style={{ color: '#aaa', marginLeft: 8, fontSize: 12 }}>{h.user_id}</span>
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* Location history table */}
            {selectedHiker && locations.length > 0 && (
              <div style={{
                background: 'white', borderRadius: 14,
                border: '1px solid #e0eed8',
                boxShadow: '0 2px 10px rgba(0,0,0,0.05)',
                overflow: 'hidden', marginBottom: 24,
              }}>
                <div style={{ padding: '14px 20px', borderBottom: '1px solid #eef5ea', display: 'flex', alignItems: 'center', gap: 10 }}>
                  <span style={{ fontSize: 18 }}>📍</span>
                  <span style={{ fontWeight: 700, fontSize: 14, color: '#1b4332' }}>
                    Location History — {selectedHiker.display_name}
                  </span>
                  <span style={{ marginLeft: 'auto', fontSize: 12, color: '#74c69d', fontWeight: 600 }}>
                    Last sync: {formatTime(latest.timestamp)}
                  </span>
                </div>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                  <thead>
                    <tr style={{ background: '#f7fbf5' }}>
                      <th style={{ padding: '9px 16px', fontWeight: 600, color: '#555', textAlign: 'left', width: 36 }}>#</th>
                      <th style={{ padding: '9px 16px', fontWeight: 600, color: '#555', textAlign: 'left' }}>Time</th>
                      <th style={{ padding: '9px 16px', fontWeight: 600, color: '#555', textAlign: 'left' }}>Latitude</th>
                      <th style={{ padding: '9px 16px', fontWeight: 600, color: '#555', textAlign: 'left' }}>Longitude</th>
                    </tr>
                  </thead>
                  <tbody>
                    {[...locations].reverse().map((loc, i) => (
                      <tr key={i} style={{ borderTop: '1px solid #f0f5ec', background: i === 0 ? '#f4fbf5' : 'transparent' }}>
                        <td style={{ padding: '10px 16px', color: i === 0 ? '#2d6a4f' : '#ccc', fontWeight: 700 }}>
                          {i === 0 ? '▲' : locations.length - i}
                        </td>
                        <td style={{ padding: '10px 16px', color: i === 0 ? '#1b4332' : '#444', fontWeight: i === 0 ? 600 : 400 }}>
                          {formatTime(loc.timestamp)}
                        </td>
                        <td style={{ padding: '10px 16px', color: '#666', fontFamily: 'monospace' }}>{loc.lat.toFixed(5)}</td>
                        <td style={{ padding: '10px 16px', color: '#666', fontFamily: 'monospace' }}>{loc.lng.toFixed(5)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {/* Map */}
            <div style={{
              borderRadius: 16,
              border: '2px solid #2d6a4f',
              overflow: 'hidden',
              boxShadow: '0 6px 24px rgba(27,67,50,0.15)',
              height: 460,
              background: '#e8f0eb',
            }}>
              {selectedHiker && positions.length > 0 ? (
                <MapContainer
                  key={selectedHiker.user_id}
                  center={[latest.lat, latest.lng]}
                  zoom={15}
                  style={{ height: '100%', width: '100%' }}
                >
                  <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
                  <ReportMarkers reports={MOCK_REPORTS} />
                  <Polyline positions={positions} color="#2d6a4f" weight={4} opacity={0.9} />
                  {locations.map((loc, i) => {
                    const isLatest = i === locations.length - 1
                    return isLatest ? (
                      <Marker key={i} position={[loc.lat, loc.lng]}>
                        <Popup>
                          <strong>{selectedHiker.display_name} (latest)</strong><br />
                          {formatTime(loc.timestamp)}<br />
                          <span style={{ fontFamily: 'monospace', fontSize: 11 }}>{loc.lat.toFixed(5)}, {loc.lng.toFixed(5)}</span>
                        </Popup>
                      </Marker>
                    ) : (
                      <CircleMarker key={i} center={[loc.lat, loc.lng]} radius={6}
                        pathOptions={{ color: '#c0392b', fillColor: '#e74c3c', fillOpacity: 1, weight: 1.5 }}>
                        <Popup>
                          <strong>Stop {locations.length - i - 1}</strong><br />
                          {formatTime(loc.timestamp)}<br />
                          <span style={{ fontFamily: 'monospace', fontSize: 11 }}>{loc.lat.toFixed(5)}, {loc.lng.toFixed(5)}</span>
                        </Popup>
                      </CircleMarker>
                    )
                  })}
                </MapContainer>
              ) : (
                <div style={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', color: '#a0b8a8', gap: 12 }}>
                  <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
                    <path d="M10 52 L22 24 L32 38 L44 16 L54 52 Z" stroke="#c8ddd0" strokeWidth="2.5" fill="none" strokeLinejoin="round" />
                    <circle cx="44" cy="16" r="4" fill="#c8ddd0" />
                  </svg>
                  <span style={{ fontSize: 14 }}>Search for a hiker to see their trail</span>
                </div>
              )}
            </div>
          </div>
        )}

        {activeFeature === 'reports' && (
          <div style={{ width: '100%', maxWidth: 960 }}>

            {/* Address search */}
            <form onSubmit={handleGeocode} style={{ display: 'flex', gap: 10, marginBottom: 20 }}>
              <div style={{ position: 'relative', flex: 1 }}>
                <div style={{
                  position: 'absolute', left: 16, top: '50%', transform: 'translateY(-50%)',
                  fontSize: 18, pointerEvents: 'none', zIndex: 1,
                }}>📍</div>
                <input
                  type="text"
                  placeholder="Enter a location to jump the map there (optional)..."
                  value={reportAddress}
                  onChange={e => { setReportAddress(e.target.value); setShowSuggestions(true) }}
                  onFocus={() => setShowSuggestions(true)}
                  onBlur={() => setTimeout(() => setShowSuggestions(false), 150)}
                  style={{
                    width: '100%', padding: '14px 16px 14px 46px',
                    fontSize: 15, border: '2px solid #d0e8da',
                    borderRadius: 12, outline: 'none',
                    background: 'white',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.05)',
                    boxSizing: 'border-box',
                  }}
                  onFocusCapture={e => e.target.style.borderColor = '#2d6a4f'}
                  onBlurCapture={e => e.target.style.borderColor = '#d0e8da'}
                />
                {showSuggestions && suggestions.length > 0 && (
                  <ul style={{
                    position: 'absolute', top: '110%', left: 0, right: 0,
                    background: 'white', border: '1px solid #d0e8da', borderRadius: 10,
                    listStyle: 'none', margin: 0, padding: 4,
                    boxShadow: '0 8px 24px rgba(0,0,0,0.1)', zIndex: 3000,
                    maxHeight: 220, overflowY: 'auto',
                  }}>
                    {geocoding && (
                      <li style={{ padding: '8px 14px', color: '#aaa', fontSize: 13 }}>Searching…</li>
                    )}
                    {suggestions.map((s, i) => (
                      <li
                        key={i}
                        onMouseDown={() => handleSuggestionSelect(s)}
                        style={{ padding: '10px 14px', cursor: 'pointer', borderRadius: 7, fontSize: 13, color: '#222', display: 'flex', alignItems: 'flex-start', gap: 8 }}
                        onMouseEnter={e => e.currentTarget.style.background = '#f0faf4'}
                        onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                      >
                        <span style={{ marginTop: 1 }}>📍</span>
                        <span style={{ lineHeight: 1.4 }}>{s.display_name}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              <button
                type="submit"
                disabled={geocoding}
                style={{
                  padding: '14px 24px', background: '#2d6a4f', color: 'white',
                  border: 'none', borderRadius: 12, fontSize: 15, fontWeight: 600,
                  cursor: geocoding ? 'wait' : 'pointer',
                  whiteSpace: 'nowrap',
                }}
              >
                {geocoding ? 'Searching…' : 'Go'}
              </button>
            </form>

            {/* Visible report count badge */}
            <div style={{ marginBottom: 12, fontSize: 13, color: '#666' }}>
              Showing <strong style={{ color: '#2d6a4f' }}>{visibleReports.length}</strong> trail report{visibleReports.length !== 1 ? 's' : ''} in current view
              <span style={{ marginLeft: 12, color: '#aaa' }}>— drag or zoom the map to explore</span>
            </div>

            {/* Map */}
            <div style={{
              borderRadius: 16,
              border: '2px solid #2d6a4f',
              overflow: 'hidden',
              boxShadow: '0 6px 24px rgba(27,67,50,0.15)',
              height: 500,
            }}>
              <MapContainer
                center={[32.8772, -117.2408]}
                zoom={13}
                style={{ height: '100%', width: '100%' }}
              >
                <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
                <BoundsTracker onBoundsChange={setReportMapBounds} />
                <GeocodedPin pin={geocodedPin} />
                <ReportMarkers reports={visibleReports} />
              </MapContainer>
            </div>

            {/* Legend */}
            <div style={{ display: 'flex', gap: 20, marginTop: 14, flexWrap: 'wrap' }}>
              {Object.entries(REPORT_EMOJI).map(([type, emoji]) => (
                <span key={type} style={{ fontSize: 13, color: '#555', display: 'flex', alignItems: 'center', gap: 5 }}>
                  <span style={{ fontSize: 18 }}>{emoji}</span>
                  <span style={{ textTransform: 'capitalize' }}>{type}</span>
                </span>
              ))}
            </div>

          </div>
        )}

      </div>
    </div>
  )
}
