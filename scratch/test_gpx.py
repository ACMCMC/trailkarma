import os
import uuid
import json
import math
import xml.etree.ElementTree as ET
import h3

def latlng_to_h3(lat, lng, resolution=9):
    return h3.latlng_to_cell(lat, lng, resolution)

def _haversine_miles(coords):
    total = 0.0
    for i in range(len(coords) - 1):
        lng1, lat1 = coords[i][0], coords[i][1]
        lng2, lat2 = coords[i+1][0], coords[i+1][1]
        dlat = math.radians(lat2 - lat1)
        dlng = math.radians(lng2 - lng1)
        a = math.sin(dlat/2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlng/2)**2
        total += 3958.8 * 2 * math.asin(math.sqrt(a))
    return round(total, 2)

def _simplify_coords(coords, tolerance=0.005):
    if len(coords) <= 2:
        return coords
    result = [coords[0]]
    for pt in coords[1:-1]:
        if abs(pt[0] - result[-1][0]) >= tolerance or abs(pt[1] - result[-1][1]) >= tolerance:
            result.append(pt)
    result.append(coords[-1])
    return result

def load_gpx_trail_statements(full_schema, gpx_path, trail_name, region):
    statements = []
    trail_id = str(uuid.uuid4())
    
    try:
        tree = ET.parse(gpx_path)
        root = tree.getroot()
        ns = {'gpx': 'http://www.topografix.com/GPX/1/1'}
        
        coords = []
        for trkpt in root.findall('.//gpx:trkpt', ns):
            lat = float(trkpt.get('lat'))
            lon = float(trkpt.get('lon'))
            coords.append([lon, lat])
            
        if not coords:
            print(f"  ⚠️  No coordinates found in GPX {gpx_path}")
            return []

        print(f"Original coords: {len(coords)}")
        coords = _simplify_coords(coords, tolerance=0.005) 
        print(f"Simplified coords: {len(coords)}")
        length_miles = _haversine_miles(coords)
        geom = json.dumps({"type": "LineString", "coordinates": coords})
        
        h3_cells = [latlng_to_h3(lat, lng) for lng, lat in coords]
        h3_cells_json = json.dumps(list(set(h3_cells)))

        print(f"Trail: {trail_name}, Length: {length_miles} miles")
        print(f"H3 Cells Count: {len(list(set(h3_cells)))}")
        return True
    except Exception as e:
        print(f"Error: {e}")
        return False

if __name__ == "__main__":
    gpx_path = "data/library_walk.gpx"
    load_gpx_trail_statements("test", gpx_path, "Library Walk", "UCSD")
