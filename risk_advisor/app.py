import json
import math
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

PLACES = {
    "Mumbai": (19.076, 72.878, "India"),
    "Delhi": (28.614, 77.209, "India"),
    "Bengaluru": (12.972, 77.594, "India"),
    "Hyderabad": (17.385, 78.487, "India"),
    "Chennai": (13.083, 80.270, "India"),
    "Kolkata": (22.573, 88.364, "India"),
    "Pune": (18.520, 73.857, "India"),
    "Ahmedabad": (23.023, 72.572, "India"),
    "Surat": (21.170, 72.831, "India"),
    "Jaipur": (26.912, 75.787, "India"),
    "Lucknow": (26.847, 80.947, "India"),
    "Chandigarh": (30.733, 76.779, "India"),
    "Dehradun": (30.316, 78.032, "India"),
    "Srinagar": (34.084, 74.797, "India"),
    "Bhopal": (23.260, 77.413, "India"),
    "Indore": (22.720, 75.858, "India"),
    "Nagpur": (21.146, 79.089, "India"),
    "Patna": (25.594, 85.138, "India"),
    "Ranchi": (23.344, 85.310, "India"),
    "Jamshedpur": (22.805, 86.203, "India"),
    "Bhubaneswar": (20.296, 85.825, "India"),
    "Guwahati": (26.145, 91.736, "India"),
    "Visakhapatnam": (17.687, 83.219, "India"),
    "Coimbatore": (11.017, 76.956, "India"),
    "Kochi": (9.932, 76.267, "India"),
    "Thiruvananthapuram": (8.524, 76.936, "India"),
    "Goa": (15.299, 74.124, "India"),
    "Dubai": (25.205, 55.271, "UAE"),
    "Singapore": (1.352, 103.820, "Singapore"),
    "London": (51.507, -0.128, "UK"),
    "New York": (40.713, -74.006, "USA"),
}

LOOKUP = {name.lower(): name for name in PLACES}


def distance_km(a, b):
    lat1, lon1, _ = PLACES[a]
    lat2, lon2, _ = PLACES[b]
    p1 = math.radians(lat1)
    p2 = math.radians(lat2)
    dlon = math.radians(lon2 - lon1)
    x = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlon / 2) ** 2
    return round(6371 * 2 * math.asin(math.sqrt(x)))


def score(home, login):
    h = LOOKUP.get(home.strip().lower())
    l = LOOKUP.get(login.strip().lower())
    if h is None or l is None:
        return {"risk": 0.7, "distanceKm": -1, "reason": "Login from an unknown location"}

    km = distance_km(h, l)
    if PLACES[h][2] != PLACES[l][2]:
        risk = 0.95
        reason = "Login from another country (" + l + ")"
    elif km <= 50:
        risk = 0.1
        reason = "Login from home area"
    elif km <= 600:
        risk = 0.35
        reason = "Login from " + l + ", " + str(km) + " km from home city " + h
    elif km <= 1500:
        risk = 0.6
        reason = "Login from " + l + ", " + str(km) + " km from home city " + h
    else:
        risk = 0.8
        reason = "Login from " + l + ", " + str(km) + " km from home city " + h
    return {"risk": risk, "distanceKm": km, "reason": reason}


class Handler(BaseHTTPRequestHandler):
    def reply(self, code, data):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        url = urlparse(self.path)
        q = parse_qs(url.query)
        if url.path == "/cities":
            self.reply(200, sorted(PLACES))
        elif url.path == "/risk":
            self.reply(200, score(q.get("home", [""])[0], q.get("login", [""])[0]))
        else:
            self.reply(404, {"error": "not found"})


if __name__ == "__main__":
    port = int(os.environ.get("RISK_PORT", "5000"))
    print("Risk checker running on port", port)
    ThreadingHTTPServer(("localhost", port), Handler).serve_forever()
