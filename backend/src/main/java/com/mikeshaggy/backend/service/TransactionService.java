package com.mikeshaggy.backend.service;

import com.mikeshaggy.backend.domain.transaction.Category;
import com.mikeshaggy.backend.domain.transaction.Transaction;
import com.mikeshaggy.backend.domain.transaction.Type;
import com.mikeshaggy.backend.domain.user.User;
import com.mikeshaggy.backend.dto.TransactionDTO;
import com.mikeshaggy.backend.mapper.TransactionMapper;
import com.mikeshaggy.backend.repository.CategoryRepository;
import com.mikeshaggy.backend.repository.TransactionRepository;
import com.mikeshaggy.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionMapper transactionMapper;

    public List<TransactionDTO> getAllTransactions() {
        return transactionRepository.findAll()
                .stream()
                .map(transactionMapper::toDTO)
                .collect(Collectors.toList());
    }

    public List<TransactionDTO> getTransactionsByUserId(Integer userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found with id: " + userId);
        }
        return transactionRepository.findByUserId(userId)
                .stream()
                .map(transactionMapper::toDTO)
                .collect(Collectors.toList());
    }

    public TransactionDTO getTransactionById(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with id: " + id));
        return transactionMapper.toDTO(transaction);
    }

    @Transactional
    public TransactionDTO createTransaction(TransactionDTO transactionDTO) {
        if (transactionDTO.getType() == Type.INCOME) {
            transactionDTO.setImportance(null);
        } else if (transactionDTO.getType() == Type.EXPENSE && transactionDTO.getImportance() == null) {
            throw new IllegalArgumentException("Expense transactions must have importance");
        }

        User user = userRepository.findById(transactionDTO.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + transactionDTO.getUserId()));

        Category category = categoryRepository.findById(transactionDTO.getCategoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + transactionDTO.getCategoryId()));

        Transaction transaction = transactionMapper.toEntity(transactionDTO);
        transaction.setUser(user);
        transaction.setCategory(category);

        category.addTransaction(transaction);

        Transaction savedTransaction = transactionRepository.save(transaction);
//        categoryRepository.save(category);
        return transactionMapper.toDTO(savedTransaction);
    }

    @Transactional
    public TransactionDTO updateTransaction(Long id, TransactionDTO transactionDTO) {
        Transaction existingTransaction = transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found with id: " + id));

        if (transactionDTO.getCategoryId() != null) {
            Category category = categoryRepository.findById(transactionDTO.getCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + transactionDTO.getCategoryId()));
            existingTransaction.setCategory(category);
        }

        existingTransaction.setTitle(transactionDTO.getTitle());
        existingTransaction.setAmount(transactionDTO.getAmount());
        existingTransaction.setTransactionDate(transactionDTO.getTransactionDate());
        existingTransaction.setNotes(transactionDTO.getNotes());
        existingTransaction.setImportance(transactionDTO.getImportance());
        existingTransaction.setCycle(transactionDTO.getCycle());
        existingTransaction.setType(transactionDTO.getType());

        Transaction updatedTransaction = transactionRepository.save(existingTransaction);
        return transactionMapper.toDTO(updatedTransaction);
    }

    @Transactional
    public void deleteTransaction(Long id) {
        if (!transactionRepository.existsById(id)) {
            throw new EntityNotFoundException("Transaction not found with id: " + id);
        }
        transactionRepository.deleteById(id);
    }
}
