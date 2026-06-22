package org.example.server.dao;

import org.example.common.model.GroupChat;
import org.example.common.model.GroupMember;
import org.example.common.model.User;
import org.example.server.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupMemberDAO {

    public boolean save(GroupMember groupMember) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.persist(groupMember);
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

    public boolean isMember(GroupChat groupChat, User user) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM GroupMember gm WHERE gm.groupChat.id = :groupId AND gm.user.id = :userId";
            Query<GroupMember> query = session.createQuery(hql, GroupMember.class);
            query.setParameter("groupId", groupChat.getId());
            query.setParameter("userId", user.getId());
            query.setMaxResults(1);
            return query.uniqueResult() != null;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<User> getMembers(GroupChat groupChat) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT gm.user FROM GroupMember gm WHERE gm.groupChat.id = :groupId";
            Query<User> query = session.createQuery(hql, User.class);
            query.setParameter("groupId", groupChat.getId());
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    // đếm số thành viên của nhiều nhóm bằng một query group by
    public Map<Long, Integer> getMemberCountBatch(List<Long> groupIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (groupIds == null || groupIds.isEmpty()) return result;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT gm.groupChat.id, COUNT(gm.id) FROM GroupMember gm " +
                    "WHERE gm.groupChat.id IN :groupIds GROUP BY gm.groupChat.id";
            Query<Object[]> query = session.createQuery(hql, Object[].class);
            query.setParameter("groupIds", groupIds);
            for (Object[] row : query.list()) {
                result.put((Long) row[0], ((Long) row[1]).intValue());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}

