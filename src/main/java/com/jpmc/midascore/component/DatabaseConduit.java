package com.jpmc.midascore.component;

import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.repository.TransactionRecordRepository;
import com.jpmc.midascore.repository.UserRecordRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Optional;

@Component
public class DatabaseConduit {

    private final UserRecordRepository userRecordRepository;
    private final TransactionRecordRepository transactionRecordRepository;
    private final RestTemplate restTemplate;

    // ✅ Constructor injection (BEST PRACTICE)
    public DatabaseConduit(UserRecordRepository userRecordRepository,
                           TransactionRecordRepository transactionRecordRepository,
                           RestTemplate restTemplate) {
        this.userRecordRepository = userRecordRepository;
        this.transactionRecordRepository = transactionRecordRepository;
        this.restTemplate = restTemplate;
    }

    // existing method (keep it)
    public void save(UserRecord userRecord) {
        userRecordRepository.save(userRecord);
    }

    @Transactional
    public void handleTransaction(Transaction tx) {

        // 1. Find sender
        Optional<UserRecord> senderOpt = userRecordRepository.findById(tx.getSenderId());
        if (senderOpt.isEmpty()) {
            return;
        }

        // 2. Find recipient
        Optional<UserRecord> recipientOpt = userRecordRepository.findById(tx.getRecipientId());
        if (recipientOpt.isEmpty()) {
            return;
        }

        UserRecord sender = senderOpt.get();
        UserRecord recipient = recipientOpt.get();

        // 3. Check balance
        if (sender.getBalance() < tx.getAmount()) {
            return;
        }

        // 3.5 Call Incentive API
        String incentiveApiUrl = "http://localhost:8080/incentive";

        Incentive incentive = restTemplate.postForObject(
                incentiveApiUrl,
                tx,
                Incentive.class
        );

        // store incentive in transaction
        tx.setIncentive(incentive.getAmount());

        // 4. Adjust balances (Task 4 rule)
        sender.setBalance(sender.getBalance() - tx.getAmount());

        recipient.setBalance(
                recipient.getBalance()
                        + tx.getAmount()
                        + tx.getIncentive().floatValue()
        );

        // 5. Save users
        userRecordRepository.save(sender);
        userRecordRepository.save(recipient);

        // 6. Save transaction record
        TransactionRecord record = new TransactionRecord(
                sender,
                recipient,
                tx.getAmount(),
                Instant.now()
        );

        transactionRecordRepository.save(record);
    }
}
