#!/usr/bin/env python3
"""Moyeota 부하테스트 대시보드 생성기.
   python3 loadtest/grafana/generate-dashboard.py  → dashboards/moyeota-loadtest.json
패널을 고칠 때는 JSON 을 직접 편집하지 말고 이 파일을 고쳐 다시 생성한다.
주의: k6 의 Prometheus remote write 는 시간 지표(http_req_duration, Trend(…, true))를 ms 가 아니라 초로 내보낸다.
"""
import json, os

DS = {"type": "prometheus", "uid": "prometheus"}
_id = [0]
def nid():
    _id[0] += 1
    return _id[0]

def ts(title, targets, x, y, w=8, h=8, unit=None, desc=None, max_=None, stack=False):
    p = {
        "id": nid(), "type": "timeseries", "title": title, "datasource": DS,
        "gridPos": {"x": x, "y": y, "w": w, "h": h},
        "fieldConfig": {"defaults": {"custom": {"lineWidth": 1, "fillOpacity": 8, "showPoints": "never",
                                                "stacking": {"mode": "normal" if stack else "none"}}},
                        "overrides": []},
        "options": {"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True},
                    "tooltip": {"mode": "multi", "sort": "desc"}},
        "targets": [{"datasource": DS, "expr": e, "legendFormat": l, "refId": chr(65 + i)} for i, (e, l) in enumerate(targets)],
    }
    if unit: p["fieldConfig"]["defaults"]["unit"] = unit
    if max_ is not None: p["fieldConfig"]["defaults"]["max"] = max_
    if desc: p["description"] = desc
    return p

def stat(title, expr, x, y, w=4, h=4, unit=None, decimals=None, desc=None):
    p = {
        "id": nid(), "type": "stat", "title": title, "datasource": DS,
        "gridPos": {"x": x, "y": y, "w": w, "h": h},
        "fieldConfig": {"defaults": {"color": {"mode": "thresholds"},
                                     "thresholds": {"mode": "absolute", "steps": [{"color": "green", "value": None}]}},
                        "overrides": []},
        "options": {"reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False},
                    "colorMode": "value", "graphMode": "area", "textMode": "value"},
        "targets": [{"datasource": DS, "expr": expr, "refId": "A"}],
    }
    if unit: p["fieldConfig"]["defaults"]["unit"] = unit
    if decimals is not None: p["fieldConfig"]["defaults"]["decimals"] = decimals
    if desc: p["description"] = desc
    return p

def row(title, y):
    return {"id": nid(), "type": "row", "title": title, "collapsed": False,
            "gridPos": {"x": 0, "y": y, "w": 24, "h": 1}, "panels": []}

T = 'testid=~"$testid"'
NOSETUP = 'name!~"setup_.*|seed_.*"'
panels = []
y = 0

# ---- k6 ----
panels.append(row("k6 (부하 발생기 시점)", y)); y += 1
panels.append(stat("활성 VU", f'max(k6_vus{{{T}}})', 0, y, desc="현재 가상 사용자 수 (선택한 회차 중 최대)"))
panels.append(stat("초당 요청", f'sum(rate(k6_http_reqs_total{{{T}, {NOSETUP}}}[$__rate_interval]))', 4, y, unit="reqps", decimals=1))
panels.append(stat("에러율", f'sum(rate(k6_http_reqs_total{{{T}, {NOSETUP}, expected_response="false"}}[$__rate_interval])) / sum(rate(k6_http_reqs_total{{{T}, {NOSETUP}}}[$__rate_interval]))', 8, y, unit="percentunit", decimals=2,
                   desc="expected_response=false 인 요청 비율 (시나리오가 허용한 409 등은 제외)"))
panels.append(stat("p95 응답시간 (가장 느린 요청 종류)", f'max(k6_http_req_duration_p95{{{T}, {NOSETUP}}})', 12, y, unit="s", decimals=3))
panels.append(stat("체크 통과율 (최저)", f'min(k6_checks_rate{{{T}}})', 16, y, unit="percentunit", decimals=3))
panels.append(stat("완료 반복 수", f'sum(k6_iterations_total{{{T}}})', 20, y, decimals=0))
y += 4
panels.append(ts("요청 종류별 p95", [(f'max by (name) (k6_http_req_duration_p95{{{T}, {NOSETUP}}})', "{{name}}")], 0, y, w=12, unit="s",
                 desc="tags.name 별 p95. matching_join, dispatch_accept, dispatch_location 이 시나리오 핵심"))
