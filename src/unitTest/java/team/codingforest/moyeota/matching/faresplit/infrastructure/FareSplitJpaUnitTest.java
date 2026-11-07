package team.codingforest.moyeota.matching.faresplit.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.matching.faresplit.domain.Bank;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplit;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitStatus;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitTransfer;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 *  정산 INSERT 가 실패했을 때 중복 키(같은 방에 이미 있는 정산)만 DuplicateKeyException 으로 바꾸고, 다른 무결성 위반은 그대로 올리는지 본다.
 *  실제 DB 의 중복 키는 FareSplitJpaPersistenceTest(H2)·FareSplitPostgresTest 가 본다
 */
@ExtendWith(MockitoExtension.class)
class FareSplitJpaUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Instant CREATED_AT = Instant.parse("2026-11-03T12:40:00Z");

    @Mock
    private FareSplitJpaRepository repository;
    @Mock
    private EntityManager em;
    @Mock
    private PartyAccess partyAccess;
    @InjectMocks
    private FareSplitJpa fareSplits;

    @DisplayName("create - unique 제약 위반(Hibernate ConstraintKind.UNIQUE)이면 DuplicateKeyException 으로 바꾸고 원인을 남긴다")
    @Test
    void create_uniqueViolation() {

        /*
         * settings
         */
        ConstraintViolationException violation = new ConstraintViolationException("duplicate key", new SQLException("duplicate key"),
                ConstraintKind.UNIQUE, "fare_split_pkey");
        doThrow(violation).when(em).flush();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> fareSplits.create(split()));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(DuplicateKeyException.class).hasCause(violation);
        verify(em).persist(any(FareSplitEntity.class));
    }

    @DisplayName("create - 제약 종류를 몰라도 원인을 따라가 SQLState 23505(unique_violation)가 있으면 DuplicateKeyException 이다")
    @Test
    void create_uniqueViolationBySqlState() {

        /*
         * settings
         */
        PersistenceException wrapped = new PersistenceException("could not execute statement",
                new ConstraintViolationException("duplicate key", new SQLException("duplicate key", "23505"), "fare_split_pkey"));
        doThrow(wrapped).when(em).flush();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> fareSplits.create(split()));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(DuplicateKeyException.class).hasCause(wrapped);
    }

    @DisplayName("create - 중복 키가 아닌 무결성 위반(FK·NOT NULL·CHECK)은 바꾸지 않고 그대로 올린다")
    @ParameterizedTest(name = "{0} / SQLState {1}")
    @CsvSource({"FOREIGN_KEY, 23503", "NOT_NULL, 23502", "CHECK, 23514"})
    void create_otherViolation(ConstraintKind kind, String sqlState) {

        /*
         * settings
         */
        ConstraintViolationException violation = new ConstraintViolationException("violation", new SQLException("violation", sqlState),
                kind, "some_constraint");
        doThrow(violation).when(em).flush();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> fareSplits.create(split()));

        /*
         * verify
         */
        assertThat(thrown).isSameAs(violation);
    }

    private static FareSplit split() {
        return FareSplit.load(PARTY_ID, 1L, 18_700, 6_240, Bank.KAKAO, new AccountNumber("3333012345678"), FareSplitStatus.OPEN, CREATED_AT, null,
                List.of(FareSplitTransfer.load(2L, null, null, 1, CREATED_AT, null), FareSplitTransfer.load(3L, null, null, 1, CREATED_AT, null)));
    }
}
