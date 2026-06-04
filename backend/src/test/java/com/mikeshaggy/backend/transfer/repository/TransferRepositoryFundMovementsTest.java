package com.mikeshaggy.backend.transfer.repository;

import com.mikeshaggy.backend.transfer.domain.Transfer;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TransferRepositoryFundMovementsTest {

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("transferDate"), Sort.Order.desc("id"));

    private User user;
    private Wallet main;
    private Wallet main2;
    private Wallet fundA;
    private Wallet fundB;

    @BeforeEach
    void setUp() {
        user = entityManager.persist(User.builder()
                .email("fund-mov@example.com")
                .username("fund-mov")
                .passwordHash("hash")
                .build());
        main = wallet("Main", false);
        main2 = wallet("Secondary", false);
        fundA = wallet("Vacation", true);
        fundB = wallet("Car", true);
    }

    private Wallet wallet(String name, boolean isFund) {
        return entityManager.persist(Wallet.builder()
                .user(user)
                .name(name)
                .balance(BigDecimal.ZERO)
                .isFund(isFund)
                .build());
    }

    private Transfer transfer(Wallet from, Wallet to, String amount, LocalDate date) {
        return entityManager.persist(Transfer.builder()
                .fromWallet(from)
                .toWallet(to)
                .amount(new BigDecimal(amount))
                .transferDate(date)
                .build());
    }

    @Test
    void returnsOnlyTransfersInvolvingTheFundWallet_newestFirst() {
        Transfer depositA = transfer(main, fundA, "500.00", LocalDate.of(2026, 5, 25));   // fund A
        Transfer withdrawA = transfer(fundA, main, "200.00", LocalDate.of(2026, 5, 27));  // fund A
        transfer(main, fundB, "300.00", LocalDate.of(2026, 5, 26));                       // other fund -> excluded
        transfer(main, main2, "100.00", LocalDate.of(2026, 5, 28));                       // normal -> excluded
        entityManager.flush();
        entityManager.clear();

        Page<Transfer> page = transferRepository.findFundMovements(
                user.getId(), fundA.getId(), PageRequest.of(0, 10, NEWEST_FIRST));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Transfer::getId)
                .containsExactly(withdrawA.getId(), depositA.getId()); // 05-27 before 05-25
    }

    @Test
    void paginationAndCountWork() {
        transfer(main, fundA, "500.00", LocalDate.of(2026, 5, 25));
        transfer(fundA, main, "200.00", LocalDate.of(2026, 5, 27));
        entityManager.flush();
        entityManager.clear();

        Page<Transfer> firstPage = transferRepository.findFundMovements(
                user.getId(), fundA.getId(), PageRequest.of(0, 1, NEWEST_FIRST));

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(firstPage.hasNext()).isTrue();
    }

    @Test
    void doesNotLeakOtherUsersTransfers() {
        User other = entityManager.persist(User.builder()
                .email("other@example.com").username("other").passwordHash("hash").build());
        Wallet otherMain = entityManager.persist(Wallet.builder()
                .user(other).name("Main").balance(BigDecimal.ZERO).isFund(false).build());
        Wallet otherFund = entityManager.persist(Wallet.builder()
                .user(other).name("Vacation").balance(BigDecimal.ZERO).isFund(true).build());
        transfer(otherMain, otherFund, "999.00", LocalDate.of(2026, 5, 25));
        entityManager.flush();
        entityManager.clear();

        // Querying our user with the OTHER user's fund wallet id must return nothing.
        Page<Transfer> page = transferRepository.findFundMovements(
                user.getId(), otherFund.getId(), PageRequest.of(0, 10, NEWEST_FIRST));

        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void emptyWhenNoMovements() {
        Page<Transfer> page = transferRepository.findFundMovements(
                user.getId(), fundA.getId(), PageRequest.of(0, 10, NEWEST_FIRST));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }
}
