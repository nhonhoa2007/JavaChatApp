package org.example.server.dao;

import org.example.common.model.Friendship;
import org.example.common.model.User;
import org.example.server.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.List;

public class FriendshipDAO {

    public boolean saveFriendship(Friendship friendship) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.persist(friendship);
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

    public boolean updateFriendship(Friendship friendship) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.merge(friendship);
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

    // Tách riêng hàm cập nhật Block để tránh lỗi Detached Entity khi merge
    public boolean updateBlockStatus(User user1, User user2, String blockedBy) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            String hql = "UPDATE Friendship f SET f.blockedBy = :blockedBy WHERE (f.user = :u1 AND f.friend = :u2) OR (f.user = :u2 AND f.friend = :u1)";
            Query<?> query = session.createQuery(hql);
            query.setParameter("blockedBy", blockedBy);
            query.setParameter("u1", user1);
            query.setParameter("u2", user2);
            query.executeUpdate();
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

    // Tách riêng hàm cập nhật Mute
    public boolean updateMuteStatus(User user1, User user2, String mutedBy) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            String hql = "UPDATE Friendship f SET f.mutedBy = :mutedBy WHERE (f.user = :u1 AND f.friend = :u2) OR (f.user = :u2 AND f.friend = :u1)";
            Query<?> query = session.createQuery(hql);
            query.setParameter("mutedBy", mutedBy);
            query.setParameter("u1", user1);
            query.setParameter("u2", user2);
            query.executeUpdate();
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

    public Friendship findFriendship(User user1, User user2) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Friendship f WHERE (f.user = :u1 AND f.friend = :u2) OR (f.user = :u2 AND f.friend = :u1)";
            Query<Friendship> query = session.createQuery(hql, Friendship.class);
            query.setParameter("u1", user1);
            query.setParameter("u2", user2);
            query.setMaxResults(1);
            return query.uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Friendship> getAcceptedFriends(User user) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Friendship f WHERE (f.user = :user OR f.friend = :user) AND f.status = 'ACCEPTED'";
            Query<Friendship> query = session.createQuery(hql, Friendship.class);
            query.setParameter("user", user);
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Friendship> getPendingRequests(User user) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Friendship f WHERE f.friend = :user AND f.status = 'PENDING'";
            Query<Friendship> query = session.createQuery(hql, Friendship.class);
            query.setParameter("user", user);
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean isBlocked(User sender, User receiver) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Friendship f WHERE ((f.user = :receiver AND f.friend = :sender) OR (f.user = :sender AND f.friend = :receiver)) " +
                         "AND f.blockedBy LIKE :blockedPattern";
            Query<Friendship> query = session.createQuery(hql, Friendship.class);
            query.setParameter("receiver", receiver);
            query.setParameter("sender", sender);
            query.setParameter("blockedPattern", "%" + receiver.getUsername() + "%");
            query.setMaxResults(1);
            
            return query.uniqueResult() != null;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isMuted(User sender, User receiver) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Friendship f WHERE ((f.user = :receiver AND f.friend = :sender) OR (f.user = :sender AND f.friend = :receiver)) " +
                         "AND f.mutedBy LIKE :mutedPattern";
            Query<Friendship> query = session.createQuery(hql, Friendship.class);
            query.setParameter("receiver", receiver);
            query.setParameter("sender", sender);
            query.setParameter("mutedPattern", "%" + receiver.getUsername() + "%");
            query.setMaxResults(1);
            
            return query.uniqueResult() != null;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
