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
            String hql = "UPDATE Friendship f SET f.blockedBy = :blockedBy WHERE (f.user.id = :u1Id AND f.friend.id = :u2Id) OR (f.user.id = :u2Id AND f.friend.id = :u1Id)";
            Query<?> query = session.createQuery(hql);
            if (blockedBy == null) {
                query.setParameter("blockedBy", null, String.class);
            } else {
                query.setParameter("blockedBy", blockedBy);
            }
            query.setParameter("u1Id", user1.getId());
            query.setParameter("u2Id", user2.getId());
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
            String hql = "UPDATE Friendship f SET f.mutedBy = :mutedBy WHERE (f.user.id = :u1Id AND f.friend.id = :u2Id) OR (f.user.id = :u2Id AND f.friend.id = :u1Id)";
            Query<?> query = session.createQuery(hql);
            if (mutedBy == null) {
                query.setParameter("mutedBy", null, String.class);
            } else {
                query.setParameter("mutedBy", mutedBy);
            }
            query.setParameter("u1Id", user1.getId());
            query.setParameter("u2Id", user2.getId());
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
            String hql = "FROM Friendship f WHERE (f.user.id = :u1Id AND f.friend.id = :u2Id) OR (f.user.id = :u2Id AND f.friend.id = :u1Id)";
            Query<Friendship> query = session.createQuery(hql, Friendship.class);
            query.setParameter("u1Id", user1.getId());
            query.setParameter("u2Id", user2.getId());
            query.setMaxResults(1);
            return query.uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Friendship> getAcceptedFriends(User user) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Friendship f WHERE (f.user.id = :userId OR f.friend.id = :userId) AND f.status = 'ACCEPTED'";
            Query<Friendship> query = session.createQuery(hql, Friendship.class);
            query.setParameter("userId", user.getId());
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Friendship> getPendingRequests(User user) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Friendship f WHERE f.friend.id = :userId AND f.status = 'PENDING'";
            Query<Friendship> query = session.createQuery(hql, Friendship.class);
            query.setParameter("userId", user.getId());
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean isBlocked(User sender, User receiver) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Friendship f WHERE ((f.user.id = :receiverId AND f.friend.id = :senderId) OR (f.user.id = :senderId AND f.friend.id = :receiverId)) " +
                         "AND f.blockedBy LIKE :blockedPattern";
            Query<Friendship> query = session.createQuery(hql, Friendship.class);
            query.setParameter("receiverId", receiver.getId());
            query.setParameter("senderId", sender.getId());
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
            String hql = "FROM Friendship f WHERE ((f.user.id = :receiverId AND f.friend.id = :senderId) OR (f.user.id = :senderId AND f.friend.id = :receiverId)) " +
                         "AND f.mutedBy LIKE :mutedPattern";
            Query<Friendship> query = session.createQuery(hql, Friendship.class);
            query.setParameter("receiverId", receiver.getId());
            query.setParameter("senderId", sender.getId());
            query.setParameter("mutedPattern", "%" + receiver.getUsername() + "%");
            query.setMaxResults(1);
            
            return query.uniqueResult() != null;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
<<<<<<< HEAD

    public List<Friendship> getAllFriendships(User user) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Friendship f WHERE f.user.id = :userId OR f.friend.id = :userId";
            Query<Friendship> query = session.createQuery(hql, Friendship.class);
            query.setParameter("userId", user.getId());
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return java.util.Collections.emptyList();
        }
    }
=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
}
