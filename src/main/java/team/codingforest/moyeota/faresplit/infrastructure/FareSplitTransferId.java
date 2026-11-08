package team.codingforest.moyeota.faresplit.infrastructure;

import java.io.Serializable;
import java.util.Objects;

public class FareSplitTransferId implements Serializable {
    private Long fareSplit;
    private Long memberId;

    protected FareSplitTransferId() {}

    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(!(o instanceof FareSplitTransferId that)) return false;
        return Objects.equals(fareSplit, that.fareSplit)
                && Objects.equals(memberId, that.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fareSplit, memberId);
    }
}
