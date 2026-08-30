set -e

PSQL="docker exec -i moyeota-postgres psql -U moyeota -d moyeota -t -A"
ROOM_ID=1
PAGE_SIZE=20
ITERATIONS=10

echo "=========================================="
echo "커서 vs offset 페이징 벤치마크"
echo "실행 시각: $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================="
echo
echo "[환경]"
$PSQL -c "select version();"
echo "총 행 수: $($PSQL -c "select count(*) from chat_message where chat_room_id = $ROOM_ID;")"
echo "페이지 크기: $PAGE_SIZE"
echo "반복: ${ITERATIONS}회 (첫 회 워밍업 제외, 중앙값 채택)"
echo
echo "[인덱스]"
$PSQL -c "select indexdef from pg_indexes where tablename = 'chat_message';"
echo

# 깊이별 측정
for DEPTH in 0 100 500 2000; do
    OFFSET=$((DEPTH * PAGE_SIZE))

    # 해당 깊이의 커서 id를 미리 조회 (측정 대상 아님)
    CURSOR=$($PSQL -c "
        select id from chat_message
        where chat_room_id = $ROOM_ID
        order by id desc
        limit 1 offset $OFFSET;")

    echo "------------------------------------------"
    echo "깊이: ${DEPTH}페이지 (offset=$OFFSET, cursor=$CURSOR)"
    echo "------------------------------------------"

    for METHOD in offset cursor; do
        if [ "$METHOD" = "offset" ]; then
            QUERY="select id, content from chat_message
                   where chat_room_id = $ROOM_ID
                   order by id desc
                   limit $PAGE_SIZE offset $OFFSET;"
        else
            QUERY="select id, content from chat_message
                   where chat_room_id = $ROOM_ID and id < $CURSOR
                   order by id desc
                   limit $((PAGE_SIZE + 1));"
        fi

        TIMES=()
        for i in $(seq 1 $ITERATIONS); do
            MS=$($PSQL -c "explain (analyze, timing off, format text) $QUERY" \
                 | grep "Execution Time" | grep -oE "[0-9]+\.[0-9]+")
            [ "$i" -eq 1 ] && continue   # 워밍업 제외
            TIMES+=("$MS")
        done

        MEDIAN=$(printf '%s\n' "${TIMES[@]}" | sort -n | awk '{a[NR]=$1} END{print (NR%2==1) ? a[(NR+1)/2] : (a[NR/2]+a[NR/2+1])/2}')
        echo "$METHOD: ${MEDIAN} ms  (측정값: ${TIMES[*]})"
    done
    echo
done

echo "=========================================="
echo "[실행 계획 원본 — 2000페이지 깊이]"
echo "=========================================="
OFFSET=$((2000 * PAGE_SIZE))
CURSOR=$($PSQL -c "select id from chat_message where chat_room_id = $ROOM_ID order by id desc limit 1 offset $OFFSET;")

echo "--- offset 방식"
docker exec -i moyeota-postgres psql -U moyeota -d moyeota -c "
explain (analyze, buffers)
select id, content from chat_message
where chat_room_id = $ROOM_ID
order by id desc
limit $PAGE_SIZE offset $OFFSET;"

echo "--- 커서 방식"
docker exec -i moyeota-postgres psql -U moyeota -d moyeota -c "
explain (analyze, buffers)
select id, content from chat_message
where chat_room_id = $ROOM_ID and id < $CURSOR
order by id desc
limit $((PAGE_SIZE + 1));"