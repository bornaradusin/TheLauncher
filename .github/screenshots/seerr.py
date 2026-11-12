import json, re
from http.server import BaseHTTPRequestHandler, HTTPServer

def movie(tmdb, title, date, poster, status=None, overview=""):
    item = {"id": tmdb, "mediaType": "movie", "title": title, "releaseDate": date,
            "posterPath": poster, "overview": overview, "voteAverage": 7.4}
    if status:
        item["mediaInfo"] = {"id": tmdb, "tmdbId": tmdb, "status": status}
    return item

RESULTS = [
    movie(414906, "The Batman", "2022-03-01", "/74xTEgt7R36Fpooo50r9T25onhq.jpg", 5,
          "In his second year of fighting crime, Batman uncovers corruption in Gotham City."),
    movie(268, "Batman", "1989-06-21", "/cij4dd21v2Rk2YtUQbV5kW69WB2.jpg", None,
          "Batman must face his most ruthless nemesis when a deformed madman calling himself the Joker seizes control of Gotham's criminal underworld."),
    {"id": 2098, "mediaType": "tv", "name": "Batman: The Animated Series", "firstAirDate": "1992-09-05",
     "posterPath": "/lBomQFW1vlm1yUYMNSbFZ45R4Ox.jpg", "voteAverage": 8.5,
     "overview": "The Dark Knight battles crime in Gotham City with occasional help from Robin and Batgirl."},
    movie(272, "Batman Begins", "2005-06-10", "/4MpN4kIEqUjW8OPtOQJXlTdHiJV.jpg", 3,
          "Driven by tragedy, billionaire Bruce Wayne dedicates his life to uncovering and defeating the corruption that plagues his home."),
    movie(364, "Batman Returns", "1992-06-19", "/jKBjeXM7iBBV9UkUcOXx3m7FSHY.jpg", 5,
          "The Penguin rises from the sewers to take over Gotham City."),
    movie(415, "Batman & Robin", "1997-06-20", None),
    movie(155, "The Dark Knight", "2008-07-16", None),
]

class Handler(BaseHTTPRequestHandler):
    def reply(self, code, body=None):
        data = json.dumps(body if body is not None else {}).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/api/v1/status":
            return self.reply(200, {"version": "2.7.3"})
        if path == "/api/v1/auth/me":
            return self.reply(200, {"id": 1, "displayName": "Admin"})
        if path == "/api/v1/search":
            return self.reply(200, {"page": 1, "totalPages": 1, "totalResults": len(RESULTS), "results": RESULTS})
        m = re.fullmatch(r"/api/v1/(movie|tv)/(\d+)", path)
        if m:
            item = next((r for r in RESULTS if r["id"] == int(m.group(2))), None)
            if item is None:
                return self.reply(404, {"message": "Not found"})
            details = dict(item)
            if m.group(1) == "movie":
                details["runtime"] = 126
            else:
                details["numberOfSeasons"] = 3
                details["status"] = "Ended"
                details["seasons"] = [{"seasonNumber": n, "name": "Season %d" % n, "episodeCount": c}
                                      for n, c in ((1, 65), (2, 20), (3, 0))]
            info = details.get("mediaInfo")
            if info:
                info = dict(info)
                info["requests"] = [{"id": item["id"] * 10, "is4k": False, "status": 2}]
                if info["status"] == 5:
                    info["serviceId"] = 0
                    info["externalServiceId"] = 7
                details["mediaInfo"] = info
            return self.reply(200, details)
        self.reply(404, {"message": "Not found"})

    def do_POST(self):
        self.reply(201, {"id": 99, "status": 2})

    def do_DELETE(self):
        self.reply(204)

HTTPServer(("0.0.0.0", 5055), Handler).serve_forever()
