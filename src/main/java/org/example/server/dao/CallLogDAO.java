package org.example.server.dao;

import org.example.common.model.CallLog;
import org.example.common.model.User;
import org.example.server.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.List;

public class CallLogDAO {

    public boolean save(CallLog callLog) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.persist(callLog);
            transaction.commit();
            return true;
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
            return false;
        }
    }

    // Lấy lịch sử cuộc gọi của user (cả gọi đi và nhận), mới nhất trước
    public List<CallLog> getCallHistory(User user, int limit) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM CallLog c WHERE c.caller.id = :userId OR c.callee.id = :userId ORDER BY c.startedAt DESC";
            Query<CallLog> query = session.createQuery(hql, CallLog.class);
            query.setParameter("userId", user.getId());
            if (limit > 0) query.setMaxResults(limit);
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }
}