panels.append(ts("요청 종류별 처리량", [(f'sum by (name) (rate(k6_http_reqs_total{{{T}, {NOSETUP}}}[$__rate_interval]))', "{{name}}")], 12, y, w=12, unit="reqps", stack=True))
y += 8
panels.append(ts("요청 종류별 에러율", [(f'sum by (name) (rate(k6_http_reqs_total{{{T}, {NOSETUP}, expected_response="false"}}[$__rate_interval])) / sum by (name) (rate(k6_http_reqs_total{{{T}, {NOSETUP}}}[$__rate_interval]))', "{{name}}")], 0, y, w=12, unit="percentunit", max_=1))
panels.append(ts("VU 와 반복", [(f'max by (testid) (k6_vus{{{T}}})', "VU {{testid}}"), (f'sum by (testid) (rate(k6_iterations_total{{{T}}}[$__rate_interval]))', "반복/초 {{testid}}")], 12, y, w=12))
y += 8

# ---- 시나리오 고유 ----
panels.append(row("시나리오 고유 지표", y)); y += 1
panels.append(ts("배차 콜 지연 (정원 충족 → 콜 열림)", [(f'max by (testid) (k6_dispatch_notify_ms_p95{{{T}}})', "p95 {{testid}}"), (f'max by (testid) (k6_dispatch_notify_ms_p99{{{T}}})', "p99 {{testid}}")], 0, y, unit="s",
                 desc="ride-e2e / dispatch-race / mixed. 아웃박스 → @Async 리스너 → GEOSEARCH 경로의 지연. 폴링 간격(250ms)만큼의 오차가 있다"))
panels.append(ts("콜 수신 / 여정 성공 / 승자 1명 비율", [(f'min by (testid) (k6_dispatch_notified_rate{{{T}}})', "콜 수신 {{testid}}"), (f'min by (testid) (k6_ride_journey_ok_rate{{{T}}})', "여정 성공 {{testid}}"), (f'min by (testid) (k6_dispatch_single_winner_rate{{{T}}})', "승자 1명 {{testid}}")], 8, y, unit="percentunit", max_=1))
panels.append(ts("아웃박스 미완료 이벤트", [('moyeota_event_publication_incomplete', "event_publication")], 16, y,
                 desc="완료 안 된 event_publication 행 수. 배차 리스너가 밀리면 쌓인다"))
y += 8
panels.append(ts("여정 / 매칭 사이클 시간 p95", [(f'max by (testid) (k6_ride_journey_ms_p95{{{T}}})', "여정 {{testid}}"), (f'max by (testid) (k6_matching_cycle_ms_p95{{{T}}})', "매칭 사이클 {{testid}}")], 0, y, unit="s"))
panels.append(ts("배차 수락 경쟁", [(f'sum(rate(k6_dispatch_accept_winners_total{{{T}}}[$__rate_interval]))', "승자/초"), (f'sum(rate(k6_dispatch_accept_conflicts_total{{{T}}}[$__rate_interval]))', "409 충돌/초")], 8, y, desc="dispatch-race. 반복당 승자가 1명을 넘으면 중복 배정"))
panels.append(ts("서버 측 URI 별 p95 (http_server_requests)", [('histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{uri!~"/prometheus|/health|/actuator.*"}[$__rate_interval])))', "{{uri}}")], 16, y, unit="s",
                 desc="k6 가 본 시간과 비교. 차이가 크면 네트워크나 Tomcat 큐 대기"))
y += 8

# ---- JVM / Tomcat / Hikari ----
panels.append(row("앱 (JVM · Tomcat · HikariCP)", y)); y += 1
panels.append(ts("Tomcat 스레드", [('tomcat_threads_busy_threads', "busy"), ('tomcat_threads_current_threads', "current"), ('tomcat_threads_config_max_threads', "max")], 0, y,
                 desc="busy 가 max(기본 200)에 붙으면 요청이 큐에서 대기한다"))
panels.append(ts("HikariCP 커넥션", [('hikaricp_connections_active', "active"), ('hikaricp_connections_pending', "pending"), ('hikaricp_connections_idle', "idle"), ('hikaricp_connections_max', "max")], 8, y,
                 desc="pending 이 0 을 넘으면 풀 크기(DB_POOL_MAX_SIZE)가 병목"))
panels.append(ts("HikariCP 커넥션 획득 대기 p95", [('histogram_quantile(0.95, sum by (le) (rate(hikaricp_connections_acquire_seconds_bucket[$__rate_interval])))', "acquire p95")], 16, y, unit="s"))
y += 8
panels.append(ts("JVM heap", [('sum(jvm_memory_used_bytes{area="heap"})', "used"), ('sum(jvm_memory_committed_bytes{area="heap"})', "committed"), ('sum(jvm_memory_max_bytes{area="heap"})', "max")], 0, y, unit="bytes"))
panels.append(ts("GC pause", [('sum by (action, cause) (rate(jvm_gc_pause_seconds_sum[$__rate_interval]))', "{{action}} {{cause}}")], 8, y, unit="s", desc="초당 GC 에 쓴 시간(초). 0.1 이면 CPU 시간의 10%"))
panels.append(ts("JVM 스레드 / CPU", [('jvm_threads_live_threads', "live threads"), ('process_cpu_usage * 100', "process CPU %"), ('system_cpu_usage * 100', "system CPU %")], 16, y))
y += 8

