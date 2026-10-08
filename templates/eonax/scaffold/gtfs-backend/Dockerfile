# The provider's own system: serves its public transport timetable (a synthetic GTFS feed) as gtfs.zip.
# Not part of the data space: only the provider's data plane reads it (the asset's dataAddress).
FROM python:3.14.8-slim@sha256:c3e521df8b2b498a7a682e7e18676771cb80c6b75b8699af886b2d554ce40151
WORKDIR /srv
COPY gtfs/ gtfs/
RUN cd gtfs && python -m zipfile -c ../gtfs.zip *.txt
USER 10001
EXPOSE 8000
CMD ["python", "-m", "http.server", "8000", "--directory", "/srv"]
