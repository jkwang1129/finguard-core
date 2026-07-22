package com.finguard.core.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finguard.core.account.entity.Account;
import com.finguard.core.account.model.AccountStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AccountMapper extends BaseMapper<Account> {

    @Select("SELECT COUNT(*) FROM accounts WHERE account_no = #{accountNo}")
    long countByAccountNoIncludingDeleted(@Param("accountNo") String accountNo);

    @Select("SELECT COUNT(*) FROM transactions WHERE account_id = #{accountId}")
    long countAllTransactionsByAccountId(@Param("accountId") Long accountId);

    @Update("""
            UPDATE accounts
            SET account_name = #{accountName}
            WHERE id = #{accountId}
              AND deleted = 0
            """)
    int updateAccountName(
            @Param("accountId") Long accountId,
            @Param("accountName") String accountName
    );

    @Update("""
            UPDATE accounts
            SET status = #{status}
            WHERE id = #{accountId}
              AND deleted = 0
            """)
    int updateAccountStatus(
            @Param("accountId") Long accountId,
            @Param("status") AccountStatus status
    );
}
