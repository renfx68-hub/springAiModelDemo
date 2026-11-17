package com.example.aiterminal.repository;

import com.example.aiterminal.entity.ChatSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 会话存取接口；查询均按归属用户过滤。 */
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    List<ChatSession> findAllByOwnerIdOrderByUpdatedAtDesc(String ownerId);

    /** 匿名模式：无主会话（历史数据/匿名新建）+ 显式匿名归属的会话。 */
    List<ChatSession> findAllByOwnerIdIsNullOrOwnerIdOrderByUpdatedAtDesc(String ownerId);

    /** 把账号体系上线前的无主会话归属给首个注册用户。 */
    @Modifying
    @Query("update ChatSession s set s.ownerId = :ownerId where s.ownerId is null")
    int adoptOrphans(@Param("ownerId") String ownerId);
}
