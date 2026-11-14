-- 탑승 기록의 시각을 하차 시각에서 승차 시각으로 바로잡는다.
-- 서버는 하차를 알 수 없다. 방이 끝날 때 dropoff_time 에 남기던 값은 실제로는 방장이 출발을 누른 순간(승차 시각)이었다.
-- dropoff_time 이 비어 있는 기록은 택시 기능 시절 하차 전에 멈춘 탑승이라 목록에도 나오지 않았다 - 지우고 NOT NULL 로 묶는다
DELETE FROM ride_record WHERE dropoff_time IS NULL;
ALTER TABLE ride_record RENAME COLUMN dropoff_time TO pickup_time;
ALTER TABLE ride_record ALTER COLUMN pickup_time SET NOT NULL;
