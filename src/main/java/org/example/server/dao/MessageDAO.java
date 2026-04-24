package org.example.server.dao;

import org.example.common.model.Message;
import org.example.common.model.GroupChat;
import org.example.common.model.User;
import org.example.server.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.List;

public class MessageDAO {

    public boolean saveMessage(Message message) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.persist(message);
            transaction.commit();
            return true;
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateMessage(Message message) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.merge(message);
            transaction.commit();
            return true;
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
            return false;
        }
    }

    public Message findById(Long id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(Message.class, id);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Message> getPrivateHistory(User user1, User user2, int limit) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Message m WHERE (m.sender = :u1 AND m.receiver = :u2) " +
                         "OR (m.sender = :u2 AND m.receiver = :u1) ORDER BY m.sentAt ASC";
            Query<Message> query = session.createQuery(hql, Message.class);
            query.setParameter("u1", user1);
            query.setParameter("u2", user2);
            if (limit > 0) {
                query.setMaxResults(limit);
            }
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Message> getGroupHistory(GroupChat groupChat, int limit) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Message m WHERE m.groupChat.id = :groupId ORDER BY m.sentAt ASC";
            Query<Message> query = session.createQuery(hql, Message.class);
            query.setParameter("groupId", groupChat.getId());
            if (limit > 0) {
                query.setMaxResults(limit);
            }
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
