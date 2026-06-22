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

    // lấy lịch sử cuộc gọi của user theo thứ tự mới nhất
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

    // lấy lịch sử cuộc gọi giữa hai user theo thứ tự cũ nhất
    public List<CallLog> getCallHistoryBetween(User user1, User user2, int limit) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM CallLog c WHERE " +
                    "(c.caller.id = :u1 AND c.callee.id = :u2) OR " +
                    "(c.caller.id = :u2 AND c.callee.id = :u1) " +
                    "ORDER BY c.startedAt ASC";
            Query<CallLog> query = session.createQuery(hql, CallLog.class);
            query.setParameter("u1", user1.getId());
            query.setParameter("u2", user2.getId());
            if (limit > 0) query.setMaxResults(limit);
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }
}
