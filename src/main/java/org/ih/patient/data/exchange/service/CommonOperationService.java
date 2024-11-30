package org.ih.patient.data.exchange.service;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

import org.springframework.stereotype.Service;

@Service
public class CommonOperationService {

	@PersistenceContext
	private EntityManager em;

	public Integer findLoationByUuid(String table, String uuid, String name) {
		System.err.println(uuid + ":" + name);
		String sql = "select location_id from " + table + " where uuid=:uuid or name=:name ";
		Integer id = null;
		List l = em.createNativeQuery(sql).setParameter("uuid", uuid).setParameter("name", name).getResultList();
		for (Object p : l) {
			id = (Integer) p;
		}
		if (id == null) {
			return 0;
		}
		return id;
	}

	public Integer findResourceIdByUuid(String table, String uuid, String primaryKey) {
		String sql = "select " + primaryKey + " from " + table + " where uuid=:uuid ";
		Integer id = null;
		List l = em.createNativeQuery(sql).setParameter("uuid", uuid).getResultList();
		for (Object p : l) {
			id = (Integer) p;
		}
		if (id == null) {
			return 0;
		}
		return id;
	}

	public String findResourceUuidByName(String table, String inputName, String inputFiledName, String outputFiled) {
		String sql = "select " + outputFiled + " from " + table + " where " + inputFiledName + "=:inputName ";
		System.err.println(sql);
		String uuid = null;
		List l = em.createNativeQuery(sql).setParameter("inputName", inputName).getResultList();
		for (Object p : l) {
			uuid = (String) p;
		}
		if (uuid == null) {
			return null;
		}
		return uuid;
	}

	@Transactional
	public Integer updateResource(String table, Integer id, String uuid, String conditionId) {
		String sql = " update  " + table + " set uuid=:uuid where " + conditionId + "=:id ";
		em.createNativeQuery(sql).setParameter("uuid", uuid).setParameter("id", id).executeUpdate();
		return 0;

	}
}
