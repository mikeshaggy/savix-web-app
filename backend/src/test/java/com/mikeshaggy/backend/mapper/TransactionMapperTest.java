package com.mikeshaggy.backend.mapper;

import com.mikeshaggy.backend.domain.transaction.Category;
import com.mikeshaggy.backend.domain.transaction.Cycle;
import com.mikeshaggy.backend.domain.transaction.Importance;
import com.mikeshaggy.backend.domain.transaction.Transaction;
import com.mikeshaggy.backend.domain.transaction.Type;
import com.mikeshaggy.backend.domain.user.User;
import com.mikeshaggy.backend.dto.TransactionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class TransactionMapperTest {

    private TransactionMapper transactionMapper;

    private static final long TRANSACTION_ID = 1L;
    private static final int USER_ID = 2;
    private static final String USER_USERNAME = "johndoe";
    private static final String USER_PASSWORD = "test_password";
    private static final int CATEGORY_ID = 3;
    private static final String CATEGORY_NAME = "Groceries";
    private static final String TRANSACTION_TITLE = "Weekly Shopping";
    private static final BigDecimal TRANSACTION_AMOUNT = new BigDecimal("150.50");
    private static final String TRANSACTION_NOTES = "Bought vegetables and fruits";
    private static final Importance TRANSACTION_IMPORTANCE = Importance.ESSENTIAL;
    private static final Cycle TRANSACTION_CYCLE = Cycle.WEEKLY;
    private static final Type TRANSACTION_TYPE = Type.EXPENSE;

    private LocalDateTime createdAt;
    private LocalDate transactionDate;
    private User testUser;
    private Category testCategory;
    private Transaction testTransaction;

    @BeforeEach
    void setUp() {
        transactionMapper = TransactionMapper.INSTANCE;

        createdAt = LocalDateTime.of(2024, 1, 15, 10, 30);
        transactionDate = LocalDate.of(2024, 1, 15);

        testUser = User.builder()
                .id(USER_ID)
                .username(USER_USERNAME)
                .password(USER_PASSWORD)
                .createdAt(createdAt)
                .categories(new HashSet<>())
                .transactions(new HashSet<>())
                .build();

        testCategory = Category.builder()
                .id(CATEGORY_ID)
                .name(CATEGORY_NAME)
                .createdAt(createdAt)
                .user(testUser)
                .build();

        testTransaction = Transaction.builder()
                .id(TRANSACTION_ID)
                .user(testUser)
                .category(testCategory)
                .title(TRANSACTION_TITLE)
                .amount(TRANSACTION_AMOUNT)
                .transactionDate(transactionDate)
                .notes(TRANSACTION_NOTES)
                .importance(TRANSACTION_IMPORTANCE)
                .cycle(TRANSACTION_CYCLE)
                .type(TRANSACTION_TYPE)
                .createdAt(createdAt)
                .build();
    }

    @Test
    @DisplayName("Converting Transaction to TransactionDTO")
    void entityToDto() {
        // when
        TransactionDTO transactionDTO = transactionMapper.toDTO(testTransaction);

        // then
        assertAll(
                () -> assertThat(transactionDTO.getId()).isEqualTo(TRANSACTION_ID),
                () -> assertThat(transactionDTO.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(transactionDTO.getCategoryId()).isEqualTo(CATEGORY_ID),
                () -> assertThat(transactionDTO.getCategoryName()).isEqualTo(CATEGORY_NAME),
                () -> assertThat(transactionDTO.getTitle()).isEqualTo(TRANSACTION_TITLE),
                () -> assertThat(transactionDTO.getAmount()).isEqualTo(TRANSACTION_AMOUNT),
                () -> assertThat(transactionDTO.getTransactionDate()).isEqualTo(transactionDate),
                () -> assertThat(transactionDTO.getNotes()).isEqualTo(TRANSACTION_NOTES),
                () -> assertThat(transactionDTO.getImportance()).isEqualTo(TRANSACTION_IMPORTANCE),
                () -> assertThat(transactionDTO.getCycle()).isEqualTo(TRANSACTION_CYCLE),
                () -> assertThat(transactionDTO.getType()).isEqualTo(TRANSACTION_TYPE),
                () -> assertThat(transactionDTO.getCreatedAt()).isEqualTo(createdAt)
        );
    }

    @Test
    @DisplayName("Converting TransactionDTO to Transaction")
    void dtoToEntity() {
        // given
        TransactionDTO transactionDTO = TransactionDTO.builder()
                .id(TRANSACTION_ID)
                .userId(USER_ID)
                .categoryId(CATEGORY_ID)
                .categoryName(CATEGORY_NAME)
                .title(TRANSACTION_TITLE)
                .amount(TRANSACTION_AMOUNT)
                .transactionDate(transactionDate)
                .notes(TRANSACTION_NOTES)
                .importance(TRANSACTION_IMPORTANCE)
                .cycle(TRANSACTION_CYCLE)
                .type(TRANSACTION_TYPE)
                .createdAt(createdAt)
                .build();

        // when
        Transaction transaction = transactionMapper.toEntity(transactionDTO);

        // then
        assertAll(
                () -> assertThat(transaction.getId()).isEqualTo(TRANSACTION_ID),
                () -> assertThat(transaction.getUser().getId()).isEqualTo(USER_ID),
                () -> assertThat(transaction.getCategory().getId()).isEqualTo(CATEGORY_ID),
                () -> assertThat(transaction.getTitle()).isEqualTo(TRANSACTION_TITLE),
                () -> assertThat(transaction.getAmount()).isEqualTo(TRANSACTION_AMOUNT),
                () -> assertThat(transaction.getTransactionDate()).isEqualTo(transactionDate),
                () -> assertThat(transaction.getNotes()).isEqualTo(TRANSACTION_NOTES),
                () -> assertThat(transaction.getImportance()).isEqualTo(TRANSACTION_IMPORTANCE),
                () -> assertThat(transaction.getCycle()).isEqualTo(TRANSACTION_CYCLE),
                () -> assertThat(transaction.getType()).isEqualTo(TRANSACTION_TYPE),
                () -> assertThat(transaction.getCreatedAt()).isEqualTo(createdAt),
                () -> assertThat(transaction.getUser().getUsername()).isNull(),
                () -> assertThat(transaction.getUser().getPassword()).isNull(),
                () -> assertThat(transaction.getUser().getCreatedAt()).isNull(),
                () -> assertThat(transaction.getUser().getCategories()).isEmpty(),
                () -> assertThat(transaction.getUser().getTransactions()).isEmpty(),
                () -> assertThat(transaction.getCategory().getName()).isNull(),
                () -> assertThat(transaction.getCategory().getUser()).isNull(),
                () -> assertThat(transaction.getCategory().getCreatedAt()).isNull(),
                () -> assertThat(transaction.getCategory().getTransactions()).isEmpty()
        );
    }

    @Test
    @DisplayName("Converting null Transaction should return null")
    void entityToDto_WithNullTransaction_ShouldReturnNull() {
        // when
        TransactionDTO result = transactionMapper.toDTO(null);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Converting null TransactionDTO should return null")
    void dtoToEntity_WithNullTransactionDTO_ShouldReturnNull() {
        // when
        Transaction result = transactionMapper.toEntity(null);

        // then
        assertThat(result).isNull();
    }
}