# ---- 인프라 ----
panels.append(row("인프라 (Postgres · Redis · 호스트)", y)); y += 1
panels.append(ts("Postgres 커넥션 상태", [('sum by (state) (pg_stat_activity_count{datname="moyeota"})', "{{state}}")], 0, y, stack=True))
panels.append(ts("Postgres 락 / 트랜잭션", [('sum(pg_locks_count{datname="moyeota", mode=~"RowExclusiveLock|ExclusiveLock|AccessExclusiveLock"})', "row/exclusive locks"), ('rate(pg_stat_database_xact_commit{datname="moyeota"}[$__rate_interval])', "commit/s"), ('rate(pg_stat_database_xact_rollback{datname="moyeota"}[$__rate_interval])', "rollback/s")], 8, y))
panels.append(ts("Postgres 캐시 히트율 / 데드락", [('rate(pg_stat_database_blks_hit{datname="moyeota"}[$__rate_interval]) / (rate(pg_stat_database_blks_hit{datname="moyeota"}[$__rate_interval]) + rate(pg_stat_database_blks_read{datname="moyeota"}[$__rate_interval]))', "cache hit"), ('rate(pg_stat_database_deadlocks{datname="moyeota"}[$__rate_interval])', "deadlock/s")], 16, y))
y += 8
panels.append(ts("Redis 명령 처리량", [('sum by (cmd) (rate(redis_commands_total[$__rate_interval]))', "{{cmd}}")], 0, y, unit="ops", stack=True, desc="geoadd / geosearch / sadd / get 이 시나리오 핵심 명령"))
panels.append(ts("Redis 명령별 평균 지연", [('sum by (cmd) (rate(redis_commands_duration_seconds_total[$__rate_interval])) / sum by (cmd) (rate(redis_commands_total[$__rate_interval]))', "{{cmd}}")], 8, y, unit="s"))
panels.append(ts("Redis 연결 / 메모리", [('redis_connected_clients', "clients"), ('redis_memory_used_bytes', "memory")], 16, y))
y += 8
panels.append(ts("호스트 CPU", [('100 - avg(rate(node_cpu_seconds_total{mode="idle"}[$__rate_interval])) * 100', "CPU %")], 0, y, unit="percent", max_=100))
panels.append(ts("호스트 메모리", [('node_memory_MemTotal_bytes - node_memory_MemAvailable_bytes', "used"), ('node_memory_MemTotal_bytes', "total")], 8, y, unit="bytes"))
panels.append(ts("호스트 네트워크", [('sum(rate(node_network_receive_bytes_total{device!~"lo|docker.*|veth.*"}[$__rate_interval]))', "rx"), ('sum(rate(node_network_transmit_bytes_total{device!~"lo|docker.*|veth.*"}[$__rate_interval]))', "tx")], 16, y, unit="Bps"))

dash = {
    "uid": "moyeota-loadtest",
    "title": "Moyeota 부하테스트",
    "description": "k6 시나리오 고유 지표와 앱·인프라 공통 지표를 같은 시간축에 놓는다. testid 로 회차 선택",
    "tags": ["moyeota", "loadtest"],
    "timezone": "browser", "editable": True, "schemaVersion": 39, "version": 1, "refresh": "5s",
    "time": {"from": "now-30m", "to": "now"},
    "templating": {"list": [{
        "name": "testid", "label": "회차 (testid)", "type": "query", "datasource": DS,
        "query": {"query": "label_values(k6_vus, testid)", "refId": "A"},
        "definition": "label_values(k6_vus, testid)",
        "refresh": 2, "sort": 4, "multi": True, "includeAll": True, "allValue": ".*",
        "current": {"text": "All", "value": "$__all"},
    }]},
    "annotations": {"list": [{"builtIn": 1, "type": "dashboard", "name": "Annotations & Alerts", "datasource": {"type": "grafana", "uid": "-- Grafana --"}, "enable": True, "hide": True}]},
    "panels": panels,
}
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dashboards", "moyeota-loadtest.json")
json.dump(dash, open(out, "w"), ensure_ascii=False, indent=2)
print(f"{out}: {len(panels)} panels")
