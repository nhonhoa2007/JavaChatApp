package org.example.server.dao;

import org.example.common.model.GroupChat;
import org.example.common.model.User;
import org.example.server.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.Query;

import java.util.List;

public class GroupDAO {

    public boolean save(GroupChat groupChat) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.persist(groupChat);
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

    public GroupChat findById(Long id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(GroupChat.class, id);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<GroupChat> getGroupsOfUser(User user) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT gm.groupChat FROM GroupMember gm WHERE gm.user.id = :userId ORDER BY gm.groupChat.createdAt DESC";
            Query<GroupChat> query = session.createQuery(hql, GroupChat.class);
            query.setParameter("userId", user.getId());
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public boolean deleteGroup(GroupChat groupChat) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            MutationQuery deleteReactions = session.createMutationQuery(
                    "DELETE FROM MessageReaction mr WHERE mr.message.id IN " +
                            "(SELECT m.id FROM Message m WHERE m.groupChat.id = :groupId)");
            deleteReactions.setParameter("groupId", groupChat.getId());
            deleteReactions.executeUpdate();

            MutationQuery deleteMessages = session.createMutationQuery(
                    "DELETE FROM Message m WHERE m.groupChat.id = :groupId");
            deleteMessages.setParameter("groupId", groupChat.getId());
            deleteMessages.executeUpdate();

            MutationQuery deleteMembers = session.createMutationQuery(
                    "DELETE FROM GroupMember gm WHERE gm.groupChat.id = :groupId");
            deleteMembers.setParameter("groupId", groupChat.getId());
            deleteMembers.executeUpdate();

            MutationQuery deleteGroup = session.createMutationQuery(
                    "DELETE FROM GroupChat g WHERE g.id = :groupId");
            deleteGroup.setParameter("groupId", groupChat.getId());
            int deleted = deleteGroup.executeUpdate();

            transaction.commit();
            return deleted > 0;
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
            return false;
        }
    }
}

