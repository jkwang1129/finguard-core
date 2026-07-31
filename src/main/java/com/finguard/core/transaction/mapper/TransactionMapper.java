package com.finguard.core.transaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finguard.core.transaction.entity.Transaction;
import com.finguard.core.transaction.model.TransactionDirection;
import com.finguard.core.transaction.model.TransactionBusinessKey;
import com.finguard.core.transaction.model.TransactionSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TransactionMapper extends BaseMapper<Transaction> {

    @Insert("""
            <script>
            INSERT INTO transactions (
                account_id,
                import_job_id,
                external_transaction_no,
                direction,
                amount,
                transaction_time,
                description,
                source,
                deleted
            )
            VALUES
            <foreach collection="transactions"
                     item="transaction"
                     separator=",">
                (
                    #{transaction.accountId},
                    #{transaction.importJobId},
                    #{transaction.externalTransactionNo},
                    #{transaction.direction},
                    #{transaction.amount},
                    #{transaction.transactionTime},
                    #{transaction.description},
                    #{transaction.source},
                    0
                )
            </foreach>
            </script>
            """)
    int insertBatch(
            @Param("transactions") List<Transaction> transactions
    );

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
            <script>
            SELECT account_id AS accountId,
                   external_transaction_no AS externalTransactionNo
            FROM transactions
            WHERE source = #{source}
              AND (account_id, external_transaction_no) IN
              <foreach collection="businessKeys"
                       item="businessKey"
                       open="("
                       separator=","
                       close=")">
                (#{businessKey.accountId},
                 #{businessKey.externalTransactionNo})
              </foreach>
            </script>
            """)
    List<TransactionBusinessKey> selectExistingBusinessKeysIncludingDeleted(
            @Param("source") TransactionSource source,
            @Param("businessKeys")
            List<TransactionBusinessKey> businessKeys
    );

    @Select("""
            SELECT id,
                   account_id AS accountId,
                   import_job_id AS importJobId,
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
