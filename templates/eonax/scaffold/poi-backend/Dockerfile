# The consumer's own system: serves its tourist points of interest (synthetic GeoJSON) as pois.geojson.
# A participant can be consumer and provider: the travel app offers this dataset to the other members.
FROM python:3.14.8-slim@sha256:c3e521df8b2b498a7a682e7e18676771cb80c6b75b8699af886b2d554ce40151
WORKDIR /srv
COPY pois.geojson .
USER 10001
EXPOSE 8000
CMD ["python", "-m", "http.server", "8000", "--directory", "/srv"]
