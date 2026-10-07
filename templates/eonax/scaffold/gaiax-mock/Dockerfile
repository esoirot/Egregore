# MOCK of the Gaia-X Digital Clearing House (GXDCH): compliance service and registration number
# notary, plus the web server that would host your did.json. See README.md next to this file.
FROM python:3.14.8-slim@sha256:c3e521df8b2b498a7a682e7e18676771cb80c6b75b8699af886b2d554ce40151
WORKDIR /mock
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py .
USER 10001
EXPOSE 8080
CMD ["python", "app.py"]
