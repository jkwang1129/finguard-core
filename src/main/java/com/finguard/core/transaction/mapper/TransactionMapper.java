package com.finguard.core.transaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finguard.core.transaction.entity.Transaction;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface TransactionMapper extends BaseMapper<Transaction> {

    @Select("""
            SELECT COUNT(*)
            FROM transactions
            WHERE account_id = #{accountId}
              AND source = #{source}
              AND external_transaction_no = #{externalTransactionNo}
            """)
    long countByBusinessKeyIncludingDeleted(
            @Param("accountId") Long accountId,
            @Param("source") TransactionSource source,
            @Param("externalTransactionNo") String externalTransactionNo
    );

    @Select("""
            SELECT id,
                   account_id AS accountId,
                   external_transaction_no AS externalTransactionNo,
                   direction,
                   amount,
                   transaction_time AS transactionTime,
                   description,
                   source,
                   created_at AS createdAt,
                   updated_at AS updatedAt,
                   deleted
            FROM transactions
            WHERE id = #{transactionId}
            """)
    Transaction selectByIdIncludingDeleted(
            @Param("transactionId") Long transactionId
    );

    @Update("""
            UPDATE transactions
            SET direction = #{direction},
                amount = #{amount},
                transaction_time = #{transactionTime},
                description = #{description}
            WHERE id = #{transactionId}
              AND deleted = 0
            """)
    int updateMutableFields(
            @Param("transactionId") Long transactionId,
            @Param("direction") TransactionDirection direction,
            @Param("amount") BigDecimal amount,
            @Param("transactionTime") LocalDateTime transactionTime,
            @Param("description") String description
    );

    @Update("""
            UPDATE transactions
            SET deleted = 1
            WHERE id = #{transactionId}
              AND deleted = 0
            """)
    int softDeleteById(@Param("transactionId") Long transactionId);
}
