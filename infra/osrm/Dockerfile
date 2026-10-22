FROM ghcr.io/project-osrm/osrm-backend:v26.9.0-debian AS builder
WORKDIR /data
COPY south-korea-latest.osm.pbf /data/korea.osm.pbf
RUN osrm-extract -p /opt/foot.lua /data/korea.osm.pbf \
 && osrm-contract /data/korea.osrm \
 && rm /data/korea.osm.pbf

FROM ghcr.io/project-osrm/osrm-backend:v26.9.0-debian
COPY --from=builder /data /data
EXPOSE 5000
CMD ["osrm-routed", "--algorithm", "ch", "/data/korea.osrm"]