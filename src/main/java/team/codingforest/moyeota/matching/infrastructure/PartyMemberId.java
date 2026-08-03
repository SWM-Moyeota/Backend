package team.codingforest.moyeota.matching.infrastructure;

import java.io.Serializable;
import java.util.Objects;

public class PartyMemberId implements Serializable {
    private Long party;
    private Long memberId;

    protected PartyMemberId() {}

    public PartyMemberId(Long party, Long memberId) {
        this.party = party;
        this.memberId = memberId;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(!(o instanceof PartyMemberId that)) return false;
        return Objects.equals(party, that.party)
                && Objects.equals(memberId, that.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(party, memberId);
    }
}
