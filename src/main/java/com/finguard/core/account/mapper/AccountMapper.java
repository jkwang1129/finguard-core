package com.finguard.core.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finguard.core.account.entity.Account;
import com.finguard.core.account.model.AccountStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AccountMapper extends BaseMapper<Account> {

    @Select("SELECT COUNT(*) FROM accounts WHERE account_no = #{accountNo}")
    long countByAccountNoIncludingDeleted(@Param("accountNo") String accountNo);

    @Select("SELECT COUNT(*) FROM transactions WHERE account_id = #{accountId}")
    long countAllTransactionsByAccountId(@Param("accountId") Long accountId);

    @Select("""
            <script>
            SELECT id,
                   account_no AS accountNo,
                   status,
                   deleted
            FROM accounts
            WHERE deleted = 0
              AND account_no IN
              <foreach collection="accountNos"
                       item="accountNo"
                       open="("
                       separator=","
                       close=")">
                #{accountNo}
              </foreach>
            </script>
            """)
    List<Account> selectActiveOrDisabledByAccountNos(
            @Param("accountNos") List<String> accountNos
    );

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
