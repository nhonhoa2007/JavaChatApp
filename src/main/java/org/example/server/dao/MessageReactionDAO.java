package org.example.server.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.example.common.model.Message;
import org.example.common.model.MessageReaction;
import org.example.common.model.User;
import org.example.server.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.List;

public class MessageReactionDAO {

	public MessageReaction findByMessageAndUser(Message message, User user) {
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			String hql = "FROM MessageReaction mr WHERE mr.message.id = :messageId AND mr.user.id = :userId";
			Query<MessageReaction> query = session.createQuery(hql, MessageReaction.class);
			query.setParameter("messageId", message.getId());
			query.setParameter("userId", user.getId());
			query.setMaxResults(1);
			return query.uniqueResult();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public boolean saveOrUpdate(MessageReaction reaction) {
		Transaction transaction = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			transaction = session.beginTransaction();
			session.merge(reaction);
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

	public boolean delete(MessageReaction reaction) {
		Transaction transaction = null;
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			transaction = session.beginTransaction();
			session.remove(session.contains(reaction) ? reaction : session.merge(reaction));
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

	public JsonArray getReactionSummary(Message message) {
		JsonArray result = new JsonArray();
		try (Session session = HibernateUtil.getSessionFactory().openSession()) {
			String hql = "SELECT mr.emoji, COUNT(mr.id) FROM MessageReaction mr WHERE mr.message.id = :messageId GROUP BY mr.emoji ORDER BY COUNT(mr.id) DESC";
			Query<Object[]> query = session.createQuery(hql, Object[].class);
			query.setParameter("messageId", message.getId());

			List<Object[]> rows = query.list();
			for (Object[] row : rows) {
				JsonObject item = new JsonObject();
				item.addProperty("emoji", String.valueOf(row[0]));
				item.addProperty("count", ((Long) row[1]).intValue());
				result.add(item);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}
}

