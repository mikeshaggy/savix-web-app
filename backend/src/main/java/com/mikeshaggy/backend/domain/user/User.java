package com.mikeshaggy.backend.domain.user;

import com.mikeshaggy.backend.domain.transaction.Category;
import com.mikeshaggy.backend.domain.transaction.Transaction;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Builder
    public User(Integer id, String username, String password,
                LocalDateTime createdAt, Set<Category> categories,
                Set<Transaction> transactions) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.createdAt = createdAt;
        this.categories = categories;
        this.transactions = transactions;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private Set<Category> categories = new HashSet<>();

    public void addCategory(Category category) {
        categories.add(category);
        category.setUser(this);
    }

    public void removeCategory(Category category) {
        categories.remove(category);
        category.setUser(null);
    }

    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private Set<Transaction> transactions = new HashSet<>();

    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
        transaction.setUser(this);
    }

    public void removeTransaction(Transaction transaction) {
        transactions.remove(transaction);
        transaction.setUser(null);
    }
}
