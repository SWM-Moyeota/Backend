package team.codingforest.moyeota.common;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

@MappedSuperclass
@Getter
public abstract class BaseTimeEntity extends BaseCreatedTimeEntity implements TimeTrackable {

    @LastModifiedDate
    private Instant updatedAt;
}
